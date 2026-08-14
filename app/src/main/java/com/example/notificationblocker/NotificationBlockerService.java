package com.example.notificationblocker;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class NotificationBlockerService extends AccessibilityService {

    private static NotificationBlockerService instance;
    public static volatile boolean shouldBlock = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String PREFS_NAME = "NotificationBlockerPrefs";
    private static final String KEY_BLOCK_ENABLED = "block_enabled";
    private static final String KEY_TIME_RANGES = "time_ranges";

    private final Runnable timeChecker = new Runnable() {
        @Override
        public void run() {
            shouldBlock = computeShouldBlock();
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        shouldBlock = computeShouldBlock();
        handler.postDelayed(timeChecker, 30_000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (!shouldBlock) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        dismissDialog();
    }

    private void dismissDialog() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            AccessibilityNodeInfo btn = findButtonText(root, "知道了");
            if (btn != null) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findButtonText(AccessibilityNodeInfo root, String text) {
        try {
            java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                return nodes.get(0);
            }
        } catch (Exception e) {
        }
        return null;
    }

    private boolean computeShouldBlock() {
        try {
            Set<String> saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getStringSet(KEY_TIME_RANGES, new HashSet<String>());
            boolean blockEnabled = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_BLOCK_ENABLED, false);

            if (!blockEnabled) return false;
            if (saved.isEmpty()) return true;

            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int minute = now.get(Calendar.MINUTE);

            for (String s : saved) {
                TimeRange tr = TimeRange.fromPrefsString(s);
                if (tr != null && tr.contains(hour, minute)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    public static boolean isRunning() {
        return instance != null;
    }
}
