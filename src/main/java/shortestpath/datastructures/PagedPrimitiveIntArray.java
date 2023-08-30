package shortestpath.datastructures;

import java.util.Arrays;

public class PagedPrimitiveIntArray {
	// How many bytes per page; 4096 bytes is an arbitrary compromise between memory usage and new allocations
	// Must be a power of 2
	private static final int PAGE_SIZE_BYTES = 4096;

	// Number of ints that fit in a page; Must be a power of 2
	private static final int ELEMENT_COUNT = PAGE_SIZE_BYTES / Integer.BYTES;
	private static final int ELEMENT_MODULO = ELEMENT_COUNT - 1;

	// Number of bits needed to store the index of elements in each page
	private static final int PAGE_INDEX_BITS = Integer.bitCount(Integer.highestOneBit(ELEMENT_COUNT) - 1);

	private int[][] pages;
	private int maxPage = 0;
	private int maxIndex = 0;

	public PagedPrimitiveIntArray(int initialSize) {
		final int pageCount = PrimitiveHelpers.getNextPow2((initialSize - 1) / ELEMENT_COUNT);
		pages = new int[pageCount][];
		pages[0] = new int[ELEMENT_COUNT];
	}

	public boolean hasElement(int index) {
		return index >= 0 && index < maxIndex;
	}

	public int get(int index) {
		if (!hasElement(index)) {
			throw new ArrayIndexOutOfBoundsException(index);
		}

		return pages[index >> PAGE_INDEX_BITS][index & ELEMENT_MODULO];
	}

	public void set(int index, int value) {
		if (index < 0 || index >= maxIndex) {
			throw new ArrayIndexOutOfBoundsException(index);
		}
		pages[index >> PAGE_INDEX_BITS][index & ELEMENT_MODULO] = value;
	}

	public int add(int value) {
		final int index = maxIndex++;
		final int pageIndex = index >> PAGE_INDEX_BITS;
		if (pageIndex > maxPage || pages[pageIndex] == null) {
			addPage();
		}

		pages[pageIndex][index & ELEMENT_MODULO] = value;
		return index;
	}

	public int size() {
		return maxIndex;
	}

	private void addPage() {
		if (++maxPage == pages.length) {
			growPages();
		}

		pages[maxPage] = new int[ELEMENT_COUNT];
	}

	private void growPages() {
		final int newPageCount = PrimitiveHelpers.getNextPow2(pages.length);
		if (newPageCount == pages.length) {
			throw new IllegalStateException("Too many pages");
		}

		int[][] oldPages = pages;
		pages = new int[newPageCount][];
		System.arraycopy(oldPages, 0, pages, 0, oldPages.length);
	}

	public void clear() {
		for (int p = 0; p < pages.length; ++p) {
			final int[] page = pages[p];
			if (page == null) break;
			Arrays.fill(page, 0);
		}

		maxIndex = 0;
	}
}
