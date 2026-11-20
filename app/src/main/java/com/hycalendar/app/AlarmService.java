package com.hycalendar.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** 알람이 울리는 동안 소리·진동을 계속 내고, '알람 종료'를 누를 때까지 멈추지 않는다. */
public class AlarmService extends Service {
    static final String ACT_RING = "com.hycalendar.app.SVC_RING";
    static final String ACT_STOP = "com.hycalendar.app.SVC_STOP";      // 알람 종료 (남은 다시 울림도 취소)
    static final String ACT_SNOOZE = "com.hycalendar.app.SVC_SNOOZE";  // 이번 소리만 끔 (예약된 다시 울림은 그대로)
    static final String ACT_CLOSE = "com.hycalendar.app.RING_CLOSE";   // 알람 화면 닫기
    private static final String CH = "alarm_ring_v1";
    private static final int NID = 7001;

    private MediaPlayer mp;
    private Ringtone rt;
    private Vibrator vib;
    private PowerManager.WakeLock wl;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable autoOff;
    private int base = -1;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) { finishRing(); return START_NOT_STICKY; }
        String a = i.getAction();
        if (ACT_STOP.equals(a)) {
            Alarms.cancelRest(this, i.getIntExtra("base", base));
            finishRing();
            return START_NOT_STICKY;
        }
        if (ACT_SNOOZE.equals(a)) {
            finishRing();
            return START_NOT_STICKY;
        }
        try {
            Store.init(this);
            Ui.init(this);
            silence();
            base = i.getIntExtra("base", -1);
            Intent act = new Intent(this, AlarmRingActivity.class);
            if (i.getExtras() != null) act.putExtras(i.getExtras());
            act.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Notification n = buildNotification(i, act);
            if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NID, n);
            int auto = i.getIntExtra("auto", 0);
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hycalendar:alarm");
                wl.acquire((auto > 0 ? auto + 1 : 30) * 60000L);
            }
            play(i.getStringExtra("sound"), i.getBooleanExtra("vib", true));
            if (auto > 0) {
                autoOff = this::finishRing;
                h.postDelayed(autoOff, auto * 60000L);
            }
            // '다른 앱 위에 표시' 권한이 있으면 화면을 켜고 사용 중일 때도 알람 화면이 바로 뜬다
            try { startActivity(act); } catch (Throwable ignored) { }
        } catch (Throwable t) {
            finishRing();
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(Intent i, Intent act) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "알람 울림", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        PendingIntent full = PendingIntent.getActivity(this, 1, act, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent st = new Intent(this, AlarmService.class).setAction(ACT_STOP).putExtra("base", base);
        PendingIntent stop = PendingIntent.getService(this, 2, st, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = i.getStringExtra("title");
        return new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title == null ? "Hy캘린더 알람" : title)
                .setContentText(i.getStringExtra("text"))
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(new Notification.Action.Builder(0, "알람 종료", stop).build())
                .build();
    }

    private void play(String sound, boolean vibrate) {
        if (sound == null) sound = "";
        if (!"none".equals(sound)) {
            Uri u = sound.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(sound);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            try {
                mp = new MediaPlayer();
                mp.setAudioAttributes(aa);
                mp.setDataSource(this, u);
                mp.setLooping(true);
                mp.prepare();
                mp.start();
            } catch (Throwable e) {
                try { if (mp != null) mp.release(); } catch (Throwable ignored) { }
                mp = null;
                try {
                    rt = RingtoneManager.getRingtone(this, u);
                    if (rt != null) {
                        rt.setAudioAttributes(aa);
                        if (android.os.Build.VERSION.SDK_INT >= 28) rt.setLooping(true);
                        rt.play();
                    }
                } catch (Throwable ignored) { }
            }
        }
        if (vibrate) {
            try {
                vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vib != null) vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 600, 300, 600, 300, 600, 1200}, 0));
            } catch (Throwable ignored) { }
        }
    }

    private void silence() {
        if (autoOff != null) { h.removeCallbacks(autoOff); autoOff = null; }
        try { if (mp != null) { mp.stop(); mp.release(); } } catch (Throwable ignored) { }
        mp = null;
        try { if (rt != null) rt.stop(); } catch (Throwable ignored) { }
        rt = null;
        try { if (vib != null) vib.cancel(); } catch (Throwable ignored) { }
        vib = null;
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) { }
        wl = null;
    }

    private void finishRing() {
        silence();
        try { sendBroadcast(new Intent(ACT_CLOSE).setPackage(getPackageName())); } catch (Throwable ignored) { }
        try { stopForeground(true); } catch (Throwable ignored) { }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        silence();
        super.onDestroy();
    }
}
