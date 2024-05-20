import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

import java.lang.Math;
import java.nio.IntBuffer;

import org.joml.*;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

public class Main {
    private int width = 1200;
    private int height = 800;
    private int mouseX, mouseY;
    private boolean viewing;

    private final Matrix4f mat = new Matrix4f();
    private final Quaternionf orientation = new Quaternionf();
    private final Vector3f position = new Vector3f(0, 0, 0);
    private boolean[] keyDown = new boolean[GLFW_KEY_LAST + 1];

    private void run() {
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_SAMPLES, 4);

        long window = glfwCreateWindow(width, height, "A start!", NULL, NULL);
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
                orientation.rotateLocalX(deltaY * 0.01f).rotateLocalY(deltaX * 0.01f);
            }
            mouseX = (int) xpos;
            mouseY = (int) ypos;
        });
        glfwSetMouseButtonCallback(window, (long window4, int button, int action, int mods) -> {
            if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS) {
                viewing = true;
            } else {
                viewing = false;
            }
        });


        try ( MemoryStack stack = stackPush() ) {
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

        glClearColor(0.7f, 0.8f, 0.9f, 1);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // set up vertex data (and buffer(s)) and configure vertex attributes
        // ------------------------------------------------------------------
//        float[] vertices = {
//            0.5f,  0.5f, 0.0f,  // top right
//            0.5f, -0.5f, 0.0f,  // bottom right
//            -0.5f, -0.5f, 0.0f,  // bottom left
//            -0.5f,  0.5f, 0.0f   // top left
//        };
//        int[] indices = {  // note that we start from 0!
//            0, 1, 3,  // first Triangle
//            1, 2, 3   // second Triangle
//        };

        float[] triangles = {
                0, 1, 0,
                0, 0, 0,
                1, 0, 0,
        };

        int VAO = 0;
        int VBO = 0;
        int EBO = 0;
        if(false)
        {
            float[] vertices = {
                    -0.5f,  0.5f, 0.0f,   // top left
                    0.5f,  0.5f, 0.0f,  // top right
                    -0.5f, -0.5f, 0.0f,  // bottom left
                    0.5f, -0.5f, 0.0f,  // bottom right
            };
            int[] indices = {  // note that we start from 0!
                    0, 2, 3,
                    //                0, 1, 3,  // first Triangle
                    //                1, 2, 3   // second Triangle
            };
            VAO = glGenVertexArrays();
            VBO = glGenBuffers();
            EBO = glGenBuffers();
            // bind the Vertex Array Object first, then bind and set vertex buffer(s), and then configure vertex attributes(s).
            glBindVertexArray(VAO);

            glBindBuffer(GL_ARRAY_BUFFER, VBO);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBO);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, NULL);
            glEnableVertexAttribArray(0);

            glBindBuffer(GL_ARRAY_BUFFER, 0);

            glBindVertexArray(0);
        }
        else
        {
            VAO = glGenVertexArrays();
            VBO = glGenBuffers();
            glBindVertexArray(VAO);
            glBindBuffer(GL_ARRAY_BUFFER, VBO);
            glBufferData(GL_ARRAY_BUFFER, triangles, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, NULL);
            glEnableVertexAttribArray(0);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }

        int shader_color = compileProgramSource(
                """
                #version 330 core
                layout (location = 0) in vec3 vertex_pos;
                
                uniform vec4 fill_color;
                uniform mat4 model;
                uniform mat4 view;
                void main()
                {
                    
                   vec4 pos = view * model * vec4(vertex_pos, 1.0f);
                   gl_Position = pos;
                }
                """,
                """
                #version 330 core
                out vec4 FragColor;
                uniform vec4 color;
                void main()
                {
                   FragColor = color;
                }
                """);


        int shader_bezier_color = compileProgramSource(
                """
                #version 330 core
                layout (location = 0) in vec3 vertex_pos;
                out vec2 uv;
                
                uniform mat4 model;
                uniform mat4 view;
                void main()
                {
                   switch(gl_VertexID % 3)
                   {
                    case 0: uv = vec2(0, 0); break;
                    case 1: uv = vec2(0.5, 0); break;
                    case 2: uv = vec2(1, 1); break;
                   }
                
                   vec4 pos = view * model * vec4(vertex_pos, 1.0f);
                   gl_Position = pos;
                }
                """,
                """
                #version 330 core
                out vec4 frag_color;
                
                in vec2 uv;
                uniform vec4 color;
                uniform float aa_threshold;
                
                void main()
                {
                    float u = uv.x;
                    float v = uv.y;
                    vec2 du = dFdx(uv);
                    vec2 dv = dFdy(uv);
                
                    float F = u*u - v;
                    vec2 J_F = vec2(
                        2*u*du.x - du.y,
                        2*u*dv.x - dv.y
                    );
                    float dist = F/(length(J_F) + 0.0000001);
                    float abs_dist = abs(dist);
                    
                    vec4 out_color = color;
    //                out_color.x = u;
    //                out_color.y = v;
    //                out_color.z = 0;
    //                if(abs_dist < aa_threshold) 
    //                    out_color = color;
    
                    out_color.w = min(-dist/(aa_threshold + 0.0000001), 1);
                    if(out_color.w <= 0) 
                        discard;
                    
                    frag_color = out_color;
                }
                """);

        Matrix4f view_matrix = new Matrix4f();
        Matrix4f model_matrix = new Matrix4f();
        float rotation = 0;
        float rotate_speed = 1;
        float zoom = 1;

        glfwShowWindow(window);
        long lastTime = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {
            long thisTime = System.nanoTime();
            float dt = (thisTime - lastTime) * 1E-9f;
            lastTime = thisTime;

            glfwPollEvents();

            if(true) {
                float speed = 2f;
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

                view_matrix.identity().rotateLocalZ(rotation).translate(position).scale(zoom);
            }

            glClearColor(0.2f, 0.3f, 0.3f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);

//            glUseProgram(shader_color);
            glUseProgram(shader_bezier_color);

            try (MemoryStack stack = stackPush()) {
                double t = glfwGetTime();
                model_matrix.identity();

                setUniform(shader_bezier_color, "color", new Vector4f((float) sin(t), (float) cos(t), 1, 1));
                setUniform(shader_bezier_color, "aa_threshold", 0.5f);

                setUniform(shader_bezier_color, "model", model_matrix, stack);
                setUniform(shader_bezier_color, "view", view_matrix, stack);
            }

            glBindVertexArray(VAO);
            glDrawArrays(GL_TRIANGLES, 0, triangles.length);
//            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            // glBindVertexArray(0); // no need to unbind it every time

            glfwSwapBuffers(window);
        }
    }

    public static boolean setUniform(int program, CharSequence name, float val)
    {
        int location = glGetUniformLocation(program, name);
        if(location == -1)
            return false;

        glUniform1f(location, val);
        return true;
    }

    public static boolean setUniform(int program, CharSequence name, Vector3f val)
    {
        int location = glGetUniformLocation(program, name);
        if(location == -1)
            return false;

        glUniform3f(location, val.x, val.y, val.z);
        return true;
    }

    public static boolean setUniform(int program, CharSequence name, Vector4f val)
    {
        int location = glGetUniformLocation(program, name);
        if(location == -1)
        {
            System.err.println(STR."program \{program} couldnt find uniform '\{name}'");
            return false;
        }

        glUniform4f(location, val.x, val.y, val.z, val.w);
        return true;
    }

    public static boolean setUniform(int program, CharSequence name, Matrix4f val, MemoryStack stack)
    {
        int location = glGetUniformLocation(program, name);
        if(location == -1)
        {
            System.err.println(STR."program \{program} couldnt find uniform '\{name}'");
            return false;
        }

        glUniformMatrix4fv(location, false, val.get(stack.mallocFloat(16)));
        return true;
    }

    public static int compileProgramSource(CharSequence fragment, CharSequence vertex)
    {
        int gridProgram = glCreateProgram();
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, fragment);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == 0) {
            String shaderLog = glGetShaderInfoLog(vs);
            System.err.println(shaderLog);
            throw new AssertionError("Could not compile vertex shader");
        }

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, vertex);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == 0) {
            String shaderLog = glGetShaderInfoLog(fs);
            System.err.println(shaderLog);
            throw new AssertionError("Could not compile fragment shader");
        }

        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String programLog = glGetProgramInfoLog(program);
            System.err.println(programLog);
            throw new AssertionError("Could not link program");
        }
        return program;
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
