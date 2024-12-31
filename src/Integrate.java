public final class Integrate {
    public static abstract class Integrand_1D {
        public long iters;
        public double dt_end;
        public abstract double eval(double t, double x);
    }

    public static double rk1_2_integrate_1d(Integrand_1D integrand, double x_ini, double dt_ini, double t_ini, double t_end, double epsilon, double min_dt, double max_dt)
    {
        //Heun-Euler embedded adaptive method
        //see: https://wiki.math.ntnu.no/_media/tma4130/2020h/ode.pdf
        assert dt_ini > 0;
        assert epsilon > 0;

        double dt = dt_ini;
        double x = x_ini;
        double t = t_ini;

        double k1 = integrand.eval(t, x);

        integrand.iters = 1;
        for(;; integrand.iters += 1)
        {
            boolean end = t + dt >= t_end;
            if(end) {
                integrand.dt_end = dt;
                dt = t_end - t;
            }

            double k2 = integrand.eval(t + dt, x + dt*k1);
            double delta = dt/2*Math.abs(k2 - k1);
            if(delta < epsilon)
            {
                x = x + dt/2*(k1 + k2);
                t = t + dt;

                if(end) break;
                if(delta == 0) continue;
            }
            dt = Math.clamp(0.8*Math.sqrt(epsilon/delta)*dt, min_dt, max_dt);
        }
        return x;
    }

    public static double rk2_3_integrate_1d(Integrand_1D integrand, double x_ini, double dt_ini, double t_ini, double t_end, double epsilon, double min_dt, double max_dt)
    {
        //Bogacki–Shampine embedded adaptive method
        //see: https://tobydriscoll.net/fnc-julia/ivp/adaptive-rk.html

        //TODO
        return 0;
    }
    
    public static double rk4_5_integrate_1d(Integrand_1D integrand, double x_ini, double dt_ini, double t_ini, double t_end, double epsilon, double min_dt, double max_dt)
    {
        //Runge-Kutta-Merson embedded adaptive method
        //see: https://encyclopediaofmath.org/wiki/Kutta-Merson_method
        assert dt_ini > 0;
        assert epsilon > 0;

        double dt = dt_ini;
        double x = x_ini;
        double t = t_ini;

        double k1 = integrand.eval(t, x);

        integrand.iters = 1;
        for(;; integrand.iters += 1)
        {
            boolean end = t + dt >= t_end;
            if(end) {
                integrand.dt_end = dt;
                dt = t_end - t;
            }

            double k2 = integrand.eval(t + dt/3, x + dt/3*k1);
            double k3 = integrand.eval(t + dt/3, x + dt/6*(k1 + k2));
            double k4 = integrand.eval(t + dt/2, x + dt/8*(k1 + 3*k2));
            double k5 = integrand.eval(t + dt,   x + dt/2*(k1 - 3*k3 + 4*k4));

            double delta = dt/3*Math.abs(0.2*k1 - 0.9*k3 + 0.8*k4 - 0.1*k5);
            if(delta < epsilon)
            {
                x = x + dt/6*(k1 + 4*k4 + k5);
                t = t + dt;

                if(end) break;
                if(delta == 0) continue;
            }
            dt = Math.clamp(Math.pow(epsilon/delta, 0.2)*dt, min_dt, max_dt);
        }

        return x;
    }

    public static double rk4_5_integrate_1d(Integrand_1D integrand, double x_ini, double dt_ini, double t_ini, double t_end)
    {
        return rk4_5_integrate_1d(integrand, x_ini, dt_ini, t_ini, t_end, 1e-8, 1e-6, Double.POSITIVE_INFINITY);
    }

    public static double rk1_2_integrate_1d(Integrand_1D integrand, double x_ini, double dt_ini, double t_ini, double t_end)
    {
        return rk1_2_integrate_1d(integrand, x_ini, dt_ini, t_ini, t_end, 1e-8, 1e-6, Double.POSITIVE_INFINITY);
    }

    public static abstract class Simple_Integrand {
        public int dimensions;
        public abstract void eval(float[] out_values, float t);
    }

    public static float[] simpsons_rule_integrate(Simple_Integrand integrand, float h, float a, float b)
    {
        // recaluclate h and n to
        float h_in = h;
        long n = (long) Math.ceil((b - a)/h_in);
        n += n % 2;
        h = (b - a)/n;

        int dims = integrand.dimensions;
        float[] step = new float[dims];
        float[] integral = new float[dims];

        // integral = f(a) + f(b)
        // for i in range(1, n, 2):
        // integral += 4 * f(a + i*h)
        // for i in range(2, n-1, 2):
        // integral += 2 * f(a + i*h)
        //integral *= h/3

        integrand.eval(integral, a);
        integrand.eval(step, b);
        for(int d = 0; d < dims; d++)
            integral[d] += step[d];

        for(int i = 1; i < n; i += 2)
        {
            integrand.eval(step, a);
            for(int d = 0; d < dims; d++)
                integral[d] += 4*step[d];
        }

        for(int i = 2; i < n-1; i += 2)
        {
            integrand.eval(step, a);
            for(int d = 0; d < dims; d++)
                integral[d] += 4*step[d];
        }

        for(int d = 0; d < dims; d++)
            integral[d] *= h/3;

        return integral;
    }
}