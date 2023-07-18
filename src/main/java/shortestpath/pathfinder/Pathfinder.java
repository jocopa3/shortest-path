package shortestpath.pathfinder;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import shortestpath.WorldPointUtil;

public class Pathfinder implements Runnable {
    private AtomicBoolean done = new AtomicBoolean();
    private AtomicBoolean cancelled = new AtomicBoolean();

    @Getter
    private final WorldPoint start;
    @Getter
    private final WorldPoint target;

    private final int targetPacked;

    private final PathfinderConfig config;
    private final CollisionMap map;
    private final boolean targetInWilderness;

    // Capacities should be enough to store all nodes without requiring the queue to grow
    // They were found by checking the max queue size
    private final Deque<Node> boundaryStart = new ArrayDeque<>(4096);
    private final Deque<Node> boundaryTarget = new ArrayDeque<>(4096);
    private final Queue<Node> pending = new PriorityQueue<>(256);
    private final VisitedTiles visitedStart = new VisitedTiles();
    private final VisitedTiles visitedTarget = new VisitedTiles();

    @SuppressWarnings("unchecked") // Casting EMPTY_LIST is safe here
    private List<WorldPoint> path = (List<WorldPoint>)Collections.EMPTY_LIST;
    private boolean pathNeedsUpdate = false;
    private Node rootNodeS;
    private Node bestLastNodeS;
    private Node rootNodeT;

    public Pathfinder(PathfinderConfig config, WorldPoint start, WorldPoint target) {
        this.config = config;
        this.map = config.getMap();
        this.start = start;
        this.target = target;
        targetPacked = WorldPointUtil.packWorldPoint(target);
        targetInWilderness = PathfinderConfig.isInWilderness(target);
        
        new Thread(this).start();
    }

    public boolean isDone() {
        return done.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public List<WorldPoint> getPath() {
        Node lastNode = bestLastNodeS; // For thread safety, read bestLastNode once
        if (lastNode == null) {
            return path;
        }

        if (pathNeedsUpdate) {
            path = lastNode.getPath();
            pathNeedsUpdate = false;
        }

        return path;
    }

    private Node findLeaf(Deque<Node> fringeNodes, int leafPositionPacked) {
        while (!fringeNodes.isEmpty()) {
            Node leaf = fringeNodes.removeFirst();
            if (leaf.packedPosition == leafPositionPacked) {
                return leaf;
            }
        }
        return null;
    }

    private void addNeighbors(Node node, Deque<Node> boundary, VisitedTiles visitedTiles) {
        List<Node> nodes = map.getNeighbors(node, config, visitedTiles);
        for (int i = 0; i < nodes.size(); ++i) {
            Node neighbor = nodes.get(i);
            if ((config.isAvoidWilderness() && config.avoidWilderness(node.packedPosition, neighbor.packedPosition, targetInWilderness))) {
                continue;
            }
            visitedTiles.set(neighbor.packedPosition);
            if (neighbor instanceof TransportNode) {
                //pending.add(neighbor);
            } else {
                boundary.addLast(neighbor);
            }
        }
    }

    private void cleanup() {
        boundaryStart.clear();
        boundaryTarget.clear();
        visitedStart.clear();
        visitedTarget.clear();
        pending.clear();
    }

    @Override
    public void run() {
        Node.nodeCount.set(0);

        rootNodeS = new Node(start, null);
        boundaryStart.addFirst(rootNodeS);
        rootNodeT = new Node(target, null);
        boundaryTarget.addFirst(rootNodeT);
        Node meetingPoint = null;

        // Bidirectional search
        while (!cancelled.get() && ((!boundaryStart.isEmpty() && !boundaryTarget.isEmpty()) || !pending.isEmpty())) {
            Node nodeS = boundaryStart.peekFirst();
            Node nodeT = boundaryTarget.peekFirst();
            if (visitedStart.get(nodeT.packedPosition)) {
                bestLastNodeS = findLeaf(boundaryStart, nodeT.packedPosition);
                meetingPoint = bestLastNodeS;
                break;
            } else if (visitedTarget.get(nodeS.packedPosition)) {
                bestLastNodeS = nodeS;
                meetingPoint = bestLastNodeS;
                break;
            }
            boundaryStart.removeFirst();
            boundaryTarget.removeFirst();

            addNeighbors(nodeS, boundaryStart, visitedStart);
            addNeighbors(nodeT, boundaryTarget, visitedTarget);
        }

        if (meetingPoint == null) {
            done.set(!cancelled.get());
            cleanup();
            return;
        }

        // Unidirectional search from meeting point
        // TODO: Remove; the first search should reconstruct the path once found
        boundaryStart.addFirst(rootNodeS);
        while (!cancelled.get() && (!boundaryStart.isEmpty() || !pending.isEmpty())) {
            Node node = boundaryStart.removeFirst();
            if (node.packedPosition == targetPacked) {
                bestLastNodeS = node;
                pathNeedsUpdate = true;
                break;
            }

            addNeighbors(node, boundaryStart, visitedStart);
        }

        done.set(!cancelled.get());
        cleanup();
        System.out.println("Nodes: " + Node.nodeCount.get());
    }
}
