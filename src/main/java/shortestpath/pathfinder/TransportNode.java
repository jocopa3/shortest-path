package shortestpath.pathfinder;

import lombok.Getter;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import shortestpath.Transport;

public class TransportNode extends Node implements Comparable<TransportNode> {
    @Getter
    final private Transport transport;

    public TransportNode(WorldPoint position, Node previous, Transport transport) {
        super(position, previous, transport.getWait());
        this.transport = transport;
        this.gp += transport.getItemCost(ItemID.COINS_995);
    }

    @Override
    public int compareTo(TransportNode other) {
        return Integer.compare(cost, other.cost);
    }
}
