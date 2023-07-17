package shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;

public enum OrdinalDirection {
    WEST(-1, 0),
    EAST(1, 0),
    SOUTH(0, -1),
    NORTH(0, 1),
    SOUTH_WEST(-1, -1),
    SOUTH_EAST(1, -1),
    NORTH_WEST(-1, 1),
    NORTH_EAST(1, 1);

    final int x;
    final int y;

    OrdinalDirection(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public WorldPoint translatePoint(WorldPoint origin) {
        return new WorldPoint(origin.getX() + x, origin.getY() + y, origin.getPlane());
    }
}
