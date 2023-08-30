package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import shortestpath.PathfinderDebugMode;
import shortestpath.datastructures.PagedPrimitiveIntArray;
import shortestpath.datastructures.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.datastructures.PrimitiveIntHeap;
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
    private CollisionMap map;
    private PrimitiveIntHeap pending;
    private PrimitiveIntQueue boundary;
    private NodeTree nodes;
    private VisitedTiles visited;

    // Debug types
    private final PathfinderDebugMode debugMode;
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

        pending = resources.getPendingQueueInstance();
        pending.setComparator((o1, o2) -> {
            final long node1 = nodes.getNode(o1);
            final long node2 = nodes.getNode(o2);
            return Integer.compare(NodeTree.unpackNodeCost(node1), NodeTree.unpackNodeCost(node2));
        });

        targetPacked = WorldPointUtil.packWorldPoint(target);
        targetInWilderness = PathfinderConfig.isInWilderness(target);

        debugMode = config.getDebugPathfindingMode();
        switch (debugMode) {
            case TREE:
                maxStep.set(1);
                edges = new ArrayList<>(128);
                break;
            case DEAD_ENDS:
                nodes.enableDebug();
                maxStep.set(1);
                edgesMap = new PrimitiveIntHashMap<>(128);
                break;
        }

        bestLastNodeIndex = nodes.setRootNode(WorldPointUtil.packWorldPoint(start));
        boundary.push(bestLastNodeIndex);
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
        int lastNodeIndex = bestLastNodeIndex; // For thread safety, read bestLastNode once
        if (lastNodeIndex < 0) {
            return path;
        }

        if (pathNeedsUpdate) {
            List<WorldPoint> pathTemp = new LinkedList<>();
            int nodeIndex = lastNodeIndex;
            long nodeData = nodes.getNode(nodeIndex);
            int nodeParentIndex = NodeTree.unpackNodeParent(nodeData);

            do {
                WorldPoint position = NodeTree.getNodeWorldPoint(nodeData);
                pathTemp.add(0, position);

                nodeIndex = nodeParentIndex;
                nodeData = nodes.getNode(nodeIndex);
                nodeParentIndex = NodeTree.unpackNodeParent(nodeData);
            } while (nodeIndex != nodeParentIndex);

            pathTemp.add(0, NodeTree.getNodeWorldPoint(nodeData));
            path = new ArrayList<>(pathTemp);
            pathNeedsUpdate = false;
        }

        return path;
    }

    private void markDeadEnds(int nodeIndex) {
        if (edgesMap == null || !nodes.hasNode(nodeIndex)) return;

        long nodeData = nodes.getNode(nodeIndex);
        int parentIndex = NodeTree.unpackNodeParent(nodeData);

        int nodeDebug = nodes.getNodeDebug(nodeIndex);
        int children = NodeTree.unpackDebugChildren(nodeDebug);
        int deadEnds = NodeTree.unpackDebugDeadEnds(nodeDebug);

        while (nodeIndex != parentIndex && children == deadEnds) {
            int nodePos = NodeTree.unpackNodePosition(nodeData);

            nodeIndex = parentIndex;
            nodeData = nodes.getNode(nodeIndex);
            parentIndex = NodeTree.unpackNodeParent(nodeData);

            edgesMap.put(nodePos, new WorldPointPair(nodePos, NodeTree.unpackNodePosition(nodeData), 0xFF0000FF));
            nodes.incrementDeadEnds(nodeIndex);
            nodeDebug = nodes.getNodeDebug(nodeIndex);
            children = NodeTree.unpackDebugChildren(nodeDebug);
            deadEnds = NodeTree.unpackDebugDeadEnds(nodeDebug);
        }
    }

    private void addDebugEdge(long start, long end) {
        WorldPointPair edge;
        switch (debugMode) {
            default:
            case OFF:
                break;
            case TREE:
                final int endCost = NodeTree.unpackNodeCost(end);
                edge = new WorldPointPair(NodeTree.unpackNodePosition(start), NodeTree.unpackNodePosition(end), endCost);
                edges.add(edge);
                break;
            case DEAD_ENDS:
                final int endPos = NodeTree.unpackNodePosition(end);
                edge = new WorldPointPair(NodeTree.unpackNodePosition(start), endPos, 0xFFFF0000);
                edgesMap.put(endPos, edge);
                break;
        }
    }

    private int addNeighbors(int nodeIndex) {
        PrimitiveIntQueue neighbors = map.getNeighbors(nodeIndex, visited, nodes, config);
        final long nodeData = nodes.getNode(nodeIndex);
        final int curPos = NodeTree.unpackNodePosition(nodeData);
        for (int i = 0; i < neighbors.size(); ++i) {
            int neighbor = neighbors.get(i);
            long neighborNode = nodes.getNode(neighbor);
            final int neighborPos = NodeTree.unpackNodePosition(neighborNode);
            addDebugEdge(nodeData, neighborNode);

            if (neighborPos == targetPacked) {
                return neighbor;
            }

            if (config.isAvoidWilderness() && config.avoidWilderness(curPos, neighborPos, targetInWilderness)) {
                continue;
            }

            visited.set(neighborPos);
            if (NodeTree.unpackNodeTransport(neighborNode)) {
                pending.add(neighbor);
                ++stats.transportsChecked;
            } else {
                boundary.push(neighbor);
                ++stats.nodesChecked;
            }
        }

        if (neighbors.isEmpty()) {
            markDeadEnds(nodeIndex);
        }

        return -1;
    }

    private int bestDistance = Integer.MAX_VALUE;
    private long bestHeuristic = Integer.MAX_VALUE;
    private void updateBestNode(int nodeIndex) {
        markDeadEnds(bestLastNodeIndex);
        bestLastNodeIndex = nodeIndex;
        pathNeedsUpdate = true;
    }

    @Override
    public void run() {
        try {
            active = true;
            if (done) return;
            stats.start();

            long cutoffDurationMillis = config.getCalculationCutoffMillis();
            long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;

            boolean debugBreak = false;
            final int nextStep = maxStep.get();

            while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty())) {
                final int pendingIndex = pending.isEmpty() ? -1 : pending.peek();
                final long pendingNode = nodes.getNode(pendingIndex);
                final int pendingNodeCost = NodeTree.unpackNodeCost(pendingNode);

                int nodeIndex = boundary.peekFirst();
                long node = nodes.getNode(nodeIndex);
                int nodeCost = NodeTree.unpackNodeCost(node);

                if (node == NodeTree.INVALID_NODE || pendingNodeCost < nodeCost) {
                    nodeIndex = pendingIndex;
                    node = pendingNode;
                    nodeCost = pendingNodeCost;
                    pending.poll();
                } else {
                    boundary.pop();
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

                if (nextStep < nodeCost) {
                    debugBreak = true;
                    break;
                }
            }

            if (!debugBreak) {
                done = !cancelled;
                getPath(); // Refresh the path before clearing references
                clearReferences();
                stats.end(); // Include cleanup in stats to get the total cost of pathfinding
            }

            active = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearReferences() {
        map = null;
        pending = null;
        boundary = null;
        nodes = null;
        visited = null;
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
