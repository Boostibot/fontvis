import static java.lang.Math.*;
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

    public static final class Geometry
    {
        public float[] xs;
        public float[] ys;
        public int[] flags;
        public int[] indices;
        public int indices_length;

        public Triangulate.AABB_Vertices aabb;
    }

    /*
    public static Geometry geometry_from_glyph(Font_Parser.Glyph glyph, float units_per_em)
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

        for(int j = 0; j < glyph.contour_ends.length; j++)
        {
            int i_start = j == 0 ? 0 : glyph.contour_ends[j - 1];
            int i_end = glyph.contour_ends[j];
            int range = i_end - i_start;
            if(range <= 1)
                continue;

            //reverse orientation to make it easier to work with

            //calculate signed area of this contour in
            // treating all beziers as straight lines.
            //The signed are will be positive for when the indices
            // are oriented counter clockwise
            float signed_area = 0;
            for(int i = 0; i < range;)
            {
                int i0 = i_start + i;
                int i1 = i_start + ((i+1) % range);

                int p0 = i0;
                int p1 = i1;
                i += 1;

                assert glyph.points_on_curve[i0];
                //if is a bezier ignore the control vertex
                // and instead use third point
                if(glyph.points_on_curve[i1] == false)
                {
                    int i2 = i_start + ((i+2) % range);
                    assert glyph.points_on_curve[i2];

                    p1 = i2;
                    i += 1;
                }

                signed_area += xs[p0] * ys[p1] - xs[p1] * ys[p0];
            }

            boolean is_hole = signed_area < 0;
            boolean is_polygon = signed_area >= 0;
            assert is_hole == false;

            IntArray polygon_indices = new IntArray();
            IntArray bezier_indices_out_bend = new IntArray();
            IntArray bezier_indices_in_bend = new IntArray();

            for(int i = 0; i <= range;)
            {
                int i0 = i_start + ((i+0) % range);
                int i1 = i_start + ((i+1) % range);
                int i2 = i_start + ((i+2) % range);
                int increment = 0;

                assert glyph.points_on_curve[i0];

                //if is a bezier
                if(glyph.points_on_curve[i1] == false)
                {
                    assert glyph.points_on_curve[i2];

                    //Pessimistically approximate the bezier using straight lines.
                    // That is replace it with straight lines such that the are of the
                    // resulting shape will be less than the original filled bezier.

                    //if is bend outwards approximate using straight line
                    // (reduce D shape curve to line)
                    if(Triangulate.counter_clockwise_is_convex(xs, ys, i0, i1, i2) == is_polygon)
                    {
                        polygon_indices.push(i0);

                        bezier_indices_out_bend.push(i0);
                        bezier_indices_out_bend.push(i1);
                        bezier_indices_out_bend.push(i2);
                    }
                    //is is bend inwards approximate using V shape
                    else
                    {
                        polygon_indices.push(i0);
                        polygon_indices.push(i1);

                        bezier_indices_in_bend.push(i0);
                        bezier_indices_in_bend.push(i1);
                        bezier_indices_in_bend.push(i2);
                    }

                    //add the bezier
                    increment = 2;
                }
                //is a simple line
                else
                {
                    polygon_indices.push(i0);
                    increment = 1;
                }

                i += increment;
            }



        }

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
                position.x += deltaX/height;
                position.y -= deltaY/height;

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




        glyph_buffer.submit_text_countour(font, "kruw", 1.0f/font.units_per_em, 0.05f, 0.05f, 1, 0x80FFFFFF, null);

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
