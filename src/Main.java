import static java.lang.Math.*;
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
import static org.lwjgl.system.Checks.CHECKS;
import static org.lwjgl.system.Checks.check;
import static org.lwjgl.system.JNI.callPPV;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static java.util.Arrays.*;

import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import org.joml.*;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.NativeType;

public class Main {
    private int width = 1200;
    private int height = 800;
    private int mouseX, mouseY;
    private boolean viewing;

    private final Matrix4f mat = new Matrix4f();
    private final Quaternionf orientation = new Quaternionf();
    private final Vector3f position = new Vector3f(0, 0, 0);
    private boolean[] keyDown = new boolean[GLFW_KEY_LAST + 1];

    public static final int KB = 1024;
    public static final int MB = 1024*1024;
    public static final int GB = 1024*1024*1024;
    public static final Matrix4f MAT4_ID = new Matrix4f().identity();

    public static class Frame_Arena
    {
        public static MemoryStack stack;
    }

    public static int ucast(byte b)
    {
        return (int) b & 0xFF;
    }

    public static int rgba_to_hex(byte r, byte g, byte b, byte a)
    {

        return (ucast(r) << 24) | (ucast(g) << 16) | (ucast(b) << 8) | ucast(a);
    }

    public static byte hex_to_r(int hex)
    {
        return (byte) (hex >> 24);
    }

    public static byte hex_to_g(int hex)
    {
        return (byte) (hex >> 16);
    }

    public static byte hex_to_b(int hex)
    {
        return (byte) (hex >> 8);
    }

    public static byte hex_to_a(int hex)
    {
        return (byte) (hex >> 0);
    }

    public static int rgba_to_hex(float r, float g, float b, float a)
    {
        return rgba_to_hex((byte)(int) (r*255), (byte)(int) (g*255), (byte)(int) (b*255), (byte)(int) (a*255));
    }

    public static class Quadratic_Bezier_Buffer {
        //3 floats position
        //1 uint for color
        //1 uint for flags
        public ByteBuffer buffer;

        public int length;
        public int capacity;
        public boolean grows = true;

        public static int BYTES_PER_VERTEX = 3*Float.BYTES
                + 2*Integer.BYTES;
        public static int VERTICES_PER_SEGMENT = 3;
        public static int BYTES_PER_SEGMENT = VERTICES_PER_SEGMENT*BYTES_PER_VERTEX;

        public Quadratic_Bezier_Buffer(int max_cpu_size)
        {
            this.capacity = max_cpu_size/BYTES_PER_SEGMENT;
            this.buffer = ByteBuffer.allocate(this.capacity*BYTES_PER_SEGMENT);
            this.buffer.order(ByteOrder.nativeOrder());
        }

        public void reserve(int to_size)
        {
            if(grows == false)
                assert to_size <= capacity;
            else if(to_size > capacity)
            {
                capacity = to_size*3/2 + 8;
                buffer = ByteBuffer.wrap(Arrays.copyOf(buffer.array(), BYTES_PER_SEGMENT*capacity));
                buffer.order(ByteOrder.nativeOrder());
            }
            assert buffer != null;
        }

        public void submit(float x1, float y1, float z1,
                           float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           int color, int flags)
        {
            reserve(length + 1);
            byte r = hex_to_r(color);
            byte g = hex_to_g(color);
            byte b = hex_to_b(color);
            byte a = hex_to_a(color);

//            color = 0xFF00FFFF;
            buffer.putFloat(x1);
            buffer.putFloat(y1);
            buffer.putFloat(z1);
            buffer.putInt(color);
//            buffer.put(a);
//            buffer.put(b);
//            buffer.put(g);
//            buffer.put(r);
            buffer.putInt(flags);

            buffer.putFloat(x2);
            buffer.putFloat(y2);
            buffer.putFloat(z2);
            buffer.putInt(color);
//            buffer.put(a);
//            buffer.put(b);
//            buffer.put(g);
//            buffer.put(r);
            buffer.putInt(flags);

            buffer.putFloat(x3);
            buffer.putFloat(y3);
            buffer.putFloat(z3);
            buffer.putInt(color);
//            buffer.put(a);
//            buffer.put(b);
//            buffer.put(g);
//            buffer.put(r);
            buffer.putInt(flags);
            length += 1;
        }

        void reset()
        {
            buffer.clear();
            length = 0;
        }
    }

    public static class Quadratic_Bezier_Render {
        public int VBO;
        public int VAO;
        public int shader;
        public int capacity;
        public float aa_threshold = 0.5f;
        public ByteBuffer temp_buffer;

        public static int VERTICES_PER_SEGMENT = Quadratic_Bezier_Buffer.VERTICES_PER_SEGMENT;
        public static int BYTES_PER_SEGMENT = Quadratic_Bezier_Buffer.BYTES_PER_SEGMENT;
        public static int BYTES_PER_VERTEX = Quadratic_Bezier_Buffer.BYTES_PER_VERTEX;

        public Quadratic_Bezier_Render(int max_gpu_size)
        {
            capacity = max_gpu_size/BYTES_PER_SEGMENT;
            temp_buffer = ByteBuffer.allocateDirect(capacity*BYTES_PER_SEGMENT);
            //init gpu buffers
            {
                VAO = glGenVertexArrays();
                VBO = glGenBuffers();
                glBindVertexArray(VAO);

                glBindBuffer(GL_ARRAY_BUFFER, VBO);
                glBufferData(GL_ARRAY_BUFFER, (long) capacity*BYTES_PER_SEGMENT, GL_STREAM_DRAW);

                long pos = 0;
                glVertexAttribPointer(0, 3, GL_FLOAT, false, BYTES_PER_VERTEX, pos); pos += 3*Float.BYTES; //position
                glVertexAttribIPointer(1, 1, GL_UNSIGNED_INT, BYTES_PER_VERTEX, pos); pos += Integer.BYTES; //color
                glVertexAttribIPointer(2, 1, GL_UNSIGNED_INT, BYTES_PER_VERTEX, pos); pos += Integer.BYTES; //flags

                glEnableVertexAttribArray(0);
                glEnableVertexAttribArray(1);
                glEnableVertexAttribArray(2);

                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindVertexArray(0);
            }

            //init shader
            this.shader = compileProgramSource(
                """
                #version 330 core
                layout (location = 0) in vec3 vertex_pos;
                layout (location = 1) in uint vertex_color;
                layout (location = 2) in uint vertex_flags;
                out vec2 uv;
                out vec4 color;
                flat out uint flags;
                
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
                    
//                    uint ucol = uint(0xFFFF00FF);
                    uint ucol = uint(vertex_color);
                    uint r = (ucol >> 24) & uint(0xFF);
                    uint g = (ucol >> 16) & uint(0xFF);
                    uint b = (ucol >> 8) & uint(0xFF);
                    uint a = (ucol >> 0) & uint(0xFF);
                    
                    flags = uint(vertex_flags);
                    color.rgba = vec4(r/255.0, g/255.0, b/255.0, a/255.0);
                    vec4 pos = view * model * vec4(vertex_pos, 1.0f);
                    gl_Position = pos;
                }
                """,
                """
                #version 330 core
                out vec4 frag_color;
                
                in vec2 uv;
                in vec4 color;
                flat in uint flags;
                uniform float aa_threshold;
                
                void main()
                {
//                    frag_color = vec4(1, 1, 1, 1);
//                    return;
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
    
                    out_color.w = min(-dist/(aa_threshold + 0.0000001), 1);
                    if(out_color.w <= 0) 
                        discard;
                    
                    frag_color = out_color;
                }
                """);
        }

        public void render(Matrix4f model_or_null, Matrix4f view_or_null, Quadratic_Bezier_Buffer... batches)
        {
            if(batches == null || batches.length == 0)
                return;

            Matrix4f model = model_or_null == null ? MAT4_ID : model_or_null;
            Matrix4f view = view_or_null == null ? MAT4_ID : view_or_null;

            temp_buffer.clear();

            assert capacity > 0;
            try (MemoryStack stack = stackPush()) {
                //Set once global state
                glBindVertexArray(VAO);
                glBindBuffer(GL_ARRAY_BUFFER, VBO);
                glUseProgram(shader);
                setUniform(shader, "model", model, stack);
                setUniform(shader, "view", view, stack);
                setUniform(shader, "aa_threshold", aa_threshold);

                //Draws each batch. Iterates through all batches and copies its data to the gpu buffer.
                //Once the gpu buffer is full or iterated through all batches flushes it to the screen.
                //In case only half of a batch got rendered at once, adds len to skip_len. Skip len is skipped
                // the next time around.
                int skip_len = 0;
                int filled = 0;
                for(int batch_i = 0; batch_i < batches.length; batch_i ++)
                {
                    Quadratic_Bezier_Buffer batch = batches[batch_i];
                    int len = min(capacity - filled, batch.length - skip_len);
                    if (len != 0)
                    {
                        temp_buffer.put(filled*BYTES_PER_SEGMENT, batch.buffer.array(), skip_len*BYTES_PER_SEGMENT, len*BYTES_PER_SEGMENT);
                        filled += len;
                    }

                    assert filled <= capacity;

                    //if full or last batch flush to the screen
                    if (filled == capacity || batch_i == batches.length - 1) {
                        if(filled == 0)
                            break;

                        glBufferSubData(GL_ARRAY_BUFFER, 0, temp_buffer);
                        glDrawArrays(GL_TRIANGLES, 0, filled*VERTICES_PER_SEGMENT);
                        filled = 0;
                        skip_len += len;
                        //re-evaluate this batch in case that not all of it fit inside this draw call
                        batch_i --;
                    }
                    else
                        skip_len = 0;
                }
            }
        }
    }

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
//        glDebugMessageCallback(debugProc, NULL);

        glClearColor(0.7f, 0.8f, 0.9f, 1);
        glEnable(GL_BLEND);
//        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        test_rgba_hex_conv(0xFF00FFFF);
        test_rgba_hex_conv(0x0F00FF00);
        test_rgba_hex_conv(0x33333333);

        Quadratic_Bezier_Render bezier_render = new Quadratic_Bezier_Render(64*MB);
        Quadratic_Bezier_Buffer bezier_buffer = new Quadratic_Bezier_Buffer(64*MB);

        Matrix4f view_matrix = new Matrix4f();
        Matrix4f model_matrix = new Matrix4f();
        float rotation = 0;
        float rotate_speed = 1;
        float zoom = 1;

        glfwShowWindow(window);
        long lastTime = System.nanoTime();
        while (!glfwWindowShouldClose(window)) {

            try (MemoryStack frame_stack = stackPush()) {
                Frame_Arena.stack = frame_stack;

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

                double t = glfwGetTime();
                double t1 = t + 1;

                bezier_buffer.submit(
                    0, 1, 0, //p1
                    0, 0, 0, //p2
                    1, 0, 0, //p3
                    rgba_to_hex((float) (sin(t)*sin(t)), (float) (cos(t)*cos(t)), 1, 1), //color
                    0 //flags
                );

                bezier_buffer.submit(
                        -0.5f, 0, 0, //p1
                        0, 2, 0, //p2
                        0.5f, 0, 0, //p3
                        rgba_to_hex((float) (sin(t1)*sin(t1)), 1, (float) (cos(t1)*cos(t1)), 1), //color
                        0 //flags
                );
                bezier_render.render(model_matrix, view_matrix, bezier_buffer);
                bezier_buffer.reset();

                glfwSwapBuffers(window);
            }
        }
    }

    public static void test_rgba_hex_conv(int hex)
    {
        byte r = hex_to_r(hex);
        byte g = hex_to_g(hex);
        byte b = hex_to_b(hex);
        byte a = hex_to_a(hex);

        int back = rgba_to_hex(r,g,b,a);
        assert back == hex;
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

//    void gl_debug_output_enable()
//    {
//        int flags = 0;
//        glGetIntegerv(GL_CONTEXT_FLAGS, &flags);
//        if (flags & GL_CONTEXT_FLAG_DEBUG_BIT)
//        {
//            LOG_INFO(DEBUG_OUTPUT_CHANEL, "Debug info enabled");
//            glEnable(GL_DEBUG_OUTPUT);
//            glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
//            glDebugMessageCallback((int source, int type, int id, int severity, int length, long message, long userParam) -> {
//
//                System.err.println(programLog);
//            }, NULL);
//            glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DONT_CARE, 0, true);
//        }
//
//        gladSetGLPostCallback(gl_post_call_gl_callback);
//        gladInstallGLDebug();
//    }

    public static void main(String[] args) {
        new Main().run();
    }
}
