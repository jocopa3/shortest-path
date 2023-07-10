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

    /** The quest required to use this transport */
    @Getter
    private Quest quest;

    /** Whether the transport is an agility shortcut */
    @Getter
    private boolean isAgilityShortcut;

    /** Whether the transport is a magic shortcut */
    @Getter
    private boolean isMagicShortcut;

    /** Whether the transport is a crossbow grapple shortcut */
    @Getter
    private boolean isGrappleShortcut;

    /** Whether the transport is a boat */
    @Getter
    private boolean isBoat;

    /** Whether the transport is a fairy ring */
    @Getter
    private boolean isFairyRing;

    /** Whether the transport is a teleport */
    @Getter
    private boolean isTeleport;

    /** Whether the transport is a one-way teleport */
    @Getter
    private boolean isOneWay;

    /** The additional travel time */
    @Getter
    private int wait;

    Transport(final WorldPoint origin, final WorldPoint destination) {
        this.origin = origin;
        this.destination = destination;
    }

    Transport(final WorldPoint origin, final WorldPoint destination, final boolean isFairyRing) {
        this(origin, destination);
        this.isFairyRing = isFairyRing;
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
                        break;
                    }
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

        isMagicShortcut = getRequiredLevel(Skill.MAGIC) > 1;
        isAgilityShortcut = getRequiredLevel(Skill.AGILITY) > 1;
        isGrappleShortcut = isAgilityShortcut && (getRequiredLevel(Skill.RANGED) > 1 || getRequiredLevel(Skill.STRENGTH) > 1);
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

    private static void addTransports(Map<WorldPoint, List<Transport>> transports, ShortestPathConfig config, String path, TransportType transportType) {
        try {
            String s = new String(Util.readAllBytes(ShortestPathPlugin.class.getResourceAsStream(path)), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            List<WorldPoint> fairyRings = new ArrayList<>();
            List<String> fairyRingsQuestNames = new ArrayList<>();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                if (TransportType.FAIRY_RING.equals(transportType)) {
                    String[] p = line.split("\t");
                    fairyRings.add(new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                    fairyRingsQuestNames.add(p.length >= 7 ? p[6] : "");
                } else {
                    Transport transport = new Transport(line);
                    transport.isBoat = TransportType.BOAT.equals(transportType);
                    transport.isTeleport = TransportType.TELEPORT.equals(transportType);
                    transport.isOneWay = TransportType.ONE_WAY.equals(transportType);
                    if (!config.useAgilityShortcuts() && transport.isAgilityShortcut) {
                        continue;
                    }
                    if (!config.useGrappleShortcuts() && transport.isGrappleShortcut) {
                        continue;
                    }
                    if (!config.useItemsAndSpells() && transport.isOneWay) {
                        continue;
                    }
                    WorldPoint origin = transport.getOrigin();
                    transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);
                }
            }

            for (WorldPoint origin : fairyRings) {
                for (int i = 0; i < fairyRings.size(); i++) {
                    WorldPoint destination = fairyRings.get(i);
                    String questName = fairyRingsQuestNames.get(i);
                    if (origin.equals(destination)) {
                        continue;
                    }
                    Transport transport = new Transport(origin, destination, true);
                    transport.wait = 5;
                    transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);
                    if (!Strings.isNullOrEmpty(questName)) {
                        transport.quest = findQuest(questName);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static HashMap<WorldPoint, List<Transport>> fromResources(ShortestPathConfig config) {
        HashMap<WorldPoint, List<Transport>> transports = new HashMap<>();

        addTransports(transports, config, "/transports.txt", TransportType.TRANSPORT);

        if (config.useBoats()) {
            addTransports(transports, config, "/boats.txt", TransportType.BOAT);
        }

        if (config.useFairyRings()) {
            addTransports(transports, config, "/fairy_rings.txt", TransportType.FAIRY_RING);
        }

        if (config.useTeleports()) {
            addTransports(transports, config, "/teleports.txt", TransportType.TELEPORT);
        }

        if (config.useItems()) {
            addTransports(transports, config, "/items.txt", TransportType.ONE_WAY);
        }

        if (config.useSpells()) {
            addTransports(transports, config, "/spells.txt", TransportType.ONE_WAY);
        }

        return transports;
    }

    private enum TransportType {
        TRANSPORT,
        BOAT,
        FAIRY_RING,
        TELEPORT,
        ONE_WAY
    }
}
