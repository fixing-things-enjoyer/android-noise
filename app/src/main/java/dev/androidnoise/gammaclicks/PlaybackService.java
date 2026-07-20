package dev.androidnoise.gammaclicks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Locale;

public final class PlaybackService extends Service {
    public static final String ACTION_START = "dev.androidnoise.gammaclicks.START";
    public static final String ACTION_STOP = "dev.androidnoise.gammaclicks.STOP";
    public static final String ACTION_VOLUME = "dev.androidnoise.gammaclicks.VOLUME";
    public static final String EXTRA_VOLUME = "volume";
    public static final String EXTRA_DURATION_MS = "duration_ms";
    public static volatile boolean isRunning;
    public static volatile boolean isTimed;
    public static volatile long endElapsedRealtime;

    private static final String CHANNEL = "gamma_playback";
    private static final int NOTIFICATION_ID = 40;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private volatile boolean playing;
    private volatile float gain = 0.2f;
    private AudioTrack track;
    private Thread audioThread;

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!playing || !isTimed) return;
            long remaining = endElapsedRealtime - SystemClock.elapsedRealtime();
            if (remaining <= 0) {
                stopPlayback();
                stopSelf();
                return;
            }
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification());
            timerHandler.postDelayed(this, Math.min(1000, remaining));
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Gamma click playback",
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && intent.hasExtra(EXTRA_VOLUME)) {
            gain = Math.max(0, Math.min(100, intent.getIntExtra(EXTRA_VOLUME, 20))) / 100f;
            if (track != null) track.setVolume(gain);
        }
        if (ACTION_START.equals(action) && !playing) {
            long durationMs = Math.max(0, intent.getLongExtra(EXTRA_DURATION_MS, 0));
            isTimed = durationMs > 0;
            endElapsedRealtime = isTimed ? SystemClock.elapsedRealtime() + durationMs : 0;
            startForeground(NOTIFICATION_ID, notification());
            startPlayback();
            if (isTimed) timerHandler.post(timerTick);
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        Intent stop = new Intent(this, PlaybackService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        String detail = isTimed ? getString(R.string.notification_remaining,
                formatRemaining(endElapsedRealtime - SystemClock.elapsedRealtime()))
                : getString(R.string.notification_indefinite);
        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(detail)
                .setContentIntent(openIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.stop), stopIntent).build())
                .build();
    }

    private String formatRemaining(long remainingMs) {
        long total = Math.max(0, remainingMs + 999) / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d",
                total / 3600, (total % 3600) / 60, total % 60);
    }

    private void startPlayback() {
        int sampleRate = 48_000;
        int minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minimum, 4_800);
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        track = audioTrack;
        audioTrack.setVolume(gain);
        playing = true;
        isRunning = true;
        audioTrack.play();
        audioThread = new Thread(() -> writeClicks(sampleRate, audioTrack), "gamma-click-audio");
        audioThread.start();
    }

    private void writeClicks(int sampleRate, AudioTrack audioTrack) {
        final int period = sampleRate / 40;
        final int pulse = sampleRate / 1000;
        short[] samples = new short[period * 4];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((i % period) < pulse ? 16384 : 0);
        }
        try {
            while (playing && audioTrack.write(samples, 0, samples.length,
                    AudioTrack.WRITE_BLOCKING) >= 0) { }
        } catch (IllegalStateException ignored) {
            // The Stop action may release the track while a blocking write exits.
        }
    }

    private void stopPlayback() {
        timerHandler.removeCallbacks(timerTick);
        playing = false;
        isRunning = false;
        isTimed = false;
        endElapsedRealtime = 0;
        AudioTrack audioTrack = track;
        track = null;
        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.release();
            } catch (IllegalStateException ignored) { }
        }
        audioThread = null;
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
    }

    @Override public void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
