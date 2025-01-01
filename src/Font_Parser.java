import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import java.nio.file.Files;

public final class Font_Parser {
    public static final class Glyph
    {
        public int unicode;
        public int index;
        public int[] points_x;
        public int[] points_y;
        public boolean[] points_on_curve;
        public int[] contour_end_indices;
        public int advance_width;
        public int left_side_bearing;

        public int min_x;
        public int max_x;
        public int min_y;
        public int max_y;

        public int computed_min_x;
        public int computed_max_x;
        public int computed_min_y;
        public int computed_max_y;
    }

    public static final class Font
    {
        public String name;
        public String family;
        public String subfamily;
        public String description;
        public String manufacturer_name;
        public String designer_name;
        public String vendor_url;
        public String designer_url;
        public String copyright;
        public String license;
        public String license_url;

        public Glyph missing_glyph;

        public int units_per_em;
        public Instant created_time;
        public Instant modified_time;

        //Maps unicode codepoints to glyphs.
        public HashMap<Integer, Glyph> glyphs;
    }

    public static final class Font_Log {
        public long offset;
        public String table;
        public String error;
        public String category;
    };

    public static final class Ref<T> {
        public T val;
        public Ref(T v)
        {
            val = v;
        }
    }

    public static Font parse(ByteBuffer buffer, ArrayList<Font_Log> logs)
    {
        //Taken from the original C# implementation TODO!!!
        //Refer to https://developer.apple.com/fonts/TrueType-Reference-Manual/RM06/Chap6.html for specification

        Font out = new Font();
        out.glyphs = new HashMap<Integer, Glyph>();
        Ref<Integer> offset = new Ref<Integer>(0);

        final class Table_Info {
            String tag;
            long checksum;
            long offset;
            long length;
        };

        // Read table offsets from header
        long scaler_type = read_u32(buffer, offset);
        var table_infos = new HashMap<String, Table_Info>();
        {
            //if is not 'true' (as uint big endian) or 0x00010000 then is not an official TrueType file
            if(scaler_type != 0x74727565 && scaler_type != 0x00010000)
                font_log_into_list(logs, "warn", offset.val, "header", STR."Invalid scaler type \{Integer.toHexString((int) scaler_type)}");

            long numTables = read_u16(buffer, offset);
            if(numTables > 200)
                font_log_into_list(logs, "warn", offset.val, "header", STR."Suspiciosly many tables \{numTables} tables.");
            offset.val += 6; // unused: searchRange, entrySelector, rangeShift

            for (int i = 0; i < numTables; i++)
            {
                Table_Info info = new Table_Info();
                info.tag = read_string(buffer, 4, offset);
                info.checksum = read_u32(buffer, offset);
                info.offset = read_u32(buffer, offset);
                info.length = read_u32(buffer, offset);

                font_log_into_list(logs, "info", offset.val, "header", STR."Adding table with tag '\{info.tag}''");

                if(info.offset + info.length > buffer.remaining())
                    font_log_into_list(logs, "error", offset.val, "header", STR."Tables with tag '\{info.tag}' has invalid offset '\{info.offset}' or length '\{info.length}'. Skipping.");
                else
                {
                    Table_Info old = table_infos.put(info.tag, info);
                    if(old != null)
                        font_log_into_list(logs, "warn", offset.val, "header", STR."Duplicit tables with tag '\{info.tag}'");
                }
            }

            //Check if all required tables are present
            String[] required_table_infos = {"glyf", "loca", "cmap", "head", "maxp", "hmtx"};
            boolean failed = false;
            for(int i = 0; i < required_table_infos.length; i++)
                if(table_infos.get(required_table_infos[i]) == null)
                {
                    font_log_into_list(logs, "fatal", offset.val, "header", STR."Couldnt find required table '\{required_table_infos[i]}'!");
                    failed = true; //dont exit immedietely instead finish the iteration (reporting more errors then leave)
                }

            if(failed)
                return out;
        }

        Table_Info name_table = table_infos.get("name");
        if(name_table != null)
        {
            offset.val = (int) name_table.offset;
            offset.val += 2;
            int name_entries = read_u16(buffer, offset);
            int string_offset = read_u16(buffer, offset);

            if(name_entries > 100)
                font_log_into_list(logs, "warn", offset.val, "header", STR."Suspiciosly many name entries \{name_entries} tables.");

            Ref<Integer> string_curr_offset = new Ref<>(0);
            for (int i = 0; i < name_entries; i++) {
                int platform_id = read_u16(buffer, offset);
                int platform_specific_id = read_u16(buffer, offset);
                int language_id = read_u16(buffer, offset);
                int name_id = read_u16(buffer, offset);
                int length = read_u16(buffer, offset);
                int offset_from_string_offset = read_u16(buffer, offset);

                string_curr_offset.val = (int)(name_table.offset + string_offset + offset_from_string_offset);
                String read = read_utf16BE(buffer, length, string_curr_offset);

                String[] name_id_to_name = {
                    "Copyright notice",
                    "Font Family",
                    "Font Subfamily",
                    "Unique subfamily",
                    "Full name",
                    "Version",
                    "PostScript name",
                    "Trademark notice",
                    "Manufacturer name",
                    "Designer",
                    "Description",
                    "vendor URL",
                    "designer URL",
                    "License description",
                    "License URL",
                };
                String name_id_string = name_id < name_id_to_name.length ? name_id_to_name[name_id] : "";
                font_log_into_list(logs, "info", string_curr_offset.val, "name", STR."Read name id \{name_id} \{name_id_string}: '\{read}'");

                //TODO: validity checking
                if(name_id == 0) out.copyright = read;
                if(name_id == 1) out.family = read;
                if(name_id == 2) out.subfamily = read;
                if(name_id == 4) out.name = read;
                if(name_id == 8) out.manufacturer_name = read;
                if(name_id == 9) out.designer_name = read;
                if(name_id == 10) out.description = read;
                if(name_id == 11) out.vendor_url = read;
                if(name_id == 12) out.designer_url = read;
                if(name_id == 13) out.license = read;
                if(name_id == 14) out.license_url = read;
            }
        }

        //TODO: bhed instead of head if does not have outlines!
        //Read global font info from 'head' table
        int num_bytes_per_loc = 4;
        {
            offset.val = (int) table_infos.get("head").offset;
            offset.val += 12; //skip version, fontRevision, magicNumber
            long head_magic = read_u32(buffer, offset);
            if(head_magic != 0x5F0F3CF5)
                font_log_into_list(logs, "warn", offset.val, "head", STR."Magic number is not correct \{Integer.toHexString((int) head_magic)}");

            offset.val += 2; //skip flags
            int units_per_em = read_u16(buffer, offset);
            if(64 > units_per_em || units_per_em > 16384)
                font_log_into_list(logs, "warn", offset.val, "head", STR."Units per EM outside of their inteded range \{units_per_em}");

            long SECONDS_BETWEEN_1904_AND_1970 = 2_082_844_800;
            long created_time = read_i64(buffer, offset);
            long modified_time = read_i64(buffer, offset);
            offset.val += 14; //skip many things

            // Number of bytes used by the offsets in the 'loca' table (for looking up glyph locations)
            int num_bytes_per_loc_signal = read_u16(buffer, offset);
            if(num_bytes_per_loc_signal == 0)
                num_bytes_per_loc = 2;
            else if(num_bytes_per_loc_signal == 1)
                num_bytes_per_loc = 4;
            else
                font_log_into_list(logs, "error", offset.val, "head", STR."Strange number signaling bytes per location \{num_bytes_per_loc}. Using 4.");

            out.created_time = Instant.ofEpochSecond(created_time - SECONDS_BETWEEN_1904_AND_1970);
            out.modified_time = Instant.ofEpochSecond(modified_time - SECONDS_BETWEEN_1904_AND_1970);
            out.units_per_em = units_per_em;
            font_log_into_list(logs, "info", offset.val, "head", STR."Read units_per_em: \{out.units_per_em}");
            font_log_into_list(logs, "info", offset.val, "head", STR."Read created_time: \{out.created_time}");
            font_log_into_list(logs, "info", offset.val, "head", STR."Read modified_time: \{out.modified_time}");
        }

        //Read number of glyps from 'maxp' table
        int num_glyphs = 0;
        {
            offset.val = (int) table_infos.get("maxp").offset;
            long maxp_version = read_u32(buffer, offset);
            if(maxp_version != 0x00010000 && maxp_version != 0x00005000)
                font_log_into_list(logs, "warn", offset.val, "maxp", STR."Unexpected maxp version \{Integer.toHexString((int) maxp_version)}");

            num_glyphs = read_u16(buffer, offset);
            font_log_into_list(logs, "info", offset.val, "head", STR."Read num_glyphs: \{num_glyphs}");
        }

        //Read glyph locations from 'loca' table
        long[] glyph_locations = new long[num_glyphs];
        {
            int loca_offset = (int) table_infos.get("loca").offset;
            int glyf_offset = (int) table_infos.get("glyf").offset;

            for (int glyphIndex = 0; glyphIndex < num_glyphs; glyphIndex++)
            {
                offset.val = loca_offset + glyphIndex * num_bytes_per_loc;

                // If 2-byte format is used, the stored location is half of actual location (so multiply by 2)
                long glyphOffset = num_bytes_per_loc == 2 ? (long) read_u16(buffer, offset)*2 : read_u32(buffer, offset);
                glyph_locations[glyphIndex] = glyf_offset + glyphOffset;
            }
        }

        class Glyph_Map {
            long glyph_index;
            long unicode_index;
        }
        ArrayList<Glyph_Map> glyph_pairs = new ArrayList<Glyph_Map>();
        //Create unicode to glyph index mapping
        {
            offset.val = (int) table_infos.get("cmap").offset;

            int cmap_version = read_u16(buffer, offset);
            if(cmap_version != 0)
                font_log_into_list(logs, "warn", offset.val, "cmap", STR."Unexpected cmap version \{Integer.toHexString((int) cmap_version)}");
            int numSubtables = read_u16(buffer, offset); // font can contain multiple character maps for different platforms
            if(numSubtables > 50)
                font_log_into_list(logs, "warn", offset.val, "cmap", STR."Unexpectedly high number of subtables \{numSubtables}");

            // --- Read through metadata for each character map to find the one we want to use ---
            long cmapSubtableOffset = 0;
            int selectedUnicodeVersionID = -1;

            for (int i = 0; i < numSubtables; i++)
            {
                int platformID = read_u16(buffer, offset);
                int platformSpecificID = read_u16(buffer, offset);
                long subtable_offset = read_u32(buffer, offset);

                font_log_into_list(logs, "info", offset.val, "cmap", STR."Found character map with id \{platformID} and platform specific id \{platformSpecificID}");
                // Unicode encoding
                if (platformID == 0)
                {
                    // Use highest supported unicode version
                    if (0 <= platformSpecificID && platformSpecificID <= 4 && platformSpecificID > selectedUnicodeVersionID)
                    {
                        cmapSubtableOffset = subtable_offset;
                        selectedUnicodeVersionID = platformSpecificID;
                    }
                }
                // Microsoft Encoding
                else if (platformID == 3 && selectedUnicodeVersionID == -1)
                {
                    if (platformSpecificID == 1 || platformSpecificID == 10)
                        cmapSubtableOffset = subtable_offset;
                }
            }

            if (cmapSubtableOffset == 0)
            {
                font_log_into_list(logs, "fatal", offset.val, "cmap", STR."Font does not contain supported character map type!");
                return out;
            }

            // Go to the character map
            offset.val = (int) (table_infos.get("cmap").offset + cmapSubtableOffset); 
            int format = read_u16(buffer, offset);
            boolean hasReadMissingCharGlyph = false;
            
            // ---- Parse Format 4 ----
            if (format == 4)
            {
                int length = read_u16(buffer, offset);
                int languageCode = read_u16(buffer, offset);
                // Number of contiguous segments of character codes
                int segCount2X = read_u16(buffer, offset);
                int segCount = segCount2X / 2;
                offset.val += 6; // Skip: searchRange, entrySelector, rangeShift

                // Ending character code for each segment (last = 2^16 - 1)
                int[] endCodes = new int[segCount];
                for (int i = 0; i < segCount; i++)
                    endCodes[i] = read_u16(buffer, offset);

                int reserved_pad = read_u16(buffer, offset);
                if(reserved_pad != 0)
                    font_log_into_list(logs, "warn", offset.val, "cmap", STR."Reserved pad is not 0. Pad: \{reserved_pad}");

                int[] startCodes = new int[segCount];
                for (int i = 0; i < segCount; i++)
                    startCodes[i] = read_u16(buffer, offset);

                int[] idDeltas = new int[segCount];
                for (int i = 0; i < segCount; i++)
                    idDeltas[i] = read_u16(buffer, offset);

                int[] id_ranges_offset = new int[segCount];
                int[] id_ranges_read_loc = new int[segCount];
                for (int i = 0; i < segCount; i++)
                {
                    id_ranges_read_loc[i] = offset.val;
                    id_ranges_offset[i] = read_u16(buffer, offset);
                }

                for (int i = 0; i < startCodes.length; i++)
                {
                    int endCode = endCodes[i];
                    int currCode = startCodes[i];

                    if (currCode == 65535) break; // not sure about this (hack to avoid out of bounds on a specific font)

                    for(; currCode <= endCode; currCode++)
                    {
                        int glyphIndex;
                        // If idRangeOffset is 0, the glyph index can be calculated directly
                        if (id_ranges_offset[i] == 0)
                            glyphIndex = (currCode + idDeltas[i]) % 65536;
                        // Otherwise, glyph index needs to be looked up from an array
                        else
                        {
                            int old_offset = offset.val;
                            int range_offset_loc = id_ranges_read_loc[i] + id_ranges_offset[i];
                            int glyph_index_array_loc = 2 * (currCode - startCodes[i]) + range_offset_loc;

                            offset.val = glyph_index_array_loc;
                            glyphIndex = read_u16(buffer, offset);

                            if (glyphIndex != 0)
                                glyphIndex = (glyphIndex + idDeltas[i]) % 65536;

                            offset.val = old_offset;
                        }

                        var map = new Glyph_Map();
                        map.glyph_index = glyphIndex;
                        map.unicode_index = currCode;
                        glyph_pairs.add(map);
                        font_log_into_list(logs, "debug", offset.val, "cmap", STR."Found glyph map '\{Character.toString((int) map.unicode_index)}' U\{Integer.toHexString((int) map.unicode_index)} -> \{map.glyph_index}");
                        hasReadMissingCharGlyph = hasReadMissingCharGlyph || glyphIndex == 0;
                    }
                }
            }
            // ---- Parse Format 12 ----
            else if (format == 12)
            {
                offset.val += 10; // Skip: reserved, subtableByteLengthInlcudingHeader, languageCode
                long numGroups = read_u32(buffer, offset);

                for (int i = 0; i < numGroups; i++)
                {
                    long startCharCode = read_u32(buffer, offset);
                    long endCharCode = read_u32(buffer, offset);
                    long startGlyphIndex = read_u32(buffer, offset);

                    long numChars = endCharCode - startCharCode + 1;
                    for (int charCodeOffset = 0; charCodeOffset < numChars; charCodeOffset++)
                    {
                        long charCode = (startCharCode + charCodeOffset);
                        long glyphIndex = (startGlyphIndex + charCodeOffset);

                        var map = new Glyph_Map();
                        map.glyph_index = glyphIndex;
                        map.unicode_index = charCode;
                        glyph_pairs.add(map);

                        hasReadMissingCharGlyph |= glyphIndex == 0;
                    }
                }
            }
            else
            {
                font_log_into_list(logs, "fatal", offset.val, "cmap", STR."Font cmap format not supported: \{format}!");
                return out;
            }

            if (!hasReadMissingCharGlyph)
            {
                var map = new Glyph_Map();
                map.glyph_index = 0;
                map.unicode_index = 65535;
                glyph_pairs.add(map);
            }
        }

        //Read all glyphs
        Glyph_Map[] glyph_pairs0 = glyph_pairs.toArray(new Glyph_Map[0]);
        Glyph[] glyphs = new Glyph[glyph_pairs0.length];
        {
            for (int i = 0; i < glyph_pairs0.length; i++)
            {
                Glyph_Map mapping = glyph_pairs0[i];
                Glyph glyph = read_glyph_data(buffer, offset, glyph_locations, (int) mapping.glyph_index, (int) mapping.unicode_index, logs, 0);
                glyph.unicode = (int) mapping.unicode_index;
                glyphs[i] = glyph;
            }
        }

        //Layout data from '' and '' tables
        {
            int[] glyphs_advance = new int[num_glyphs];
            int[] glyphs_left = new int[num_glyphs];

            // Get number of metrics from the 'hhea' table
            offset.val = (int) table_infos.get("hhea").offset;
            offset.val += 8; // unused: version, ascent, descent
            int lineGap = read_i16(buffer, offset);
            int advance_widthMax = read_i16(buffer, offset);
            offset.val += 22; // unused: minleft_side_bearing, minRightSideBearing, xMaxExtent, caretSlope/Offset, reserved, metricDataFormat
            int metrics_count = read_i16(buffer, offset);

            // Get the advance width and left_side_bearing metrics from the 'hmtx' table
            offset.val = (int) table_infos.get("hmtx").offset;
            int last_advance_width = 0;

            for (int i = 0; i < metrics_count; i++)
            {
                int advance_width = read_u16(buffer, offset);
                int left_side_bearing = read_i16(buffer, offset);
                last_advance_width = advance_width;

                glyphs_advance[i] = advance_width;
                glyphs_left[i] = left_side_bearing;
            }

            // Some fonts have a run of monospace characters at the end
            int numRem = num_glyphs - metrics_count;
            for (int i = 0; i < numRem; i++)
            {
                int left_side_bearing = read_i16(buffer, offset);
                int glyphIndex = metrics_count + i;

                glyphs_advance[glyphIndex] = last_advance_width;
                glyphs_left[glyphIndex] = left_side_bearing;
            }

            // Apply
            for(Glyph glyph : glyphs)
            {
                glyph.advance_width = glyphs_advance[glyph.index];
                glyph.left_side_bearing = glyphs_left[glyph.index];
            }
        }

        for(Glyph glyph : glyphs)
        {
            out.glyphs.put(glyph.unicode, glyph);

            if(glyph.index == 0)
                out.missing_glyph = glyph;
        }

        return out;
    }

    public static Glyph read_glyph_data(ByteBuffer buffer, Ref<Integer> offset, long[] glyph_locations, int glyph_index, int unicode_index, ArrayList<Font_Log> logs, int recursion_depth)
    {
        Glyph glyph = new Glyph();
        glyph.index = glyph_index;
        if(recursion_depth > 200)
        {
            font_log_into_list(logs, "error", offset.val, "glyf", STR."Recursive compound glyph on inde \{glyph_index} and unicode \{Integer.toHexString(unicode_index)} too deep! Recursion steps \{recursion_depth}");
            return glyph;
        }

        long glyph_location = glyph_locations[glyph_index];
        offset.val = (int) glyph_location;

        int contourCount = read_i16(buffer, offset);

        // Glyph is either simple or compound
        // * Simple: outline data is stored here directly
        // * Compound: two or more simple glyphs need to be looked up, transformed, and combined
        boolean isSimpleGlyph = contourCount >= 0;
        if (isSimpleGlyph)
        {
            int BIT_ON_CURVE      = 1 << 0;
            int BIT_SINGLE_BYTE_X = 1 << 1;
            int BIT_SINGLE_BYTE_Y = 1 << 2;
            int BIT_REPEAT        = 1 << 3;
            int BIT_SINGLE_INSTRUCTION_X = 1 << 4;
            int BIT_SINGLE_INSTRUCTION_Y = 1 << 5;

            glyph.min_x = read_i16(buffer, offset);
            glyph.min_y = read_i16(buffer, offset);
            glyph.max_x = read_i16(buffer, offset);
            glyph.max_y = read_i16(buffer, offset);

            // Read contour ends
            int numPoints = 0;
            glyph.contour_end_indices = new int[contourCount];
            for (int i = 0; i < contourCount; i++)
            {
                int contourEndIndex = read_u16(buffer, offset);
                numPoints = Math.max(numPoints, contourEndIndex + 1);
                glyph.contour_end_indices[i] = contourEndIndex;
            }

            if(glyph.contour_end_indices.length == 0)
                font_log_into_list(logs, "error", offset.val, "glyf", STR."No countour end indeces! Glyph index \{glyph_index} Unicode index \{unicode_index}");

            if(numPoints <= 0)
                font_log_into_list(logs, "warn", offset.val, "glyf", STR."Strange number of points \{numPoints} Glyph index \{glyph_index} Unicode index \{unicode_index}");

            int instructionsLength = read_u16(buffer, offset);
            offset.val += instructionsLength; // skip instructions (hinting stuff)

            //Read flags
            byte[] allFlags = new byte[numPoints];
            glyph.points_x = new int[numPoints];
            glyph.points_y = new int[numPoints];
            glyph.points_on_curve = new boolean[numPoints];
            for (int i = 0; i < numPoints; i++)
            {
                int flag = read_i8(buffer, offset);
                allFlags[i] = (byte) flag;

                if ((flag & BIT_REPEAT) > 0)
                {
                    int repeatCount = read_i8(buffer, offset);
                    if(i + repeatCount > numPoints)
                    {
                        font_log_into_list(logs, "error", offset.val, "glyf", STR."Glyph flag repeats too many times! Repeat: \{repeatCount} Glyph index \{glyph_index} Unicode index \{unicode_index}");
                        repeatCount = numPoints - i;
                    }

                    for (int r = 0; r < repeatCount; r++)
                    {
                        i++;
                        allFlags[i] = (byte) flag;
                    }
                }
            }

            for(int k = 0; k < 2; k++)
            {
                boolean readingX = k == 0;
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;

                int BIT_SINGLE_BYTE = readingX ? BIT_SINGLE_BYTE_X : BIT_SINGLE_BYTE_Y;
                int BIT_SAME_COORD_OR_SIGN = readingX ? BIT_SINGLE_INSTRUCTION_X : BIT_SINGLE_INSTRUCTION_Y;

//                int prev_val = 0;
                int coord = 0;
//                int countour_index = 0;
                for (int i = 0; i < numPoints; i++)
                {
                    byte flag = allFlags[i];

                    int coord_offset = 0;
                    // Offset value is represented with 1 byte (unsigned)
                    // Here the instruction flag tells us whether to add or subtract the offset
                    if ((flag & BIT_SINGLE_BYTE) > 0)
                    {
                        coord_offset = read_u8(buffer, offset);
                        boolean negate = (flag & BIT_SAME_COORD_OR_SIGN) > 0;
                        coord_offset = negate ? coord_offset : -coord_offset;
                    }
                    // Offset value is represented with 2 bytes (signed)
                    // Here the instruction flag tells us whether an offset value exists or not
                    else if ((flag & BIT_SAME_COORD_OR_SIGN) == 0)
                    {
                        coord_offset = read_i16(buffer, offset);;
                    }
                    //Else is the same
                    else
                    {
                        coord_offset = 0;
                    }

                    coord += coord_offset;
//                    prev_val = coord_offset;
                    if (readingX)
                        glyph.points_x[i] = coord;
                    else
                        glyph.points_y[i] = coord;

                    glyph.points_on_curve[i] = (flag & BIT_ON_CURVE) > 0;

                    min = Math.min(min, coord);
                    max = Math.max(max, coord);

//                    int countour_end = glyph.contour_end_indices.length > countour_index ? glyph.contour_end_indices[countour_index] : Integer.MAX_VALUE;
//                    if(i == countour_end)
//                    {
//                        coord = 0;
//                        countour_index += 1;
//                    }
                }

                if(numPoints != 0)
                {
                    if (readingX)
                    {
                        glyph.computed_min_x = min;
                        glyph.computed_max_x = max;
                    }
                    else
                    {
                        glyph.computed_min_y = min;
                        glyph.computed_max_y = max;
                    }
                }
            }

            return glyph;
        }
        else
        {
            Glyph compound = new Glyph();
            glyph.min_x = read_i16(buffer, offset);
            glyph.min_y = read_i16(buffer, offset);
            glyph.max_x = read_i16(buffer, offset);
            glyph.max_y = read_i16(buffer, offset);

            boolean has_more_glyphs = true;
            while (has_more_glyphs)
            {
                int flags = read_u16(buffer, offset);
                int part_glyph_index = read_u16(buffer, offset);

                long primitive_location = glyph_locations[part_glyph_index];
                // If compound glyph refers to itself, return empty glyph to avoid infinite loop.
                // Had an issue with this on the 'carriage return' character in robotoslab.
                // There's likely a bug in my parsing somewhere, but this is my work-around for now...
                if (primitive_location == glyph_location)
                {
                    font_log_into_list(logs, "error", offset.val, "glyf", STR."Self referntial compound glyph! Glyph index \{glyph_index} Unicode index \{unicode_index}");
                    break;
                }

                // Decode flags
                boolean argsAre2Bytes =             (flags & (1 << 0)) > 0;
                boolean argsAreXYValues =           (flags & (1 << 1)) > 0;
                boolean roundXYToGrid =             (flags & (1 << 2)) > 0;
                boolean isSingleScaleValue =        (flags & (1 << 3)) > 0;
                has_more_glyphs =                   (flags & (1 << 5)) > 0;
                boolean isXAndYScale =              (flags & (1 << 6)) > 0;
                boolean is2x2Matrix =               (flags & (1 << 7)) > 0;
                boolean hasInstructions =           (flags & (1 << 8)) > 0;
                boolean useThisComponentMetrics =   (flags & (1 << 9)) > 0;
                boolean componentsOverlap =         (flags & (1 << 10)) > 0;

                // Read args (these are either x/y offsets, or point number)
                int arg1 = argsAre2Bytes ? read_i16(buffer, offset) : read_i8(buffer, offset);
                int arg2 = argsAre2Bytes ? read_i16(buffer, offset) : read_i8(buffer, offset);

                if (!argsAreXYValues)
                    font_log_into_list(logs, "error", offset.val, "glyf", STR."TODO: Args1&2 are point indices to be matched, rather than offsets Glyph index \{glyph_index} Unicode index \{unicode_index}");

                //Read transforms
                double offsetX = arg1;
                double offsetY = arg2;

                double iHat_x = 1;
                double iHat_y = 0;
                double jHat_x = 0;
                double jHat_y = 1;

                if (isSingleScaleValue)
                {
                    iHat_x = u2dot14_to_double(read_u16(buffer, offset));
                    jHat_y = iHat_x;
                }
                else if (isXAndYScale)
                {
                    iHat_x = u2dot14_to_double(read_u16(buffer, offset));
                    jHat_y = u2dot14_to_double(read_u16(buffer, offset));
                }
                // TODO: incomplete implemntation
                else if (is2x2Matrix)
                {
                    iHat_x = u2dot14_to_double(read_u16(buffer, offset));
                    iHat_y = u2dot14_to_double(read_u16(buffer, offset));
                    jHat_x = u2dot14_to_double(read_u16(buffer, offset));
                    jHat_y = u2dot14_to_double(read_u16(buffer, offset));
                }

                //Read the primitive glyph
                int loc = offset.val;
                Glyph primitive = read_glyph_data(buffer, offset, glyph_locations, part_glyph_index, -1, logs, recursion_depth + 1);
                offset.val = loc;

                //transform its points
                for (int i = 0; i < primitive.points_x.length; i++)
                {
                    long x = primitive.points_x[i];
                    long y = primitive.points_y[i];
                    double xPrime = iHat_x*x + jHat_x*y + offsetX;
                    double yPrime = iHat_y*x + jHat_y*y + offsetY;

                    primitive.points_x[i] = (int) xPrime;
                    primitive.points_y[i] = (int) yPrime;
                }

                //Add the primitive to the compound (if is the first one steal its data)
                if(compound.points_x == null)
                {
                    compound.points_x = primitive.points_x;
                    compound.points_y = primitive.points_y;
                    compound.contour_end_indices = primitive.contour_end_indices;
                    compound.points_on_curve = primitive.points_on_curve;
                }
                else
                {
                    //Offset the end indeces
                    for (int i = 0; i < primitive.contour_end_indices.length; i++)
                        primitive.contour_end_indices[i] += compound.points_x.length;

                    compound.points_x = array_concat(compound.points_x, primitive.points_x);
                    compound.points_y = array_concat(compound.points_y, primitive.points_y);
                    compound.points_on_curve = array_concat(compound.points_on_curve, primitive.points_on_curve);
                    compound.contour_end_indices = array_concat(compound.contour_end_indices, primitive.contour_end_indices);
                }
            }

            return compound;
        }
    }

    public static Font parse_load(String path, ArrayList<Font_Log> logs)
    {
        var exc = new Ref<Exception>(null);
        byte[] bytes = read_entire_file(path, exc);
        if(exc.val != null)
        {
            font_log_into_list(logs, "error", 0, "file", STR."Cannot load font file '\{path}' error '\{exc.val.toString()}'");
            return null;
        }
        return parse(ByteBuffer.wrap(bytes), logs);
    }

    public static <T> T[] array_concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    public static boolean[] array_concat(boolean[] a, boolean[] b)
    {
        boolean[] concat = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, concat, a.length, b.length);
        return concat;
    }
    public static int[] array_concat(int[] a, int[] b)
    {
        int[] concat = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, concat, a.length, b.length);
        return concat;
    }
    public static long[] array_concat(long[] a, long[] b)
    {
        long[] concat = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, concat, a.length, b.length);
        return concat;
    }


    static public long read_i64(ByteBuffer buffer, Ref<Integer> offset)
    {
        long out = buffer.getLong(offset.val);
        offset.val += 8;
        return out;
    }

    static public long read_u32(ByteBuffer buffer, Ref<Integer> offset)
    {
        long out = Integer.toUnsignedLong(buffer.getInt(offset.val));
        offset.val += 4;
        return out;
    }

    static public int read_i32(ByteBuffer buffer, Ref<Integer> offset)
    {
        int out = buffer.getInt(offset.val);
        offset.val += 4;
        return out;
    }

    static public int read_u16(ByteBuffer buffer, Ref<Integer> offset)
    {
        int out = Short.toUnsignedInt(buffer.getShort(offset.val));
        offset.val += 2;
        return out;
    }

    static public short read_i16(ByteBuffer buffer, Ref<Integer> offset)
    {
        short out = buffer.getShort(offset.val);
        offset.val += 2;
        return out;
    }

    static public short read_u8(ByteBuffer buffer, Ref<Integer> offset)
    {
//        short out = (short) Byte.toUnsignedInt(buffer.get(offset.val));
        short out = (short) buffer.get(offset.val);
        if(out < 0)
            out += 256;
        offset.val += 1;
        return out;
    }

    static public byte read_i8(ByteBuffer buffer, Ref<Integer> offset)
    {
        byte out = buffer.get(offset.val);
        offset.val += 1;
        return out;
    }

    static public double u2dot14_to_double(int val)
    {
        return (double) val / (double)(1 << 14);
    }

    static public String read_string(ByteBuffer buffer, int length, Ref<Integer> offset)
    {
        ByteBuffer slice = buffer.slice(offset.val, length);
        offset.val += length;
        return StandardCharsets.UTF_8.decode(slice).toString();
    }

    static public String read_utf16BE(ByteBuffer buffer, int length, Ref<Integer> offset)
    {
        ByteBuffer slice = buffer.slice(offset.val, length);
        offset.val += length;
        return StandardCharsets.UTF_16BE.decode(slice).toString();
    }

    public static void font_log_into_list(ArrayList<Font_Log> logs, String category, long offset, String table, String error)
    {
        System.out.println(STR."FONT_PARSE \{category}: [\{table}] \{error} offset \{Long.toHexString(offset)}");
        if(logs != null)
        {
            Font_Log log = new Font_Log();
            log.category = category;
            log.offset = offset;
            log.table = table;
            log.error = error;
            logs.add(log);
        }
    }

    public static byte[] read_entire_file(String path, Ref<Exception> error_or_null)
    {
        byte[] fileContent = new byte[0];
        try {
            fileContent = Files.readAllBytes(new File(path).toPath());
        }
        catch(Exception e){
            if(error_or_null != null)
                error_or_null.val = e;
        }
        return fileContent;
    }

}
