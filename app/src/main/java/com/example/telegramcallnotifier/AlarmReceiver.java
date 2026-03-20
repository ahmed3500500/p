package com.example.telegramcallnotifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    private static final long MIN_DUPLICATE_GAP_MS = 3000L;
    private static final long PERIODIC_STATUS_INTERVAL_MS = 60 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        DebugLogger.log(context, "AlarmReceiver", "onReceive action=" + (intent != null ? intent.getAction() : "null"));
        DebugLogger.logState(context, "AlarmReceiver", "alarm fired");

        SharedPreferences prefs = context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE);

        long now = System.currentTimeMillis();
        long lastHandledAt = prefs.getLong("last_alarm_handled_at", 0L);

        if (now - lastHandledAt < MIN_DUPLICATE_GAP_MS) {
            DebugLogger.log(context, "AlarmReceiver", "Duplicate alarm ignored. gapMs=" + (now - lastHandledAt));
            return;
        }

        prefs.edit().putLong("last_alarm_handled_at", now).apply();

        long lastPeriodicSentAt = prefs.getLong("last_periodic_sent_at", 0L);

        boolean sendPeriodic = false;
        if ((now - lastPeriodicSentAt) >= PERIODIC_STATUS_INTERVAL_MS) {
            sendPeriodic = true;
            prefs.edit().putLong("last_periodic_sent_at", now).apply();
        }

        DebugLogger.log(context, "AlarmReceiver", "sendPeriodic=" + sendPeriodic);

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;

        try {
            if (powerManager != null) {
                DebugLogger.log(context, "AlarmReceiver", "Trying to acquire WakeLock for 30 seconds");
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "app:WAKE"
                );
                wakeLock.acquire(30 * 1000L);
                DebugLogger.log(context, "AlarmReceiver", "WakeLock acquired");
            } else {
                DebugLogger.log(context, "AlarmReceiver", "powerManager is null");
            }

            Intent wakeIntent = new Intent(context, WakeActivity.class);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingWakeIntent = PendingIntent.getActivity(
                    context, 0, wakeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel("wake_channel", "System Wake", NotificationManager.IMPORTANCE_HIGH);
                    notificationManager.createNotificationChannel(channel);
                }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "wake_channel")
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle("System is awake")
                        .setContentText("Keeping the system alive.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setFullScreenIntent(pendingWakeIntent, true);

                notificationManager.notify(9999, builder.build());
                DebugLogger.log(context, "AlarmReceiver", "Fired Full-Screen Intent Notification");

                // Auto cancel notification after 30 seconds
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    notificationManager.cancel(9999);
                }, 30000);
            }

            Intent serviceIntent = new Intent(context, ReportService.class);
            serviceIntent.setAction("ALARM_TRIGGER");
            serviceIntent.putExtra("reportType", "fully_awake_report");

            try {
                DebugLogger.log(context, "AlarmReceiver", "Starting ReportService for fully awake report");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                DebugLogger.log(context, "AlarmReceiver", "ReportService start requested successfully");
            } catch (Exception e) {
                DebugLogger.logError(context, "AlarmReceiver", e);
            }

            if (sendPeriodic) {
                Intent periodicIntent = new Intent(context, ReportService.class);
                periodicIntent.setAction("ALARM_TRIGGER");
                periodicIntent.putExtra("reportType", "periodic_status");

                DebugLogger.log(context, "AlarmReceiver", "Periodic report detected, delaying ReportService start by 3 seconds");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(periodicIntent);
                        } else {
                            context.startService(periodicIntent);
                        }
                        DebugLogger.log(context, "AlarmReceiver", "Delayed ReportService start requested for periodic report");
                    } catch (Exception e) {
                        DebugLogger.logError(context, "AlarmReceiver", e);
                    }
                }, 3000);
            }

        } catch (Exception e) {
            DebugLogger.logError(context, "AlarmReceiver", e);
        } finally {
            DebugLogger.log(context, "AlarmReceiver", "Scheduling next alarm");
            AlarmScheduler.scheduleNext(context, AlarmScheduler.TEST_INTERVAL_MS);

            // Removed wakeLock.release() here so it can accurately keep the device awake for 30 seconds.
            // The wakeLock will release itself automatically after the 30-second timeout specified in acquire().
        }
    }
}
