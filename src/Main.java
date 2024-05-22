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


    private void run() {
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
;
        glEnable(GL_BLEND);
        glEnable(GL_FRAMEBUFFER_SRGB);
//        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);

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

        Colorspace clear_color = new Colorspace(0.2f, 0.3f, 0.3f).srgb_to_lin();
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
                        position.add(move_dir.normalize().mul(dt * speed).rotateZ(-rotation));

                    view_matrix.identity().rotateLocalZ(rotation).translate(position).scale(zoom).scaleXY((float)height/width, 1);
                }

                glClearColor(clear_color.x, clear_color.y, clear_color.z, clear_color.a);
                glClear(GL_COLOR_BUFFER_BIT);

                double t = glfwGetTime();
                double t1 = t + 1;

                bezier_buffer.submit(
                    -0.5f, 0, 0xFF0000, //p1
                    0.5f, 0.5f, 0x00FF00, //p2
                    0.5f, 0, 0x00FF00, //p3
                    Quadratic_Bezier_Buffer.FLAG_SOLID | Quadratic_Bezier_Buffer.FLAG_OKLAB //flags
                );
                bezier_buffer.submit(
                    -0.5f, 0.5f, 0xFF0000, //p2
                    0.5f, 0.5f, 0x00FF00, //p1
                    -0.5f, 0, 0xFF0000, //p3
                    Quadratic_Bezier_Buffer.FLAG_SOLID | Quadratic_Bezier_Buffer.FLAG_OKLAB //flags
                );

                bezier_buffer.submit(
                        0, 0.7f, //p1
                        0, 0, //p2
                        0.7f, 0, //p3
                        Colorspace.rgba_to_hex((float) (sin(t)*sin(t)), (float) (cos(t)*cos(t)), 1, 0.5f), //color
                        0 //flags
                );
                bezier_buffer.submit(
                        0.7f, 0.7f, //p1
                        0, 0, //p2
                        0.7f, 0, //p3
                        0x80AAAAFF, //color
                        0 //flags
                );

                bezier_render.render(model_matrix, view_matrix, bezier_buffer);
                bezier_buffer.reset();

                glfwSwapBuffers(window);
            }
        }
    }

    public static class Quadratic_Bezier_Buffer {
        //2 floats position
        //1 uint for color
        //1 uint for flags
        public ByteBuffer buffer;

        public int length;
        public int capacity;
        public boolean grows = true;

        public static int BYTES_PER_VERTEX = 2*Float.BYTES + 2*Integer.BYTES;
        public static int VERTICES_PER_SEGMENT = 3;
        public static int BYTES_PER_SEGMENT = VERTICES_PER_SEGMENT*BYTES_PER_VERTEX;
        public static int FLAG_CONCAVE = 1;
        public static int FLAG_SOLID = 2;
        public static int FLAG_OKLAB = 4;

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

        public void submit(float x1, float y1, int color1,
                           float x2, float y2, int color2,
                           float x3, float y3, int color3,
                           int flags)
        {
            reserve(length + 1);

            buffer.putFloat(x1);
            buffer.putFloat(y1);
            buffer.putInt(color1);
            buffer.putInt(flags);

            buffer.putFloat(x2);
            buffer.putFloat(y2);
            buffer.putInt(color2);
            buffer.putInt(flags);

            buffer.putFloat(x3);
            buffer.putFloat(y3);
            buffer.putInt(color3);
            buffer.putInt(flags);
            length += 1;
        }

        public void submit(float x1, float y1,
                           float x2, float y2,
                           float x3, float y3,
                           int color, int flags)
        {
            //Doesnt make sense to use fancy interpolation for triangle where all color are the same!
            submit(x1, y1, color, x2, y2, color, x3, y3, color, flags & ~FLAG_OKLAB);
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
                glVertexAttribPointer(0, 2, GL_FLOAT, false, BYTES_PER_VERTEX, pos); pos += 2*Float.BYTES; //position
                glVertexAttribIPointer(1, 1, GL_UNSIGNED_INT, BYTES_PER_VERTEX, pos); pos += Integer.BYTES; //color
                glVertexAttribIPointer(2, 1, GL_UNSIGNED_INT, BYTES_PER_VERTEX, pos); pos += Integer.BYTES; //flags

                glEnableVertexAttribArray(0);
                glEnableVertexAttribArray(1);
                glEnableVertexAttribArray(2);

                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindVertexArray(0);
            }

            //init shader
            this.shader = compile_shader_source_prepend(
                    """
                    #version 330 core
                    layout (location = 0) in vec2 vertex_pos;
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
                        
                        uint ucol = uint(vertex_color);
                        uint a = uint(255) - (ucol >> 24) & uint(0xFF);
                        uint r = (ucol >> 16) & uint(0xFF);
                        uint g = (ucol >> 8) & uint(0xFF);
                        uint b = (ucol >> 0) & uint(0xFF);
                        
                        flags = uint(vertex_flags);
                        vec4 out_color = vec4(r/255.0, g/255.0, b/255.0, a/255.0);
                        out_color.xyz = srgb_to_linear(out_color.xyz);
                        if((vertex_flags & uint(FLAG_OKLAB)) != uint(0))
                            out_color.xyz = linear_to_oklab(out_color.xyz);
                        
                        color = out_color;
                        vec4 pos = view * model * vec4(vertex_pos, 0, 1);
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
                        float dist = -999999;
                        if((flags & uint(FLAG_SOLID)) == uint(0))
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
                            dist = F/(length(J_F) + 0.0000001);
                        }
                        
                        if((flags & uint(FLAG_CONCAVE)) != uint(0))
                            dist = -dist;
                           
                        if((flags & uint(FLAG_CONCAVE)) != uint(0))
                            dist = -dist;
                            
                        float abs_dist = abs(dist);
                        vec4 out_color = color;
                        if((flags & uint(FLAG_OKLAB)) != uint(0))
                            out_color.xyz = oklab_to_linear(out_color.xyz);
                            
                        float alpha_factor = min(-dist/(aa_threshold + 0.0000001), 1);
                        if(alpha_factor <= 0) 
                            discard;
                        
                        out_color.w *= alpha_factor;
                        frag_color = out_color;
                    }
                    """,
                    """
                    #define FLAG_CONCAVE    1
                    #define FLAG_SOLID      2
                    #define FLAG_OKLAB      4
                    
                    vec3 linear_to_srgb(vec3 linearRGB)
                    {
                        bvec3 cutoff = lessThan(linearRGB, vec3(0.0031308));
                        vec3 higher = vec3(1.055)*pow(linearRGB, vec3(1.0/2.4)) - vec3(0.055);
                        vec3 lower = linearRGB * vec3(12.92);
                    
                        return mix(higher, lower, cutoff);
                    }
                    
                    vec3 srgb_to_linear(vec3 sRGB)
                    {
                        bvec3 cutoff = lessThan(sRGB, vec3(0.04045));
                        vec3 higher = pow((sRGB + vec3(0.055))/vec3(1.055), vec3(2.4));
                        vec3 lower = sRGB/vec3(12.92);
                    
                        return mix(higher, lower, cutoff);
                    }
                    
                    vec3 linear_to_oklab(vec3 col) {
                        const mat3 lrgb2cone = mat3(
                            0.412165612, 0.211859107, 0.0883097947,
                            0.536275208, 0.6807189584, 0.2818474174,
                            0.0514575653, 0.107406579, 0.6302613616
                        );
                    
                        const mat3 cone2lab = mat3(
                            +0.2104542553, +1.9779984951, +0.0259040371,
                            +0.7936177850, -2.4285922050, +0.7827717662,
                            +0.0040720468, +0.4505937099, -0.8086757660
                        );
                        
                        col = lrgb2cone * col;
                        col = pow(col, vec3(1.0 / 3.0));
                        col = cone2lab * col;
                        return col;
                    }
                    
                    vec3 oklab_to_linear(highp vec3 col) {
                        const mat3 lab2cone = mat3(
                            +4.0767416621, -1.2684380046, -0.0041960863,
                            -3.3077115913, +2.6097574011, -0.7034186147,
                            +0.2309699292, -0.3413193965, +1.7076147010
                        );
                        
                        const mat3 cone2lrgb = mat3(
                            1, 1, 1,
                            +0.3963377774f, -0.1055613458f, -0.0894841775f,
                            +0.2158037573f, -0.0638541728f, -1.2914855480f
                        );
                    
                        col = cone2lrgb * col;
                        col = col * col * col;
                        col = lab2cone * col;
                        return col;
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

    public static void test_rgba_hex_conv(int hex)
    {
        byte r = hex_r(hex);
        byte g = hex_g(hex);
        byte b = hex_b(hex);
        byte a = hex_a(hex);

        int back = Colorspace.rgba_to_hex(r,g,b,a);
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

    public static StringBuilder prepend_shader_source(CharSequence shader, CharSequence prepend)
    {
        StringBuilder builder = new StringBuilder(shader);

        String version_string = "#version";
        int version_i = builder.indexOf("#version");
        if(version_i == -1)
        {
            builder.insert(0, "#version 330 core\n");
            version_i = 0;
        }

        version_i += version_string.length();
        if(prepend != null && prepend.length() > 0)
        {
            int new_line_i = builder.indexOf("\n", version_i);
            if(new_line_i == -1)
                new_line_i = builder.length();

            builder.insert(new_line_i, "\n");
            builder.insert(new_line_i, prepend);
            builder.insert(new_line_i, "\n");
        }

        return builder;
    }

    public static int compile_shader_source(CharSequence vertex, CharSequence fragment)
    {
        return compile_shader_source_prepend(vertex, fragment, "");
    }

    public static int compile_shader_source_prepend(CharSequence vertex, CharSequence fragment, CharSequence prepend)
    {
        StringBuilder fragment_fixed = prepend_shader_source(fragment, prepend);
        StringBuilder vertex_fixed = prepend_shader_source(vertex, prepend);
        return compile_raw_shader_source(vertex_fixed, fragment_fixed);
    }

    public static StringBuilder prepend_lines_with_numbers(CharSequence seq)
    {
        StringBuilder out = new StringBuilder();
        String separator = "\n";
        String str = seq.toString();
        int index = 0;
        int line_num = 0;
        while (index < str.length())
        {
            line_num += 1;
            int nextIndex = str.indexOf(separator, index);

            if(nextIndex == -1)
                nextIndex = str.length();
            else
                nextIndex += separator.length();

            String line = str.substring(index, nextIndex);

            // do something with line.
            out.append(line_num + " " + line);
            index = nextIndex;
        }

        return out;
    }

    public static int compile_raw_shader_source(CharSequence vertex, CharSequence fragment)
    {
        int gridProgram = glCreateProgram();
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertex);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == 0) {
            String shaderLog = glGetShaderInfoLog(vs);
            System.err.println(shaderLog);
            System.err.println(prepend_lines_with_numbers(vertex));
            throw new AssertionError("Could not compile vertex shader");
        }

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragment);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == 0) {
            String shaderLog = glGetShaderInfoLog(fs);
            System.err.println(shaderLog);
            System.err.println(prepend_lines_with_numbers(fragment));
            throw new AssertionError("Could not compile fragment shader");
        }

        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String programLog = glGetProgramInfoLog(program);
            System.err.println(programLog);
            System.err.println(prepend_lines_with_numbers(vertex));
            System.err.println(prepend_lines_with_numbers(fragment));
            throw new AssertionError("Could not link program");
        }
        return program;
    }

    //===================== Colors =====================
    public static class Colorspace {
        public float x; //r
        public float y; //g
        public float z; //b
        public float a; //alpha

        public static int ucast(byte val)
        {
            return (int) val & 0xFF;
        }

        public static int rgba_to_hex(byte r, byte g, byte b, byte a)
        {
            return ucast((byte)(255 - a)) << 24 | ucast(r) << 16 | ucast(g) << 8 | ucast(b);
        }

        public static int rgba_to_hex(float r, float g, float b, float a)
        {
            return rgba_to_hex((byte)(int) (r*255), (byte)(int) (g*255), (byte)(int) (b*255), (byte)(int) (a*255));
        }

        public static double lin_to_srgb(double c)
        {
            return c >= 0.0031308 ? 1.055 * Math.pow(c, 1 / 2.4) - 0.055 : 12.92 * c;
        }

        public static double srgb_to_lin(double c)
        {
            return c >= 0.04045 ? Math.pow((c + 0.055) / 1.055, 2.4) : c / 12.92;
        }

        public Colorspace(float _x, float _z, float _y, float _a)
        {
            x = _x;
            y = _y;
            z = _z;
            a = _a;
        }

        public Colorspace(float _x, float _z, float _y)
        {
            x = _x;
            y = _y;
            z = _z;
            a = 1;
        }

        public Colorspace(int hex)
        {
            x = hex_r(hex)/255f;
            y = hex_g(hex)/255f;
            z = hex_b(hex)/255f;
            a = hex_a(hex)/255f;
        }

        public int to_hex()
        {
            return rgba_to_hex(x, y, z, a);
        }

        public Colorspace lin_to_srgb()
        {
            x = (float) lin_to_srgb(x);
            y = (float) lin_to_srgb(y);
            z = (float) lin_to_srgb(z);
            return this;
        }

        public Colorspace srgb_to_lin()
        {
            x = (float) srgb_to_lin(x);
            y = (float) srgb_to_lin(y);
            z = (float) srgb_to_lin(z);
            return this;
        }

        public Colorspace lin_to_oklab()
        {
            float l = 0.4122214708f*x + 0.5363325363f*y + 0.0514459929f*z;
            float m = 0.2119034982f*x + 0.6806995451f*y + 0.1073969566f*z;
            float s = 0.0883024619f*x + 0.2817188376f*y + 0.6299787005f*z;

            float l_ = (float) Math.cbrt(l);
            float m_ = (float) Math.cbrt(m);
            float s_ = (float) Math.cbrt(s);

            x = 0.2104542553f*l_ + 0.7936177850f*m_ - 0.0040720468f*s_;
            y = 1.9779984951f*l_ - 2.4285922050f*m_ + 0.4505937099f*s_;
            z = 0.0259040371f*l_ + 0.7827717662f*m_ - 0.8086757660f*s_;
            return this;
        }

        public Colorspace oklab_to_lin()
        {
            float l_ = 1*x + 0.3963377774f*y + 0.2158037573f*z;
            float m_ = 1*x - 0.1055613458f*y - 0.0638541728f*z;
            float s_ = 1*x - 0.0894841775f*y - 1.2914855480f*z;

            float l = l_*l_*l_;
            float m = m_*m_*m_;
            float s = s_*s_*s_;

            x = +4.0767416621f*l - 3.3077115913f*m + 0.2309699292f*s;
            y = -1.2684380046f*l + 2.6097574011f*m - 0.3413193965f*s;
            z = -0.0041960863f*l - 0.7034186147f*m + 1.7076147010f*s;
            return this;
        }
    }

    public static byte hex_t(int hex)
    {
        return (byte) (hex >> 24);
    }

    public static byte hex_a(int hex)
    {
        return (byte) (255 - (byte) (hex >> 24));
    }

    public static byte hex_r(int hex)
    {
        return (byte) (hex >> 16);
    }

    public static byte hex_g(int hex)
    {
        return (byte) (hex >> 8);
    }

    public static byte hex_b(int hex)
    {
        return (byte) (hex);
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
