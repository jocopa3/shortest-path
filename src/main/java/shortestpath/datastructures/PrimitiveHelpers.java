package shortestpath.datastructures;

public class PrimitiveHelpers {
	public static int getNextPow2(int size) {
		if (size == 0) {
			return 1;
		}

		final int nextPow2 = -1 >>> Integer.numberOfLeadingZeros(size);
		if (nextPow2 >= (Integer.MAX_VALUE >>> 1)) {
			return (Integer.MAX_VALUE >>> 1) + 1;
		}
		return nextPow2 + 1;
	}
}
