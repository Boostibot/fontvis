import java.util.Arrays;

public final class Triangulate {
    public static final class AABB {
        public float x0 = 0;
        public float x1 = 0;
        public float y0 = 0;
        public float y1 = 0;
    }

    public static final class AABB_Vertices {
        public float x0 = 0;
        public float x1 = 0;
        public float y0 = 0;
        public float y1 = 0;

        public int x0_index = 0;
        public int x1_index = 0;
        public int y0_index = 0;
        public int y1_index = 0;

        public int x0_vertex = 0;
        public int x1_vertex = 0;
        public int y0_vertex = 0;
        public int y1_vertex = 0;
    }

    public static AABB aabb_from_aabb_vertices(AABB_Vertices aabb)
    {
        AABB out = new AABB();
        out.x0 = aabb.x0;
        out.x1 = aabb.x1;
        out.y0 = aabb.y0;
        out.y1 = aabb.y1;
        return out;
    }

    public static AABB_Vertices aabb_calculate(float[] xs, float[] ys, int[] indices)
    {
        AABB_Vertices aabb = new AABB_Vertices();
        if(indices.length > 0)
        {
            aabb.x0 = Float.POSITIVE_INFINITY;
            aabb.x1 = Float.NEGATIVE_INFINITY;
            aabb.y0 = Float.POSITIVE_INFINITY;
            aabb.y1 = Float.NEGATIVE_INFINITY;
            for(int k = 0; k < indices.length; k++)
            {
                int index = indices[k];
                if(aabb.x0 >= xs[index]) {
                    aabb.x0 = xs[index];
                    aabb.x0_index = k;
                }

                if(aabb.x1 <= xs[index]) {
                    aabb.x1 = xs[index];
                    aabb.x1_index = k;
                }

                if(aabb.y0 >= ys[index]) {
                    aabb.y0 = ys[index];
                    aabb.y0_index = k;
                }

                if(aabb.y1 <= ys[index]) {
                    aabb.y1 = ys[index];
                    aabb.y1_index = k;
                }
            }

            aabb.x0_vertex = indices[aabb.x0_index];
            aabb.x1_vertex = indices[aabb.x1_index];
            aabb.y0_vertex = indices[aabb.y0_index];
            aabb.y1_vertex = indices[aabb.y1_index];
        }
        return aabb;
    }

    /*
    public static IntArray connect_holes(float[] xs, float[] ys, int[] indices, int[][] holes)
    {
        if(indices.length == 0)
            return new IntArray();

        //Get info about each hole (TODO replace with AABB)
        class Sort_Hole {
            public int max_x_vertex = 0;
            public int max_x_index = 0;
            public float max_x_vertex_x = 0;
            public float max_x_vertex_y = 0;
            public int[] indices = null;
        };

        Sort_Hole[] sorted_holes = new Sort_Hole[holes.length];
        for(int hole_i = 0; hole_i < holes.length; hole_i++)
        {
            int[] hole = holes[hole_i];

            int max_x_index = -1;
            int max_x_vertex = -1;
            float max_x = Float.NEGATIVE_INFINITY;
            float max_y = Float.NEGATIVE_INFINITY;
            for(int k = 0; k < hole.length; k++)
            {
                int index = hole[k];
                if(max_x <= xs[index]) {
                    max_x = xs[index];
                    max_x_index = k;
                }
            }

            if(max_x_index != -1)
            {
                max_x_vertex = hole[max_x_index];
                max_y = ys[max_x_vertex];
            }

            Sort_Hole sorted = new Sort_Hole();
            sorted.max_x_index = max_x_index;
            sorted.max_x_vertex = max_x_vertex;
            sorted.max_x_vertex_x = max_x;
            sorted.max_x_vertex_y = max_y;
            sorted.indices = hole;
            sorted_holes[hole_i] = sorted;
        }

        //and sort them so the hole with the vertex with biggest max_x_vertex_x is first
        Arrays.sort(sorted_holes, (hole_a, hole_b) -> -Float.compare(hole_a.max_x_vertex_x, hole_b.max_x_vertex_x));

        //calculate the needed allocation size for the holes
        int total_cap = indices.length;
        for(int i = 0; i < holes.length; i++)
            total_cap += holes[i].length;

        total_cap += holes.length; //the bridges

        //perform the merging
        IntArray out = IntArray.with_capacity(total_cap);
        out.push(indices);

        Raycast_Result ray_res = new Raycast_Result();
        for(Sort_Hole hole : sorted_holes)
        {
            int[] holei = hole.indices;
            if(holei.length > 0)
            {
                float origin_x = hole.max_x_vertex_x;
                float origin_y = hole.max_x_vertex_y;
                int max_x_index = hole.max_x_index;
                if(raycast_x_polygon_first_optimistic_hit(ray_res, xs, ys, out.array, origin_x, origin_y))
                {
                    int index_0 = ray_res.index_0;

                    //hole: hole[max_x_index:]hole[:max_x_index]
                    //vert: out[:index_0]out[index_0:]
                    //============= merge ==========   
                    //merged: out[:index_0]hole[max_x_index:]hole[:max_x_index]out[index_0]res[index_0:]
                    out.insert_hole(index_0, holei.length + 1);
                    System.arraycopy(holei, max_x_index, out.array, index_0, holei.length - max_x_index);
                    System.arraycopy(holei, 0, out.array, index_0 + holei.length - max_x_index, max_x_index);
                    out.array[index_0 + holei.length] = ray_res.vertex_0;
                }
                else
                {
                    assert false;
                }
            }
        }


        return out;
    }
    */
    public static IntArray triangulate(float[] xs, float[] ys, int[] indices)
    {
        IntArray triangle_indices = IntArray.with_capacity(3*indices.length);
        int[] remaining = indices.clone();
        int remaining_len = remaining.length;

        while(remaining_len >= 3)
        {
            boolean did_remove = false;
            for(int i = 1; i <= remaining_len; i++)
            {
                int rem = remaining_len; //for brevity
                int prev = remaining[i - 1];
                int curr = remaining[i   < rem ? i   : i-rem];
                int next = remaining[i+1 < rem ? i+1 : i+1-rem];

                if(counter_clockwise_is_convex(xs, ys, prev, curr, next))
                {
                    //check intersections with all points (not prev,curr,next)
                    boolean contains_no_other_points = true;
                    for(int k = 0; k < indices.length; k++)
                    {
                        if(k < prev || k > next)
                        {
                            if(is_in_triangle_with_boundary(xs, ys, prev, curr, next, k))
                            {
                                contains_no_other_points = false;
                                break;
                            }
                        }
                    }

                    if(contains_no_other_points)
                    {
                        triangle_indices.push(prev);
                        triangle_indices.push(curr);
                        triangle_indices.push(next);

                        //remove arr[curr]
                        for(int j = curr; j < remaining_len - 1; j++)
                            remaining[j] = remaining[j + 1];
                        remaining_len -= 1;

                        did_remove = true;
                        break;
                    }
                }
            }

            if(did_remove == false)
            {
                System.out.println("bad bad");
                break;
            }
        }

        return triangle_indices;
    }


    public static float cross_product_z(float ux, float uy, float vx, float vy)
    {
        return ux*vy - vx*uy;
    }
    public static float cross_product_z(float px, float py, float p1x, float p1y, float p2x, float p2y)
    {
        return cross_product_z(p1x - px, p1y - py, p2x - px, p2y - py);
    }

    public static boolean is_point_on_segment(float px, float py, float p1x, float p1y, float p2x, float p2y)
    {
        float d1x = p1x - px;
        float d1y = p1y - py;
        float d2x = p2x - px;
        float d2y = p2y - py;

        return cross_product_z(d1x, d1y, d2x, d2x) == 0  //if forms a zero area triangle with point p
            && d1x*d2x <= 0  //if px is between p1x and p2x (or equal to one)
            && d1y*d2y <= 0; //similarly for py
    }

    public static int POINT_IN_SHAPE_WITH_BOUNDARY = 0;
    public static int POINT_IN_SHAPE_INTERIOR = 1;
    public static int POINT_IN_SHAPE_BOUNDARY_DONT_CARE = 2;
    public static boolean is_inside_polygon_winding(int allow_boundary, float[] x, float[] y, int from_i, int to_i, float origin_x, float origin_y)
    {
        //https://web.archive.org/web/20130126163405/http://geomalgorithms.com/a03-_inclusion.html
        if(from_i >= to_i)
            return false;

        int winding_number = 0;
        float p1x = x[to_i - 1] - origin_x;
        float p1y = y[to_i - 1] - origin_y;
        for (int i = from_i; i < to_i; i++)
        {
            float p2x = x[i] - origin_x;
            float p2y = y[i] - origin_y;

            //early out
            if(p1y*p2y <= 0)
            {
                float cross = cross_product_z(p1x, p1y, p2x, p2y, 0, 0);
                // Check if the point lies on the current edge
                if(allow_boundary != POINT_IN_SHAPE_BOUNDARY_DONT_CARE)
                    if(cross == 0 //if forms a zero area triangle with point p
                        && p1x*p2x <= 0)  //if px is between p1x and p2x (or equal to one)
                        return allow_boundary == POINT_IN_SHAPE_WITH_BOUNDARY;

                // Calculate the cross product to determine winding direction
                if (p1y <= 0) {
                    if (p2y > 0 && cross > 0)
                        winding_number++;
                }
                else {
                    if (p2y <= 0 && cross < 0)
                        winding_number--;
                }
            }

            p1x = p2x;
            p1y = p2y;
        }

        return winding_number != 0;
    }

    public static boolean is_inside_polygon_hit(int allow_boundary, float[] x, float[] y, int from_i, int to_i, float origin_x, float origin_y)
    {
        int num_hits = 0;
        float p1x = x[to_i - 1] - origin_x;
        float p1y = y[to_i - 1] - origin_y;
        for(int i = from_i; i < to_i; i++)
        {
            float p2x = x[i] - origin_x;
            float p2y = y[i] - origin_y;

            if(Math.max(p1x, p2x) >= 0)
            {
                if(p1y*p2y <= 0)
                {
                    float cross = cross_product_z(p1x, p1y, p2x, p2y);
                    float sign = p2y - p1y;
                    if(sign != 0)
                    {
                        if(cross*sign >= 0) {
                            if(allow_boundary != POINT_IN_SHAPE_BOUNDARY_DONT_CARE && cross == 0)
                                return allow_boundary == POINT_IN_SHAPE_WITH_BOUNDARY;
                            num_hits += 1;
                        }
                    }
                    else if(allow_boundary != POINT_IN_SHAPE_BOUNDARY_DONT_CARE && p1x*p2x <= 0)
                        return allow_boundary == POINT_IN_SHAPE_WITH_BOUNDARY;
                }
            }

            p1x = p2x;
            p1y = p2y;
        }

        return num_hits % 2 == 1;
    }

    public static boolean is_inside_normalized_bezier(int allow_boundary, float[] x, float[] y, int from_i, int to_i, float origin_x, float origin_y)
    {
        if(from_i + 1 >= to_i || (to_i - from_i) % 2 != 0)
            return false;

        //this has no effect on the global correctness of the algorithm
        // ie setting this wrong will not cause any error stripes, but
        // it will allow pixels right on the boundary to be counted in
        float hit_eps = (float) 1e-7;

        int hits = 0;
        float p1x = x[to_i - 1] - origin_x;
        float p1y = y[to_i - 1] - origin_y;
        for (int i = from_i; i + 1 < to_i; i += 2)
        {
            float p2x = x[i] - origin_x;
            float p2y = y[i] - origin_y;
            float p3x = x[i + 1] - origin_x;
            float p3y = y[i + 1] - origin_y;

            //assert that bezier is normalized:
            // the control point is never an extremity
            //This allows us to write a lot simpler code
            assert Math.min(p1y, p3y) <= p2y && p2y <= Math.max(p1y, p3y);

            //check bounding boxes
            if(Math.max(p1x, p3x) >= 0)
            {
                if(p1y*p3y <= 0)
                {
                    if(p1y != p3y)
                    {
                        //if  is by convention linear segment use the more accurate anc cheaper line intersection
                        if(p3x == p2x && p3y == p2y)
                        {
                            float cross = cross_product_z(p1x, p1y, p3x, p3y);
                            float sign = p3y - p1y;
                            if(cross*sign >= 0) {
                                if(allow_boundary != POINT_IN_SHAPE_BOUNDARY_DONT_CARE && cross == 0)
                                    return allow_boundary == POINT_IN_SHAPE_WITH_BOUNDARY;
                                hits += 1;
                            }
                        }
                        else
                        {
                            //find roots between the quadratic bezier and the x axis
                            // this just means finding t for which B(t)y = y (inv bezier).
                            //Since origin is between p1y and p3y such solution must exist
                            // and further because we have normalized bezier we know that this
                            // solution is unique (normalized bezier means that there are no
                            // parabolas. for each y exists unique x)
                            //This lest us skip checking whether the solution t is valid
                            // (ie if it lays inside the [0, 1] interval) and instead clamp it to the
                            // [0, 1] interval. This gets rid of an array of numerical inaccuracy problems.
                            float t = 0;

                            //get params to the quadratic formula
                            float a = p3y - 2*p2y + p1y;
                            float b = 2*(p2y - p1y);
                            float c = p1y;
                            float D = b*b - 4*a*c;

                            if(a == 0)
                            {
                                //if b = 0 and a = 0 then we get b1 = b2 = b3
                                // which is a case of straight line handled outside this branch
                                // so b will never be zero.
                                assert b != 0;
                                t = -c/b;
                            }
                            //We know that there must be a valid solution => D will not be negative
                            //If it is as a result of floating point we just treat it as zero
                            else if(D <= 0)
                                t = -b/a/2;
                            //In the quadratic formula we are trying to select t in [0, 1]
                            // this depends on a,b,c. If one goes through the cases
                            // for p1 > p3, one finds that we need to select -sqrtD.
                            // Repeating this procedure fo p1 < p3 one find that we need +sqrtD
                            else
                            {
                                float sqrtD = (float) Math.sqrt(D);
                                if(p1y > p3y)
                                    t = (-b - sqrtD)/a/2;
                                else
                                    t = (-b + sqrtD)/a/2;
                            }

                            //assert that our reasoning is correct and all errors are due to floating point
                            float eps = (float) 1e-3;
                            assert -eps < t && t < 1 + eps;

                            //We know t must exist => clamp it to the valid range
                            t = Math.clamp(t, 0, 1);

                            //evaluate bezier x at t
                            float hit1_x = t*t*p3x + 2*t*(1-t)*p2x + (1-t)*(1-t)*p1x;
                            if(-hit_eps <= hit1_x)
                                hits += 1;
                        }
                    }
                    else if(allow_boundary != POINT_IN_SHAPE_BOUNDARY_DONT_CARE)
                        if(p1x*p3x <= 0)
                            return allow_boundary == POINT_IN_SHAPE_WITH_BOUNDARY;
                }
            }

            p1x = p3x;
            p1y = p3y;
        }

        return hits % 2 == 1;
    }

    static final int RAYCAST_ALLOW_P0 = 1;
    static final int RAYCAST_ALLOW_P1 = 2;
    static final int RAYCAST_ALLOW_ENDPOINTS = RAYCAST_ALLOW_P0 | RAYCAST_ALLOW_P1;
    public static boolean raycast_x_line(int allow_endpoints, float p1x, float p1y, float p2x, float p2y)
    {
        if(Math.max(p1x, p2x) >= 0)
        {
            boolean allowed = false;
            if(allow_endpoints == 0)
                allowed = p2y*p1y < 0;
            else if(allow_endpoints == RAYCAST_ALLOW_ENDPOINTS)
                allowed = p2y*p1y <= 0;
            else if(allow_endpoints == RAYCAST_ALLOW_P0)
                allowed = p2y == 0 || p2y*p1y < 0;
            else if(allow_endpoints == RAYCAST_ALLOW_P1)
                allowed = p1y == 0 || p2y*p1y < 0;

            if(allowed)
            {
                float cross = cross_product_z(p1x, p1y, p2x, p2y);
                float sign = p2y - p1y;
                return cross*sign >= 0;
            }
        }
        return false;
    }

    public static boolean raycast_x_line(int allow_endpoints, float origin_x, float origin_y, float p0x, float p0y, float p1x, float p1y)
    {
        return raycast_x_line(allow_endpoints, p0x - origin_x, p1y - origin_y, p1x - origin_x, p1y - origin_y);
    }

    //raycast in x direction starting from origin
    public static int raycast_x_polygon_first_optimistic_hit(float[] x, float[] y, int from_i, int to_i, float origin_x, float origin_y)
    {
        float p1x = x[to_i - 1] - origin_x;
        float p1y = y[to_i - 1] - origin_y;
        for(int i = from_i; i < to_i; i++)
        {
            float p2x = x[i] - origin_x;
            float p2y = y[i] - origin_y;

            if(raycast_x_line(RAYCAST_ALLOW_P0, p1x, p1y, p2x, p2y))
                return i;
        }
        return -1;
    }

    public static float cross_product_z(float[] x, float[] y, int a, int b, int c)
    {
        //cross product implicitly treating the z component as zero,
        // thus will have only the z component nonzero
        //(cross product is orthogonal to both input vectors which are in XY plane)
        float cross_product_z = (x[b] - x[a])*(y[c] - y[a]) - (y[b] - y[a])*(x[c] - x[a]);
        return cross_product_z;
    }

    public static float triangle_area(float[] x, float[] y, int a, int b, int c) 
    {
        return Math.abs(cross_product_z(x, y, a, b, c)/2);
    }
    public static boolean counter_clockwise_is_convex(float[] x, float[] y, int a, int b, int c) 
    {
        return cross_product_z(x, y, a, b, c) >= 0;
    }
    public static boolean clockwise_is_convex(float[] x, float[] y, int a, int b, int c)
    {
        return cross_product_z(x, y, a, b, c) <= 0;
    }

    public static boolean is_in_triangle_interior2(float[] x, float[] y, int a, int b, int c, int p)
    {
        float twice_area = (y[b] - y[c]) * (x[a] - x[c]) - (x[c] - x[b]) * (y[a] - y[c]);
        if(twice_area == 0)
            return false;

        float u = ((y[b] - y[c]) * (x[p] - x[c]) + (x[c] - x[b]) * (y[p] - y[c]))/twice_area;
        float v = ((y[c] - y[a]) * (x[p] - x[c]) + (x[a] - x[c]) * (y[p] - y[c]))/twice_area;
        return (0 < u) && (0 < v) && (u + v < 1);
    }

    public static boolean is_in_triangle_interior(float[] x, float[] y, int p0, int p1, int p2, int p)
    {
        var A2 = (-y[p1]*x[p2] + y[p0]*(-x[p1] + x[p2]) + x[p0]*(y[p1] - y[p2]) + x[p1]*y[p2]);
        var sign = A2 < 0 ? -1 : 1;
        var s = (y[p0]*x[p2] - x[p0]*y[p2] + (y[p2] - y[p0])*x[p] + (x[p0] - x[p2])*y[p])*sign;
        var t = (x[p0]*y[p1] - y[p0]*x[p1] + (y[p0] - y[p1])*x[p] + (x[p1] - x[p0])*y[p])* sign;

        return s > 0 && t > 0 && (s + t) < A2 * sign;
    }


    public static boolean is_in_triangle_with_boundary(float[] x, float[] y, int a, int b, int c, int p)
    {
        //taken from: https://stackoverflow.com/a/20861130
        //I dont really grok how this works
        //Also it does not properly handle vertex points themselves
        var s = (x[a] - x[c]) * (y[p] - y[c]) - (y[a] - y[c]) * (x[p] - x[c]);
        var t = (x[b] - x[a]) * (y[p] - y[a]) - (y[b] - y[a]) * (x[p] - x[a]);

        if ((s < 0) != (t < 0) && s != 0 && t != 0)
            return false;

        var d = (x[c] - x[b]) * (y[p] - y[b]) - (y[c] - y[b]) * (x[p] - x[b]);
        return d == 0 || (d < 0) == (s + t <= 0);
    }

    public static float counter_clockwise_signed_area(float[] x, float[] y, int[] indices, int indices_from, int indices_to)
    {
        if(indices.length == 0)
            return 0;

        float signed_area = 0;
        for(int i = indices_from; i < indices_to; i++) {
            int j = i + 1 < indices_to ? i + 1 : indices_from;
            signed_area += x[i] * y[j] - x[j] * y[i];
        }

        return signed_area;
    }

    public static boolean is_polygon_counter_clockwise(float[] x, float[] y, int[] indices, int indices_from, int indices_to)
    {
        return counter_clockwise_signed_area(x, y, indices, indices_from, indices_to) >= 0;
    }

    public static void test_is_in_triangle()
    {
        class H {
            public static void test_is_in_triangle_single(float x1, float y1, float x2, float y2, float x3, float y3, float px, float py, boolean interior, boolean boundary)
            {
                float[] xs = {x1, x2, x3, px};
                float[] ys = {y1, y2, y3, py};

                float[] degenrate_xs = {x1, x1, x2, x2, x3, x3};
                float[] degenrate_ys = {y1, y1, y2, y2, y3, y3};

                //test all permutations
                {
                    boolean is_interior = is_in_triangle_interior(xs, ys, 0, 1, 2, 3);
                    boolean is_boundary = is_in_triangle_with_boundary(xs, ys, 0, 1, 2, 3);
                    boolean is_inside_polygon_hit_interior = is_inside_polygon_hit(POINT_IN_SHAPE_INTERIOR, xs, ys, 0, 3, px, py);
                    boolean is_inside_polygon_hit_boundary = is_inside_polygon_hit(POINT_IN_SHAPE_WITH_BOUNDARY, xs, ys, 0, 3, px, py);
                    boolean is_inside_polygon_winding_interior = is_inside_polygon_winding(POINT_IN_SHAPE_INTERIOR, xs, ys, 0, 3, px, py);
                    boolean is_inside_polygon_winding_boundary = is_inside_polygon_winding(POINT_IN_SHAPE_WITH_BOUNDARY, xs, ys, 0, 3, px, py);

                    boolean is_inside_normalized_bezier_interior = is_inside_normalized_bezier(POINT_IN_SHAPE_INTERIOR, degenrate_xs, degenrate_ys, 0, 6, px, py);
                    boolean is_inside_normalized_bezier_boundary = is_inside_normalized_bezier(POINT_IN_SHAPE_WITH_BOUNDARY, degenrate_xs, degenrate_ys, 0, 6, px, py);

                    assert interior == is_interior;
                    assert boundary == is_boundary;
                    assert is_inside_polygon_hit_interior == interior;
                    assert is_inside_polygon_hit_boundary == boundary;
                    assert is_inside_polygon_winding_interior == interior;
                    assert is_inside_polygon_winding_boundary == boundary;
                    assert is_inside_normalized_bezier_interior == interior;
                    assert is_inside_normalized_bezier_boundary == boundary;
                }

                {
                    boolean is_interior = is_in_triangle_interior(xs, ys, 1, 2, 0, 3);
                    boolean is_boundary = is_in_triangle_with_boundary(xs, ys, 1, 2, 0, 3);
                    boolean is_inside_polygon_hit_interior = is_inside_polygon_hit(POINT_IN_SHAPE_INTERIOR, xs, ys, 0, 3, px, py);
                    boolean is_inside_polygon_hit_boundary = is_inside_polygon_hit(POINT_IN_SHAPE_WITH_BOUNDARY, xs, ys, 0, 3, px, py);

                    assert interior == is_interior;
                    assert boundary == is_boundary;
                    assert is_inside_polygon_hit_interior == is_interior;
                    assert is_inside_polygon_hit_boundary == boundary;
                }

                {
                    boolean is_interior = is_in_triangle_interior(xs, ys, 2, 0, 1, 3);
                    boolean is_boundary = is_in_triangle_with_boundary(xs, ys, 2, 0, 1, 3);
                    boolean is_inside_polygon_hit_interior = is_inside_polygon_hit(POINT_IN_SHAPE_INTERIOR, xs, ys, 0, 3, px, py);
                    boolean is_inside_polygon_hit_boundary = is_inside_polygon_hit(POINT_IN_SHAPE_WITH_BOUNDARY, xs, ys, 0, 3, px, py);

                    assert interior == is_interior;
                    assert boundary == is_boundary;
                    assert is_inside_polygon_hit_interior == is_interior;
                    assert is_inside_polygon_hit_boundary == boundary;
                }
            }
        }

        //we should really test with random data using u,v vectors 
        // but whatever good enough for now...

        //regular simple case with permutations 
        H.test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0.9f, 0.9f, true, true);
        H.test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0.5f, 0.5f, true, true);

        //boundaries
        for(int i = 1; i < 8; i++) {
            H.test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0, 0.25f*i, false, true);
            H.test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0.25f*i, 0, false, true);
            H.test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 2 - 0.25f*i, 0.25f*i, false, true);
        }

        //vertices points
//        H.test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0, 0, false, true);
//        H.test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 2, 0, false, true);
//        H.test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0, 2, false, true);

        //degenerate triangles
        for(int i = 0; i <= 8; i++) 
            H.test_is_in_triangle_single(0, 0, 2, 0, 1, 0, /**/ 0.25f*i, 0, false, true);
    }
}