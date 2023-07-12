package shortestpath;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum Spellbook {
    STANDARD("Standard", "Standard Spellbook"),
    ANCIENT("Ancient", "Ancient Magicks"),
    LUNAR("Lunar", "Lunar Spellbook"),
    ARCEUUS("Arceuus", "Arceuus Spellbook");

    public static final int VARBIT_VALUE = 4070;
    static final String SPELLBOOK_NAMES = Arrays.stream(Spellbook.values())
            .map((spellbook -> { return spellbook.name;}))
            .collect(Collectors.joining(", "));

    final String name, friendlyName;

    Spellbook(String name, String friendlyName) {
        this.name = name;
        this.friendlyName = friendlyName;
    }

    public static Spellbook fromName(String name) {
        for (Spellbook spellbook : values()) {
            if (spellbook.name.equals(name)) {
                return spellbook;
            }
        }

        return null;
    }
}
