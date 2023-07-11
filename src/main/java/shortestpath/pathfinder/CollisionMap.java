package shortestpath.pathfinder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.runelite.api.coords.WorldPoint;
import shortestpath.ShortestPathPlugin;
import shortestpath.Transport;
import shortestpath.Util;

public class CollisionMap extends SplitFlagMap {
    public CollisionMap(int regionSize, Map<Position, byte[]> compressedRegions) {
        super(regionSize, compressedRegions, 2);
    }

    public boolean n(int x, int y, int z) {
        return get(x, y, z, 0);
    }

    public boolean s(int x, int y, int z) {
        return n(x, y - 1, z);
    }

    public boolean e(int x, int y, int z) {
        return get(x, y, z, 1);
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

    public List<Node> getNeighbors(Node node, PathfinderConfig config) {
        int x = node.position.getX();
        int y = node.position.getY();
        int z = node.position.getPlane();

        List<Node> neighbors = new ArrayList<>();

        boolean isFairyRing = false;
        List<Transport> transports = config.getTransports().get(node.position);
        if (transports != null) {
            for (Transport transport : transports) {
                if (config.useTransport(transport)) {
                    neighbors.add(new TransportNode(transport.getDestination(), node, transport.getWait()));
                    isFairyRing |= transport.isFairyRing();
                }
            }
        }

        if (node.isRootNode()) {
            for (Transport transport : config.getTransports().getOrDefault(null, new ArrayList<>())) {
                if (config.useTransport(transport)) {
                    neighbors.add(new TransportNode(transport.getDestination(), node, transport.getWait()));
                }
            }
        }

        boolean[] traversable;
        if (isBlocked(x, y, z) && isFairyRing) {
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
            OrdinalDirection d = OrdinalDirection.values()[i];
            if (traversable[i]) {
                neighbors.add(new Node(d.translatePoint(node.position), node));
            } else if (Math.abs(d.x + d.y) == 1 && isBlocked(x + d.x, y + d.y, z)) {
                transports = config.getTransports().get(d.translatePoint(node.position));
                if (transports == null) continue;
                for (Transport transport : transports) {
                    neighbors.add(new Node(transport.getOrigin(), node));
                }
            }
        }

        return neighbors;
    }

    public static CollisionMap fromResources() {
        Map<SplitFlagMap.Position, byte[]> compressedRegions = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(ShortestPathPlugin.class.getResourceAsStream("/collision-map.zip"))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String[] n = entry.getName().split("_");

                compressedRegions.put(
                        new SplitFlagMap.Position(Integer.parseInt(n[0]), Integer.parseInt(n[1])),
                        Util.readAllBytes(in)
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CollisionMap(64, compressedRegions);
    }
}
