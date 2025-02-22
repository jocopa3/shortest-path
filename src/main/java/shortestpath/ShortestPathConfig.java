package shortestpath;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(ShortestPathPlugin.CONFIG_GROUP)
public interface ShortestPathConfig extends Config {
    @ConfigSection(
        name = "Settings",
        description = "Options for the pathfinding",
        position = 0
    )
    String sectionSettings = "sectionSettings";

    @ConfigItem(
        keyName = "avoidWilderness",
        name = "Avoid wilderness",
        description = "Whether the wilderness should be avoided if possible<br>" +
            "(otherwise, will e.g. suggest using wilderness lever to travel from Edgeville to Ardougne)",
        position = 1,
        section = sectionSettings
    )
    default boolean avoidWilderness() {
        return true;
    }

    @ConfigItem(
        keyName = "useAgilityShortcuts",
        name = "Use agility shortcuts",
        description = "Whether to include agility shortcuts in the path. You must also have the required agility level",
        position = 2,
        section = sectionSettings
    )
    default boolean useAgilityShortcuts() {
        return true;
    }

    @ConfigItem(
        keyName = "useGrappleShortcuts",
        name = "Use grapple shortcuts",
        description = "Whether to include crossbow grapple agility shortcuts in the path.<br>" +
            "You must also have the required agility, ranged and strength levels",
        position = 3,
        section = sectionSettings
    )
    default boolean useGrappleShortcuts() {
        return false;
    }

    @ConfigItem(
        keyName = "useBoats",
        name = "Use boats",
        description = "Whether to include boats, canoes and charter ships in the path",
        position = 4,
        section = sectionSettings
    )
    default boolean useBoats() {
        return true;
    }

    @ConfigItem(
        keyName = "useFairyRings",
        name = "Use fairy rings",
        description = "Whether to include fairy rings in the path.<br>" +
            "You must also have completed the required quest or miniquest",
        position = 5,
        section = sectionSettings
    )
    default boolean useFairyRings() {
        return false;
    }

    @ConfigItem(
        keyName = "useTeleports",
        name = "Use teleports",
        description = "Whether to include teleportation portals and levers in the path",
        position = 6,
        section = sectionSettings
    )
    default boolean useTeleports() {
        return false;
    }

    @ConfigItem(
        keyName = "useSpiritTree",
        name = "Use spirit tree",
        description = "Whether to include spirit tree teleports in the path",
        position = 7,
        section = sectionSettings
    )
    default boolean useSpiritTree() {
        return false;
    }

    @ConfigItem(
        keyName = "useGnomeGlider",
        name = "Use gnome glider",
        description = "Whether to include gnome glider transports in the path",
        position = 8,
        section = sectionSettings
    )
    default boolean useGnomeGlider() {
        return false;
    }

    @ConfigItem(
        keyName = "useItems",
        name = "Use teleportation items",
        description = "Whether to include teleportation items",
        position = 9,
        section = sectionSettings
    )
    default boolean useItems() {
        return false;
    }

    @ConfigItem(
        keyName = "useSpells",
        name = "Use teleportation spells",
        description = "Whether to include teleportation spells",
        position = 10,
        section = sectionSettings
    )
    default boolean useSpells() {
        return false;
    }

    @ConfigItem(
        keyName = "itemsLocation",
        name = "Search items in...",
        description = "Where to look for items to use for magic or item teleports",
        position = 11,
        section = sectionSettings
    )
    default ItemSearchLocation itemsLocation() {
        return ItemSearchLocation.INVENTORY;
    }

    @Range()
    @ConfigItem(
        keyName = "useGP",
        name = "Take toll routes",
        description = "Whether to take routes that use gp to travel",
        position = 12,
        section = sectionSettings
    )
    default boolean useGP() {
        return true;
    }

    @Range()
    @ConfigItem(
        keyName = "gpCost",
        name = "Max gp cost",
        description = "How much GP to spend on routes",
        position = 13,
        section = sectionSettings
    )
    default int gpCost() {
        return 5000; // Covers the most expensive toll route (Port Sarim - Prifddinas)
    }

    @ConfigItem(
        keyName = "cancelInstead",
        name = "Cancel instead of recalculating",
        description = "Whether the path should be cancelled rather than recalculated when the recalculate distance limit is exceeded",
        position = 14,
        section = sectionSettings
    )
    default boolean cancelInstead() {
        return false;
    }

    @Range(
        min = -1,
        max = 20000
    )
    @ConfigItem(
        keyName = "recalculateDistance",
        name = "Recalculate distance",
        description = "Distance from the path the player should be for it to be recalculated (-1 for never)",
        position = 15,
        section = sectionSettings
    )
    default int recalculateDistance() {
        return 10;
    }

    @Range(
        min = -1,
        max = 50
    )
    @ConfigItem(
        keyName = "finishDistance",
        name = "Finish distance",
        description = "Distance from the target tile at which the path should be ended (-1 for never)",
        position = 16,
        section = sectionSettings
    )
    default int reachedDistance() {
        return 5;
    }

    @ConfigItem(
        keyName = "showTileCounter",
        name = "Show tile counter",
        description = "Whether to display the number of tiles travelled, number of tiles remaining or disable counting",
        position = 17,
        section = sectionSettings
    )
    default TileCounter showTileCounter() {
        return TileCounter.DISABLED;
    }

    @ConfigItem(
        keyName = "tileCounterStep",
        name = "Tile counter step",
        description = "The number of tiles between the displayed tile counter numbers",
        position = 18,
        section = sectionSettings
    )
    default int tileCounterStep()
    {
        return 1;
    }

    @Units(
        value = Units.TICKS
    )
    @Range(
        min = 1,
        max = 1000
    )
    @ConfigItem(
        keyName = "calculationCutoff",
        name = "Calculation cutoff",
        description = "The cutoff threshold in number of ticks (0.6 seconds) of no progress being<br>" +
            "made towards the path target before the calculation will be stopped",
        position = 19,
        section = sectionSettings
    )
    default int calculationCutoff()
    {
        return 5;
    }

    @ConfigSection(
        name = "Display",
        description = "Options for displaying the path on the world map, minimap and scene tiles",
        position = 20
    )
    String sectionDisplay = "sectionDisplay";

    @ConfigItem(
        keyName = "drawMap",
        name = "Draw path on world map",
        description = "Whether the path should be drawn on the world map",
        position = 21,
        section = sectionDisplay
    )
    default boolean drawMap() {
        return true;
    }

    @ConfigItem(
        keyName = "drawMinimap",
        name = "Draw path on minimap",
        description = "Whether the path should be drawn on the minimap",
        position = 21,
        section = sectionDisplay
    )
    default boolean drawMinimap() {
        return true;
    }

    @ConfigItem(
        keyName = "drawTiles",
        name = "Draw path on tiles",
        description = "Whether the path should be drawn on the game tiles",
        position = 23,
        section = sectionDisplay
    )
    default boolean drawTiles() {
        return true;
    }

    @ConfigItem(
        keyName = "drawTransports",
        name = "Draw transports",
        description = "Whether transports should be drawn",
        position = 24,
        section = sectionDisplay
    )
    default boolean drawTransports() {
        return false;
    }

    @ConfigItem(
        keyName = "drawCollisionMap",
        name = "Draw collision map",
        description = "Whether the collision map should be drawn",
        position = 25,
        section = sectionDisplay
    )
    default boolean drawCollisionMap() {
        return false;
    }

    @ConfigItem(
        keyName = "pathStyle",
        name = "Path style",
        description = "Whether to display the path as tiles or a segmented line",
        position = 26,
        section = sectionDisplay
    )
    default TileStyle pathStyle() {
        return TileStyle.TILES;
    }

    @ConfigSection(
        name = "Colours",
        description = "Colours for the path map, minimap and scene tiles",
        position = 27
    )
    String sectionColours = "sectionColours";

    @Alpha
    @ConfigItem(
        keyName = "colourPath",
        name = "Path",
        description = "Colour of the path tiles on the world map, minimap and in the game scene",
        position = 28,
        section = sectionColours
    )
    default Color colourPath() {
        return new Color(255, 0, 0);
    }

    @Alpha
    @ConfigItem(
        keyName = "colourPathCalculating",
        name = "Calculating",
        description = "Colour of the path tiles while the pathfinding calculation is in progress",
        position = 29,
        section = sectionColours
    )
    default Color colourPathCalculating() {
        return new Color(0, 0, 255);
    }

    @Alpha
    @ConfigItem(
        keyName = "colourTransports",
        name = "Transports",
        description = "Colour of the transport tiles",
        position = 30,
        section = sectionColours
    )
    default Color colourTransports() {
        return new Color(0, 255, 0, 128);
    }

    @Alpha
    @ConfigItem(
        keyName = "colourCollisionMap",
        name = "Collision map",
        description = "Colour of the collision map tiles",
        position = 31,
        section = sectionColours
    )
    default Color colourCollisionMap() {
        return new Color(0, 128, 255, 128);
    }
}
