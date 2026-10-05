package app.littlebylittle.watertracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class ReminderScheduler {
    static final String PREFS = "water_reminders";
    static final String ENABLED = "enabled";
    static final String INTERVAL = "interval";
    static final String START = "start";
    static final String END = "end";
    static final String MESSAGE = "message";
    static final String TOTAL = "daily_total";
    static final String GOAL = "daily_goal";
    static final String GLASS_SIZE = "glass_size";
    static final String PROGRESS_DATE = "progress_date";
    static final String NEAR_GOAL_DATE = "near_goal_notification_date";

    private ReminderScheduler() {}

    static void configure(Context context, boolean enabled, int interval, String start, String end, String message) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED, enabled)
                .putInt(INTERVAL, Math.max(15, interval))
                .putString(START, safeTime(start, "08:00"))
                .putString(END, safeTime(end, "21:00"))
                .putString(MESSAGE, message == null || message.trim().isEmpty()
                        ? "Time for a little drink of water? 💧" : message.trim())
                .apply();
        if (enabled) scheduleNext(context, System.currentTimeMillis());
        else cancel(context);
    }

    static void scheduleNext(Context context, long fromMillis) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(ENABLED, false)) return;
        int interval = Math.max(15, prefs.getInt(INTERVAL, 60));
        String start = prefs.getString(START, "08:00");
        String end = prefs.getString(END, "21:00");
        long triggerAt = nextTrigger(fromMillis, interval, start, end);
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent pending = pendingIntent(context);
        if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }

    static void cancel(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        manager.cancel(pendingIntent(context));
    }

    static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 517, intent, flags);
    }

    static int plannedAmount(SharedPreferences prefs) {
        int interval = Math.max(15, prefs.getInt(INTERVAL, 60));
        int goal = Math.max(200, prefs.getInt(GOAL, 2000));
        LocalTime start = parse(prefs.getString(START, "08:00"), "08:00");
        LocalTime end = parse(prefs.getString(END, "21:00"), "21:00");
        int span = start.equals(end) ? 24 * 60
                : (int) (((end.toSecondOfDay() - start.toSecondOfDay() + 24 * 60 * 60)
                % (24 * 60 * 60)) / 60);
        int reminders = Math.max(1, (span + interval - 1) / interval);
        return Math.max(1, Math.round((float) goal / reminders));
    }

    static boolean insideWindow(long millis, String startText, String endText) {
        LocalTime now = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime();
        LocalTime start = parse(startText, "08:00");
        LocalTime end = parse(endText, "21:00");
        if (start.equals(end)) return true;
        if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
        return !now.isBefore(start) || now.isBefore(end);
    }

    private static long nextTrigger(long nowMillis, int interval, String startText, String endText) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        LocalTime start = parse(startText, "08:00");
        LocalTime end = parse(endText, "21:00");
        int windowMinutes = start.equals(end) ? 24 * 60
                : (int) ((end.toSecondOfDay() - start.toSecondOfDay() + 24 * 60 * 60) % (24 * 60 * 60)) / 60;

        ZonedDateTime windowStart = LocalDate.from(now).atTime(start).atZone(zone);
        if (start.isAfter(end) && !now.toLocalTime().isAfter(end)) {
            windowStart = windowStart.minusDays(1);
        }
        if (now.isBefore(windowStart)) return windowStart.toInstant().toEpochMilli();

        long elapsedMinutes = Duration.between(windowStart, now).toMinutes();
        long nextOffset = (elapsedMinutes / interval + 1) * interval;
        if (nextOffset < windowMinutes) {
            return windowStart.plusMinutes(nextOffset).toInstant().toEpochMilli();
        }
        return LocalDate.from(windowStart).plusDays(1).atTime(start).atZone(zone)
                .toInstant().toEpochMilli();
    }

    private static LocalTime parse(String value, String fallback) {
        try { return LocalTime.parse(safeTime(value, fallback)); }
        catch (Exception ignored) { return LocalTime.parse(fallback); }
    }

    private static String safeTime(String value, String fallback) {
        if (value == null || !value.matches("\\d{2}:\\d{2}")) return fallback;
        return value;
    }
}
