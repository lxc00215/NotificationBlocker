package com.example.notificationblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(new Intent(context, KeepAliveService.class));
                } else {
                    context.startService(new Intent(context, KeepAliveService.class));
                }
            } catch (Exception e) {
                // Android 12+ 后台禁止启动前台服务，失败不能让进程崩溃
                android.util.Log.e("NBService", "start KeepAlive failed: " + e);
            }
        }
    }
}
