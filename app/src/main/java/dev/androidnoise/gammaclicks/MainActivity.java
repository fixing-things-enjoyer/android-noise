package dev.androidnoise.gammaclicks;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private Button toggle;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(245, 245, 245));

        TextView title = text("40 Hz Gamma Clicks", 28);
        title.setTextColor(Color.rgb(25, 25, 25));
        root.addView(title, matchWrap());

        TextView detail = text("1 ms click every 25 ms\nPlays alongside music and podcasts", 17);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(16), 0, dp(28));
        root.addView(detail, matchWrap());

        TextView volumeLabel = text(getString(R.string.click_volume, 20), 16);
        root.addView(volumeLabel, matchWrap());

        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(20);
        root.addView(volume, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                volumeLabel.setText(getString(R.string.click_volume, value));
                if (PlaybackService.isRunning) command(PlaybackService.ACTION_VOLUME, value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        toggle = new Button(this);
        toggle.setText(R.string.start);
        toggle.setTextSize(18);
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(20);
        root.addView(toggle, buttonParams);
        toggle.setOnClickListener(v -> {
            if (PlaybackService.isRunning) {
                command(PlaybackService.ACTION_STOP, volume.getProgress());
                toggle.setText(R.string.start);
            } else {
                command(PlaybackService.ACTION_START, volume.getProgress());
                toggle.setText(R.string.stop);
            }
        });

        TextView warning = text("Start low. Phone volume does not correspond to the study's measured dB level. Not a medical treatment.", 14);
        warning.setGravity(Gravity.CENTER);
        warning.setTextColor(Color.DKGRAY);
        warning.setPadding(0, dp(28), 0, 0);
        root.addView(warning, matchWrap());
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (toggle != null) toggle.setText(PlaybackService.isRunning ? R.string.stop : R.string.start);
    }

    private void command(String action, int volume) {
        Intent intent = new Intent(this, PlaybackService.class)
                .setAction(action).putExtra(PlaybackService.EXTRA_VOLUME, volume);
        if (action.equals(PlaybackService.ACTION_START) && Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
