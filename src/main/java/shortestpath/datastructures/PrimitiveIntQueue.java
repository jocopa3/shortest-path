package shortestpath.datastructures;

import java.util.NoSuchElementException;

public class PrimitiveIntQueue {
	private int[] buffer;
	private int size = 0;
	private int head = 0;
	private int tail = 0;

	public PrimitiveIntQueue(int initialSize) {
		initialSize = Math.max(1, initialSize);
		buffer = new int[initialSize];
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public int get(int index) {
		if (size == 0 || index >= size) {
			throw new ArrayIndexOutOfBoundsException(index);
		}

		return buffer[inc(head, index)];
	}

	private int inc(int index, int amount) {
		return (index + amount) % buffer.length;
	}

	private int dec(int index, int amount) {
		return (index - amount + buffer.length) % buffer.length;
	}

	public void push(int value) {
		if (size == buffer.length) {
			growBuffer();
		}

		buffer[tail] = value;
		tail = inc(tail, 1);
		size++;
	}

	public void pushFirst(int value) {
		if (size == buffer.length) {
			growBuffer();
		}

		head = dec(head, 1);
		buffer[head] = value;
		size++;
	}

	public int pop() {
		if (isEmpty()) {
			throw new NoSuchElementException("Queue is empty");
		}

		int value = buffer[head];
		head = inc(head, 1);
		size--;

		return value;
	}

	public int peekFirst() {
		if (isEmpty()) {
			return -1; // This is a hack; instead of throwing an exception, throw a value that in this context is "invalid"
		}

		return buffer[head];
	}

	private void growBuffer() {
		final int oldSize = buffer.length;
		final int newSize = oldSize < 1024 ? oldSize * 2 : oldSize + (oldSize >> 1);

		final int[] oldBuffer = buffer;
		buffer = new int[newSize];
		if (head == 0) {
			System.arraycopy(oldBuffer, head, buffer, 0, size);
		} else {
			System.arraycopy(oldBuffer, head, buffer, 0, oldSize - head);
			System.arraycopy(oldBuffer, 0, buffer, oldSize - head, tail);
		}

		head = 0;
		tail = size;
	}

	public void clear() {
		// No need to clean-up values in the buffer
		head = 0;
		tail = 0;
		size = 0;
	}

	public static void main(String[] args) {
		PrimitiveIntQueue queue = new PrimitiveIntQueue(2);
		queue.push(0);
		queue.push(1);
		queue.push(2);
		queue.push(3);
		queue.pop();
		queue.push(4);
		queue.push(5);
		queue.push(6);
		queue.push(7);
		queue.pop();
		queue.push(8);
		queue.push(9);
		queue.push(10);
	}
}
