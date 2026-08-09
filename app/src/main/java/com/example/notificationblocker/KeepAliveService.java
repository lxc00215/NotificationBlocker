package com.example.notificationblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

public class KeepAliveService extends Service {

    private static final String CHANNEL_ID = "keep_alive";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "后台服务", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("保持拦截服务运行");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("通知拦截器")
                .setContentText("拦截服务运行中")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build();
    }

    public static boolean shouldBlockNow(SharedPreferences prefs) {
        if (!prefs.getBoolean("block_enabled", false)) return false;

        String saved = prefs.getString("time_ranges", "");
        if (saved.isEmpty()) return true;

        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);

        for (String s : saved.split("\\|")) {
            TimeRange r = TimeRange.fromPrefsString(s.trim());
            if (r != null && r.contains(hour, minute)) {
                return true;
            }
        }
        return false;
    }

    public static void updateBlockState(SharedPreferences prefs) {
        NotificationBlockerService.shouldBlock = shouldBlockNow(prefs);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateBlockState(getSharedPreferences("config", MODE_PRIVATE));
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
