package shortestpath.pathfinder;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import shortestpath.WorldPointUtil;

public class WorldPointPair {
	@Getter
	private final int start, end, distance, value;
	@Getter
	private final WorldPoint startPoint, endPoint;

	public WorldPointPair(int start, int end, int value) {
		this.value = value;
		this.start = start;
		this.end = end;
		this.startPoint = WorldPointUtil.unpackWorldPoint(start);
		this.endPoint = WorldPointUtil.unpackWorldPoint(end);
		distance = startPoint.distanceTo(endPoint);
	}

	public WorldPointPair(WorldPoint start, WorldPoint end, int value) {
		this.value = value;
		this.start = WorldPointUtil.packWorldPoint(start);
		this.end = WorldPointUtil.packWorldPoint(end);
		this.startPoint = start;
		this.endPoint = end;
		distance = startPoint.distanceTo(end);
	}
}
