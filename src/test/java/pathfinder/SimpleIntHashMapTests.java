package pathfinder;

import net.runelite.api.coords.WorldPoint;
import shortestpath.SimpleIntHashMap;
import shortestpath.Transport;
import shortestpath.WorldPointUtil;

import java.util.List;
import java.util.Map;

public class SimpleIntHashMapTests {
    public static void main(String[] args) {
        Map<WorldPoint, List<Transport>> transports = Transport.loadAllFromResources();
        SimpleIntHashMap<List<Transport>> map = new SimpleIntHashMap<>(10);
        for (Map.Entry<WorldPoint, List<Transport>> entry : transports.entrySet()) {
            int packedPoint = WorldPointUtil.packWorldPoint(entry.getKey());
            map.put(packedPoint, entry.getValue());
        }

        for (Map.Entry<WorldPoint, List<Transport>> entry : transports.entrySet()) {
            int packedPoint = WorldPointUtil.packWorldPoint(entry.getKey());
            assert map.get(packedPoint) == entry.getValue();
        }
    }
}
