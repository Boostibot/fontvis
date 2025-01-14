public final class Colorspace {
    public float x; //r
    public float y; //g
    public float z; //b
    public float a; //alpha

    public static int hex_r(int hex)
    {
        return (int) (hex >>> 16);
    }

    public static int hex_g(int hex)
    {
        return (int) (hex >>> 8) & 0xFF;
    }

    public static int hex_b(int hex)
    {
        return (int) (hex) & 0xFF;
    }

    //transparency: 0 = completely opaque, 1 = completely transparent
    public static int hex_t(int hex)
    {
        return (int) (hex >>> 24) & 0xFF;
    }

    //alpha = 1 - transparency: 0 = completely transparent, 1 = completely opaque
    public static int hex_a(int hex)
    {
        return (int) (255 - (int) (hex >>> 24) & 0xFF);
    }

    public static int rgba_to_hex(int r, int g, int b, int a)
    {
        int ri = (int) r & 0xFF;
        int gi = (int) g & 0xFF;
        int bi = (int) b & 0xFF;
        int ti = (int) (255 - a) & 0xFF;
        return ti << 24 | ri << 16 | gi << 8 | bi;
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

    public Colorspace(Colorspace other)
    {
        x = other.x;
        y = other.y;
        z = other.z;
        a = other.a;
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

    public Colorspace hsv_to_rgb(float h, float s, float v) {
        float r = 0;
        float g = 0;
        float b = 0;

        int i = (int) Math.floor(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            case 5: r = v; g = p; b = q; break;
        }

        this.x = r;
        this.y = g;
        this.z = b;
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

    public static void test_colorspace()
    {
        class H {
            public static void test_rgba_hex_conv(int hex)
            {
                int r = Colorspace.hex_r(hex);
                int g = Colorspace.hex_g(hex);
                int b = Colorspace.hex_b(hex);
                int a = Colorspace.hex_a(hex);

                int back = Colorspace.rgba_to_hex(r,g,b,a);
                assert back == hex;
            }

            public static void test_lin_oklab_conv(int hex)
            {
                Colorspace lin = new Colorspace(hex);
                Colorspace lin_back = new Colorspace(hex);
                lin_back.lin_to_oklab().oklab_to_lin();

                float eps = (float) 1e-3;
                assert Math.abs(lin.x - lin_back.x) < eps;
                assert Math.abs(lin.y - lin_back.y) < eps;
                assert Math.abs(lin.z - lin_back.z) < eps;
                assert Math.abs(lin.a - lin_back.a) < eps;
            }
        }

        H.test_rgba_hex_conv(0xFF00FFFF);
        H.test_rgba_hex_conv(0x0F00FF00);
        H.test_rgba_hex_conv(0x33333333);

        H.test_lin_oklab_conv(0x00000000);
        H.test_lin_oklab_conv(0x000000FF);
        H.test_lin_oklab_conv(0xFF00FFFF);
        H.test_lin_oklab_conv(0x0F00FF00);
        H.test_lin_oklab_conv(0x33333333);
        H.test_lin_oklab_conv(0xFFFFFFFF);
    }
}