package io.github.hairpin01.metrolistlyricswidget;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

import java.lang.reflect.Method;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 40;
    private static final long KARAOKE_PREVIEW_DURATION_MS = 5_600L;
    private static final long WIDGET_REFRESH_INTERVAL_MS = 90L;
    private static final String KARAOKE_PREVIEW_TEXT = "Слова оживают по слогам";
    private static final int[] KARAOKE_PREVIEW_START = {0, 3, 6, 9, 11, 14, 17, 20};
    private static final int[] KARAOKE_PREVIEW_END = {3, 5, 9, 11, 13, 16, 20, 23};
    private static final long[] KARAOKE_PREVIEW_START_MS = {
            300, 800, 1400, 1950, 2450, 3100, 3800, 4400
    };
    private static final long[] KARAOKE_PREVIEW_END_MS = {
            800, 1250, 1950, 2450, 2900, 3500, 4400, 5000
    };

    private View root;
    private RadioGroup colorGroup;
    private RadioGroup backgroundGroup;
    private RadioGroup progressColorGroup;
    private RadioGroup karaokeColorGroup;
    private RadioGroup karaokeActiveColorGroup;
    private RadioGroup karaokeHighlightModeGroup;
    private LinearLayout customColorsContainer;
    private LinearLayout progressControlsContainer;
    private LinearLayout progressCustomColorContainer;
    private LinearLayout karaokeControlsContainer;
    private LinearLayout karaokeCustomColorContainer;
    private LinearLayout karaokeActiveColorContainer;
    private LinearLayout karaokeActiveCustomColorContainer;
    private LinearLayout karaokePopStrengthContainer;
    private View imagePickerContainer;
    private EditText surfaceInput;
    private EditText foregroundInput;
    private EditText accentInput;
    private EditText progressColorInput;
    private EditText karaokeColorInput;
    private EditText karaokeActiveColorInput;
    private MaterialSwitch karaokeSeparateActiveColorSwitch;
    private MaterialSwitch karaokePopSwitch;
    private MaterialCardView karaokePreviewCard;
    private TextView karaokePreviewCurrent;
    private Slider karaokePreviewSlider;
    private MaterialButton karaokePreviewPlay;
    private ValueAnimator karaokePreviewAnimator;
    private ThemePalette karaokePreviewPalette;
    private boolean karaokePreviewPlaying = true;
    private boolean karaokePreviewDragging;
    private long karaokePreviewPositionMs;
    private final Handler widgetRefreshHandler = new Handler(Looper.getMainLooper());
    private boolean widgetRefreshScheduled;
    private final Runnable widgetRefreshRunnable = () -> {
        widgetRefreshScheduled = false;
        WidgetState.updateAll(MainActivity.this);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.main_root);
        configureSystemBars();
        bindHeader();
        bindColorSettings();
        bindBackgroundSettings();
        bindFeatureSettings();
        bindKaraokeSettings();
        bindTextSettings();
        bindActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        karaokePreviewPalette = null;
        updateKaraokePreview();
        if (karaokePreviewPlaying) resumeKaraokePreview();
        requestTrackAccentRefresh();
    }

    @Override
    protected void onPause() {
        if (karaokePreviewAnimator != null && karaokePreviewAnimator.isStarted()
                && !karaokePreviewAnimator.isPaused()) {
            karaokePreviewAnimator.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        widgetRefreshHandler.removeCallbacks(widgetRefreshRunnable);
        if (karaokePreviewAnimator != null) karaokePreviewAnimator.cancel();
        super.onDestroy();
    }

    private void configureSystemBars() {
        int surface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface, Color.BLACK);
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(surface);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller == null) return;
        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        int appearance = Color.luminance(surface) > 0.5f ? mask : 0;
        controller.setSystemBarsAppearance(appearance, mask);
    }

    private void bindHeader() {
        TextView status = findViewById(R.id.module_status);
        status.setText(metroListStatus());
    }

    private void bindColorSettings() {
        colorGroup = findViewById(R.id.color_source_group);
        customColorsContainer = findViewById(R.id.custom_colors_container);
        surfaceInput = findViewById(R.id.custom_surface_input);
        foregroundInput = findViewById(R.id.custom_foreground_input);
        accentInput = findViewById(R.id.custom_accent_input);

        surfaceInput.setText(hex(WidgetSettings.customSurface(this)));
        foregroundInput.setText(hex(WidgetSettings.customForeground(this)));
        accentInput.setText(hex(WidgetSettings.customAccent(this)));

        int source = WidgetSettings.colorSource(this);
        colorGroup.check(source == WidgetSettings.COLOR_WALLPAPER
                ? R.id.color_wallpaper
                : source == WidgetSettings.COLOR_CUSTOM
                ? R.id.color_custom
                : source == WidgetSettings.COLOR_TRACK
                ? R.id.color_track
                : R.id.color_system);
        updateColorControls(source);

        colorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int value = checkedId == R.id.color_wallpaper
                    ? WidgetSettings.COLOR_WALLPAPER
                    : checkedId == R.id.color_custom
                    ? WidgetSettings.COLOR_CUSTOM
                    : checkedId == R.id.color_track
                    ? WidgetSettings.COLOR_TRACK
                    : WidgetSettings.COLOR_SYSTEM;
            WidgetSettings.setColorSource(this, value);
            updateColorControls(value);
            karaokePreviewPalette = null;
            updateKaraokePreview();
            if (value == WidgetSettings.COLOR_TRACK) requestTrackAccentRefresh();
            refreshWidgets();
        });

        findViewById(R.id.save_custom_colors).setOnClickListener(view -> saveCustomColors());
    }

    private void updateColorControls(int source) {
        customColorsContainer.setVisibility(
                source == WidgetSettings.COLOR_CUSTOM ? View.VISIBLE : View.GONE);
    }

    private void saveCustomColors() {
        try {
            int surface = parseColor(surfaceInput.getText().toString());
            int foreground = parseColor(foregroundInput.getText().toString());
            int accent = parseColor(accentInput.getText().toString());
            WidgetSettings.setCustomColors(this, surface, foreground, accent);
            WidgetSettings.setColorSource(this, WidgetSettings.COLOR_CUSTOM);
            colorGroup.check(R.id.color_custom);
            karaokePreviewPalette = null;
            updateKaraokePreview();
            refreshWidgets();
            showMessage("Свои цвета применены");
        } catch (IllegalArgumentException error) {
            showMessage("Укажи цвет в формате #RRGGBB");
        }
    }

    private void bindBackgroundSettings() {
        backgroundGroup = findViewById(R.id.background_mode_group);
        imagePickerContainer = findViewById(R.id.image_picker_container);

        int mode = WidgetSettings.backgroundMode(this);
        backgroundGroup.check(mode == WidgetSettings.BACKGROUND_ARTWORK
                ? R.id.background_artwork
                : mode == WidgetSettings.BACKGROUND_IMAGE
                ? R.id.background_image
                : R.id.background_color);
        updateBackgroundControls(mode);

        backgroundGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int value = checkedId == R.id.background_artwork
                    ? WidgetSettings.BACKGROUND_ARTWORK
                    : checkedId == R.id.background_image
                    ? WidgetSettings.BACKGROUND_IMAGE
                    : WidgetSettings.BACKGROUND_COLOR;
            WidgetSettings.setBackgroundMode(this, value);
            updateBackgroundControls(value);
            refreshWidgets();
            if (value == WidgetSettings.BACKGROUND_IMAGE
                    && WidgetSettings.imageUri(this).isEmpty()) {
                showMessage("Выбери изображение для фона");
            }
        });

        findViewById(R.id.pick_image).setOnClickListener(view -> chooseImage());

        bindSlider(
                findViewById(R.id.opacity_slider),
                findViewById(R.id.opacity_value),
                WidgetSettings.opacity(this),
                "%",
                value -> WidgetSettings.setOpacity(this, value));
        bindSlider(
                findViewById(R.id.dimming_slider),
                findViewById(R.id.dimming_value),
                WidgetSettings.dimming(this),
                "%",
                value -> WidgetSettings.setDimming(this, value));
    }

    private void updateBackgroundControls(int mode) {
        imagePickerContainer.setVisibility(
                mode == WidgetSettings.BACKGROUND_IMAGE ? View.VISIBLE : View.GONE);
        TextView selectedImage = findViewById(R.id.selected_image_status);
        selectedImage.setText(WidgetSettings.imageUri(this).isEmpty()
                ? "Изображение ещё не выбрано"
                : "Изображение выбрано");
    }

    private void bindFeatureSettings() {
        MaterialSwitch showCover = findViewById(R.id.show_cover_switch);
        MaterialSwitch showProgress = findViewById(R.id.show_progress_switch);
        MaterialSwitch animateLines = findViewById(R.id.animate_lines_switch);
        progressControlsContainer = findViewById(R.id.progress_controls_container);
        progressCustomColorContainer = findViewById(R.id.progress_custom_color_container);
        progressColorGroup = findViewById(R.id.progress_color_group);
        progressColorInput = findViewById(R.id.progress_color_input);

        showCover.setChecked(WidgetSettings.showCover(this));
        showProgress.setChecked(WidgetSettings.showProgress(this));
        animateLines.setChecked(WidgetSettings.animateLines(this));
        progressColorInput.setText(hex(WidgetSettings.customProgressColor(this)));
        int progressColorMode = WidgetSettings.progressColorMode(this);
        progressColorGroup.check(progressColorMode == WidgetSettings.PROGRESS_COLOR_CUSTOM
                ? R.id.progress_color_custom
                : progressColorMode == WidgetSettings.PROGRESS_COLOR_TRACK
                ? R.id.progress_color_track : R.id.progress_color_accent);
        updateProgressControls(showProgress.isChecked(), progressColorMode);

        showCover.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setShowCover(this, checked);
            refreshWidgets();
        });
        showProgress.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setShowProgress(this, checked);
            updateProgressControls(checked, WidgetSettings.progressColorMode(this));
            refreshWidgets();
        });
        progressColorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = checkedId == R.id.progress_color_custom
                    ? WidgetSettings.PROGRESS_COLOR_CUSTOM
                    : checkedId == R.id.progress_color_track
                    ? WidgetSettings.PROGRESS_COLOR_TRACK
                    : WidgetSettings.PROGRESS_COLOR_ACCENT;
            WidgetSettings.setProgressColorMode(this, mode);
            updateProgressControls(showProgress.isChecked(), mode);
            if (mode == WidgetSettings.PROGRESS_COLOR_TRACK) requestTrackAccentRefresh();
            refreshWidgets();
        });
        findViewById(R.id.save_progress_color).setOnClickListener(
                view -> saveProgressColor());
        bindSlider(
                findViewById(R.id.progress_track_opacity_slider),
                findViewById(R.id.progress_track_opacity_value),
                WidgetSettings.progressTrackOpacity(this),
                "%",
                value -> WidgetSettings.setProgressTrackOpacity(this, value));
        bindSlider(
                findViewById(R.id.progress_height_slider),
                findViewById(R.id.progress_height_value),
                WidgetSettings.progressHeightDp(this),
                " dp",
                value -> WidgetSettings.setProgressHeightDp(this, value));
        animateLines.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setAnimateLines(this, checked);
            refreshWidgets();
        });
    }

    private void updateProgressControls(boolean enabled, int colorMode) {
        progressControlsContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        progressCustomColorContainer.setVisibility(enabled
                && colorMode == WidgetSettings.PROGRESS_COLOR_CUSTOM
                ? View.VISIBLE : View.GONE);
    }

    private void saveProgressColor() {
        try {
            int color = parseColor(progressColorInput.getText().toString());
            WidgetSettings.setCustomProgressColor(this, color);
            WidgetSettings.setProgressColorMode(this, WidgetSettings.PROGRESS_COLOR_CUSTOM);
            progressColorGroup.check(R.id.progress_color_custom);
            updateProgressControls(WidgetSettings.showProgress(this),
                    WidgetSettings.PROGRESS_COLOR_CUSTOM);
            refreshWidgets();
            showMessage("Цвет прогресс-бара применён");
        } catch (IllegalArgumentException error) {
            showMessage("Укажи цвет в формате #RRGGBB");
        }
    }

    private void bindKaraokeSettings() {
        MaterialSwitch enabled = findViewById(R.id.karaoke_enabled_switch);
        MaterialSwitch bold = findViewById(R.id.karaoke_bold_switch);
        karaokePopSwitch = findViewById(R.id.karaoke_pop_switch);
        karaokeSeparateActiveColorSwitch = findViewById(
                R.id.karaoke_separate_active_color_switch);
        karaokeControlsContainer = findViewById(R.id.karaoke_controls_container);
        karaokeCustomColorContainer = findViewById(R.id.karaoke_custom_color_container);
        karaokeActiveColorContainer = findViewById(R.id.karaoke_active_color_container);
        karaokeActiveCustomColorContainer = findViewById(
                R.id.karaoke_active_custom_color_container);
        karaokePopStrengthContainer = findViewById(R.id.karaoke_pop_strength_container);
        karaokeColorGroup = findViewById(R.id.karaoke_color_group);
        karaokeActiveColorGroup = findViewById(R.id.karaoke_active_color_group);
        karaokeHighlightModeGroup = findViewById(R.id.karaoke_highlight_mode_group);
        karaokeColorInput = findViewById(R.id.karaoke_color_input);
        karaokeActiveColorInput = findViewById(R.id.karaoke_active_color_input);

        enabled.setChecked(WidgetSettings.karaokeEnabled(this));
        bold.setChecked(WidgetSettings.karaokeBold(this));
        karaokePopSwitch.setChecked(WidgetSettings.karaokePop(this));
        karaokeSeparateActiveColorSwitch.setChecked(
                WidgetSettings.karaokeSeparateActiveColor(this));
        karaokeColorInput.setText(hex(WidgetSettings.customKaraokeColor(this)));
        karaokeActiveColorInput.setText(hex(WidgetSettings.customKaraokeActiveColor(this)));

        int colorMode = WidgetSettings.karaokeColorMode(this);
        karaokeColorGroup.check(colorMode == WidgetSettings.KARAOKE_COLOR_CUSTOM
                ? R.id.karaoke_color_custom
                : colorMode == WidgetSettings.KARAOKE_COLOR_TRACK
                ? R.id.karaoke_color_track : R.id.karaoke_color_accent);
        int activeColorMode = WidgetSettings.karaokeActiveColorMode(this);
        karaokeActiveColorGroup.check(activeColorMode == WidgetSettings.KARAOKE_COLOR_CUSTOM
                ? R.id.karaoke_active_color_custom
                : activeColorMode == WidgetSettings.KARAOKE_COLOR_TRACK
                ? R.id.karaoke_active_color_track : R.id.karaoke_active_color_accent);
        int highlightMode = WidgetSettings.karaokeMode(this);
        karaokeHighlightModeGroup.check(
                highlightMode == WidgetSettings.KARAOKE_MODE_ACTIVE_TOKEN
                        ? R.id.karaoke_mode_active_token
                        : highlightMode == WidgetSettings.KARAOKE_MODE_ACTIVE_WORD
                        ? R.id.karaoke_mode_active_word : R.id.karaoke_mode_trail);
        updateKaraokeControls(enabled.isChecked(), colorMode);

        enabled.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setKaraokeEnabled(this, checked);
            updateKaraokeControls(checked, WidgetSettings.karaokeColorMode(this));
            updateKaraokePreview();
            refreshWidgets();
        });
        karaokeHighlightModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = checkedId == R.id.karaoke_mode_active_token
                    ? WidgetSettings.KARAOKE_MODE_ACTIVE_TOKEN
                    : checkedId == R.id.karaoke_mode_active_word
                    ? WidgetSettings.KARAOKE_MODE_ACTIVE_WORD
                    : WidgetSettings.KARAOKE_MODE_TRAIL;
            WidgetSettings.setKaraokeMode(this, mode);
            updateKaraokePreview();
            refreshWidgets();
        });
        bold.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setKaraokeBold(this, checked);
            updateKaraokePreview();
            refreshWidgets();
        });
        karaokePopSwitch.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setKaraokePop(this, checked);
            updateKaraokeControls(enabled.isChecked(), WidgetSettings.karaokeColorMode(this));
            updateKaraokePreview();
            refreshWidgets();
        });
        karaokeSeparateActiveColorSwitch.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setKaraokeSeparateActiveColor(this, checked);
            updateKaraokeControls(enabled.isChecked(), WidgetSettings.karaokeColorMode(this));
            updateKaraokePreview();
            refreshWidgets();
        });
        karaokeColorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = checkedId == R.id.karaoke_color_custom
                    ? WidgetSettings.KARAOKE_COLOR_CUSTOM
                    : checkedId == R.id.karaoke_color_track
                    ? WidgetSettings.KARAOKE_COLOR_TRACK
                    : WidgetSettings.KARAOKE_COLOR_ACCENT;
            WidgetSettings.setKaraokeColorMode(this, mode);
            updateKaraokeControls(enabled.isChecked(), mode);
            updateKaraokePreview();
            if (mode == WidgetSettings.KARAOKE_COLOR_TRACK) requestTrackAccentRefresh();
            refreshWidgets();
        });
        karaokeActiveColorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = checkedId == R.id.karaoke_active_color_custom
                    ? WidgetSettings.KARAOKE_COLOR_CUSTOM
                    : checkedId == R.id.karaoke_active_color_track
                    ? WidgetSettings.KARAOKE_COLOR_TRACK
                    : WidgetSettings.KARAOKE_COLOR_ACCENT;
            WidgetSettings.setKaraokeActiveColorMode(this, mode);
            updateKaraokeControls(enabled.isChecked(),
                    WidgetSettings.karaokeColorMode(this));
            updateKaraokePreview();
            if (mode == WidgetSettings.KARAOKE_COLOR_TRACK) requestTrackAccentRefresh();
            refreshWidgets();
        });
        findViewById(R.id.save_karaoke_color).setOnClickListener(
                view -> saveKaraokeColor());
        findViewById(R.id.save_karaoke_active_color).setOnClickListener(
                view -> saveKaraokeActiveColor());
        bindSlider(
                findViewById(R.id.karaoke_unsung_opacity_slider),
                findViewById(R.id.karaoke_unsung_opacity_value),
                WidgetSettings.karaokeUnsungOpacity(this),
                "%",
                value -> WidgetSettings.setKaraokeUnsungOpacity(this, value));
        bindSlider(
                findViewById(R.id.karaoke_pop_strength_slider),
                findViewById(R.id.karaoke_pop_strength_value),
                WidgetSettings.karaokePopStrength(this),
                "%",
                value -> WidgetSettings.setKaraokePopStrength(this, value));
        bindTimingOffset();
        bindKaraokePreview();
    }

    private void updateKaraokeControls(boolean enabled, int colorMode) {
        karaokeControlsContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
        karaokeCustomColorContainer.setVisibility(enabled
                && colorMode == WidgetSettings.KARAOKE_COLOR_CUSTOM
                ? View.VISIBLE : View.GONE);
        boolean separateActive = enabled && karaokeSeparateActiveColorSwitch.isChecked();
        karaokeActiveColorContainer.setVisibility(separateActive ? View.VISIBLE : View.GONE);
        karaokeActiveCustomColorContainer.setVisibility(separateActive
                && WidgetSettings.karaokeActiveColorMode(this)
                == WidgetSettings.KARAOKE_COLOR_CUSTOM ? View.VISIBLE : View.GONE);
        karaokePopStrengthContainer.setVisibility(enabled && karaokePopSwitch.isChecked()
                ? View.VISIBLE : View.GONE);
    }

    private void saveKaraokeColor() {
        try {
            int color = parseColor(karaokeColorInput.getText().toString());
            WidgetSettings.setCustomKaraokeColor(this, color);
            WidgetSettings.setKaraokeColorMode(this, WidgetSettings.KARAOKE_COLOR_CUSTOM);
            karaokeColorGroup.check(R.id.karaoke_color_custom);
            updateKaraokeControls(WidgetSettings.karaokeEnabled(this),
                    WidgetSettings.KARAOKE_COLOR_CUSTOM);
            updateKaraokePreview();
            refreshWidgets();
            showMessage("Цвет пройденного текста применён");
        } catch (IllegalArgumentException error) {
            showMessage("Укажи цвет в формате #RRGGBB");
        }
    }

    private void saveKaraokeActiveColor() {
        try {
            int color = parseColor(karaokeActiveColorInput.getText().toString());
            WidgetSettings.setCustomKaraokeActiveColor(this, color);
            WidgetSettings.setKaraokeSeparateActiveColor(this, true);
            WidgetSettings.setKaraokeActiveColorMode(this, WidgetSettings.KARAOKE_COLOR_CUSTOM);
            karaokeSeparateActiveColorSwitch.setChecked(true);
            karaokeActiveColorGroup.check(R.id.karaoke_active_color_custom);
            updateKaraokeControls(WidgetSettings.karaokeEnabled(this),
                    WidgetSettings.karaokeColorMode(this));
            updateKaraokePreview();
            refreshWidgets();
            showMessage("Цвет активного фрагмента применён");
        } catch (IllegalArgumentException error) {
            showMessage("Укажи цвет в формате #RRGGBB");
        }
    }

    private void bindTimingOffset() {
        Slider slider = findViewById(R.id.karaoke_timing_offset_slider);
        TextView valueLabel = findViewById(R.id.karaoke_timing_offset_value);
        int value = WidgetSettings.lyricsTimingOffsetMs(this);
        slider.setValue(value);
        valueLabel.setText(formatOffset(value));
        slider.addOnChangeListener((control, current, fromUser) -> {
            int rounded = Math.round(current / 50f) * 50;
            valueLabel.setText(formatOffset(rounded));
            if (fromUser) {
                WidgetSettings.setLyricsTimingOffsetMs(this, rounded);
                updateKaraokePreview();
                scheduleWidgetRefresh();
            }
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider control) {
            }

            @Override
            public void onStopTrackingTouch(Slider control) {
                int rounded = Math.round(control.getValue() / 50f) * 50;
                WidgetSettings.setLyricsTimingOffsetMs(MainActivity.this, rounded);
                updateKaraokePreview();
                refreshWidgets();
            }
        });
        findViewById(R.id.karaoke_timing_offset_reset).setOnClickListener(view -> {
            WidgetSettings.setLyricsTimingOffsetMs(this, 0);
            slider.setValue(0f);
            valueLabel.setText(formatOffset(0));
            updateKaraokePreview();
            refreshWidgets();
        });
    }

    private void bindKaraokePreview() {
        karaokePreviewCard = findViewById(R.id.karaoke_preview_card);
        karaokePreviewCurrent = findViewById(R.id.karaoke_preview_current);
        karaokePreviewSlider = findViewById(R.id.karaoke_preview_slider);
        karaokePreviewPlay = findViewById(R.id.karaoke_preview_play);
        karaokePreviewSlider.setValue(0f);
        karaokePreviewSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            karaokePreviewPositionMs = Math.round(
                    KARAOKE_PREVIEW_DURATION_MS * value / 100f);
            if (karaokePreviewAnimator != null && karaokePreviewAnimator.isStarted()) {
                karaokePreviewAnimator.setCurrentPlayTime(karaokePreviewPositionMs);
            }
            updateKaraokePreview();
        });
        karaokePreviewSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                karaokePreviewDragging = true;
                if (karaokePreviewAnimator != null && karaokePreviewAnimator.isStarted()
                        && !karaokePreviewAnimator.isPaused()) {
                    karaokePreviewAnimator.pause();
                }
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                karaokePreviewDragging = false;
                updateKaraokePreview();
                if (karaokePreviewPlaying) resumeKaraokePreview();
            }
        });
        karaokePreviewPlay.setOnClickListener(view -> {
            karaokePreviewPlaying = !karaokePreviewPlaying;
            if (karaokePreviewPlaying) {
                resumeKaraokePreview();
            } else if (karaokePreviewAnimator != null
                    && karaokePreviewAnimator.isStarted()) {
                karaokePreviewAnimator.pause();
            }
            updatePreviewPlayButton();
        });

        karaokePreviewAnimator = ValueAnimator.ofInt(0, (int) KARAOKE_PREVIEW_DURATION_MS);
        karaokePreviewAnimator.setDuration(KARAOKE_PREVIEW_DURATION_MS);
        karaokePreviewAnimator.setRepeatCount(ValueAnimator.INFINITE);
        karaokePreviewAnimator.setInterpolator(new LinearInterpolator());
        karaokePreviewAnimator.addUpdateListener(animation -> {
            if (karaokePreviewDragging) return;
            karaokePreviewPositionMs = (Integer) animation.getAnimatedValue();
            karaokePreviewSlider.setValue(100f * karaokePreviewPositionMs
                    / KARAOKE_PREVIEW_DURATION_MS);
            updateKaraokePreview();
        });
        updatePreviewPlayButton();
        updateKaraokePreview();
    }

    private void resumeKaraokePreview() {
        if (karaokePreviewAnimator == null || karaokePreviewDragging) return;
        if (!karaokePreviewAnimator.isStarted()) {
            karaokePreviewAnimator.start();
            karaokePreviewAnimator.setCurrentPlayTime(karaokePreviewPositionMs);
        } else if (karaokePreviewAnimator.isPaused()) {
            karaokePreviewAnimator.resume();
        }
    }

    private void updatePreviewPlayButton() {
        if (karaokePreviewPlay != null) {
            karaokePreviewPlay.setText(karaokePreviewPlaying ? "Пауза" : "Запустить");
        }
    }

    private void updateKaraokePreview() {
        if (karaokePreviewCurrent == null) return;
        long effectivePosition = karaokePreviewPositionMs
                + WidgetSettings.lyricsTimingOffsetMs(this);
        int highlightEnd = -1;
        int activeStart = -1;
        int activeEnd = -1;
        for (int i = 0; i < KARAOKE_PREVIEW_START.length; i++) {
            if (KARAOKE_PREVIEW_START_MS[i] <= effectivePosition) {
                highlightEnd = Math.max(highlightEnd, KARAOKE_PREVIEW_END[i]);
                if (effectivePosition < KARAOKE_PREVIEW_END_MS[i]) {
                    activeStart = KARAOKE_PREVIEW_START[i];
                    activeEnd = KARAOKE_PREVIEW_END[i];
                }
            }
        }
        ThemePalette palette = karaokePreviewPalette;
        if (palette == null || WidgetSettings.usesTrackAccent(this)) {
            palette = ThemePalette.resolve(this);
            karaokePreviewPalette = palette;
        }
        String trackId = WidgetState.currentTrackId(this);
        String artwork = WidgetState.currentArtwork(this);
        int trackAccent = ThemePalette.trackAccent(
                this, trackId, artwork, palette.accent);
        int highlightColor = WidgetSettings.karaokeColor(
                this, palette.accent, trackAccent);
        int activeColor = WidgetSettings.karaokeActiveColor(
                this, highlightColor, palette.accent, trackAccent);
        karaokePreviewCard.setCardBackgroundColor(palette.surface);
        karaokePreviewCurrent.setTextColor(palette.foreground);
        karaokePreviewCurrent.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                WidgetSettings.textSize(this));
        karaokePreviewCurrent.setText(WidgetState.karaokeText(this,
                KARAOKE_PREVIEW_TEXT, true, highlightEnd, activeStart, activeEnd,
                palette.foreground, highlightColor, activeColor));
    }

    private void requestTrackAccentRefresh() {
        if (!WidgetSettings.usesTrackAccent(this)) return;
        WidgetState.ensureCurrentArtwork(this, () -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            karaokePreviewPalette = null;
            updateKaraokePreview();
            refreshWidgets();
        }));
    }

    private static String formatOffset(int value) {
        return (value > 0 ? "+" : "") + value + " мс";
    }

    private void bindTextSettings() {
        bindSlider(
                findViewById(R.id.text_size_slider),
                findViewById(R.id.text_size_value),
                WidgetSettings.textSize(this),
                " sp",
                value -> WidgetSettings.setTextSize(this, value));

        RadioGroup alignment = findViewById(R.id.text_alignment_group);
        int textAlignment = WidgetSettings.textAlignment(this);
        alignment.check(textAlignment == WidgetSettings.TEXT_ALIGN_CENTER
                ? R.id.text_align_center
                : textAlignment == WidgetSettings.TEXT_ALIGN_END
                ? R.id.text_align_end : R.id.text_align_start);
        alignment.setOnCheckedChangeListener((group, checkedId) -> {
            int value = checkedId == R.id.text_align_center
                    ? WidgetSettings.TEXT_ALIGN_CENTER
                    : checkedId == R.id.text_align_end
                    ? WidgetSettings.TEXT_ALIGN_END : WidgetSettings.TEXT_ALIGN_START;
            WidgetSettings.setTextAlignment(this, value);
            refreshWidgets();
        });

        MaterialSwitch autoPadding = findViewById(R.id.auto_vertical_padding_switch);
        LinearLayout manualPadding = findViewById(R.id.manual_vertical_padding_container);
        autoPadding.setChecked(WidgetSettings.autoVerticalPadding(this));
        manualPadding.setVisibility(autoPadding.isChecked() ? View.GONE : View.VISIBLE);
        autoPadding.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setAutoVerticalPadding(this, checked);
            manualPadding.setVisibility(checked ? View.GONE : View.VISIBLE);
            refreshWidgets();
        });
        bindSlider(
                findViewById(R.id.top_padding_slider),
                findViewById(R.id.top_padding_value),
                WidgetSettings.topPaddingDp(this),
                " dp",
                value -> WidgetSettings.setTopPaddingDp(this, value));
        bindSlider(
                findViewById(R.id.bottom_padding_slider),
                findViewById(R.id.bottom_padding_value),
                WidgetSettings.bottomPaddingDp(this),
                " dp",
                value -> WidgetSettings.setBottomPaddingDp(this, value));
    }

    private void bindSlider(Slider slider, TextView valueLabel, int value,
                            String suffix, ValueSaver saver) {
        slider.setValue(value);
        valueLabel.setText(value + suffix);
        slider.addOnChangeListener((control, current, fromUser) -> {
            int rounded = Math.round(current);
            valueLabel.setText(rounded + suffix);
            if (fromUser) {
                saver.save(rounded);
                updateKaraokePreview();
                scheduleWidgetRefresh();
            }
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider control) {
            }

            @Override
            public void onStopTrackingTouch(Slider control) {
                saver.save(Math.round(control.getValue()));
                updateKaraokePreview();
                refreshWidgets();
            }
        });
    }

    private void bindActions() {
        findViewById(R.id.action_refresh).setOnClickListener(view -> {
            refreshWidgets();
            showMessage("Виджет обновлён");
        });
        findViewById(R.id.action_demo).setOnClickListener(view -> sendDemo());
        findViewById(R.id.action_pin).setOnClickListener(view -> requestPinWidget());
        findViewById(R.id.action_open_metrolist).setOnClickListener(view -> openMetroList());
        findViewById(R.id.action_reset).setOnClickListener(view -> confirmReset());
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Сбросить оформление?")
                .setMessage("Цвета, фон, компоновка, караоке, анимации и размер текста вернутся к значениям по умолчанию.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить", (dialog, which) -> {
                    WidgetSettings.reset(this);
                    refreshWidgets();
                    showMessage("Настройки сброшены");
                    root.postDelayed(this::recreate, 250L);
                })
                .show();
    }

    private String metroListStatus() {
        String moduleVersion = "";
        try {
            PackageInfo own = getPackageManager().getPackageInfo(getPackageName(), 0);
            moduleVersion = own.versionName == null ? "" : own.versionName;
        } catch (Throwable ignored) {
        }
        try {
            PackageInfo info = getPackageManager().getPackageInfo(Constants.TARGET_PACKAGE, 0);
            return "MetroList " + info.versionName + " найден  •  модуль " + moduleVersion;
        } catch (Throwable error) {
            return "MetroList не найден  •  модуль " + moduleVersion;
        }
    }

    private void chooseImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_IMAGE);
        } catch (Throwable error) {
            showMessage("Не удалось открыть выбор изображения");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Throwable ignored) {
        }
        WidgetSettings.setImageUri(this, uri.toString());
        WidgetSettings.setBackgroundMode(this, WidgetSettings.BACKGROUND_IMAGE);
        backgroundGroup.check(R.id.background_image);
        updateBackgroundControls(WidgetSettings.BACKGROUND_IMAGE);
        refreshWidgets();
        showMessage("Изображение установлено");
    }

    private void requestPinWidget() {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            ComponentName provider = new ComponentName(this, LyricsWidgetProvider.class);
            Method method = AppWidgetManager.class.getMethod(
                    "requestPinAppWidget", ComponentName.class, Bundle.class, PendingIntent.class);
            Object result = method.invoke(manager, provider, null, null);
            if (Boolean.FALSE.equals(result)) {
                showMessage("Добавь виджет через меню рабочего стола");
            }
        } catch (Throwable error) {
            showMessage("Добавь «Текст MetroList» через меню виджетов");
        }
    }

    private void sendDemo() {
        Intent intent = new Intent(Constants.ACTION_UPDATE);
        intent.setComponent(new ComponentName(this, LyricsWidgetProvider.class));
        intent.putExtra(Constants.EXTRA_TRACK_ID, "dQw4w9WgXcQ");
        intent.putExtra(Constants.EXTRA_TITLE, "MetroList");
        intent.putExtra(Constants.EXTRA_ARTIST, "демо");
        intent.putExtra(Constants.EXTRA_ARTWORK, "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
        intent.putExtra(Constants.EXTRA_PREVIOUS, "Предыдущая строка ближе");
        intent.putExtra(Constants.EXTRA_PREVIOUS_CONTEXT,
                "Самая ранняя строка\nЕщё одна строка выше\nПредыдущая строка ближе");
        intent.putExtra(Constants.EXTRA_CURRENT, "Демо караоке по слогам");
        intent.putExtra(Constants.EXTRA_NEXT, "Следующая тоже рядом");
        intent.putExtra(Constants.EXTRA_NEXT_CONTEXT,
                "Следующая тоже рядом\nЕщё одна строка ниже\nСамая поздняя строка");
        intent.putExtra(Constants.EXTRA_HIGHLIGHT_END, 12);
        intent.putExtra(Constants.EXTRA_ACTIVE_START, 5);
        intent.putExtra(Constants.EXTRA_ACTIVE_END, 12);
        intent.putExtra(Constants.EXTRA_KARAOKE, true);
        intent.putExtra(Constants.EXTRA_STATUS, "играет · демо");
        intent.putExtra(Constants.EXTRA_PROVIDER, "демо");
        intent.putExtra(Constants.EXTRA_PLAYING, true);
        intent.putExtra(Constants.EXTRA_POSITION, 78_000L);
        intent.putExtra(Constants.EXTRA_DURATION, 220_000L);
        sendBroadcast(intent);
        showMessage("Демо отправлено в виджет");
    }

    private void openMetroList() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(Constants.TARGET_PACKAGE);
        if (intent == null) {
            showMessage("MetroList не найден");
            return;
        }
        startActivity(intent);
    }

    private void scheduleWidgetRefresh() {
        if (widgetRefreshScheduled) return;
        widgetRefreshScheduled = true;
        widgetRefreshHandler.postDelayed(widgetRefreshRunnable, WIDGET_REFRESH_INTERVAL_MS);
    }

    private void refreshWidgets() {
        widgetRefreshHandler.removeCallbacks(widgetRefreshRunnable);
        widgetRefreshScheduled = false;
        WidgetState.updateAll(this);
    }

    private void showMessage(String message) {
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
    }

    private int parseColor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("#")) value = "#" + value;
        if (value.length() != 7 && value.length() != 9) {
            throw new IllegalArgumentException("bad color");
        }
        return Color.parseColor(value);
    }

    private static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    private interface ValueSaver {
        void save(int value);
    }
}
