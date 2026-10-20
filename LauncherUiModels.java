package com.gathin.tvhome;

public final class LauncherUiModels {
    private LauncherUiModels() {}

    public static final int THEME_CLASSIC = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_VIBRANT = 2;

    public static final int WALLPAPER_APP_BANNER = 0;
    public static final int WALLPAPER_CURATED_STILL = 1;
    public static final int WALLPAPER_CURATED_AMBIENT = 2;

    public static final int SOUND_OFF = 0;
    public static final int SOUND_SOFT = 1;
    public static final int SOUND_CINEMATIC = 2;

    public static final int MOTION_SYSTEM = 0;
    public static final int MOTION_REDUCED = 1;
    public static final int MOTION_FULL = 2;

    public static int normalizeTheme(int value) {
        if (value < THEME_CLASSIC || value > THEME_VIBRANT) return THEME_CLASSIC;
        return value;
    }

    public static int normalizeWallpaper(int value) {
        if (value < WALLPAPER_APP_BANNER || value > WALLPAPER_CURATED_AMBIENT) return WALLPAPER_APP_BANNER;
        return value;
    }

    public static int normalizeSound(int value) {
        if (value < SOUND_OFF || value > SOUND_CINEMATIC) return SOUND_OFF;
        return value;
    }

    public static int normalizeMotion(int value) {
        if (value < MOTION_SYSTEM || value > MOTION_FULL) return MOTION_SYSTEM;
        return value;
    }

    public static boolean resolveReducedMotion(boolean systemReduced, int motionMode) {
        motionMode = normalizeMotion(motionMode);
        if (motionMode == MOTION_REDUCED) return true;
        if (motionMode == MOTION_FULL) return false;
        return systemReduced;
    }

    public static Palette paletteForTheme(int themeMode) {
        switch (normalizeTheme(themeMode)) {
            case THEME_LIGHT:
                return new Palette(0xFFF3F5F8, 0xFFFFFFFF, 0xFF10141A, 0xFF4D5563, 0xFF7A869A, 0xFF10141A, 0xFFE7ECF4);
            case THEME_VIBRANT:
                return new Palette(0xFF070912, 0xFF1A1F33, 0xFFE9F0FF, 0xFF95A6CF, 0xFF8FA7FF, 0xFFE9F0FF, 0xFF111A35);
            default:
                return new Palette(0xFF0A0A0F, 0xFF1C1C24, 0xFFF5F5F7, 0xFFAEB1BA, 0xFF9EA3B1, 0xFFF5F5F7, 0xFF14141C);
        }
    }

    public static int wallpaperSeedForPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return 0;
        return Math.abs(pkg.hashCode());
    }

    public static int[] curatedGradientForSeed(int seed) {
        int[][] presets = new int[][]{
                {0xFF0A2A43, 0xFF06121F},
                {0xFF2B1A4A, 0xFF0F1021},
                {0xFF123D36, 0xFF091B18},
                {0xFF3A2A10, 0xFF181109},
                {0xFF3E1734, 0xFF140B1E}
        };
        return presets[seed % presets.length];
    }

    public static final class Palette {
        public final int bg;
        public final int card;
        public final int text;
        public final int mutedText;
        public final int rowInactive;
        public final int rowActive;
        public final int heroFallback;

        public Palette(int bg, int card, int text, int mutedText, int rowInactive, int rowActive, int heroFallback) {
            this.bg = bg;
            this.card = card;
            this.text = text;
            this.mutedText = mutedText;
            this.rowInactive = rowInactive;
            this.rowActive = rowActive;
            this.heroFallback = heroFallback;
        }
    }
}
