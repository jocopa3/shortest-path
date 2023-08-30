package shortestpath.pathfinder;

import shortestpath.datastructures.PagedPrimitiveIntArray;
import shortestpath.datastructures.PrimitiveIntHeap;
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
	private final ThreadLocalSoftReference<PrimitiveIntHeap> pending;

	public PathfinderResources(SplitFlagMap mapData) {
		this.mapData = mapData;
		nodeTree = ThreadLocalSoftReference.withInitial(() -> new NodeTree());
		collisionMap = ThreadLocalSoftReference.withInitial(() -> new CollisionMap(this.mapData));
		visitedTiles = ThreadLocalSoftReference.withInitial(() -> new VisitedTiles(getCollisionMapInstance()));
		boundary = ThreadLocalSoftReference.withInitial(() -> new PrimitiveIntQueue(4096));
		pending = ThreadLocalSoftReference.withInitial(() -> new PrimitiveIntHeap(256));
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

	public PrimitiveIntHeap getPendingQueueInstance() {
		PrimitiveIntHeap instance = pending.getReference();
		instance.clear();
		return instance;
	}

	private static class ThreadLocalSoftReference<T> {
		private final ThreadLocal<SoftReference<T>> threadLocal;

		private ThreadLocalSoftReference(ThreadLocal<SoftReference<T>> threadLocal) {
			this.threadLocal = threadLocal;
		}

		public T getReference() {
			SoftReference<T> ref = threadLocal.get();
			if (ref == null) {
				// Remove the stale reference to allow the object to be re-initialized
				threadLocal.remove();
			}
			return threadLocal.get().get();
		}

		public static <T> ThreadLocalSoftReference<T> withInitial(Supplier<? extends T> supplier) {
			return new ThreadLocalSoftReference<>(ThreadLocal.withInitial(() -> new SoftReference<>(supplier.get())));
		}
	}
}
