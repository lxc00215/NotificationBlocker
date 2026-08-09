package com.example.notificationblocker;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

public class WheelTimePickerDialog {

    public interface OnTimeSetListener {
        void onTimeSet(int startHour, int startMinute, int endHour, int endMinute);
    }

    private final Context context;
    private final OnTimeSetListener listener;
    private int startHour, startMinute, endHour, endMinute;
    private boolean isEdit;

    public WheelTimePickerDialog(Context context, int startH, int startM, int endH, int endM, OnTimeSetListener listener) {
        this.context = context;
        this.listener = listener;
        this.isEdit = startH >= 0;
        this.startHour = isEdit ? startH : 8;
        this.startMinute = isEdit ? startM : 0;
        this.endHour = isEdit ? endH : 22;
        this.endMinute = isEdit ? endM : 0;
    }

    public void show() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        TextView tvStart = new TextView(context);
        tvStart.setText("开始时间");
        tvStart.setTextSize(14);
        tvStart.setPadding(0, 0, 0, 8);
        layout.addView(tvStart);

        NumberPicker startHourPicker = createHourPicker();
        startHourPicker.setValue(startHour);
        startHourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> startHour = newVal);

        NumberPicker startMinPicker = createMinutePicker();
        startMinPicker.setValue(startMinute);
        startMinPicker.setOnValueChangedListener((picker, oldVal, newVal) -> startMinute = newVal);

        LinearLayout startRow = new LinearLayout(context);
        startRow.setOrientation(LinearLayout.HORIZONTAL);
        startRow.addView(startHourPicker, lp());
        TextView colon1 = new TextView(context);
        colon1.setText(" : ");
        colon1.setTextSize(18);
        startRow.addView(colon1);
        startRow.addView(startMinPicker, lp());
        layout.addView(startRow);

        TextView tvEnd = new TextView(context);
        tvEnd.setText("结束时间");
        tvEnd.setTextSize(14);
        tvEnd.setPadding(0, 24, 0, 8);
        layout.addView(tvEnd);

        NumberPicker endHourPicker = createHourPicker();
        endHourPicker.setValue(endHour);
        endHourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> endHour = newVal);

        NumberPicker endMinPicker = createMinutePicker();
        endMinPicker.setValue(endMinute);
        endMinPicker.setOnValueChangedListener((picker, oldVal, newVal) -> endMinute = newVal);

        LinearLayout endRow = new LinearLayout(context);
        endRow.setOrientation(LinearLayout.HORIZONTAL);
        endRow.addView(endHourPicker, lp());
        TextView colon2 = new TextView(context);
        colon2.setText(" : ");
        colon2.setTextSize(18);
        endRow.addView(colon2);
        endRow.addView(endMinPicker, lp());
        layout.addView(endRow);

        new AlertDialog.Builder(context)
            .setTitle(isEdit ? "编辑时间段" : "添加时间段")
            .setView(layout)
            .setPositiveButton("确定", (d, w) -> {
                if (listener != null) {
                    listener.onTimeSet(startHour, startMinute, endHour, endMinute);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private NumberPicker createHourPicker() {
        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(0);
        picker.setMaxValue(23);
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(value -> String.format("%02d", value));
        return picker;
    }

    private NumberPicker createMinutePicker() {
        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(0);
        picker.setMaxValue(59);
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(value -> String.format("%02d", value));
        return picker;
    }

    private LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }
}
