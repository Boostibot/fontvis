import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.Arrays;

import static java.lang.Math.abs;
import static java.lang.Math.min;
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
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.system.MemoryStack.stackPush;

public class Render {
    public static final Matrix4f MAT4_ID = new Matrix4f().identity();

    public static final class Bezier_Buffer {
        //2 floats position
        //2 floats uv
        //1 uint for color
        //1 uint for flags
        public float[] buffer = new float[0];

        public int length;
        public int capacity;
        public boolean grows = true;

        public static final int BYTES_PER_VERTEX = 4*Float.BYTES + 2*Integer.BYTES;
        public static final int VERTICES_PER_SEGMENT = 3;
        public static final int BYTES_PER_SEGMENT = VERTICES_PER_SEGMENT*BYTES_PER_VERTEX;
        public static final int FLOATS_PER_VERTEX = BYTES_PER_VERTEX / Float.BYTES;
        public static final int FLOATS_PER_SEGMENT = BYTES_PER_SEGMENT / Float.BYTES;

        //default rendering mode: solid triangle with points p1, p2, p3
        public static final int FLAG_BEZIER = 1; //the triangle is interpreted as quadratic bezier curve where p1, p3 are endpoints and p2 is control point
        public static final int FLAG_CIRCLE = 2; //the triangle is interpreted as circle segment where p2 is the center, and p1, p2 mark the segment sides. The radius is 1 in uv space.
        public static final int FLAG_INVERSE = 4; //the triangle shape is filled inside out. When combined with rendering triangles, makes the triangle entirely invisible!
        public static final int FLAG_OKLAB = 8; //vertex colors are interpolated in the OKLAB colorspace instead of linear colorspace.

        public Bezier_Buffer() {}

        public Bezier_Buffer(int max_cpu_size, boolean grows)
        {
            this.capacity = max_cpu_size/BYTES_PER_SEGMENT;
            if(max_cpu_size > 0)
                this.buffer = new float[this.capacity*FLOATS_PER_SEGMENT];
            this.grows = grows;
        }

        public void free_all()
        {
            buffer = new float[0];
            length = 0;
            capacity = 0;
            grows = true;
        }

        void reset()
        {
            length = 0;
        }

        public void reserve(int to_size)
        {
            if(to_size > capacity)
            {
                assert grows;
                capacity = capacity*3/2 + 8;
                if(capacity < to_size)
                    capacity = to_size;

                buffer = Arrays.copyOf(buffer, capacity*FLOATS_PER_SEGMENT);
            }
            assert buffer != null;
        }

        public void copy_to(Bezier_Buffer into, int into_offset, int from, int to)
        {
            System.arraycopy(this.buffer, from*FLOATS_PER_SEGMENT, into.buffer, into_offset*FLOATS_PER_SEGMENT, (to - from)*FLOATS_PER_SEGMENT);
        }

        public void submit_buffer(float[] vertex_data, int float_from, int float_to)
        {
            int segments = (float_to - float_from)/FLOATS_PER_SEGMENT;
            reserve(length + segments);
            System.arraycopy(vertex_data, float_from, buffer, length*FLOATS_PER_SEGMENT, segments*FLOATS_PER_SEGMENT);
            length += segments;
        }

        public void submit_buffer(Bezier_Buffer vertex_data, int from, int to)
        {
            reserve(length + (to - from));
            System.arraycopy(vertex_data.buffer, from*FLOATS_PER_SEGMENT, buffer, length*FLOATS_PER_SEGMENT, (to - from)*FLOATS_PER_SEGMENT);
            length += (to - from);
        }

        public void submit_buffer(Bezier_Buffer vertex_data, int from, int to, Matrix3f transform, boolean recolor, int color)
        {
            int before = length;
            submit_buffer(vertex_data, from, to);
            transform(before, length, transform, recolor, color);
        }

        public void submit_buffer(Bezier_Buffer vertex_data, Matrix3f transform, boolean recolor, int color)
        {
            submit_buffer(vertex_data, 0, vertex_data.length, transform, recolor, color);
        }

        public void transform(int from, int to, Matrix3f transform, boolean recolor, int color)
        {
            int start = from*FLOATS_PER_SEGMENT;
            int end = to*FLOATS_PER_SEGMENT;
            for(int i = start; i < end; i += FLOATS_PER_SEGMENT)
            {
                if(transform != null)
                {
                    float x1 = buffer[i + 0*FLOATS_PER_VERTEX + 0];
                    float y1 = buffer[i + 0*FLOATS_PER_VERTEX + 1];

                    float x2 = buffer[i + 1*FLOATS_PER_VERTEX + 0];
                    float y2 = buffer[i + 1*FLOATS_PER_VERTEX + 1];

                    float x3 = buffer[i + 2*FLOATS_PER_VERTEX + 0];
                    float y3 = buffer[i + 2*FLOATS_PER_VERTEX + 1];

                    float x1_ = x1;
                    x1 = transform.m00*x1_ + transform.m10*y1 + transform.m20;
                    y1 = transform.m01*x1_ + transform.m11*y1 + transform.m21;

                    float x2_ = x2;
                    x2 = transform.m00*x2_ + transform.m10*y2 + transform.m20;
                    y2 = transform.m01*x2_ + transform.m11*y2 + transform.m21;

                    float x3_ = x3;
                    x3 = transform.m00*x3_ + transform.m10*y3 + transform.m20;
                    y3 = transform.m01*x3_ + transform.m11*y3 + transform.m21;

                    buffer[i + 0*FLOATS_PER_VERTEX + 0] = x1;
                    buffer[i + 0*FLOATS_PER_VERTEX + 1] = y1;

                    buffer[i + 1*FLOATS_PER_VERTEX + 0] = x2;
                    buffer[i + 1*FLOATS_PER_VERTEX + 1] = y2;

                    buffer[i + 2*FLOATS_PER_VERTEX + 0] = x3;
                    buffer[i + 2*FLOATS_PER_VERTEX + 1] = y3;
                }

                if(recolor) {
                    buffer[i + 0*FLOATS_PER_VERTEX + 4] = Float.intBitsToFloat(color);
                    buffer[i + 1*FLOATS_PER_VERTEX + 4] = Float.intBitsToFloat(color);
                    buffer[i + 2*FLOATS_PER_VERTEX + 4] = Float.intBitsToFloat(color);
                }
            }
        }

        public void transform_to_rand_colors(int from, int to, long seed, int transparency)
        {
            int start = from*FLOATS_PER_SEGMENT;
            int end = to*FLOATS_PER_SEGMENT;

            long rand_state = seed;
            for(int i = start; i < end; i += FLOATS_PER_SEGMENT)
            {
                long rand = 0;
                //splitmix random
                {
                    rand_state += 0x9e3779b97f4a7c15L;
                    long z = rand_state;
                    z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
                    z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
                    rand = z ^ (z >>> 31);
                }

                int color = (int) (transparency << 24) | (int) (rand >>> (64 - 24));

                buffer[i + 0*FLOATS_PER_VERTEX + 4] = Float.intBitsToFloat(color);
                buffer[i + 1*FLOATS_PER_VERTEX + 4] = Float.intBitsToFloat(color);
                buffer[i + 2*FLOATS_PER_VERTEX + 4] = Float.intBitsToFloat(color);
            }
        }

        public void recolor(int from, int to, int color)
        {
            transform(from, to, null, true, color);
        }

        public void transform(int from, int to, Matrix3f transform)
        {
            transform(from, to, transform, false, 0);
        }

        public void submit(float x1, float y1, float u1, float v1, int color1,
                           float x2, float y2, float u2, float v2, int color2,
                           float x3, float y3, float u3, float v3, int color3,
                           int flags, Matrix3f transform_or_null)
        {
            //If transform is passed in apply it to all vertices (but not uvs!)
            if(transform_or_null != null)
            {
                float x1_ = x1;
                x1 = transform_or_null.m00*x1_ + transform_or_null.m10*y1 + transform_or_null.m20;
                y1 = transform_or_null.m01*x1_ + transform_or_null.m11*y1 + transform_or_null.m21;

                float x2_ = x2;
                x2 = transform_or_null.m00*x2_ + transform_or_null.m10*y2 + transform_or_null.m20;
                y2 = transform_or_null.m01*x2_ + transform_or_null.m11*y2 + transform_or_null.m21;

                float x3_ = x3;
                x3 = transform_or_null.m00*x3_ + transform_or_null.m10*y3 + transform_or_null.m20;
                y3 = transform_or_null.m01*x3_ + transform_or_null.m11*y3 + transform_or_null.m21;
            }

            reserve(length + 1);
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*0 + 0] = x1;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*0 + 1] = y1;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*0 + 2] = u1;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*0 + 3] = v1;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*0 + 4] = Float.intBitsToFloat(color1);
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*0 + 5] = Float.intBitsToFloat(flags);;

            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*1 + 0] = x2;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*1 + 1] = y2;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*1 + 2] = u2;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*1 + 3] = v2;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*1 + 4] = Float.intBitsToFloat(color2);
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*1 + 5] = Float.intBitsToFloat(flags);;

            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*2 + 0] = x3;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*2 + 1] = y3;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*2 + 2] = u3;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*2 + 3] = v3;
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*2 + 4] = Float.intBitsToFloat(color3);
            buffer[length*FLOATS_PER_SEGMENT + FLOATS_PER_VERTEX*2 + 5] = Float.intBitsToFloat(flags);;
            length += 1;
        }

        public void submit_triangle(
                float x1, float y1, int color1,
                float x2, float y2, int color2,
                float x3, float y3, int color3,
                int flags, Matrix3f transform_or_null)
        {
            submit(x1, y1, 0, 0, color1, x2, y2, 0.5f, 0, color2, x3, y3, 1, 1, color3, flags, transform_or_null);
        }

        public void submit_circle_segment(
                float x1, float y1, int color1,
                float x2, float y2, int color2,
                float x3, float y3, int color3,
                float r, int flags, Matrix3f transform_or_null)
        {
            submit(x1, y1, (x1 - x2)/r, (y1 - y2)/r, color1,
                    x2, y2, 0, 0, color2,
                    x3, y3, (x3 - x2)/r, (y3 - y2)/r, color3,
                    flags | FLAG_CIRCLE & ~FLAG_BEZIER, transform_or_null);
        }

        public void submit_triangle(
                float x1, float y1,
                float x2, float y2,
                float x3, float y3, int color,
                int flags, Matrix3f transform_or_null)
        {
            submit_triangle(x1, y1, color, x2, y2, color, x3, y3, color, flags & ~FLAG_OKLAB, transform_or_null);
        }

        public void submit_circle(float x, float y, float r, int color1, int color2, int color3, int flags, Matrix3f transform_or_null)
        {
            float sqrt3 = 1.7320508075688772f;
            submit(
                    x + -sqrt3*r, y + -r, -sqrt3, -1, color1,
                    x + sqrt3*r, y + -r, sqrt3, -1, color2,
                    x, y + 2*r, 0, 2, color3,
                    Bezier_Buffer.FLAG_CIRCLE | flags, transform_or_null
            );
        }

        public void submit_circle(float x, float y, float r, int color, Matrix3f transform_or_null)
        {
            submit_circle(x,y, r, color, color, color, 0, transform_or_null);
        }


        public void submit_rectangle(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int c1, int c2, int c3, int c4, int flags, Matrix3f transform_or_null)
        {
            submit(
                x1, y1, 0, 0, c1,
                x2, y2, 0, 0, c2,
                x3, y3, 0, 0, c3,
                flags, transform_or_null
            );
            submit(
                x3, y3, 0, 0, c3,
                x4, y4, 0, 0, c4,
                x1, y1, 0, 0, c1,
                flags, transform_or_null
            );
        }

        public void submit_rectangle(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color, int flags, Matrix3f transform_or_null)
        {
            submit_rectangle(x1, y1, x2, y2, x3, y3, x4, y4, color, color, color, color, flags, transform_or_null);
        }

        public void submit_rectangle(float x, float y, float width, float height, int c_ul, int c_ur, int c_ll, int c_lr, int flags, Matrix3f transform_or_null)
        {
            float w2 = width/2;
            float h2 = height/2;
            //upper left triangle
            submit(
                    x-w2, y+h2, 0, 0, c_ul,
                    x+w2, y+h2, 0, 0, c_ur,
                    x-w2, y-h2, 0, 0, c_ll,
                    flags, transform_or_null
            );
            //lower right triangle
            submit(
                    x-w2, y-h2, 0, 0, c_ll,
                    x+w2, y+h2, 0, 0, c_ur,
                    x+w2, y-h2, 0, 0, c_lr,
                    flags, transform_or_null
            );
        }


        public void submit_rectangle(float x, float y, float width, float height, int color, Matrix3f transform_or_null)
        {
            submit_rectangle(x, y, width, height, color, color, color, color, 0, transform_or_null);
        }

        public void submit_rounded_rectangle(float x, float y, float width, float height, float corner_radius, int color, Matrix3f transform_or_null)
        {
            if(corner_radius <= 0)
                submit_rectangle(x, y, width, height, color, transform_or_null);
            else
            {
                float w2 = width/2;
                float h2 = height/2;
                float r = corner_radius;
                float eps = 0.0000001f;
                if(r > w2)
                    r = w2;
                if(r > h2)
                    r = h2;
                if(h2 - r < eps)
                {
                    submit_circle(x - w2 + r, y, r, color, transform_or_null);
                    submit_rectangle(x, y, width - 2*r, height, color, transform_or_null);
                    submit_circle(x + w2 - r, y, r, color, transform_or_null);
                }
                else if (w2 - r < eps)
                {
                    submit_circle(x, y - h2 + r, r, color, transform_or_null);
                    submit_rectangle(x, y, width, height - 2*r, color, transform_or_null);
                    submit_circle(x, y + h2 - r, r, color, transform_or_null);
                }
                else
                {
                    //Corners
                    //
                    // All corners are made of a circle filled triangle like so:
                    //      r
                    //    <--->
                    //  ^ 1 - 2
                    // r| |  /
                    //  | | /
                    //  V 3
                    // Where each point 1,2,3 has x and y components.
                    //
                    // The diagram is the case for Upper Left (ul).
                    // All other corners are mirrors this means that
                    // Upper Right is (ur) is ul flipped horizontally,
                    // Lower left (ll) is ul flipped vertically and
                    // Lower Right (lr) is ul flipped both horizontally and vertically.

                    float ul_x1 = x - w2;
                    float ul_y1 = y + h2;
                    float ul_x2 = ul_x1 + r;
                    float ul_y2 = ul_y1;
                    float ul_x3 = ul_x1;
                    float ul_y3 = ul_y1 - r;
                    submit(
                            ul_x2, ul_y2, 1, 0, color,
                            ul_x1, ul_y1, 1, 1, color,
                            ul_x3, ul_y3, 0, 1, color,
                            FLAG_CIRCLE, transform_or_null
                    );

                    float ur_x1 = x + w2;
                    float ur_y1 = y + h2;
                    float ur_x2 = ur_x1 - r;
                    float ur_y2 = ur_y1;
                    float ur_x3 = ur_x1;
                    float ur_y3 = ur_y1 - r;
                    submit(
                            ur_x2, ur_y2, 1, 0, color,
                            ur_x1, ur_y1, 1, 1, color,
                            ur_x3, ur_y3, 0, 1, color,
                            FLAG_CIRCLE, transform_or_null
                    );

                    float ll_x1 = x - w2;
                    float ll_y1 = y - h2;
                    float ll_x2 = ll_x1 + r;
                    float ll_y2 = ll_y1;
                    float ll_x3 = ll_x1;
                    float ll_y3 = ll_y1 + r;
                    submit(
                            ll_x2, ll_y2, 1, 0, color,
                            ll_x1, ll_y1, 1, 1, color,
                            ll_x3, ll_y3, 0, 1, color,
                            FLAG_CIRCLE, transform_or_null
                    );

                    float lr_x1 = x + w2;
                    float lr_y1 = y - h2;
                    float lr_x2 = lr_x1 - r;
                    float lr_y2 = lr_y1;
                    float lr_x3 = lr_x1;
                    float lr_y3 = lr_y1 + r;
                    submit(
                            lr_x2, lr_y2, 1, 0, color,
                            lr_x1, lr_y1, 1, 1, color,
                            lr_x3, lr_y3, 0, 1, color,
                            FLAG_CIRCLE, transform_or_null
                    );

                    //top strip between ul and ul
                    submit(
                            ul_x2, ul_y2, 0, 0, color,
                            ur_x2, ur_y2, 0, 0, color,
                            ul_x3, ul_y3, 0, 0, color,
                            0, transform_or_null
                    );

                    submit(
                            ul_x3, ul_y3, 0, 0, color,
                            ur_x3, ur_y3, 0, 0, color,
                            ur_x2, ur_y2, 0, 0, color,
                            0, transform_or_null
                    );

                    //between all corners
                    submit(
                            ul_x3, ul_y3, 0, 0, color,
                            ur_x3, ur_y3, 0, 0, color,
                            ll_x3, ll_y3, 0, 0, color,
                            0, transform_or_null
                    );
                    submit(
                            ll_x3, ll_y3, 0, 0, color,
                            lr_x3, lr_y3, 0, 0, color,
                            ur_x3, ur_y3, 0, 0, color,
                            0, transform_or_null
                    );

                    //Bottom strip between ll and ll
                    submit(
                            ll_x2, ll_y2, 0, 0, color,
                            lr_x2, lr_y2, 0, 0, color,
                            ll_x3, ll_y3, 0, 0, color,
                            0, transform_or_null
                    );

                    submit(
                            ll_x3, ll_y3, 0, 0, color,
                            lr_x3, lr_y3, 0, 0, color,
                            lr_x2, lr_y2, 0, 0, color,
                            0, transform_or_null
                    );
                }
            }
        }

        public void submit_line(float x1, float y1, float x2, float y2, float width, int color, Matrix3f transform_or_null)
        {
            float w2 = width/2;
            float perpend_x = -(y2 - y1);
            float perpend_y = x2 - x1;
            float perpend_norm = (float) Math.sqrt(perpend_x*perpend_x + perpend_y*perpend_y);
            float scaled_perpend_x = perpend_x*w2/perpend_norm;
            float scaled_perpend_y = perpend_y*w2/perpend_norm;

            float begin_top_x = x1 + scaled_perpend_x;
            float begin_top_y = y1 + scaled_perpend_y;
            float begin_bot_x = x1 - scaled_perpend_x;
            float begin_bot_y = y1 - scaled_perpend_y;

            float end_top_x = x2 + scaled_perpend_x;
            float end_top_y = y2 + scaled_perpend_y;
            float end_bot_x = x2 - scaled_perpend_x;
            float end_bot_y = y2 - scaled_perpend_y;

            //submit triangles
            submit(
                    begin_top_x, begin_top_y, 0, 0, color,
                    begin_bot_x, begin_bot_y, 0, 0, color,
                    end_top_x, end_top_y, 0, 0, color,
                    0, transform_or_null
            );
            submit(
                    end_top_x, end_top_y, 0, 0, color,
                    begin_bot_x, begin_bot_y, 0, 0, color,
                    end_bot_x, end_bot_y, 0, 0, color,
                    0, transform_or_null
            );
        }

        public static float hypot(float a, float b)
        {
            return (float) Math.sqrt(a*a + b*b);
        }

        public static final class Line_Connection
        {
            float u_side_x1;
            float u_side_y1;
            float u_side_x2;
            float u_side_y2;

            float v_side_x1;
            float v_side_y1;
            float v_side_x2;
            float v_side_y2;

            boolean has_primitive;
            boolean has_circle; //circles uvs are circle_x/r
            boolean undetermined;
            //if undetermined == false the ordering of u_side_x1 should be paired with v_side_x1
            // and so on. If undetermined == true each point from one side should be paired with the
            // most likely point from the other side. This is obtained by comparing the connected point direction
            // with the direction of the line segment. The point combination most similar is selected.

            float primitive_x1;
            float primitive_y1;
            float primitive_x2;
            float primitive_y2;
            float primitive_x3;
            float primitive_y3;

            float r;
            float circle_x1;
            float circle_y1;
            float circle_x2;
            float circle_y2;
            float circle_x3;
            float circle_y3;
        }

        public static float LINE_CONNECTION_DEF_CUT_CORNER_THRESHOLD = 0.75f;
        public static float LINE_CONNECTION_DEF_PERPEND_EPSILON = 0.00001f;
        static Line_Connection calculate_line_connection(Line_Connection connection, float ux, float uy, float vx, float vy, float r, float sharp_corner_height, boolean do_rounded, float sharp_connection_threshold, float perpend_epsilon)
        {
            //Calculates how to join two line outlines with width=2r. This function only cares about directions of the
            // segments and not the coordinates themselves. The directions of the connected lines are given by
            // vectors `u` and `v`. h denotes the halfway vector which (because u and v are normalized) divides the angle
            // between u and v in half.
            //
            //                  h=u+v        v
            //                   ^      /    /    /
            //                    \    /    /    /
            //                     \  /    /    /
            //                      \/    /    /
            //        <------------- A   /    /     ^
            //                        \ /    /      |
            //      u <----------------0    /       | 2r
            //                             /        |
            //        <-------------------B         V
            //
            // A is shared across all connection styles while B differs/is represented by two points instead.
            // If the angle between v and u is very small thus the corner is very sharp we use "cut corner".
            // The angle of a=90 (sharp_corner_height = cos(a=90)) is here just for illustration as
            // normally we cut at far narrower angles. We need to add additional triangle A,B,B' as shown on the picture.
            //
            //                           2r
            //                       |<------>|
            //                       |   |    |
            //                       |   |    |
            //                       |   |    |
            //        <------------- A--_|_   |
            //        ^               \  | '--B'
            //    2r  |------------------0   /
            //        V                 \  /
            //        <------------------B
            //
            // Lastly we optionally do rounded corners. The lines are connected by a circle with center in the origin.
            // We need to add additional circle segment contained within a triangle as shown on the picture.
            //
            //                           2r
            //                       |<------>|
            //                       |   |    |
            //                       |   |    |
            //                       |   |    |
            //        <------------- A--_|_   |
            //        ^               \  | ^--|-
            //    2r  |------------------0    | ^---B'
            //        V                \     ./    /
            //        <----------------|----"    /
            //                          \     /
            //                          |  /
            //                           B

            final float PERPEND_EPSILON = 0.0001f;

            float u_dot_v = ux*vx + uy*vy;

            //perpendicular
            float pux = -uy;
            float puy = ux;
            float pvx = -vy;
            float pvy = vx;

            connection.r = r;

            float v_dot_pu = vx*pux + vy*puy;

            //If is nearly straight (ie the corner barely exists) dont bother with anything fancy and
            // just place A and B along the perpendicular of u
            // (and also of v since they are very similar in this case)
            if(abs(v_dot_pu) < perpend_epsilon)
            {
                connection.u_side_x1 = pux*r;
                connection.u_side_y1 = puy*r;
                connection.u_side_x2 = -pux*r;
                connection.u_side_y2 = -puy*r;

                connection.v_side_x1 = connection.u_side_x1;
                connection.v_side_y1 = connection.u_side_y1;
                connection.v_side_x2 = connection.u_side_x2;
                connection.v_side_y2 = connection.u_side_y2;
                connection.has_primitive = false;
                connection.has_circle = false;
                connection.undetermined = true;
            }
            else
            {
                connection.undetermined = false;
                float h_mag = hypot(ux + vx, uy + vy);
                float hx = (ux + vx)/h_mag;
                float hy = (uy + vy)/h_mag;
                float phx = -hy;
                float phy = hx;
                //First consider the following setup
                //                   H
                //                _- |\
                //             _-    | \
                //          _K       |  \
                //       _-   \      |   \
                //    _-       \     |    \
                //  0-----------G----I-----J
                //  where between both GIH and 0HJ is 90 degrees.
                //
                // We know that I = (G.H/G.G)G which is a projection of H onto G
                // we can calculate K = (H.G/H.H)H (by swapping G and H)
                // which is projection of G onto H.
                // We can notice that 0GK and 0HJ triangles are similar
                // and thus the same relationship between G and K will hold for J and H:
                // projecting J onto H will equal H. We get H = (H.J/H.H)H and from the
                // fact that J is colinear with G we can write J = aG for some scalar a.
                // Thus we get H = (aG.H/H.H)H ie 1 = aG.H/H.H. So a = H.H/G.H
                // and vector J = (H.H/G.H)G. One last thing is to realize this does not
                // depend at all on the magnitude of G so even when we know only its
                // direction we can calculate J.
                //
                // Now we are trying to calculate the inner joint intersection ie find A. (see first diagram).
                // We have vector u,h and pu (perpendicular to u). We get the following diagram
                //                  r*pu
                //                _-  \
                //             _-      \
                //          _-          \
                //       _-              \
                //    _-                  \
                //  0---------->h----------A
                // where A is the unknown vector. Comparing with the earlier triangle we can rewrite it in terms
                // of the variables there so H = r*pu, G = h, A = J and immediately obtain A as
                //    A = (H.H/G.H)G = (r*pu . r*pu/h.r*pu)h = (r^2(pu.pu)/r*(h.pu))h
                //      = r/(h*pu)h [because pu is normalized thus pu.pu=1] = beta*h
                float h_dot_pu = hx*pux + hy*puy;

                //The position of sharp connection point on the inside of the bend
                // is shared among all connection styles
                float beta = r/h_dot_pu;
                if(Float.isInfinite(beta))
                    beta = 0;

                float Ax = beta*hx;
                float Ay = beta*hy;

                //Rounded corner
                if(do_rounded)
                {
                    final float RADIUS_TO_TRIANGLE_SIZE = 2;
                    final float TANGENT_OF_30_DEGREES = 0.5773502691896257f;

                    float max_triangle_size = RADIUS_TO_TRIANGLE_SIZE*r;
                    float offset = abs(h_dot_pu*r);
                    float triangle_height = max_triangle_size - offset;
                    float triangle_width = triangle_height*TANGENT_OF_30_DEGREES;

                    assert triangle_height > 0;
                    assert triangle_width > 0;

                    connection.has_circle = true;
                    connection.circle_x1 = -max_triangle_size*hx;
                    connection.circle_y1 = -max_triangle_size*hy;
                    connection.circle_x2 = -offset*hx + triangle_width*phx;
                    connection.circle_y2 = -offset*hy + triangle_width*phy;
                    connection.circle_x3 = -offset*hx - triangle_width*phx;
                    connection.circle_y3 = -offset*hy - triangle_width*phy;

                    //Since is perpendicular depends on direction is required to make two cases.
                    // Dont ask too much. This is more of a hack since I am too tired to think this really through.
                    if(v_dot_pu <= 0)
                    {
                        connection.u_side_x1 = pux*r;
                        connection.u_side_y1 = puy*r;
                        connection.u_side_x2 = -Ax;
                        connection.u_side_y2 = -Ay;

                        connection.v_side_x1 = -pvx*r;
                        connection.v_side_y1 = -pvy*r;
                        connection.v_side_x2 = -Ax;
                        connection.v_side_y2 = -Ay;

                        connection.has_primitive = true;
                        connection.primitive_x1 = connection.u_side_x1;
                        connection.primitive_y1 = connection.u_side_y1;
                        connection.primitive_x2 = connection.v_side_x1;
                        connection.primitive_y2 = connection.v_side_y1;
                        connection.primitive_x3 = connection.v_side_x2;
                        connection.primitive_y3 = connection.v_side_y2;
                    }
                    else
                    {
                        connection.u_side_x2 = -pux*r;
                        connection.u_side_y2 = -puy*r;
                        connection.u_side_x1 = Ax;
                        connection.u_side_y1 = Ay;

                        connection.v_side_x2 = pvx*r;
                        connection.v_side_y2 = pvy*r;
                        connection.v_side_x1 = Ax;
                        connection.v_side_y1 = Ay;

                        connection.has_primitive = true;
                        connection.primitive_x1 = connection.u_side_x2;
                        connection.primitive_y1 = connection.u_side_y2;
                        connection.primitive_x2 = connection.v_side_x2;
                        connection.primitive_y2 = connection.v_side_y2;
                        connection.primitive_x3 = connection.v_side_x1;
                        connection.primitive_y3 = connection.v_side_y1;
                    }

                    assert !Float.isNaN(connection.u_side_x2);
                    assert !Float.isNaN(connection.u_side_x1);
                    assert !Float.isNaN(connection.circle_x2);
                    assert !Float.isNaN(connection.primitive_x3);
                }
                //Cut corner
                else if(u_dot_v > sharp_connection_threshold)
                {
                    //Find such vector B on the outer boundary along the u vector
                    // (that is B = -A + gamma*u) so that its sharp_corner_height=s
                    // distance along h from the origin. That is |proj_{-h} B| = s
                    // s = B.-h / |h|. factor out gamma to get the following code.
                    // The same for B' using v instead of u.
                    //Note that A.B
                    float u_dot_h = ux*hx + uy*hy;
                    float v_dot_h = vx*hx + vy*hy;
                    float u_gamma = (beta - sharp_corner_height)/u_dot_h;
                    float v_gamma = (beta - sharp_corner_height)/v_dot_h;

                    connection.u_side_x1 = Ax;
                    connection.u_side_y1 = Ay;
                    connection.u_side_x2 = -Ax + ux*u_gamma;
                    connection.u_side_y2 = -Ay + uy*u_gamma;

                    connection.v_side_x1 = Ax;
                    connection.v_side_y1 = Ay;
                    connection.v_side_x2 = -Ax + vx*v_gamma;
                    connection.v_side_y2 = -Ay + vy*v_gamma;

                    connection.has_circle = false;
                    connection.has_primitive = true;
                    connection.primitive_x1 = connection.v_side_x2;
                    connection.primitive_y1 = connection.v_side_y2;
                    connection.primitive_x2 = connection.u_side_x2;
                    connection.primitive_y2 = connection.u_side_y2;
                    connection.primitive_x3 = Ax;
                    connection.primitive_y3 = Ay;

                    //no need to set uvs because is simple triangle
                }
                //Sharp corner
                else
                {
                    connection.u_side_x1 = Ax;
                    connection.u_side_y1 = Ay;
                    connection.u_side_x2 = -Ax;
                    connection.u_side_y2 = -Ay;

                    connection.v_side_x1 = Ax;
                    connection.v_side_y1 = Ay;
                    connection.v_side_x2 = -Ax;
                    connection.v_side_y2 = -Ay;
                    connection.has_primitive = false;
                    connection.has_circle = false;
                }
            }
            return connection;
        }

        public static Line_Connection calculate_line_connection(Line_Connection connection, float x1, float y1, float x2, float y2, float x3, float y3, float r, boolean do_rounded, float sharp_connection_threshold)
        {
            float ux = x1-x2;
            float uy = y1-y2;
            float vx = x3-x2;
            float vy = y3-y2;

            float u_mag = hypot(ux, uy);
            if(u_mag > 0)
            {
                ux /= u_mag;
                uy /= u_mag;
            }

            float v_mag = hypot(vx, vy);
            if(v_mag > 0)
            {
                vx /= v_mag;
                vy /= v_mag;
            }

            return calculate_line_connection(connection, ux, uy, vx, vy, r, r, do_rounded, sharp_connection_threshold, LINE_CONNECTION_DEF_PERPEND_EPSILON);
        }

        public void submit_connection_primitive(Line_Connection con, float x, float y, int color, Matrix3f transform_or_null)
        {
            if(con.has_circle)
            {
                float r = con.r;
                submit(
                    x + con.circle_x1, y + con.circle_y1, con.circle_x1/r, con.circle_y1/r, color,
                    x + con.circle_x2, y + con.circle_y2, con.circle_x2/r, con.circle_y2/r, color,
                    x + con.circle_x3, y + con.circle_y3, con.circle_x3/r, con.circle_y3/r, color,
                    FLAG_CIRCLE, transform_or_null
                );
            }
            if(con.has_primitive)
            {
                submit(
                    x + con.primitive_x1, y + con.primitive_y1, 0, 0, color,
                    x + con.primitive_x2, y + con.primitive_y2, 0, 0, color,
                    x + con.primitive_x3, y + con.primitive_y3, 0, 0, color,
                    0, transform_or_null
                );
            }
        }

        public void submit_connected_line(Line_Connection from, Line_Connection to, float from_x, float from_y, float to_x, float to_y, int color, Matrix3f transform_or_null)
        {
            submit_connection_primitive(to, to_x, to_y, color, transform_or_null);
            float begin_top_x = from_x + from.v_side_x1;
            float begin_top_y = from_y + from.v_side_y1;
            float begin_bot_x = from_x + from.v_side_x2;
            float begin_bot_y = from_y + from.v_side_y2;

            float end_top_x = to_x + to.u_side_x1;
            float end_top_y = to_y + to.u_side_y1;
            float end_bot_x = to_x + to.u_side_x2;
            float end_bot_y = to_y + to.u_side_y2;
                    
            submit(
                    begin_top_x, begin_top_y, 0, 0, color,
                    begin_bot_x, begin_bot_y, 0, 0, color,
                    end_top_x, end_top_y, 0, 0, color,
                    0, transform_or_null
            );
            submit(
                    end_top_x, end_top_y, 0, 0, color,
                    begin_bot_x, begin_bot_y, 0, 0, color,
                    end_bot_x, end_bot_y, 0, 0, color,
                    0, transform_or_null
            );
        }

        public void submit_derivative_connected_line(
            float[] xs, float[] ys, float[] derx, float[] dery, int from, int to, 
            Line_Connection con_from, Line_Connection con_to, float width, int color, Matrix3f transform_or_null)
        {
            if(to - from < 2)
                return;

            float r = width/2;
            float p11x = 0;
            float p11y = 0;
            float p12x = 0;
            float p12y = 0;

            int start = from;
            int end = to;
            if(con_from != null) {
                p11x = xs[0] + con_from.v_side_x1;
                p11y = ys[0] + con_from.v_side_y1;
                p12x = xs[0] + con_from.v_side_x2;
                p12y = ys[0] + con_from.v_side_y2;
                start += 1;
            }

            if(con_to != null)
                end -= 1;

            for(int i = start; i < end; i++)
            {
                float mag = (float) Math.sqrt(derx[i]*derx[i] + dery[i]*dery[i]);
                float der_x = derx[i]/mag;
                float der_y = dery[i]/mag;

                float p21x = xs[i] + der_y*r;
                float p21y = ys[i] - der_x*r;
                float p22x = xs[i] - der_y*r;
                float p22y = ys[i] + der_x*r;
                
                if(i > from) {

                    this.submit(
                        p11x, p11y, 0, 0, color,
                        p21x, p21y, 0, 0, color,
                        p22x, p22y, 0, 0, color, 0, transform_or_null);
                    this.submit(
                        p11x, p11y, 0, 0, color,
                        p12x, p12y, 0, 0, color,
                        p22x, p22y, 0, 0, color, 0, transform_or_null);
                }

                p11x = p21x;
                p11y = p21y;
                p12x = p22x;
                p12y = p22y;
            }

            if(con_to != null) {
                float p21x = xs[to-1] + con_to.u_side_x1;
                float p21y = ys[to-1] + con_to.u_side_y1;
                float p22x = xs[to-1] + con_to.u_side_x2;
                float p22y = ys[to-1] + con_to.u_side_y2;

                this.submit(p11x, p11y, 0, 0, color,
                            p21x, p21y, 0, 0, color,
                            p22x, p22y, 0, 0, color, 0, transform_or_null);
                this.submit(p11x, p11y, 0, 0, color,
                            p12x, p12y, 0, 0, color,
                            p22x, p22y, 0, 0, color, 0, transform_or_null);

                submit_connection_primitive(con_to, xs[to-1], ys[to-1], color, transform_or_null);
            }
        }


        public void submit_bezier_line(Buffers.PointArray points, Buffers.PointArray ders, float x1, float y1, float x2, float y2, float x3, float y3, float epsilon, float r, int color, Matrix3f transform_or_null)
        {
            points.resize(0);
            ders.resize(0);
            Splines.sample_bezier(points, ders, x1, y1, x2, y2, x3, y3, 0, 10, epsilon);
            submit_derivative_connected_line(points.xs, points.ys, ders.xs, ders.ys, 0, points.length, null, null, r, color, transform_or_null);
        }

        int beziers_point_sum = 0;
        int beziers_sample_count = 0;
        public void submit_bezier_line(Line_Connection from, Line_Connection to, Buffers.PointArray points, Buffers.PointArray ders, float x1, float y1, float x2, float y2, float x3, float y3, float epsilon, float r, int color, Matrix3f transform_or_null)
        {
            points.resize(0);
            ders.resize(0);
            Splines.sample_bezier(points, ders, x1, y1, x2, y2, x3, y3, 0, 10, epsilon);
            beziers_point_sum += points.length;
            beziers_sample_count += 1;
            if(beziers_sample_count == 10000)
            {
                System.out.println(STR."average points per bezier \{(double) beziers_point_sum / beziers_sample_count}");
                beziers_point_sum = 0;
                beziers_sample_count = 0;
            }
            submit_derivative_connected_line(points.xs, points.ys, ders.xs, ders.ys, 0, points.length, from, to, r, color, transform_or_null);
        }

        public void submit_bezier_countour(Buffers.PointArray points, Buffers.PointArray ders, Line_Connection[] connections, float[] xs, float[] ys, Buffers.IndexBuffer shape, float width, float sharp_cutoff_threshold, float bezier_epsilon, int color, Matrix3f transform_or_null)
        {
            //if length is 2 its a single vertex
            //if length is >= 4 its at least a line...
            float r = width/2;
            boolean do_rounded = sharp_cutoff_threshold < 0;
            if(shape.length >= 4)
            {
                float pmx = xs[shape.at(shape.length - 3)];
                float pmy = ys[shape.at(shape.length - 3)];
                float p0x = xs[shape.at(shape.length - 2)];
                float p0y = ys[shape.at(shape.length - 2)];
                float p1x = xs[shape.at(shape.length - 1)];
                float p1y = ys[shape.at(shape.length - 1)];
                for(int it = 0; it <= shape.length; it += 2)
                {
                    int i = it;
                    if(i >= shape.length)
                        i = 0;

                    int vi = shape.at(i);
                    int vn = shape.at(i+1);
                    float p2x = xs[vi];
                    float p2y = ys[vi];
                    float p3x = xs[vn];
                    float p3y = ys[vn];

                    Render.Bezier_Buffer.Line_Connection prev_connection = connections[(it/2+1) & 1];
                    Render.Bezier_Buffer.Line_Connection curr_connection = connections[(it/2+0) & 1];

                    if((p0x == p1x && p0y == p1y)) {
                        Render.Bezier_Buffer.calculate_line_connection(curr_connection, pmx, pmy, p1x, p1y, p2x, p2y, r, do_rounded, sharp_cutoff_threshold);
                        if(it > 0) {
                            this.submit_connected_line(
                                prev_connection, curr_connection,
                                pmx, pmy,
                                p1x, p1y, color, null
                            );
                        }
                    }
                    else {
                        Render.Bezier_Buffer.calculate_line_connection(curr_connection, p0x, p0y, p1x, p1y, p2x, p2y, r, do_rounded, sharp_cutoff_threshold);
                        if(it > 0) {
                            this.submit_bezier_line(
                                prev_connection, curr_connection,
                                points, ders,
                                pmx, pmy,
                                p0x, p0y,
                                p1x, p1y,
                                bezier_epsilon, 2*r, color, null
                            );
                        }
                    }

                    pmx = p1x;
                    pmy = p1y;
                    p0x = p2x;
                    p0y = p2y;
                    p1x = p3x;
                    p1y = p3y;
                }
            }
            else if(shape.length >= 2)
            {
                float x = xs[shape.at(1)];
                float y = ys[shape.at(1)];
                this.submit_circle(x, y, r, color, transform_or_null);
            }
        }

        public void submit_indexed_triangles(float[] xs, float[] ys, Buffers.IndexBuffer indices, int color, int flags, Matrix3f transform_or_null)
        {
            for(int i = 0; i < indices.length; i += 3)
            {
                this.submit_triangle(
                        xs[indices.at(i)], ys[indices.at(i)],
                        xs[indices.at(i+1)], ys[indices.at(i+1)],
                        xs[indices.at(i+2)], ys[indices.at(i+2)],
                        color, flags, null);
            }
        }
    }

    public static final class Quadratic_Bezier_Render {
        public int VBO;
        public int VAO;
        public int shader;
        public int capacity;
        public float aa_threshold = 0.7f;
        public FloatBuffer temp_buffer;

        public static int VERTICES_PER_SEGMENT = Bezier_Buffer.VERTICES_PER_SEGMENT;
        public static int BYTES_PER_SEGMENT = Bezier_Buffer.BYTES_PER_SEGMENT;
        public static int BYTES_PER_VERTEX = Bezier_Buffer.BYTES_PER_VERTEX;
        public static int FLOATS_PER_VERTEX = Bezier_Buffer.FLOATS_PER_VERTEX;
        public static int FLOATS_PER_SEGMENT = Bezier_Buffer.FLOATS_PER_SEGMENT;

        public Quadratic_Bezier_Render(int max_gpu_size)
        {
            capacity = max_gpu_size/BYTES_PER_SEGMENT;
            temp_buffer = MemoryUtil.memAllocFloat(capacity*FLOATS_PER_SEGMENT);

            //init gpu buffers
            {
                VAO = glGenVertexArrays();
                VBO = glGenBuffers();
                glBindVertexArray(VAO);

                glBindBuffer(GL_ARRAY_BUFFER, VBO);
                glBufferData(GL_ARRAY_BUFFER, (long) capacity*BYTES_PER_SEGMENT, GL_STREAM_DRAW);

                long pos = 0;
                glVertexAttribPointer(0, 2, GL_FLOAT, false, BYTES_PER_VERTEX, pos); pos += 2*Float.BYTES; //position
                glVertexAttribPointer(1, 2, GL_FLOAT, false, BYTES_PER_VERTEX, pos); pos += 2*Float.BYTES; //uv
                glVertexAttribIPointer(2, 1, GL_UNSIGNED_INT, BYTES_PER_VERTEX, pos); pos += Integer.BYTES; //color
                glVertexAttribIPointer(3, 1, GL_INT, BYTES_PER_VERTEX, pos); pos += Integer.BYTES; //flags

                glEnableVertexAttribArray(0);
                glEnableVertexAttribArray(1);
                glEnableVertexAttribArray(2);
                glEnableVertexAttribArray(3);

                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindVertexArray(0);
            }

            //init shader
            this.shader = compile_shader_source_prepend(
                    """
                    #version 330 core
                    layout (location = 0) in vec2 vertex_pos;
                    layout (location = 1) in vec2 vertex_uv;
                    layout (location = 2) in uint vertex_color;
                    layout (location = 3) in int vertex_flags;
                    out vec2 uv;
                    out vec4 color;
                    flat out int flags;
                    
                    uniform mat4 model;
                    uniform mat4 view;
                    void main()
                    {
                        uint ucol = uint(vertex_color);
                        uint a = uint(255) - (ucol >> 24) & uint(0xFF);
                        uint r = (ucol >> 16) & uint(0xFF);
                        uint g = (ucol >> 8) & uint(0xFF);
                        uint b = (ucol >> 0) & uint(0xFF);
                        
                        flags = vertex_flags;
                        vec4 out_color = vec4(r/255.0, g/255.0, b/255.0, a/255.0);
                        out_color.xyz = srgb_to_linear(out_color.xyz);
                        if((vertex_flags & FLAG_OKLAB) != 0)
                            out_color.xyz = linear_to_oklab(out_color.xyz);
                        
                        uv = vertex_uv;
//                        if((vertex_flags & FLAG_CIRCLE) != 0)
//                            uv = (view * model * vec4(vertex_uv, 0, 1)).xy;
                            
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
                    flat in int flags;
                    uniform float aa_threshold;
                    
                    void main()
                    {
                        float dist = -999999;
                        float u = uv.x;
                        float v = uv.y;
                        vec2 du = dFdx(uv);
                        vec2 dv = dFdy(uv);
                        
                        if((flags & FLAG_BEZIER) != 0)
                        {
                            float F = u*u - v;
                            vec2 J_F = vec2(
                                2*u*du.x - du.y,
                                2*u*dv.x - dv.y
                            );
                            dist = F/(length(J_F) + 0.0000001);
                        }
                        else if((flags & FLAG_CIRCLE) != 0)
                        {
                            float F = u*u + v*v - 1;
                            vec2 J_F = vec2(
                                2*u*du.x + 2*v*du.y,
                                2*u*dv.x + 2*v*dv.y
                            );
                            dist = F/(length(J_F) + 0.0000001);
                        }
                        
                        if((flags & FLAG_INVERSE) != 0)
                            dist = -dist;
                            
                        float abs_dist = abs(dist);
                        vec4 out_color = color;
                        if((flags & FLAG_OKLAB) != 0)
                            out_color.xyz = oklab_to_linear(out_color.xyz);
                            
                        float alpha_factor = min(-dist/(aa_threshold + 0.0000001), 1);
                        if(alpha_factor <= 0) 
                            discard;
                        
                        out_color.w *= alpha_factor;
                        frag_color = out_color;
                    }
                    """,
                    """
                    #define FLAG_BEZIER     1
                    #define FLAG_CIRCLE     2
                    #define FLAG_INVERSE    4
                    #define FLAG_OKLAB      8
                    
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

        public void render(Matrix4f model_or_null, Matrix4f view_or_null, Bezier_Buffer... batches)
        {
            if(batches == null || batches.length == 0)
                return;

            Matrix4f model = model_or_null == null ? MAT4_ID : model_or_null;
            Matrix4f view = view_or_null == null ? MAT4_ID : view_or_null;

            temp_buffer.clear();
            temp_buffer.limit(capacity*FLOATS_PER_SEGMENT);
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
                    Bezier_Buffer batch = batches[batch_i];
                    int len = min(capacity - filled, batch.length - skip_len);
                    if (len != 0)
                    {
                        temp_buffer.put(filled*FLOATS_PER_SEGMENT, batch.buffer, skip_len*FLOATS_PER_SEGMENT, len*FLOATS_PER_SEGMENT);
                        filled += len;
                    }

                    assert filled <= capacity;
                    temp_buffer.limit(filled*FLOATS_PER_SEGMENT);

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



    public static int glGetUniformAndLog(int program, CharSequence name, CharSequence type)
    {
        int location = glGetUniformLocation(program, name);
        if(location == -1)
        {
            if(type.length() > 0)
                System.err.println(STR."program \{program} couldnt find uniform \{type} '\{name}'");
            else
                System.err.println(STR."program \{program} couldnt find uniform '\{name}'");
        }

        return location;
    }

    public static boolean setUniform(int program, CharSequence name, float val)
    {
        int location = glGetUniformAndLog(program, name, "float");
        if(location == -1)
            return false;

        glUniform1f(location, val);
        return true;
    }

    public static boolean setUniform(int program, CharSequence name, Vector3f val)
    {
        int location = glGetUniformAndLog(program, name, "vec3");
        if(location == -1)
            return false;

        glUniform3f(location, val.x, val.y, val.z);
        return true;
    }

    public static boolean setUniform(int program, CharSequence name, Vector4f val)
    {
        int location = glGetUniformAndLog(program, name, "vec4");
        if(location == -1)
            return false;

        glUniform4f(location, val.x, val.y, val.z, val.w);
        return true;
    }

    public static boolean setUniform(int program, CharSequence name, Matrix4f val, MemoryStack stack)
    {
        int location = glGetUniformAndLog(program, name, "mat4");
        if(location == -1)
            return false;

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

}
