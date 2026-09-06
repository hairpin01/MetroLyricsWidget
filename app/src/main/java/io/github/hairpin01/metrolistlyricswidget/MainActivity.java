package io.github.hairpin01.metrolistlyricswidget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

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

    private View root;
    private RadioGroup colorGroup;
    private RadioGroup backgroundGroup;
    private LinearLayout customColorsContainer;
    private View imagePickerContainer;
    private EditText surfaceInput;
    private EditText foregroundInput;
    private EditText accentInput;

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
        bindTextSettings();
        bindActions();
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
                : R.id.color_system);
        updateColorControls(source);

        colorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int value = checkedId == R.id.color_wallpaper
                    ? WidgetSettings.COLOR_WALLPAPER
                    : checkedId == R.id.color_custom
                    ? WidgetSettings.COLOR_CUSTOM
                    : WidgetSettings.COLOR_SYSTEM;
            WidgetSettings.setColorSource(this, value);
            updateColorControls(value);
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

        showCover.setChecked(WidgetSettings.showCover(this));
        showProgress.setChecked(WidgetSettings.showProgress(this));
        animateLines.setChecked(WidgetSettings.animateLines(this));

        showCover.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setShowCover(this, checked);
            refreshWidgets();
        });
        showProgress.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setShowProgress(this, checked);
            refreshWidgets();
        });
        animateLines.setOnCheckedChangeListener((button, checked) -> {
            WidgetSettings.setAnimateLines(this, checked);
            refreshWidgets();
        });
    }

    private void bindTextSettings() {
        bindSlider(
                findViewById(R.id.text_size_slider),
                findViewById(R.id.text_size_value),
                WidgetSettings.textSize(this),
                " sp",
                value -> WidgetSettings.setTextSize(this, value));
    }

    private void bindSlider(Slider slider, TextView valueLabel, int value,
                            String suffix, ValueSaver saver) {
        slider.setValue(value);
        valueLabel.setText(value + suffix);
        slider.addOnChangeListener((control, current, fromUser) ->
                valueLabel.setText(Math.round(current) + suffix));
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider control) {
            }

            @Override
            public void onStopTrackingTouch(Slider control) {
                saver.save(Math.round(control.getValue()));
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
                .setMessage("Цвета, фон, прозрачность и размер текста вернутся к значениям по умолчанию.")
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
        intent.putExtra(Constants.EXTRA_CURRENT, "Текущая строка текста");
        intent.putExtra(Constants.EXTRA_NEXT, "Следующая тоже рядом");
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

    private void refreshWidgets() {
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
