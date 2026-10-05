package app.littlebylittle.watertracker;

import android.webkit.JavascriptInterface;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ReminderBridge {
    private final MainActivity activity;

    ReminderBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void setReminder(boolean enabled, int intervalMinutes, String start, String end, String message) {
        ReminderScheduler.configure(activity, enabled, intervalMinutes, start, end, message);
        if (enabled) activity.runOnUiThread(activity::onRemindersEnabled);
    }

    @JavascriptInterface
    public void updateProgress(int total, int goal, int glassSize, boolean fromDrink) {
        SharedPreferences prefs = activity.getSharedPreferences(ReminderScheduler.PREFS, MainActivity.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean sameDay = today.equals(prefs.getString(ReminderScheduler.PROGRESS_DATE, ""));
        int previousTotal = sameDay ? prefs.getInt(ReminderScheduler.TOTAL, 0) : 0;
        int safeGoal = Math.max(200, goal);
        int safeTotal = Math.max(0, total);
        int safeGlass = Math.max(200, glassSize);
        int threshold = (int) Math.ceil(safeGoal * 0.9);
        boolean crossedNearGoal = fromDrink && previousTotal < threshold && safeTotal >= threshold
                && !today.equals(prefs.getString(ReminderScheduler.NEAR_GOAL_DATE, ""));

        SharedPreferences.Editor editor = prefs.edit()
                .putString(ReminderScheduler.PROGRESS_DATE, today)
                .putInt(ReminderScheduler.TOTAL, safeTotal)
                .putInt(ReminderScheduler.GOAL, safeGoal)
                .putInt(ReminderScheduler.GLASS_SIZE, safeGlass);
        if (crossedNearGoal) editor.putString(ReminderScheduler.NEAR_GOAL_DATE, today);
        editor.apply();

        if (crossedNearGoal) {
            int remaining = Math.max(0, safeGoal - safeTotal);
            int percent = Math.min(100, Math.round(safeTotal * 100f / safeGoal));
            String title = safeTotal >= safeGoal ? "Goal reached! 🎉" : "Almost there! 💧";
            String message = safeTotal >= safeGoal
                    ? "You hit your " + safeGoal + " ml goal. Amazing work!"
                    : "You’re at " + percent + "% — just " + remaining + " ml to reach your goal!";
            ReminderReceiver.showNotification(activity, title, message,
                    ReminderReceiver.NEAR_GOAL_NOTIFICATION_ID, ReminderScheduler.plannedAmount(prefs));
        }
    }
}
