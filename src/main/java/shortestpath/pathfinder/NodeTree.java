package shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import shortestpath.WorldPointUtil;
import shortestpath.datastructures.PrimitiveLongArray;

public class NodeTree {
	public static final long INVALID_NODE = -1L;

	private static final int DEFAULT_SIZE = (1 << 17) - 1; // 131071
	private PrimitiveLongArray nodeArray = new PrimitiveLongArray(DEFAULT_SIZE);

	private static long packNode(int parent, int packedPosition, int cost) {
		return (((long)cost & 0xFFFL)) | (((long)packedPosition & 0x3FFFFFFFL) << 12L) | (((long)parent & 0x3FFFFFL) << 42L);
	}

	public static int unpackNodeCost(long node) {
		return (int)(node & 0xFFFL);
	}

	public static int unpackNodePosition(long node) {
		return (int)((node >> 12L) & 0x3FFFFFFFL);
	}

	public static WorldPoint getNodeWorldPoint(long node) {
		return WorldPointUtil.unpackWorldPoint(unpackNodePosition(node));
	}

	public static int unpackNodeParent(long node) {
		return (int)((node >> 42L) & 0x3FFFFFL);
	}

	public int setRootNode(int packedPosition) {
		if ((packedPosition & 0x3FFFFFFF) != packedPosition) {
			throw new IllegalArgumentException("Position is too large: " + packedPosition + "; max value: " + 0x3FFFFFFF);
		}

		final long rootNode = packNode(0, packedPosition, 0);
		if (nodeArray.hasElement(0)) {
			nodeArray.set(0, rootNode);
		} else {
			nodeArray.add(rootNode);
		}

		return 0;
	}

	public int addNode(int parentIndex, int packedPosition, int wait) {
		final int cost = cost(parentIndex, packedPosition, wait);
		if ((cost & 0xFFF) != cost || (parentIndex & 0x3FFFFF) != parentIndex || (packedPosition & 0x3FFFFFFF) != packedPosition) {
			throw new IllegalArgumentException("A parameter is too large"); // TODO: Better message?
		}

		return nodeArray.add(packNode(parentIndex, packedPosition, cost));
	}

	public boolean hasNode(int index) {
		return nodeArray.hasElement(index);
	}

	public long getNode(int index) {
		return nodeArray.hasElement(index) ? nodeArray.get(index) : INVALID_NODE;
	}

	public long getParentNode(long node) {
		final int parentIndex = unpackNodeParent(node);
		return nodeArray.get(parentIndex);
	}

	public void clear() {
		nodeArray.clear();
	}

	private int cost(int parentIndex, int packedPosition, int wait) {
		int parentCost = 0;
		int distance = 0;

		if (nodeArray.hasElement(parentIndex)) {
			final long parentNode = nodeArray.get(parentIndex);
			final int parentPackedPosition = unpackNodePosition(parentNode);
			parentCost = unpackNodeCost(parentNode);
			distance = WorldPointUtil.distanceBetween(parentPackedPosition, packedPosition);

			final int parentPlane = WorldPointUtil.unpackWorldPlane(parentPackedPosition);
			final int currentPlane = WorldPointUtil.unpackWorldPlane(parentPackedPosition);
			boolean isTransport = distance > 1 || parentPlane != currentPlane;
			if (isTransport) {
				distance = wait;
			}
		}

		return parentCost + distance;
	}
}
