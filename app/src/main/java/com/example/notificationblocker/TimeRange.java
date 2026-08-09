package com.example.notificationblocker;

public class TimeRange {
    public int startHour;
    public int startMinute;
    public int endHour;
    public int endMinute;

    public TimeRange(int startHour, int startMinute, int endHour, int endMinute) {
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.endHour = endHour;
        this.endMinute = endMinute;
    }

    public boolean contains(int hour, int minute) {
        int start = startHour * 60 + startMinute;
        int end = endHour * 60 + endMinute;
        int now = hour * 60 + minute;
        return now >= start && now < end;
    }

    public String format() {
        return String.format("%02d:%02d - %02d:%02d", startHour, startMinute, endHour, endMinute);
    }

    public String toPrefsString() {
        return startHour + "," + startMinute + "," + endHour + "," + endMinute;
    }

    public static TimeRange fromPrefsString(String s) {
        String[] parts = s.split(",");
        if (parts.length != 4) return null;
        try {
            return new TimeRange(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
