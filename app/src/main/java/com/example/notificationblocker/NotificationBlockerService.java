package com.example.notificationblocker;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
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
        // 只在新窗口出现时检测，避免频繁事件重复触发
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        if (isTargetDialog()) {
            dismissDialog();
            retryDismissDialog();
        }
    }

    /**
     * 检测"正在运行的服务"弹窗：
     * 1. 标题含"正在运行的服务"
     * 2. 或列表里显示了"番茄锁"且带"知道了"按钮
     */
    private boolean isTargetDialog() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        try {
            List<AccessibilityNodeInfo> titles =
                root.findAccessibilityNodeInfosByText("正在运行的服务");
            if (titles != null && !titles.isEmpty()) return true;

            List<AccessibilityNodeInfo> app =
                root.findAccessibilityNodeInfosByText("番茄锁");
            if (app != null && !app.isEmpty()) {
                List<AccessibilityNodeInfo> btn =
                    root.findAccessibilityNodeInfosByText("知道了");
                if (btn != null && !btn.isEmpty()) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            root.recycle();
        }
    }

    private void dismissDialog() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            if (root != null) {
                List<AccessibilityNodeInfo> btn =
                    root.findAccessibilityNodeInfosByText("知道了");
                if (btn != null && !btn.isEmpty()) {
                    for (AccessibilityNodeInfo node : btn) {
                        if (node.isClickable()) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            return;
                        }
                    }
                    btn.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    return;
                }
            }
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } finally {
            if (root != null) root.recycle();
        }
    }

    private void retryDismissDialog() {
        for (int i = 1; i <= 20; i++) {
            final int delay = i * 80;
            handler.postDelayed(() -> {
                if (shouldBlock && isTargetDialog()) {
                    dismissDialog();
                }
            }, delay);
        }
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

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
