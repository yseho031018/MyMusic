package com.example.mymusic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SettingsActivity extends Activity {

    private SharedPreferences preferences;
    private RadioGroup serverOptions;
    private RadioButton optionCustom;
    private boolean updatingSelection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        View root = findViewById(R.id.settingsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int spacing = Math.round(24 * getResources().getDisplayMetrics().density);
            view.setPadding(bars.left + spacing, bars.top + spacing,
                    bars.right + spacing, bars.bottom + spacing);
            return insets;
        });

        preferences = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        serverOptions = findViewById(R.id.serverOptions);
        optionCustom = findViewById(R.id.optionCustom);
        RadioButton optionLaptop = findViewById(R.id.optionLaptop);
        RadioButton optionDesktop = findViewById(R.id.optionDesktop);

        optionLaptop.setText("노트북 (" + MainActivity.DEFAULT_LAPTOP_IP + ")");
        optionDesktop.setText("데스크톱 (" + MainActivity.DEFAULT_DESKTOP_IP + ")");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        updateSelection();
        serverOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (updatingSelection) return;

            if (checkedId == R.id.optionAuto) {
                saveSelection(true, null);
            } else if (checkedId == R.id.optionLaptop) {
                saveSelection(false, MainActivity.DEFAULT_LAPTOP_IP);
            } else if (checkedId == R.id.optionDesktop) {
                saveSelection(false, MainActivity.DEFAULT_DESKTOP_IP);
            }
        });
        optionCustom.setOnClickListener(v -> showCustomIpDialog());
    }

    private void updateSelection() {
        boolean auto = preferences.getBoolean(MainActivity.PREF_KEY_AUTO_MODE, true);
        String ip = preferences.getString(MainActivity.PREF_KEY_SERVER_IP,
                MainActivity.DEFAULT_LAPTOP_IP);

        updatingSelection = true;
        optionCustom.setText("직접 입력 (" + ip + ")");
        if (auto) {
            serverOptions.check(R.id.optionAuto);
        } else if (MainActivity.DEFAULT_LAPTOP_IP.equals(ip)) {
            serverOptions.check(R.id.optionLaptop);
        } else if (MainActivity.DEFAULT_DESKTOP_IP.equals(ip)) {
            serverOptions.check(R.id.optionDesktop);
        } else {
            serverOptions.check(R.id.optionCustom);
        }
        updatingSelection = false;
    }

    private void saveSelection(boolean auto, String ip) {
        boolean oldAuto = preferences.getBoolean(MainActivity.PREF_KEY_AUTO_MODE, true);
        String oldIp = preferences.getString(MainActivity.PREF_KEY_SERVER_IP,
                MainActivity.DEFAULT_LAPTOP_IP);
        if (oldAuto != auto || (!auto && !ip.equals(oldIp))) {
            SharedPreferences.Editor editor = preferences.edit()
                    .putBoolean(MainActivity.PREF_KEY_AUTO_MODE, auto);
            if (!auto) editor.putString(MainActivity.PREF_KEY_SERVER_IP, ip);
            editor.apply();
            setResult(RESULT_OK);
        }
        updateSelection();
    }

    private void showCustomIpDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(preferences.getString(MainActivity.PREF_KEY_SERVER_IP,
                MainActivity.DEFAULT_LAPTOP_IP));
        input.setHint("예: 192.168.45.247");

        new AlertDialog.Builder(this)
                .setTitle("서버 IP 직접 입력")
                .setView(input)
                .setPositiveButton("저장", (dialog, which) -> {
                    String ip = input.getText().toString().trim();
                    if (ip.isEmpty()) {
                        Toast.makeText(this, "서버 주소를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        updateSelection();
                    } else {
                        saveSelection(false, ip);
                    }
                })
                .setNegativeButton("취소", (dialog, which) -> updateSelection())
                .setOnCancelListener(dialog -> updateSelection())
                .show();
    }
}
