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
// connect lines
// submit outline
// line cache as a part of glyph
// text layouting
// basic typing
// instructions on screen (seperate buffer)
// background (line) shader

public class Main {
    private int width = 1200;
    private int height = 800;
    private int mouseX = 0;
    private int mouseY = 0;
    private boolean panning = false;
    private float zoom_val = 1;
    private float rotation = 0;
    private float dt = 0;

    static float ZOOM_SPEED_KEY = 2;
    static float ZOOM_SPEED_SCROLL = 2;
    static float CAMERA_SPEED = 2;
    static float SPRINT_CAMERA_SPEED = 10;
    static float ROTATE_SPEED = 1;

    private final Quaternionf orientation = new Quaternionf();
    private final Vector3f position = new Vector3f(0, 0, 0);
    private boolean[] keyDown = new boolean[GLFW_KEY_LAST + 1];

    public static final int KB = 1024;
    public static final int MB = 1024*1024;
    public static final int GB = 1024*1024*1024;

    /*
    Triangulate.PointArray normalized = new Triangulate.PointArray();
    Triangulate.bezier_normalize_y(normalized, xs, ys, Triangulate.IndexBuffer.til(xs.length));

    for(int yi = 0; yi < resy; yi++)
        for(int xi = 0; xi < resx; xi++)
        {
            float x = (float) xi/resx*scale_x;
            float y = (float) yi/resy*scale_y;

            boolean inside = Triangulate.is_inside_normalized_bezier(
                    Triangulate.POINT_IN_SHAPE_BOUNDARY_DONT_CARE,
                    normalized.xs, normalized.ys, 0, normalized.length, x, y);

            if(is_solid)
                bitmap[xi + yi*resx] = bitmap[xi + yi*resx] || inside;
            else
                bitmap[xi + yi*resx] = bitmap[xi + yi*resx] && !inside;
        }
     */

    public static class Processed_Glyph
    {
        public int solids_count;
        public int holes_count;
        public Triangulate.PointArray points;
        public Triangulate.IndexBuffer[] shapes;
        public boolean[] are_solid;
        //todo bezier lengths!

        public int unicode;
        public int advance_width;
        public int left_side_bearing;
    }

    //TODO make this more optimized - allocate just once etc. ...
    public static Processed_Glyph preprocess_glyph(Font_Parser.Glyph glyph, int units_per_em)
    {
        Triangulate.IndexBuffer[] shapes = new Triangulate.IndexBuffer[glyph.contour_ends.length];
        boolean[] are_solid = new boolean[glyph.contour_ends.length];
        int[] contour_ends = glyph.contour_ends;
        Triangulate.PointArray processed = new Triangulate.PointArray();
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
        Triangulate.PointArray with_implied_points = new Triangulate.PointArray();
        Triangulate.BoolArray with_implied_on_curve = new Triangulate.BoolArray();
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
            shapes[k] = Triangulate.IndexBuffer.range(out_from_i, out_to_i);
        }

        Processed_Glyph out = new Processed_Glyph();
        out.advance_width = glyph.advance_width;
        out.left_side_bearing = glyph.left_side_bearing;
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
        public Processed_Glyph glyph;
        public Triangulate.IndexBuffer[] connected_solids;
        public Triangulate.IndexBuffer triangles;
        public Triangulate.IndexBuffer convex_beziers;
        public Triangulate.IndexBuffer concave_beziers;
    }

    public static Triangulated_Glyph triangulate_glyph(Processed_Glyph glyph)
    {
        Triangulate.IndexBuffer triangles = new Triangulate.IndexBuffer();
        Triangulate.IndexBuffer convex_beziers = new Triangulate.IndexBuffer();
        Triangulate.IndexBuffer concave_beziers = new Triangulate.IndexBuffer();

        //split into polygonal and bezier part
        Triangulate.IndexBuffer[] polygon_holes = new Triangulate.IndexBuffer[glyph.holes_count];
        Triangulate.IndexBuffer[] polygon_solids = new Triangulate.IndexBuffer[glyph.solids_count];
        {
            int solid_count = 0;
            int hole_count = 0;
            for(int k = 0; k < glyph.shapes.length; k++)
            {
                Triangulate.IndexBuffer shape = glyph.shapes[k];
                Triangulate.IndexBuffer polygon = new Triangulate.IndexBuffer();
                polygon.reserve(shape.length/2);
                Triangulate.bezier_contour_classify(polygon, convex_beziers, concave_beziers, glyph.points.xs, glyph.points.ys, shape, (float) 1e-5, true);

                if(glyph.are_solid[k])
                    polygon_solids[solid_count++] = polygon;
                else
                    polygon_holes[hole_count++] = polygon;
            }
        }

        //connect solids with holes
        Triangulate.IndexBuffer[] connected_solids = new Triangulate.IndexBuffer[glyph.solids_count];
        for(int k = 0; k < glyph.solids_count; k++)
            connected_solids[k] = Triangulate.connect_holes(null, glyph.points.xs, glyph.points.ys, polygon_solids[k], polygon_holes);

        for(int k = 0; k < connected_solids.length; k++)
            Triangulate.triangulate(triangles, glyph.points.xs, glyph.points.ys, connected_solids[k], true);

        Triangulated_Glyph out = new Triangulated_Glyph();
        out.connected_solids = connected_solids;
        out.triangles = triangles;
        out.concave_beziers = concave_beziers;
        out.convex_beziers = convex_beziers;
        out.glyph = glyph;
        return out;
    }

    static void sample_bezier(Triangulate.PointArray points, Triangulate.PointArray derivatives, float x1, float y1, float x2, float y2, float x3, float y3, int min_times_log2, int max_times_log2, float min_error)
    {
        class H {
            static float sqr_dist(float x1, float y1, float x2, float y2) {
                float dx = x1 - x2;
                float dy = y1 - y2;
                return dx*dx + dy*dy;
            }

            static void sample_recursive(Triangulate.PointArray points, Triangulate.PointArray derivatives, float x1, float y1, float x2, float y2, float x3, float y3, int min_times_log2, int max_times_log2, float sqr_min_error, int depth)
            {
                if(depth > max_times_log2)
                    return;

                float area = Triangulate.cross_product_z(x2, y2, x1, y1, x3, y3);
                float sqr_base = sqr_dist(x1, y1, x3, y3);
                if(area*area <= sqr_min_error*sqr_base && depth >= min_times_log2)
                    return;

                float x_mid = Splines.bezier(x1, x2, x3, 0.5f);
                float y_mid = Splines.bezier(y1, y2, y3, 0.5f);

                float lo_x2 = (x1 + x2)/2;
                float lo_y2 = (y1 + y2)/2;
                sample_recursive(points, derivatives, x1, y1, lo_x2, lo_y2, x_mid, y_mid, min_times_log2, max_times_log2, sqr_min_error, depth + 1);

                points.push(x_mid, y_mid);
                if(derivatives != null)
                {
                    derivatives.push(
                        Splines.bezier_derivative(x1, x2, x3, 0.5f),
                        Splines.bezier_derivative(y1, y2, y3, 0.5f)
                    );
                }

                float hi_x2 = (x2 + x3)/2;
                float hi_y2 = (y2 + y3)/2;
                sample_recursive(points, derivatives, x_mid, y_mid, hi_x2, hi_y2, x3, y3, min_times_log2, max_times_log2, sqr_min_error, depth + 1);
            }
        }

        float sqr_min_error = min_error*min_error;
        //detect problematic curves and subdivide them at least once
        if(min_times_log2 == 0)
            if(H.sqr_dist(x1, y1, x3, y3) == 0 && H.sqr_dist(x1, y1, x2, y2) != 0)
                min_times_log2 = 1;

        points.push(x1, y1);
        if(derivatives != null)
            derivatives.push(
                Splines.bezier_derivative(x1, x2, x3, 0),
                Splines.bezier_derivative(y1, y2, y3, 0)
            );
        H.sample_recursive(points, derivatives, x1, y1, x2, y2, x3, y3, min_times_log2, max_times_log2, sqr_min_error, 0);
        points.push(x3, y3);
        if(derivatives != null)
            derivatives.push(
                    Splines.bezier_derivative(x1, x2, x3, 1),
                    Splines.bezier_derivative(y1, y2, y3, 1)
            );
    }

    static void sample_bezier(Triangulate.PointArray points, float x1, float y1, float x2, float y2, float x3, float y3, float min_error)
    {
        sample_bezier(points, null, x1, y1, x2, y2, x3, y3, 0, 16, min_error);
    }

    private void run() {

        ArrayList<Font_Parser.Font_Log> logs = new ArrayList<>();
        Font_Parser.Font font = Font_Parser.parse_load("./assets/fonts/Roboto/Roboto-Black.ttf", logs);
        if(font == null || font.glyphs == null)
            throw new IllegalStateException("Unable to Read font file");

        HashMap<Integer, Triangulated_Glyph> glyph_cache = new HashMap<>();
        {
            final long start_time = System.currentTimeMillis();
            for (var entry : font.glyphs.entrySet()) {
                Font_Parser.Glyph glyph = entry.getValue();
                Processed_Glyph processed = preprocess_glyph(glyph, font.units_per_em);
                Triangulated_Glyph triangulated = triangulate_glyph(processed);
                glyph_cache.put(triangulated.glyph.unicode, triangulated);
            }
            final long end_time = System.currentTimeMillis();
            System.out.println(STR."Processing took \{end_time-start_time} ms");
        }

        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_SAMPLES, 8);

        long window = glfwCreateWindow(width, height, "graphix", NULL, NULL);
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
                width = w;
                height = h;
                glViewport(0, 0, width, height);
            }
        });
        glfwSetCursorPosCallback(window, (long window_, double xpos, double ypos) -> {
            if (panning) {
                float zoom = (float) Math.exp(zoom_val);
                float deltaX = (float) xpos - mouseX;
                float deltaY = (float) ypos - mouseY;
                position.x += deltaX/width*2/zoom;
                position.y -= deltaY/height*2/zoom;

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
            zoom_val += (float)deltax*ZOOM_SPEED_SCROLL*dt;
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            width = pWidth.get(0);
            height = pHeight.get(0);
            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - width) / 2,
                    (vidmode.height() - height) / 2
            );
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();
        var debugProc = GLUtil.setupDebugMessageCallback();
;
        glEnable(GL_BLEND);
        glEnable(GL_FRAMEBUFFER_SRGB);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);

        Render.Quadratic_Bezier_Render bezier_render = new Render.Quadratic_Bezier_Render(64*MB);
        Render.Bezier_Buffer bezier_buffer = new Render.Bezier_Buffer(64*MB);
        Render.Bezier_Buffer glyph_buffer = new Render.Bezier_Buffer(4*MB);
        glyph_buffer.grows = false;

        /*
        if(false)
        {
            int resx = 200;
            int resy = 200;
            Font_Parser.Glyph glyph = font.glyphs.get("a".codePointAt(0));
            float scalex = 1.0f/font.units_per_em;
            float scaley = 1.0f/font.units_per_em;
            boolean[] raster = rasterize_glyph(glyph, resx, resy, 1, 1, font.units_per_em);

            for(int yi = 0; yi < resy; yi++)
                for(int xi = 0; xi < resx; xi++)
                    if(raster[xi + yi*resx])
                        glyph_buffer.submit_circle((float)xi/resx, (float)yi/resy, 0.5f/resx, 0x0, null);
        }
        */
        {
            Triangulated_Glyph triangulated = glyph_cache.get("&".codePointAt(0));
            Processed_Glyph processed = triangulated.glyph;

            //triangulate
            int[] colors = {0xFF0000, 0x00FF00, 0x0000FF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFF0000, 0x00FF00, 0x0000FF};

            //Draw triangulated solid regions
            int solids_color = 0x00;
            int convex_flags = Render.Bezier_Buffer.FLAG_BEZIER;
            int concave_flags = Render.Bezier_Buffer.FLAG_BEZIER
                    | Render.Bezier_Buffer.FLAG_INVERSE;

            glyph_buffer.submit_index_buffer(processed.points.xs, processed.points.ys, triangulated.triangles, solids_color, 0, null);
            glyph_buffer.submit_index_buffer(processed.points.xs, processed.points.ys, triangulated.convex_beziers, solids_color, convex_flags, null);
            glyph_buffer.submit_index_buffer(processed.points.xs, processed.points.ys, triangulated.concave_beziers, solids_color, concave_flags, null);
            {
                Triangulate.PointArray temp_points = new Triangulate.PointArray();
                Triangulate.PointArray temp_ders = new Triangulate.PointArray();
                float[] xs = processed.points.xs;
                float[] ys = processed.points.ys;
                float r = 0.005f;
                int color = 0xFF0000;
                for(var shape : processed.shapes)
                {
                    float p1x = xs[shape.at(shape.length - 1)];
                    float p1y = ys[shape.at(shape.length - 1)];
                    for(int i = 0; i < shape.length; i += 2)
                    {
                        float p2x = xs[shape.at(i)];
                        float p2y = ys[shape.at(i)];
                        float p3x = xs[shape.at(i+1)];
                        float p3y = ys[shape.at(i+1)];

                        if(p2x == p3x && p2y == p3y)
                            glyph_buffer.submit_line(
                                p1x, p1y,
                                p2x, p2y, r, color, null
                            );
                        else
                            glyph_buffer.submit_bezier_line(temp_points, temp_ders,
                                p1x, p1y,
                                p2x, p2y,
                                p3x, p3y,
                                0.001f, r, color, null
                            );

                        p1x = p3x;
                        p1y = p3y;
                    }
                }
            }

            //draw connections polygons
            if(false)
            {
            Triangulate.PointArray points = processed.points;
            for(int k = 0; k < processed.solids_count; k++)
            {
                //int point_color = 0x330000FF;
                int point_color = colors[k];
                float r = 0.005f;
                Triangulate.IndexBuffer connected = triangulated.connected_solids[k];

                int j = connected.length - 1;
                for(int i = 0; i < connected.length; i++)
                {
                    int vj = connected.at(j);
                    int vi = connected.at(i);
                    glyph_buffer.submit_line(points.xs[vj], points.ys[vj], points.xs[vi], points.ys[vi], r, point_color, null);
                    glyph_buffer.submit_circle(points.xs[vi], points.ys[vi], r, point_color, null);
                    j = i;
                }
            }
            }
        }


//        glyph_buffer.submit_text_countour(font, "obaH", 1.0f/font.units_per_em, 0.05f, 0.05f, 1, 0x80FFFFFF, null);

        Matrix4f view_matrix = new Matrix4f();
        Matrix3f transf_matrix = new Matrix3f();
        Matrix4f model_matrix = new Matrix4f();



        Colorspace clear_color = new Colorspace(0.9f, 0.9f, 0.9f).srgb_to_lin();
        glfwShowWindow(window);
        long lastTime = System.nanoTime();

        Vector3f move_dir = new Vector3f();
        Vector3f view_position = new Vector3f();
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

                view_position.set(position).mul(zoom);
                view_matrix.identity().scaleXY((float)height/width, 1).rotateZ(rotation).translateLocal(view_position).scale(zoom);

                glClearColor(clear_color.x, clear_color.y, clear_color.z, clear_color.a);
                glClear(GL_COLOR_BUFFER_BIT);

                bezier_render.render(model_matrix, view_matrix, bezier_buffer);
                bezier_render.render(model_matrix, view_matrix, glyph_buffer);
                bezier_buffer.reset();

                glfwSwapBuffers(window);
            }
        }
    }

    public static void main(String[] args) {
        Splines.test_some_splines();
        Colorspace.test_colorspace();
        Triangulate.test_is_in_triangle();
        new Main().run();
    }
}
