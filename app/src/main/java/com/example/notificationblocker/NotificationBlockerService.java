package com.example.notificationblocker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationBlockerService extends AccessibilityService {

    private static NotificationBlockerService instance;
    public static volatile boolean shouldBlock = false;
    private long lastActionTime = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String PREFS_NAME = "NotificationBlockerPrefs";
    private static final String KEY_BLOCK_ENABLED = "block_enabled";
    private static final String KEY_TIME_RANGES = "time_ranges";

    private static final List<String> PANEL_IDS = Arrays.asList(
        "com.android.systemui:id/notification_panel",
        "com.android.systemui:id/notifications_stack_scroller",
        "com.android.systemui:id/expandableNotificationShade",
        "com.android.systemui:id/control_center_container",
        "miui.systemui.plugin:id/main_panel",
        "miui.systemui.plugin:id/main_panel_container"
    );

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

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;

        String pkgName = pkg.toString();
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        boolean isSystemUi = "com.android.systemui".equals(pkgName)
            || "miui.systemui.plugin".equals(pkgName);

        if (isSystemUi) {
            if (isPanelExpanded()) {
                dismissNow();
                retryDismiss();
            }
        }

        if (isRunningServicesDialog()) {
            dismissRunningServicesDialog();
            retryDismissDialog();
        }
    }

    private void dismissNow() {
        long now = System.currentTimeMillis();
        if (now - lastActionTime < 50) return;
        lastActionTime = now;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        }
        performGlobalAction(GLOBAL_ACTION_BACK);
        performGlobalAction(GLOBAL_ACTION_HOME);
        dispatchBackGesture();
    }

    private void dispatchBackGesture() {
        try {
            Display display = getDisplay();
            if (display == null) return;
            Point size = new Point();
            display.getRealSize(size);

            Path path = new Path();
            path.moveTo(5, size.y / 2f);
            path.lineTo(size.x / 2f, size.y / 2f);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 150);
            builder.addStroke(stroke);

            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
        }
    }

    private void retryDismiss() {
        for (int i = 1; i <= 40; i++) {
            final int delay = i * 50;
            handler.postDelayed(() -> {
                if (shouldBlock && isPanelExpanded()) {
                    long now = System.currentTimeMillis();
                    if (now - lastActionTime < 50) return;
                    lastActionTime = now;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
                    }
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    performGlobalAction(GLOBAL_ACTION_HOME);
                    dispatchBackGesture();
                }
            }, delay);
        }
    }

    private boolean isPanelExpanded() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        try {
            for (String id : PANEL_IDS) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null && !nodes.isEmpty()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            root.recycle();
        }
    }

    private boolean isRunningServicesDialog() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        try {
            List<AccessibilityNodeInfo> titleNodes = root.findAccessibilityNodeInfosByText("正在运行的服务");
            if (titleNodes != null && !titleNodes.isEmpty()) {
                return true;
            }

            List<AccessibilityNodeInfo> btnNodes = root.findAccessibilityNodeInfosByText("知道了");
            if (btnNodes != null && !btnNodes.isEmpty()) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        } finally {
            root.recycle();
        }
    }

    private void dismissRunningServicesDialog() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            List<AccessibilityNodeInfo> btnNodes = root.findAccessibilityNodeInfosByText("知道了");
            if (btnNodes != null && !btnNodes.isEmpty()) {
                for (AccessibilityNodeInfo node : btnNodes) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                return;
            }

            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } finally {
            root.recycle();
        }
    }

    private void retryDismissDialog() {
        for (int i = 1; i <= 20; i++) {
            final int delay = i * 80;
            handler.postDelayed(() -> {
                if (shouldBlock && isRunningServicesDialog()) {
                    dismissRunningServicesDialog();
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
