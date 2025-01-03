import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

import java.lang.Math;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

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

    public static final class Triangulated_Glyph
    {
        public float[] xs;
        public float[] ys;
        public int[] flags;
        public int[] indices;
        public int indices_length;

        public Triangulate.AABB_Vertices aabb;
    }

    public static final class Normalized_Beziere
    {
        public float[] xs;
        public float[] ys;
        public int length;
    }

    public static Normalized_Beziere normalize_bezier(float[] xs, float[] ys, int from_i, int to_i)
    {
        class H {
            static void push_segment(Normalized_Beziere out, float x1, float y1, float x2, float y2)
            {
                if(out.length + 2 > out.xs.length) {
                    out.xs = Arrays.copyOf(out.xs, out.xs.length*5/4 + 8);
                    out.ys = Arrays.copyOf(out.ys, out.ys.length*5/4 + 8);
                }

                //control point
                out.xs[out.length] = x1;
                out.ys[out.length] = y1;
                //on curve point
                out.xs[out.length + 1] = x2;
                out.ys[out.length + 1] = y2;
                out.length += 2;
            }
        }

        assert xs.length % 2 == 0;
        assert ys.length % 2 == 0;
        assert ys.length == xs.length;

        Splines.Quad_Bezier b1 = new Splines.Quad_Bezier();
        Splines.Quad_Bezier b2 = new Splines.Quad_Bezier();
        Normalized_Beziere out = new Normalized_Beziere();
        //normalize in the x direction
        {
            out.xs = new float[to_i - from_i + 8];
            out.ys = new float[to_i - from_i + 8];
            out.length = 0;

            int j = to_i - 1;
            for(int i = from_i; i < to_i; i += 2)
            {
                float x1 = xs[j];
                float y1 = ys[j];
                float x2 = xs[i];
                float y2 = ys[i];
                float x3 = xs[i+1];
                float y3 = ys[i+1];

                //if control point is extreme - either bigger then both or
                // smaller then both. If both are smaller then the product below
                // is positive. If both are bigger then again its positive.
                boolean is_not_extreme1 = (x1 - x2)*(x3 - x2) <= 0;
                boolean is_not_extreme2 = Math.min(x1, x3) <= x2 && x2 <= Math.max(x1, x3);
                assert is_not_extreme1 == is_not_extreme2;

                if((x1 - x2)*(x3 - x2) > 0)
                {
                    float t = Splines.bezier_extreme(x1, x2, x3);
                    assert 0 < t && t < 1;
                    Splines.bezier_split_at_extreme(false, b1, b2, x1, y1, x2, y2, x3, y3, t);
                    H.push_segment(out, b1.x2, b1.y2, b1.x3, b1.y3);
                    H.push_segment(out, b2.x2, b2.y2, b2.x3, b2.y3);

                    assert (b1.x1 - b1.x2)*(b1.x3 - b1.x2) <= 0;
                    assert (b2.x1 - b2.x2)*(b2.x3 - b2.x2) <= 0;
                }
                else
                    H.push_segment(out, x2, y2, x3, y3);

                j = i;
            }
        }

        //make the last result the new input parameters
        xs = out.xs;
        ys = out.ys;
        from_i = 0;
        to_i = out.length;

        //normalize in the y direction
        {
            out.xs = new float[to_i - from_i + 8];
            out.ys = new float[to_i - from_i + 8];
            out.length = 0;

            int j = to_i - 2;
            for(int i = from_i; i < to_i; i += 2)
            {
                float x1 = xs[j+1];
                float y1 = ys[j+1];
                float x2 = xs[i];
                float y2 = ys[i];
                float x3 = xs[i+1];
                float y3 = ys[i+1];

                if((y1 - y2)*(y3 - y2) > 0)
                {
                    float t = Splines.bezier_extreme(y1, y2, y3);
                    assert 0 < t && t < 1;
                    Splines.bezier_split_at_extreme(true, b1, b2, x1, y1, x2, y2, x3, y3, t);
                    H.push_segment(out, b1.x2, b1.y2, b1.x3, b1.y3);
                    H.push_segment(out, b2.x2, b2.y2, b2.x3, b2.y3);

                    assert (b1.y1 - b1.y2)*(b1.y3 - b1.y2) <= 0;
                    assert (b2.y1 - b2.y2)*(b2.y3 - b2.y2) <= 0;
                    assert (b1.x1 - b1.x2)*(b1.x3 - b1.x2) <= 0;
                    assert (b2.x1 - b2.x2)*(b2.x3 - b2.x2) <= 0;
                }
                else
                    H.push_segment(out, x2, y2, x3, y3);

                j = i;
            }
        }

        //assert is normalized
        {
            int j = out.length - 2;
            for(int i = 0; i < out.length; i += 2)
            {
                float x1 = out.xs[j+1];
                float y1 = out.ys[j+1];
                float x2 = out.xs[i];
                float y2 = out.ys[i];
                float x3 = out.xs[i+1];
                float y3 = out.ys[i+1];

                boolean is_not_extreme1_x = (x1 - x2)*(x3 - x2) <= 0;
                assert is_not_extreme1_x;

                boolean is_not_extreme1_y = (y1 - y2)*(y3 - y2) <= 0;
                assert is_not_extreme1_y;

                j = i;
            }
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

            Normalized_Beziere normalized = normalize_bezier(xs, ys, 0, xs.length);
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
        glfwWindowHint(GLFW_SAMPLES, 5);

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



//        glyph_buffer.submit_text_countour(font, "obaH", 1.0f/font.units_per_em, 0.05f, 0.05f, 1, 0x80FFFFFF, null);

        Matrix4f view_matrix = new Matrix4f();
        Matrix3f transf_matrix = new Matrix3f();
        Matrix4f model_matrix = new Matrix4f();

        float rotation = 0;
        float rotate_speed = 1;
        float zoom = 1;
        float speed = 2f;

        Colorspace clear_color = new Colorspace(0.2f, 0.3f, 0.3f).srgb_to_lin();
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
