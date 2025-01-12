import java.util.ArrayList;
import java.util.Arrays;
public final class Triangulate {

    public static void bezier_normalize_append_x(Buffers.PointArray into, float[] xs, float[] ys, Buffers.IndexBuffer indices)
    {
        if(indices.length == 0)
            return;

        assert indices.length % 2 == 0;
        Splines.Quad_Bezier b1 = new Splines.Quad_Bezier();
        Splines.Quad_Bezier b2 = new Splines.Quad_Bezier();
        into.reserve(indices.length + 8);

        float x1 = xs[indices.at(indices.length - 1)];
        float y1 = ys[indices.at(indices.length - 1)];
        for(int i = 0; i < indices.length; i += 1)
        {
            float x2 = xs[indices.at(i)];
            float y2 = ys[indices.at(i)];
            float x3 = xs[indices.at(i+1)];
            float y3 = ys[indices.at(i+1)];

            //if control point is extreme - either bigger than both or
            // smaller than both. If both are smaller than the product below
            // is positive. If both are bigger than again its positive.
            boolean is_not_extreme1 = (x1 - x2)*(x3 - x2) <= 0;
            boolean is_not_extreme2 = Math.min(x1, x3) <= x2 && x2 <= Math.max(x1, x3);
            assert is_not_extreme1 == is_not_extreme2;

            if((x1 - x2)*(x3 - x2) > 0)
            {
                float t = Splines.bezier_extreme(x1, x2, x3);
                assert 0 < t && t < 1;
                Splines.bezier_split_at_extreme(false, b1, b2, x1, y1, x2, y2, x3, y3, t);
                into.push(b1.x2, b1.y2, b1.x3, b1.y3);
                into.push(b2.x2, b2.y2, b2.x3, b2.y3);

                assert (b1.x1 - b1.x2)*(b1.x3 - b1.x2) <= 0;
                assert (b2.x1 - b2.x2)*(b2.x3 - b2.x2) <= 0;
            }
            else
                into.push(x2, y2, x3, y3);

            x1 = x3;
            y1 = y3;
        }
    }

    public static void bezier_normalize_append_y(Buffers.PointArray into, float[] xs, float[] ys, Buffers.IndexBuffer indices)
    {
        if(indices.length == 0)
            return;

        assert indices.length % 2 == 0;
        Splines.Quad_Bezier b1 = new Splines.Quad_Bezier();
        Splines.Quad_Bezier b2 = new Splines.Quad_Bezier();
        into.reserve(indices.length + 8);

        float x1 = xs[indices.at(indices.length - 1)];
        float y1 = ys[indices.at(indices.length - 1)];
        for(int i = 0; i < indices.length; i += 1)
        {
            float x2 = xs[indices.at(i)];
            float y2 = ys[indices.at(i)];
            float x3 = xs[indices.at(i+1)];
            float y3 = ys[indices.at(i+1)];

            if((y1 - y2)*(y3 - y2) > 0)
            {
                float t = Splines.bezier_extreme(y1, y2, y3);
                assert 0 < t && t < 1;
                Splines.bezier_split_at_extreme(true, b1, b2, x1, y1, x2, y2, x3, y3, t);
                into.push(b1.x2, b1.y2, b1.x3, b1.y3);
                into.push(b2.x2, b2.y2, b2.x3, b2.y3);

                assert (b1.y1 - b1.y2)*(b1.y3 - b1.y2) <= 0;
                assert (b2.y1 - b2.y2)*(b2.y3 - b2.y2) <= 0;
            }
            else
                into.push(x2, y2, x3, y3);

            x1 = x3;
            y1 = y3;
        }
    }

    public static final int BEZIER_IS_NORMALIZED_X = 1;
    public static final int BEZIER_IS_NORMALIZED_Y = 2;
    public static final int BEZIER_IS_NORMALIZED_ASSERT = 4;
    public static boolean bezier_is_normalized(float[] xs, float[] ys, Buffers.IndexBuffer indices, int options)
    {
        boolean out = true;
        float x1 = xs[indices.at(indices.length - 1)];
        float y1 = ys[indices.at(indices.length - 1)];
        for(int i = 0; i < indices.length; i += 1)
        {
            float x2 = xs[indices.at(i)];
            float y2 = ys[indices.at(i)];
            float x3 = xs[indices.at(i+1)];
            float y3 = ys[indices.at(i+1)];

            if((options & BEZIER_IS_NORMALIZED_X) > 0 && (x1 - x2)*(x3 - x2) > 0)
            {
                assert (options & BEZIER_IS_NORMALIZED_ASSERT) == 0;
                out = false;
                break;
            }

            if((options & BEZIER_IS_NORMALIZED_Y) > 0 && (y1 - y2)*(y3 - y2) > 0)
            {
                assert (options & BEZIER_IS_NORMALIZED_ASSERT) == 0;
                out = false;
                break;
            }
            x1 = x3;
            y1 = y3;
        }

        return out;
    }

    public static void bezier_normalize_x(Buffers.PointArray into, float[] xs, float[] ys, Buffers.IndexBuffer indices)
    {
        into.resize(0);
        bezier_normalize_append_x(into, xs, ys, indices);
    }

    public static void bezier_normalize_y(Buffers.PointArray into, float[] xs, float[] ys, Buffers.IndexBuffer indices)
    {
        into.resize(0);
        bezier_normalize_append_y(into, xs, ys, indices);
    }

    public static void bezier_contour_classify(Buffers.IndexBuffer polygon, Buffers.IndexBuffer convex_beziers, Buffers.IndexBuffer concave_beziers, float[] xs, float[] ys, Buffers.IndexBuffer indices, float linear_epsilon, boolean append)
    {
        if(append == false) {
            if(polygon != null) polygon.resize(0);
            if(convex_beziers != null) convex_beziers.resize(0);
            if(concave_beziers != null) concave_beziers.resize(0);
        }

        assert indices.length % 2 == 0;
        int j = indices.length - 2;
        for(int i = 0; i < indices.length; i += 2)
        {
            int vp = indices.at(j+1);
            int vc = indices.at(i+0);
            int vn = indices.at(i+1);

            float x1 = xs[vp];
            float y1 = ys[vp];
            float x2 = xs[vc];
            float y2 = ys[vc];
            float x3 = xs[vn];
            float y3 = ys[vn];

            float cross = Triangulate.cross_product_z(x1, y1, x2, y2, x3, y3);
            if(cross > linear_epsilon) {
                if(polygon != null) polygon.push(vn);
                if(convex_beziers != null) convex_beziers.push(vp, vc, vn);
            }
            else if(cross < -linear_epsilon){
                if(polygon != null) polygon.push(vc, vn);
                if(concave_beziers != null) concave_beziers.push(vp, vc, vn);
            }
            else if(polygon != null)
                polygon.push(vn);
            j = i;
        }
    }

    public static void bezier_contour_classify(Buffers.IndexBuffer polygon_or_null, Buffers.IndexBuffer convex_beziers_or_null, Buffers.IndexBuffer concave_beziers_or_null, float[] xs, float[] ys, Buffers.IndexBuffer indices)
    {
        float eps = (float) 1e-4;
        bezier_contour_classify(polygon_or_null, convex_beziers_or_null, concave_beziers_or_null, xs, ys, indices, eps, false);
    }

    public static int connect_holes(Buffers.IndexBuffer into, float[] xs, float[] ys, Buffers.IndexBuffer indices, Buffers.IndexBuffer[] holes, ArrayList<Integer> error_holes_or_null)
    {
        //calculate the needed allocation size for the holes
        int total_cap = indices.length;
        for(Buffers.IndexBuffer hole : holes)
            total_cap += hole.length;

        total_cap += holes.length*2; //the bridges

        int errors = 0;
        //add all of the input vertices
        into.resize(0);
        into.reserve(into.length + total_cap);
        for(int i = 0; i < indices.length; i += 1)
            into.push(indices.at(i));

        int hole_num = 0;
        for(Buffers.IndexBuffer hole : holes)
        {
            hole_num += 1;
            if(hole.length == 0)
            {
                if(error_holes_or_null != null)
                    error_holes_or_null.add(hole_num - 1);
                errors += 1;
            }
            else
            {
                //test whether this hole is inside the shape
                float first_x = xs[hole.at(0)];
                float first_y = ys[hole.at(0)];
                boolean is_inside = is_inside_polygon_hit(POINT_IN_SHAPE_INTERIOR, xs, ys, into, first_x, first_y);
                if(is_inside == false)
                {
                    if(error_holes_or_null != null)
                        error_holes_or_null.add(hole_num - 1);

                    errors += 1;
                }
                else
                {
                    //find closest vertices
                    int closest_hole_i = -1;
                    int closest_into_i = -1;

                    float min_sqr_dist = Float.POSITIVE_INFINITY;
                    for(int into_i = 0; into_i < into.length; into_i += 1)
                    {
                        float into_x = xs[into.at(into_i)];
                        float into_y = ys[into.at(into_i)];
                        for(int hole_i = 0; hole_i < hole.length; hole_i += 1)
                        {
                            float hole_x = xs[hole.at(hole_i)];
                            float hole_y = ys[hole.at(hole_i)];

                            float dist_x = hole_x - into_x;
                            float dist_y = hole_y - into_y;
                            float sqr_dist = dist_x*dist_x + dist_y*dist_y;
                            if(min_sqr_dist >= sqr_dist) {
                                min_sqr_dist = sqr_dist;
                                closest_hole_i = hole_i;
                                closest_into_i = into_i;
                            }
                        }
                    }

                    //for brevity
                    int into_i = closest_into_i;
                    int into_v = into.at(closest_into_i);
                    int hole_i = closest_hole_i;
                    int hole_v = hole.at(closest_hole_i);

                    //Perform the following merging op
                    //hole: hole[hole_i:]hole[:hole_i]
                    //into: into[:into_i]into[into_i:]
                    //============= merge ==========
                    //merged: into[:into_i + 1]hole[hole_i:]hole[:hole_i + 1]into[into_i:]

                    int move_count = into.length - (into_i + 1);
                    int hole_size = hole.length + 2;
                    into.resize(into.length + hole_size);

                    //add space for new indices
                    System.arraycopy(into.indices, into_i + 1, into.indices, into_i + 1 + hole_size, move_count);
                    Arrays.fill(into.indices, into_i+1, into_i+1 + hole_size, -1);

                    //push new indices
                    int pushing_to = into_i + 1;
                    for(int k = hole_i; k < hole.length; k += 1)
                        into.indices[pushing_to++] = hole.at(k);

                    for(int k = 0; k < hole_i; k += 1)
                        into.indices[pushing_to++] = hole.at(k);

                    //push from bridge
                    into.indices[pushing_to++] = hole_v;
                    into.indices[pushing_to++] = into_v;
                }
            }
        }

        return errors;
    }

    public static int triangulate(Buffers.IndexBuffer triangle_indices, float[] xs, float[] ys, Buffers.IndexBuffer indices, boolean append)
    {
        if(append == false)
            triangle_indices.resize(0);
        triangle_indices.reserve(3*indices.length);

        int remaining_len = indices.length;
        int[] remaining = new int[indices.length];
        for(int i = 0; i < indices.length; i++)
            remaining[i] = indices.at(i);

        while(remaining_len >= 3)
        {
            boolean did_remove = false;
            for(int i = 0; i < remaining_len; i++)
            {
                int rem = remaining_len; //for brevity
                int prev = remaining[i-1 >= 0  ? i-1 : rem-1];
                int curr = remaining[i];
                int next = remaining[i+1 < rem ? i+1 : i+1-rem];

                if(cross_product_z(xs, ys, prev, curr, next) > 0)
                {
                    //check intersections with all points (not prev,curr,next)
                    boolean contains_no_other_points = true;
                    for(int k = 0; k < indices.length; k += 1)
                    {
                        int vert = indices.at(k);
                        if(vert != prev && vert != curr && vert != next) {
                            if(is_in_triangle_with_boundary(xs, ys, prev, curr, next, vert))
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

                        //remove arr[curr] by shifting remaining indices
                        for(int j = i; j < remaining_len - 1; j++)
                            remaining[j] = remaining[j + 1];

                        remaining_len -= 1;

                        did_remove = true;
                        break;
                    }
                }
            }

            if(did_remove == false)
                break;
        }

        return remaining_len >= 3 ? remaining_len : 0;
    }

    public static float cross_product_z(float ux, float uy, float vx, float vy)
    {
        return ux*vy - vx*uy;
    }
    public static float cross_product_z(float px, float py, float p1x, float p1y, float p2x, float p2y)
    {
        return cross_product_z(p1x - px, p1y - py, p2x - px, p2y - py);
    }

    public static int POINT_IN_SHAPE_WITH_BOUNDARY = 0;
    public static int POINT_IN_SHAPE_INTERIOR = 1;
    public static int POINT_IN_SHAPE_BOUNDARY_DONT_CARE = 2;
    public static boolean is_inside_polygon_winding(int allow_boundary, float[] x, float[] y, Buffers.IndexBuffer indices, float origin_x, float origin_y)
    {
        //https://web.archive.org/web/20130126163405/http://geomalgorithms.com/a03-_inclusion.html
        if(indices.length == 0)
            return false;

        int winding_number = 0;
        float p1x = x[indices.at(indices.length - 1)] - origin_x;
        float p1y = y[indices.at(indices.length - 1)] - origin_y;
        for(int i = 0; i < indices.length; i ++)
        {
            float p2x = x[indices.at(i)] - origin_x;
            float p2y = y[indices.at(i)] - origin_y;

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

    public static boolean is_inside_polygon_hit(int allow_boundary, float[] x, float[] y, Buffers.IndexBuffer indices, float origin_x, float origin_y)
    {
        if(indices.length == 0)
            return false;

        int num_hits = 0;
        float p1x = x[indices.at(indices.length - 1)] - origin_x;
        float p1y = y[indices.at(indices.length - 1)] - origin_y;
        for(int i = 0; i < indices.length; i ++)
        {
            float p2x = x[indices.at(i)] - origin_x;
            float p2y = y[indices.at(i)] - origin_y;
            if(Math.max(p1x, p2x) >= 0)
            {
                if(p1y*p2y <= 0)
                {
                    if(Math.min(p1y, p2y) < 0)
                    {
                        float cross = cross_product_z(p1x, p1y, p2x, p2y);
                        float sign = p2y - p1y;
                        if(cross*sign >= 0) {
                            if(allow_boundary != POINT_IN_SHAPE_BOUNDARY_DONT_CARE && cross == 0)
                                return allow_boundary == POINT_IN_SHAPE_WITH_BOUNDARY;
                            num_hits += 1;
                        }
                    }
                    else if(allow_boundary != POINT_IN_SHAPE_BOUNDARY_DONT_CARE && p1y == p2y && p1x*p2x <= 0)
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
        assert (to_i - from_i) % 2 == 0;
        if(from_i + 1 >= to_i)
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
                    if(Math.min(p1y, p2y) < 0)
                    {
                        //if is by convention linear segment use the more accurate and cheaper line intersection
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
                            // parabolas - for each y exists unique x)
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
    public static int raycast_x_polygon_first_optimistic_hit(float[] x, float[] y, Buffers.IndexBuffer indices, float origin_x, float origin_y)
    {
        if(indices.length == 0)
            return -1;

        float p1x = x[indices.at(indices.length - 1)] - origin_x ;
        float p1y = y[indices.at(indices.length - 1)] - origin_y;
        for(int i = 0; i < indices.length; i ++)
        {
            float p2x = x[indices.at(i)] - origin_x;
            float p2y = y[indices.at(i)] - origin_y;

            if(raycast_x_line(RAYCAST_ALLOW_P0, p1x, p1y, p2x, p2y))
                return i-1 >= 0 ? i-1 : indices.length-1;

            p1x = p2x;
            p1y = p2y;
        }
        return -1;
    }

    public static int raycast_x_polygon_first_optimistic_hit(float[] x, float[] y, int from_i, int to_i, float origin_x, float origin_y)
    {
        float p1x = x[to_i - 1] - origin_x;
        float p1y = y[to_i - 1] - origin_y;
        for(int i = from_i; i < to_i; i++)
        {
            float p2x = x[i] - origin_x;
            float p2y = y[i] - origin_y;

            if(raycast_x_line(RAYCAST_ALLOW_P0, p1x, p1y, p2x, p2y))
                return i-1 >= 0 ? i-1 : to_i-1;

            p1x = p2x;
            p1y = p2y;
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

    public static boolean is_in_triangle_interior(float[] x, float[] y, int p0, int p1, int p2, int p)
    {
        var A2 = (-y[p1]*x[p2] + y[p0]*(-x[p1] + x[p2]) + x[p0]*(y[p1] - y[p2]) + x[p1]*y[p2]);
        var sign = Math.signum(A2);
        var s = (y[p0]*x[p2] - x[p0]*y[p2] + (y[p2] - y[p0])*x[p] + (x[p0] - x[p2])*y[p])*sign;
        var t = (x[p0]*y[p1] - y[p0]*x[p1] + (y[p0] - y[p1])*x[p] + (x[p1] - x[p0])*y[p])*sign;

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
        float signed_area = 0;
        int j = indices_to - 1;
        for(int i = indices_from; i < indices_to; i++) {
            signed_area += x[i]*y[j] - x[j]*y[i];
            j = i;
        }

        return signed_area/2;
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
                Buffers.IndexBuffer range = Buffers.IndexBuffer.til(3);

                float[] degenrate_xs = {x1, x1, x2, x2, x3, x3};
                float[] degenrate_ys = {y1, y1, y2, y2, y3, y3};

                //test all permutations
                {
                    boolean is_interior = is_in_triangle_interior(xs, ys, 0, 1, 2, 3);
                    boolean is_boundary = is_in_triangle_with_boundary(xs, ys, 0, 1, 2, 3);
                    boolean is_inside_polygon_hit_interior = is_inside_polygon_hit(POINT_IN_SHAPE_INTERIOR, xs, ys, range, px, py);
                    boolean is_inside_polygon_hit_boundary = is_inside_polygon_hit(POINT_IN_SHAPE_WITH_BOUNDARY, xs, ys, range, px, py);
                    boolean is_inside_polygon_winding_interior = is_inside_polygon_winding(POINT_IN_SHAPE_INTERIOR, xs, ys, range, px, py);
                    boolean is_inside_polygon_winding_boundary = is_inside_polygon_winding(POINT_IN_SHAPE_WITH_BOUNDARY, xs, ys, range, px, py);

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
                    boolean is_inside_polygon_hit_interior = is_inside_polygon_hit(POINT_IN_SHAPE_INTERIOR, xs, ys, range, px, py);
                    boolean is_inside_polygon_hit_boundary = is_inside_polygon_hit(POINT_IN_SHAPE_WITH_BOUNDARY, xs, ys, range, px, py);

                    assert interior == is_interior;
                    assert boundary == is_boundary;
                    assert is_inside_polygon_hit_interior == is_interior;
                    assert is_inside_polygon_hit_boundary == boundary;
                }

                {
                    boolean is_interior = is_in_triangle_interior(xs, ys, 2, 0, 1, 3);
                    boolean is_boundary = is_in_triangle_with_boundary(xs, ys, 2, 0, 1, 3);
                    boolean is_inside_polygon_hit_interior = is_inside_polygon_hit(POINT_IN_SHAPE_INTERIOR, xs, ys, range, px, py);
                    boolean is_inside_polygon_hit_boundary = is_inside_polygon_hit(POINT_IN_SHAPE_WITH_BOUNDARY, xs, ys, range, px, py);

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

    public static AABB_Vertices aabb_calculate(float[] xs, float[] ys, Buffers.IndexBuffer indices)
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
                int index = indices.at(k);
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

            aabb.x0_vertex = indices.at(aabb.x0_index);
            aabb.x1_vertex = indices.at(aabb.x1_index);
            aabb.y0_vertex = indices.at(aabb.y0_index);
            aabb.y1_vertex = indices.at(aabb.y1_index);
        }
        return aabb;
    }
}