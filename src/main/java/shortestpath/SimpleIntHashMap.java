package shortestpath;

import java.lang.reflect.Array;
import java.util.Arrays;

// This class is not intended as a general purpose replacement for a hashmap; it lacks convenience features
// found in regular maps and has no way to remove elements or get a list of keys/values.
public class SimpleIntHashMap<V> {
    // Unless the hash function is really unbalanced, most things should fit within small 4-element buckets
    // Buckets will grow as needed without forcing a rehash of the whole map
    private final int DEFAULT_BUCKET_SIZE = 4;

    // The larger the value, the less often rehashing needs to be done, but the more space the map requires
    private final float GROWTH_RATE = 2.0f;

    // How full the map should get before growing it again. Smaller values speed up lookup times at the expense of space
    private final float DEFAULT_LOAD_FACTOR = 0.75f;

    private class IntNode<V> {
        private int key;
        private V value;

        private IntNode(int key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // This is just a container to get around Java's generic cast constraints. If buckets become too large then
    // it may be worth converting large buckets into an array-backed binary tree
    private class Bucket {
        private IntNode<V>[] values;

        private Bucket(int size) {
            @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
            IntNode<V>[] temp = (IntNode<V>[])Array.newInstance(IntNode.class, size);
            values = temp;
        }
    }
    private Bucket[] buckets;
    private int size;
    private int capacity;
    private long maxSize;
    private final float loadFactor;

    public SimpleIntHashMap(int initialSize) {
        loadFactor = DEFAULT_LOAD_FACTOR;
        size = 0;
        maxSize = initialSize;
        capacity = calculateCapacity();
        recreateArrays();
    }

    public SimpleIntHashMap(int initialSize, float loadFactor) {
        this.loadFactor = loadFactor;
        size = 0;
        maxSize = initialSize;
        growCapacity();
        recreateArrays();
    }

    private static int hash(int value) {
        return value ^ (value >>> 16);
    }

    private int getBucket(int key) {
        return (hash(key) & 0x7FFFFFFF) % (int)maxSize;
    }

    private int bucketIndex(int key, int bucketIndex) {
        Bucket bucket = buckets[bucketIndex];
        if (bucket == null) {
            return -1;
        }

        for (int i = 0; i < bucket.values.length; ++i) {
            if (bucket.values[i] == null) {
                break;
            }
            if (bucket.values[i].key == key) {
                return i;
            }
        }

        // Searched the bucket and found nothing
        return -1;
    }

    public V get(int key) {
        return getOrDefault(key, null);
    }

    public V getOrDefault(int key, V defaultValue) {
        int bucket = getBucket(key);
        int index = bucketIndex(key, bucket);
        if (index == -1) {
            return defaultValue;
        }
        return buckets[bucket].values[index].value;
    }

    public V put(int key, V value) {
        int bucketIndex = getBucket(key);
        Bucket bucket = buckets[bucketIndex];

        if (bucket == null) {
            @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
            Bucket newBucket = new Bucket(DEFAULT_BUCKET_SIZE);
            IntNode<V> newNode = new IntNode<>(key, value);
            newBucket.values[0] = newNode;
            buckets[bucketIndex] = newBucket;
            incrementSize();
            return null;
        }

        for (int i = 0; i < bucket.values.length; ++i) {
            if (bucket.values[i] == null) {
                IntNode<V> newNode = new IntNode<>(key, value);
                bucket.values[i] = newNode;
                incrementSize();
                return null;
            } else if (bucket.values[i].key == key) {
                V previous = bucket.values[i].value;
                bucket.values[i].value = value;
                incrementSize();
                return previous;
            }
        }

        // No space in the bucket, grow it
        growBucket(bucketIndex).values[bucket.values.length] = new IntNode<>(key, value);
        incrementSize();
        return null;
    }

    private void incrementSize() {
        size++;
        if (size >= capacity) {
            rehash();
        }
    }

    // Ideally the map should grow before buckets do, but just in-case
    private Bucket growBucket(int bucketIndex) {
        Bucket oldBucket = buckets[bucketIndex];
        Bucket newBucket = new Bucket(oldBucket.values.length * 2);
        System.arraycopy(oldBucket.values, 0, newBucket.values, 0, oldBucket.values.length);
        buckets[bucketIndex] = newBucket;
        return newBucket;
    }

    private int calculateCapacity() {
        return (int)(maxSize * loadFactor);
    }

    private void growCapacity() {
        maxSize = (long)(maxSize * GROWTH_RATE);
        if (maxSize >= Integer.MAX_VALUE) {
            throw new IllegalStateException("Hashmap size is larger than max array size");
        }

        capacity = calculateCapacity();
    }

    // Grow the bucket array then rehash all the values into new buckets and discard the old ones
    private void rehash() {
        growCapacity();

        Bucket[] oldBuckets = buckets;
        recreateArrays();

        for (int i = 0; i < oldBuckets.length; ++i) {
            Bucket oldBucket = oldBuckets[i];
            if (oldBucket == null) {
                continue;
            }

            for (int ind = 0; ind < oldBucket.values.length; ++ind) {
                if (oldBucket.values[ind] == null) {
                    break;
                }

                int bucketIndex = getBucket(oldBucket.values[ind].key);
                Bucket newBucket = buckets[bucketIndex];
                if (newBucket == null) {
                    newBucket = new Bucket(DEFAULT_BUCKET_SIZE);
                    newBucket.values[0] = oldBucket.values[ind];
                    buckets[bucketIndex] = newBucket;
                } else {
                    int bInd;
                    for (bInd = 0; bInd < newBucket.values.length; ++bInd) {
                        if (newBucket.values[bInd] == null) {
                            newBucket.values[bInd] = oldBucket.values[ind];
                            break;
                        }
                    }

                    if (bInd >= newBucket.values.length) {
                        growBucket(bucketIndex).values[newBucket.values.length] = oldBucket.values[ind];
                        return;
                    }
                }
            }
        }
    }

    private void recreateArrays() {
        @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
        Bucket[] temp = (Bucket[])Array.newInstance(Bucket.class, (int)maxSize);
        buckets = temp;
    }

    public void clear() {
        size = 0;
        Arrays.fill(buckets, null);
    }
}
