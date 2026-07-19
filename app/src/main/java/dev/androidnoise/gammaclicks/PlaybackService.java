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
import android.os.IBinder;

public final class PlaybackService extends Service {
    public static final String ACTION_START = "dev.androidnoise.gammaclicks.START";
    public static final String ACTION_STOP = "dev.androidnoise.gammaclicks.STOP";
    public static final String ACTION_VOLUME = "dev.androidnoise.gammaclicks.VOLUME";
    public static final String EXTRA_VOLUME = "volume";
    public static volatile boolean isRunning;

    private static final String CHANNEL = "gamma_playback";
    private static final int NOTIFICATION_ID = 40;
    private volatile boolean playing;
    private volatile float gain = 0.2f;
    private AudioTrack track;
    private Thread audioThread;

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
            startForeground(NOTIFICATION_ID, notification());
            startPlayback();
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
        return builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("40 Hz clicks playing")
                .setContentText("Mixed with other app audio")
                .setContentIntent(openIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stopIntent).build())
                .build();
    }

    private void startPlayback() {
        int sampleRate = 48_000;
        int minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(minimum, 4_800);
        track = new AudioTrack.Builder()
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
        track.setVolume(gain);
        playing = true;
        isRunning = true;
        track.play();
        audioThread = new Thread(() -> writeClicks(sampleRate), "gamma-click-audio");
        audioThread.start();
    }

    private void writeClicks(int sampleRate) {
        final int period = sampleRate / 40;       // 25 ms
        final int pulse = sampleRate / 1000;      // 1 ms
        short[] samples = new short[period * 4];  // 100 ms, four exact cycles
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((i % period) < pulse ? 16384 : 0);
        }
        while (playing) {
            int written = track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
            if (written < 0) break;
        }
    }

    private void stopPlayback() {
        playing = false;
        isRunning = false;
        if (track != null) {
            track.pause();
            track.flush();
            track.release();
            track = null;
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
