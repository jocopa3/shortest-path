package shortestpath.pathfinder;

import shortestpath.datastructures.PrimitiveIntQueue;

import java.lang.ref.SoftReference;
import java.util.function.Supplier;

// Stores per-thread instances of various data structures needed for pathfinding
// This avoids the need to reallocate these resources while keeping the pathfinder thread-safe
// Additionally, using SoftReferences allows the GC to free these resources if it needs to
public class PathfinderResources {
	private final SplitFlagMap mapData;
	private final ThreadLocalSoftReference<NodeTree> nodeTree;
	private final ThreadLocalSoftReference<CollisionMap> collisionMap;
	private final ThreadLocalSoftReference<VisitedTiles> visitedTiles;
	private final ThreadLocalSoftReference<PrimitiveIntQueue> boundary;

	public PathfinderResources(SplitFlagMap mapData) {
		this.mapData = mapData;
		nodeTree = ThreadLocalSoftReference.withInitialSoftReference(() -> new NodeTree());
		collisionMap = ThreadLocalSoftReference.withInitialSoftReference(() -> new CollisionMap(this.mapData));
		visitedTiles = ThreadLocalSoftReference.withInitialSoftReference(() -> new VisitedTiles(getCollisionMapInstance()));
		boundary = ThreadLocalSoftReference.withInitialSoftReference(() -> new PrimitiveIntQueue(4096));
	}

	public NodeTree getNodeTreeInstance() {
		NodeTree instance = nodeTree.getReference();
		instance.clear();
		return instance;
	}

	public CollisionMap getCollisionMapInstance() {
		return collisionMap.getReference();
	}

	public VisitedTiles getVisitedTilesInstance() {
		VisitedTiles instance = visitedTiles.getReference();
		instance.clear();
		return instance;
	}

	public PrimitiveIntQueue getBoundaryQueueInstance() {
		PrimitiveIntQueue instance = boundary.getReference();
		instance.clear();
		return instance;
	}

	private static class ThreadLocalSoftReference<T> {
		private final ThreadLocal<SoftReference<T>> threadLocal;

		private ThreadLocalSoftReference(ThreadLocal<SoftReference<T>> threadLocal) {
			this.threadLocal = threadLocal;
		}

		public T getReference() {
			return threadLocal.get().get();
		}

		public static <T> ThreadLocalSoftReference<T> withInitialSoftReference(Supplier<? extends T> supplier) {
			return new ThreadLocalSoftReference<>(ThreadLocal.withInitial(() -> new SoftReference<>(supplier.get())));
		}
	}
}
