package shortestpath.pathfinder;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.zip.GZIPInputStream;
import shortestpath.Util;

import static net.runelite.api.Constants.REGION_SIZE;

public class SplitFlagMapData extends CollisionMapData {
    private static final int MAXIMUM_SIZE = 20 * 1024 * 1024;

    private final LoadingCache<Integer, FlagMap> regionMaps;

    public SplitFlagMapData(Map<Integer, byte[]> compressedRegions, int flagCount) {
        super(flagCount);

        regionMaps = CacheBuilder
                .newBuilder()
                .weigher((Weigher<Integer, FlagMap>) (k, v) -> v.flags.size() / 8)
                .maximumWeight(MAXIMUM_SIZE)
                .build(CacheLoader.from(position -> {
                    byte[] compressedRegion = compressedRegions.get(position);

                    if (compressedRegion == null) {
                        int x = unpackX(position);
                        int y = unpackY(position);
                        return new FlagMap(x * REGION_SIZE, y * REGION_SIZE, (x + 1) * REGION_SIZE - 1, (y + 1) * REGION_SIZE - 1, this.flagCount);
                    }

                    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressedRegion))) {
                        return new FlagMap(Util.readAllBytes(in), this.flagCount);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
    }

    @Override
    public boolean get(int x, int y, int z, int flag) {
        try {
            return regionMaps.get(packPosition(x / REGION_SIZE, y / REGION_SIZE)).get(x, y, z, flag);
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e);
        }
    }
}
