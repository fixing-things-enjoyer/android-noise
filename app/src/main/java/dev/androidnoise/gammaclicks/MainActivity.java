package dev.androidnoise.gammaclicks;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String PREFS = "settings";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Button toggle;
    private RadioGroup mode;
    private RadioButton indefiniteMode;
    private RadioButton timerMode;
    private LinearLayout timerControls;
    private NumberPicker hours;
    private NumberPicker minutes;
    private NumberPicker seconds;
    private TextView status;
    private SeekBar volume;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshState();
            uiHandler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int pad = dp(24);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(Color.rgb(247, 248, 250));

        TextView title = text(getString(R.string.app_title), 28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(24, 32, 44));
        content.addView(title, matchWrap());

        TextView detail = text(getString(R.string.subtitle), 16);
        detail.setTextColor(Color.DKGRAY);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(10), 0, dp(24));
        content.addView(detail, matchWrap());

        mode = new RadioGroup(this);
        mode.setOrientation(RadioGroup.HORIZONTAL);
        mode.setGravity(Gravity.CENTER);
        indefiniteMode = radio(getString(R.string.indefinite));
        timerMode = radio(getString(R.string.timer));
        mode.addView(indefiniteMode, weighted());
        mode.addView(timerMode, weighted());
        indefiniteMode.setChecked(true);
        content.addView(mode, matchWrap());

        timerControls = new LinearLayout(this);
        timerControls.setOrientation(LinearLayout.VERTICAL);
        timerControls.setPadding(0, dp(16), 0, dp(8));
        timerControls.setVisibility(View.GONE);

        TextView durationLabel = text(getString(R.string.duration), 15);
        durationLabel.setTextColor(Color.DKGRAY);
        timerControls.addView(durationLabel, matchWrap());

        LinearLayout pickers = new LinearLayout(this);
        pickers.setOrientation(LinearLayout.HORIZONTAL);
        pickers.setGravity(Gravity.CENTER);
        hours = picker(0, 99, prefs.getInt("hours", 0));
        minutes = picker(0, 59, prefs.getInt("minutes", 30));
        seconds = picker(0, 59, prefs.getInt("seconds", 0));
        addPicker(pickers, hours, getString(R.string.hours));
        addPicker(pickers, minutes, getString(R.string.minutes));
        addPicker(pickers, seconds, getString(R.string.seconds));
        timerControls.addView(pickers, matchWrap());
        content.addView(timerControls, matchWrap());

        mode.setOnCheckedChangeListener((group, checkedId) -> {
            timerControls.setVisibility(timerMode.isChecked() ? View.VISIBLE : View.GONE);
            updateStartEnabled();
        });
        NumberPicker.OnValueChangeListener durationChanged = (picker, oldValue, newValue) -> updateStartEnabled();
        hours.setOnValueChangedListener(durationChanged);
        minutes.setOnValueChangedListener(durationChanged);
        seconds.setOnValueChangedListener(durationChanged);

        TextView volumeLabel = text("", 15);
        volumeLabel.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams volumeLabelParams = matchWrap();
        volumeLabelParams.topMargin = dp(16);
        content.addView(volumeLabel, volumeLabelParams);

        volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(prefs.getInt("volume", 20));
        volumeLabel.setText(getString(R.string.click_volume, volume.getProgress()));
        content.addView(volume, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                volumeLabel.setText(getString(R.string.click_volume, value));
                if (fromUser) {
                    prefs.edit().putInt("volume", value).apply();
                    if (PlaybackService.isRunning) command(PlaybackService.ACTION_VOLUME, 0);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        status = text("", 20);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        status.setPadding(0, dp(12), 0, dp(8));
        content.addView(status, matchWrap());

        toggle = new Button(this);
        toggle.setTextSize(18);
        toggle.setMinHeight(dp(54));
        content.addView(toggle, matchWrap());
        toggle.setOnClickListener(v -> {
            if (PlaybackService.isRunning) {
                command(PlaybackService.ACTION_STOP, 0);
            } else {
                long durationMs = timerMode.isChecked() ? selectedDurationMs() : 0;
                if (timerMode.isChecked() && durationMs == 0) return;
                prefs.edit().putInt("hours", hours.getValue())
                        .putInt("minutes", minutes.getValue())
                        .putInt("seconds", seconds.getValue()).apply();
                command(PlaybackService.ACTION_START, durationMs);
            }
            refreshState();
        });

        TextView warning = text(getString(R.string.warning), 13);
        warning.setGravity(Gravity.CENTER);
        warning.setTextColor(Color.DKGRAY);
        warning.setPadding(0, dp(24), 0, 0);
        content.addView(warning, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
        refreshState();
    }

    @Override protected void onResume() {
        super.onResume();
        uiHandler.post(refresh);
    }

    @Override protected void onPause() {
        uiHandler.removeCallbacks(refresh);
        super.onPause();
    }

    private void refreshState() {
        boolean running = PlaybackService.isRunning;
        mode.setEnabled(!running);
        indefiniteMode.setEnabled(!running);
        timerMode.setEnabled(!running);
        hours.setEnabled(!running);
        minutes.setEnabled(!running);
        seconds.setEnabled(!running);
        toggle.setText(running ? R.string.stop_playback : R.string.start_playback);
        if (running && PlaybackService.isTimed) {
            if (!timerMode.isChecked()) timerMode.setChecked(true);
            long remaining = Math.max(0, PlaybackService.endElapsedRealtime - SystemClock.elapsedRealtime());
            status.setText(getString(R.string.remaining, formatDuration((remaining + 999) / 1000)));
            status.setVisibility(View.VISIBLE);
        } else if (running) {
            status.setText(R.string.playing_indefinitely);
            status.setVisibility(View.VISIBLE);
        } else {
            status.setVisibility(View.GONE);
        }
        updateStartEnabled();
    }

    private void updateStartEnabled() {
        if (toggle != null) toggle.setEnabled(PlaybackService.isRunning
                || !timerMode.isChecked() || selectedDurationMs() > 0);
    }

    private long selectedDurationMs() {
        long totalSeconds = hours.getValue() * 3600L + minutes.getValue() * 60L + seconds.getValue();
        return totalSeconds * 1000L;
    }

    private String formatDuration(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
    }

    private void command(String action, long durationMs) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action)
                .putExtra(PlaybackService.EXTRA_VOLUME, volume.getProgress())
                .putExtra(PlaybackService.EXTRA_DURATION_MS, durationMs);
        if (action.equals(PlaybackService.ACTION_START) && Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void addPicker(LinearLayout row, NumberPicker picker, String label) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.addView(picker, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(118)));
        TextView caption = text(label, 13);
        caption.setGravity(Gravity.CENTER);
        caption.setTextColor(Color.DKGRAY);
        column.addView(caption, matchWrap());
        row.addView(column, weighted());
    }

    private NumberPicker picker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setFormatter(number -> String.format(java.util.Locale.getDefault(), "%02d", number));
        return picker;
    }

    private RadioButton radio(String label) {
        RadioButton button = new RadioButton(this);
        button.setText(label);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setButtonDrawable(null);
        button.setBackgroundResource(R.drawable.mode_button);
        button.setTextColor(getColorStateList(R.color.mode_text));
        button.setMinHeight(dp(48));
        return button;
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
