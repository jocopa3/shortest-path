package shortestpath.pathfinder;

import net.runelite.api.geometry.RectangleUnion;
import shortestpath.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static net.runelite.api.Constants.REGION_SIZE;

public class RawCollisionData extends CollisionMapData {

    private final RectangleUnion.Rectangle regionExtents;
    private final int width;
    private final int height;

    // Size is automatically chosen based on the max extents of the collision data
    private final FlagMap[] flagMaps;

    public RawCollisionData(Map<Integer, byte[]> compressedRegions, int flagCount) {
        super(flagCount);

        regionExtents = getExtents(compressedRegions);
        width = regionExtents.getX2() - regionExtents.getX1() + 1;
        height = regionExtents.getY2() - regionExtents.getY1() + 1;
        flagMaps = new FlagMap[width * height];

        for (int pos = 0; pos < flagMaps.length; ++pos) {
            final int x = (pos % width) + regionExtents.getX1();
            final int y = (pos / width) + regionExtents.getY1();

            FlagMap map;
            final byte[] compressedRegion = compressedRegions.get(packPosition(x, y));
            if (compressedRegion == null) {
                map = new FlagMap(x * REGION_SIZE, y * REGION_SIZE, (x + 1) * REGION_SIZE - 1, (y + 1) * REGION_SIZE - 1, this.flagCount);
            } else {
                try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressedRegion))) {
                    byte[] bytes = Util.readAllBytes(in);
                    map = new FlagMap(bytes, this.flagCount);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            flagMaps[getIndex(x, y)] = map;
        }
    }

    private int getIndex(int x, int y) {
        return (x - regionExtents.getX1()) + (y - regionExtents.getY1()) * width;
    }

    @Override
    public boolean get(int x, int y, int z, int flag) {
        return flagMaps[getIndex(x / REGION_SIZE, y / REGION_SIZE)].get(x, y, z, flag);
    }

    private static RectangleUnion.Rectangle getExtents(Map<Integer, byte[]> compressedRegions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;

        for (int packedPosition : compressedRegions.keySet()) {
            final int x = unpackX(packedPosition);
            final int y = unpackY(packedPosition);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        return new RectangleUnion.Rectangle(minX, minY, maxX, maxY);
    }
}
