package shortestpath.pathfinder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import shortestpath.*;

public class PathfinderConfig {
    private static final WorldArea WILDERNESS_ABOVE_GROUND = new WorldArea(2944, 3523, 448, 448, 0);
    private static final WorldArea WILDERNESS_UNDERGROUND = new WorldArea(2944, 9918, 320, 442, 0);

    @Getter
    private final CollisionMap map;

    private final Map<WorldPoint, List<Transport>> allTransports;
    @Getter
    private final Map<WorldPoint, List<Transport>> transports;
    private final Client client;
    private final ShortestPathConfig config;
    private final ShortestPathPlugin plugin;

    @Getter
    private Duration calculationCutoff;
    private boolean avoidWilderness;
    private boolean useAgilityShortcuts;
    private boolean useGrappleShortcuts;
    private boolean useBoats;
    private boolean useFairyRings;
    private boolean useSpiritTree;
    private boolean useGnomeGliders;
    private boolean useTeleports;
    private boolean useItems;
    private boolean useSpells;
    private Spellbook spellbook;
    private int agilityLevel;
    private int magicLevel;
    private int rangedLevel;
    private int strengthLevel;
    private int prayerLevel;
    private int woodcuttingLevel;
    private ItemSearchLocation itemSearchLocation;
    private ItemGroup items;
    @Getter
    private int gp = Integer.MAX_VALUE; // Assume player is loaded unless otherwise specified

    private Map<Quest, QuestState> questStates = new HashMap<>();

    public PathfinderConfig(CollisionMap map, Map<WorldPoint, List<Transport>> transports, Client client,
                            ShortestPathConfig config, ShortestPathPlugin plugin) {
        this.map = map;
        this.allTransports = transports;
        this.transports = new HashMap<>();
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        calculationCutoff = Duration.ofMillis(config.calculationCutoff() * Constants.GAME_TICK_LENGTH);
        avoidWilderness = config.avoidWilderness();
        useAgilityShortcuts = config.useAgilityShortcuts();
        useGrappleShortcuts = config.useGrappleShortcuts();
        useBoats = config.useBoats();
        useFairyRings = config.useFairyRings();
        useSpiritTree = config.useSpiritTree();
        useGnomeGliders = config.useGnomeGlider();
        useTeleports = config.useTeleports();
        useItems = config.useItems();
        useSpells = config.useSpells();
        itemSearchLocation = config.itemsLocation();

        if (GameState.LOGGED_IN.equals(client.getGameState())) {
            spellbook = Spellbook.values()[client.getVarbitValue(Spellbook.VARBIT_VALUE)];
            agilityLevel = client.getBoostedSkillLevel(Skill.AGILITY);
            magicLevel = client.getBoostedSkillLevel(Skill.MAGIC); // Is boosted level correct for teleport spells?
            rangedLevel = client.getBoostedSkillLevel(Skill.RANGED);
            strengthLevel = client.getBoostedSkillLevel(Skill.STRENGTH);
            prayerLevel = client.getBoostedSkillLevel(Skill.PRAYER);
            woodcuttingLevel = client.getBoostedSkillLevel(Skill.WOODCUTTING);
            //wildernessLevel = client.getWidget(WidgetInfo.PVP_WILDERNESS_LEVEL).getText();
            //plugin.getClientThread().invokeLater(this::refreshQuests);
            refreshQuests(); // TODO: is this safe? Why was invokeLater used?

            List<ItemContainer> containers = new ArrayList<>(3);
            switch (itemSearchLocation) {
                case BANK:
                    containers.add(client.getItemContainer(InventoryID.BANK));
                    // Fall-through
                case INVENTORY:
                    containers.add(client.getItemContainer(InventoryID.INVENTORY));
                    containers.add(client.getItemContainer(InventoryID.EQUIPMENT));
                    break;
            }

            items = ItemGroup.fromItemContainers(containers);
            if (config.useGP()) {
                gp = Math.min(items.getItemCount(ItemID.COINS_995), config.gpCost());
            } else {
                gp = 0;
            }
        }

        refreshTransports();
    }

    private void refreshQuests() {
        useFairyRings &= !QuestState.NOT_STARTED.equals(Quest.FAIRYTALE_II__CURE_A_QUEEN.getState(client));
        useSpiritTree &= QuestState.FINISHED.equals(Quest.TREE_GNOME_VILLAGE.getState(client));
        useGnomeGliders &= QuestState.FINISHED.equals(Quest.THE_GRAND_TREE.getState(client));

        for (Map.Entry<WorldPoint, List<Transport>> entry : allTransports.entrySet()) {
            for (Transport transport : entry.getValue()) {
                if (transport.isQuestLocked()) {
                    try {
                        questStates.put(transport.getQuest(), transport.getQuest().getState(client));
                    } catch (NullPointerException ignored) {
                    }
                }
            }
        }
    }

    private boolean isInWilderness(WorldPoint p) {
        return WILDERNESS_ABOVE_GROUND.distanceTo(p) == 0 || WILDERNESS_UNDERGROUND.distanceTo(p) == 0;
    }

    public boolean avoidWilderness(WorldPoint position, WorldPoint neighbor, WorldPoint target) {
        return avoidWilderness && !isInWilderness(position) && isInWilderness(neighbor) && !isInWilderness(target);
    }

    public boolean isNear(WorldPoint location) {
        if (plugin.isStartPointSet() || client.getLocalPlayer() == null) {
            return true;
        }
        return config.recalculateDistance() < 0 ||
               client.getLocalPlayer().getWorldLocation().distanceTo2D(location) <= config.recalculateDistance();
    }

    private void refreshTransports() {
        transports.clear();
        for (Map.Entry<WorldPoint, List<Transport>> entry : allTransports.entrySet()) {
            List<Transport> usableTransports = new ArrayList<>(entry.getValue().size());
            for (Transport t : entry.getValue()) {
                if (useTransport(t)) {
                    usableTransports.add(t);
                }
            }

            if (!usableTransports.isEmpty()) {
                transports.put(entry.getKey(), usableTransports);
            }
        }
    }

    private boolean useTransport(Transport transport) {
        final int transportAgilityLevel = transport.getRequiredLevel(Skill.AGILITY);
        final int transportMagicLevel = transport.getRequiredLevel(Skill.MAGIC);
        final int transportRangedLevel = transport.getRequiredLevel(Skill.RANGED);
        final int transportStrengthLevel = transport.getRequiredLevel(Skill.STRENGTH);
        final int transportPrayerLevel = transport.getRequiredLevel(Skill.PRAYER);
        final int transportWoodcuttingLevel = transport.getRequiredLevel(Skill.WOODCUTTING);
        final ItemGroup[] transportItems = transport.getRequiredItems();
        final Spellbook transportSpellbook = transport.getRequiredSpellbook();

        final boolean isAgilityShortcut = transport.isAgilityShortcut();
        final boolean isGrappleShortcut = transport.isGrappleShortcut();
        final boolean isBoat = transport.isBoat();
        final boolean isFairyRing = transport.isFairyRing();
        final boolean isSpiritTree = transport.isSpiritTree();
        final boolean isGnomeGlider = transport.isGnomeGlider();
        final boolean isTeleport = transport.isTeleport();
        final boolean isCanoe = isBoat && transportWoodcuttingLevel > 1;
        final boolean isPrayerLocked = transportPrayerLevel > 1;
        final boolean isQuestLocked = transport.isQuestLocked();
        final boolean isSpell = transport.isSpell();
        final boolean isItem = transportItems != null;
        
        if (isAgilityShortcut) {
            if (!useAgilityShortcuts || agilityLevel < transportAgilityLevel) {
                return false;
            }

            if (isGrappleShortcut && (!useGrappleShortcuts || rangedLevel < transportRangedLevel || strengthLevel < transportStrengthLevel)) {
                return false;
            }
        }

        if (isBoat) {
            if (!useBoats) {
                return false;
            }

            if (isCanoe && woodcuttingLevel < transportWoodcuttingLevel) {
                return false;
            }
        }

        if (isFairyRing && !useFairyRings) {
            return false;
        }

        if (isSpiritTree && !useSpiritTree) {
            return false;
        }

        if (isGnomeGlider && !useGnomeGliders) {
            return false;
        }

        if (isTeleport && !useTeleports) {
            return false;
        }

        if (isPrayerLocked && prayerLevel < transportPrayerLevel) {
            return false;
        }

        if (isSpell && (!useSpells || magicLevel < transportMagicLevel || !spellbook.equals(transportSpellbook))) {
            return false;
        }

        if (isQuestLocked && !QuestState.FINISHED.equals(questStates.getOrDefault(transport.getQuest(), QuestState.NOT_STARTED))) {
            return false;
        }

        if (isItem) {
            if (!useItems || items == null) {
                return false;
            } else if (!ItemSearchLocation.NONE.equals(itemSearchLocation)) {
                boolean hasItems = false;
                for (ItemGroup requiredItems : transportItems) {
                    if (items.hasItems(requiredItems)) {
                        hasItems = true;
                        break;
                    }
                }

                if (!hasItems) {
                    return false;
                }
            }
        }

        return true;
    }
}
