package com.edge.cityray.themephase1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressLint("SdCardPath")
public final class MainActivity extends Activity {
    private static final int PICK_VIDEO = 1002;
    private static final int PICK_GALLERY_VIDEO = 1003;
    private static final int REQUEST_HU_MEDIA = 2001;
    private static final int HISTORY_SLOTS = 3;
    private static final String HISTORY_PATH = "applied_history_path_";
    private static final String HISTORY_NAME = "applied_history_name_";
    private static final String VERIFIED_RESTORE_SOURCE =
            "/sdcard/EDGEThemePhase1/original/verified_restore_source.png";
    private static final String KNOWN_RESTORE_SOURCE = "/sdcard/DCIM/Camera/CityRay.png";

    private static final int BG = Color.rgb(2, 7, 13);
    private static final int SURFACE = Color.rgb(7, 19, 31);
    private static final int SURFACE_HIGH = Color.rgb(10, 28, 43);
    private static final int CYAN = Color.rgb(38, 216, 255);
    private static final int CYAN_PRESS = Color.rgb(124, 237, 255);
    private static final int BLUE = Color.rgb(45, 118, 255);
    private static final int TEXT = Color.rgb(246, 250, 255);
    private static final int MUTED = Color.rgb(158, 183, 202);
    private static final int SUCCESS = Color.rgb(97, 255, 193);
    private static final int WARNING = Color.rgb(255, 190, 120);

    private Uri selectedVideoUri;
    private String selectedVideoName;
    private CropVideoView videoPreview;
    private TextView emptyPreview;
    private TextView videoName;
    private TextView videoDetails;
    private TextView status;
    private TextView statusDot;
    private Button applyButton;
    private ProgressBar progress;
    private AccentFrameLayout previewFrame;
    private LinearLayout recentWallpaperStrip;
    private LinearLayout dashboardThemeGrid;
    private HorizontalScrollView dashboardThemePager;
    private TextView dashboardPageIndicator;
    private Button dashboardPreviousPage;
    private Button dashboardNextPage;
    private int dashboardThemePage;
    private boolean activationFlowOpened;
    private BuiltInAdbBridge builtInAdb;
    private DashboardThemeBridge dashboardThemeBridge;
    private int selectedDashboardTheme;
    private int dualAccessFailures;
    private long dualAccessLockoutUntil;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Exclusive artwork is session-gated: every fresh app launch starts locked.
        DualPreviewAccess.lock(this);
        builtInAdb = new BuiltInAdbBridge(this);
        dashboardThemeBridge = new DashboardThemeBridge(this);
        selectedDashboardTheme = getSharedPreferences("edge_dashboard_themes", MODE_PRIVATE)
                .getInt("selected_factory_theme", 0);
        if (selectedDashboardTheme < 1 || selectedDashboardTheme > 6) {
            selectedDashboardTheme = 0;
            getSharedPreferences("edge_dashboard_themes", MODE_PRIVATE).edit()
                    .remove("selected_factory_theme")
                    .apply();
        }
        FrameLayout scene = buildScene();
        setContentView(scene);
        restoreSelectedVideo();
        seedCurrentWallpaperHistory();
        importVideoFromIntent(getIntent());
        refreshUi();
        showBootReveal(scene);
        if (getIntent().getBooleanExtra("edge_activate_live", false)) {
            applyFromIntent();
        }
        if (getIntent().getBooleanExtra("edge_return_to_geely", false)) {
            returnToGeelyWallpapers();
        }
        if (getIntent().getBooleanExtra("edge_show_dual_access", false)) {
            scene.postDelayed(this::showDualScreenAccess, 450L);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedVideoUri != null && videoPreview != null) {
            videoPreview.start();
        }
        if (activationFlowOpened) {
            activationFlowOpened = false;
            status.postDelayed(this::finishActivationCheck, 700);
        }
    }

    @Override
    protected void onPause() {
        if (videoPreview != null && videoPreview.isPlaying()) videoPreview.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (videoPreview != null) videoPreview.release();
        super.onDestroy();
    }

    private FrameLayout buildScene() {
        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(BG);
        scene.addView(new CyberGridView(this), new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scene.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(38), dp(56), dp(54));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(buildHeader(), row(-1, -2, 0, 0, 0, 30));
        root.addView(buildWallpaperDeck(), row(-1, -2, 0, 0, 0, 24));
        root.addView(buildControls(), row(-1, -2, 0, 0, 0, 22));
        root.addView(buildDashboardThemes(), row(-1, -2, 0, 0, 0, 22));
        root.addView(buildRecovery(), row(-1, -2, 0, 0, 0, 18));
        return scene;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.edge_cityray_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("EDGE CityRay Live Wallpaper logo");
        header.addView(logo, new LinearLayout.LayoutParams(dp(132), dp(132)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(26), 0, 0, 0);
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView product = text("EDGE CITYRAY", 31, TEXT, Typeface.BOLD);
        product.setLetterSpacing(0.08f);
        copy.addView(product, row(-1, -2, 0, 0, 0, 5));
        TextView system = text("LIVE WALLPAPER SYSTEM", 17, CYAN, Typeface.BOLD);
        system.setLetterSpacing(0.14f);
        copy.addView(system, row(-1, -2, 0, 0, 0, 0));

        LinearLayout state = new LinearLayout(this);
        state.setOrientation(LinearLayout.HORIZONTAL);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(20), 0, dp(20), 0);
        state.setBackground(new CornerCutDrawable(Color.rgb(4, 36, 45), CYAN, dp(1), dp(12)));
        TextView dot = text("●", 15, SUCCESS, Typeface.BOLD);
        state.addView(dot, new LinearLayout.LayoutParams(-2, -2));
        TextView label = text("  MAIN HU", 15, TEXT, Typeface.BOLD);
        label.setLetterSpacing(0.09f);
        state.addView(label, new LinearLayout.LayoutParams(-2, -2));
        header.addView(state, new LinearLayout.LayoutParams(dp(200), dp(58)));
        return header;
    }

    private View buildWallpaperDeck() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.addView(text("WALLPAPER DECK", 18, TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView mode = text("3 VERTICAL PREVIEWS  •  LIVE + RECENT", 15, CYAN,
                Typeface.BOLD);
        mode.setLetterSpacing(0.08f);
        labelRow.addView(mode);
        section.addView(labelRow, row(-1, -2, 0, 0, 0, 14));

        LinearLayout deckRow = new LinearLayout(this);
        deckRow.setOrientation(LinearLayout.HORIZONTAL);
        section.addView(deckRow, row(-1, dp(520), 0, 0, 0, 0));

        previewFrame = new AccentFrameLayout(this);
        previewFrame.setBeamEnabled(true);
        previewFrame.setPadding(dp(5), dp(5), dp(5), dp(5));
        previewFrame.setBackground(new CornerCutDrawable(SURFACE, Color.rgb(28, 74, 99),
                dp(1), dp(26)));
        deckRow.addView(previewFrame, weightedRow(1f, -1, 0, 0, 8, 0));

        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        previewFrame.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        videoPreview = new CropVideoView(this);
        videoPreview.setContentDescription("Selected live wallpaper video preview");
        stage.addView(videoPreview, new FrameLayout.LayoutParams(-1, -1));

        emptyPreview = text("SELECT A VIDEO\nTO INITIALIZE LIVE PREVIEW", 21, MUTED, Typeface.BOLD);
        emptyPreview.setGravity(Gravity.CENTER);
        emptyPreview.setLetterSpacing(0.08f);
        stage.addView(emptyPreview, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(18), dp(14), dp(18), dp(16));
        info.setBackgroundColor(Color.argb(222, 2, 9, 16));
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                -1, dp(112), Gravity.BOTTOM);
        stage.addView(info, infoParams);
        videoName = text("NO WALLPAPER SELECTED", 16, TEXT, Typeface.BOLD);
        videoName.setSingleLine(true);
        videoName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        info.addView(videoName, row(-1, -2, 0, 0, 0, 5));
        videoDetails = text("CHOOSE AN MP4 FROM HU GALLERY, USB OR FILES", 12, MUTED,
                Typeface.NORMAL);
        videoDetails.setLetterSpacing(0.05f);
        videoDetails.setSingleLine(true);
        videoDetails.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(videoDetails);

        recentWallpaperStrip = new LinearLayout(this);
        recentWallpaperStrip.setOrientation(LinearLayout.HORIZONTAL);
        deckRow.addView(recentWallpaperStrip, weightedRow(2f, -1, 8, 0, 0, 0));
        return section;
    }

    private View buildControls() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(30), dp(28), dp(30), dp(28));
        panel.setBackground(new CornerCutDrawable(SURFACE_HIGH, Color.rgb(26, 70, 94),
                dp(1), dp(24)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(buttons, row(-1, -2, 0, 0, 0, 20));

        Button browse = actionButton("CHOOSE VIDEO", false, v -> showVideoSourcePicker());
        buttons.addView(browse, weightedRow(1f, dp(84), 0, 0, 10, 0));
        applyButton = actionButton("ACTIVATE / UPDATE MAIN HU  →", true,
                v -> previewLiveWallpaper());
        buttons.addView(applyButton, weightedRow(1.45f, dp(84), 10, 0, 0, 0));

        LinearLayout statusLine = new LinearLayout(this);
        statusLine.setOrientation(LinearLayout.HORIZONTAL);
        statusLine.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(statusLine, new LinearLayout.LayoutParams(-1, dp(46)));
        statusDot = text("●", 16, SUCCESS, Typeface.BOLD);
        statusLine.addView(statusDot, new LinearLayout.LayoutParams(dp(30), -2));
        status = text("SYSTEM READY", 16, MUTED, Typeface.BOLD);
        status.setLetterSpacing(0.08f);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusLine.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(CYAN));
        progress.setVisibility(View.GONE);
        statusLine.addView(progress, new LinearLayout.LayoutParams(dp(230), dp(8)));
        return panel;
    }

    private View buildRecovery() {
        LinearLayout recovery = new LinearLayout(this);
        recovery.setOrientation(LinearLayout.HORIZONTAL);
        recovery.setGravity(Gravity.CENTER_VERTICAL);
        recovery.setPadding(dp(28), dp(20), dp(28), dp(20));
        recovery.setBackground(new CornerCutDrawable(Color.rgb(5, 16, 27),
                Color.rgb(32, 61, 82), dp(1), dp(18)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        recovery.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        copy.addView(text("FACTORY RECOVERY", 16, TEXT, Typeface.BOLD), row(-1, -2, 0, 0, 0, 4));
        copy.addView(text("Return control to the original Geely wallpaper menu", 15, MUTED,
                Typeface.NORMAL));
        Button factory = actionButton("FACTORY WALLPAPERS", false,
                v -> returnToGeelyWallpapers());
        recovery.addView(factory, new LinearLayout.LayoutParams(dp(330), dp(70)));
        return recovery;
    }

    private View buildDashboardThemes() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(28), dp(24), dp(28), dp(28));
        section.setBackground(new CornerCutDrawable(SURFACE_HIGH,
                Color.rgb(28, 91, 121), dp(1), dp(24)));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(text("DUAL-SCREEN THEMES", 18, TEXT, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView nativeLabel = text("MAIN HU + DASHBOARD", 13, CYAN,
                Typeface.BOLD);
        nativeLabel.setLetterSpacing(.08f);
        titleRow.addView(nativeLabel);
        section.addView(titleRow, row(-1, -2, 0, 0, 0, 5));
        section.addView(text("Preview the real Geely pair, then control each screen separately",
                15, MUTED, Typeface.NORMAL), row(-1, -2, 0, 0, 0, 16));

        Button factorySelector = actionButton("DUAL-WALLPAPER SELECTOR", false,
                v -> showDualScreenAccess());
        factorySelector.setContentDescription(
                "Open EDGE dual-screen access and QR artwork");
        section.addView(factorySelector, row(-1, dp(72), 0, 0, 0, 18));

        dashboardThemePager = new HorizontalScrollView(this);
        dashboardThemePager.setFillViewport(true);
        dashboardThemePager.setHorizontalScrollBarEnabled(false);
        dashboardThemePager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dashboardThemePager.setContentDescription(
                "Swipe left or right through dual-screen theme pages");
        dashboardThemePager.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                int width = Math.max(1, dashboardThemePager.getWidth());
                int page = Math.round(dashboardThemePager.getScrollX() / (float) width);
                dashboardThemePager.post(() -> scrollToDashboardThemePage(page, true));
            }
            return false;
        });
        section.addView(dashboardThemePager, row(-1, dp(558), 0, 0, 0, 12));

        dashboardThemeGrid = new LinearLayout(this);
        dashboardThemeGrid.setOrientation(LinearLayout.HORIZONTAL);
        dashboardThemePager.addView(dashboardThemeGrid,
                new HorizontalScrollView.LayoutParams(-2, -1));

        LinearLayout paging = new LinearLayout(this);
        paging.setOrientation(LinearLayout.HORIZONTAL);
        paging.setGravity(Gravity.CENTER_VERTICAL);
        dashboardPreviousPage = actionButton("‹", false,
                v -> scrollToDashboardThemePage(dashboardThemePage - 1, true));
        dashboardPreviousPage.setContentDescription("Previous dual-screen theme page");
        paging.addView(dashboardPreviousPage,
                new LinearLayout.LayoutParams(dp(72), dp(58)));
        dashboardPageIndicator = text("SWIPE TO EXPLORE  •  1 / 3", 13, MUTED,
                Typeface.BOLD);
        dashboardPageIndicator.setGravity(Gravity.CENTER);
        dashboardPageIndicator.setLetterSpacing(.08f);
        paging.addView(dashboardPageIndicator,
                new LinearLayout.LayoutParams(0, dp(58), 1f));
        dashboardNextPage = actionButton("›", false,
                v -> scrollToDashboardThemePage(dashboardThemePage + 1, true));
        dashboardNextPage.setContentDescription("Next dual-screen theme page");
        paging.addView(dashboardNextPage,
                new LinearLayout.LayoutParams(dp(72), dp(58)));
        section.addView(paging, row(-1, dp(58), 0, 0, 0, 0));
        refreshDashboardThemeGrid();
        return section;
    }

    private void refreshDashboardThemeGrid() {
        if (dashboardThemeGrid == null) return;
        dashboardThemeGrid.removeAllViews();
        String[] names = {"WATER BALL", "BLUE HORIZON", "FLUID",
                "GEOMETRY", "GEOMETRY MINIMAL", "MONUMENT VALLEY",
                "MOROCCO HORIZON", "ALPINE MIRROR", "NEON CITY"};
        int[] accents = {CYAN, Color.rgb(65, 143, 255), Color.rgb(77, 202, 255),
                Color.rgb(170, 220, 255), Color.rgb(184, 133, 255),
                Color.rgb(78, 160, 255), Color.rgb(46, 197, 255),
                Color.rgb(92, 168, 255), Color.rgb(255, 72, 224)};
        int pageWidth = Math.max(dp(620),
                getResources().getDisplayMetrics().widthPixels - dp(168));
        final int pageCount = 3;
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                for (int column = 0; column < 2; column++) {
                    int itemIndex = pageIndex * 4 + rowIndex * 2 + column;
                    int left = column == 0 ? 0 : 7;
                    int right = column == 1 ? 0 : 7;
                    View card;
                    if (itemIndex < names.length) {
                        int themeId = itemIndex + 1;
                        card = buildDashboardThemeCard(themeId, names[itemIndex],
                                accents[itemIndex], themeId == selectedDashboardTheme);
                    } else if (itemIndex == names.length) {
                        card = buildDualScreenVisionCard();
                    } else {
                        card = new View(this);
                    }
                    rowLayout.addView(card,
                            weightedRow(1f, dp(266), left, 0, right, 0));
                }
                page.addView(rowLayout,
                        row(-1, dp(266), 0, rowIndex == 0 ? 0 : 14, 0, 0));
            }
            dashboardThemeGrid.addView(page,
                    new LinearLayout.LayoutParams(pageWidth, dp(546)));
        }
        if (selectedDashboardTheme > 0 && dashboardThemePage == 0) {
            dashboardThemePage = Math.min(2, (selectedDashboardTheme - 1) / 4);
        }
        dashboardThemePager.post(() -> scrollToDashboardThemePage(
                dashboardThemePage, false));
    }

    private void scrollToDashboardThemePage(int requestedPage, boolean smooth) {
        dashboardThemePage = Math.max(0, Math.min(2, requestedPage));
        if (dashboardThemePager != null) {
            int target = dashboardThemePage * Math.max(1, dashboardThemePager.getWidth());
            if (smooth) dashboardThemePager.smoothScrollTo(target, 0);
            else dashboardThemePager.scrollTo(target, 0);
        }
        if (dashboardPageIndicator != null) {
            dashboardPageIndicator.setText(String.format(Locale.US,
                    "SWIPE TO EXPLORE  •  %d / 3", dashboardThemePage + 1));
        }
        if (dashboardPreviousPage != null) {
            dashboardPreviousPage.setEnabled(dashboardThemePage > 0);
            dashboardPreviousPage.setAlpha(dashboardThemePage > 0 ? 1f : .38f);
        }
        if (dashboardNextPage != null) {
            dashboardNextPage.setEnabled(dashboardThemePage < 2);
            dashboardNextPage.setAlpha(dashboardThemePage < 2 ? 1f : .38f);
        }
    }

    private View buildDashboardThemeCard(int themeId, String name, int accent,
                                         boolean selected) {
        NeonHistoryCardLayout card = new NeonHistoryCardLayout(this, accent, selected);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout previews = new LinearLayout(this);
        previews.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(previews, new LinearLayout.LayoutParams(-1, 0, 1f));
        previews.addView(buildScreenTarget(themeId, name, true),
                weightedRow(1f, -1, 0, 0, 3, 0));
        previews.addView(buildScreenTarget(themeId, name, false),
                weightedRow(1f, -1, 3, 0, 0, 0));

        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(15), dp(10), dp(15), dp(11));
        caption.setBackgroundColor(Color.argb(224, 2, 9, 16));
        content.addView(caption, new LinearLayout.LayoutParams(-1, dp(66)));
        String state = selected ? "  •  DASHBOARD ACTIVE"
                : themeId > 6 ? "  •  CUSTOM PREVIEW" : "";
        TextView number = text(String.format(Locale.US, "THEME %02d%s", themeId,
                state), 11, accent, Typeface.BOLD);
        number.setLetterSpacing(.08f);
        caption.addView(number, row(-1, -2, 0, 0, 0, 2));
        TextView title = text(name, 13, TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.addView(title);
        return card;
    }

    private View buildScreenTarget(int themeId, String name, boolean mainHu) {
        FrameLayout target = new FrameLayout(this);
        target.setBackgroundColor(Color.rgb(5, 18, 31));
        target.setClickable(true);
        target.setFocusable(true);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        boolean custom = themeId > 6;
        boolean customUnlocked = !custom || DualPreviewAccess.isUnlocked(this);
        final Bitmap bitmap = customUnlocked
                ? dashboardThemeBridge.loadFactoryPreview(themeId, mainHu, true) : null;
        if (custom && !customUnlocked) {
            TextView locked = text("EDGE EXCLUSIVE\nSCAN OR TAP TO UNLOCK", 12,
                    CYAN, Typeface.BOLD);
            locked.setGravity(Gravity.CENTER);
            locked.setLetterSpacing(.06f);
            locked.setBackgroundColor(Color.rgb(3, 13, 24));
            target.addView(locked, new FrameLayout.LayoutParams(-1, -1));
        } else {
            if (bitmap != null) preview.setImageBitmap(bitmap);
            target.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        }

        String label = mainHu ? "MAIN HU" : "DASHBOARD";
        String action = custom
                ? (customUnlocked ? "PREVIEW" : "LOCKED")
                : mainHu ? "PREVIEW PAIR" : "APPLY ONLY";
        TextView badge = text(label + "  •  " + action, 10, TEXT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setLetterSpacing(.05f);
        badge.setBackgroundColor(Color.argb(226, 2, 14, 24));
        target.addView(badge, new FrameLayout.LayoutParams(-1, dp(42), Gravity.BOTTOM));
        target.setContentDescription((mainHu ? "Open factory selector for " :
                "Apply only dashboard portion of ") + name);
        target.setOnClickListener(v -> {
            if (custom && !DualPreviewAccess.isUnlocked(this)) {
                showDualScreenAccess();
            } else if (custom) {
                showCustomThemeActions(themeId, name);
            } else if (mainHu) {
                showPairedThemePreview(themeId, name);
            } else {
                confirmDashboardOnly(themeId, name, bitmap);
            }
        });
        return target;
    }

    private void showCustomThemeActions(int themeId, String name) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(34), dp(28), dp(34), dp(28));
        card.addView(text(String.format(Locale.US, "CUSTOM THEME %02d", themeId),
                24, TEXT, Typeface.BOLD), row(-1, -2, 0, 0, 0, 7));
        card.addView(text(name + " • Night concept preview • No dashboard files are changed",
                15, MUTED, Typeface.NORMAL), row(-1, -2, 0, 0, 0, 18));

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        ImageView hu = new ImageView(this);
        hu.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hu.setImageBitmap(dashboardThemeBridge.loadFactoryPreview(themeId, true, true));
        hu.setContentDescription("Main HU preview");
        pair.addView(hu, weightedRow(1f, dp(260), 0, 0, 5, 0));
        ImageView meter = new ImageView(this);
        meter.setScaleType(ImageView.ScaleType.CENTER_CROP);
        meter.setImageBitmap(dashboardThemeBridge.loadFactoryPreview(themeId, false, true));
        meter.setContentDescription("Dashboard preview");
        pair.addView(meter, weightedRow(1f, dp(260), 5, 0, 0, 0));
        card.addView(pair, row(-1, dp(260), 0, 0, 0, 18));

        Button both = actionButton("PREVIEW MAIN HU", true, v -> {
            dialog.dismiss();
            showScreenPreview(themeId, name, true);
        });
        card.addView(both, row(-1, dp(78), 0, 0, 0, 12));
        Button dashboard = actionButton("PREVIEW DASHBOARD", false, v -> {
            dialog.dismiss();
            showScreenPreview(themeId, name, false);
        });
        card.addView(dashboard, row(-1, dp(72), 0, 0, 0, 12));
        card.addView(actionButton("CANCEL", false, v -> dialog.dismiss()),
                row(-1, dp(66), 0, 0, 0, 0));
        showNeonDialog(dialog, card, 920);
    }

    private void showPairedThemePreview(int themeId, String name) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(34), dp(28), dp(34), dp(28));
        card.addView(text(String.format(Locale.US, "FACTORY THEME %02d", themeId),
                24, TEXT, Typeface.BOLD), row(-1, -2, 0, 0, 0, 7));
        card.addView(text(name + " • Night preview • Main HU and dashboard factory pair",
                15, MUTED, Typeface.NORMAL), row(-1, -2, 0, 0, 0, 18));
        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.addView(previewImage(themeId, true, "Main HU preview"),
                weightedRow(1f, dp(260), 0, 0, 5, 0));
        pair.addView(previewImage(themeId, false, "Dashboard preview"),
                weightedRow(1f, dp(260), 5, 0, 0, 0));
        card.addView(pair, row(-1, dp(260), 0, 0, 0, 18));
        Button both = actionButton("APPLY MAIN HU + DASHBOARD", true, v -> {
            dialog.dismiss();
            applyFactoryBoth(themeId, name);
        });
        card.addView(both, row(-1, dp(78), 0, 0, 0, 12));
        Button dashboard = actionButton("APPLY DASHBOARD ONLY", false, v -> {
            dialog.dismiss();
            applyDashboardTheme(themeId, name);
        });
        card.addView(dashboard, row(-1, dp(72), 0, 0, 0, 12));
        card.addView(actionButton("CLOSE", false, v -> dialog.dismiss()),
                row(-1, dp(66), 0, 0, 0, 0));
        showNeonDialog(dialog, card, 920);
    }

    private ImageView previewImage(int themeId, boolean mainHu, String description) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(4, 15, 25));
        Bitmap bitmap = dashboardThemeBridge.loadFactoryPreview(themeId, mainHu, true);
        if (bitmap != null) image.setImageBitmap(bitmap);
        image.setContentDescription(description);
        return image;
    }

    private void showScreenPreview(int themeId, String name, boolean mainHu) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(24), dp(28), dp(26));
        card.addView(text((mainHu ? "MAIN HU" : "DASHBOARD") + " • " + name,
                22, TEXT, Typeface.BOLD), row(-1, -2, 0, 0, 0, 7));
        card.addView(text(mainHu
                        ? "Custom center-display night concept preview"
                        : "Custom driver-display night concept preview • Visual only",
                14, mainHu ? MUTED : WARNING, Typeface.NORMAL),
                row(-1, -2, 0, 0, 0, 16));
        ImageView image = previewImage(themeId, mainHu,
                mainHu ? "Full Main HU preview" : "Full dashboard preview");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(image, row(-1, dp(470), 0, 0, 0, 18));
        card.addView(actionButton("CLOSE PREVIEW", false, v -> dialog.dismiss()),
                row(-1, dp(70), 0, 0, 0, 0));
        showNeonDialog(dialog, card, 960);
    }

    private void showDualScreenAccess() {
        boolean unlocked = DualPreviewAccess.isUnlocked(this);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(30), dp(26), dp(30), dp(28));
        card.addView(text("EDGE DUAL-SCREEN ACCESS", 24, TEXT, Typeface.BOLD),
                row(-1, -2, 0, 0, 0, 7));
        card.addView(text(unlocked
                        ? "ACCESS ACTIVE • Custom paired previews are unlocked"
                        : "Scan the QR artwork or tap below to unlock exclusive previews",
                15, unlocked ? SUCCESS : MUTED, Typeface.NORMAL),
                row(-1, -2, 0, 0, 0, 16));
        ImageView artwork = new ImageView(this);
        artwork.setImageResource(R.drawable.edge_cityray_dual_screen);
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artwork.setBackground(new CornerCutDrawable(Color.rgb(2, 10, 18),
                unlocked ? SUCCESS : CYAN, dp(1), dp(18)));
        artwork.setPadding(dp(4), dp(4), dp(4), dp(4));
        artwork.setContentDescription("EDGE dual-screen QR access artwork");
        artwork.setClickable(true);
        artwork.setFocusable(true);
        artwork.setOnClickListener(v -> {
            if (!DualPreviewAccess.isUnlocked(this)) {
                dialog.dismiss();
                showDualAccessPassword();
            }
        });
        card.addView(artwork, row(-1, dp(420), 0, 0, 0, 18));
        if (!unlocked) {
            Button unlock = actionButton("UNLOCK WITH ACCESS CODE", true, v -> {
                dialog.dismiss();
                showDualAccessPassword();
            });
            card.addView(unlock, row(-1, dp(80), 0, 0, 0, 12));
        } else {
            Button explore = actionButton("EXPLORE CUSTOM PREVIEWS", true, v -> {
                dialog.dismiss();
                dashboardThemePage = 2;
                refreshDashboardThemeGrid();
                dashboardThemePager.post(() -> scrollToDashboardThemePage(2, true));
                setStatus("EDGE DUAL PREVIEWS UNLOCKED", SUCCESS);
            });
            card.addView(explore, row(-1, dp(80), 0, 0, 0, 12));
            Button original = actionButton("OPEN ORIGINAL GEELY MENU", false, v -> {
                dialog.dismiss();
                openFactoryDashboardThemes();
            });
            card.addView(original, row(-1, dp(72), 0, 0, 0, 12));
            Button lock = actionButton("LOCK EXCLUSIVE PREVIEWS", false, v -> {
                DualPreviewAccess.lock(this);
                dialog.dismiss();
                refreshDashboardThemeGrid();
                setStatus("EDGE DUAL PREVIEWS LOCKED", MUTED);
            });
            card.addView(lock, row(-1, dp(68), 0, 0, 0, 12));
        }
        card.addView(actionButton("CLOSE", false, v -> dialog.dismiss()),
                row(-1, dp(66), 0, 0, 0, 0));
        showNeonDialog(dialog, card, 960);
    }

    private void showDualAccessPassword() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(38), dp(32), dp(38), dp(30));
        card.addView(text("PRIVATE ACCESS", 25, TEXT, Typeface.BOLD),
                row(-1, -2, 0, 0, 0, 8));
        card.addView(text("Enter the six-digit EDGE access code",
                16, MUTED, Typeface.NORMAL), row(-1, -2, 0, 0, 0, 18));
        EditText code = pairingInput("6-DIGIT ACCESS CODE", 6);
        code.setInputType(InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        card.addView(code, row(-1, dp(78), 0, 0, 0, 8));
        TextView feedback = text("Five attempts allowed before a short lockout",
                13, MUTED, Typeface.NORMAL);
        card.addView(feedback, row(-1, -2, 0, 0, 0, 18));
        Button verify = actionButton("ACTIVATE ACCESS", true, null);
        verify.setOnClickListener(v -> {
            long remaining = dualAccessLockoutUntil - System.currentTimeMillis();
            if (remaining > 0) {
                feedback.setText("TRY AGAIN IN " + ((remaining + 999) / 1000) + " SECONDS");
                feedback.setTextColor(WARNING);
                return;
            }
            String value = code.getText().toString().trim();
            if (value.length() != 6) {
                feedback.setText("ENTER ALL SIX DIGITS");
                feedback.setTextColor(WARNING);
                return;
            }
            verify.setEnabled(false);
            verify.setText("VERIFYING…");
            char[] candidate = value.toCharArray();
            code.setText("");
            new Thread(() -> {
                boolean accepted = DualPreviewAccess.verify(candidate);
                runOnUiThread(() -> {
                    if (accepted) {
                        dualAccessFailures = 0;
                        DualPreviewAccess.unlock(this);
                        dialog.dismiss();
                        refreshDashboardThemeGrid();
                        setStatus("EDGE DUAL PREVIEWS ACTIVATED", SUCCESS);
                        showDualScreenAccess();
                    } else {
                        dualAccessFailures++;
                        if (dualAccessFailures >= 5) {
                            dualAccessFailures = 0;
                            dualAccessLockoutUntil = System.currentTimeMillis() + 30_000L;
                            feedback.setText("ACCESS LOCKED FOR 30 SECONDS");
                        } else {
                            feedback.setText("ACCESS CODE NOT ACCEPTED • " +
                                    (5 - dualAccessFailures) + " ATTEMPTS LEFT");
                        }
                        feedback.setTextColor(WARNING);
                        verify.setEnabled(true);
                        verify.setText("ACTIVATE ACCESS");
                    }
                });
            }, "edge-dual-access-verify").start();
        });
        card.addView(verify, row(-1, dp(80), 0, 0, 0, 12));
        card.addView(actionButton("CANCEL", false, v -> dialog.dismiss()),
                row(-1, dp(68), 0, 0, 0, 0));
        showNeonDialog(dialog, card, 820);
    }

    private void applyCustomBoth(int themeId, String name) {
        progress.setVisibility(View.VISIBLE);
        dashboardThemeGrid.setEnabled(false);
        dashboardThemeGrid.setAlpha(.62f);
        setStatus("APPLYING " + name + " TO MAIN HU", CYAN);
        dashboardThemeBridge.applyCustomMainHu(themeId, (success, message) ->
                runOnUiThread(() -> {
                    if (!success) {
                        progress.setVisibility(View.GONE);
                        dashboardThemeGrid.setEnabled(true);
                        dashboardThemeGrid.setAlpha(1f);
                        setStatus(message, WARNING);
                        return;
                    }
                    setStatus(message + " • SENDING DASHBOARD COMMAND", CYAN);
                    applyDashboardTheme(themeId, name);
                }));
    }

    private void confirmDashboardOnly(int themeId, String name, Bitmap preview) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(34), dp(28), dp(34), dp(28));
        card.addView(text("APPLY TO DASHBOARD ONLY?", 24, TEXT, Typeface.BOLD),
                row(-1, -2, 0, 0, 0, 7));
        card.addView(text(name + " • The Main HU live wallpaper will remain unchanged",
                15, MUTED, Typeface.NORMAL), row(-1, -2, 0, 0, 0, 18));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(4, 15, 25));
        if (preview != null) image.setImageBitmap(preview);
        card.addView(image, row(-1, dp(235), 0, 0, 0, 18));
        Button apply = actionButton("APPLY DASHBOARD ONLY", true, v -> {
            dialog.dismiss();
            applyDashboardTheme(themeId, name);
        });
        card.addView(apply, row(-1, dp(78), 0, 0, 0, 12));
        Button originalPair = actionButton("APPLY MAIN HU + DASHBOARD", false, v -> {
            dialog.dismiss();
            applyFactoryBoth(themeId, name);
        });
        card.addView(originalPair, row(-1, dp(72), 0, 0, 0, 12));
        card.addView(actionButton("CANCEL", false, v -> dialog.dismiss()),
                row(-1, dp(66), 0, 0, 0, 0));
        showNeonDialog(dialog, card, 900);
    }

    private void applyFactoryBoth(int themeId, String name) {
        progress.setVisibility(View.VISIBLE);
        dashboardThemeGrid.setEnabled(false);
        dashboardThemeGrid.setAlpha(.62f);
        setStatus("APPLYING " + name + " TO MAIN HU", CYAN);
        dashboardThemeBridge.applyFactoryMainHu(themeId, (success, message) ->
                runOnUiThread(() -> {
                    if (!success) {
                        progress.setVisibility(View.GONE);
                        dashboardThemeGrid.setEnabled(true);
                        dashboardThemeGrid.setAlpha(1f);
                        setStatus(message, WARNING);
                        return;
                    }
                    setStatus(message + " • APPLYING DASHBOARD", CYAN);
                    applyDashboardTheme(themeId, name);
                }));
    }

    private View buildCustomDashboardTestCard() {
        NeonHistoryCardLayout card = new NeonHistoryCardLayout(this, CYAN, false);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(pair, new FrameLayout.LayoutParams(-1, -1));
        pair.addView(buildCustomMeterVariant(false), weightedRow(1f, -1, 0, 0, 3, 0));
        pair.addView(buildCustomMeterVariant(true), weightedRow(1f, -1, 3, 0, 0, 0));

        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(18), dp(10), dp(18), dp(10));
        caption.setBackgroundColor(Color.argb(226, 2, 9, 16));
        card.addView(caption, new FrameLayout.LayoutParams(-1, dp(72), Gravity.BOTTOM));
        caption.addView(text("CUSTOM DASHBOARD 07 • MOROCCO HORIZON", 13, TEXT,
                Typeface.BOLD), row(-1, -2, 0, 0, 0, 3));
        caption.addView(text("DAY + NIGHT • 2880 × 1080 • TRANSFER NOT YET UNLOCKED",
                11, WARNING, Typeface.BOLD));
        card.setContentDescription("Custom dashboard image 7 preview only");
        card.setOnClickListener(v -> setStatus(
                "CUSTOM 07 IS READY TO TEST AFTER DIM FILE TRANSFER IS UNLOCKED", WARNING));
        return card;
    }

    private View buildCustomMeterVariant(boolean night) {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackgroundColor(Color.rgb(5, 18, 31));
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = dashboardThemeBridge.loadCustomMeterPreview(night);
        if (bitmap != null) preview.setImageBitmap(bitmap);
        panel.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        TextView label = text(night ? "DASHBOARD • NIGHT" : "DASHBOARD • DAY",
                10, TEXT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setBackgroundColor(Color.argb(210, 2, 14, 24));
        panel.addView(label, new FrameLayout.LayoutParams(-1, dp(36), Gravity.TOP));
        return panel;
    }

    private void showNeonDialog(Dialog dialog, View card, int maxWidthDp) {
        boolean resumeVideo = videoPreview != null
                && videoPreview.getVisibility() == View.VISIBLE;
        if (resumeVideo) {
            videoPreview.pause();
        }
        dialog.setOnDismissListener(ignored -> {
            if (resumeVideo && videoPreview != null) {
                videoPreview.start();
            }
        });
        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = .72f;
            window.setAttributes(attributes);
        }
        dialog.show();
        revealModal(card);
        if (window != null) {
            int available = getResources().getDisplayMetrics().widthPixels - dp(112);
            window.setLayout(Math.min(dp(maxWidthDp), available),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void applyDashboardTheme(int themeId, String name) {
        dashboardThemeGrid.setEnabled(false);
        dashboardThemeGrid.setAlpha(.62f);
        progress.setVisibility(View.VISIBLE);
        setStatus("APPLYING " + name + " TO DRIVER DISPLAY", CYAN);
        dashboardThemeBridge.applyFactoryTheme(themeId, (success, message) ->
                runOnUiThread(() -> {
                    dashboardThemeGrid.setEnabled(true);
                    dashboardThemeGrid.setAlpha(1f);
                    progress.setVisibility(View.GONE);
                    if (success) {
                        selectedDashboardTheme = themeId;
                        getSharedPreferences("edge_dashboard_themes", MODE_PRIVATE).edit()
                                .putInt("selected_factory_theme", themeId).apply();
                        refreshDashboardThemeGrid();
                        setStatus(message, SUCCESS);
                    } else {
                        setStatus(message, WARNING);
                        if (themeId <= 6) openFactoryDashboardThemes();
                    }
                }));
    }

    private void openFactoryDashboardThemes() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.geely.theme",
                    "com.geely.theme.ui.mine.defaultattired.DefaultAttiredActivity"));
            startActivity(intent);
        } catch (Throwable ignored) {
            setStatus("GEELY DASHBOARD THEME MANAGER NOT FOUND", WARNING);
        }
    }

    private View buildDualScreenVisionCard() {
        NeonHistoryCardLayout card = new NeonHistoryCardLayout(this,
                Color.rgb(38, 216, 255), false);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));

        ImageView artwork = new ImageView(this);
        artwork.setImageResource(R.drawable.edge_cityray_dual_screen);
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artwork.setBackgroundColor(Color.rgb(2, 7, 13));
        artwork.setContentDescription(
                "EDGE CityRay dual-screen vision with pairing QR code");
        card.addView(artwork, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(15), dp(10), dp(15), dp(11));
        caption.setBackgroundColor(Color.argb(230, 2, 9, 16));
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(
                -1, dp(66), Gravity.BOTTOM);
        card.addView(caption, captionParams);
        TextView eyebrow = text("EDGE EXCLUSIVE  •  QR", 11, CYAN, Typeface.BOLD);
        eyebrow.setLetterSpacing(.08f);
        caption.addView(eyebrow, row(-1, -2, 0, 0, 0, 2));
        caption.addView(text("DUAL-SCREEN VISION", 13, TEXT, Typeface.BOLD));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("Open EDGE dual-screen QR access");
        card.setOnClickListener(v -> showDualScreenAccess());
        return card;
    }

    private void chooseLiveVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    private void showVideoSourcePicker() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(38), dp(32), dp(38), dp(30));

        TextView title = text("CHOOSE VIDEO SOURCE", 25, TEXT, Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        card.addView(title, row(-1, -2, 0, 0, 0, 9));
        TextView subtitle = text("Select from the CityRay gallery or browse USB and files",
                16, MUTED, Typeface.NORMAL);
        card.addView(subtitle, row(-1, -2, 0, 0, 0, 26));

        Button gallery = actionButton("HU GALLERY", true, v -> {
            dialog.dismiss();
            chooseGalleryVideo();
        });
        gallery.setContentDescription("Choose a video from the head unit gallery");
        card.addView(gallery, row(-1, dp(82), 0, 0, 0, 14));

        Button files = actionButton("USB & FILES", false, v -> {
            dialog.dismiss();
            chooseLiveVideo();
        });
        files.setContentDescription("Choose a video from USB or file storage");
        card.addView(files, row(-1, dp(82), 0, 0, 0, 14));

        Button cancel = actionButton("CANCEL", false, v -> dialog.dismiss());
        card.addView(cancel, row(-1, dp(70), 0, 0, 0, 0));

        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.72f;
            window.setAttributes(attributes);
        }
        dialog.show();
        revealModal(card);
        if (window != null) {
            int available = getResources().getDisplayMetrics().widthPixels - dp(112);
            window.setLayout(Math.min(dp(820), available), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void chooseGalleryVideo() {
        if (Build.VERSION.SDK_INT <= 32
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            setStatus("ALLOW VIDEO ACCESS FOR HU GALLERY", CYAN);
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_HU_MEDIA);
            return;
        }
        showHuVideoLibrary();
    }

    private void showHuVideoLibrary() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(34), dp(28), dp(34), dp(28));
        card.addView(text("HU VIDEO LIBRARY", 25, TEXT, Typeface.BOLD),
                row(-1, -2, 0, 0, 0, 7));
        card.addView(text("Videos indexed by the CityRay media system", 15, MUTED,
                Typeface.NORMAL), row(-1, -2, 0, 0, 0, 18));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));

        int count = 0;
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
        };
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH);
                int heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT);
                while (cursor.moveToNext() && count < 100) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    long duration = cursor.getLong(durationColumn);
                    int width = cursor.getInt(widthColumn);
                    int height = cursor.getInt(heightColumn);
                    Uri uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            Long.toString(id));
                    String detail = (name == null ? "HU VIDEO" : name.toUpperCase(Locale.ROOT))
                            + "\n" + width + " × " + height + "  •  "
                            + String.format(Locale.US, "%d:%02d", duration / 60000,
                            (duration / 1000) % 60);
                    Button item = actionButton(detail, false, v -> {
                        dialog.dismiss();
                        importSelectedVideo(uri);
                    });
                    item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    item.setAllCaps(false);
                    list.addView(item, row(-1, dp(92), 0, 0, 0, 10));
                    count++;
                }
            }
        } catch (Throwable error) {
            setStatus("HU GALLERY READ FAILED", WARNING);
        }

        if (count == 0) {
            TextView empty = text("NO VIDEOS FOUND IN HU GALLERY", 17, WARNING, Typeface.BOLD);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, row(-1, dp(100), 0, 0, 0, 8));
        }
        card.addView(scroll, new LinearLayout.LayoutParams(-1, dp(600)));
        Button close = actionButton("CLOSE", false, v -> dialog.dismiss());
        card.addView(close, row(-1, dp(70), 0, 14, 0, 0));

        dialog.setContentView(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.72f;
            window.setAttributes(attributes);
        }
        dialog.show();
        revealModal(card);
        if (window != null) {
            int available = getResources().getDisplayMetrics().widthPixels - dp(112);
            window.setLayout(Math.min(dp(920), available), WindowManager.LayoutParams.WRAP_CONTENT);
        }
        setStatus(count + " HU VIDEOS AVAILABLE", SUCCESS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_HU_MEDIA) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showHuVideoLibrary();
        } else {
            setStatus("HU GALLERY ACCESS DENIED • USE USB & FILES", WARNING);
        }
    }

    private void previewLiveWallpaper() {
        if (selectedVideoUri == null) return;
        if (isEdgeWallpaperActive()) {
            recordAppliedWallpaper();
            bumpVideoRevision();
            setStatus("WALLPAPER UPDATED • NO PAIRING NEEDED", SUCCESS);
            pulsePreviewFrame();
            return;
        }
        setStatus("APPLYING TO MAIN HU", CYAN);
        progress.setVisibility(View.VISIBLE);
        applyButton.setEnabled(false);
        builtInAdb.apply(this::handleBuiltInAdbResult);
    }

    private void handleBuiltInAdbResult(boolean success, boolean needsPairing, String message) {
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            applyButton.setEnabled(selectedVideoUri != null);
            if (success) {
                recordAppliedWallpaper();
                bumpVideoRevision();
                setStatus(message, SUCCESS);
                pulsePreviewFrame();
            } else if (needsPairing) {
                setStatus(message, WARNING);
                showBuiltInAdbPairing();
            } else {
                setStatus(message, WARNING);
            }
        });
    }

    private void showBuiltInAdbPairing() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        NeonModalLayout card = new NeonModalLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(38), dp(32), dp(38), dp(30));
        card.addView(text("ENABLE ONE-TAP APPLY", 25, TEXT, Typeface.BOLD),
                row(-1, -2, 0, 0, 0, 8));
        card.addView(text("One-time setup: open Pair device with pairing code and leave "
                        + "that screen visible. EDGE discovers the port automatically.",
                16, MUTED, Typeface.NORMAL), row(-1, -2, 0, 0, 0, 22));

        EditText code = pairingInput("6-DIGIT PAIRING CODE", 6);
        card.addView(code, row(-1, dp(78), 0, 0, 0, 18));

        Button pair = actionButton("PAIR ONCE & APPLY", true, v -> {
            String codeValue = code.getText().toString().trim();
            if (codeValue.length() != 6) {
                setStatus("ENTER THE 6-DIGIT PAIRING CODE", WARNING);
                return;
            }
            dialog.dismiss();
            setStatus("PAIRING BUILT-IN ADB", CYAN);
            progress.setVisibility(View.VISIBLE);
            applyButton.setEnabled(false);
            builtInAdb.pairAndApply(codeValue, this::handleBuiltInAdbResult);
        });
        card.addView(pair, row(-1, dp(82), 0, 0, 0, 12));
        Button cancel = actionButton("CANCEL", false, v -> dialog.dismiss());
        card.addView(cancel, row(-1, dp(70), 0, 0, 0, 0));

        dialog.setContentView(card);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.72f;
            window.setAttributes(attributes);
        }
        dialog.show();
        revealModal(card);
        if (window != null) {
            int available = getResources().getDisplayMetrics().widthPixels - dp(112);
            window.setLayout(Math.min(dp(820), available), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private EditText pairingInput(String hint, int maxLength) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(18);
        input.setSingleLine(true);
        input.setPadding(dp(22), 0, dp(22), 0);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(maxLength)});
        input.setBackground(new CornerCutDrawable(Color.rgb(4, 16, 27), BLUE, dp(1), dp(12)));
        return input;
    }

    private void applyFromIntent() {
        if (selectedVideoUri == null) {
            setStatus("IMPORT A VIDEO BEFORE ACTIVATION", WARNING);
            return;
        }
        previewLiveWallpaper();
    }

    private boolean isEdgeWallpaperActive() {
        try {
            android.app.WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
            return info != null && getPackageName().equals(info.getPackageName())
                    && EdgeLiveWallpaperService.class.getName().equals(info.getServiceName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openSystemWallpaperActivation() {
        setStatus("CONFIRM EDGE LIVE WALLPAPER", CYAN);
        progress.setVisibility(View.VISIBLE);
        Intent direct = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        direct.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, EdgeLiveWallpaperService.class));
        if (direct.resolveActivity(getPackageManager()) != null) {
            activationFlowOpened = true;
            startActivity(direct);
            return;
        }
        Intent chooser = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
        if (chooser.resolveActivity(getPackageManager()) != null) {
            activationFlowOpened = true;
            startActivity(chooser);
            return;
        }
        activationFlowOpened = false;
        progress.setVisibility(View.GONE);
        setStatus("ONE-TIME HU ACTIVATION REQUIRED", WARNING);
    }

    private void finishActivationCheck() {
        progress.setVisibility(View.GONE);
        applyButton.setEnabled(selectedVideoUri != null);
        applyButton.setAlpha(selectedVideoUri == null ? 0.42f : 1f);
        if (isEdgeWallpaperActive()) {
            bumpVideoRevision();
            setStatus("LIVE WALLPAPER ACTIVE", SUCCESS);
            pulsePreviewFrame();
        } else {
            setStatus("SELECT EDGE CITYRAY LIVE • THEN CONFIRM", WARNING);
        }
    }

    private void bumpVideoRevision() {
        getSharedPreferences(EdgeLiveWallpaperService.PREFS, MODE_PRIVATE).edit()
                .putLong(EdgeLiveWallpaperService.KEY_VIDEO_REVISION,
                        System.currentTimeMillis())
                .apply();
    }

    private void returnToGeelyWallpapers() {
        setStatus("RESTORING FACTORY WALLPAPER MODE", CYAN);
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try (InputStream in = openRestoreSource()) {
                WallpaperManager.getInstance(this).setStream(
                        in, null, true, WallpaperManager.FLAG_SYSTEM);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    setStatus("FACTORY WALLPAPER MODE RESTORED", SUCCESS);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    setStatus("FACTORY RESTORE UNAVAILABLE", WARNING);
                });
            }
        }).start();
    }

    private InputStream openRestoreSource() throws Exception {
        java.io.File verified = new java.io.File(VERIFIED_RESTORE_SOURCE);
        if (verified.isFile() && verified.length() > 0) return new FileInputStream(verified);
        java.io.File known = new java.io.File(KNOWN_RESTORE_SOURCE);
        if (known.isFile() && known.length() > 0) return new FileInputStream(known);
        return getAssets().open("phase1_csd.png");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean videoResult = requestCode == PICK_VIDEO || requestCode == PICK_GALLERY_VIDEO;
        if (!videoResult || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        importSelectedVideo(uri);
    }

    private void importSelectedVideo(Uri source) {
        String name = displayName(source);
        setStatus("IMPORTING VIDEO TO EDGE STORAGE", CYAN);
        progress.setVisibility(View.VISIBLE);
        applyButton.setEnabled(false);
        new Thread(() -> {
            try {
                java.io.File directory = getExternalFilesDir("wallpapers");
                if (directory == null) throw new IllegalStateException("Wallpaper storage unavailable");
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new IllegalStateException("Could not create wallpaper storage");
                }
                java.io.File temporary = new java.io.File(directory, "active.importing");
                java.io.File destination = new java.io.File(directory,
                        "wallpaper_" + System.currentTimeMillis() + ".mp4");
                try (InputStream input = getContentResolver().openInputStream(source);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new IllegalStateException("Selected video could not be opened");
                    byte[] buffer = new byte[256 * 1024];
                    int count;
                    while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                    output.getFD().sync();
                }
                if (!temporary.renameTo(destination)) {
                    throw new IllegalStateException("Could not finish wallpaper import");
                }
                saveSelectedVideo(destination, name);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    refreshUi();
                    setStatus("LIVE PREVIEW INITIALIZED", SUCCESS);
                    pulsePreviewFrame();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    applyButton.setEnabled(selectedVideoUri != null);
                    setStatus("VIDEO IMPORT FAILED • TRY ANOTHER FILE", WARNING);
                });
            }
        }).start();
    }

    private void saveSelectedVideo(java.io.File file, String name) {
        selectedVideoUri = Uri.fromFile(file);
        selectedVideoName = name;
        getSharedPreferences(EdgeLiveWallpaperService.PREFS, MODE_PRIVATE).edit()
                .putString(EdgeLiveWallpaperService.KEY_MAIN_VIDEO_PATH, file.getAbsolutePath())
                .putString(EdgeLiveWallpaperService.KEY_MAIN_VIDEO_NAME, name)
                .putLong(EdgeLiveWallpaperService.KEY_VIDEO_REVISION,
                        System.currentTimeMillis())
                .remove(EdgeLiveWallpaperService.KEY_MAIN_VIDEO_URI)
                .remove(EdgeLiveWallpaperService.KEY_VIDEO_URI)
                .apply();
    }

    private void restoreSelectedVideo() {
        SharedPreferences prefs = getSharedPreferences(EdgeLiveWallpaperService.PREFS, MODE_PRIVATE);
        String path = prefs.getString(EdgeLiveWallpaperService.KEY_MAIN_VIDEO_PATH, null);
        java.io.File file = path == null ? null : new java.io.File(path);
        if (file == null || !file.isFile()) {
            java.io.File directory = getExternalFilesDir("wallpapers");
            java.io.File candidate = directory == null ? null : new java.io.File(directory, "active.mp4");
            if (candidate != null && candidate.isFile()) {
                file = candidate;
                prefs.edit().putString(EdgeLiveWallpaperService.KEY_MAIN_VIDEO_PATH,
                        candidate.getAbsolutePath()).apply();
            }
        }
        if (file != null && file.isFile()) selectedVideoUri = Uri.fromFile(file);
        selectedVideoName = prefs.getString(EdgeLiveWallpaperService.KEY_MAIN_VIDEO_NAME,
                "EDGE LIVE WALLPAPER");
    }

    private void importVideoFromIntent(Intent intent) {
        if (intent == null) return;
        String value = intent.getStringExtra("edge_live_main_uri");
        if (value != null && !value.trim().isEmpty()) importSelectedVideo(Uri.parse(value));
    }

    private void refreshUi() {
        boolean ready = selectedVideoUri != null;
        applyButton.setEnabled(ready);
        applyButton.setAlpha(ready ? 1f : 0.42f);
        emptyPreview.setVisibility(ready ? View.GONE : View.VISIBLE);
        refreshHistoryDeck();
        if (!ready) return;
        videoName.setText((selectedVideoName == null ? "EDGE LIVE WALLPAPER" : selectedVideoName)
                .toUpperCase(Locale.ROOT));
        videoDetails.setText(videoSummary(selectedVideoUri).toUpperCase(Locale.ROOT));
        videoPreview.setVideoPath(selectedVideoUri.getPath());
        videoPreview.setOnPreparedListener(player -> {
            player.setLooping(true);
            player.setVolume(0f, 0f);
            videoPreview.start();
        });
        videoPreview.setOnErrorListener((player, what, extra) -> {
            setStatus("PREVIEW UNAVAILABLE • VIDEO REMAINS SELECTED", WARNING);
            return true;
        });
    }

    private void seedCurrentWallpaperHistory() {
        if (selectedVideoUri == null || selectedVideoUri.getPath() == null) return;
        SharedPreferences prefs = getSharedPreferences(EdgeLiveWallpaperService.PREFS, MODE_PRIVATE);
        if (prefs.getString(HISTORY_PATH + 0, null) != null) return;
        prefs.edit()
                .putString(HISTORY_PATH + 0, selectedVideoUri.getPath())
                .putString(HISTORY_NAME + 0, selectedVideoName == null
                        ? "EDGE LIVE WALLPAPER" : selectedVideoName)
                .apply();
    }

    private void recordAppliedWallpaper() {
        if (selectedVideoUri == null || selectedVideoUri.getPath() == null) return;
        SharedPreferences prefs = getSharedPreferences(EdgeLiveWallpaperService.PREFS, MODE_PRIVATE);
        List<String> paths = new ArrayList<>();
        List<String> names = new ArrayList<>();
        paths.add(selectedVideoUri.getPath());
        names.add(selectedVideoName == null ? "EDGE LIVE WALLPAPER" : selectedVideoName);
        List<String> previous = new ArrayList<>();
        for (int slot = 0; slot < HISTORY_SLOTS; slot++) {
            String path = prefs.getString(HISTORY_PATH + slot, null);
            if (path == null) continue;
            previous.add(path);
            if (!paths.contains(path) && new java.io.File(path).isFile()
                    && paths.size() < HISTORY_SLOTS) {
                paths.add(path);
                names.add(prefs.getString(HISTORY_NAME + slot, "EDGE WALLPAPER"));
            }
        }
        SharedPreferences.Editor edit = prefs.edit();
        for (int slot = 0; slot < HISTORY_SLOTS; slot++) {
            if (slot < paths.size()) {
                edit.putString(HISTORY_PATH + slot, paths.get(slot));
                edit.putString(HISTORY_NAME + slot, names.get(slot));
            } else {
                edit.remove(HISTORY_PATH + slot).remove(HISTORY_NAME + slot);
            }
        }
        edit.apply();
        for (String oldPath : previous) {
            if (!paths.contains(oldPath)) removeRetiredWallpaper(oldPath);
        }
        refreshHistoryDeck();
    }

    private void removeRetiredWallpaper(String path) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.File directory = getExternalFilesDir("wallpapers");
            if (directory != null && directory.equals(file.getParentFile())
                    && file.getName().startsWith("wallpaper_")
                    && (selectedVideoUri == null || !path.equals(selectedVideoUri.getPath()))) {
                // History owns only its generated wallpaper_* files; legacy/user files are untouched.
                file.delete();
            }
        } catch (Throwable ignored) { }
    }

    private void refreshHistoryDeck() {
        if (recentWallpaperStrip == null) return;
        recentWallpaperStrip.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(EdgeLiveWallpaperService.PREFS, MODE_PRIVATE);
        int[] accents = {CYAN, Color.rgb(159, 86, 255), Color.rgb(255, 64, 197)};
        int shown = 0;
        for (int slot = 0; slot < HISTORY_SLOTS; slot++) {
            String path = prefs.getString(HISTORY_PATH + slot, null);
            if (path != null && selectedVideoUri != null
                    && path.equals(selectedVideoUri.getPath())) continue;
            String name = prefs.getString(HISTORY_NAME + slot, "EMPTY DECK");
            java.io.File file = path == null ? null : new java.io.File(path);
            boolean available = file != null && file.isFile();
            if (!available) continue;
            int displaySlot = shown + 1;
            View card = buildHistoryCard(displaySlot, path, name,
                    accents[Math.min(displaySlot, accents.length - 1)], false);
            int left = shown == 0 ? 0 : 8;
            int right = shown == 1 ? 0 : 8;
            recentWallpaperStrip.addView(card,
                    weightedRow(1f, -1, left, 0, right, 0));
            shown++;
            if (shown == 2) break;
        }
        while (shown < 2) {
            int displaySlot = shown + 1;
            int left = shown == 0 ? 0 : 8;
            int right = shown == 1 ? 0 : 8;
            recentWallpaperStrip.addView(buildHistoryCard(displaySlot, null,
                            "EMPTY DECK", accents[Math.min(displaySlot,
                                    accents.length - 1)], false),
                    weightedRow(1f, -1, left, 0, right, 0));
            shown++;
        }
    }

    private View buildHistoryCard(int slot, String path, String name, int accent,
                                  boolean selected) {
        NeonHistoryCardLayout card = new NeonHistoryCardLayout(this, accent, selected);
        card.setPadding(dp(5), dp(5), dp(5), dp(5));

        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setBackgroundColor(Color.rgb(2, 7, 13));
        card.addView(thumbnail, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        caption.setPadding(dp(16), dp(12), dp(16), dp(14));
        caption.setBackgroundColor(Color.argb(225, 2, 9, 16));
        FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(
                -1, dp(92), Gravity.BOTTOM);
        card.addView(caption, captionParams);

        TextView slotLabel = text(String.format(Locale.US, "DECK %02d%s", slot + 1,
                selected ? "  •  ACTIVE" : ""), 12, accent, Typeface.BOLD);
        slotLabel.setLetterSpacing(.09f);
        caption.addView(slotLabel, row(-1, -2, 0, 0, 0, 3));
        TextView title = text(path == null ? "EMPTY HISTORY SLOT" : name.toUpperCase(Locale.ROOT),
                14, path == null ? MUTED : TEXT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        caption.addView(title);

        if (path == null) {
            TextView empty = text("+", 46, Color.argb(155, Color.red(accent),
                    Color.green(accent), Color.blue(accent)), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            card.addView(empty, new FrameLayout.LayoutParams(-1, dp(410), Gravity.TOP));
            card.setContentDescription("Empty wallpaper history slot " + (slot + 1));
        } else {
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription("Reload " + name + " from wallpaper history");
            card.setOnClickListener(view -> loadWallpaperFromHistory(path, name));
            loadHistoryThumbnail(thumbnail, path);
        }
        return card;
    }

    private void loadWallpaperFromHistory(String path, String name) {
        java.io.File file = new java.io.File(path);
        if (!file.isFile()) {
            setStatus("HISTORY VIDEO IS NO LONGER AVAILABLE", WARNING);
            refreshHistoryDeck();
            return;
        }
        selectedVideoUri = Uri.fromFile(file);
        selectedVideoName = name;
        getSharedPreferences(EdgeLiveWallpaperService.PREFS, MODE_PRIVATE).edit()
                .putString(EdgeLiveWallpaperService.KEY_MAIN_VIDEO_PATH, path)
                .putString(EdgeLiveWallpaperService.KEY_MAIN_VIDEO_NAME, name)
                .apply();
        refreshUi();
        setStatus("DECK LOADED • PRESS ACTIVATE TO APPLY", SUCCESS);
        pulsePreviewFrame();
    }

    private void loadHistoryThumbnail(ImageView target, String path) {
        target.setTag(path);
        new Thread(() -> {
            Bitmap frame = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(path);
                frame = retriever.getFrameAtTime(750_000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } catch (Throwable ignored) {
            } finally {
                try { retriever.release(); } catch (Throwable ignored) { }
            }
            Bitmap result = frame;
            if (result != null) target.post(() -> {
                if (path.equals(target.getTag())) target.setImageBitmap(result);
            });
        }, "edge-history-thumbnail").start();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (Throwable ignored) { }
        return "Selected wallpaper";
    }

    private String videoSummary(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if ("file".equals(uri.getScheme())) retriever.setDataSource(uri.getPath());
            else retriever.setDataSource(this, uri);
            int width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            long duration = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            String size = width > 0 && height > 0 ? width + " × " + height : "Video";
            String time = duration > 0 ? String.format(Locale.US, "%d:%02d",
                    duration / 60000, (duration / 1000) % 60) : "Loop";
            return size + "  •  " + time + "  •  Muted loop";
        } catch (Throwable ignored) {
            return "Ready  •  Muted loop";
        } finally {
            try { retriever.release(); } catch (Throwable ignored) { }
        }
    }

    private void showBootReveal(FrameLayout scene) {
        if (!animationsEnabled()) return;
        FrameLayout boot = new FrameLayout(this);
        boot.setBackgroundColor(BG);
        boot.setClickable(true);
        scene.addView(boot, new FrameLayout.LayoutParams(-1, -1));
        boot.addView(new CyberGridView(this), new FrameLayout.LayoutParams(-1, -1));

        AccentFrameLayout frame = new AccentFrameLayout(this);
        frame.setReveal(0f);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(dp(650), dp(650), Gravity.CENTER);
        boot.addView(frame, frameParams);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        frame.addView(center, new FrameLayout.LayoutParams(-1, -1));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.edge_cityray_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        center.addView(logo, new LinearLayout.LayoutParams(dp(390), dp(390)));
        TextView name = text("LIVE WALLPAPER SYSTEM", 21, TEXT, Typeface.BOLD);
        name.setLetterSpacing(0.16f);
        center.addView(name, row(-2, -2, 0, 14, 0, 8));
        TextView ready = text("INITIALIZING CITYRAY EXPERIENCE", 15, CYAN, Typeface.BOLD);
        ready.setLetterSpacing(0.12f);
        center.addView(ready);

        center.setAlpha(0f);
        center.setScaleX(0.9f);
        center.setScaleY(0.9f);
        center.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(new DecelerateInterpolator()).start();
        frame.animate().setDuration(360).setUpdateListener(animation ->
                frame.setReveal(animation.getAnimatedFraction())).start();

        Runnable dismiss = () -> boot.animate().alpha(0f).setDuration(240)
                .withEndAction(() -> scene.removeView(boot)).start();
        boot.setOnClickListener(v -> dismiss.run());
        boot.postDelayed(dismiss, 1250);
    }

    private void pulsePreviewFrame() {
        if (!animationsEnabled()) return;
        previewFrame.setScaleX(0.992f);
        previewFrame.setScaleY(0.992f);
        previewFrame.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void revealModal(View card) {
        if (!animationsEnabled()) return;
        card.setAlpha(0f);
        card.setScaleX(.96f);
        card.setScaleY(.96f);
        card.setTranslationY(dp(18));
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private boolean animationsEnabled() {
        try {
            return Settings.Global.getFloat(getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Throwable ignored) { return true; }
    }

    private void setStatus(String message, int color) {
        status.setText(message);
        statusDot.setTextColor(color);
    }

    private Button actionButton(String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setAllCaps(false);
        button.setLetterSpacing(0.06f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(primary ? BG : TEXT);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        button.setContentDescription(label);
        button.setStateListAnimator(null);
        button.setOnTouchListener((view, event) -> {
            if (!view.isEnabled() || !animationsEnabled()) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(.985f).scaleY(.985f).alpha(.9f)
                        .setDuration(90).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                new CornerCutDrawable(primary ? CYAN_PRESS : Color.rgb(17, 54, 76),
                        CYAN_PRESS, dp(2), dp(18)));
        states.addState(new int[]{-android.R.attr.state_enabled},
                new CornerCutDrawable(Color.rgb(19, 38, 49), Color.rgb(47, 76, 91),
                        dp(1), dp(18)));
        states.addState(new int[]{}, new CornerCutDrawable(primary ? CYAN : Color.rgb(8, 30, 45),
                primary ? CYAN : BLUE, dp(primary ? 0 : 2), dp(18)));
        button.setBackground(states);
        return button;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private LinearLayout.LayoutParams weightedRow(float weight, int height,
                                                   int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, weight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams row(int width, int height,
                                          int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Throwable ignored) { return 0; }
    }

    private long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Throwable ignored) { return 0; }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
