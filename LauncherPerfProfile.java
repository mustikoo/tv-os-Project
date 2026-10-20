package com.gathin.tvhome;

public final class LauncherPerfProfile {
    private LauncherPerfProfile() {}

    public static final float FOCUS_SCALE = 1.06f;
    public static final long FOCUS_ANIM_MS = 140L;
    public static final long UNFOCUS_ANIM_MS = 120L;
    public static final long HERO_DEBOUNCE_MS = 120L;
    public static final long HERO_FADE_OUT_MS = 120L;
    public static final long HERO_FADE_IN_MS = 150L;
    public static final int HERO_MAX_DECODE_WIDTH = 1920;

    public static int clampHeroDecodeWidth(int maxDecodeWidth, int requestedWidth) {
        if (requestedWidth <= 0) return maxDecodeWidth;
        return Math.min(maxDecodeWidth, requestedWidth);
    }
}
