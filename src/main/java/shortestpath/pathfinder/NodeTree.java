package shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import shortestpath.WorldPointUtil;
import shortestpath.datastructures.PagedPrimitiveIntArray;
import shortestpath.datastructures.PagedPrimitiveLongArray;

public class NodeTree {
	public static final long INVALID_NODE = -1L;
	public static final int INVALID_DEBUG_NODE = -1;

	private static final long COST_BIT_COUNT = 12L;
	private static final long COST_BIT_MASK = (1L << COST_BIT_COUNT) - 1L;

	private static final long POSITION_BIT_COUNT = 30L;
	private static final long POSITION_BIT_MASK = (1L << POSITION_BIT_COUNT) - 1L;
	private static final long POSITION_BIT_POS = COST_BIT_COUNT;

	private static final long PARENT_BIT_COUNT = 21L;
	private static final long PARENT_BIT_MASK = (1L << PARENT_BIT_COUNT) - 1L;
	private static final long PARENT_BIT_POS = COST_BIT_COUNT + POSITION_BIT_COUNT;
	private static final long TRANSPORT_BIT_POS = COST_BIT_COUNT + POSITION_BIT_COUNT + PARENT_BIT_COUNT;

	private static final int CHILDREN_BIT_COUNT = 16;
	private static final int CHILDREN_BIT_MASK = (1 << CHILDREN_BIT_COUNT) - 1;

	private static final int DEAD_ENDS_BIT_COUNT = 16;
	private static final int DEAD_ENDS_BIT_MASK = (1 << DEAD_ENDS_BIT_COUNT) - 1;
	private static final int DEAD_ENDS_BIT_POS = CHILDREN_BIT_COUNT;

	private static final int DEFAULT_SIZE = (1 << 17) - 1; // 131071
	private PagedPrimitiveLongArray nodeArray;
	private PagedPrimitiveIntArray debugArray;

	public NodeTree() {
		nodeArray = new PagedPrimitiveLongArray(DEFAULT_SIZE);
	}

	void enableDebug() {
		debugArray = new PagedPrimitiveIntArray(DEFAULT_SIZE);
	}

	void disableDebug() {
		debugArray = null;
	}

	private static long packNode(int parent, int packedPosition, int cost, boolean transport) {
		return	(((long)cost & COST_BIT_MASK))
			|	(((long)packedPosition & POSITION_BIT_MASK) << POSITION_BIT_POS)
			|	(((long)parent & PARENT_BIT_MASK) << PARENT_BIT_POS)
			|	((transport ? 1L : 0L) << TRANSPORT_BIT_POS);
	}

	public static int unpackNodeCost(long node) {
		return (int)(node & COST_BIT_MASK);
	}

	public static int unpackNodePosition(long node) {
		return (int)((node >>> POSITION_BIT_POS) & POSITION_BIT_MASK);
	}

	public static WorldPoint getNodeWorldPoint(long node) {
		return WorldPointUtil.unpackWorldPoint(unpackNodePosition(node));
	}

	public static int unpackNodeParent(long node) {
		return (int)((node >>> PARENT_BIT_POS) & PARENT_BIT_MASK);
	}

	public static boolean unpackNodeTransport(long node) {
		return node < 0; // Transport bit is stored as the sign bit, no bit manipulation needed
	}

	static int packDebug(int children, int deadEnds) {
		return (children & CHILDREN_BIT_MASK) | ((deadEnds & DEAD_ENDS_BIT_MASK) << DEAD_ENDS_BIT_POS);
	}

	static int unpackDebugChildren(int nodeDebug) {
		return nodeDebug & CHILDREN_BIT_MASK;
	}

	static int unpackDebugDeadEnds(int nodeDebug) {
		return (nodeDebug >>> DEAD_ENDS_BIT_POS) & DEAD_ENDS_BIT_MASK;
	}

	static int incrementDebugChildren(int nodeDebug) {
		return nodeDebug + 1; // TODO: Does overflow matter? Can a node have more than 65536 children?
	}

	static int incrementDebugDeadEnds(int nodeDebug) {
		return nodeDebug + (1 << DEAD_ENDS_BIT_POS); // TODO: Does overflow matter? Can a node have more than 65536 children?
	}

	public int setRootNode(int packedPosition) {
		if ((packedPosition & POSITION_BIT_MASK) != packedPosition) {
			throw new IllegalArgumentException("Position is too large: " + packedPosition + "; max value: " + POSITION_BIT_MASK);
		}

		final long rootNode = packNode(0, packedPosition, 0, false);
		if (nodeArray.hasElement(0)) {
			nodeArray.set(0, rootNode);
			if (debugArray != null) {
				debugArray.set(0, 0);
			}
		} else {
			nodeArray.add(rootNode);
			if (debugArray != null) {
				debugArray.add(0);
			}
		}

		return 0;
	}

	public int addNode(int parentIndex, int packedPosition, int wait, boolean transport) {
		final int cost = cost(parentIndex, packedPosition, wait);
		if ((cost & COST_BIT_MASK) != cost
				|| (parentIndex & PARENT_BIT_MASK) != parentIndex
				|| (packedPosition & POSITION_BIT_MASK) != packedPosition
		) {
			throw new IllegalArgumentException("A parameter is too large"); // TODO: Better message?
		}

		if (debugArray != null) {
			final int nodeDebug = debugArray.get(parentIndex);
			debugArray.set(parentIndex, incrementDebugChildren(nodeDebug));
			debugArray.add(0);
		}

		return nodeArray.add(packNode(parentIndex, packedPosition, cost, transport));
	}

	public boolean hasNode(int index) {
		return nodeArray.hasElement(index);
	}

	public long getNode(int index) {
		return nodeArray.hasElement(index) ? nodeArray.get(index) : INVALID_NODE;
	}

	public int getNodeDebug(int index) {
		return debugArray.hasElement(index) ? debugArray.get(index) : INVALID_DEBUG_NODE;
	}

	public void incrementDeadEnds(int index) {
		debugArray.set(index, incrementDebugDeadEnds(debugArray.get(index)));
	}

	public long getParentNode(long node) {
		final int parentIndex = unpackNodeParent(node);
		return nodeArray.get(parentIndex);
	}

	public void clear() {
		nodeArray.clear();
		debugArray = null;
	}

	private int cost(int parentIndex, int packedPosition, int wait) {
		final long parentNode = nodeArray.get(parentIndex);
		final int parentPackedPosition = unpackNodePosition(parentNode);
		final int parentCost = unpackNodeCost(parentNode);
		final int distance = WorldPointUtil.distanceBetween(parentPackedPosition, packedPosition);

		final int parentPlane = WorldPointUtil.unpackWorldPlane(parentPackedPosition);
		final int currentPlane = WorldPointUtil.unpackWorldPlane(packedPosition);
		final boolean isTransport = distance > 1 || parentPlane != currentPlane;
		if (isTransport) {
			return wait + parentCost;
		}

		return parentCost + distance;
	}
}
