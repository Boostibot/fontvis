public static final class Splines {

    
    public static final class Quad_Bezier {
        public float x1;
        public float x2;
        public float x3;

        public float y1;
        public float y2;
        public float y3;
    }

    public static void split_quadratic_bezier(Quad_Bezier s1, Quad_Bezier s2, float x1, float y1, float x2, float y2, float x3, float y3, float z) 
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

    public static void split_quadratic_bezier_recusive(Quad_Bezier s1, Quad_Bezier s2, float x1, float y1, float x2, float y2, float x3, float y3, int ) 
    {
    }
}