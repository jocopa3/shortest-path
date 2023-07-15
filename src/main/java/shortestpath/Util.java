package shortestpath;

import net.runelite.api.coords.WorldPoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Util {

    public static int packWorldPoint(WorldPoint point) {
        return (point.getX() & 0x7FFF) | ((point.getY() & 0x7FFF) << 15) | ((point.getPlane() & 0x3) << 30);
    }

    // Pack an x, y, plane position into a single int with 15 bits for XY and 2 bits for the plane
    public static int packWorldPoint(int x, int y, int plane) {
        return (x & 0x7FFF) | ((y & 0x7FFF) << 15) | ((plane & 0x3) << 30);
    }

    public static int unpackWorldPositionX(int packedWorldPos) {
        return packedWorldPos & 0x7FFF;
    }

    public static int unpackWorldPositionY(int packedWorldPos) {
        return (packedWorldPos >> 15) & 0x7FFF;
    }

    public static int unpackWorldPositionPlane(int packedWorldPos) {
        return (packedWorldPos >> 30) & 0x3;
    }

    public static WorldPoint unpackWorldPoint(int packedWorldPos) {
        final int x = unpackWorldPositionX(packedWorldPos);
        final int y = unpackWorldPositionY(packedWorldPos);
        final int plane = unpackWorldPositionPlane(packedWorldPos);

        return new WorldPoint(x, y, plane);
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];

        while (true) {
            int read = in.read(buffer, 0, buffer.length);

            if (read == -1) {
                return result.toByteArray();
            }

            result.write(buffer, 0, read);
        }
    }

    public static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException ignored) {
        }
    }
}
