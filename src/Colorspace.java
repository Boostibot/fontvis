public final class Colorspace {
    public float x; //r
    public float y; //g
    public float z; //b
    public float a; //alpha

    public static int ucast(byte val)
    {
        return (int) val & 0xFF;
    }

    public static byte hex_t(int hex)
    {
        return (byte) (hex >> 24);
    }

    public static byte hex_a(int hex)
    {
        return (byte) (255 - (byte) (hex >> 24));
    }

    public static byte hex_r(int hex)
    {
        return (byte) (hex >> 16);
    }

    public static byte hex_g(int hex)
    {
        return (byte) (hex >> 8);
    }

    public static byte hex_b(int hex)
    {
        return (byte) (hex);
    }

    public static int rgba_to_hex(byte r, byte g, byte b, byte a)
    {
        return ucast((byte)(255 - a)) << 24 | ucast(r) << 16 | ucast(g) << 8 | ucast(b);
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
}