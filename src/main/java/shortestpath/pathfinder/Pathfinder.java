package shortestpath.pathfinder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import shortestpath.Transport;

public class Pathfinder implements Runnable {
    @Getter
    private final WorldPoint start;
    @Getter
    private final WorldPoint target;
    private final PathfinderConfig config;

    private final Deque<Node> boundary = new LinkedList<>();
    private final Set<WorldPoint> visited = new HashSet<>();
    private final Queue<Node> pending = new PriorityQueue<>();

    private Node lastNode;

    @Getter
    private boolean done = false;

    private int totalNodes = 0;

    public Pathfinder(PathfinderConfig config, WorldPoint start, WorldPoint target) {
        this.config = config;
        this.start = start;
        this.target = target;
        this.config.refresh();

        new Thread(this).start();
    }

    private void addNeighbors(Node node) {
        for (Node neighbor : config.getMap().getNeighbors(node, config)) {
            ++totalNodes;
            if (config.avoidWilderness(node.position, neighbor.position, target)) {
                continue;
            }
            if (visited.add(neighbor.position)) {
                if (neighbor instanceof TransportNode) {
                    pending.add(neighbor);
                } else {
                    boundary.addLast(neighbor);
                }
            }
        }
    }

    public List<WorldPoint> getPath() {
        return lastNode.getPathPoints();
    }

    @Override
    public void run() {
        lastNode = new Node(start, null);
        boundary.addFirst(lastNode);
        totalNodes = 1;

        int bestDistance = Integer.MAX_VALUE;
        long bestHeuristic = Integer.MAX_VALUE;
        Instant cutoffTime = Instant.now().plus(config.getCalculationCutoff());

        Instant startTime = Instant.now();
        while (!boundary.isEmpty() || !pending.isEmpty()) {
            Node node = boundary.peekFirst();
            Node p = pending.peek();

            if (p != null && (node == null || p.cost < node.cost)) {
                boundary.addFirst(p);
                pending.poll();
            }

            node = boundary.removeFirst();
            if (config.getGp() < node.gp) {
                continue; // TODO: generalize heuristic check?
            }

            if (node.position.equals(target) || !config.isNear(start)) {
                lastNode = node;
                break;
            }

            int distance = Node.distanceBetween(node.position, target);
            long heuristic = distance + Node.distanceBetween(node.position, target, 2);
            if (heuristic < bestHeuristic || (heuristic <= bestHeuristic && distance < bestDistance)) {
                lastNode = node;
                bestDistance = distance;
                bestHeuristic = heuristic;
                cutoffTime = Instant.now().plus(config.getCalculationCutoff());
            }

            if (Instant.now().isAfter(cutoffTime)) {
                break;
            }

            addNeighbors(node);
        }
        Instant endTime = Instant.now();
        System.out.println("Time taken: " + ((endTime.toEpochMilli() - startTime.toEpochMilli()) / 1000.0) + "; Nodes: " + totalNodes);
        List<Node> nodes = lastNode.getPathNodes();
        int step = 0;
        for (Node n : nodes) {
            if (n instanceof TransportNode) {
                System.out.println((++step) + ": " + ((TransportNode)n).getTransport().getDescription());
            }
        }
        done = true;
        boundary.clear();
        visited.clear();
        pending.clear();
    }
}
