package com.gathin.tvhome;

public final class LauncherPerfProfileTest {
    public static void main(String[] args) {
        assertEquals(1.06f, LauncherPerfProfile.FOCUS_SCALE, "focus scale");
        assertEquals(1920, LauncherPerfProfile.HERO_MAX_DECODE_WIDTH, "hero width cap");

        assertEquals(1920,
                LauncherPerfProfile.clampHeroDecodeWidth(LauncherPerfProfile.HERO_MAX_DECODE_WIDTH, 3840),
                "caps 4K request at 1080p width");
        assertEquals(1280,
                LauncherPerfProfile.clampHeroDecodeWidth(LauncherPerfProfile.HERO_MAX_DECODE_WIDTH, 1280),
                "keeps smaller width");
        assertEquals(1920,
                LauncherPerfProfile.clampHeroDecodeWidth(LauncherPerfProfile.HERO_MAX_DECODE_WIDTH, 0),
                "fallback width when requested is invalid");

        if (!(LauncherPerfProfile.FOCUS_ANIM_MS >= 120 && LauncherPerfProfile.FOCUS_ANIM_MS <= 150)) {
            throw new AssertionError("focus duration out of expected range");
        }
        if (!(LauncherPerfProfile.UNFOCUS_ANIM_MS >= 90 && LauncherPerfProfile.UNFOCUS_ANIM_MS <= 150)) {
            throw new AssertionError("unfocus duration out of expected range");
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(float expected, float actual, String message) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
