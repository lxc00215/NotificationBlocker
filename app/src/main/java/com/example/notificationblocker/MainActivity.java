package com.example.notificationblocker;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "NotificationBlockerPrefs";
    private static final String KEY_TIME_RANGES = "time_ranges";
    private static final String KEY_BLOCK_ENABLED = "block_enabled";

    private TextView tvAccessibilityStatus;
    private TextView tvSwitchStatus;
    private Button btnToggleSwitch;
    private Button btnAddTimeRange;
    private Button btnEnableAccessibility;
    private RecyclerView rvTimeRanges;

    private TimeRangeAdapter adapter;
    private ArrayList<TimeRange> timeRanges = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus);
        tvSwitchStatus = findViewById(R.id.tvSwitchStatus);
        btnToggleSwitch = findViewById(R.id.btnToggleSwitch);
        btnAddTimeRange = findViewById(R.id.btnAddTimeRange);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);
        rvTimeRanges = findViewById(R.id.rvTimeRanges);

        rvTimeRanges.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimeRangeAdapter(timeRanges, new TimeRangeAdapter.OnTimeRangeClickListener() {
            @Override
            public void onEditClick(int position) {
                showEditTimeRangeDialog(position);
            }
            @Override
            public void onDeleteClick(int position) {
                timeRanges.remove(position);
                adapter.notifyItemRemoved(position);
                saveTimeRanges();
                updateBlockState();
            }
        });
        rvTimeRanges.setAdapter(adapter);

        loadTimeRanges();

        btnEnableAccessibility.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        btnToggleSwitch.setOnClickListener(v -> {
            boolean newState = !isBlockEnabled();
            setBlockEnabled(newState);
            updateBlockState();
            updateUI();
        });

        btnAddTimeRange.setOnClickListener(v -> showAddTimeRangeDialog());

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBlockState();
        updateUI();
    }

    private boolean isBlockEnabled() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_BLOCK_ENABLED, false);
    }

    private void setBlockEnabled(boolean enabled) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_BLOCK_ENABLED, enabled).apply();
    }

    private boolean shouldBlockNow() {
        if (!isBlockEnabled()) return false;
        if (timeRanges.isEmpty()) return true;

        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);

        for (TimeRange range : timeRanges) {
            if (range.contains(hour, minute)) return true;
        }
        return false;
    }

    private void updateBlockState() {
        NotificationBlockerService.shouldBlock = shouldBlockNow();
    }

    private void updateUI() {
        boolean accEnabled = isAccessibilityServiceEnabled();
        boolean isBlockOn = isBlockEnabled();

        tvAccessibilityStatus.setText("无障碍服务: " + (accEnabled ? "已启用 ✓" : "未启用 ✗"));
        tvAccessibilityStatus.setTextColor(accEnabled ? 0xFF4CAF50 : 0xFFF44336);

        tvSwitchStatus.setText("通知栏屏蔽: " + (isBlockOn ? "开启 ✓" : "关闭 ✗"));
        tvSwitchStatus.setTextColor(isBlockOn ? 0xFF4CAF50 : 0xFFF44336);

        btnToggleSwitch.setText(isBlockOn ? "关闭屏蔽" : "开启屏蔽");
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + NotificationBlockerService.class.getCanonicalName();
        try {
            String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices != null) {
                return enabledServices.contains(service);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void showAddTimeRangeDialog() {
        new WheelTimePickerDialog(this, -1, -1, -1, -1, new WheelTimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(int startHour, int startMinute, int endHour, int endMinute) {
                timeRanges.add(new TimeRange(startHour, startMinute, endHour, endMinute));
                adapter.notifyItemInserted(timeRanges.size() - 1);
                saveTimeRanges();
                updateBlockState();
            }
        }).show();
    }

    private void showEditTimeRangeDialog(int position) {
        TimeRange range = timeRanges.get(position);
        new WheelTimePickerDialog(this, range.startHour, range.startMinute, range.endHour, range.endMinute, new WheelTimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(int startHour, int startMinute, int endHour, int endMinute) {
                range.startHour = startHour;
                range.startMinute = startMinute;
                range.endHour = endHour;
                range.endMinute = endMinute;
                adapter.notifyItemChanged(position);
                saveTimeRanges();
                updateBlockState();
            }
        }).show();
    }

    private void loadTimeRanges() {
        timeRanges.clear();
        Set<String> saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getStringSet(KEY_TIME_RANGES, new HashSet<String>());
        for (String s : saved) {
            TimeRange tr = TimeRange.fromPrefsString(s);
            if (tr != null) timeRanges.add(tr);
        }
        adapter.notifyDataSetChanged();
    }

    private void saveTimeRanges() {
        Set<String> set = new HashSet<>();
        for (TimeRange tr : timeRanges) set.add(tr.toPrefsString());
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putStringSet(KEY_TIME_RANGES, set).apply();
    }
}
