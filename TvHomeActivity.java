package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TV Home — tvOS tarzı launcher (tek dosya, sıfır bağımlılık).
 *
 * Bu sürümde düzeltilenler:
 *  - animate().elevation() derlenme hatası → translationZ kullanımı
 *  - Hero banner artık gerçekten yükleniyor (loadBanner)
 *  - rememberedCols artık yazılıyor (satır hafızası çalışıyor)
 *  - Launcher kendini listeden hariç tutuyor, uygulamalar alfabetik
 *  - Büyüyen karo kırpılmıyor (tüm ata zincirinde clipChildren=false)
 *  - Odaktaki karo komşuların üstünde çiziliyor (Z-order)
 *  - İkonlar kırpılmıyor (FIT_CENTER), etiket karonun ALTINDA (tvOS stili)
 *  - Leanback + normal launcher intent'leri birleştiriliyor
 *  - Uygulama açılışında karodan büyüme animasyonu
 *  - İlk satırda YUKARI → üst bar (tvOS kontrol merkezi hissi)
 *  - Daha yumuşak, çok kısmi tonlu ses sentezi
 *  - Reduced-motion (animasyon kapatma) desteği
 *  - Hero'da kaydırmayla paralaks
 */
public class TvHomeActivity extends Activity {

    // ═══════════════════════════════════════════════════════════════
    //  KONFIGÜRASYON — tüm tvOS değerleri burada
    // ═══════════════════════════════════════════════════════════════
    // Apollo Premium4K için: 4K/60 çıkış korunurken launcher yüzeyi 1080p'de 60 FPS hedefler.
    private static final float  FOCUS_SCALE    = LauncherPerfProfile.FOCUS_SCALE;
    private static final float  PRESS_SCALE    = 0.96f;
    private static final long   FOCUS_ANIM_MS  = LauncherPerfProfile.FOCUS_ANIM_MS;
    private static final long   UNFOCUS_ANIM_MS = LauncherPerfProfile.UNFOCUS_ANIM_MS;
    private static final long   HERO_DEBOUNCE_MS = LauncherPerfProfile.HERO_DEBOUNCE_MS;
    private static final long   HERO_FADE_OUT_MS = LauncherPerfProfile.HERO_FADE_OUT_MS;
    private static final long   HERO_FADE_IN_MS = LauncherPerfProfile.HERO_FADE_IN_MS;
    private static final int    HERO_MAX_WIDTH_PX = LauncherPerfProfile.HERO_MAX_DECODE_WIDTH;
    private static final boolean NAV_SOUNDS_ENABLED_DEFAULT = false;

    private static final int    DP_HERO_H      = 480;
    private static final int    DP_TILE_W      = 200;
    private static final int    DP_TILE_H      = 112;
    private static final int    DP_TILE_GAP    = 18;
    private static final int    DP_PAD_H       = 56;
    private static final int    DP_ROW_GAP     = 26;
    private static final String PREFS_NAME = "tvhome_ui_prefs";
    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String PREF_WALLPAPER_MODE = "wallpaper_mode";
    private static final String PREF_SOUND_MODE = "sound_mode";
    private static final String PREF_MOTION_MODE = "motion_mode";

    // ═══════════════════════════════════════════════════════════════
    //  DURUM
    // ═══════════════════════════════════════════════════════════════
    private float density;
    private int colorBg;
    private int colorCard;
    private int colorText;
    private int colorMutedText;
    private int colorRowActive;
    private int colorRowInactive;
    private int colorHeroFallback;
    private ImageView heroBanner;
    private View heroAmbientOverlay;
    private TextView clockView;
    private ImageView avatar;
    private LinearLayout sectionsContainer;
    private ScrollView verticalScroll;
    private final SparseIntArray rememberedCols = new SparseIntArray();
    private final List<Section> sections = new ArrayList<>();
    private boolean jumping = false;
    private boolean reducedMotion = false;
    private AppInfo currentHeroInfo;
    private Handler clockHandler;
    private Handler heroHandler;
    private Runnable pendingHeroRunnable;
    private int heroUpdateToken = 0;
    private ValueAnimator ambientAnimator;
    private SharedPreferences prefs;
    private int themeMode;
    private int wallpaperMode;
    private int soundMode;
    private int motionMode;

    // ═══════════════════════════════════════════════════════════════
    //  YAŞAM DÖNGÜSÜ
    // ═══════════════════════════════════════════════════════════════
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadUserPrefs();
        boolean systemReduced = Settings.Global.getFloat(getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        reducedMotion = LauncherUiModels.resolveReducedMotion(systemReduced, motionMode);
        applyThemePalette();
        applySoundMode();
        // Açılıştaki ilk otomatik odakta "tik" çalmasın.
        clockHandler = new Handler(Looper.getMainLooper());
        heroHandler = new Handler(Looper.getMainLooper());
        if (SoundFx.enabled) {
            clockHandler.postDelayed(() -> SoundFx.ready = true, 500);
        }

        setContentView(buildLayout());
        renderSections();

        sectionsContainer.post(() -> focusItem(0, 0));
        clockHandler.post(tick);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (clockHandler != null) clockHandler.removeCallbacksAndMessages(null);
        if (heroHandler != null) heroHandler.removeCallbacksAndMessages(null);
        if (ambientAnimator != null) ambientAnimator.cancel();
        SoundFx.release();
    }

    private void loadUserPrefs() {
        themeMode = LauncherUiModels.normalizeTheme(
                prefs.getInt(PREF_THEME_MODE, LauncherUiModels.THEME_CLASSIC));
        wallpaperMode = LauncherUiModels.normalizeWallpaper(
                prefs.getInt(PREF_WALLPAPER_MODE, LauncherUiModels.WALLPAPER_APP_BANNER));
        soundMode = LauncherUiModels.normalizeSound(
                prefs.getInt(PREF_SOUND_MODE,
                        NAV_SOUNDS_ENABLED_DEFAULT ? LauncherUiModels.SOUND_SOFT : LauncherUiModels.SOUND_OFF));
        motionMode = LauncherUiModels.normalizeMotion(
                prefs.getInt(PREF_MOTION_MODE, LauncherUiModels.MOTION_SYSTEM));
    }

    private void savePref(String key, int value) {
        if (prefs == null) return;
        prefs.edit().putInt(key, value).apply();
    }

    private void applyThemePalette() {
        LauncherUiModels.Palette palette = LauncherUiModels.paletteForTheme(themeMode);
        colorBg = palette.bg;
        colorCard = palette.card;
        colorText = palette.text;
        colorMutedText = palette.mutedText;
        colorRowInactive = palette.rowInactive;
        colorRowActive = palette.rowActive;
        colorHeroFallback = palette.heroFallback;
    }

    private void applySoundMode() {
        soundMode = LauncherUiModels.normalizeSound(soundMode);
        if (soundMode == LauncherUiModels.SOUND_OFF) {
            SoundFx.setEnabled(false);
            SoundFx.setProfile(soundMode);
            return;
        }
        SoundFx.setProfile(soundMode);
        SoundFx.setEnabled(true);
        SoundFx.init();
    }

    /** BACK: önce odağı sol üst karoya al; tvOS'ta home "dışarı çıkmaz". */
    @Override public void onBackPressed() {
        View f = getCurrentFocus();
        if (f instanceof AppCard) {
            AppCard c = (AppCard) f;
            if (c.rowIdx == 0 && c.colIdx == 0) { super.onBackPressed(); return; }
            SoundFx.back();
            focusItem(0, rememberedCols.get(0, 0));
        } else if (f == avatar) {
            SoundFx.back();
            focusItem(0, rememberedCols.get(0, 0));
        } else {
            super.onBackPressed();
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (clockView != null) {
                clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date()));
            }
            // Dakika başına hizala — 30 sn'lik sabit periyot saati kaydırır.
            long delay = 60_000 - (System.currentTimeMillis() % 60_000) + 250;
            clockHandler.postDelayed(this, delay);
        }
    };

    /** Reduced-motion açıksa süreleri sıfırla. */
    private long dur(long ms) { return reducedMotion ? 1 : ms; }

    // ═══════════════════════════════════════════════════════════════
    //  PROGRAMATİK LAYOUT
    // ═══════════════════════════════════════════════════════════════
    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(colorBg);
        // NOT: setKeepScreenOn kasıtlı YOK — home ekranı daydream/uykuyu engellemesin.

        // --- Hero banner ---
        heroBanner = new ImageView(this);
        heroBanner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroBanner.setBackgroundColor(colorHeroFallback);
        FrameLayout.LayoutParams heroLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(DP_HERO_H));
        heroLp.gravity = Gravity.TOP;
        root.addView(heroBanner, heroLp);

        heroAmbientOverlay = new View(this);
        FrameLayout.LayoutParams ambientLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(DP_HERO_H));
        ambientLp.gravity = Gravity.TOP;
        root.addView(heroAmbientOverlay, ambientLp);

        // --- Hero scrim (üst transparan → alt arka plan) ---
        View heroScrim = new View(this);
        heroScrim.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{ colorBg, withAlpha(colorBg, 0x99), 0x00000000 }));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(DP_HERO_H));
        slp.gravity = Gravity.TOP;
        root.addView(heroScrim, slp);

        // --- Top bar ---
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(DP_PAD_H), dp(32), dp(DP_PAD_H), 0);

        TextView brand = new TextView(this);
        brand.setText("TV Home");
        brand.setTextColor(colorText);
        brand.setTextSize(TypedValue.COMPLEX_SP, 20);
        brand.setLetterSpacing(0.08f);
        brand.setAlpha(0.9f);
        topBar.addView(brand, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        clockView = new TextView(this);
        clockView.setText("00:00");
        clockView.setTextColor(colorText);
        clockView.setTextSize(TypedValue.COMPLEX_SP, 16);
        clockView.setGravity(Gravity.CENTER);
        clockView.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable badge = new GradientDrawable();
        badge.setCornerRadius(dp(24));
        badge.setColor(withAlpha(colorText, 0x26));
        badge.setStroke(dp(1), withAlpha(colorText, 0x22));
        clockView.setBackground(badge);
        topBar.addView(clockView);

        avatar = new ImageView(this);
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(withAlpha(colorText, 0x26));
        avBg.setStroke(dp(1), withAlpha(colorText, 0x33));
        avatar.setBackground(avBg);
        avatar.setImageResource(android.R.drawable.ic_menu_preferences);
        avatar.setPadding(dp(10), dp(10), dp(10), dp(10));
        avatar.setColorFilter(colorText);
        avatar.setFocusable(true);
        avatar.setClickable(true);
        avatar.setOnClickListener(v -> {
            SoundFx.open();
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) {}
        });
        avatar.setOnLongClickListener(v -> {
            openLauncherSettings();
            return true;
        });
        avatar.setOnFocusChangeListener((v, focused) -> {
            SoundFx.focus();
            v.animate().scaleX(focused ? 1.15f : 1f).scaleY(focused ? 1.15f : 1f)
                .alpha(focused ? 1f : 0.85f)
                .setDuration(dur(180)).setInterpolator(new DecelerateInterpolator()).start();
        });
        avatar.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusItem(0, rememberedCols.get(0, 0));
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        avLp.leftMargin = dp(14);
        topBar.addView(avatar, avLp);

        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.gravity = Gravity.TOP;
        root.addView(topBar, tl);

        // --- Dikey scroll ---
        verticalScroll = new ScrollView(this);
        verticalScroll.setClipToPadding(false);
        verticalScroll.setClipChildren(false);   // taşan karolar kırpılmasın
        verticalScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        verticalScroll.setVerticalScrollBarEnabled(false);
        verticalScroll.setPadding(0, dp(380), 0, dp(56));

        // Hero paralaks: içerik kayarken hero daha yavaş akar.
        verticalScroll.setOnScrollChangeListener((v, sx, sy, osx, osy) -> {
            if (!reducedMotion) heroBanner.setTranslationY(sy * 0.35f);
        });

        sectionsContainer = new LinearLayout(this);
        sectionsContainer.setOrientation(LinearLayout.VERTICAL);
        sectionsContainer.setClipChildren(false); // taşan karolar kırpılmasın
        sectionsContainer.setClipToPadding(false);
        verticalScroll.addView(sectionsContainer, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(verticalScroll, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // --- Alt vinyet ---
        View vig = new View(this);
        vig.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{ withAlpha(colorBg, 0xCC), 0x00000000 }));
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(140));
        vl.gravity = Gravity.BOTTOM;
        root.addView(vig, vl);
        updateHeroAmbientMode();

        return root;
    }

    // ═══════════════════════════════════════════════════════════════
    //  SATIRLARI OLUŞTUR
    // ═══════════════════════════════════════════════════════════════
    private void renderSections() {
        List<AppInfo> all = loadApps();
        List<AppInfo> media = new ArrayList<>();
        List<AppInfo> games = new ArrayList<>();
        for (AppInfo a : all) {
            String p = a.pkg.toLowerCase();
            if (p.contains("game") || p.contains("oyun")) games.add(a);
            else if (p.contains("video") || p.contains("youtube")
                  || p.contains("netflix") || p.contains("spotify")
                  || p.contains("tv")) media.add(a);
        }

        if (!media.isEmpty()) sections.add(new Section("Medya", media));
        if (!games.isEmpty()) sections.add(new Section("Oyunlar", games));
        sections.add(new Section("Tüm Uygulamalar", all));

        sectionsContainer.removeAllViews();
        for (int i = 0; i < sections.size(); i++) {
            sectionsContainer.addView(buildSectionView(i, sections.get(i)));
        }
    }

    private View buildSectionView(int rowIndex, Section s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(DP_ROW_GAP);
        row.setLayoutParams(rowLp);

        TextView title = new TextView(this);
        title.setText(s.title);
        title.setTextColor(colorRowInactive);
        title.setTextSize(TypedValue.COMPLEX_SP, 26);
        title.setLetterSpacing(0.03f);
        title.setAlpha(0.72f);
        title.setPadding(dp(DP_PAD_H), 0, dp(DP_PAD_H), dp(10));
        title.setTag("row_title_" + rowIndex);
        row.addView(title);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setClipToPadding(false);
        hsv.setClipChildren(false);   // büyüyen karo dikeyde kırpılmasın
        hsv.setPadding(dp(DP_PAD_H), dp(10), dp(DP_PAD_H), dp(10));

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        hsv.addView(strip, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int col = 0; col < s.apps.size(); col++) {
            AppInfo info = s.apps.get(col);
            AppCard card = new AppCard(info, rowIndex, col, s.apps.size());
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cLp.rightMargin = dp(DP_TILE_GAP);
            strip.addView(card, cLp);
        }

        row.addView(hsv);
        return row;
    }

    private List<AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        // Leanback + normal launcher intent'lerini birleştir, paket bazında tekilleştir.
        Map<String, AppInfo> map = new LinkedHashMap<>();
        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(lean, 0)) putApp(pm, ri, map);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) putApp(pm, ri, map);

        List<AppInfo> out = new ArrayList<>(map.values());
        // Türkçe'ye duyarlı alfabetik sıralama.
        Collator col = Collator.getInstance(new Locale("tr", "TR"));
        out.sort((a, b) -> col.compare(a.label, b.label));
        return out;
    }

    private void putApp(PackageManager pm, ResolveInfo ri, Map<String, AppInfo> map) {
        String pkg = ri.activityInfo.packageName;
        // Launcher kendini listelemesin.
        if (pkg.equals(getPackageName())) return;
        if (map.containsKey(pkg)) return;
        AppInfo info = new AppInfo(pkg, ri.loadLabel(pm).toString(), deriveSubtitle(pkg), ri.loadIcon(pm));
        // Hero için gerçek TV banner'ı — DeepSeek sürümünde hiç yüklenmiyordu.
        try { info.banner = ri.loadBanner(pm); } catch (Exception ignored) {}
        map.put(pkg, info);
    }

    private String deriveSubtitle(String pkg) {
        if (pkg == null) return "";
        String[] chunks = pkg.split("\\.");
        if (chunks.length >= 2) return chunks[1].toUpperCase(Locale.ROOT);
        return chunks.length == 1 ? chunks[0].toUpperCase(Locale.ROOT) : "";
    }

    // ═══════════════════════════════════════════════════════════════
    //  ODAK YÖNETİMİ
    // ═══════════════════════════════════════════════════════════════
    private void focusItem(int row, int col) {
        if (row < 0 || row >= sectionsContainer.getChildCount()) return;
        View rowV = sectionsContainer.getChildAt(row);
        HorizontalScrollView hsv = (HorizontalScrollView) ((LinearLayout) rowV).getChildAt(1);
        LinearLayout strip = (LinearLayout) hsv.getChildAt(0);
        if (strip.getChildCount() == 0) return;
        col = Math.min(Math.max(col, 0), strip.getChildCount() - 1);
        strip.getChildAt(col).requestFocus();
    }

    private void jumpRows(int rowIdx, int colIdx, int keyCode) {
        int dir = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
        int targetRow = rowIdx + dir;
        if (targetRow < 0) { // ilk satırdayken YUKARI → üst bar (tvOS hissi)
            avatar.requestFocus();
            return;
        }
        if (jumping || targetRow >= sections.size()) return;

        int targetCount = sections.get(targetRow).apps.size();
        if (targetCount == 0) return;

        int remembered = rememberedCols.get(targetRow, colIdx);
        int targetCol = Math.min(Math.max(remembered, 0), targetCount - 1);

        jumping = true;
        View targetRowV = sectionsContainer.getChildAt(targetRow);
        int centerY = targetRowV.getTop()
            + targetRowV.getHeight() / 2
            - verticalScroll.getHeight() / 2;
        verticalScroll.smoothScrollTo(0, Math.max(0, centerY));

        sectionsContainer.postDelayed(() -> {
            focusItem(targetRow, targetCol);
            jumping = false;
        }, dur(130));
    }

    /** Odaktaki karoyu yatayda görünür tut — tvOS'un yumuşak kayması. */
    private void revealCard(AppCard c) {
        ViewParent p = c.getParent();
        if (!(p instanceof LinearLayout)) return;
        LinearLayout strip = (LinearLayout) p;
        if (!(strip.getParent() instanceof HorizontalScrollView)) return;
        HorizontalScrollView hsv = (HorizontalScrollView) strip.getParent();
        int target = c.getLeft() + c.getWidth() / 2 - hsv.getWidth() / 2;
        hsv.smoothScrollTo(Math.max(0, target), 0);
    }

    /** Odaktaki satır dikeyde görünür değilse yumuşakça kaydır. */
    private void ensureRowVisible(int rowIdx) {
        if (jumping || rowIdx < 0 || rowIdx >= sectionsContainer.getChildCount()) return;
        View rowV = sectionsContainer.getChildAt(rowIdx);
        int top = rowV.getTop();
        int bottom = rowV.getBottom();
        int scrollY = verticalScroll.getScrollY();
        int visible = verticalScroll.getHeight();
        int topLimit = top - dp(420);   // hero altı
        if (topLimit < scrollY) {
            verticalScroll.smoothScrollTo(0, Math.max(0, topLimit));
        } else if (bottom + dp(90) > scrollY + visible) {
            verticalScroll.smoothScrollTo(0, bottom + dp(90) - visible);
        }
    }

    private void onItemFocused(int rowIdx, AppInfo info) {
        if (info != currentHeroInfo) {
            currentHeroInfo = info;
            queueHeroUpdate(info);
        }
        ensureRowVisible(rowIdx);
        for (int j = 0; j < sectionsContainer.getChildCount(); j++) {
            View rowV = sectionsContainer.getChildAt(j);
            View titleV = rowV.findViewWithTag("row_title_" + j);
            if (!(titleV instanceof TextView)) continue;
            ((TextView) titleV).setTextColor(j == rowIdx ? colorRowActive : colorRowInactive);
            float target = (j == rowIdx) ? 1f : 0.62f;
            if (Math.abs(titleV.getAlpha() - target) > 0.01f) {
                titleV.animate().alpha(target).setDuration(dur(220)).start();
            }
        }
    }

    private void queueHeroUpdate(AppInfo info) {
        if (heroHandler == null || info == null) return;
        heroUpdateToken++;
        final int token = heroUpdateToken;
        if (pendingHeroRunnable != null) heroHandler.removeCallbacks(pendingHeroRunnable);
        pendingHeroRunnable = () -> {
            if (token != heroUpdateToken || info != currentHeroInfo) return;
            Drawable d = getPreparedHero(info);
            if (d != null) setHero(d, token);
        };
        heroHandler.postDelayed(pendingHeroRunnable, dur(HERO_DEBOUNCE_MS));
    }

    private Drawable getPreparedHero(AppInfo info) {
        if (info == null) return null;
        if (wallpaperMode == LauncherUiModels.WALLPAPER_APP_BANNER) {
            if (info.banner != null) {
                if (info.preparedBanner == null) {
                    info.preparedBanner = prepareHeroDrawable(info.banner);
                }
                return info.preparedBanner;
            }
            return getCuratedHero(info);
        }
        return getCuratedHero(info);
    }

    private Drawable getCuratedHero(AppInfo info) {
        if (info.curatedBanner == null) {
            int seed = LauncherUiModels.wallpaperSeedForPackage(info.pkg);
            int[] colors = LauncherUiModels.curatedGradientForSeed(seed);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
            g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            info.curatedBanner = g;
        }
        return info.curatedBanner;
    }

    private Drawable prepareHeroDrawable(Drawable source) {
        if (source == null) return null;
        int width = source.getIntrinsicWidth();
        int height = source.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return source;
        int targetWidth = LauncherPerfProfile.clampHeroDecodeWidth(HERO_MAX_WIDTH_PX, width);
        if (targetWidth >= width) return source;
        int targetHeight = Math.max(1, (int) (((long) height * targetWidth) / width));
        Bitmap.Config config = source.getOpacity() == PixelFormat.OPAQUE
                ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        int oldLeft = source.getBounds().left;
        int oldTop = source.getBounds().top;
        int oldRight = source.getBounds().right;
        int oldBottom = source.getBounds().bottom;
        try {
            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, config);
            Canvas canvas = new Canvas(bitmap);
            source.setBounds(0, 0, targetWidth, targetHeight);
            source.draw(canvas);
            return new BitmapDrawable(getResources(), bitmap);
        } catch (OutOfMemoryError ignored) {
            return source;
        } finally {
            source.setBounds(oldLeft, oldTop, oldRight, oldBottom);
        }
    }

    private void setHero(Drawable d, int token) {
        if (d == null) return;
        heroBanner.animate().cancel();
        heroBanner.animate().alpha(0f).scaleX(1.04f).scaleY(1.04f)
            .setDuration(dur(HERO_FADE_OUT_MS)).setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                if (token != heroUpdateToken) return;
                heroBanner.setImageDrawable(d);
                heroBanner.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(dur(HERO_FADE_IN_MS)).setInterpolator(new DecelerateInterpolator())
                    .start();
                updateHeroAmbientMode();
            }).start();
    }

    private int dp(float v) { return (int) (v * density); }

    private void updateHeroAmbientMode() {
        if (heroAmbientOverlay == null) return;
        int overlayAlpha = wallpaperMode == LauncherUiModels.WALLPAPER_APP_BANNER ? 0x00 : 0x33;
        heroAmbientOverlay.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{withAlpha(colorBg, overlayAlpha), 0x00000000}));
        if (ambientAnimator != null) {
            ambientAnimator.cancel();
            ambientAnimator = null;
        }
        if (wallpaperMode == LauncherUiModels.WALLPAPER_CURATED_AMBIENT && !reducedMotion) {
            ambientAnimator = ValueAnimator.ofFloat(-14f, 14f);
            ambientAnimator.setDuration(10_500);
            ambientAnimator.setRepeatMode(ValueAnimator.REVERSE);
            ambientAnimator.setRepeatCount(ValueAnimator.INFINITE);
            ambientAnimator.setInterpolator(new DecelerateInterpolator());
            ambientAnimator.addUpdateListener(a ->
                    heroBanner.setTranslationX((float) a.getAnimatedValue()));
            ambientAnimator.start();
        } else {
            heroBanner.setTranslationX(0f);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private interface ChoiceHandler {
        void onChoice(int index);
    }

    private void openLauncherSettings() {
        String[] items = {
                "Theme",
                "Wallpaper",
                "Motion",
                "Sound"
        };
        new AlertDialog.Builder(this)
                .setTitle("Launcher Settings")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showChoiceDialog("Theme",
                                new String[]{"Classic", "Light", "Vibrant"},
                                themeMode, value -> {
                                    themeMode = LauncherUiModels.normalizeTheme(value);
                                    savePref(PREF_THEME_MODE, themeMode);
                                    recreate();
                                });
                    } else if (which == 1) {
                        showChoiceDialog("Wallpaper",
                                new String[]{"App banners", "Curated still", "Curated ambient"},
                                wallpaperMode, value -> {
                                    wallpaperMode = LauncherUiModels.normalizeWallpaper(value);
                                    savePref(PREF_WALLPAPER_MODE, wallpaperMode);
                                    recreate();
                                });
                    } else if (which == 2) {
                        showChoiceDialog("Motion",
                                new String[]{"System", "Reduced", "Full"},
                                motionMode, value -> {
                                    motionMode = LauncherUiModels.normalizeMotion(value);
                                    savePref(PREF_MOTION_MODE, motionMode);
                                    recreate();
                                });
                    } else {
                        showChoiceDialog("Sound",
                                new String[]{"Off", "Soft", "Cinematic"},
                                soundMode, value -> {
                                    soundMode = LauncherUiModels.normalizeSound(value);
                                    savePref(PREF_SOUND_MODE, soundMode);
                                    applySoundMode();
                                    if (SoundFx.enabled && clockHandler != null) {
                                        SoundFx.ready = false;
                                        clockHandler.postDelayed(() -> SoundFx.ready = true, 350);
                                    }
                                });
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showChoiceDialog(String title, String[] labels, int checkedItem, ChoiceHandler handler) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    handler.onChoice(which);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    //  VERİ SINIFLARI
    // ═══════════════════════════════════════════════════════════════
    static class AppInfo {
        final String pkg;
        final String label;
        final String subtitle;
        final Drawable icon;
        Drawable banner;
        Drawable preparedBanner;
        Drawable curatedBanner;
        AppInfo(String p, String l, String s, Drawable i) {
            pkg = p;
            label = l;
            subtitle = s;
            icon = i;
        }
    }

    static class Section {
        final String title;
        final List<AppInfo> apps;
        Section(String t, List<AppInfo> a) { title = t; apps = a; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  KART VIEW — tüm focus / basma / tilt burada
    //  Yapı: AppCard (dikey kapsayıcı, odaklanabilir)
    //        ├── tile  (yuvarlatılmış zemin + ikon) ← scale/tilt/gölge buna
    //        └── label (karonun ALTINDA, tvOS stili)  ← sadece fade
    // ═══════════════════════════════════════════════════════════════
    class AppCard extends LinearLayout {
        private final FrameLayout tile;
        private final TextView label;
        private final TextView subtitle;
        private final AppInfo info;
        private final int rowIdx, colIdx, rowSize;
        private boolean focused;
        private float currentScale = 1f;
        private ValueAnimator scaleAnim;

        AppCard(AppInfo info, int rowIdx, int colIdx, int rowSize) {
            super(TvHomeActivity.this);
            this.info = info;
            this.rowIdx = rowIdx;
            this.colIdx = colIdx;
            this.rowSize = rowSize;

            setOrientation(VERTICAL);
            setFocusable(true);
            setClickable(true);
            setCameraDistance(6000f * density);

            // --- tile ---
            tile = new FrameLayout(TvHomeActivity.this);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(colorCard);
            tile.setBackground(bg);
            tile.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
                }
            });
            tile.setClipToOutline(false);
            tile.setElevation(dp(1));
            tile.setCameraDistance(6000f * density);

            ImageView icon = new ImageView(TvHomeActivity.this);
            // FIT_CENTER: kare ikon dikdörtgen karoda kırpılmaz.
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = dp(22);
            icon.setPadding(pad, dp(14), pad, dp(14));
            icon.setImageDrawable(info.icon);
            tile.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(tile, new LinearLayout.LayoutParams(dp(DP_TILE_W), dp(DP_TILE_H)));

            // --- label (karonun altında) ---
            label = new TextView(TvHomeActivity.this);
            label.setText(info.label);
            label.setTextColor(colorText);
            label.setTextSize(TypedValue.COMPLEX_SP, 14);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setAlpha(0f);
            label.setPadding(dp(4), dp(4), dp(4), 0);
            addView(label, new LinearLayout.LayoutParams(dp(DP_TILE_W), dp(30)));

            subtitle = new TextView(TvHomeActivity.this);
            subtitle.setText(info.subtitle);
            subtitle.setTextColor(colorMutedText);
            subtitle.setTextSize(TypedValue.COMPLEX_SP, 11);
            subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setAlpha(0.72f);
            subtitle.setPadding(dp(4), 0, dp(4), 0);
            addView(subtitle, new LinearLayout.LayoutParams(dp(DP_TILE_W), dp(24)));

            setOnClickListener(v -> {
                SoundFx.open();
                launch();
            });

            setOnLongClickListener(v -> {
                // Tuş bırakma olayı ayarlar ekranına gidecek; basma ölçeğini geri al.
                if (focused) animateScale(FOCUS_SCALE, dur(FOCUS_ANIM_MS));
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + info.pkg));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                } catch (Exception ignored) {}
                return true;
            });

            setOnKeyListener((v, keyCode, e) -> {
                boolean isCenter = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;

                if (isCenter) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                        SoundFx.press();
                        animateScale(FOCUS_SCALE * PRESS_SCALE, dur(90));
                    } else if (e.getAction() == KeyEvent.ACTION_UP) {
                        animateScale(FOCUS_SCALE, dur(FOCUS_ANIM_MS));
                    }
                    return false; // click release'de ateşlensin
                }

                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;

                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    rememberedCols.put(rowIdx, colIdx); // satır hafızası artık yazılıyor
                    jumpRows(rowIdx, colIdx, keyCode);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && colIdx == 0) {
                    SoundFx.back();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && colIdx == rowSize - 1) {
                    SoundFx.back();
                    return true;
                }
                return false;
            });

            setOnFocusChangeListener((v, hasFocus) -> {
                this.focused = hasFocus;
                if (hasFocus) {
                    animateScale(FOCUS_SCALE, dur(FOCUS_ANIM_MS));
                    // Gölge derinliği: translationZ (elevation() metodu yoktur!)
                    tile.animate().translationZ(dp(6)).setDuration(dur(FOCUS_ANIM_MS)).start();
                    // Z-order: odaktaki karo komşuların ÜSTÜNDE çizilsin.
                    animate().translationZ(dp(3)).setDuration(dur(FOCUS_ANIM_MS)).start();
                    label.animate().alpha(1f).setDuration(dur(120)).start();
                    subtitle.animate().alpha(1f).setDuration(dur(120)).start();
                    SoundFx.focus();
                    rememberedCols.put(rowIdx, colIdx);
                    onItemFocused(rowIdx, info);
                    revealCard(this);
                } else {
                    animateScale(1f, dur(UNFOCUS_ANIM_MS));
                    tile.animate().translationZ(0f).setDuration(dur(UNFOCUS_ANIM_MS)).start();
                    animate().translationZ(0f).setDuration(dur(UNFOCUS_ANIM_MS)).start();
                    label.animate().alpha(0f).setDuration(dur(90)).start();
                    subtitle.animate().alpha(0.72f).setDuration(dur(90)).start();
                }
            });
        }

        private void animateScale(float target, long durMs) {
            if (scaleAnim != null) scaleAnim.cancel();
            scaleAnim = ValueAnimator.ofFloat(currentScale, target);
            scaleAnim.setDuration(durMs);
            scaleAnim.setInterpolator(new DecelerateInterpolator());
            scaleAnim.addUpdateListener(a -> {
                currentScale = (float) a.getAnimatedValue();
                tile.setScaleX(currentScale);
                tile.setScaleY(currentScale);
            });
            scaleAnim.start();
        }

        private void launch() {
            Intent i = getContext().getPackageManager().getLaunchIntentForPackage(info.pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // tvOS açılışı: karo olduğu yerden tam ekrana büyür.
                Bundle opts = ActivityOptions.makeScaleUpAnimation(
                    tile, 0, 0, tile.getWidth(), tile.getHeight()).toBundle();
                try { getContext().startActivity(i, opts); }
                catch (Exception e) { getContext().startActivity(i); }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SES — programatik sentez, dosya yok
    //  Saf sinüs "bip" yerine çok kısmi tonlu, hızlı sönümlü yumuşak vuruşlar.
    //  İleride res/raw altına .wav koyarsan SoundPool sürümüne geçirmek kolay.
    // ═══════════════════════════════════════════════════════════════
    static class SoundFx {
        private static final int SR = 44100;
        private static AudioTrack focus, press, back, open;
        static volatile boolean ready = false; // açılış tikini bastır
        static volatile boolean enabled = true;
        private static int profile = LauncherUiModels.SOUND_OFF;

        static void setEnabled(boolean value) {
            enabled = value;
            if (!enabled) ready = false;
        }

        static void setProfile(int mode) {
            profile = LauncherUiModels.normalizeSound(mode);
            release();
        }

        static void init() {
            if (!enabled) return;
            if (focus != null) return;
            if (profile == LauncherUiModels.SOUND_CINEMATIC) {
                focus = tone(new double[][]{{1320, 1.0}, {1980, 0.34}}, 48, 0.34f, 0.010);
                press = tone(new double[][]{{740, 1.0}, {370, 0.52}}, 88, 0.50f, 0.020);
                back  = sweep(720, 280, 140, 0.44f);
                open  = chime(new double[]{659, 988}, 270, 0.46f);
            } else {
                // Yumuşak "tik" — ana ton + zayıf üst kısmi, çok hızlı sönüm.
                focus = tone(new double[][]{{1568, 1.0}, {2350, 0.30}}, 45, 0.28f, 0.007);
                // Basma "tok" — gövdeli alt oktav ile.
                press = tone(new double[][]{{784, 1.0}, {392, 0.45}}, 80, 0.42f, 0.018);
                // Geri — alçalan süpürme.
                back  = sweep(700, 320, 130, 0.40f);
                // Açılış — yükselen iki tonlu yumuşak zil.
                open  = chime(new double[]{659, 988}, 260, 0.42f);
            }
        }

        /** Çok kısmi tonlu vuruş: partials = {frekans, genlik} çiftleri. */
        private static AudioTrack tone(double[][] partials, int durMs, float vol, double decay) {
            int n = SR * durMs / 1000;
            short[] buf = new short[n];
            for (int i = 0; i < n; i++) {
                double t = (double) i / SR;
                double env = Math.min(1.0, t / 0.003)
                           * Math.exp(-Math.max(0, t - 0.003) / decay);
                double s = 0;
                for (double[] p : partials) s += Math.sin(2 * Math.PI * p[0] * t) * p[1];
                buf[i] = (short) (s / partials.length * env * vol * Short.MAX_VALUE);
            }
            return makeTrack(buf);
        }

        private static AudioTrack sweep(double f0, double f1, int durMs, float vol) {
            int n = SR * durMs / 1000;
            short[] buf = new short[n];
            double durSec = durMs / 1000.0;
            for (int i = 0; i < n; i++) {
                double t = (double) i / SR;
                double f = f0 + (f1 - f0) * (t / durSec);
                double env = Math.min(1.0, t / 0.004) * Math.exp(-t * 6);
                buf[i] = (short) (Math.sin(2 * Math.PI * f * t) * env * vol * Short.MAX_VALUE);
            }
            return makeTrack(buf);
        }

        /** Sırayla çalan iki tonlu zil (uygulama açılışı). */
        private static AudioTrack chime(double[] freqs, int durMs, float vol) {
            int n = SR * durMs / 1000;
            short[] buf = new short[n];
            double durSec = durMs / 1000.0;
            double half = durSec * 0.45;
            for (int i = 0; i < n; i++) {
                double t = (double) i / SR;
                double s;
                if (t < half) {
                    s = Math.sin(2 * Math.PI * freqs[0] * t) * Math.exp(-t * 8);
                } else {
                    double t2 = t - half;
                    s = Math.sin(2 * Math.PI * freqs[0] * t) * Math.exp(-t * 8) * 0.3
                      + Math.sin(2 * Math.PI * freqs[1] * t2) * Math.exp(-t2 * 6);
                }
                double env = Math.min(1.0, t / 0.004);
                buf[i] = (short) (s * env * vol * Short.MAX_VALUE);
            }
            return makeTrack(buf);
        }

        private static AudioTrack makeTrack(short[] buf) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            AudioFormat fmt = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SR)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
            AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(buf.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
            t.write(buf, 0, buf.length);
            return t;
        }

        static void focus() { play(focus); }
        static void press() { play(press); }
        static void back()  { play(back); }
        static void open()  { play(open); }

        private static void play(AudioTrack t) {
            if (!enabled || t == null || !ready) return;
            try {
                t.stop();
                t.reloadStaticData();
                t.play();
            } catch (Exception ignored) {}
        }

        static void release() {
            for (AudioTrack t : new AudioTrack[]{ focus, press, back, open }) {
                if (t != null) try { t.release(); } catch (Exception ignored) {}
            }
            focus = press = back = open = null;
        }
    }
}
