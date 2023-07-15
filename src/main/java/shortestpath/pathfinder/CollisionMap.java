package shortestpath.pathfinder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import shortestpath.ShortestPathPlugin;
import shortestpath.Transport;
import shortestpath.Util;

public class CollisionMap {
    // Enum.values() makes copies every time which hurts performance in the hotpath
    private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();
    private final CollisionMapData collisionMapData;

    public CollisionMap(Map<Integer, byte[]> compressedRegions) {
        collisionMapData = new RawCollisionData(compressedRegions, 2);
    }

    public boolean n(int x, int y, int z) {
        return collisionMapData.get(x, y, z, 0);
    }

    public boolean s(int x, int y, int z) {
        return n(x, y - 1, z);
    }

    public boolean e(int x, int y, int z) {
        return collisionMapData.get(x, y, z, 1);
    }

    public boolean w(int x, int y, int z) {
        return e(x - 1, y, z);
    }

    private boolean ne(int x, int y, int z) {
        return n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z);
    }

    private boolean nw(int x, int y, int z) {
        return n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z);
    }

    private boolean se(int x, int y, int z) {
        return s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z);
    }

    private boolean sw(int x, int y, int z) {
        return s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z);
    }

    public boolean isBlocked(int x, int y, int z) {
        return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z);
    }

    private static int neighborPointPackedFromOrdinal(int packedPoint, OrdinalDirection direction) {
        final int x = Util.unpackWorldPositionX(packedPoint);
        final int y = Util.unpackWorldPositionY(packedPoint);
        final int plane = Util.unpackWorldPositionPlane(packedPoint);
        return Util.packWorldPoint(x + direction.x, y + direction.y, plane);
    }

    public List<Node> getNeighbors(Node node, Map<Integer, List<Transport>> transportsPacked) {
        int x = node.getX();
        int y = node.getY();
        int z = node.getPlane();

        List<Node> neighbors = new ArrayList<>();

        @SuppressWarnings("unchecked") // Quiet the List<Transport> cast from Collections.EMPTY_LIST
        List<Transport> transports = transportsPacked.getOrDefault(node.packedPosition, (List<Transport>)Collections.EMPTY_LIST);
        for (int i = 0; i < transports.size(); ++i) {
            Transport transport = transports.get(i);
            neighbors.add(new TransportNode(transport.getDestination(), node, transport.getWait()));
        }

        boolean[] traversable;
        if (isBlocked(x, y, z)) {
            boolean westBlocked = isBlocked(x - 1, y, z);
            boolean eastBlocked = isBlocked(x + 1, y, z);
            boolean southBlocked = isBlocked(x, y - 1, z);
            boolean northBlocked = isBlocked(x, y + 1, z);
            boolean southWestBlocked = isBlocked(x - 1, y - 1, z);
            boolean southEastBlocked = isBlocked(x + 1, y - 1, z);
            boolean northWestBlocked = isBlocked(x - 1, y + 1, z);
            boolean northEastBlocked = isBlocked(x + 1, y + 1, z);
            traversable = new boolean[] {
                !westBlocked,
                !eastBlocked,
                !southBlocked,
                !northBlocked,
                !southWestBlocked && !westBlocked && !southBlocked,
                !southEastBlocked && !eastBlocked && !southBlocked,
                !northWestBlocked && !westBlocked && !northBlocked,
                !northEastBlocked && !eastBlocked && !northBlocked
            };
        } else {
            traversable = new boolean[] {
                w(x, y, z), e(x, y, z), s(x, y, z), n(x, y, z), sw(x, y, z), se(x, y, z), nw(x, y, z), ne(x, y, z)
            };
        }

        for (int i = 0; i < traversable.length; i++) {
            OrdinalDirection d = ORDINAL_VALUES[i];
            int neighborPacked = neighborPointPackedFromOrdinal(node.packedPosition, d);
            if (traversable[i]) {
                neighbors.add(new Node(neighborPacked, node));
            } else if (Math.abs(d.x + d.y) == 1 && isBlocked(x + d.x, y + d.y, z)) {
                @SuppressWarnings("unchecked") // Quiet the List<Transport> cast from Collections.EMPTY_LIST
                List<Transport> neighborTransports = transportsPacked.getOrDefault(node.packedPosition, (List<Transport>)Collections.EMPTY_LIST);
                for (int t = 0; t < neighborTransports.size(); ++t) {
                    Transport transport = neighborTransports.get(t);
                    neighbors.add(new Node(transport.getOrigin(), node));
                }
            }
        }

        return neighbors;
    }

    public static CollisionMap fromResources() {
        Map<Integer, byte[]> compressedRegions = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(ShortestPathPlugin.class.getResourceAsStream("/collision-map.zip"))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String[] n = entry.getName().split("_");

                compressedRegions.put(
                        CollisionMapData.packPosition(Integer.parseInt(n[0]), Integer.parseInt(n[1])),
                        Util.readAllBytes(in)
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CollisionMap(compressedRegions);
    }
}
