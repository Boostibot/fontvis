import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

import java.lang.Math;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import org.joml.*;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

//tomorrow:
// fix that one bug
// basic typing
// instructions on screen (seperate buffer)
// background (line) shader

public class Main {
    int window_width = 1200;
    int window_height = 800;
    int mouseX = 0;
    int mouseY = 0;
    boolean panning = false;
    float zoom_val = 1;
    float rotation = 0;
    float dt = 0;

    static float ZOOM_SPEED_KEY = 2;
    static float ZOOM_SPEED_SCROLL = 2;
    static float CAMERA_SPEED = 2;
    static float SPRINT_CAMERA_SPEED = 10;
    static float ROTATE_SPEED = 1;

    final Vector3f position = new Vector3f(0, 0, 0);
    boolean[] keyDown = new boolean[GLFW_KEY_LAST + 1];

    public static final int KB = 1024;
    public static final int MB = 1024*1024;
    public static final int GB = 1024*1024*1024;

    Render.Quadratic_Bezier_Render render;
    Render.Bezier_Buffer ui_buffer = new Render.Bezier_Buffer(64*MB, false);
    Render.Bezier_Buffer glyph_buffer = new Render.Bezier_Buffer(4*MB, false);
    Outline_Cache outline_cache = new Outline_Cache();

    ArrayList<Font> fonts = new ArrayList<>();

    public static final String on_screen_instructions = """
            arrow keys to move
            hold mouse to pan
            O,P or scroll to zoom
            Q,E to roll camera.
            """;

    public static final class Text_Style
    {
        public static final int FLAG_RAINBOW_FILL = 1;
        public static final int FLAG_RAINBOW_OUTLINE = 2;
        public static final int FLAG_SHOW_SKELETON = 4;
        public static final int FLAG_SHOW_TRIANGLES = 8;
        public static final int FLAG_DANCING = 16;

        public short font_index = 0;
        public short font_gen;

        public float size = 0.05f;
        public int color = 0x00;
        public float outline_width;
        public float outline_sharpness = 0.7f;
        public int outline_color;
        public float spacing_x;
        public float spacing_y;
        public float spacing_scale_x = 1;
        public float spacing_scale_y = 1;

        public int flags;
        public Matrix3f transform;
    }

    public static final Text_Style DEF_TEXT_STYLE = new Text_Style();
    public static final Text_Style UI_TEXT_STYLE = new Text_Style();


    public static int hash(int x) {
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return x;
    }
    public static long hash(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        x = x ^ (x >>> 31);
        return x;
    }

    public static class Outline_Cache_Key
    {
        public int font;
        public int unicode;
        public float width;
        public float sharpness;
        public float epsilon;

        public Outline_Cache_Key() {}
        public void set(int font, int unicode, float width, float sharpness, float epsilon)
        {
            this.font = font;
            this.unicode = unicode;
            this.width = width;
            this.sharpness = sharpness;
            this.epsilon = epsilon;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass())
                return false;

            Outline_Cache_Key that = (Outline_Cache_Key) o;
            return this.unicode == that.unicode
                && this.font == that.font
                && this.width == that.width
                && this.sharpness == that.sharpness
                && this.epsilon == that.epsilon;
        }

        @Override
        public int hashCode() {
            int width = Float.floatToIntBits(this.width);
            int sharpness = Float.floatToIntBits(this.sharpness);
            int epsilon = Float.floatToIntBits(this.epsilon);

            long l1 = (long) unicode << 32 | width;
            long l2 = (long) sharpness << 32 | epsilon;
            return (int) (hash(l1) ^ hash(l2)) ^ hash(font);
        }
    }

    public static class Outline_Cache_Entry
    {
        public Render.Bezier_Buffer buffer;
        public Outline_Cache_Entry next;
        public Outline_Cache_Entry prev;
        Outline_Cache_Key key;
    }

    public class Outline_Cache
    {
        HashMap<Outline_Cache_Key, Outline_Cache_Entry> outlines = new HashMap<>();
        Outline_Cache_Entry first;
        Outline_Cache_Entry last;
        Outline_Cache_Entry freelist;
        Render.Bezier_Buffer staging_buffer = new Render.Bezier_Buffer(64*KB, true);

        long items_count;
        long bytes_count;
        long capacity = 9999999;
        long max_bytes_count;
        long max_items_count;

        //some things so that we dont have to call new everytime
        private final Outline_Cache_Key temp_key = new Outline_Cache_Key();
        private final Buffers.PointArray temp_points = new Buffers.PointArray();
        private final Buffers.PointArray temp_ders = new Buffers.PointArray();
        private final Render.Bezier_Buffer.Line_Connection[] temp_connections = {
                new Render.Bezier_Buffer.Line_Connection(),
                new Render.Bezier_Buffer.Line_Connection(),
        };

        public Render.Bezier_Buffer get(Outline_Cache_Key key)
        {
            Outline_Cache_Entry entry = outlines.get(key);
            if(entry == null)
            {
                Font font = fonts.get(key.font);
                Kept_Glyph glyph = font.glyphs.get(key.unicode);
                System.out.println(STR."OUTLINE_CACHE info: adding \{Integer.toHexString(key.unicode)} font \{font.fullname} with params width:\{key.width} sharpness:\{key.sharpness} epsilon:\{key.epsilon}");
                if(glyph == null)
                {
                    System.out.println(STR."OUTLINE_CACHE error: font \{font.fullname} doesnt support glyph \{Integer.toHexString(key.unicode)}");
                    return null;
                }

                //rasterize glyph
                staging_buffer.reset();
                float[] xs = glyph.processed.points.xs;
                float[] ys = glyph.processed.points.ys;
                int color = 0x000000;
                for(var shape : glyph.processed.shapes)
                    staging_buffer.submit_bezier_countour(temp_points, temp_ders, temp_connections, xs, ys, shape, key.width, key.sharpness, key.epsilon, color, null);

                //if too much stuff remove untill space
                int bytes = staging_buffer.length*Render.Bezier_Buffer.BYTES_PER_SEGMENT;
                while(bytes + bytes_count > capacity)
                {
                    System.out.println(STR."OUTLINE_CACHE info: cache full evicting \{Integer.toHexString(key.unicode)} font \{font.fullname} with params width:\{key.width} sharpness:\{key.sharpness} epsilon:\{key.epsilon}");
                    if(last == null)
                    {
                        System.out.println(STR."OUTLINE_CACHE error: no space to store even single shape of size \{(double) bytes/KB}KB");
                        return null;
                    }

                    var unlinked = unlink(last);
                    bytes_count -= (long) unlinked.buffer.length*Render.Bezier_Buffer.BYTES_PER_SEGMENT;
                    unlinked.buffer.free_all();

                    outlines.remove(unlinked.key);
                    unlinked.next = freelist;
                    freelist = unlinked;
                }

                //get new empty entry
                if(freelist == null)
                {
                    System.out.println(STR."OUTLINE_CACHE debug: empty entry was created");
                    entry = new Outline_Cache_Entry();
                    entry.key = new Outline_Cache_Key();
                    entry.buffer = new Render.Bezier_Buffer(0, true);
                }
                else
                {
                    System.out.println(STR."OUTLINE_CACHE debug: empty entry was found in freelist");
                    entry = freelist;
                    freelist = entry.next;
                }

                //fill empty entry
                entry.buffer.grows = true;
                entry.key.set(key.font, key.unicode, key.width, key.sharpness, key.epsilon);
                entry.buffer.submit(staging_buffer, 0, staging_buffer.length);
                entry.buffer.grows = false;

                //insert into structures
                link_front(entry);
                outlines.put(entry.key, entry);

                //update stats
                bytes_count += bytes;
                max_bytes_count = Math.max(max_bytes_count, bytes_count);
                max_items_count = Math.max(max_items_count, outlines.size());
            }
            else
            {
                Font font = fonts.get(key.font);
                //System.out.println(STR."OUTLINE_CACHE info: found in cache \{Integer.toHexString(key.unicode)} font \{font.fullname} with params width:\{key.width} sharpness:\{key.sharpness} epsilon:\{key.epsilon}");

                //reinsert it as recently used
                unlink(entry);
                link_front(entry);
            }

            return entry.buffer;
        }

        private Outline_Cache_Entry link_front(Outline_Cache_Entry entry)
        {
            entry.next = first;
            if(first != null)
                first.prev = entry;
            first = entry;
            if(last == null)
                last = entry;

            return entry;
        }

        private Outline_Cache_Entry unlink(Outline_Cache_Entry entry)
        {
            assert entry != null;
            if(first == entry)
                first = entry.next;
            if(last == entry)
                last = entry.prev;

            if(entry.next != null)
                entry.next.prev = entry.prev;

            if(entry.prev != null)
                entry.prev.next = entry.next;

            entry.prev = null;
            entry.next = null;
            return entry;
        }

        public Render.Bezier_Buffer get(int font, int unicode, float width, float sharpness, float epsilon)
        {
            temp_key.font = font;
            temp_key.unicode = unicode;
            temp_key.width = width;
            temp_key.sharpness = sharpness;
            temp_key.epsilon = epsilon;
            return get(temp_key);
        }
    }

    public static class Font
    {
        public String fullname;
        public String name;
        public String family;
        public String subfamily;

        Kept_Glyph missing_glyph = new Kept_Glyph();
        HashMap<Integer, Kept_Glyph> glyphs = new HashMap<>();
        Render.Bezier_Buffer glyph_buffer = new Render.Bezier_Buffer(64*KB, true);

        Matrix3f temp_matrix = new Matrix3f();
    }

    public Text_Style temp_text_style = new Text_Style();
    public boolean submit_styled_text(float x, float y, float max_x, float max_y, Render.Bezier_Buffer buffer, CharSequence text, Text_Style style, Matrix3f transform_or_null)
    {
        if(style == null)
            style = DEF_TEXT_STYLE;

        if(style.font_index < 0 || style.font_index > fonts.size())
            return false;

        Font font = fonts.get(style.font_index);

        float curr_x = 0;
        int k_x = 0;
        int k_y = 0; //TODO
        for(int c : text.codePoints().toArray()){
            Kept_Glyph glyph = font.glyphs.getOrDefault(c, font.missing_glyph);
            if(glyph == font.missing_glyph)
                System.err.println(STR."Couldnt render unicode character '\{Character.toChars(c)}' using not found glyph");

            font.temp_matrix.identity();
            font.temp_matrix.m20 = k_x*style.spacing_x + curr_x + glyph.processed.left_side_bearing*style.spacing_scale_x;
            font.temp_matrix.scale(style.size);
            if(transform_or_null != null)
                font.temp_matrix.mulLocal(transform_or_null);

            buffer.submit(font.glyph_buffer, glyph.triangulated_from, glyph.triangulated_to, font.temp_matrix, true, style.color);
            if(style.outline_width > 0)
            {
                var outline = outline_cache.get(style.font_index, c, style.outline_width, style.outline_sharpness, 0.001f);
                if(outline != null)
                    buffer.submit(outline, font.temp_matrix, true, style.outline_color);
            }
            curr_x += glyph.processed.advance_width*style.spacing_scale_x;
            k_x += 1;
        }

        return true;
    }

    public static class Processed_Glyph
    {
        public int solids_count;
        public int holes_count;
        public Buffers.PointArray points;
        public Buffers.IndexBuffer[] shapes;
        public boolean[] are_solid;
        public Triangulate.AABB_Vertices aabb;

        public int unicode;
        public float advance_width;
        public float left_side_bearing;
    }

    //TODO make this more optimized - allocate just once etc. ...
    public static Processed_Glyph preprocess_glyph(Font_Parser.Glyph glyph, int units_per_em)
    {
        Buffers.IndexBuffer[] shapes = new Buffers.IndexBuffer[glyph.contour_ends.length];
        boolean[] are_solid = new boolean[glyph.contour_ends.length];
        int[] contour_ends = glyph.contour_ends;
        Buffers.PointArray processed = new Buffers.PointArray();
        processed.reserve(2*glyph.xs.length + 4);

        //find maximum length segment
        int max_segment = 0;
        for(int k = 0; k < contour_ends.length; k++)
        {
            int start_i = k == 0 ? 0 : contour_ends[k-1];
            int end_i = contour_ends[k];
            max_segment = Math.max(max_segment, end_i - start_i);
        }

        //allocate temporary storage
        Buffers.PointArray with_implied_points = new Buffers.PointArray();
        Buffers.BoolArray with_implied_on_curve = new Buffers.BoolArray();
        with_implied_points.reserve(max_segment*5/4 + 4);
        with_implied_on_curve.reserve(max_segment*5/4 + 4);

        //process into contours
        int solid_count = 0;
        int hole_count = 0;
        for(int k = 0; k < contour_ends.length; k++)
        {
            int start_i = k == 0 ? 0 : contour_ends[k-1];
            int end_i = contour_ends[k];

            int input_points = end_i - start_i;
            if(input_points <= 0)
                continue;

            //calculate signed area in counter-clockwise direction
            // because of this is negative for solids and positive for holes
            long signed_area = 0;
            {
                int j = end_i - 1;
                for(int i = start_i; i < end_i; i++) {
                    signed_area += (long)glyph.xs[i]*glyph.ys[j] - (long)glyph.xs[j]*glyph.ys[i];
                    j = i;
                }
            }

            boolean is_solid = signed_area >= 0;
            if(is_solid)
                solid_count += 1;
            else
                hole_count += 1;

            //add all implied points
            int on_curve_points = 0;
            int implied_points = 0;
            with_implied_points.resize(0);
            with_implied_on_curve.resize(0);
            {
                float units_per_em_float = units_per_em;
                int j = 0;
                for(int i = end_i; i-- > start_i;) {
                    if(glyph.on_curve[j] == false && glyph.on_curve[i] == false)
                    {
                        int mid_x = (glyph.xs[j] + glyph.xs[i])/2;
                        int mid_y = (glyph.ys[j] + glyph.ys[i])/2;
                        with_implied_points.push(mid_x/units_per_em_float, mid_y/units_per_em_float);
                        with_implied_on_curve.push(true);
                        implied_points += 1;
                    }

                    if(glyph.on_curve[i])
                        on_curve_points += 1;

                    with_implied_points.push(glyph.xs[i]/units_per_em_float, glyph.ys[i]/units_per_em_float);
                    with_implied_on_curve.push(glyph.on_curve[i]);
                    j = i;
                }
            }

            int total_points = on_curve_points + implied_points;
            int out_from_i = processed.length;
            int out_to_i = processed.length + 2*total_points;
            assert processed.length % 2 == 0;

            processed.reserve(out_to_i);

            //add all on curve points in reverse order and assume they form linear segments
            for(int i = 0; i < with_implied_points.length; i++) {
                if(with_implied_on_curve.items[i] )
                {
                    processed.push(
                        with_implied_points.xs[i], with_implied_points.ys[i],
                        with_implied_points.xs[i], with_implied_points.ys[i]
                    );
                }
            }
            assert processed.length == out_to_i;
            assert processed.length % 2 == 0;

            //override some connections with bezier curves
            int write_i = 0;
            for(int i = 0; i < with_implied_points.length; i++) {
                if(with_implied_on_curve.items[i])
                    write_i = write_i+1 < total_points ? write_i+1 : 0;
                else
                {
                    assert out_from_i + 2*write_i < out_to_i;
                    processed.xs[out_from_i + 2*write_i] = with_implied_points.xs[i];;
                    processed.ys[out_from_i + 2*write_i] = with_implied_points.ys[i];;
                }
            }

            are_solid[k] = is_solid;
            shapes[k] = Buffers.IndexBuffer.range(out_from_i, out_to_i);
        }

        Processed_Glyph out = new Processed_Glyph();
        out.advance_width = (float) glyph.advance_width/units_per_em;
        out.left_side_bearing = (float) glyph.left_side_bearing/units_per_em;
        out.aabb = Triangulate.aabb_calculate(processed.xs, processed.ys, Buffers.IndexBuffer.til(processed.length));
        out.unicode = glyph.unicode;
        out.points = processed;
        out.are_solid = are_solid;
        out.shapes = shapes;
        out.solids_count = solid_count;
        out.holes_count = hole_count;

        return out;
    }

    public static class Triangulated_Glyph
    {
        public Buffers.IndexBuffer[] connected_solids;
        public Buffers.IndexBuffer triangles;
        public Buffers.IndexBuffer convex_beziers;
        public Buffers.IndexBuffer concave_beziers;
    }

    public static class Kept_Glyph
    {
        public Processed_Glyph processed;
        public Buffers.IndexBuffer[] connected_solids;

        public int triangulated_from;
        public int triangulated_to;
    }

    public static Triangulated_Glyph triangulate_glyph(Processed_Glyph glyph)
    {
        Buffers.IndexBuffer triangles = new Buffers.IndexBuffer();
        Buffers.IndexBuffer convex_beziers = new Buffers.IndexBuffer();
        Buffers.IndexBuffer concave_beziers = new Buffers.IndexBuffer();

        //split into polygonal and bezier part
        Buffers.IndexBuffer[] polygon_holes = new Buffers.IndexBuffer[glyph.holes_count];
        Buffers.IndexBuffer[] polygon_solids = new Buffers.IndexBuffer[glyph.solids_count];
        {
            int solid_count = 0;
            int hole_count = 0;
            for(int k = 0; k < glyph.shapes.length; k++)
            {
                Buffers.IndexBuffer shape = glyph.shapes[k];
                Buffers.IndexBuffer polygon = new Buffers.IndexBuffer();
                polygon.reserve(shape.length/2);
                Triangulate.bezier_contour_classify(polygon, convex_beziers, concave_beziers, glyph.points.xs, glyph.points.ys, shape, (float) 1e-5, true);

                if(glyph.are_solid[k])
                    polygon_solids[solid_count++] = polygon;
                else
                    polygon_holes[hole_count++] = polygon;
            }
        }

        //connect solids with holes
        Buffers.IndexBuffer[] connected_solids = new Buffers.IndexBuffer[glyph.solids_count];
        for(int k = 0; k < glyph.solids_count; k++)
        {
            connected_solids[k] = new Buffers.IndexBuffer();
            Triangulate.connect_holes(connected_solids[k], glyph.points.xs, glyph.points.ys, polygon_solids[k], polygon_holes, null);
        }

        for (Buffers.IndexBuffer connected : connected_solids)
            Triangulate.triangulate(triangles, glyph.points.xs, glyph.points.ys, connected, true);

        Triangulated_Glyph out = new Triangulated_Glyph();
        out.connected_solids = connected_solids;
        out.triangles = triangles;
        out.concave_beziers = concave_beziers;
        out.convex_beziers = convex_beziers;
        return out;
    }

    public static boolean font_load(Font out, CharSequence sequence, ArrayList<Font_Parser.Font_Log> logs)
    {
        Font_Parser.Font font = Font_Parser.parse_load(sequence.toString(), logs);
        if(font == null || font.glyphs == null)
            return false;

        out.family = font.family;
        out.name = font.name;
        out.subfamily = font.subfamily;
        out.fullname = font.name; //for now;

        final long start_time = System.nanoTime();
        for (var entry : font.glyphs.entrySet()) {
            Font_Parser.Glyph glyph = entry.getValue();

            //process and triangulate
            Processed_Glyph processed = preprocess_glyph(glyph, font.units_per_em);
            if(glyph.unicode == "h".codePointAt(0))
                System.out.println("!");
            Triangulated_Glyph triangulated = triangulate_glyph(processed);

            //submit into centralized buffer
            int solids_color = 0x00;
            int convex_flags = Render.Bezier_Buffer.FLAG_BEZIER;
            int concave_flags = Render.Bezier_Buffer.FLAG_BEZIER
                    | Render.Bezier_Buffer.FLAG_INVERSE;

            int triangulated_from = out.glyph_buffer.length;
            out.glyph_buffer.submit_indexed_triangles(processed.points.xs, processed.points.ys, triangulated.triangles, solids_color, 0, null);
            out.glyph_buffer.submit_indexed_triangles(processed.points.xs, processed.points.ys, triangulated.convex_beziers, solids_color, convex_flags, null);
            out.glyph_buffer.submit_indexed_triangles(processed.points.xs, processed.points.ys, triangulated.concave_beziers, solids_color, concave_flags, null);

            //create kept and store
            Kept_Glyph kept = new Kept_Glyph();
            kept.processed = processed;
            kept.connected_solids = triangulated.connected_solids;
            kept.triangulated_from = triangulated_from;
            kept.triangulated_to = out.glyph_buffer.length;
            out.glyphs.put(glyph.unicode, kept);

            //assign missing glyph
            if(glyph.index == 0)
                out.missing_glyph = kept;
        }
        final long end_time = System.nanoTime();
        System.out.println(STR."Processing took \{(end_time-start_time)*1e3} us");
        return true;
    }

    public static final int BUTTON_FILL_COLOR = 0xAAAAAA;
    public static final int BUTTON_OUTLINE_COLOR = 0x999999;
    public static final int BUTTON_TEXT_COLOR = 0x111111;
    public static final float BUTTON_CORNER_RADIUS = 0.05f;
    public static final float BUTTON_OUTLINE_WIDTH = 0.005f;
    public static final Matrix3f BUTTON_TRANSFORM = new Matrix3f().identity().m10(0.1f); //slight sheer
    public boolean do_button(float x, float y, CharSequence text, float width, float height)
    {
        float pad = 0.01f;
        ui_buffer.submit_rounded_rectangle(x, y, width + 2*BUTTON_OUTLINE_WIDTH, height+2*BUTTON_OUTLINE_WIDTH, BUTTON_CORNER_RADIUS, BUTTON_OUTLINE_COLOR, BUTTON_TRANSFORM);
        ui_buffer.submit_rounded_rectangle(x, y, width, height, BUTTON_CORNER_RADIUS, BUTTON_FILL_COLOR, BUTTON_TRANSFORM);

//        submit_styled_text(
//                0, 0,
//                0, 0,
//                ui_buffer, text, UI_TEXT_STYLE, null
//        );

        submit_styled_text(
            x - width/2 + pad, y + height/2 - pad,
            x + width/2 - pad, y - height/2 + pad,
                ui_buffer, text, UI_TEXT_STYLE, BUTTON_TRANSFORM
        );

        float norm_mouse_x = (float) mouseX/window_width - 0.5f;
        float norm_mouse_y = (float) mouseY/window_height - 0.5f;

        if(x - width/2 <= norm_mouse_x && norm_mouse_x <= x + width/2)
            if(y - width/2 <= norm_mouse_y && norm_mouse_y <= y + width/2)
                return true;

        return false;
    }

    private void run() {

        ArrayList<Font_Parser.Font_Log> logs = new ArrayList<>();

        Font loaded = new Font();
        boolean font_load_state = font_load(loaded, "./assets/fonts/Roboto/Roboto-Black.ttf", logs);
        for(var log : logs)
            if(log.category.equals("info") == false && log.category.equals("debug") == false)
                System.out.println(STR."FONT_PARSE \{log.category}: [\{log.table}] \{log.error}");
        if(font_load_state == false)
            throw new IllegalStateException("Unable to Read font file");

        fonts.add(loaded);
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_SAMPLES, 8);

        long window = glfwCreateWindow(window_width, window_height, "graphix", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");


        glfwSetKeyCallback(window, (long window1, int key, int scancode, int action, int mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window1, true);
            } else if (key >= 0 && key <= GLFW_KEY_LAST) {
                keyDown[key] = action == GLFW_PRESS || action == GLFW_REPEAT;
            }
        });
        glfwSetFramebufferSizeCallback(window, (long window3, int w, int h) -> {
            if (w > 0 && h > 0) {
                window_width = w;
                window_height = h;
                glViewport(0, 0, window_width, window_height);
            }
        });
        glfwSetCursorPosCallback(window, (long window_, double xpos, double ypos) -> {
            if (panning) {
                float zoom = (float) Math.exp(zoom_val);
                float deltaX = (float) xpos - mouseX;
                float deltaY = (float) ypos - mouseY;
                position.x += deltaX/ window_width *2/zoom;
                position.y -= deltaY/ window_height *2/zoom;

                //orientation.rotateLocalX(deltaY * 0.01f).rotateLocalY(deltaX * 0.01f);
            }
            mouseX = (int) xpos;
            mouseY = (int) ypos;
        });
        glfwSetMouseButtonCallback(window, (long window_, int button, int action, int mods) -> {
            if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS)
                panning = true;
            else
                panning = false;
        });

        glfwSetScrollCallback(window, (long window_, double deltax, double deltay) -> {
            zoom_val += (float)deltay*ZOOM_SPEED_SCROLL*dt;
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            window_width = pWidth.get(0);
            window_height = pHeight.get(0);
            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - window_width) / 2,
                    (vidmode.height() - window_height) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();
        var debugProc = GLUtil.setupDebugMessageCallback();
;
        render = new Render.Quadratic_Bezier_Render(64*MB);

        glEnable(GL_BLEND);
        glEnable(GL_FRAMEBUFFER_SRGB);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);


        Matrix4f view_matrix = new Matrix4f();
        Matrix3f transf_matrix = new Matrix3f();
        Matrix4f model_matrix = new Matrix4f();

        Colorspace clear_color = new Colorspace(0.9f, 0.9f, 0.9f).srgb_to_lin();
        glfwShowWindow(window);
        long lastTime = System.nanoTime();

        Vector3f move_dir = new Vector3f();
        Vector3f view_position = new Vector3f();
        Colorspace temp_color = new Colorspace(0);


        while (!glfwWindowShouldClose(window)) {

            try (MemoryStack frame_stack = stackPush()) {

                long thisTime = System.nanoTime();
                dt = (thisTime - lastTime) * 1E-9f;
                lastTime = thisTime;

                float camera_speed = CAMERA_SPEED;
                glfwPollEvents();
                move_dir.zero();
                if (keyDown[GLFW_KEY_LEFT_SHIFT])
                    camera_speed = SPRINT_CAMERA_SPEED;
                if (keyDown[GLFW_KEY_Q])
                    rotation -= 1f * ROTATE_SPEED*dt;
                if (keyDown[GLFW_KEY_E])
                    rotation += 1f * ROTATE_SPEED*dt;

                if (keyDown[GLFW_KEY_D])
                    move_dir.x += 1;
                if (keyDown[GLFW_KEY_A])
                    move_dir.x -= 1;
                if (keyDown[GLFW_KEY_W])
                    move_dir.y += 1;
                if (keyDown[GLFW_KEY_S])
                    move_dir.y -= 1;

                if (keyDown[GLFW_KEY_O])
                    zoom_val -= ZOOM_SPEED_KEY *dt;
                if (keyDown[GLFW_KEY_P])
                    zoom_val += ZOOM_SPEED_KEY *dt;

                float zoom = (float) Math.exp(zoom_val);
                if(move_dir.x != 0 || move_dir.y != 0)
                    position.add(move_dir.normalize().mul(dt * camera_speed/zoom));

                int curr_color = rainbow(thisTime*1e-9, 0.5f);

                view_position.set(position).mul(zoom);
                view_matrix.identity().scaleXY((float) window_height / window_width, 1).rotateZ(rotation).translateLocal(view_position).scale(zoom);

                glClearColor(clear_color.x, clear_color.y, clear_color.z, clear_color.a);
                glClear(GL_COLOR_BUFFER_BIT);

                glyph_buffer.reset();
                ui_buffer.reset();
                if(do_button(0, 0, "hello", 0.3f, 0.07f))
                    ui_buffer.submit_circle(0, 0, 1, 0xFF, null);
//                if(false)
                {
                    submit_styled_text(0, 0, 0, 0, glyph_buffer, "hello world", DEF_TEXT_STYLE, null);
//                    var outline = outline_cache.get(0, "h".codePointAt(0), 0.005f, 0.75f, 0.001f);
//                    glyph_buffer.submit(outline, null, true, curr_color);


                    Kept_Glyph glyph = loaded.glyphs.getOrDefault("h".codePointAt(0), loaded.missing_glyph);

                    float r = 0.005f;
                    for(var solid : glyph.connected_solids)
                    {
                        int color = 0xFF;
                        float[] xs = glyph.processed.points.xs;
                        float[] ys = glyph.processed.points.ys;
                        int j = solid.length - 1;
                        for(int i = 0; i < solid.length; j = i, i++)
                        {
                            glyph_buffer.submit_line(
                                xs[solid.at(j)], ys[solid.at(j)],
                                xs[solid.at(i)], ys[solid.at(i)],
                                r, color, null
                            );
                            glyph_buffer.submit_circle(xs[solid.at(j)], ys[solid.at(j)], r, color, null);
                        }


                        glyph_buffer.submit_circle(xs[0], ys[0], r, 0x00FF00, null);
                        glyph_buffer.submit_circle(xs[25], ys[25], r, 0x00FF00, null);
                        glyph_buffer.submit_circle(xs[27], ys[27], r, 0x00FF00, null);
                    }
//                    glyph_buffer.submit(loaded.glyph_buffer, glyph.triangulated_from, glyph.triangulated_to, null, true, curr_color);
//                    glyph_buffer.submit_circle(0, 0, 1, curr_color, null);
                }

                render.render(model_matrix, view_matrix, glyph_buffer);
                render.render(model_matrix, null, ui_buffer);

                glfwSwapBuffers(window);
            }
        }
    }

    public static int rainbow(double t, float alpha)
    {
        float x = (float) Math.pow(Math.sin(t), 2);
        float y = (float) Math.pow(Math.cos(t), 2);
        float z = (float) Math.pow(Math.cos(1.5 + t)/2 + 0.5f, 2);
        float a = alpha;
        return Colorspace.rgba_to_hex(x, y, z, a);
    }

    public static void main(String[] args) {
        Splines.test_some_splines();
        Colorspace.test_colorspace();
        Triangulate.test_is_in_triangle();
        new Main().run();
    }
}
