//Behaves like std::vector but also allows slicing.
//Slicing gives view of the part of the array to some other object.
//The slice is
public class IntArray implements Cloneable {
    public int[] array = new int[0];
    public int length = 0;
    public int capacity = 0;
    public int from = 0;

    public void reserve(int desired_capacity)
    {
        //invariants
        assert 0 <= from && from <= array.length;
        assert 0 <= capacity && capacity < array.length - from;
        assert 0 <= length && length <= capacity;

        if(desired_capacity > capacity) {
            int new_capacity = capacity*3/4 + 8;
            if(new_capacity < desired_capacity)
                new_capacity = desired_capacity;

            int[] new_array = new int[new_capacity];
            System.arraycopy(array, from, new_array, 0, array.length);
            from = 0;
            array = new_array;
            capacity = new_capacity;
        }
    }

    public void resize(int new_length)
    {
        reserve(new_length);
        if(length < new_length) {
            for(int i = length; i < new_length; i++)
                array[i] = 0;
        }
        length = new_length;
    }

    public int at(int i) {
        if(i < 0 || i > length)
            throw new ArrayIndexOutOfBoundsException();

        return array[i];
    }
    public void at(int i, int val) {
        if(i < 0 || i > length)
            throw new ArrayIndexOutOfBoundsException();

        array[i] = val;
    }

    public void push(int val)
    {
        reserve(length + 1);
        array[length] = val;
        length += 1;
    }

    public void push(int[] items, int from, int to)
    {
        assert (0 <= from && from <= to && to <= items.length);
        reserve(length + to - from);
        System.arraycopy(items, from, array, length, to - from);
        length += to - from;
    }

    public void push(int[] items)
    {
        push(items, 0, items.length);
    }

    public int pop()
    {
        length -= 1;
        return array[length];
    }

    public void insert_hole(int at, int count)
    {
        assert at >= 0;
        reserve(at + count);
        System.arraycopy(array, at, array, at + count, count);
        length += count;
    }

    public void remove(int at, int count)
    {
        assert at >= 0;
        if(at + count > length)
            count = length - at;

        System.arraycopy(array, at + count, array, at, count);
        length -= count;
    }

    public void insert(int val, int at)
    {
        insert_hole(at, 1);
        array[at] = val;
    }

    public Object clone()
    {
        //I dont care about super.clone(), shut up.
        IntArray out = with_capacity(length);
        System.arraycopy(array, from, out.array, 0, length);
        out.length = 0;
        return out;
    }

    public IntArray range(int from, int to)
    {
        assert 0 <= from && from <= length;
        assert from <= to && to <= length;

        IntArray out = new IntArray();
        out.from = this.from + from;
        out.length = to - from;
        out.capacity = out.length;
        out.array = this.array;
        return out;
    }

    public IntArray safe_range(int from, int to)
    {
        IntArray out = new IntArray();
        if(from < 0)
            from = 0;
        if(from > out.length)
            from = out.length;

        if(to < from)
            to = from;
        if(to > out.length)
            to = out.length;

        out.from = this.from + from;
        out.length = to - from;
        out.capacity = out.length;
        out.array = this.array;
        return out;
    }

    public IntArray range() { return range(0, length); }
    public IntArray head(int from) { return range(from, length); }
    public IntArray tail(int from) { return range(0, from); }
    public IntArray safe_head(int from) { return safe_range(from, length); }
    public IntArray safe_tail(int from) { return safe_range(0, from); }

    public static IntArray with_capacity(int capacity)
    {
        IntArray out = new IntArray();
        out.reserve(capacity);
        return out;
    }

    public static IntArray with_size(int capacity)
    {
        IntArray out = new IntArray();
        out.resize(capacity);
        return out;
    }

    public static IntArray from_array(int[] array, int from, int to)
    {
        IntArray out = new IntArray();
        out.array = array;
        out.from = from;
        out.length = to - from;
        out.capacity = out.length;
        return out;
    }

    public static IntArray from_array(int[] array)
    {
        return from_array(array, 0, array.length);
    }
}