import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Array-backed min-heap for int values. This mutable class is not thread-safe. */
public final class IntMinHeap {
    private static final int DEFAULT_CAPACITY = 16;

    private int[] elements;
    private int size;

    public IntMinHeap() {
        elements = new int[DEFAULT_CAPACITY];
    }

    /** Builds a heap in O(n) time without retaining the caller's array. */
    public IntMinHeap(int[] values) {
        Objects.requireNonNull(values, "values");
        elements = Arrays.copyOf(values, Math.max(DEFAULT_CAPACITY, values.length));
        size = values.length;
        for (int index = size / 2 - 1; index >= 0; index--) {
            siftDown(index);
        }
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void add(int value) {
        ensureCapacity();
        elements[size] = value;
        siftUp(size);
        size++;
    }

    public int peek() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        return elements[0];
    }

    public int poll() {
        int minimum = peek();
        int last = elements[--size];
        if (size > 0) {
            elements[0] = last;
            siftDown(0);
        }
        return minimum;
    }

    private void siftUp(int index) {
        int value = elements[index];
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (elements[parent] <= value) {
                break;
            }
            elements[index] = elements[parent];
            index = parent;
        }
        elements[index] = value;
    }

    private void siftDown(int index) {
        int value = elements[index];
        int firstLeaf = size / 2;
        while (index < firstLeaf) {
            int left = index * 2 + 1;
            int right = left + 1;
            int smallerChild = left;
            if (right < size && elements[right] < elements[left]) {
                smallerChild = right;
            }
            if (value <= elements[smallerChild]) {
                break;
            }
            elements[index] = elements[smallerChild];
            index = smallerChild;
        }
        elements[index] = value;
    }

    private void ensureCapacity() {
        if (size < elements.length) {
            return;
        }
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity + (oldCapacity >>> 1) + 1;
        if (newCapacity < 0) {
            throw new OutOfMemoryError("heap capacity overflow");
        }
        elements = Arrays.copyOf(elements, newCapacity);
    }
}
