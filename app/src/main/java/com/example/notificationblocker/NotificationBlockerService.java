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
    private long lastDetectTime = 0;
    private long lastRetryTime = 0;
    private long lastEntryLog = 0;
    private long lastQuickDetect = 0;
    private int dismissAttempts = 0;
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
        android.util.Log.e("NBService", "service onCreate pid=" + android.os.Process.myPid());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        shouldBlock = computeShouldBlock();
        handler.postDelayed(timeChecker, 30_000);
        android.util.Log.e("NBService", "serviceConnected shouldBlock=" + shouldBlock);
        // 服务连上时主动查一次，避免弹窗在连接前已打开而漏掉
        handler.postDelayed(() -> {
            if (shouldBlock && isTargetDialog()) {
                android.util.Log.e("NBService", "dialog already open on connect");
                dismissDialog();
                retryDismissDialog();
            }
        }, 500);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        long entryNow = System.currentTimeMillis();
        if (entryNow - lastEntryLog > 300) {
            lastEntryLog = entryNow;
            android.util.Log.e("NBService", "event type=" + event.getEventType()
                + " pkg=" + event.getPackageName()
                + " cls=" + event.getClassName()
                + " shouldBlock=" + shouldBlock);
        }

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        boolean isState = (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        if (!shouldBlock) return;

        long now = System.currentTimeMillis();
        // 状态变化（新窗口出现）立即检测；内容变化事件频繁，400ms 节流
        if (!isState && now - lastDetectTime < 400) return;
        lastDetectTime = now;

        boolean hit = isTargetDialog();
        android.util.Log.e("NBService", "isTargetDialog=" + hit + " type=" + type);
        if (hit) {
            dismissDialog();
            retryDismissDialog();
        } else {
            dismissAttempts = 0;
            // 新窗口事件到达时弹窗可能还没登记进窗口列表，补几次快速复查
            if (isState) scheduleQuickDetect();
        }
    }

    /** 抓空后的快速复查：60/120/200/300ms 各查一次 */
    private void scheduleQuickDetect() {
        long now = System.currentTimeMillis();
        if (now - lastQuickDetect < 500) return;
        lastQuickDetect = now;

        long[] delays = {60, 120, 200, 300};
        for (final long d : delays) {
            handler.postDelayed(() -> {
                if (!shouldBlock) return;
                if (isTargetDialog()) {
                    android.util.Log.e("NBService", "quick-detect hit after " + d + "ms");
                    dismissDialog();
                    retryDismissDialog();
                } else {
                    dismissAttempts = 0;
                }
            }, d);
        }
    }

    /**
     * 遍历所有无障碍窗口找弹窗（弹窗是独立窗口，active window 未必是它）。
     * 为了快：第一遍只比对窗口标题（几乎零开销），第二遍才对 systemui 窗口做文本搜索。
     */
    private boolean isTargetDialog() {
        try {
            List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
            if (windows == null) return false;

            // 第一遍：窗口标题
            for (android.view.accessibility.AccessibilityWindowInfo w : windows) {
                CharSequence title = w.getTitle();
                if (title != null && title.toString().contains("正在运行的服务")) {
                    android.util.Log.e("NBService", "hit by window title");
                    return true;
                }
            }

            // 第二遍：对窗口根节点做文本搜索（先看包名，跳过非 systemui 窗口）
            for (android.view.accessibility.AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo r = null;
                try {
                    r = w.getRoot();
                    if (r == null) continue;
                    CharSequence pkg = r.getPackageName();
                    String pkgStr = (pkg == null) ? "" : pkg.toString();
                    if (!"com.android.systemui".equals(pkgStr)
                        && !"miui.systemui.plugin".equals(pkgStr)) {
                        continue;
                    }
                    if (nodeHasDialogText(r)) {
                        android.util.Log.e("NBService", "hit by window node, title=" + w.getTitle());
                        return true;
                    }
                } finally {
                    if (r != null) r.recycle();
                }
            }
            return false;
        } catch (Exception e) {
            android.util.Log.e("NBService", "detect error: " + e);
            return false;
        }
    }

    private boolean nodeHasDialogText(AccessibilityNodeInfo root) {
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
        }
    }

    /** 只发返回键，让系统自己关掉弹窗 */
    private void dismissDialog() {
        dismissAttempts++;
        performGlobalAction(GLOBAL_ACTION_BACK);
        android.util.Log.e("NBService", "sent BACK attempt=" + dismissAttempts);
    }

    private void retryDismissDialog() {
        long now = System.currentTimeMillis();
        if (now - lastRetryTime < 2000) return;
        lastRetryTime = now;

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
