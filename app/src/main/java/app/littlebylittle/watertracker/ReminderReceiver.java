package app.littlebylittle.watertracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.concurrent.ThreadLocalRandom;

public final class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL_ID = "water_reminders_v1";
    private static final int NOTIFICATION_ID = 517;
    static final int NEAR_GOAL_NOTIFICATION_ID = 518;
    private static final String[] FUNNY_MESSAGES = {
            "Your water bottle says it misses you. 💧",
            "Hydration called. It says, “Pick up!”",
            "A sip now keeps the desert vibes away. 🏜️",
            "Your future self sent a tiny water reminder.",
            "Drink some water before your brain starts buffering. 😄"
    };
    private static final String[] ENCOURAGING_MESSAGES = {
            "Small sip, big win. You’ve got this! 💧",
            "A little water is a kind thing to do for yourself.",
            "One glass closer to your daily goal. Keep going!",
            "Take a refreshing moment. You deserve it. 🌿",
            "Look at you, building a lovely little habit!"
    };
    private static final String[] SURPRISE_MESSAGES = {
            "Your water bottle says it misses you. 💧",
            "Hydration called. It says, “Pick up!”",
            "A sip now keeps the desert vibes away. 🏜️",
            "Your future self sent a tiny water reminder.",
            "Small sip, big win. You’ve got this! 💧",
            "A little water is a kind thing to do for yourself.",
            "One glass closer to your daily goal. Keep going!",
            "Take a refreshing moment. You deserve it. 🌿",
            "Look at you, building a lovely little habit!"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(ReminderScheduler.ENABLED, false)) return;
        long now = System.currentTimeMillis();
        String start = prefs.getString(ReminderScheduler.START, "08:00");
        String end = prefs.getString(ReminderScheduler.END, "21:00");
        if (ReminderScheduler.insideWindow(now, start, end)) {
            String message = prefs.getString(ReminderScheduler.MESSAGE, "@tone:surprise");
            int amount = ReminderScheduler.plannedAmount(prefs);
            showNotification(context, "Water reminder 💧", chooseMessage(message), NOTIFICATION_ID, amount);
        }
        ReminderScheduler.scheduleNext(context, now);
    }

    private String chooseMessage(String setting) {
        if (setting == null) setting = "@tone:surprise";
        if (setting.startsWith("@tone:")) {
            String[] messages;
            switch (setting.substring("@tone:".length())) {
                case "funny": messages = FUNNY_MESSAGES; break;
                case "encouraging": messages = ENCOURAGING_MESSAGES; break;
                default: messages = SURPRISE_MESSAGES;
            }
            return messages[ThreadLocalRandom.current().nextInt(messages.length)];
        }
        return setting.trim().isEmpty() ? SURPRISE_MESSAGES[0] : setting.trim();
    }

    static void showNotification(Context context, String title, String message, int notificationId, int glassSize) {
        Intent open = new Intent(context, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(context, 518, open, flags);
        Intent addGlassIntent = new Intent(context, MainActivity.class)
                .setAction(MainActivity.ACTION_ADD_GLASS)
                .putExtra(MainActivity.EXTRA_ADD_GLASS_SIZE, glassSize)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent addGlass = PendingIntent.getActivity(context, 519, addGlassIntent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_water_drop)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(content)
                .addAction(R.drawable.ic_water_drop, "Add a glass (" + glassSize + " ml)", addGlass)
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(notificationId, notification);
    }
}
