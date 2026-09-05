package io.github.hairpin01.metrolistlyricswidget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 40;
    private static final int ID_COLOR_SYSTEM = 101;
    private static final int ID_COLOR_WALLPAPER = 102;
    private static final int ID_COLOR_CUSTOM = 103;
    private static final int ID_BG_COLOR = 201;
    private static final int ID_BG_ARTWORK = 202;
    private static final int ID_BG_IMAGE = 203;

    private ThemePalette palette;
    private LinearLayout root;
    private EditText surfaceInput;
    private EditText foregroundInput;
    private EditText accentInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = ThemePalette.resolve(this);
        int bar = ThemePalette.blend(palette.surface, Color.BLACK, 0.32f);
        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        root.setBackgroundColor(palette.surface);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("MetroLyrics Widget", 28, palette.accent);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        TextView description = text(
                "Синхронный текст MetroList на рабочем столе. Настройки ниже применяются сразу ко всем виджетам.",
                15,
                palette.secondary);
        description.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(description, descriptionParams);

        TextView target = text(metroListStatus(), 13, palette.muted);
        target.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(target, matchWrap());

        addColorSettings();
        addBackgroundSettings();
        addSizingSettings();
        addElementsSettings();
        addActions();

        setContentView(scroll);
    }

    private void addColorSettings() {
        root.addView(section("Цвета"), sectionParams());
        TextView hint = text(
                "Системная палитра берёт Material You, включая изменения ColorBlendr. Палитра обоев строится отдельно из обоев рабочего стола.",
                13,
                palette.secondary);
        root.addView(hint, matchWrap());

        final RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.addView(radio(ID_COLOR_SYSTEM, "Системная тема / ColorBlendr"));
        group.addView(radio(ID_COLOR_WALLPAPER, "Цвета обоев рабочего стола"));
        group.addView(radio(ID_COLOR_CUSTOM, "Свои цвета"));
        int source = WidgetSettings.colorSource(this);
        group.check(source == WidgetSettings.COLOR_WALLPAPER ? ID_COLOR_WALLPAPER
                : source == WidgetSettings.COLOR_CUSTOM ? ID_COLOR_CUSTOM : ID_COLOR_SYSTEM);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                int value = checkedId == ID_COLOR_WALLPAPER ? WidgetSettings.COLOR_WALLPAPER
                        : checkedId == ID_COLOR_CUSTOM ? WidgetSettings.COLOR_CUSTOM : WidgetSettings.COLOR_SYSTEM;
                WidgetSettings.setColorSource(MainActivity.this, value);
                refreshWidgets();
            }
        });
        root.addView(group, matchWrap());

        surfaceInput = colorInput("#231F27", WidgetSettings.customSurface(this));
        foregroundInput = colorInput("#FFFFFF", WidgetSettings.customForeground(this));
        accentInput = colorInput("#D0BCFF", WidgetSettings.customAccent(this));
        root.addView(text("Свой цвет фона", 12, palette.muted), inputLabelParams());
        root.addView(surfaceInput, inputParams());
        root.addView(text("Свой цвет текста", 12, palette.muted), inputLabelParams());
        root.addView(foregroundInput, inputParams());
        root.addView(text("Свой акцент", 12, palette.muted), inputLabelParams());
        root.addView(accentInput, inputParams());

        Button saveColors = button("Сохранить свои цвета");
        saveColors.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    int surface = parseColor(surfaceInput.getText().toString());
                    int foreground = parseColor(foregroundInput.getText().toString());
                    int accent = parseColor(accentInput.getText().toString());
                    WidgetSettings.setCustomColors(MainActivity.this, surface, foreground, accent);
                    WidgetSettings.setColorSource(MainActivity.this, WidgetSettings.COLOR_CUSTOM);
                    refreshWidgets();
                    Toast.makeText(MainActivity.this, "Свои цвета применены", Toast.LENGTH_SHORT).show();
                    recreate();
                } catch (IllegalArgumentException error) {
                    Toast.makeText(MainActivity.this, "Нужен цвет вида #RRGGBB", Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(saveColors, buttonParams());
    }

    private void addBackgroundSettings() {
        root.addView(section("Фон"), sectionParams());
        final RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        group.addView(radio(ID_BG_COLOR, "Цвет выбранной палитры"));
        group.addView(radio(ID_BG_ARTWORK, "Обложка текущего трека"));
        group.addView(radio(ID_BG_IMAGE, "Своя картинка"));
        int mode = WidgetSettings.backgroundMode(this);
        group.check(mode == WidgetSettings.BACKGROUND_ARTWORK ? ID_BG_ARTWORK
                : mode == WidgetSettings.BACKGROUND_IMAGE ? ID_BG_IMAGE : ID_BG_COLOR);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup ignored, int checkedId) {
                int value = checkedId == ID_BG_ARTWORK ? WidgetSettings.BACKGROUND_ARTWORK
                        : checkedId == ID_BG_IMAGE ? WidgetSettings.BACKGROUND_IMAGE : WidgetSettings.BACKGROUND_COLOR;
                WidgetSettings.setBackgroundMode(MainActivity.this, value);
                refreshWidgets();
                if (value == WidgetSettings.BACKGROUND_IMAGE && WidgetSettings.imageUri(MainActivity.this).length() == 0) {
                    Toast.makeText(MainActivity.this, "Выбери картинку кнопкой ниже", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(group, matchWrap());

        Button pick = button(WidgetSettings.imageUri(this).length() == 0
                ? "Выбрать картинку" : "Сменить выбранную картинку");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { chooseImage(); }
        });
        root.addView(pick, buttonParams());

        addSeek("Непрозрачность фона", WidgetSettings.opacity(this), 0, 100, "%", new ValueSaver() {
            @Override public void save(int value) { WidgetSettings.setOpacity(MainActivity.this, value); }
        });
        addSeek("Затемнение обложки / картинки", WidgetSettings.dimming(this), 0, 90, "%", new ValueSaver() {
            @Override public void save(int value) { WidgetSettings.setDimming(MainActivity.this, value); }
        });
    }

    private void addSizingSettings() {
        root.addView(section("Текст"), sectionParams());
        addSeek("Размер текущей строки", WidgetSettings.textSize(this), 15, 27, " sp", new ValueSaver() {
            @Override public void save(int value) { WidgetSettings.setTextSize(MainActivity.this, value); }
        });
        TextView note = text(
                "Строки теперь собраны плотно: предыдущая, текущая и следующая без растягивания по всей высоте виджета.",
                13,
                palette.secondary);
        root.addView(note, matchWrap());
    }

    private void addElementsSettings() {
        root.addView(section("Элементы"), sectionParams());
        addCheck("Показывать обложку трека", WidgetSettings.showCover(this), new CheckToggler() {
            @Override public void toggle(boolean value) {
                WidgetSettings.setShowCover(MainActivity.this, value);
            }
        });
        addCheck("Показывать прогресс трека", WidgetSettings.showProgress(this), new CheckToggler() {
            @Override public void toggle(boolean value) {
                WidgetSettings.setShowProgress(MainActivity.this, value);
            }
        });
        addCheck("Анимировать смену строк", WidgetSettings.animateLines(this), new CheckToggler() {
            @Override public void toggle(boolean value) {
                WidgetSettings.setAnimateLines(MainActivity.this, value);
            }
        });
    }

    private void addCheck(String label, boolean checked, final CheckToggler toggler) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextSize(14);
        box.setTextColor(palette.foreground);
        box.setButtonTintList(ColorStateList.valueOf(palette.accent));
        box.setChecked(checked);
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean value) {
                toggler.toggle(value);
                refreshWidgets();
            }
        });
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(6), 0, 0);
        root.addView(box, params);
    }

    private interface CheckToggler {
        void toggle(boolean value);
    }

    private void addActions() {
        root.addView(section("Действия"), sectionParams());

        Button apply = button("Обновить виджет сейчас");
        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                refreshWidgets();
                Toast.makeText(MainActivity.this, "Виджет обновлён", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(apply, buttonParams());

        Button demo = button("Показать демо");
        demo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { sendDemo(); }
        });
        root.addView(demo, buttonParams());

        Button pin = button("Закрепить виджет");
        pin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { requestPinWidget(); }
        });
        root.addView(pin, buttonParams());

        Button open = button("Открыть MetroList");
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openMetroList(); }
        });
        root.addView(open, buttonParams());

        Button reset = button("Сбросить оформление");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                WidgetSettings.reset(MainActivity.this);
                refreshWidgets();
                Toast.makeText(MainActivity.this, "Настройки сброшены", Toast.LENGTH_SHORT).show();
                recreate();
            }
        });
        root.addView(reset, buttonParams());

        TextView hint = text(
                "Нажатие на виджет открывает MetroList. Для реального текста MetroList должен быть пропатчен этой же версией модуля через LSPatch или модуль должен быть включён в LSPosed.",
                12,
                palette.muted);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.setMargins(0, dp(14), 0, 0);
        root.addView(hint, hintParams);
    }

    private void addSeek(final String label, int value, final int min, int max,
                         final String suffix, final ValueSaver saver) {
        final TextView valueLabel = text(label + ": " + value + suffix, 14, palette.foreground);
        LinearLayout.LayoutParams labelParams = matchWrap();
        labelParams.setMargins(0, dp(14), 0, 0);
        root.addView(valueLabel, labelParams);

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(value - min);
        seek.setProgressTintList(ColorStateList.valueOf(palette.accent));
        seek.setThumbTintList(ColorStateList.valueOf(palette.accent));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueLabel.setText(label + ": " + (progress + min) + suffix);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {
                saver.save(bar.getProgress() + min);
                refreshWidgets();
            }
        });
        root.addView(seek, matchWrap());
    }

    private String metroListStatus() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(Constants.TARGET_PACKAGE, 0);
            return "MetroList найден: " + info.versionName + "  ·  модуль 0.2.0";
        } catch (Throwable error) {
            return "MetroList не найден (" + Constants.TARGET_PACKAGE + ")";
        }
    }

    private void chooseImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_IMAGE);
        } catch (Throwable error) {
            Toast.makeText(this, "Не удалось открыть выбор изображения", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Throwable ignored) {}
        WidgetSettings.setImageUri(this, uri.toString());
        WidgetSettings.setBackgroundMode(this, WidgetSettings.BACKGROUND_IMAGE);
        refreshWidgets();
        Toast.makeText(this, "Картинка установлена", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void requestPinWidget() {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            ComponentName provider = new ComponentName(this, LyricsWidgetProvider.class);
            Method method = AppWidgetManager.class.getMethod(
                    "requestPinAppWidget", ComponentName.class, Bundle.class, PendingIntent.class);
            Object result = method.invoke(manager, provider, null, null);
            if (Boolean.FALSE.equals(result)) {
                Toast.makeText(this, "Добавь виджет через меню рабочего стола", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable error) {
            Toast.makeText(this, "Добавь «Текст MetroList» через меню виджетов", Toast.LENGTH_LONG).show();
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
        intent.putExtra(Constants.EXTRA_POSITION, 42_000L);
        intent.putExtra(Constants.EXTRA_DURATION, 213_000L);
        sendBroadcast(intent);
        Toast.makeText(this, "Демо отправлено в виджет", Toast.LENGTH_SHORT).show();
    }

    private void openMetroList() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(Constants.TARGET_PACKAGE);
        if (intent == null) {
            Toast.makeText(this, "MetroList не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
    }

    private void refreshWidgets() {
        WidgetState.updateAll(this);
    }

    private TextView section(String value) {
        TextView view = text(value, 19, palette.accent);
        view.setGravity(Gravity.START);
        return view;
    }

    private RadioButton radio(int id, String label) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(palette.foreground);
        button.setButtonTintList(ColorStateList.valueOf(palette.accent));
        button.setPadding(dp(2), dp(2), 0, dp(2));
        return button;
    }

    private EditText colorInput(String hint, int color) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(hex(color));
        input.setHint(hint);
        input.setTextSize(14);
        input.setTextColor(palette.foreground);
        input.setHintTextColor(palette.muted);
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        return input;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        button.setTextColor(contrast(palette.accent));
        return button;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(24), 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams inputLabelParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(9), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams inputParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(9), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int parseColor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("#")) value = "#" + value;
        if (value.length() != 7 && value.length() != 9) throw new IllegalArgumentException("bad color");
        return Color.parseColor(value);
    }

    private static String hex(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    private static int contrast(int background) {
        double value = 0.2126 * Color.red(background) + 0.7152 * Color.green(background) + 0.0722 * Color.blue(background);
        return value > 155.0 ? Color.rgb(25, 24, 27) : Color.WHITE;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private interface ValueSaver {
        void save(int value);
    }
}