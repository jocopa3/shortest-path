package shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import shortestpath.Transport;

public class TransportNode extends Node implements Comparable<TransportNode> {
    final private Transport transport;

    public TransportNode(WorldPoint position, Node previous, int wait, Transport transport) {
        super(position, previous, wait);
        this.transport = transport;
    }

    @Override
    public int compareTo(TransportNode other) {
        return Integer.compare(cost, other.cost);
    }
}
