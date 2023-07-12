package shortestpath;

import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * This class represents a travel point between two WorldPoints.
 */
public class Transport {

    /** The starting point of this transport */
    @Getter
    private final WorldPoint origin;

    /** The ending point of this transport */
    @Getter
    private final WorldPoint destination;

    /** The skill levels required to use this transport */
    private final int[] skillLevels = new int[Skill.values().length];

    @Getter
    private ItemGroup[] requiredItems;

    /** The quest required to use this transport */
    @Getter
    private Quest quest;

    /** Whether the transport is an agility shortcut */
    @Getter
    private boolean isAgilityShortcut;


    /** Whether the transport is a crossbow grapple shortcut */
    @Getter
    private boolean isGrappleShortcut;

    /** Whether the transport is a magic shortcut */
    @Getter
    private boolean isSpell;

    /** The spellbook required */
    @Getter
    private Spellbook requiredSpellbook;

    /** Whether the transport is a boat */
    @Getter
    private boolean isBoat;

    /** Whether the transport is a fairy ring */
    @Getter
    private boolean isFairyRing;

    /** Whether the transport is a spirit tree */
    @Getter
    private boolean isSpiritTree;

    /** Whether the transport is a gnome glider */
    @Getter
    private boolean isGnomeGlider;

    /** Whether the transport is a teleport */
    @Getter
    private boolean isTeleport;

    /** Whether the transport is a one-way teleport */
    @Getter
    private boolean isOneWay;

    /** The additional travel time */
    @Getter
    private int wait;

    /** Description of the transport method */
    @Getter
    private String description;

    Transport(final WorldPoint origin, final WorldPoint destination) {
        this.origin = origin;
        this.destination = destination;
    }

    Transport(final WorldPoint origin, final WorldPoint destination, final TransportType transportType) {
        this(origin, destination);

        switch (transportType) {
            case FAIRY_RING:
                this.isFairyRing = true;
                break;
            case SPIRIT_TREE:
                this.isSpiritTree = true;
                break;
            case GNOME_GLIDER:
                this.isGnomeGlider = true;
                break;
        }
    }

    Transport(final String line) {
        final String DELIM = " ";

        String[] parts = line.split("\t");

        if (parts[0].isEmpty()) {
            origin = null;
        } else {
            String[] parts_origin = parts[0].split(DELIM);
            origin = new WorldPoint(
                Integer.parseInt(parts_origin[0]),
                Integer.parseInt(parts_origin[1]),
                Integer.parseInt(parts_origin[2]));
        }

        String[] parts_destination = parts[1].split(DELIM);
        destination = new WorldPoint(
            Integer.parseInt(parts_destination[0]),
            Integer.parseInt(parts_destination[1]),
            Integer.parseInt(parts_destination[2]));

        // Description
        if (parts.length >= 3 && !parts[2].isEmpty()) {
             description = parts[2];
        } else {
            description = "";
        }

        // Skill requirements
        if (parts.length >= 4 && !parts[3].isEmpty()) {
            String[] skillRequirements = parts[3].split(";");

            for (String requirement : skillRequirements) {
                String[] levelAndSkill = requirement.split(DELIM);

                int level = Integer.parseInt(levelAndSkill[0]);
                String skillName = levelAndSkill[1];

                Skill[] skills = Skill.values();
                for (int i = 0; i < skills.length; i++) {
                    if (skills[i].getName().equals(skillName)) {
                        skillLevels[i] = level;
                        if (Skill.MAGIC.equals(skills[i])) {
                            if (levelAndSkill.length < 3 || (requiredSpellbook = Spellbook.fromName(levelAndSkill[2])) == null) {
                                throw new IllegalArgumentException("Magic requires a spellbook; valid spellbooks are: " + Spellbook.SPELLBOOK_NAMES + "\nLine: " + line);
                            }
                        }
                        break;
                    }
                }
            }
        }

        // Item requirements
        if (parts.length >= 5 && !parts[4].isEmpty()) {
            String[] itemStrings = parts[4].split(";");
            requiredItems = new ItemGroup[itemStrings.length];

            for (int i = 0; i < itemStrings.length; ++i) {
                try {
                    requiredItems[i] = ItemGroup.fromString(itemStrings[i].trim());
                } catch (Exception e) {
                    System.err.println("Bad String: " + itemStrings[i]);
                    System.err.println("Bad Line: " + line);
                    e.printStackTrace();
                }
            }
        }

        // Quest requirements
        if (parts.length >= 6 && !parts[5].isEmpty()) {
            this.quest = findQuest(parts[5]);
        }

        // Additional travel time
        if (parts.length >= 7 && !parts[6].isEmpty()) {
            this.wait = Integer.parseInt(parts[6]);
        }

        // parts[7] = Additional comments?

        isSpell = getRequiredLevel(Skill.MAGIC) >= 1;
        isAgilityShortcut = getRequiredLevel(Skill.AGILITY) >= 1;
        isGrappleShortcut = isAgilityShortcut && (getRequiredLevel(Skill.RANGED) >= 1 || getRequiredLevel(Skill.STRENGTH) >= 1);
    }

    /** The skill level required to use this transport */
    public int getRequiredLevel(Skill skill) {
        return skillLevels[skill.ordinal()];
    }

    /** Whether the transport has a quest requirement */
    public boolean isQuestLocked() {
        return quest != null;
    }

    private static Quest findQuest(String questName) {
        for (Quest quest : Quest.values()) {
            if (quest.getName().equals(questName)) {
                return quest;
            }
        }
        return null;
    }

    private static class MultiDirectionalTransport {
        final WorldPoint point;
        final String questName;
        final String description;
        final boolean oneWay;

        MultiDirectionalTransport(WorldPoint point, String questName, String description) {
            this(point, questName, description, false);
        }

        MultiDirectionalTransport(WorldPoint point, String questName, String description, boolean oneWay) {
            this.point = point;
            this.questName = questName;
            this.description = description;
            this.oneWay = oneWay;
        }
    }

    private static void addMultiDirectionalTransports(Map<WorldPoint, List<Transport>> transports, List<MultiDirectionalTransport> points, TransportType transportType, int wait) {
        for (MultiDirectionalTransport origin : points) {
            if (origin.oneWay) continue;

            for (int i = 0; i < points.size(); i++) {
                MultiDirectionalTransport destination = points.get(i);
                if (origin.point.equals(destination.point)) {
                    continue;
                }

                Transport transport = new Transport(origin.point, destination.point, transportType);
                transport.description = destination.description.replaceFirst(" \\d*?$", ""); // Remove last digits;
                transport.wait = wait;

                if (!Strings.isNullOrEmpty(destination.questName)) {
                    transport.quest = findQuest(destination.questName);
                }

                transports.computeIfAbsent(origin.point, k -> new ArrayList<>()).add(transport);
            }
        }
    }

    public static void addTransports(Map<WorldPoint, List<Transport>> transports, String path, TransportType transportType) {
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);

            List<MultiDirectionalTransport> fairyRings = new ArrayList<>();
            List<MultiDirectionalTransport> spiritTrees = new ArrayList<>();
            List<MultiDirectionalTransport> gnomeGliders = new ArrayList<>();

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                switch (transportType) {
                    case FAIRY_RING: {
                            String[] p = line.split("\t");
                            WorldPoint point = new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                            MultiDirectionalTransport ring = new MultiDirectionalTransport(point, p.length >= 7 ? "Fairy Ring (" + p[6] + ")" : "", p.length >= 4 ? p[3] : "");
                            fairyRings.add(ring);
                        }
                        break;
                    case SPIRIT_TREE: {
                            String[] p = line.split("\t");
                            WorldPoint point = new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                            MultiDirectionalTransport tree = new MultiDirectionalTransport(point, p.length >= 7 ? p[6] : "", p.length >= 4 ? p[3] : "");
                            spiritTrees.add(tree);
                        }
                        break;
                    case GNOME_GLIDER: {
                            String[] p = line.split("\t");
                            WorldPoint point = new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                            MultiDirectionalTransport glider = new MultiDirectionalTransport(point, p.length >= 7 ? p[6] : "", p.length >= 4 ? p[3] : "", p.length >= 9);
                            gnomeGliders.add(glider);
                        }
                        break;
                    default:
                        Transport transport = new Transport(line);
                        transport.isBoat = TransportType.BOAT.equals(transportType);
                        transport.isTeleport = TransportType.TELEPORT.equals(transportType);
                        transport.isOneWay = TransportType.ONE_WAY.equals(transportType);
                        WorldPoint origin = transport.getOrigin();
                        transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);
                        break;
                }
            }

            addMultiDirectionalTransports(transports, fairyRings, TransportType.FAIRY_RING, 5);
            addMultiDirectionalTransports(transports, spiritTrees, TransportType.SPIRIT_TREE, 5); // Not sure what wait is for spirit trees
            addMultiDirectionalTransports(transports, gnomeGliders, TransportType.GNOME_GLIDER, 5); // Not sure what wait is for gnome glider
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static HashMap<WorldPoint, List<Transport>> fromResources(ShortestPathConfig config) {
        HashMap<WorldPoint, List<Transport>> transports = new HashMap<>();

        addTransports(transports, "/transports.txt", TransportType.TRANSPORT);

        if (config.useBoats()) {
            addTransports(transports, "/boats.txt", TransportType.BOAT);
        }

        if (config.useFairyRings()) {
            addTransports(transports, "/fairy_rings.txt", TransportType.FAIRY_RING);
        }

        if (config.useTeleports()) {
            addTransports(transports, "/teleports.txt", TransportType.TELEPORT);
        }

        if (config.useItems()) {
            addTransports(transports, "/items.txt", TransportType.ONE_WAY);
        }

        if (config.useSpells()) {
            addTransports(transports, "/spells.txt", TransportType.ONE_WAY);
        }

        if (config.useSpiritTree()) {
            addTransports(transports, "/spirit_trees.txt", TransportType.SPIRIT_TREE);
        }

        if (config.useGnomeGlider()) {
            addTransports(transports, "/gnome_glider.txt", TransportType.GNOME_GLIDER);
        }

        return transports;
    }

    public static HashMap<WorldPoint, List<Transport>> loadAllFromResources() {
        HashMap<WorldPoint, List<Transport>> transports = new HashMap<>();

        addTransports(transports, "/transports.txt", TransportType.TRANSPORT);
        addTransports(transports, "/boats.txt", TransportType.BOAT);
        addTransports(transports, "/fairy_rings.txt", TransportType.FAIRY_RING);
        addTransports(transports, "/teleports.txt", TransportType.TELEPORT);
        addTransports(transports, "/items.txt", TransportType.ONE_WAY);
        addTransports(transports, "/spells.txt", TransportType.ONE_WAY);
        addTransports(transports, "/spirit_trees.txt", TransportType.SPIRIT_TREE);
        addTransports(transports, "/gnome_glider.txt", TransportType.GNOME_GLIDER);

        return transports;
    }

    public static enum TransportType {
        TRANSPORT,
        BOAT,
        FAIRY_RING,
        TELEPORT,
        ONE_WAY,
        SPIRIT_TREE,
        GNOME_GLIDER
    }
}
