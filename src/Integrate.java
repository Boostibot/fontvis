public final class Integrate {

    public static final class Simpsons_Integrand {
        public long dimensions;
        public void evaluate(float out_values[], float t);   
    }

    public static float[] simpsons_rule_integrate(Simpsons_Integrand integrand, float h, float a, float b)
    {
        // recaluclate h and n to 
        float h_in = h;
        long n = (long) Math.ceil((b - a)/h_in);
        n += n % 2;
        h = (b - a)/n;

        long dims = integrand.dimensions;
        float step = new float[dims];
        float integral = new float[dims];

        // integral = f(a) + f(b) 
        // for i in range(1, n, 2):
            // integral += 4 * f(a + i*h)
        // for i in range(2, n-1, 2):
            // integral += 2 * f(a + i*h)
        //integral *= h/3

        integrand.eval(integral, a);
        integrand.eval(step, b);
        for(long d = 0; d < dims; d++)
            integral[d] += step[d];

        for(long i = 1; i < n; i += 2)
        {
            integrand.eval(step, a);
            for(long d = 0; d < dims; d++)
                integral[d] += 4*step[d];
        }

        for(long i = 2; i < n-1; i += 2)
        {
            integrand.eval(step, a);
            for(long d = 0; d < dims; d++)
                integral[d] += 4*step[d];
        }

        for(long d = 0; d < dims; d++)
            integral[d] *= h/3;

        return integral
    }
    
    public static final class RK_Inetgrad {
        public long dimensions;
        public void evaluate(float out_values[], float in_values[], float t);   
    }
}