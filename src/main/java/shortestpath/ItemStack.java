package shortestpath;

import lombok.Getter;
import net.runelite.api.Item;

public class ItemStack {
    @Getter
    private final int itemId;

    @Getter
    private final int count;

    public ItemStack(int itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    public static ItemStack fromString(String item) {
        String[] params = item.split(" ", 2);
        if (params.length != 2) {
            throw new IllegalArgumentException("Invalid item stack string: " + item);
        }

        int itemId = Integer.MAX_VALUE;
        try {
            itemId = Integer.parseInt(params[1].trim());
        } catch (Exception e) {
            // Todo: Search by name or replace existing with ids?
            if (params[1].startsWith("Coin")) {
                itemId = 995;
            }
        }

        int count = Integer.parseInt(params[0].trim());
        return new ItemStack(itemId, count);
    }

    public static ItemStack fromItem(Item item) {
        return new ItemStack(item.getId(), item.getQuantity());
    }
}
