package com.gathin.tvhome;

import java.util.Arrays;

public final class LauncherUiModelsTest {
    public static void main(String[] args) {
        assertEquals(LauncherUiModels.THEME_CLASSIC, LauncherUiModels.normalizeTheme(-1), "theme normalize lower bound");
        assertEquals(LauncherUiModels.WALLPAPER_APP_BANNER, LauncherUiModels.normalizeWallpaper(999), "wallpaper normalize upper bound");
        assertEquals(LauncherUiModels.SOUND_OFF, LauncherUiModels.normalizeSound(999), "sound normalize upper bound");

        assertTrue(LauncherUiModels.resolveReducedMotion(true, LauncherUiModels.MOTION_SYSTEM), "system motion keep true");
        assertTrue(LauncherUiModels.resolveReducedMotion(false, LauncherUiModels.MOTION_REDUCED), "force reduced motion");
        assertTrue(!LauncherUiModels.resolveReducedMotion(true, LauncherUiModels.MOTION_FULL), "force full motion");

        int[] g1 = LauncherUiModels.curatedGradientForSeed(LauncherUiModels.wallpaperSeedForPackage("com.sample.one"));
        int[] g2 = LauncherUiModels.curatedGradientForSeed(LauncherUiModels.wallpaperSeedForPackage("com.sample.one"));
        assertTrue(Arrays.equals(g1, g2), "same package must map to deterministic gradient");

        LauncherUiModels.Palette light = LauncherUiModels.paletteForTheme(LauncherUiModels.THEME_LIGHT);
        assertEquals(0xFFF3F5F8, light.bg, "light bg token");
    }

    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }
}
