package shortestpath;

import java.util.Arrays;

public class SimpleIntHashMap<V> {
    private V[] values;
    private int[] keys;
    private int size;
    private int capacity;
    private long maxSize;
    private final float loadFactor;

    public SimpleIntHashMap(int initialSize) {
        size = 0;
        loadFactor = 0.5f;
        setNewCapacity(initialSize);
        recreateArrays();
    }

    public SimpleIntHashMap(int initialSize, float loadFactor) {
        size = 0;
        this.loadFactor = loadFactor;
        setNewCapacity(initialSize);
        recreateArrays();
    }

    private int getIndex(int key) {
        return ((key % capacity) + capacity) % capacity;
    }

    private int indexOf(int key) {
        int startIndex = getIndex(key);
        int index = startIndex;
        do {
            if (values[index] == null) {
                return -1;
            } else if (key == keys[index]) {
                return index;
            }
        } while ((index = (index + 1) % capacity) != startIndex);
        // Searched the entire map and found nothing
        return -1;
    }

    public V get(int key) {
        int index = indexOf(key);
        if (index == -1) {
            return null;
        }
        return values[index];
    }

    public V getOrDefault(int key, V defaultValue) {
        int index = indexOf(key);
        if (index == -1) {
            return defaultValue;
        }
        return values[index];
    }

    public V put(int key, V value) {
        int startIndex = getIndex(key);
        int index = startIndex;
        do {
            if (values[index] == null) {
                keys[index] = key;
                values[index] = value;
                incrementSize();
                return null;
            } else if (keys[index] == key) {
                V previous = values[index];
                values[index] = value;
                return previous;
            }
        } while ((index = (index + 1) % capacity) != startIndex);

        // This shouldn't be reached
        throw new IllegalStateException("Cannot insert object with key: " + key);
    }

    private void incrementSize() {
        size++;
        if (size >= capacity) {
            grow();
        }
    }

    private long calculateMaxSize(int newCapacity) {
        return (long)Math.ceil((2.0f - loadFactor) * newCapacity);
    }

    private void setNewCapacity(int newCapacity) {
        maxSize = calculateMaxSize(newCapacity);
        if (maxSize >= Integer.MAX_VALUE) {
            throw new IllegalStateException("New size is larger than max array size");
        }
        capacity = newCapacity;
    }

    private void grow() {
        setNewCapacity((int)maxSize);

        V[] oldValues = values;
        int[] oldKeys = keys;
        recreateArrays();

        for (int i = 0; i < oldValues.length; ++i) {
            V oldValue = oldValues[i];
            if (oldValue == null) {
                continue;
            }

            int oldKey = oldKeys[i];
            int index = getIndex(oldKey);

            while (true) {
                if (values[index] == null) {
                    keys[index] = oldKey;
                    values[index] = oldValue;
                    break;
                }
                index = (index + 1) % capacity;
            }
        }
    }

    private void recreateArrays() {
        @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
        V[] tempValues = (V[])new Object[capacity];
        values = tempValues;
        keys = new int[capacity];
    }

    public void clear() {
        size = 0;
        Arrays.fill(keys, 0);
        Arrays.fill(values, null);
    }
}
