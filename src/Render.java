import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    public static final class Quadratic_Bezier_Buffer {
        //2 floats position
        //2 floats uv
        //1 uint for color
        //1 uint for flags
        public ByteBuffer buffer;

        public int length;
        public int capacity;
        public boolean grows = true;

        public static int BYTES_PER_VERTEX = 4*Float.BYTES + 2*Integer.BYTES;
        public static int VERTICES_PER_SEGMENT = 3;
        public static int BYTES_PER_SEGMENT = VERTICES_PER_SEGMENT*BYTES_PER_VERTEX;

        //default rendering mode: solid triangle with points p1, p2, p3
        public static int FLAG_BEZIER = 1; //the triangle is interpreted as quadratic bezier curve where p1, p3 are endpoints and p2 is control point
        public static int FLAG_CIRCLE = 2; //the triangle is interpreted as circle segment where p2 is the center, and p1, p2 mark the segment sides. The radius is 1 in uv space.
        public static int FLAG_INVERSE = 4; //the triangle shape is filled inside out. When combined with rendering triangles, makes the triangle entirely invisible!
        public static int FLAG_OKLAB = 8; //vertex colors are interpolated in the OKLAB colorspace instead of linear colorspace.

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

        public void submit(float x1, float y1, float u1, float v1, int color1,
                           float x2, float y2, float u2, float v2, int color2,
                           float x3, float y3, float u3, float v3, int color3,
                           int flags, Matrix3f transform_or_null)
        {
            //If transfomr is passed in apply it to all vertices (but not uvs!)
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

            buffer.putFloat(x1);
            buffer.putFloat(y1);
            buffer.putFloat(u1);
            buffer.putFloat(v1);
            buffer.putInt(color1);
            buffer.putInt(flags);

            buffer.putFloat(x2);
            buffer.putFloat(y2);
            buffer.putFloat(u2);
            buffer.putFloat(v2);
            buffer.putInt(color2);
            buffer.putInt(flags);

            buffer.putFloat(x3);
            buffer.putFloat(y3);
            buffer.putFloat(u3);
            buffer.putFloat(v3);
            buffer.putInt(color3);
            buffer.putInt(flags);
            length += 1;
        }

        public void submit_bezier_or_triangle(
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

        public void submit_bezier_or_triangle(
                float x1, float y1,
                float x2, float y2,
                float x3, float y3, int color,
                int flags, Matrix3f transform_or_null)
        {
            submit_bezier_or_triangle(x1, y1, color, x2, y2, color, x3, y3, color, flags & ~FLAG_OKLAB, transform_or_null);
        }

        public void submit_circle(float x, float y, float r, int color1, int color2, int color3, int flags, Matrix3f transform_or_null)
        {
            float sqrt3 = 1.7320508075688772f;
            submit(
                    x + -sqrt3*r, y + -r, -sqrt3, -1, color1,
                    x + sqrt3*r, y + -r, sqrt3, -1, color2,
                    x, y + 2*r, 0, 2, color3,
                    Quadratic_Bezier_Buffer.FLAG_CIRCLE | flags, transform_or_null
            );
        }

        public void submit_circle(float x, float y, float r, int color, Matrix3f transform_or_null)
        {
            submit_circle(x,y, r, color, color, color, 0, transform_or_null);
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

        public static int MOD(int val, int range)
        {
            return (((val) % (range) + (range)) % (range));
        }

        public static float hypot(float a, float b)
        {
            return (float) Math.sqrt(a*a + b*b);
        }

        public static boolean is_near(float a, float b, double epsilon)
        {
            return Math.abs(a - b) < epsilon;
        }

        public static boolean is_near(float a, float b)
        {
            return is_near(a, b, 1e-7);
        }

        static final class Line_Connection
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
            // The angle of a=90 (sharp_corner_heightt = cos(a=90)) is here just for illustration as
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

//            assert is_near(hypot(ux, uy), 1); //Needs to be normalized
//            assert is_near(hypot(vx, vy), 1); //Needs to be normalized

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
                connection.u_side_x1 = -pux*r;
                connection.u_side_y1 = -puy*r;
                connection.u_side_x2 = pux*r;
                connection.u_side_y2 = puy*r;

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
                float h_dot_pu = hx*pux + hy*puy;

                //The position of sharp connection point on the inside of the bend
                // is shared among all connection styles
                float beta = r/h_dot_pu;
                if(Float.isInfinite(beta))
                    beta = 0;

                float Ax = hx*beta;
                float Ay = hy*beta;

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
                    if(v_dot_pu < 0)
                    {
                        connection.u_side_x2 = pux*r;
                        connection.u_side_y2 = puy*r;
                        connection.u_side_x1 = -Ax;
                        connection.u_side_y1 = -Ay;

                        connection.v_side_x2 = -pvx*r;
                        connection.v_side_y2 = -pvy*r;
                        connection.v_side_x1 = -Ax;
                        connection.v_side_y1 = -Ay;

                        connection.has_primitive = true;
                        connection.primitive_x1 = connection.u_side_x2;
                        connection.primitive_y1 = connection.u_side_y2;
                        connection.primitive_x2 = connection.v_side_x2;
                        connection.primitive_y2 = connection.v_side_y2;
                        connection.primitive_x3 = -Ax;
                        connection.primitive_y3 = -Ay;
                    }
                    else
                    {
                        connection.u_side_x1 = -pux*r;
                        connection.u_side_y1 = -puy*r;
                        connection.u_side_x2 = Ax;
                        connection.u_side_y2 = Ay;

                        connection.v_side_x1 = pvx*r;
                        connection.v_side_y1 = pvy*r;
                        connection.v_side_x2 = Ax;
                        connection.v_side_y2 = Ay;

                        connection.has_primitive = true;
                        connection.primitive_x1 = connection.u_side_x1;
                        connection.primitive_y1 = connection.u_side_y1;
                        connection.primitive_x2 = connection.v_side_x1;
                        connection.primitive_y2 = connection.v_side_y1;
                        connection.primitive_x3 = connection.v_side_x2;
                        connection.primitive_y3 = connection.v_side_y2;
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
                    // (that is B = A + gamma*u) so that its sharp_corner_height=s
                    // distance along h from the origin. That is |proj_{-h} B| = s
                    // s = B.-h / |h|. factor out gamma and calculate.
                    // The same for B' using v instead of u.

                    //u.h = u.(u+v) = u.u + u.v = 1 + u.v
                    float u_dot_h = 1 + u_dot_v;
                    float v_dot_h = 1 + u_dot_v;
                    float u_gamma = (-beta - sharp_corner_height)/u_dot_h;
                    float v_gamma = (-beta - sharp_corner_height)/v_dot_h;

                    connection.u_side_x1 = Ax + ux*u_gamma;
                    connection.u_side_y1 = Ay + uy*u_gamma;
                    connection.u_side_x2 = -Ax;
                    connection.u_side_y2 = -Ay;

                    connection.v_side_x1 = Ax + vx*v_gamma;
                    connection.v_side_y1 = Ay + vy*v_gamma;
                    connection.v_side_x2 = -Ax;
                    connection.v_side_y2 = -Ay;

                    connection.has_circle = false;
                    connection.has_primitive = true;
                    connection.primitive_x1 = connection.v_side_x1;
                    connection.primitive_y1 = connection.v_side_y1;
                    connection.primitive_x2 = connection.u_side_x1;
                    connection.primitive_y2 = connection.u_side_y1;
                    connection.primitive_x3 = -Ax;
                    connection.primitive_y3 = -Ay;

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

        static Line_Connection calculate_line_connection(Line_Connection connection, float x1, float y1, float x2, float y2, float x3, float y3, float r, boolean do_rounded)
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

            return calculate_line_connection(connection, ux, uy, vx, vy, r, r, do_rounded, LINE_CONNECTION_DEF_CUT_CORNER_THRESHOLD, LINE_CONNECTION_DEF_PERPEND_EPSILON);
        }

        public void submit_connected_line(Line_Connection from, Line_Connection to, float from_x, float from_y, float to_x, float to_y, int color, Matrix3f transform_or_null)
        {
            if(to.has_primitive)
            {
                submit(
                        to_x + to.primitive_x1, to_y + to.primitive_y1, 0, 0, color,
                        to_x + to.primitive_x2, to_y + to.primitive_y2, 0, 0, color,
                        to_x + to.primitive_x3, to_y + to.primitive_y3, 0, 0, color,
                        0, transform_or_null
                );
            }

            float r = to.r;
            if(to.has_circle)
            {
                submit(
                        to_x + to.circle_x1, to_y + to.circle_y1, to.circle_x1/r, to.circle_y1/r, color,
                        to_x + to.circle_x2, to_y + to.circle_y2, to.circle_x2/r, to.circle_y2/r, color,
                        to_x + to.circle_x3, to_y + to.circle_y3, to.circle_x3/r, to.circle_y3/r, color,
                        FLAG_CIRCLE, transform_or_null
                );
            }

            submit_line(from_x, from_y, to_x, to_y, 0.005f, 0xFFFFFF, transform_or_null);

            float begin_top_x = from_x + from.v_side_x1;
            float begin_top_y = from_y + from.v_side_y1;
            float begin_bot_x = from_x + from.v_side_x2;
            float begin_bot_y = from_y + from.v_side_y2;

            float end_top_x = to_x + to.u_side_x1;
            float end_top_y = to_y + to.u_side_y1;
            float end_bot_x = to_x + to.u_side_x2;
            float end_bot_y = to_y + to.u_side_y2;

            //Ignore undeterminacy for now since its not needed
            if(false)
                if(to.undetermined || from.undetermined)
                {
                    float true_dirx = to_x - from_x;
                    float true_diry = to_y - from_y;
                    float true_dir_mag = hypot(true_dirx, true_diry);

                    float dir1x = end_top_x - begin_top_x;
                    float dir1y = end_top_y - begin_top_y;
                    float dir1_mag = hypot(dir1x, dir1y);

                    float dir2x = end_bot_x - begin_top_x;
                    float dir2y = end_bot_y - begin_top_y;
                    float dir2_mag = hypot(dir2x, dir2y);

                    float similarity1 = (true_dirx*dir1x + true_diry*dir1y)/(true_dir_mag*dir1_mag);
                    float similarity2 = (true_dirx*dir2x + true_diry*dir2y)/(true_dir_mag*dir2_mag);
                    if(similarity1 < similarity2)
                    {
                        float tempx = end_top_x;
                        float tempy = end_top_y;
                        end_top_x = end_bot_x;
                        end_top_y = end_bot_y;
                        end_bot_x = tempx;
                        end_bot_y = tempy;
                    }
                }

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

        static Line_Connection[] SUMBIT_GLYPH_CONNECTIONS = {new Line_Connection(), new Line_Connection()};
        public void submit_glyph_contour(Font_Parser.Glyph glyph, float scale, float width, int color, boolean rounded_joints, Matrix3f transform_or_null)
        {
            float r = width/2;
            for(int k = 0; k < glyph.solids.length + glyph.holes.length; k++)
            {
                Font_Parser.Countour countour = k < glyph.solids.length
                        ? glyph.solids[k]
                        : glyph.holes[k - glyph.solids.length];

                assert countour.xs.length == countour.ys.length;

                int prev = countour.xs.length - 1;
                for(int i = 0; i + 1 <= countour.xs.length; i += 2)
                {
                    int control = i;
                    int curr = i + 1;

                    float x1 = countour.xs[prev]*scale;
                    float y1 = countour.ys[prev]*scale;
                    float x2 = countour.xs[control]*scale;
                    float y2 = countour.ys[control]*scale;
                    float x3 = countour.xs[curr]*scale;
                    float y3 = countour.ys[curr]*scale;

                    if(false)
                    {

                    if(i == 0)
                        submit_circle(x1, y2, r, 0x00FF00, transform_or_null);

                    submit_line(x1, y1, x2, y2, width, 0x55FF0000, transform_or_null);
                    submit_line(x2, y2, x3, y3, width, color, transform_or_null);

                    if(x2 != x3 || y2 != y3)
                        submit_circle(x2, y2, r, 0x0000FF, transform_or_null);
                    }

                    if(true)
                    if(x2 == x3 && y2 == y3)
                        submit_line(x1, y1, x3, y3, width, color, transform_or_null);
                    else
                        submit_bezier_contour(x1, y1, x2, y2, x3, y3, width, color, false, 20, 20, transform_or_null);
                    prev = curr;
                }
            }
        }

        static Line_Connection[] SUMBIT_BEZIER_CONNECTIONS = {new Line_Connection(), new Line_Connection()};
        public void submit_bezier_contour(float x1, float y1, float x2, float y2, float x3, float y3, float width, int color, boolean rounded_joints, int min_segments, int max_segments, Matrix3f transform_or_null)
        {
            float r = width/2;
            float prev_x = x1;
            float prev_y = y1;
            if(rounded_joints)
                submit_circle(prev_x, prev_y, r, color, transform_or_null);

            for(int i = 0; i < min_segments; i++)
            {
                float t = (float) (i + 1)/min_segments;

                float curr_x = Splines.bezier(x1, x2, x3, t);
                float curr_y = Splines.bezier(y1, y2, y3, t);

                int inset_prev = (i+1) & 1;
                int inset_curr = i & 1;

//                Line_Connection prev_connection = SUMBIT_BEZIER_CONNECTIONS[inset_prev];
//                Line_Connection curr_connection = SUMBIT_BEZIER_CONNECTIONS[inset_curr];
//                calculate_line_connection(curr_connection, x1, y1, x2, y2, x3, y3, r, rounded_joints);

//                submit_connected_line(prev_connection, curr_connection, x1, y1, x2, y2, color, transform_or_null);

                submit_line(prev_x, prev_y, curr_x, curr_y, width, color, transform_or_null);
                submit_circle(curr_x, curr_y, r, color, transform_or_null);

                prev_x = curr_x;
                prev_y = curr_y;
            }
        }

        static Matrix3f SUBMIT_TEXT_COUBNTOUR_TEMP_MATRIX = new Matrix3f();
        public void submit_text_countour(Font_Parser.Font font, String text, float scale, float width, float spacing_flat, float spacing_boost, int color, Matrix3f transform_or_null)
        {
            int i = 0;
            SUBMIT_TEXT_COUBNTOUR_TEMP_MATRIX.identity();
            int text_position = 0;
            for(int c : text.codePoints().toArray()){
                Font_Parser.Glyph glyph = font.glyphs.getOrDefault(c, font.missing_glyph);
                if(glyph == font.missing_glyph)
                    System.err.println(STR."Couldnt render unicode character '\{Character.toChars(c)}' using not found glyph");

//                Font_Parser.Glyph glyph = font.missing_glyph;
                SUBMIT_TEXT_COUBNTOUR_TEMP_MATRIX.m20 = i*spacing_flat + text_position*scale*spacing_boost;
                if(transform_or_null != null)
                    SUBMIT_TEXT_COUBNTOUR_TEMP_MATRIX.mulLocal(transform_or_null);

                submit_glyph_contour(glyph, scale, width, color, true, SUBMIT_TEXT_COUBNTOUR_TEMP_MATRIX);
                text_position += glyph.advance_width;
                i += 1;
            }
        }

        void reset()
        {
            buffer.clear();
            length = 0;
        }
    }

    public static final class Quadratic_Bezier_Render {
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
