package shortestpath.pathfinder;

import java.util.*;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import shortestpath.Transport;
import shortestpath.Util;

public class Pathfinder implements Runnable {
    @Getter
    private final WorldPoint start;
    @Getter
    private final WorldPoint target;
    private final int targetPackedPosition;
    private final boolean targetInWilderness;
    private final Map<Integer, List<Transport>> transportsPacked;
    private final PathfinderConfig config;

    // Capacities should be enough to store all nodes without requiring the queue to grow
    // They were found by checking the max queue size in a worst-case scenario
    private final Deque<Node> boundary = new ArrayDeque<>(4096);
    private final Queue<Node> pending = new PriorityQueue<>(256);
    private final VisitedTileSet visited = new VisitedTileSet();

    // Track the last node from which the path can be derived
    private Node lastNode;

    @Getter
    private boolean done = false;

    public Pathfinder(PathfinderConfig config, WorldPoint start, WorldPoint target) {
        this.config = config;
        this.config.refresh();
        this.start = start;
        this.target = target;
        this.targetPackedPosition = Util.packWorldPoint(target);
        this.targetInWilderness = PathfinderConfig.isInWilderness(target);

        Map<WorldPoint, List<Transport>> transports = config.getTransports();
        transportsPacked = new HashMap<>(transports.size());

        for (Map.Entry<WorldPoint, List<Transport>> entry : transports.entrySet()) {
            transportsPacked.put(Util.packWorldPoint(entry.getKey()), entry.getValue());
        }

        new Thread(this).start();
    }

    private void addNeighbors(Node node) {
        // Transports are pre-filtered by PathfinderConfig.refreshTransportData
        // Thus any transports in the list are guaranteed to be valid per the user's settings
        List<Node> neighbors = config.getMap().getNeighbors(node, transportsPacked);
        for (int i = 0; i < neighbors.size(); ++i) {
            Node neighbor = neighbors.get(i);
            if (!visited.get(node.packedPosition) && config.avoidWilderness(node.getWorldPoint(), neighbor.getWorldPoint(), targetInWilderness)) {
                continue;
            }

            if (visited.set(neighbor.packedPosition)) {
                if (neighbor instanceof TransportNode) {
                    pending.add(neighbor);
                } else {
                    boundary.addLast(neighbor);
                }
            }
        }
    }

    public List<WorldPoint> getPath() {
        return lastNode.getPath();
    }

    @Override
    public void run() {
        boundary.addFirst(new Node(start, null));

        int bestDistance = Integer.MAX_VALUE;
        long bestHeuristic = Integer.MAX_VALUE;
        long cutoffTime = System.currentTimeMillis();
        final long cutoffDuration = config.getCalculationCutoff().toMillis();

        while (!boundary.isEmpty() || !pending.isEmpty()) {
            Node node = boundary.peekFirst();
            Node p = pending.peek();

            if (p != null && (node == null || p.cost < node.cost)) {
                boundary.addFirst(p);
                pending.poll();
            }

            node = boundary.removeFirst();

            if (node.packedPosition == targetPackedPosition || !config.isNear(start)) {
                lastNode = node;
                break;
            }

            int distance = Node.distanceBetween(node.packedPosition, targetPackedPosition);
            long heuristic = distance + Node.distanceBetween(node.packedPosition, targetPackedPosition, 2);
            if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                lastNode = node;
                bestDistance = distance;
                bestHeuristic = heuristic;
                cutoffTime = System.currentTimeMillis() + cutoffDuration;
            }

            if (System.currentTimeMillis() > cutoffTime) {
                break;
            }

            addNeighbors(node);
        }

        done = true;
        boundary.clear();
        visited.clear();
        pending.clear();
    }
}
