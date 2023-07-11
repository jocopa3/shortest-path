package shortestpath;

public enum Spellbook {
    STANDARD("Standard", "Standard Spellbook"),
    ANCIENT("Ancient", "Ancient Magicks"),
    LUNAR("Lunar", "Lunar Spellbook"),
    ARCEUUS("Arceuus", "Arceuus Spellbook");

    public static final int VARBIT_VALUE = 4070;

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
