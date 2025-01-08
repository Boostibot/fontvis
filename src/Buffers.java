import java.util.Arrays;

public class Buffers {
    public static final class PointArray
    {
        public float[] xs = new float[0];
        public float[] ys = new float[0];
        public int length = 0;

        void resize(int length)
        {
            reserve(length);
            this.length = length;
        }

        void reserve(int length)
        {
            if(length > this.xs.length) {
                int new_cap = Math.max(this.xs.length*3/2 + 8, length);
                this.xs = Arrays.copyOf(this.xs, new_cap);
                this.ys = Arrays.copyOf(this.ys, new_cap);
            }
        }

        void push(float x1, float y1)
        {
            reserve(this.length + 1);
            this.xs[this.length] = x1;
            this.ys[this.length] = y1;
            this.length += 1;
        }

        void push(float x1, float y1, float x2, float y2)
        {
            reserve(this.length + 2);
            this.xs[this.length] = x1;
            this.ys[this.length] = y1;
            this.xs[this.length + 1] = x2;
            this.ys[this.length + 1] = y2;
            this.length += 2;
        }

        void push(float x1, float y1, float x2, float y2, float x3, float y3)
        {
            reserve(this.length + 3);
            this.xs[this.length] = x1;
            this.ys[this.length] = y1;
            this.xs[this.length + 1] = x2;
            this.ys[this.length + 1] = y2;
            this.xs[this.length + 2] = x3;
            this.ys[this.length + 2] = y3;
            this.length += 3;
        }
    }

    public static final class BoolArray
    {
        public boolean[] items = new boolean[0];
        public int length = 0;

        void resize(int length)
        {
            reserve(length);
            this.length = length;
        }

        void reserve(int length)
        {
            if(length > this.items.length) {
                int new_cap = Math.max(this.items.length*3/2 + 8, length);
                this.items = Arrays.copyOf(this.items, new_cap);
            }
        }

        void push(boolean b)
        {
            resize(this.length + 1);
            this.items[this.length - 1] = b;
        }
    }

    //IndexBuffer acts as a simple array iterator (from, to, stride).
    //Optionally one can also attach actual array of indices for more indirection
    // and arbitrary iteration order. In case of attached index array
    // the (from, to, stride) applies to it.
    public static final class IndexBuffer {
        public int[] indices;
        public int from;
        public int length;
        public int stride = 1;

        public int at(int i)
        {
            int o = from + stride*i;
            if(indices == null)
                return o;
            else
                return indices[o];
        }

        void resize(int length)
        {
            reserve(length);
            this.length = length;
        }

        void reserve(int length)
        {
            if(indices == null || length > indices.length) {
                if(indices == null)
                {
                    indices = new int[length];
                    for(int i = 0; i < length; i++)
                        indices[i] = from + i*stride;

                    from = 0;
                    stride = 1;
                }
                else
                {
                    int new_cap = Math.max(length, this.indices.length*3/2 + 8);
                    this.indices = Arrays.copyOf(this.indices, this.from + new_cap*this.stride);
                }
            }
        }

        void push(int i)
        {
            reserve(length + 1);
            indices[from + length*stride] = i;
            length += 1;
        }

        void push(int i1, int i2)
        {
            reserve(length + 2);
            indices[from + (length + 0)*stride] = i1;
            indices[from + (length + 1)*stride] = i2;
            length += 2;
        }

        void push(int i1, int i2, int i3)
        {
            reserve(length + 3);
            indices[from + (length + 0)*stride] = i1;
            indices[from + (length + 1)*stride] = i2;
            indices[from + (length + 2)*stride] = i3;
            length += 3;
        }

        public static IndexBuffer til(int to)
        {
            return IndexBuffer.range(0, to, 1);
        }

        public static IndexBuffer range(int from, int to)
        {
            return IndexBuffer.range(from, to, 1);
        }

        public static IndexBuffer range(int from, int to, int stride)
        {
            IndexBuffer out = new IndexBuffer();
            out.from = from;
            out.stride = stride;
            out.length = (to - from)/stride;
            return out;
        }

        public static IndexBuffer from_indices(int[] indices, int from, int to)
        {
            IndexBuffer out = new IndexBuffer();
            out.indices = indices;
            out.from = from;
            out.length = to - from;
            out.stride = 1;
            return out;
        }
    }
}
