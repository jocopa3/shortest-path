package shortestpath.datastructures;

import java.util.Arrays;

public class PrimitiveLongArray {
	// Double the size until this bit is set
	private static final int HIGHEST_SIZE_BIT = 20; // Effectively 1048576

	// Once HIGHEST_SIZE_BIT is set, this is the amount to grow linearly by
	private static final int LINEAR_GROWTH_BIT = 17; // Effectively 131072

	// Max size is all bits set up to HIGHEST_SIZE_BIT
	private static final int MAX_SIZE = (1 << (HIGHEST_SIZE_BIT + 1)) - 1; // Effectively 2097151

	private long[] array;
	private int maxIndex = 0;

	public PrimitiveLongArray(int initialSize) {
		final int size = Integer.highestOneBit(initialSize) << 1;
		array = new long[size];
	}

	/*
		Double the size until the HIGHEST_SIZE_BIT is set; from there, perform linear growth
		In this case, size doubles up to 1048576, then increases in increments of 131072 until reaching 2097151

		Reasoning for this logic is that the OSRS map generally has at most 1 million visitable nodes. Any situations
		where more nodes are needed shouldn't force the array size to keep doubling and waste exponential space.
	 */
	private static int newSize(final int oldSize) {
		int size = oldSize;
		if ((size << 1) > MAX_SIZE) {
			// This becomes a compile-time constant
			final int LINEAR_GROWTH_BIT_MASK = (1 << (HIGHEST_SIZE_BIT - LINEAR_GROWTH_BIT + 1)) - 1;

			size = (size >>> LINEAR_GROWTH_BIT) + 1;
			size = size > LINEAR_GROWTH_BIT_MASK
					? MAX_SIZE // Max size was reached
					: (size & LINEAR_GROWTH_BIT_MASK) << LINEAR_GROWTH_BIT; // Linearly grow until max size
		} else {
			size <<= 1;
		}

		if (size <= oldSize) {
			// Exceeded the arbitrary max size
			// Hitting this means that either OSRS map expanded a lot or something is wrong
			throw new OutOfMemoryError();
		}

		return size;
	}

	// Returns the index of the newly added element
	public int add(long value) {
		final int index = maxIndex++;
		if (index >= array.length) {
			grow();
		}

		array[index] = value;
		return index;
	}

	public boolean hasElement(int index) {
		return index >= 0 && index < maxIndex;
	}

	public long get(int index) {
		if (!hasElement(index)) {
			throw new ArrayIndexOutOfBoundsException(index);
		}

		return array[index];
	}

	public void set(int index, long value) {
		if (!hasElement(index)) {
			throw new ArrayIndexOutOfBoundsException(index);
		}

		array[index] = value;
	}

	public int size() {
		return maxIndex;
	}

	public void clear() {
		Arrays.fill(array, 0, maxIndex, 0L);
		maxIndex = 0;
	}

	private void grow() {
		long[] oldArray = array;
		final int newSize = newSize(oldArray.length);
		array = new long[newSize];
		System.arraycopy(oldArray, 0, array, 0, oldArray.length);
	}
}
