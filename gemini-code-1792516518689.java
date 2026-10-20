package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TvHomeActivity extends FragmentActivity {

    private ImageView heroBannerPrimary;
    private ImageView heroBannerSecondary;
    private TextView heroTitleView;
    private TextView heroSubtitleView;
    private View heroAccentGlow;

    private final Handler heroSwapHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingHeroRunnable;
    private String currentHeroPackage = "";

    private static final long HERO_DEBOUNCE_MS = 110L;
    private static final long HERO_CROSSFADE_MS = 380L;

    // =========================================================================
    // 1. DAHİLİ UYGULAMA VERİ VE GÖRSEL KAYIT DEFTERİ (REGISTRY)
    // =========================================================================
    public static class AppVisualSpec {
        public final String cardDrawableName;
        public final String heroDrawableName;
        public final String heroTitle;
        public final String heroSubtitle;
        public final int accentColor;

        public AppVisualSpec(String cardDrawableName, String heroDrawableName,
                             String heroTitle, String heroSubtitle, int accentColor) {
            this.cardDrawableName = cardDrawableName;
            this.heroDrawableName = heroDrawableName;
            this.heroTitle = heroTitle;
            this.heroSubtitle = heroSubtitle;
            this.accentColor = accentColor;
        }
    }

    private static final Map<String, AppVisualSpec> REGISTRY;

    static {
        Map<String, AppVisualSpec> map = new HashMap<>();

        map.put("com.stremio.one", new AppVisualSpec(
                "card_stremio", "hero_stremio", "Stremio",
                "4K HDR Sinema • Tüm Kütüphaneniz ve Eklentileriniz Tek Merkezde", 0xFF8A5CF5));

        map.put("com.plexapp.android", new AppVisualSpec(
                "card_plex", "hero_plex", "Plex Media Server",
                "Kişisel Medya Sunucunuz • Doğrudan Kayıpsız Akış", 0xFFE5A00D));

        map.put("com.turkcell.ott", new AppVisualSpec(
                "card_tvplus", "hero_tvplus", "TV+",
                "Canlı TV Kanalları, Spor Karşılaşmaları ve Seçkin Filmler", 0xFFFFC900));

        map.put("com.amazon.amazonvideo.livingroom", new AppVisualSpec(
                "card_prime", "hero_prime", "Prime Video",
                "Amazon Orijinal Yapımları • Dolby Vision ve Atmos Deneyimi", 0xFF00A8E1));

        map.put("com.apple.atve.androidtv.appletv", new AppVisualSpec(
                "card_appletv", "hero_appletv", "Apple TV+",
                "Apple Originals • Sinematik Başyapıtlar ve Özel İçerikler", 0xFFD2D2D7));

        map.put("com.spotify.tv.android", new AppVisualSpec(
                "card_spotify", "hero_spotify", "Spotify",
                "Milyonlarca Şarkı ve Çalma Listesi • Kayıpsız Oturma Odası Akustiği", 0xFF1DB954));

        map.put("com.google.android.videos", new AppVisualSpec(
                "card_gtv_videos", "hero_gtv_videos", "Google TV Filmler",
                "Satın Aldığınız ve Kiraladığınız Filmler • Kişisel Arşiviniz", 0xFF4285F4));

        map.put("com.sportzx.live", new AppVisualSpec(
                "card_sportzx", "hero_sportzx", "SportzX Live",
                "Canlı Spor Yayınları • Stadyum Atmosferi ve Anlık Maç Akışı", 0xFFFF2D55));

        map.put("com.nuvio.app", new AppVisualSpec(
                "card_nuvio", "hero_nuvio", "Nuvio",
                "Yeni Nesil Dijital Yayın ve Medya Oynatma Platformu", 0xFF00D4B2));

        map.put("za.kilowatch.ultimatefilemanager", new AppVisualSpec(
                "card_filemanager", "hero_filemanager", "Ultimate File Manager",
                "Sistem Depolama Yöneticisi • APK Kurulumu ve Dosya Dizini", 0xFF32ADE6));

        map.put("com.tcl.browser", new AppVisualSpec(
                "card_tcl_browser", "hero_tcl_browser", "Web Tarayıcı",
                "Büyük Ekran İçin Optimize Edilmiş Hızlı İnternet Deneyimi", 0xFF0A84FF));

        map.put("com.neilturner.aerialviews", new AppVisualSpec(
                "card_aerialviews", "hero_aerialviews", "Aerial Views",
                "4K Dolby Vision Drone Ekran Koruyucuları • Doğa ve Şehir Manzaraları", 0xFFFF9F0A));

        map.put("org.localsend.localsend_app", new AppVisualSpec(
                "card_localsend", "hero_localsend", "LocalSend",
                "Yerel Ağ Üzerinden Yüksek Hızlı Kablosuz Dosya ve APK Transferi", 0xFF30D158));

        REGISTRY = Collections.unmodifiableMap(map);
    }

    // =========================================================================
    // 2. ACTIVITY YAŞAM DÖNGÜSÜ VE PROGRAMATİK ARKA PLAN KURULUMU
    // =========================================================================
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_home);

        heroBannerPrimary = findViewById(R.id.hero_banner_primary);
        heroBannerSecondary = findViewById(R.id.hero_banner_secondary);
        heroTitleView = findViewById(R.id.hero_title);
        heroSubtitleView = findViewById(R.id.hero_subtitle);
        heroAccentGlow = findViewById(R.id.hero_accent_glow);

        setupProgrammaticDrawables();

        RecyclerView appsRecyclerView = findViewById(R.id.apps_recycler_view);
        appsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        List<String> myTvApps = new ArrayList<>(Arrays.asList(
                "com.stremio.one",
                "com.plexapp.android",
                "com.turkcell.ott",
                "com.amazon.amazonvideo.livingroom",
                "com.apple.atve.androidtv.appletv",
                "com.spotify.tv.android",
                "com.sportzx.live",
                "com.nuvio.app",
                "com.neilturner.aerialviews",
                "org.localsend.localsend_app",
                "za.kilowatch.ultimatefilemanager",
                "com.tcl.browser",
                "com.google.android.videos"
        ));

        appsRecyclerView.setAdapter(new TvOsAppAdapter(myTvApps));
    }

    /**
     * Harici XML drawable dosyalarına ihtiyaç duymamak için tüm gradyan ve
     * buzlu cam yüzeyleri çalışma zamanında oluşturur.
     */
    private void setupProgrammaticDrawables() {
        // 1. Soldan Sağa Hero Karartması
        View scrimLeft = findViewById(R.id.hero_scrim_left);
        GradientDrawable leftGrad = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE608090C, 0x6608090C, 0x0008090C});
        scrimLeft.setBackground(leftGrad);

        // 2. Aşağıdan Yukarıya Hero Karartması
        View scrimBottom = findViewById(R.id.hero_scrim_bottom);
        GradientDrawable bottomGrad = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFF08090C, 0x5908090C, 0x0008090C});
        scrimBottom.setBackground(bottomGrad);

        // 3. Rozet (Pill Badge) Arka Planı
        View badge = findViewById(R.id.hero_badge);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0x26FFFFFF);
        pillBg.setCornerRadius(dp(50));
        pillBg.setStroke(dp(1), 0x38FFFFFF);
        badge.setBackground(pillBg);

        // 4. Buzlu Cam Dock Arka Planı
        View frostedDock = findViewById(R.id.tvos_frosted_dock);
        GradientDrawable dockBg = new GradientDrawable();
        dockBg.setColor(0x421C202B);
        dockBg.setCornerRadius(dp(28));
        dockBg.setStroke(dp(1), 0x28FFFFFF);
        frostedDock.setBackground(dockBg);

        // 5. İlk Ambiyans Işığı Kurulumu
        updateAccentGlowColor(0xFF8A5CF5);
    }

    private void updateAccentGlowColor(int accentColor) {
        if (heroAccentGlow == null) return;
        int alphaCenter = (accentColor & 0x00FFFFFF) | 0xCC000000;
        int alphaMid = (accentColor & 0x00FFFFFF) | 0x55000000;
        GradientDrawable radial = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{alphaCenter, alphaMid, Color.TRANSPARENT});
        radial.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        radial.setGradientRadius(dp(230));
        heroAccentGlow.setBackground(radial);
    }

    // =========================================================================
    // 3. KART BAĞLAMA, HERO BANNER GEÇİŞİ VE 3D FOCUS FEEL
    // =========================================================================
    private void bindAppCard(String packageName, CardView cardSurface, ImageView cardImageView,
                             View focusBorderView, TextView cardLabelView) {
        PackageManager pm = getPackageManager();
        AppVisualSpec spec = REGISTRY.get(packageName);

        if (spec != null) {
            cardLabelView.setText(spec.heroTitle);
            int customCardRes = getDrawableIdByName(spec.cardDrawableName);
            if (customCardRes != 0) {
                cardImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cardImageView.setImageResource(customCardRes);
            } else {
                // Henüz webp dosyası atılmadıysa şık gradyan + sistem ikonu göster
                applyFallbackCardVisual(pm, packageName, cardImageView, spec.accentColor);
            }
        } else {
            applyFallbackCardVisual(pm, packageName, cardImageView, 0xFF2C3242);
            try {
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                cardLabelView.setText(pm.getApplicationLabel(ai));
            } catch (PackageManager.NameNotFoundException e) {
                cardLabelView.setText(packageName);
            }
        }

        cardSurface.setOnFocusChangeListener((v, hasFocus) -> {
            applyTvOsFocusFeel(v, focusBorderView, hasFocus);
            cardLabelView.setTextColor(hasFocus ? 0xFFFFFFFF : 0x99EBEBF5);
            cardLabelView.setAlpha(hasFocus ? 1.0f : 0.72f);
            if (hasFocus) {
                scheduleHeroBannerUpdate(packageName);
            }
        });
    }

    private void applyFallbackCardVisual(PackageManager pm, String packageName, ImageView imageView, int accentColor) {
        int darkStart = 0xFF12141C;
        int midTone = (accentColor & 0x00FFFFFF) | 0x55000000;
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{midTone, darkStart});
        imageView.setBackground(bg);

        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            Drawable banner = pm.getApplicationBanner(appInfo);
            if (banner != null) {
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setPadding(0, 0, 0, 0);
                imageView.setImageDrawable(banner);
            } else {
                imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                int pad = dp(24);
                imageView.setPadding(pad, pad, pad, pad);
                imageView.setImageDrawable(pm.getApplicationIcon(appInfo));
            }
        } catch (PackageManager.NameNotFoundException e) {
            imageView.setImageDrawable(null);
        }
    }

    private void scheduleHeroBannerUpdate(String packageName) {
        if (packageName.equals(currentHeroPackage)) return;

        if (pendingHeroRunnable != null) {
            heroSwapHandler.removeCallbacks(pendingHeroRunnable);
        }

        pendingHeroRunnable = () -> {
            currentHeroPackage = packageName;
            AppVisualSpec spec = REGISTRY.get(packageName);

            if (spec != null) {
                int heroRes = getDrawableIdByName(spec.heroDrawableName);
                crossfadeHeroBanner(heroRes, spec.accentColor);
                animateHeroText(spec.heroTitle, spec.heroSubtitle);
                updateAccentGlowColor(spec.accentColor);
            } else {
                crossfadeHeroBanner(0, 0xFF1E2436);
                try {
                    PackageManager pm = getPackageManager();
                    ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                    animateHeroText(pm.getApplicationLabel(ai).toString(), "Android TV Uygulaması");
                } catch (PackageManager.NameNotFoundException ignored) {
                    animateHeroText(packageName, "Android TV Uygulaması");
                }
            }
        };

        heroSwapHandler.postDelayed(pendingHeroRunnable, HERO_DEBOUNCE_MS);
    }

    private void crossfadeHeroBanner(int newDrawableRes, int fallbackAccentColor) {
        if (newDrawableRes != 0) {
            heroBannerSecondary.setBackground(null);
            heroBannerSecondary.setImageResource(newDrawableRes);
        } else {
            // Görsel dosyası henüz eklenmediyse sinematik koyu radial arka plan üretir
            heroBannerSecondary.setImageDrawable(null);
            int topRightColor = (fallbackAccentColor & 0x00FFFFFF) | 0x66000000;
            GradientDrawable fallbackHero = new GradientDrawable(
                    GradientDrawable.Orientation.TR_BL,
                    new int[]{topRightColor, 0xFF10131C, 0xFF08090C});
            heroBannerSecondary.setBackground(fallbackHero);
        }

        heroBannerSecondary.setAlpha(0f);
        heroBannerSecondary.setScaleX(1.04f);
        heroBannerSecondary.setScaleY(1.04f);
        heroBannerSecondary.setVisibility(View.VISIBLE);

        heroBannerSecondary.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(HERO_CROSSFADE_MS)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (newDrawableRes != 0) {
                            heroBannerPrimary.setBackground(null);
                            heroBannerPrimary.setImageResource(newDrawableRes);
                        } else {
                            heroBannerPrimary.setImageDrawable(null);
                            heroBannerPrimary.setBackground(heroBannerSecondary.getBackground());
                        }
                        heroBannerPrimary.setAlpha(1f);
                        heroBannerSecondary.setVisibility(View.INVISIBLE);
                    }
                })
                .start();
    }

    private void animateHeroText(String title, String subtitle) {
        heroTitleView.animate().alpha(0f).translationY(dp(6)).setDuration(110).withEndAction(() -> {
            heroTitleView.setText(title);
            heroTitleView.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }).start();

        heroSubtitleView.animate().alpha(0f).translationY(dp(6)).setDuration(110).withEndAction(() -> {
            heroSubtitleView.setText(subtitle);
            heroSubtitleView.animate().alpha(0.85f).translationY(0f).setDuration(250).start();
        }).start();
    }

    private void applyTvOsFocusFeel(View cardView, View focusBorderView, boolean hasFocus) {
        float targetScale = hasFocus ? 1.12f : 1.0f;
        float targetElevation = hasFocus ? dp(24) : dp(5);

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(18));
        ring.setStroke(dp(hasFocus ? 3 : 1), hasFocus ? 0xF2FFFFFF : 0x1AFFFFFF);
        focusBorderView.setBackground(ring);

        cardView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationZ(targetElevation)
                .setDuration(hasFocus ? 210 : 160)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    private int getDrawableIdByName(String resName) {
        if (resName == null || resName.isEmpty()) return 0;
        return getResources().getIdentifier(resName, "drawable", getPackageName());
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    // =========================================================================
    // 4. DAHİLİ RECYCLERVIEW ADAPTER VE PROGRAMATİK KART OLUŞTURUCU
    // =========================================================================
    private class TvOsAppAdapter extends RecyclerView.Adapter<TvOsAppAdapter.AppViewHolder> {

        private final List<String> packageList;

        TvOsAppAdapter(List<String> packageList) {
            this.packageList = packageList;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return packageList.get(position).hashCode();
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();

            // Ana Kapsayıcı (LinearLayout)
            LinearLayout root = new LinearLayout(ctx);
            RecyclerView.LayoutParams rootParams = new RecyclerView.LayoutParams(dp(216), ViewGroup.LayoutParams.WRAP_CONTENT);
            rootParams.setMargins(dp(10), dp(10), dp(10), dp(10));
            root.setLayoutParams(rootParams);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setClipChildren(false);
            root.setClipToPadding(false);

            // 16:9 tvOS CardView Yüzeyi
            CardView cardSurface = new CardView(ctx);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(200), dp(113));
            cardSurface.setLayoutParams(cardParams);
            cardSurface.setRadius(dp(18));
            cardSurface.setCardElevation(dp(5));
            cardSurface.setCardBackgroundColor(0xFF14161E);
            cardSurface.setPreventCornerOverlap(true);
            cardSurface.setFocusable(true);
            cardSurface.setFocusableInTouchMode(true);
            cardSurface.setClickable(true);

            // Kart Görseli
            ImageView cardImage = new ImageView(ctx);
            cardImage.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            cardImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

            // Cam Yansıması (Specular Sheen)
            View sheenView = new View(ctx);
            sheenView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            GradientDrawable sheenGrad = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{0x24FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
            sheenGrad.setCornerRadius(dp(18));
            sheenView.setBackground(sheenGrad);

            // Odak Çerçevesi Katmanı
            View focusBorder = new View(ctx);
            focusBorder.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            cardSurface.addView(cardImage);
            cardSurface.addView(sheenView);
            cardSurface.addView(focusBorder);

            // Kart Altı Uygulama Başlığı
            TextView cardLabel = new TextView(ctx);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(10);
            cardLabel.setLayoutParams(labelParams);
            cardLabel.setGravity(Gravity.CENTER);
            cardLabel.setMaxLines(1);
            cardLabel.setEllipsize(TextUtils.TruncateAt.END);
            cardLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            cardLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            cardLabel.setTextColor(0x99EBEBF5);
            cardLabel.setShadowLayer(6f, 0f, 2f, 0xB3000000);

            root.addView(cardSurface);
            root.addView(cardLabel);

            return new AppViewHolder(root, cardSurface, cardImage, focusBorder, cardLabel);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            String packageName = packageList.get(position);

            bindAppCard(packageName, holder.cardSurface, holder.cardImage, holder.focusBorder, holder.cardLabel);

            // Kumanda OK Tuşu Basılma (Tactile Press) Efekti
            holder.cardSurface.setOnKeyListener((v, keyCode, event) -> {
                if (isConfirmKey(keyCode)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(80).start();
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(110).start();
                    }
                }
                return false;
            });

            // Uygulamayı Aç
            holder.cardSurface.setOnClickListener(v -> launchAppSafely(v.getContext(), packageName));

            // İlk açılışta ilk karta odaklan
            if (position == 0 && currentHeroPackage.isEmpty()) {
                holder.cardSurface.post(holder.cardSurface::requestFocus);
            }
        }

        @Override
        public int getItemCount() {
            return packageList.size();
        }

        private boolean isConfirmKey(int keyCode) {
            return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A;
        }

        private void launchAppSafely(Context context, String packageName) {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName);

            if (launchIntent == null) {
                launchIntent = pm.getLaunchIntentForPackage(packageName);
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    context.startActivity(launchIntent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                } catch (Exception e) {
                    Toast.makeText(context, "Başlatılamadı: " + packageName, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, "Uygulama bulunamadı: " + packageName, Toast.LENGTH_SHORT).show();
            }
        }

        class AppViewHolder extends RecyclerView.ViewHolder {
            final CardView cardSurface;
            final ImageView cardImage;
            final View focusBorder;
            final TextView cardLabel;

            AppViewHolder(@NonNull View itemView, CardView cardSurface, ImageView cardImage,
                          View focusBorder, TextView cardLabel) {
                super(itemView);
                this.cardSurface = cardSurface;
                this.cardImage = cardImage;
                this.focusBorder = focusBorder;
                this.cardLabel = cardLabel;
            }
        }
    }
}