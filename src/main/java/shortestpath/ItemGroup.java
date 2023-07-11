package shortestpath;

import lombok.Getter;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

import java.util.*;

public class ItemGroup {
    @Getter
    private final Map<Integer, ItemStack> items;

    public ItemGroup(ItemStack item) {
        items = new HashMap<>();
        items.put(item.getItemId(), item);
    }

    public ItemGroup(ItemStack... others) {
        items = new HashMap<>();
        for (ItemStack item : others) {
            items.put(item.getItemId(), item);
        }
    }

    public boolean hasItems(ItemGroup other) {
        boolean hasItems = true;
        for (Map.Entry<Integer, ItemStack> entry : other.items.entrySet()) {
            if (!items.containsKey(entry.getKey())) return false;
            if (items.get(entry.getKey()).getCount() < entry.getValue().getCount()) return false;
        }
        return true;
    }

    public static ItemGroup fromString(String item) {
        String[] params = item.split(",");
        ItemStack[] items = new ItemStack[params.length];
        for (int i = 0; i < params.length; ++i) {
            try {
                items[i] = ItemStack.fromString(params[i].trim());
            } catch (Exception e) {
                System.err.println("Bad Group: " + params[i]);
                throw e;
            }
        }

        return new ItemGroup(items);
    }

    public static ItemGroup fromItemContainers(List<ItemContainer> containers) {
        List<Item> items = new ArrayList<>();
        for (ItemContainer container : containers) {
            if (container == null) continue;
            Collections.addAll(items, container.getItems());
        }

        ItemStack[] itemStacks = new ItemStack[items.size()];
        for (int i = 0; i < itemStacks.length; ++i) {
            Item item = items.get(i);
            itemStacks[i] = ItemStack.fromItem(item);
        }

        return new ItemGroup(itemStacks);
    }
}
