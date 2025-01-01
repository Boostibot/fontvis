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
        new Main().run();
    }
}
