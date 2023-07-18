package shortestpath;

import java.util.Arrays;

public class SimpleIntHashMap<V> {
    private V[][] values;
    private int[][] keys;
    private int size;
    private int capacity;
    private int bucketSize = 8;
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

    private static int hash(int value) {
        return value ^ (value >>> 16);
    }

    private int getBucket(int key) {
        return (hash(key) & 0x7FFFFFFF) % (int)maxSize;
    }

    private int bucketIndex(int key, int bucket) {
        int[] keyBucket = keys[bucket];
        if (keyBucket == null) {
            return -1;
        }

        for (int i = 0; i < bucketSize; ++i) {
            if (keyBucket[i] == key) {
                return i;
            }
        }

        // Searched the entire map and found nothing
        return -1;
    }

    public V get(int key) {
        int bucket = getBucket(key);
        int index = bucketIndex(key, bucket);
        if (index == -1) {
            return null;
        }
        return values[bucket][index];
    }

    public V getOrDefault(int key, V defaultValue) {
        int bucket = getBucket(key);
        int index = bucketIndex(key, bucket);
        if (index == -1) {
            return defaultValue;
        }
        return values[bucket][index];
    }

    public V put(int key, V value) {
        int index = getBucket(key);
        V[] valueBucket = values[index];

        if (valueBucket == null) {
            int[] keyBucket = new int[bucketSize];
            keyBucket[0] = key;
            keys[index] = keyBucket;

            @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
            V[] temp = (V[])new Object[bucketSize];
            temp[0] = value;
            values[index] = temp;

            incrementSize();
            return null;
        }

        int[] keyBucket = keys[index];
        for (int i = 0; i < bucketSize; ++i) {
            if (keyBucket[i] == key) {
                V previous = values[index][i];
                values[index][i] = value;
                return previous;
            } else if (valueBucket[i] == null) {
                keyBucket[i] = key;
                valueBucket[i] = value;
                return null;
            }
        }

        grow();
        return put(key, value);
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

        V[][] oldValues = values;
        int[][] oldKeys = keys;
        recreateArrays();

        for (int i = 0; i < oldValues.length; ++i) {
            V[] oldValueBucket = oldValues[i];
            if (oldValueBucket == null) {
                continue;
            }

            int[] oldKeyBucket = oldKeys[i];
            for (int ind = 0; ind < bucketSize; ++ind) {
                if (oldValueBucket[ind] == null) {
                    break;
                }

                int bucketIndex = getBucket(ind);
                V[] valueBucket = values[bucketIndex];
                if (valueBucket == null) {
                    @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
                    V[] newValueBucket = (V[])new Object[bucketSize];
                    newValueBucket[0] = oldValueBucket[i];
                    values[bucketIndex] = newValueBucket;

                    int[] newKeyBucket = new int[bucketSize];
                    newKeyBucket[0] = oldKeyBucket[i];
                    keys[bucketIndex] = newKeyBucket;
                } else {
                    int bInd;
                    for (bInd = 0; bInd < bucketSize; ++bInd) {
                        if (valueBucket[bInd] == null) {
                            valueBucket[bInd] = oldValueBucket[i];
                            keys[bucketIndex][bInd] = oldKeyBucket[i];
                        }
                    }

                    if (bInd >= bucketSize) {
                        grow();
                        return;
                    }
                }
            }
        }
    }

    private void recreateArrays() {
        @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
        V[][] tempValues = (V[][])new Object[(int)maxSize][];
        values = tempValues;
        keys = new int[(int)maxSize][];
    }

    public void clear() {
        size = 0;
        Arrays.fill(keys, null);
        Arrays.fill(values, null);
    }
}
