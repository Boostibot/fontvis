public final class Splines {

    public static float lerp(float p1, float p2, float t)
    {
        return (t - 1)*p1 + t*p2;
    }

    //t = 0 => p1
    //t = 1 => p3
    public static float bezier(float p1, float p2, float p3, float t)
    {
        return t*t*p3 + 2*t*(1-t)*p2 + (1-t)*(1-t)*p1;
        //return t*t*(p1 - 2*p2 + p3) - t*2*(p2 - p3) + p3
    }

    public static float bezier_derivative(float p1, float p2, float p3, float t)
    {
        return 2*t*(p3 - 2*p2 + p1) + 2*(p2 - p1);
    }

    public static float bezier_second_derivative(float p1, float p2, float p3)
    {
        return 2*(p3 - 2*p2 + p1);
    }

    public static final class Quad_Bezier {
        public float x1 = 0;
        public float x2 = 0;
        public float x3 = 0;

        public float y1 = 0;
        public float y2 = 0;
        public float y3 = 0;
    }
    public static float bezier_x(Quad_Bezier b, float t) { return bezier(b.x1, b.x2, b.x3, t); }
    public static float bezier_y(Quad_Bezier b, float t) { return bezier(b.y1, b.y2, b.y3, t); }
    public static float bezier_derivative_x(Quad_Bezier b, float t) { return bezier_derivative(b.x1, b.x2, b.x3, t); }
    public static float bezier_derivative_y(Quad_Bezier b, float t) { return bezier_derivative(b.y1, b.y2, b.y3, t); }
    public static float bezier_second_derivative_x(Quad_Bezier b) { return bezier_second_derivative(b.x1, b.x2, b.x3); }
    public static float bezier_second_derivative_y(Quad_Bezier b) { return bezier_second_derivative(b.y1, b.y2, b.y3); }

    public static float bezier_curvature(float x1, float y1, float x2, float y2, float x3, float y3, float t)
    {
        //see: https://pomax.github.io/bezierinfo/#curvature
        float dx = bezier_derivative(x1, x2, x3, t);
        float dy = bezier_derivative(y1, y2, y3, t);
        float ddx = bezier_second_derivative(x1, x2, x3);
        float ddy = bezier_second_derivative(y1, y2, y3);

        float kappa = (dx*ddy - ddx*dy)/(float) Math.pow(dx*dx + dy*dy, 1.5);
        return kappa;
    }

    //returns scale t of the first line such that
    // off1 + t*dir1 = off2 + s*dir2
    // for some other scale s
    public static float intersect_lines(
            float dir1x, float dir1y, float off1x, float off1y,
            float dir2x, float dir2y, float off2x, float off2y)
    {
        float Bx = off2x - off1x;
        float By = off2y - off1y;

        float num = By*dir2x - Bx*dir2y;
        float den = dir2x*dir1y - dir2y*dir1x;

        if(den == 0)
            return Float.POSITIVE_INFINITY;
        else
            return num/den;
    }

    public static final class Two_Hit {
        public int number = 0;
        public float r1 = 0;
        public float r2 = 0;

        public void set(int number, float r1, float r2) {
            this.number = number;
            this.r1 = r1;
            this.r2 = r2;
        }
    }

    public static final class Intersections {
        public float[] rs = new float[8];
        public int number = 0;

        public void set(int number, float r1, float r2) {
            this.number = number;
            this.rs[0] = r1;
            this.rs[1] = r2;
        }
    }

    public static int quadratic_solve(Two_Hit roots, float a, float b, float c)
    {
        if(a == 0)
        {
            if(b == 0)
                roots.set(0, 0, 0);
            else
                roots.set(1, -c/b, 0);
        }
        else
        {
            float D = b*b - 4*a*c;
            if(D < 0)
                roots.set(0, 0, 0);
            else if(D == 0)
                roots.set(1, -b/(2*a), 0);
            else
            {
                float sqrtD = (float) Math.sqrt(D);
                roots.set(2, (-b + sqrtD)/(2*a), (-b - sqrtD)/(2*a));
            }
        }

        return roots.number;
    }

    public static int bezier_inv(Two_Hit roots, float p1, float p2, float p3, float val)
    {
        float a = p1 - 2*p2 + p3;
        float b = 2*p2 - 2*p3;
        float c = p3 - val;

        quadratic_solve(roots, a, b, c);
        if(0 > roots.r1 || roots.r1 > 1)
        {
            roots.r1 = 0;
            roots.number -= 1;
        }

        if(0 > roots.r2 || roots.r2 > 1)
        {
            roots.r2 = 0;
            roots.number -= 1;
        }

        return roots.number;
    }

    public static float vec_rotate_x(float x, float y, float rot_x, float rot_y)
    {
        return x*rot_x - y*rot_y;
    }
    public static float vec_rotate_y(float x, float y, float rot_x, float rot_y)
    {
        return x*rot_y + y*rot_x;
    }

    //returns up to two t bezier parameters of the intersection of bezier curve with the x axis [0, lenx]
    public static int bezier_x_intersect(Two_Hit out_intersection, float lenx, float x1, float y1, float x2, float y2, float x3, float y3)
    {
        //find roots between the quadratic and
        // the x axis
        // (we reuse out_intersection just because we can)
        int bezier_hit_count = 0;
        float[] bezier_hits = new float[2];
        bezier_inv(out_intersection, y1, y2, y3, 0);
        bezier_hits[0] = out_intersection.r1;
        bezier_hits[1] = out_intersection.r2;

        for(int i = 0; i < out_intersection.number; i++)
        {
            float hit1_x = bezier(x1, x2, x3, out_intersection.r1);
            //hit_x in [0, rdirx] or hit_x in [rdirx, 0]
            if((0 <= hit1_x && hit1_x <= lenx) || (lenx <= hit1_x && hit1_x <= 0))
            {
                bezier_hits[i] = out_intersection.r1;
                bezier_hit_count += 1;
            }
        }

        out_intersection.number = bezier_hit_count;
        out_intersection.r1 = bezier_hits[0];
        out_intersection.r2 = bezier_hits[1];
        return bezier_hit_count;
    }

    //saves into Two_Hit intersections the t bezier parameters of the intersections.
    //returns the number of intersections in range [0, 2]
    public static int bezier_line_intersect(
        Two_Hit out_intersection, float x1, float y1, float x2, float y2, float x3, float y3,
        float dirx, float diry, float offx, float offy)
    {
        //shift so that ray is in origin
        float sx1 = x1 - offx;
        float sx2 = x2 - offx;
        float sx3 = x3 - offx;

        float sy1 = y1 - offy;
        float sy2 = y2 - offy;
        float sy3 = y3 - offy;

        //if the direction is purely vertical or horizontal we can do a cheaper alternative
        if(diry == 0)
            return bezier_x_intersect(out_intersection, dirx, x1, y1, x2, y2, x3, y3);
        else if(dirx == 0)
            //if the x component is 0 we simply transpose everything and find intersection that
            // way
            return bezier_x_intersect(out_intersection, dirx, y1, x1, y2, x2, y3, x3);
        else
        {
            //inverse rotate each vector around dir
            // this will result in the line going perfectly horizontal
            // and the bezier being rotated somehow
            float rx1 = vec_rotate_x(sx1, sy1, dirx, -diry);
            float rx2 = vec_rotate_x(sx2, sy2, dirx, -diry);
            float rx3 = vec_rotate_x(sx3, sy3, dirx, -diry);

            float ry1 = vec_rotate_y(sx1, sy1, dirx, -diry);
            float ry2 = vec_rotate_y(sx2, sy2, dirx, -diry);
            float ry3 = vec_rotate_y(sx3, sy3, dirx, -diry);

            //lastly rotate the direction vector itself - we do this because
            // dir is generally not a unit vector so rating by it also
            // has the effect of scaling things by its magnitude
            // (we could fix this by dividing by its magnitude but
            //  that would be a bit slower)
            // (upon expanding this operation it turns out that
            //  rdirx = dirx^2 + diry^2)
            float rdirx = vec_rotate_x(dirx, diry, dirx, -diry);

            return bezier_x_intersect(out_intersection, rdirx, rx1, ry1, rx2, ry2, rx3, ry3);
        }
    }

    public static double bezier_arc_length(Quad_Bezier b, double x_ini, double dt_ini, double t_ini, double t_end, double epsilon, double min_dt, double max_dt)
    {
        assert dt_ini > 0;
        assert epsilon > 0;

        double dt = dt_ini;
        double x = 0;
        double t = t_ini;

        class H {
            public static double bezier_derivative_magnitude(Quad_Bezier b, double t)
            {
                double dx = bezier_derivative_x(b, (float) t);
                double dy = bezier_derivative_y(b, (float) t);
                return Math.hypot(dx, dy);
            }
        }

        double k1 = H.bezier_derivative_magnitude(b, t);
        double dt_end = dt;
        for(;;) {
            boolean end = t + dt >= t_end;
            if(end) {
                dt_end = dt;
                dt = t_end - t;
            }

            double k2 = H.bezier_derivative_magnitude(b, t + dt);
            double delta = dt/2*Math.abs(k2 - k1);
            if(delta < epsilon)
            {
                x = x + dt/2*(k1 + k2);
                t = t + dt;

                if(end) break;
                if(delta == 0) continue;
            }
            dt = Math.clamp(0.95*Math.sqrt(epsilon/delta)*dt, min_dt, max_dt);
        }
        return x;
    }

    public static double bezier_arc_length(Quad_Bezier b, double x_ini, double dt_ini, double t_ini, double t_end)
    {
        return bezier_arc_length(b, x_ini, dt_ini, t_ini, t_end, 1e-6, 1e-8, 1e6);
    }

    public static double bezier_arc_length(Quad_Bezier b)
    {
        return bezier_arc_length(b, 0, 1e-2, 0, 1, 1e-6, 1e-8, 1e6);
    }


    public static float bezier_arc_length(float[] ts, float[] dists, Quad_Bezier b, float from_t, float to_t, float dt)
    {
        int saves = 0;
        if(ts != null && dists != null)
            saves = Math.min(ts.length, dists.length);

        dt = Math.max(dt, (float) 1e-7);
        float p0x = bezier(b.x1, b.x2, b.x3, from_t);
        float p0y = bezier(b.y1, b.y2, b.y3, from_t);

        long iters = Math.max((long) Math.ceil((to_t - from_t)/dt), saves);
        double arc_len = 0;
        int save = 1;
        for(long iter = 1; iter <= iters; iter++)
        {
            float t = Math.min(from_t + iter*dt, to_t);
            float p1x = bezier(b.x1, b.x2, b.x3, t);
            float p1y = bezier(b.y1, b.y2, b.y3, t);
            double dist = Math.hypot(p1x - p0x, p1y - p0y);

            p0x = p1x;
            p0y = p1y;
            arc_len += dist;

            //equivalent to (in reals)
            // iter/iters >= save/saves
            while(iter*saves >= save*iters)
            {
                ts[save - 1] = t;
                dists[save - 1] = (float) arc_len;
                save += 1;
            }
        }

        assert saves == 0 || save - 1 == saves;
        assert saves == 0 || ts[saves - 1] == to_t;
        return (float) arc_len;
    }

    public static int main(String[] args)
    {
        Quad_Bezier b = new Quad_Bezier();
        b.x1 = 0;
        b.y1 = 0;

        b.x2 = 1;
        b.y2 = 4;

        b.x3 = 2;
        b.y3 = 0;

        double L1 = bezier_arc_length(b);
        double L2 = bezier_arc_length(null, null, b, 0, 1, 0.01f);
        return 0;
    }

    public static float bezier_arc_length(Quad_Bezier b, float from_t, float to_t, float dt)
    {
        return bezier_arc_length(null, null, b, from_t, to_t, dt);
    }

    public static float bezier_arc_length(Quad_Bezier b, float dt)
    {
        return bezier_arc_length(null, null, b, 0, 1, dt);
    }

    public static float bezier_inv_distance(Quad_Bezier b, float[] ts, float[] dists, float dist)
    {
        if(ts.length == 0 || dist <= 0)
            return 0;

        if(dist >= dists[dists.length - 1])
            return 1;

        //lower bound search:
        // find index i such that dist <= dists[i]
        // => dists[i-1] <= dist <= dists[i]
        // (supposing i is in [1, dists.length))
        int count = dists.length;
        int first = 0;
        while (count > 0)
        {
            int step = count / 2;
            int it = first + step;

            if (dists[it] < dist)
            {
                first = ++it; //TODO ERROR HERE!!
                count -= step + 1;
            }
            else
                count = step;
        }

        int i = first;
        assert i != dists.length;

        //interpolate between two last datapoints
        class H {
            public static float safe_lerp(float v1, float v2, float num, float den)
            {
                if(den == 0)
                    return v1;

                float t = num/den;
                return (1 - t)*v1 + t*v2;
            }
        }

        if(i == 0)
            return H.safe_lerp(0, ts[0], dist, dists[0]);
        else
            return H.safe_lerp(ts[i-1], ts[i], dist - dists[i-1], dists[i] - dists[i-1]);
    }

    public static void bezier_resample_uniform(Quad_Bezier b)
    {
        //cache some distances
        //binary find target
        //
    }

    public static void bezier_split(Quad_Bezier s1, Quad_Bezier s2, float x1, float y1, float x2, float y2, float x3, float y3, float z)
    {
        //see: https://math.stackexchange.com/questions/1408478/subdividing-a-b%C3%A9zier-curve-into-n-curves
        s1.x1 = x1;
        s1.y1 = y1;
        s1.x2 = (1-z)*x1 + z*x2;
        s1.y2 = (1-z)*y1 + z*y2;
        s1.x3 = (1-z)*(1-z)*x1 + 2*(1-z)*z*x2 + z*z*x3;
        s1.y3 = (1-z)*(1-z)*y1 + 2*(1-z)*z*y2 + z*z*y3;

        s2.x1 = s1.x3;
        s2.y1 = s1.y3;
        s1.x2 = (1-z)*x2 + z*x3;
        s1.y2 = (1-z)*y2 + z*y3;
        s2.x3 = x3;
        s2.y3 = y3;
    }
}