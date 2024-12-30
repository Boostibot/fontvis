public final class Triangulate {
    public static final class AABB {
        public float x0;
        public float x1;
        public float y0;
        public float y1;
    }
    
    public static void aabb_calculate(AABB aabb, float[] xs, float[] ys, int indices[])
    {
        //todo
    }

    public static void int_buffer_insert_many(IntBuffer buffer, int vals[], int at)
    {
        buffer.resize(buffer.length + vals.length);
        int[] arr = buffer.array();
        System.arrayCopy(arr, at, arr, at + vals.length, vals.length);
        System.arrayCopy(vals, 0, arr, at, vals.length);
    }

    public static int[] connect_holes(float[] xs, float[] ys, int indices[], int holes[][], )
    {
        if(indices.length == 0)
            return new int[0];

        //calculate the needed allocation size for the holes
        int total_cap = indices.length;
        for(int i = 0; i < holes.length; i++)
            total_cap += holes[i].length;

        total_cap += holes.length*2; //the bridges

        IntBuffer res = IntBuffer.allocate(total_cap); 
        res.put(indices);

        //todo
        int right_most_indices = new int[];
        int sorted_holes = new int[][];
        
        Raycast_Result ray_res = new Raycast_Result();

        for(int hole_i = 0; hole_i < sorted_holes.length; hole_i++)
        {
            int[] hole = sorted_holes[hole_i];
            int right_most_i = right_most_indices[i];
            
            if(hole.length > 0)
            {
                float origin_x = xs[right_most_i];
                float origin_y = ys[right_most_i];

                if(raycast_x_polygon(ray_res, xs, ys, res.array(), origin_x, origin_y))
                {
                    int connect_to = ray_res.vertex_0;
                    int indices_at = ray_res.index_buffer_0;

                    //hole: hole[right_most_i:]hole[:right_most_i]
                    //vert: res[:indices_at]res[indices_at:] 
                    //============= merge ==========   
                    //merged: res[:indices_at][connection]hole[right_most_i:]hole[:right_most_i][connection]res[indices_at:]     
                } 
                else
                {
                    assert false;
                }
            }
        }

        //merge all holes
        for(int hole_i = 0; hole_i < holes.length; hole_i++)
        {
            //for each hole find minimum distance between its vertex 
            // and the vertex of the resulting polygon so 
            int[] hole = holes[hole_i];
            if(hole.length > 0)
            {
                int min_i = 0;
                int min_j = 0;
                float min_dist = Math.POSITIVE_INFINITY;

                for(int i = 0; i < hole.length; i++)
                    for(int j = 0; j < res.length; i++)
                    {
                        float dx = xs[i] - xs[j];
                        float dy = ys[i] - ys[j];
                        float sqr_dist = dx*dx + dy*dy;
                        if(min_dist > sqr_dist)
                        {
                            min_i = i;
                            min_j = j;
                            min_dist = sqr_dist;
                        }
                    }
            }
        }
    }

    public static int[] triangulate(float[] xs, float[] ys, int indices[])
    {
        IntBuffer triangle_indices = IntBuffer.allocate(3*indices.length); 
        int[] remaining = Arrays.copyOf(indices);
        int remaining_len = remaining.length;

        while(remaining_len >= 3)
        {
            boolean did_remove = false;
            for(int i = 1; i <= remaining_len; i++)
            {
                int rem = remaining_len; //for brevity
                int prev = indices[i - 1];
                int curr = indices[i   < rem ? i   : i-rem];
                int next = indices[i+1 < rem ? i+1 : i+1-rem];

                if(counter_clockwise_is_convex(xs, ys, prev, curr, next))
                {
                    //check intersections with all points (not prev,curr,next)
                    boolean contains_no_other_points = true;
                    for(int i = 0; i < indices; i++)
                    {
                        if(i < prev || i > next)
                        {
                            if(is_in_triangle_with_boundary(xs, ys, prev, curr, next, i))
                            {
                                contains_no_other_points = false;
                                break;
                            }
                        }
                    }

                    if(contains_no_other_points)
                    {
                        triangle_indices.put(prev);
                        triangle_indices.put(curr);
                        triangle_indices.put(next);

                        //remove arr[curr]
                        for(int j = curr; j < remaining_len - 1; j++)
                            arr[j] = arr[j + 1];
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

        return triangle_indices.array();
    }
    
    public static final class Raycast_Result {
        public int vertex_0;
        public int vertex_1;

        public int index_buffer_0;
        public int index_buffer_1;

        public float x;
        public float y;

        public boolean hit;
    }

    //raycast in x direction starting from origin
    public static boolean raycast_x_polygon(Raycast_Result result, float[] x, float[] y, int polygon[], float origin_x, float origin_y)
    {
        for(int i = 0; i < polygon.length; i++)
        {
            int j = i+1 < polygon.length ? i+1 : 0;

            int p0i = polygon[i];
            int p1i = polygon[j];

            float p0x = x[p0i] - origin_x;
            float p0y = y[p0i] - origin_y;
            
            float p1x = x[p1i] - origin_x;
            float p1y = y[p1i] - origin_y;

            //y must be between p0 and p1, thus one and only one of p0.y - origin_y or p1.y - origin_y must be negativie.
            //Thus to check if it is between all we need to do is check wheter wheter their product is negative
            // (or zero in which case y can be at one end point).
            if(p0y*p1y <= 0)
            {
                //x must be to the left of p0,p1
                if(0 <= p0x || 0 <= p1x)
                {
                    float dx = p0x - p1x;
                    float dy = p0y - p1y;
                    if(dy == 0)
                    {
                        result.vertex_0 = p0i;
                        result.vertex_1 = p1i;
                        result.index_buffer_0 = i;
                        result.index_buffer_1 = j;
                        result.x = origin_x;
                        result.y = origin_y;
                        result.hit = true;
                        return true;
                    }
                    
                    //instead of 0 would normally be origin_y but since we 
                    // shifted everything by origin its 0
                    float intersect_x = dx/dy*(0 - p0y) + p0x;
                    
                    if(intersect.x >= 0)
                    {
                        result.vertex_0 = p0i;
                        result.vertex_1 = p1i;
                        result.index_buffer_0 = i;
                        result.index_buffer_1 = j;
                        result.x = origin_x;
                        result.y = origin_y;
                        result.hit = true;
                        return true;
                    }
                }
            }
        }

        result.vertex_0 = 0;
        result.vertex_1 = 0;
        result.index_buffer_0 = 0;
        result.index_buffer_1 = 0;
        result.x = 0;
        result.y = 0;
        result.hit = false;
        return false;
    }

    public static int small_mod(int i, int len)
    {
        if(i < len)
            return i;
        else
            return i - len;
    }

    public static float triangle_area(float[] x, float[] y, int a, int b, int c) 
    {
        float cross_product_z = (x[b] - x[a])*(y[c] - y[a]) - (y[b] - y[a])*(x[c] - x[a]);
        return cross_product_z >= 0 ? cross_product_z : -cross_product_z;
    }
    public static boolean counter_clockwise_is_convex(float[] x, float[] y, int a, int b, int c) 
    {
        //cross product implicitly treating the z component as zero, 
        // thus will have only the z component nonzero
        //(cross product is ortogonal to both input vectors which are in XY plane)
        float cross_product_z = (x[b] - x[a])*(y[c] - y[a]) - (y[b] - y[a])*(x[c] - x[a]);
        return cross_product_z >= 0;
    }

    public static boolean is_in_triangle_interior(float[] x, float[] y, int a, int b, int c, int p)
    {
        float twice_area = (y[b] - y[c]) * (x[a] - x[c]) - (x[c] - x[b]) * (y[a] - y[c]);
        if(twice_area == 0)
            return false;

        float u = ((y[b] - y[c]) * (x[p] - x[c]) + (x[c] - x[b]) * (y[p] - y[c]))/twice_area; 
        float v = ((y[c] - y[a]) * (x[p] - x[c]) + (x[a] - x[c]) * (y[p] - y[c]))/twice_area;
        return (0 < u) && (0 < v) && (u + v < 1);
    }
    
    public static boolean is_in_triangle_with_boundary(float[] x, float[] y, int a, int b, int c, int p)
    {
        //taken from: https://stackoverflow.com/a/20861130
        var s = (x[a] - x[c]) * (y[p] - y[c]) - (y[a] - y[c]) * (x[p] - x[c]);
        var t = (x[b] - x[a]) * (y[p] - y[a]) - (y[b] - y[a]) * (x[p] - x[a]);

        if ((s < 0) != (t < 0) && s != 0 && t != 0)
            return false;

        //I dont really grok how this works
        var d = (x[c] - x[b]) * (y[p] - y[b]) - (y[c] - y[b]) * (x[p] - x[b]);
        return d == 0 || (d < 0) == (s + t <= 0);
    }

    
    public static void test_is_in_triangle_single(float x1, float y1, float x2, float y2, float x3, float y3, float px, float py, boolean interior, boolean boundary)
    {
        float[] xs = {x1, x2, x3, px};
        float[] ys = {y1, y2, y3, py};
        int[] indices = {0, 1, 2};

        //test all permutations
        {
            boolean is_interior = is_in_triangle_interior(xs, ys, 0, 1, 2, 3);
            boolean is_boundary = is_in_triangle_with_boundary(xs, ys, 0, 1, 2, 3);
            assert interior == is_interior;
            assert boundary == is_boundary;
        }
        
        {
            boolean is_interior = is_in_triangle_interior(xs, ys, 1, 2, 0, 3);
            boolean is_boundary = is_in_triangle_with_boundary(xs, ys, 1, 2, 0, 3);
            assert interior == is_interior;
            assert boundary == is_boundary;
        }
        
        {
            boolean is_interior = is_in_triangle_interior(xs, ys, 2, 0, 1, 3);
            boolean is_boundary = is_in_triangle_with_boundary(xs, ys, 2, 0, 1, 3);
            assert interior == is_interior;
            assert boundary == is_boundary;
        }
    }

    public static void test_is_in_triangle()
    {
        //we should really test with random data using u,v vectors 
        // but whatever good enought for now...

        //regular simple case with permutations 
        test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 1, 1, true, true); 
        test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0.5, 0.5, true, true); 

        //boundaries
        for(int i = 1; i < 8; i++) {
            test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0, 0.25*i, false, true); 
            test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0.25*i, 0, false, true); 
            test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 2 - 0.25*i, 0.25*i, false, true); 
        }

        //vertices points
        test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0, 0, false, true); 
        test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 2, 0, false, true); 
        test_is_in_triangle_single(0, 0, 2, 0, 0, 2, /**/ 0, 2, false, true); 

        //degenerate triangles
        for(int i = 0; i <= 8; i++) 
            test_is_in_triangle_single(0, 0, 2, 0, 1, 0, /**/ 0, 0.25*i, false, true); 
    }
}