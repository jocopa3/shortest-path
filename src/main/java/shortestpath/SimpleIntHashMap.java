package shortestpath;

import java.lang.reflect.Array;
import java.util.Arrays;

public class SimpleIntHashMap<V> {
    private class IntNode<V> {
        private int key;
        private V value;

        IntNode(int key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // This is just a container to get around Java's generic cast constraints
    // If buckets become too large then it may be worth converting to linked-list and turn buckets into a balanced tree
    private class Bucket {
        IntNode<V>[] values;
        Bucket(int size) {
            @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
            IntNode<V>[] temp = (IntNode<V>[])Array.newInstance(IntNode.class, size);
            values = temp;
        }
    }

    private Bucket[] buckets;
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
        int bucket = getBucket(key);
        int index = bucketIndex(key, bucket);
        if (index == -1) {
            return null;
        }
        return buckets[bucket].values[index].value;
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
            Bucket newBucket = new Bucket(bucketSize);
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
                return null;
            } else if (bucket.values[i].key == key) {
                V previous = bucket.values[i].value;
                bucket.values[i].value = value;
                return previous;
            }
        }

        // No space in the bucket, grow it
        growBucket(bucketIndex).values[bucket.values.length] = new IntNode<>(key, value);
        return null;
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

    private Bucket growBucket(int bucketIndex) {
        Bucket oldBucket = buckets[bucketIndex];
        Bucket newBucket = new Bucket(oldBucket.values.length * 2);
        System.arraycopy(oldBucket.values, 0, newBucket.values, 0, oldBucket.values.length);
        buckets[bucketIndex] = newBucket;
        return newBucket;
    }

    private void grow() {
        setNewCapacity((int)maxSize);

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

                int bucketIndex = getBucket(ind);
                Bucket newBucket = buckets[bucketIndex];
                if (newBucket == null) {
                    newBucket = new Bucket(bucketSize);
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
