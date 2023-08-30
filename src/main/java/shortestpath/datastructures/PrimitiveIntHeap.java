package shortestpath.datastructures;

import java.util.Comparator;

// A binary tree implementation of a MinHeap (aka PriorityQueue)
// This can be turned into a MaxHeap by reversing the order of the comparator
public class PrimitiveIntHeap {
	private int[] heap;
	private int maxIndex = 0;
	private PrimitiveIntComparator comparator;

	public interface PrimitiveIntComparator {
		int compare(int left, int right);
	}

	public PrimitiveIntHeap(int initialSize) {
		initialSize = Math.max(1, initialSize);
		heap = new int[initialSize];
		comparator = Integer::compare;
	}

	public PrimitiveIntHeap(int initialSize, PrimitiveIntComparator comparator) {
		initialSize = Math.max(1, initialSize);
		heap = new int[initialSize];
		this.comparator = comparator;
	}

	public int size() {
		return maxIndex;
	}

	public boolean isEmpty() {
		return maxIndex == 0;
	}

	public void setComparator(PrimitiveIntComparator comparator) {
		this.comparator = comparator;
	}

	private static int getLeftIndex(int index) {
		return (index << 1) + 1;
	}

	private static int getRightIndex(int index) {
		return (index << 1) + 2;
	}

	private static int getParent(int index) {
		return (index - 1) >> 1;
	}

	private void siftUp(int index) {
		final int val = heap[index];
		while (index > 0) {
			int parentIndex = getParent(index);
			int parent = heap[parentIndex];
			if (comparator.compare(val, parent) >= 0)
				break;
			heap[index] = parent;
			index = parentIndex;
		}
		heap[index] = val;
	}

	private void percolateDown(int index) {
		final int val = heap[index];
		final int lastParent = maxIndex >>> 1;
		while (index < lastParent) {
			int left = getLeftIndex(index);
			final int right = left + 1;
			int child = heap[left];
			if (right < maxIndex && comparator.compare(child, heap[right]) > 0) {
				child = heap[left = right];
			}

			if (comparator.compare(val, child) <= 0) {
				break;
			}

			heap[index] = child;
			index = left;
		}
		heap[index] = val;
	}

	public void add(int val) {
		if (maxIndex == heap.length) {
			growHeap();
		}

		heap[maxIndex] = val;
		siftUp(maxIndex++);
	}

	public int peek() {
		return heap[0];
	}

	public int poll() {
		if (maxIndex == 0) {
			return -1;
		}

		int retVal = heap[0];
		heap[0] = heap[maxIndex - 1];
		percolateDown(0);
		heap[--maxIndex] = -1;

		return retVal;
	}

	private void growHeap() {
		final int oldSize = heap.length;
		final int newSize = oldSize < 1024 ? oldSize * 2 : oldSize + (oldSize >> 1);

		final int[] oldHeap = heap;
		heap = new int[newSize];
		System.arraycopy(oldHeap, 0, heap, 0, oldSize);
	}

	public void clear() {
		maxIndex = 0;
	}
}
