package shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import shortestpath.Util;

public class VisitedTileSet {
    private static final int TOTAL_PLANES = 4;
    private static final int REGION_SIZE = 64;

    // TODO: what is the max number of regions?
    private static final int REGION_EXTENT_X = 192;
    private static final int REGION_EXTENT_Y = 256;

    // Regions are lazily allocated in set(x, y, z)
    private final VisitedRegion[] visitedRegions = new VisitedRegion[REGION_EXTENT_X * REGION_EXTENT_Y];

    public boolean get(WorldPoint point) {
        return get(point.getRegionX(), point.getRegionY(), point.getPlane());
    }

    public boolean get(int packedPosition) {
        final int x = Util.unpackWorldPositionX(packedPosition);
        final int y = Util.unpackWorldPositionY(packedPosition);
        final int plane = Util.unpackWorldPositionPlane(packedPosition);

        return get(x, y, plane);
    }

    public boolean get(int x, int y, int plane) {
        final int regionIndex = getRegionId(x, y);
        final VisitedRegion region = visitedRegions[regionIndex];

        if (region == null) {
            return false;
        }

        x %= REGION_SIZE;
        y %= REGION_SIZE;
        return region.get(x, y, plane);
    }

    public boolean set(WorldPoint point) {
        return set(point.getX(), point.getY(), point.getPlane());
    }

    public boolean set(int packedPosition) {
        final int x = Util.unpackWorldPositionX(packedPosition);
        final int y = Util.unpackWorldPositionY(packedPosition);
        final int plane = Util.unpackWorldPositionPlane(packedPosition);

        return set(x, y, plane);
    }

    public boolean set(int x, int y, int plane) {
        final int regionIndex = getRegionId(x, y);
        VisitedRegion region = visitedRegions[regionIndex];

        if (region == null) {
            region = new VisitedRegion();
            visitedRegions[regionIndex] = region;
        }

        x %= REGION_SIZE;
        y %= REGION_SIZE;
        return region.set(x, y, plane);
    }

    public void clear() {
        for (int i = 0; i < visitedRegions.length; ++i) {
            if (visitedRegions[i] != null) {
                visitedRegions[i] = null;
            }
        }
    }

    private static int getRegionId(int x, int y) {
        return ((x >> 6) << 8) | (y >> 6);
    }

    private class VisitedRegion {
        // This assumes a row is at most 64 tiles and fits in a long
        private final long[] planes = new long[TOTAL_PLANES * REGION_SIZE];

        // Sets a tile as visited in the tile bitset
        // Returns true if the tile is unique and hasn't been seen before or false if it was seen before
        public boolean set(int x, int y, int plane) {
            final int index = y + plane * REGION_SIZE;
            boolean unique = (planes[index] & (1L << x)) == 0;
            planes[index] |= 1L << x;
            return unique;
        }

        public boolean get(int x, int y, int plane) {
            return (planes[y + plane * REGION_SIZE] & (1L << x)) != 0;
        }
    }
}
