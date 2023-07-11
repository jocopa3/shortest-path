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

    /** Whether the transport is a teleport */
    @Getter
    private boolean isTeleport;

    /** Whether the transport is a one-way teleport */
    @Getter
    private boolean isOneWay;

    /** The additional travel time */
    @Getter
    private int wait;

    public int used = 0;

    Transport(final WorldPoint origin, final WorldPoint destination) {
        this.origin = origin;
        this.destination = destination;
    }

    Transport(final WorldPoint origin, final WorldPoint destination, final TransportType transportType) {
        this(origin, destination);
        this.isFairyRing = TransportType.FAIRY_RING.equals(transportType);
        this.isSpiritTree = TransportType.SPIRIT_TREE.equals(transportType);
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
//        if (parts.length >= 3 && !parts[2].isEmpty()) {
//            // Handle description
//        }

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
                            if (levelAndSkill.length < 3) {
                                throw new IllegalArgumentException("Magic requires a spellbook; Line: " + line);
                            }
                            requiredSpellbook = Spellbook.fromName(levelAndSkill[2]);
                            if (requiredSpellbook == null) {
                                throw new IllegalArgumentException("Magic requires a spellbook; Line: " + line);
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

    private static void addMultiDirectionalTransports(Map<WorldPoint, List<Transport>> transports, List<WorldPoint> points, List<String> questNames, TransportType transportType, int wait) {
        for (WorldPoint origin : points) {
            for (int i = 0; i < points.size(); i++) {
                WorldPoint destination = points.get(i);
                if (origin.equals(destination)) {
                    continue;
                }

                Transport transport = new Transport(origin, destination, transportType);
                transport.wait = wait;

                if (questNames != null && questNames.size() == points.size()) {
                    String questName = questNames.get(i);
                    if (!Strings.isNullOrEmpty(questName)) {
                        transport.quest = findQuest(questName);
                    }
                }

                transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);
            }
        }
    }

    public static void addTransports(Map<WorldPoint, List<Transport>> transports, String path, TransportType transportType) {
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);

            List<WorldPoint> fairyRings = new ArrayList<>();
            List<String> fairyRingsQuestNames = new ArrayList<>();
            List<WorldPoint> spiritTrees = new ArrayList<>();
            List<String> spiritTreesQuestNames = new ArrayList<>();

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                switch (transportType) {
                    case FAIRY_RING: {
                            String[] p = line.split("\t");
                            fairyRings.add(new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                            fairyRingsQuestNames.add(p.length >= 7 ? p[6] : "");
                        }
                        break;
                    case SPIRIT_TREE: {
                            String[] p = line.split("\t");
                            spiritTrees.add(new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                            spiritTreesQuestNames.add(p.length >= 7 ? p[6] : "");
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

            addMultiDirectionalTransports(transports, fairyRings, fairyRingsQuestNames, TransportType.FAIRY_RING, 5);
            addMultiDirectionalTransports(transports, spiritTrees, spiritTreesQuestNames, TransportType.SPIRIT_TREE, 5); // Not sure what wait is for spirit trees
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

        return transports;
    }

    public static enum TransportType {
        TRANSPORT,
        BOAT,
        FAIRY_RING,
        TELEPORT,
        ONE_WAY,
        SPIRIT_TREE
    }
}
