package com.linkup.app.model;

/** The fixed list of activity categories, each with an emoji used as its icon. */
public final class Categories {

    public static final String[] NAMES = {
            "Chai & Coffee", "Sports", "Fitness", "Study", "Food",
            "Games", "Music", "Explore", "Arts", "Other"
    };

    private static final String[] EMOJI = {
            "☕", "🏸", "🏃", "📚", "🍜",
            "🎲", "🎵", "🗺️", "🎨", "✨"
    };

    private Categories() {
    }

    public static String emojiFor(String category) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(category)) return EMOJI[i];
        }
        return EMOJI[EMOJI.length - 1];
    }

    public static int indexOf(String category) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(category)) return i;
        }
        return NAMES.length - 1;
    }

    /** "☕  Chai & Coffee" style labels for spinners. */
    public static String[] labels() {
        String[] out = new String[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) out[i] = EMOJI[i] + "  " + NAMES[i];
        return out;
    }
}
