package pathfinder;

import net.runelite.api.coords.WorldPoint;
import shortestpath.Transport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static shortestpath.Transport.addTransports;

public class TestTransports {
    public static void main(String[] args) {
        Transport.loadAllFromResources();
        Map<WorldPoint, List<Transport>> transports = new HashMap<>();
        //addTransports(transports, "/spells.txt", Transport.TransportType.ONE_WAY);
        System.out.println(transports.size());
    }
}
