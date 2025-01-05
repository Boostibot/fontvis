import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

import java.nio.IntBuffer;
import java.util.ArrayList;

import org.joml.*;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

public class Main {
    private int width = 1200;
    private int height = 800;
    private int mouseX = 0;
    private int mouseY = 0;
    private boolean viewing = false;

    private final Quaternionf orientation = new Quaternionf();
    private final Vector3f position = new Vector3f(0, 0, 0);
    private boolean[] keyDown = new boolean[GLFW_KEY_LAST + 1];

    public static final int KB = 1024;
    public static final int MB = 1024*1024;
    public static final int GB = 1024*1024*1024;


    //I dont think this is worth it tbh. Just suck it and go home...
    //
    public static int raycast_bezier_first_optimistic_hit(int explucde_i, float[] xs, float[] ys, int from_i, int to_i, float origin_x, float origin_y, float dir_x, float dir_y)
    {
        Splines.Intersections intersections = new Splines.Intersections();
        int j = to_i - 2;
        for(int i = from_i; i < to_i; i += 2)
        {
            if(i != explucde_i)
            {
                float x1 = xs[j+1];
                float y1 = ys[j+1];
                float x2 = xs[i];
                float y2 = ys[i];
                float x3 = xs[i+1];
                float y3 = ys[i+1];

                if(Splines.bezier_line_intersect(intersections, x1, y1, x2, y2, x3, y3, dir_x, dir_y, origin_x, origin_y) > 0)
                    return i;
            }

            j = i;
        }

        return -1;
    }

    public static Triangulate.PointArray bezier_make_polygon_embeddable(float[] xs, float[] ys, int from_i, int to_i)
    {
        Triangulate.PointArray out = new Triangulate.PointArray();
        out.reserve(to_i - from_i + 8);

        int max_bisections = 4;
        int j = to_i - 2;
        for(int i = from_i; i < to_i; i += 2)
        {
            float x1 = xs[j+1];
            float y1 = ys[j+1];
            float x2 = xs[i];
            float y2 = ys[i];
            float x3 = xs[i+1];
            float y3 = ys[i+1];

            //if isnt straight segment
            if(x2 != x3 || y2 != y2)
            {
                //bisect up to max_bisections times
                int bi = 0;
                for(; bi <= max_bisections; bi++)
                {
                    boolean did_hit = false;
                    int test_count = 1 << (bi + 1);
                    for(int test_i = 1; test_i < test_count; test_i ++)
                    {
                        //test if bise
                        float bi_t = (float)test_i/test_count;
                        float origin_x = Splines.bezier(x1, x2, x3, bi_t);
                        float origin_y = Splines.bezier(x1, x2, x3, bi_t);

                        float dir_x = x2 - origin_x;
                        float dir_y = y2 - origin_y;

                        int hit = raycast_bezier_first_optimistic_hit(i, xs, ys, from_i, to_i, origin_x, origin_y, dir_x, dir_y);
                        if(hit != -1)
                        {
                            System.out.println(STR."Found an overlap at index \{i} bisection \{bi}");
                            did_hit = true;
                            break;
                        }
                    }

                    if(did_hit == false)
                        break;
                }
            }

            j = i;
        }

        return out;
    }

    public boolean[] rasterize_glyph(Font_Parser.Glyph glyph, int resx, int resy, float scale_x, float scale_y, float units_per_em)
    {
        boolean[] bitmap = new boolean[resx*resy];

        for(int k = 0; k < glyph.solids.length + glyph.holes.length; k++)
        {
            Font_Parser.Countour countour = k < glyph.solids.length
                    ? glyph.solids[k]
                    : glyph.holes[k - glyph.solids.length];

            boolean is_solid = k < glyph.solids.length;
            float xs[] = new float[countour.xs.length];
            float ys[] = new float[countour.ys.length];
            for(int i = 0; i < countour.xs.length; i++) {
                xs[i] = (float) countour.xs[i]/units_per_em;
                ys[i] = (float) countour.ys[i]/units_per_em;
            }

            Triangulate.PointArray normalized = new Triangulate.PointArray();
            Triangulate.bezier_normalize_y(normalized, xs, ys, 0, xs.length);

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
        }

        return bitmap;
    }

    /*
    public static Geometry trianglulate_glyph(Font_Parser.Glyph glyph, float units_per_em)
    {


        assert glyph.points_x.length == glyph.points_y.length;
        assert glyph.points_x.length == glyph.points_on_curve.length;
        assert glyph.contour_end_indices.length > 0;

        boolean[] on_curve = glyph.points_on_curve.clone();
        float[] xs = new float[glyph.points_x.length];
        float[] ys = new float[glyph.points_y.length];

        for(int i = 0; i < glyph.points_x.length; i++)
            xs[i] = glyph.points_x[i]/units_per_em;

        for(int i = 0; i < glyph.points_y.length; i++)
            ys[i] = glyph.points_y[i]/units_per_em;

        return new Geometry();
    }
     */

    private void run() {
        ArrayList<Font_Parser.Font_Log> logs = new ArrayList<>();
        Font_Parser.Font font = Font_Parser.parse_load("/home/boosti/repos/vector_graphics/assets/fonts/Roboto/Roboto-Black.ttf", logs);
        if(font == null || font.glyphs == null)
            throw new IllegalStateException("Unable to Read font file");

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
        glfwSetCursorPosCallback(window, (long window2, double xpos, double ypos) -> {
            if (viewing) {
                float deltaX = (float) xpos - mouseX;
                float deltaY = (float) ypos - mouseY;
                position.x += deltaX/width*2;
                position.y -= deltaY/height*2;

                //orientation.rotateLocalX(deltaY * 0.01f).rotateLocalY(deltaX * 0.01f);
            }
            mouseX = (int) xpos;
            mouseY = (int) ypos;
        });
        glfwSetMouseButtonCallback(window, (long window4, int button, int action, int mods) -> {
            if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS)
                viewing = true;
            else
                viewing = false;
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
        Render.Quadratic_Bezier_Buffer bezier_buffer = new Render.Quadratic_Bezier_Buffer(64*MB);
        Render.Quadratic_Bezier_Buffer glyph_buffer = new Render.Quadratic_Bezier_Buffer(4*MB);
        glyph_buffer.grows = false;

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

        {
            Font_Parser.Glyph glyph = font.glyphs.get("&".codePointAt(0));

            int shapes_count = glyph.solids.length + glyph.holes.length;
            Triangulate.PointArray[] shapes = new Triangulate.PointArray[shapes_count];
            boolean[] are_solid = new boolean[shapes_count];

            for(int k = 0; k < shapes_count; k++)
            {
                Font_Parser.Countour countour = k < glyph.solids.length
                        ? glyph.solids[k]
                        : glyph.holes[k - glyph.solids.length];

                Triangulate.PointArray shape = new Triangulate.PointArray();
                boolean is_solid = k < glyph.solids.length;
                float xs[] = new float[countour.xs.length];
                float ys[] = new float[countour.ys.length];
                for(int i = 0; i < countour.xs.length; i++) {
                    xs[i] = (float) countour.xs[i]/font.units_per_em;
                    ys[i] = (float) countour.ys[i]/font.units_per_em;
                }
                shape.xs = xs;
                shape.ys = ys;
                shape.length = xs.length;
                shapes[k] = shape;
                are_solid[k] = is_solid;
            }

            //split into polygonal and bezier part
            Triangulate.PointArray[] polygon_shapes = new Triangulate.PointArray[shapes_count];
            Triangulate.PointArray[] convex_bezier_shapes = new Triangulate.PointArray[shapes_count];
            Triangulate.PointArray[] concave_bezier_shapes = new Triangulate.PointArray[shapes_count];
            {
                for(int k = 0; k < shapes.length; k++)
                {
                    Triangulate.PointArray shape = shapes[k];
                    Triangulate.PointArray polygon = new Triangulate.PointArray();
                    Triangulate.PointArray convex_beziers = new Triangulate.PointArray();
                    Triangulate.PointArray concave_beziers = new Triangulate.PointArray();
                    polygon.reserve(shape.length/2);

                    float eps = (float) 1e-5;
                    int j = shape.length - 2;
                    for(int i = 0; i < shape.length; i += 2)
                    {
                        float x1 = shape.xs[j+1];
                        float y1 = shape.ys[j+1];
                        float x2 = shape.xs[i];
                        float y2 = shape.ys[i];
                        float x3 = shape.xs[i+1];
                        float y3 = shape.ys[i+1];

                        float cross = Triangulate.cross_product_z(x1, y1, x2, y2, x3, y3);
                        if(cross > eps) {
                            polygon.push(x3, y3);
                            convex_beziers.push(x1, y1, x2, y2, x3, y3);
                        }
                        else if(cross < -eps){
                            polygon.push(x2, y2, x3, y3);
                            concave_beziers.push(x1, y1, x2, y2, x3, y3);
                        }
                        else
                            polygon.push(x3, y3);
                        j = i;
                    }

                    polygon_shapes[k] = polygon;
                    convex_bezier_shapes[k] = convex_beziers;
                    concave_bezier_shapes[k] = concave_beziers;
                }
            }

            //connect hole
            Triangulate.PointArray connected = new Triangulate.PointArray();
            {
                Triangulate.Hole[] holes = new Triangulate.Hole[polygon_shapes.length - 1];
                for(int k = 0; k < polygon_shapes.length - 1; k++)
                {
                    Triangulate.PointArray hole_shape = polygon_shapes[k + 1];
                    holes[k] = new Triangulate.Hole();
                    holes[k].xs = hole_shape.xs;
                    holes[k].ys = hole_shape.ys;
                    holes[k].from_i = 0;
                    holes[k].to_i = hole_shape.length;
                    holes[k].stride = 1;
                }

                Triangulate.connect_holes(connected,
                        polygon_shapes[0].xs, polygon_shapes[0].ys,
                        0, polygon_shapes[0].length, 1,
                        holes);
            }

            //triangulize
            IntArray triangles = Triangulate.triangulate(connected.xs, connected.ys, 0, connected.length, 1);
            {
                int point_color = 0x330000FF;
                float r = 0.005f;
                int color = 0x00;

//                if(false)
                {
                    int[] is = triangles.array;
                    float[] xs = connected.xs;
                    float[] ys = connected.ys;
                    for(int i = 0; i < triangles.length; i += 3)
                    {
                        glyph_buffer.submit_bezier_or_triangle(
                                xs[is[i]], ys[is[i]],
                                xs[is[i+1]], ys[is[i+1]],
                                xs[is[i+2]], ys[is[i+2]],
                                color, 0, null);

                    }
                }

                for(int k = 0; k < polygon_shapes.length; k++)
                {
                    Triangulate.PointArray convex_bezier = convex_bezier_shapes[k];
                    Triangulate.PointArray concave_bezier = concave_bezier_shapes[k];

                    for(int i = 0; i < convex_bezier.length; i += 3)
                    {
                        int flags = Render.Quadratic_Bezier_Buffer.FLAG_BEZIER;
                        glyph_buffer.submit_bezier_or_triangle(
                                convex_bezier.xs[i + 0], convex_bezier.ys[i + 0],
                                convex_bezier.xs[i + 1], convex_bezier.ys[i + 1],
                                convex_bezier.xs[i + 2], convex_bezier.ys[i + 2],
                                color, flags, null);
                    }

                    for(int i = 0; i < concave_bezier.length; i += 3)
                    {
                        int flags = Render.Quadratic_Bezier_Buffer.FLAG_BEZIER
                            | Render.Quadratic_Bezier_Buffer.FLAG_INVERSE;
                        glyph_buffer.submit_bezier_or_triangle(
                                concave_bezier.xs[i + 0], concave_bezier.ys[i + 0],
                                concave_bezier.xs[i + 1], concave_bezier.ys[i + 1],
                                concave_bezier.xs[i + 2], concave_bezier.ys[i + 2],
                                color, flags, null);
                    }
                }

//                if(false)
                {
                    int j = connected.length - 1;
                    for(int i = 0; i < connected.length; i++)
                    {
                        glyph_buffer.submit_line(connected.xs[j], connected.ys[j], connected.xs[i], connected.ys[i], r, point_color, null);
                        glyph_buffer.submit_circle(connected.xs[i], connected.ys[i], r, point_color, null);
                        j = i;
                    }
                }
            }
        }


//        glyph_buffer.submit_text_countour(font, "obaH", 1.0f/font.units_per_em, 0.05f, 0.05f, 1, 0x80FFFFFF, null);

        Matrix4f view_matrix = new Matrix4f();
        Matrix3f transf_matrix = new Matrix3f();
        Matrix4f model_matrix = new Matrix4f();

        float rotation = 0;
        float rotate_speed = 1;
        float zoom = 1;
        float speed = 2f;

        Colorspace clear_color = new Colorspace(0.9f, 0.9f, 0.9f).srgb_to_lin();
        glfwShowWindow(window);
        long lastTime = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {

            try (MemoryStack frame_stack = stackPush()) {

                long thisTime = System.nanoTime();
                float dt = (thisTime - lastTime) * 1E-9f;
                lastTime = thisTime;

                glfwPollEvents();
                Vector3f move_dir = new Vector3f();
                if (keyDown[GLFW_KEY_LEFT_SHIFT])
                    speed = 10f;
                if (keyDown[GLFW_KEY_Q])
                    rotation -= 1f * rotate_speed * dt;
                if (keyDown[GLFW_KEY_E])
                    rotation += 1f * rotate_speed * dt;

                if (keyDown[GLFW_KEY_D])
                    move_dir.x += 1;
                if (keyDown[GLFW_KEY_A])
                    move_dir.x -= 1;
                if (keyDown[GLFW_KEY_W])
                    move_dir.y += 1;
                if (keyDown[GLFW_KEY_S])
                    move_dir.y -= 1;

                if (keyDown[GLFW_KEY_O])
                    zoom -= 1*dt;
                if (keyDown[GLFW_KEY_P])
                    zoom += 1*dt;

                if(move_dir.x != 0 || move_dir.y != 0)
                    position.add(move_dir.normalize().mul(dt * speed));

                //view_matrix.identity().rotateLocalZ(rotation).translate(position).scale(zoom).scaleXY((float)height/width, 1);
                //view_matrix.identity().scale(zoom).scaleXY((float)height/width, 1).rotateLocalZ(rotation).translate(position);
                view_matrix.identity().scaleXY((float)height/width, 1).scale(zoom).rotateZ(rotation).translateLocal(position);

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
