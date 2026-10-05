package app.littlebylittle.watertracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final String ACTION_ADD_GLASS = "app.littlebylittle.watertracker.ADD_GLASS";
    static final String EXTRA_ADD_GLASS_SIZE = "add_glass_size";
    private WebView webView;
    private boolean alarmSettingsOpened = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        webView = new WebView(this);
        webView.setBackgroundColor(0xFFFBFDFB);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        webView.addJavascriptInterface(new ReminderBridge(this), "AndroidReminder");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("if(window.syncNativeReminder)syncNativeReminder();", null);
                handlePendingAddGlass();
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/water-reminder.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView != null) webView.post(this::handlePendingAddGlass);
    }

    private void handlePendingAddGlass() {
        Intent current = getIntent();
        if (current == null || !ACTION_ADD_GLASS.equals(current.getAction()) || webView == null) return;
        int amount = Math.max(200, current.getIntExtra(EXTRA_ADD_GLASS_SIZE, 250));
        webView.evaluateJavascript(
                "window.addWaterFromNative ? window.addWaterFromNative(" + amount + ") : false",
                result -> {
                    if ("true".equals(result)) {
                        setIntent(new Intent(MainActivity.this, MainActivity.class));
                    }
                });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ReminderReceiver.CHANNEL_ID, "Water reminders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Personal water drinking reminders");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 71);
        }
    }

    void onRemindersEnabled() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission();
            return;
        }
        requestExactAlarmAccessIfNeeded();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 71) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (getSharedPreferences(ReminderScheduler.PREFS, MODE_PRIVATE)
                        .getBoolean(ReminderScheduler.ENABLED, false)) requestExactAlarmAccessIfNeeded();
            } else {
                Toast.makeText(this, "Allow notifications to receive water reminders", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getSharedPreferences(ReminderScheduler.PREFS, MODE_PRIVATE)
                .getBoolean(ReminderScheduler.ENABLED, false)) {
            ReminderScheduler.scheduleNext(this, System.currentTimeMillis());
        }
    }

    void requestExactAlarmAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager manager = getSystemService(AlarmManager.class);
            if (!manager.canScheduleExactAlarms() && !alarmSettingsOpened) {
                alarmSettingsOpened = true;
                Toast.makeText(this, "Allow alarms and reminders for dependable background alerts", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
