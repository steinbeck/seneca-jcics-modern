package io.github.steinbeck.seneca.chem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * compchem {@code LIFOQueue}: bounded list, {@link #push} inserts at index 0 and truncates to the
 * capacity (the oldest entry is dropped), {@link #pop} removes index 0.
 *
 * <p>Reference: {@code legacy/decompiled/compchem/compchem/LIFOQueue.java}. Used by
 * {@code structgen.StructureGeneratorResult} (capacity 30; {@code merge} raises the capacity).
 *
 *
 * @param <T> element type
 */
public final class LifoQueue<T> {

    private final List<T> items = new ArrayList<>();
    private int capacity;
    /**
     * Length of the legacy {@code Vector}'s internal array: 10 for a new queue ({@code LIFOQueue(int)}
     * calls {@code Vector()}), doubled whenever an insertion finds it full, never shrunk. Only visible in
     * the message of the {@code setSize(-1)} failure of {@code removeIsomorphism}.
     */
    private int vectorLength = 10;

    public LifoQueue(int capacity) {
        this.capacity = capacity;
    }

    /** Insert at 0; drop entries beyond the capacity (legacy {@code setSize(size)}). */
    public void push(T item) {
        items.add(0, item);
        grown();
        while (items.size() > capacity) items.remove(items.size() - 1);
    }

    /** Remove and return index 0 (throws {@link IndexOutOfBoundsException} when empty). */
    public T pop() {
        return items.remove(0);
    }

    public T get(int index) {
        return items.get(index);
    }

    public int size() {
        return items.size();
    }

    public int capacity() {
        return capacity;
    }

    /** Legacy {@code LIFOQueue.size += n} (used by {@code StructureGeneratorResult.merge}). */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void insert(int index, T item) {
        items.add(index, item);
        grown();
    }

    private void grown() {
        while (items.size() > vectorLength) vectorLength *= 2;
    }

    /** Length of the legacy {@code Vector}'s internal array (see the field). */
    public int legacyArrayLength() {
        return vectorLength;
    }

    public T removeAt(int index) {
        return items.remove(index);
    }

    /** Legacy {@code Vector.setSize(n)} with {@code n <= size()}: truncate. */
    public void truncate(int newSize) {
        while (items.size() > newSize) items.remove(items.size() - 1);
    }

    /** Snapshot of the elements, newest first. */
    public List<T> toList() {
        return new ArrayList<>(items);
    }

    /** Deep copy with an element copier; same capacity. */
    public LifoQueue<T> copy(UnaryOperator<T> copier) {
        LifoQueue<T> q = new LifoQueue<>(capacity);
        for (T t : items) q.items.add(copier.apply(t));
        q.vectorLength = vectorLength;      // a serialized Vector keeps its array length
        return q;
    }
}
