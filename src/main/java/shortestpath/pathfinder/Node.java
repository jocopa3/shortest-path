package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import shortestpath.Util;

public class Node {
    // Position is packed to prevent unnecessary allocations
    // See Util.packWorldPosition
    public final int packedPosition;
    public final Node previous;
    public final int cost;

    public Node(int packedPosition, Node previous, int wait) {
        this.packedPosition = packedPosition;
        this.previous = previous;
        this.cost = cost(wait);
    }

    public Node(WorldPoint position, Node previous, int wait) {
        this(Util.packWorldPoint(position.getX(), position.getY(), position.getPlane()), previous, wait);
    }

    public Node(WorldPoint position, Node previous) {
        this(position, previous, 0);
    }

    public Node(int packedPosition, Node previous) {
        this(packedPosition, previous, 0);
    }

    public int getX() {
        return Util.unpackWorldPositionX(packedPosition);
    }

    public int getY() {
        return Util.unpackWorldPositionY(packedPosition);
    }

    public int getPlane() {
        return Util.unpackWorldPositionPlane(packedPosition);
    }

    public WorldPoint getWorldPoint() {
        return Util.unpackWorldPoint(packedPosition);
    }

    public List<WorldPoint> getPath() {
        List<WorldPoint> path = new LinkedList<>();
        Node node = this;

        while (node != null) {
            WorldPoint point = Util.unpackWorldPoint(node.packedPosition);
            path.add(0, point);
            node = node.previous;
        }

        return new ArrayList<>(path);
    }

    private int cost(int wait) {
        int previousCost = 0;
        int distance = 0;

        if (previous != null) {
            previousCost = previous.cost;
            distance = distanceBetween(previous.packedPosition, packedPosition);

            final int currentPlane = Util.unpackWorldPositionPlane(packedPosition);
            final int previousPlane = Util.unpackWorldPositionPlane(previous.packedPosition);
            boolean isTransport = distance > 1 || previousPlane != currentPlane;
            if (isTransport) {
                distance = wait;
            }
        }

        return previousCost + distance;
    }

    public static int distanceBetween(int previousPacked, int currentPacked, int diagonal) {
        final int currentX = Util.unpackWorldPositionX(currentPacked);
        final int currentY = Util.unpackWorldPositionY(currentPacked);
        final int previousX = Util.unpackWorldPositionX(previousPacked);
        final int previousY = Util.unpackWorldPositionY(previousPacked);

        int dx = Math.abs(previousX - currentX);
        int dy = Math.abs(previousY - currentY);

        if (diagonal == 1) {
            return Math.max(dx, dy);
        } else if (diagonal == 2) {
            return dx + dy;
        }

        return Integer.MAX_VALUE;
    }

    public static int distanceBetween(int previousPacked, int currentPacked) {
        return distanceBetween(previousPacked, currentPacked, 1);
    }
}
