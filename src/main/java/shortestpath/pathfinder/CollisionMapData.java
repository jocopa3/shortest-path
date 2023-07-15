package shortestpath.pathfinder;

public abstract class CollisionMapData {

    final int flagCount;

    public CollisionMapData(int flagCount) {
        this.flagCount = flagCount;
    }

    public abstract boolean get(int x, int y, int z, int flag);

    public static int unpackX(int position) {
        return position & 0xFFFF;
    }

    public static int unpackY(int position) {
        return (position >> 16) & 0xFFFF;
    }

    public static int packPosition(int x, int y) {
        return (x & 0xFFFF) | ((y & 0xFFFF) << 16);
    }
}
