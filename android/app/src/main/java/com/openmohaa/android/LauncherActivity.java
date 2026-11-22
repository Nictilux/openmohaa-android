package com.openmohaa.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/**
 * Start screen: import of the game files, choice of the game and renderer.
 */
public class LauncherActivity extends Activity {
    private static final int REQUEST_IMPORT = 1;

    private static final String[] GAME_NAMES = {
        "Allied Assault",
        "Spearhead",
        "Breakthrough"
    };

    private SharedPreferences prefs;
    private TextView statusText;
    private RadioGroup gameGroup;
    private RadioGroup rendererGroup;
    private EditText extraArgs;
    private Button playButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private TextView header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, dp(16), 0, dp(6));
        return t;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(24, 26, 22));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("OpenMoHAA");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(222, 196, 120));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Medal of Honor: Allied Assault на Android (неофициальный порт)");
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        root.addView(header("Файлы игры"));
        statusText = new TextView(this);
        statusText.setTextColor(Color.LTGRAY);
        root.addView(statusText);

        Button importButton = new Button(this);
        importButton.setText("Импортировать файлы игры…");
        importButton.setOnClickListener(v -> startImport());
        root.addView(importButton);

        root.addView(header("Игра"));
        gameGroup = new RadioGroup(this);
        for (int i = 0; i < GAME_NAMES.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(100 + i);
            rb.setText(GAME_NAMES[i]);
            rb.setTextColor(Color.WHITE);
            gameGroup.addView(rb);
        }
        root.addView(gameGroup);

        root.addView(header("Графика"));
        rendererGroup = new RadioGroup(this);
        RadioButton gl1 = new RadioButton(this);
        gl1.setId(200);
        gl1.setText("Классический рендер (OpenGL через gl4es) — рекомендуется");
        gl1.setTextColor(Color.WHITE);
        rendererGroup.addView(gl1);
        RadioButton gl2 = new RadioButton(this);
        gl2.setId(201);
        gl2.setText("OpenGL ES (экспериментальный, может быть с ошибками графики)");
        gl2.setTextColor(Color.WHITE);
        rendererGroup.addView(gl2);
        root.addView(rendererGroup);

        root.addView(header("Дополнительные параметры (необязательно)"));
        extraArgs = new EditText(this);
        extraArgs.setHint("например: +set in_touchSens 1.5");
        extraArgs.setTextColor(Color.WHITE);
        extraArgs.setHintTextColor(Color.GRAY);
        extraArgs.setSingleLine(true);
        root.addView(extraArgs);

        playButton = new Button(this);
        playButton.setText("ИГРАТЬ");
        playButton.setTextSize(20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        playButton.setLayoutParams(lp);
        playButton.setOnClickListener(v -> play());
        root.addView(playButton);

        TextView help = new TextView(this);
        help.setTextColor(Color.GRAY);
        help.setPadding(0, dp(16), 0, 0);
        help.setText(
            "Управление: левая половина экрана — ходьба, правая — обзор. "
            + "FIRE — стрельба (можно вести пальцем для прицеливания), ALT — второй режим/удар прикладом, "
            + "JUMP — прыжок, CROUCH — присесть, RELOAD — перезарядка, USE — использовать, "
            + "WPN+/WPN- — смена оружия, MENU — меню, KBD — клавиатура, ~ — консоль. "
            + "Чувствительность обзора: консольная переменная in_touchSens.\n\n"
            + "Поддерживаются геймпады.");
        root.addView(help);

        setContentView(scroll);

        int savedGame = prefs.getInt("game", 0);
        gameGroup.check(100 + savedGame);
        rendererGroup.check("opengl2".equals(prefs.getString("renderer", "opengl1")) ? 201 : 200);
        extraArgs.setText(prefs.getString("extra", ""));
    }

    private void refresh() {
        File base = GameData.getBaseDir(this);
        StringBuilder sb = new StringBuilder();
        boolean any = false;

        for (int i = 0; i < GAME_NAMES.length; i++) {
            boolean has = GameData.hasGame(this, i);
            any |= has;
            sb.append(has ? "✔ " : "✘ ").append(GAME_NAMES[i])
              .append(" (папка ").append(GameData.GAME_DIRS[i]).append(")\n");
            gameGroup.getChildAt(i).setEnabled(has);
        }

        sb.append("\nПапка с данными:\n").append(base.getAbsolutePath()).append("\n\n");
        if (!any) {
            sb.append("Файлы игры не найдены. Нажмите «Импортировать» и выберите папку, "
                    + "куда установлена игра (в ней есть папка main), "
                    + "предварительно скопировав её на телефон. "
                    + "Можно также скопировать папки main / mainta / maintt "
                    + "в папку выше через USB-кабель.");
        }
        statusText.setText(sb.toString());

        int checked = gameGroup.getCheckedRadioButtonId() - 100;
        if (checked < 0 || checked >= GAME_NAMES.length || !GameData.hasGame(this, checked)) {
            for (int i = 0; i < GAME_NAMES.length; i++) {
                if (GameData.hasGame(this, i)) {
                    gameGroup.check(100 + i);
                    break;
                }
            }
        }
        playButton.setEnabled(any);
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                .setMessage("Не удалось открыть выбор папки: " + e.getMessage())
                .setPositiveButton("OK", null)
                .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        runImport(data.getData());
    }

    private void runImport(Uri tree) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(16), dp(24), dp(8));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        TextView label = new TextView(this);
        label.setText("Подготовка…");
        box.addView(label);
        box.addView(bar);

        AlertDialog dlg = new AlertDialog.Builder(this)
            .setTitle("Импорт файлов игры")
            .setView(box)
            .setCancelable(false)
            .show();

        Handler ui = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            String error = null;
            int imported = 0;
            try {
                imported = GameData.importFromTree(this, tree, (name, copied, total) -> ui.post(() -> {
                    label.setText(String.format(Locale.ROOT, "%s\n%d / %d МБ",
                            name, copied >> 20, total >> 20));
                    if (total > 0) {
                        bar.setProgress((int) (copied * 1000 / total));
                    }
                }));
            } catch (Exception e) {
                error = e.toString();
            }

            final String err = error;
            final int count = imported;
            ui.post(() -> {
                dlg.dismiss();
                String msg;
                if (err != null) {
                    msg = "Ошибка импорта: " + err;
                } else if (count == 0) {
                    msg = "В выбранной папке не найдено папок main / mainta / maintt с файлами .pk3. "
                        + "Выберите папку, куда установлена игра.";
                } else {
                    msg = "Готово! Импортировано папок: " + count;
                }
                new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show();
                refresh();
            });
        }).start();
    }

    private void play() {
        int game = gameGroup.getCheckedRadioButtonId() - 100;
        if (game < 0 || game >= GAME_NAMES.length) {
            game = 0;
        }
        String renderer = rendererGroup.getCheckedRadioButtonId() == 201 ? "opengl2" : "opengl1";
        String extra = extraArgs.getText().toString();

        prefs.edit()
            .putInt("game", game)
            .putString("renderer", renderer)
            .putString("extra", extra)
            .apply();

        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_TARGET_GAME, game);
        intent.putExtra(GameActivity.EXTRA_RENDERER, renderer);
        intent.putExtra(GameActivity.EXTRA_EXTRA_ARGS, extra);
        startActivity(intent);
    }
}
