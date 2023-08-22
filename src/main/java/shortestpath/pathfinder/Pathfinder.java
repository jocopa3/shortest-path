package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import shortestpath.datastructures.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.datastructures.PrimitiveIntQueue;

public class Pathfinder implements Runnable {
    private PathfinderStats stats;
    private volatile boolean done = false;
    private volatile boolean cancelled = false;

    @Getter
    private final WorldPoint start;
    @Getter
    private final WorldPoint target;
    private final int targetPacked;
    private final boolean targetInWilderness;

    private final PathfinderConfig config;
    private final CollisionMap map;
    private final Queue<Integer> pending;
    private final PrimitiveIntQueue boundary;
    private final NodeTree nodes;
    private static VisitedTiles visited;

    // Debug types
    private AtomicInteger maxStep = new AtomicInteger(Integer.MAX_VALUE);
    @Getter
    private List<WorldPointPair> edges;
    @Getter
    private PrimitiveIntHashMap<WorldPointPair> edgesMap;

    @SuppressWarnings("unchecked") // Casting EMPTY_LIST is safe here
    private List<WorldPoint> path = (List<WorldPoint>)Collections.EMPTY_LIST;
    private boolean pathNeedsUpdate = false;
    private int bestLastNodeIndex;

    @Getter
    private volatile boolean active = false;

    public Pathfinder(PathfinderConfig config, PathfinderResources resources, WorldPoint start, WorldPoint target) {
        stats = new PathfinderStats();

        this.config = config;
        this.map = resources.getCollisionMapInstance();
        this.nodes = resources.getNodeTreeInstance();
        this.visited = resources.getVisitedTilesInstance();
        this.boundary = resources.getBoundaryQueueInstance();
        this.start = start;
        this.target = target;

        // Pending queue has to be recreated as its cost comparator references the local nodeTree instance
        // It's small enough an infrequently used that this reconstruction cost shouldn't matter
        pending = new PriorityQueue<>(256, (o1, o2) -> {
            long node1 = nodes.getNode(o1);
            long node2 = nodes.getNode(o2);
            return Comparator
                    .<Integer>naturalOrder()
                    .compare(NodeTree.unpackNodeCost(node1), NodeTree.unpackNodeCost(node2));
        });

        targetPacked = WorldPointUtil.packWorldPoint(target);
        targetInWilderness = PathfinderConfig.isInWilderness(target);

        switch (config.getDebugPathfindingMode()) {
            case TREE:
                maxStep.set(1);
                edges = new ArrayList<>(128);
                break;
            case DEAD_ENDS:
                maxStep.set(1);
                edgesMap = new PrimitiveIntHashMap<>(128);
                break;
        }
    }

    public void debugStep() {
        if (config.isDebuggingPathfinding()) {
            maxStep.incrementAndGet();
        }
    }

    public int getStep() {
        return maxStep.get();
    }

    public boolean isDone() {
        return done;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    public PathfinderStats getStats() {
        if (stats.started && stats.ended) {
            return stats;
        }

        // Don't give incomplete results
        return null;
    }

    public List<WorldPoint> getPath() {
        int lastNode = bestLastNodeIndex; // For thread safety, read bestLastNode once
        if (lastNode < 0) {
            return path;
        }

        if (pathNeedsUpdate) {
            List<WorldPoint> pathTemp = new LinkedList<>();
            int nodeIndex = lastNode;
            long nodeData = nodes.getNode(nodeIndex);
            int nodeParentIndex = NodeTree.unpackNodeParent(nodeData);

            while (nodeIndex != nodeParentIndex) {
                WorldPoint position = NodeTree.getNodeWorldPoint(nodeData);
                pathTemp.add(0, position);

                nodeIndex = nodeParentIndex;
                nodeData = nodes.getNode(nodeIndex);
                nodeParentIndex = NodeTree.unpackNodeParent(nodeData);
            }

            pathTemp.add(NodeTree.getNodeWorldPoint(nodeData));
            path = new ArrayList<>(pathTemp);
            pathNeedsUpdate = false;
        }

        return path;
    }

//    private void markDeadEnds(Node node) {
//        if (edgesMap == null) return;
//        while (node != null && node.previous != null && node.getChildren() == node.getDeadEnds()) {
//            edgesMap.put(node.packedPosition, new WorldPointPair(node.packedPosition, node.previous.packedPosition, 0xFF0000FF));
//            node = node.previous;
//            node.deadEnds++;
//        }
//    }

    private void addDebugEdge(long start, long end) {
        final int startPos = NodeTree.unpackNodePosition(start);
        final int endPos = NodeTree.unpackNodePosition(end);
        if (edges != null) {
            final int endCost = NodeTree.unpackNodeCost(end);
            WorldPointPair edge = new WorldPointPair(startPos, endPos, endCost);
            edges.add(edge);
        } else if (edgesMap != null) {
            WorldPointPair edge = new WorldPointPair(startPos, endPos, 0xFFFF0000);
            edgesMap.put(endPos, edge);
        }
    }

    private int addNeighbors(int nodeIndex) {
        PrimitiveIntQueue neighbors = map.getNeighbors(nodeIndex, visited, nodes, config);
        final long nodeData = nodes.getNode(nodeIndex);
        final int curPos = NodeTree.unpackNodePosition(nodeData);
        final int curCost = NodeTree.unpackNodeCost(nodeData);
        for (int i = 0; i < neighbors.size(); ++i) {
            int neighbor = neighbors.get(i);
            long neighborNode = nodes.getNode(neighbor);
            final int neighborPos = NodeTree.unpackNodePosition(neighborNode);
            final int neighborCost = NodeTree.unpackNodeCost(neighborNode);
            addDebugEdge(nodeData, neighborNode);

            if (neighborPos == targetPacked) {
                return neighbor;
            }

            if (config.isAvoidWilderness() && config.avoidWilderness(curPos, neighborPos, targetInWilderness)) {
                continue;
            }

            visited.set(neighborPos);
            if (neighborCost - curCost > 1) {
                pending.add(neighbor);
                ++stats.transportsChecked;
            } else {
                boundary.push(neighbor);
                ++stats.nodesChecked;
            }
        }

        //if (nodes.isEmpty()) {
        //    markDeadEnds(node);
        //}

        return -1;
    }

    private int bestDistance = Integer.MAX_VALUE;
    private long bestHeuristic = Integer.MAX_VALUE;

    private void updateBestNode(int nodeIndex) {
        //markDeadEnds(bestLastNode);
        bestLastNodeIndex = nodeIndex;
        pathNeedsUpdate = true;
    }

    @Override
    public void run() {
        active = true;
        stats.start();
        bestLastNodeIndex = nodes.setRootNode(WorldPointUtil.packWorldPoint(start));
        boundary.push(bestLastNodeIndex);

        long cutoffDurationMillis = config.getCalculationCutoffMillis();
        long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;

        boolean debugBreak = false;
        final int nextStep = maxStep.get();

        while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty())) {
            final int pendingIndex = pending.isEmpty() ? -1 : pending.peek();
            final long pendingNode = pendingIndex < 0 ? NodeTree.INVALID_NODE : nodes.getNode(pendingIndex);
            final int pendingNodeCost = NodeTree.unpackNodeCost(pendingNode);

            int nodeIndex = boundary.peekFirst();
            long node = nodeIndex < 0 ? NodeTree.INVALID_NODE : nodes.getNode(nodeIndex);
            int nodeCost = NodeTree.unpackNodeCost(node);
            if (node == NodeTree.INVALID_NODE || pendingNodeCost < nodeCost) {
                nodeIndex = pendingIndex;
                node = pendingNode;
                nodeCost = pendingNodeCost;
                pending.poll();
            } else {
                boundary.pop();
            }

            if (nextStep < nodeCost) {
                debugBreak = true;
                break;
            }

            final int nodePackedPos = NodeTree.unpackNodePosition(node);
            if (nodePackedPos == targetPacked) {
                updateBestNode(nodeIndex);
                break;
            }

            int distance = WorldPointUtil.distanceBetween(nodePackedPos, targetPacked);
            long heuristic = distance + WorldPointUtil.distanceBetween(nodePackedPos, targetPacked, 2);
            if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                updateBestNode(nodeIndex);
                bestDistance = distance;
                bestHeuristic = heuristic;
                cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
            }

            if (System.currentTimeMillis() > cutoffTimeMillis) {
                break;
            }

            // Check if target was found without processing the queue to find it
            if ((nodeIndex = addNeighbors(nodeIndex)) >= 0) {
                updateBestNode(nodeIndex);
                break;
            }
        }

        if (!debugBreak) {
            done = !cancelled;
            stats.end(); // Include cleanup in stats to get the total cost of pathfinding
        }

        active = false;
    }

    public static class PathfinderStats {
        @Getter
        private int nodesChecked = 0, transportsChecked = 0;
        private long startNanos, endNanos;
        private volatile boolean started = false, ended = false;

        public int getTotalNodesChecked() {
            return nodesChecked + transportsChecked;
        }

        public long getElapsedTimeNanos() {
            return endNanos - startNanos;
        }

        private void start() {
            if (started) {
                return;
            }

            started = true;
            nodesChecked = 0;
            transportsChecked = 0;
            startNanos = System.nanoTime();
        }

        private void end() {
            if (ended) {
                return;
            }

            endNanos = System.nanoTime();
            ended = true;
        }
    }
}
