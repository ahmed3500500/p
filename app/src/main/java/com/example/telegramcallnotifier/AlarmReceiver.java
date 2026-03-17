package com.example.telegramcallnotifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;

public class AlarmReceiver extends BroadcastReceiver {

    private static final int TELEGRAM_REPORT_EVERY_N_ALARMS = 30;
    private static final long MIN_DUPLICATE_GAP_MS = 3000L;

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

        int alarmCounter = prefs.getInt("alarm_counter", 0);
        DebugLogger.log(context, "AlarmReceiver", "alarmCounter(before)=" + alarmCounter);

        alarmCounter++;

        boolean sendTelegram = false;

        if (alarmCounter >= TELEGRAM_REPORT_EVERY_N_ALARMS) {
            sendTelegram = true;
            alarmCounter = 0;
        }

        prefs.edit().putInt("alarm_counter", alarmCounter).apply();
        DebugLogger.log(context, "AlarmReceiver", "alarmCounter(after)=" + alarmCounter + " sendTelegram=" + sendTelegram);

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;

        try {
            if (powerManager != null) {
                DebugLogger.log(context, "AlarmReceiver", "Trying to acquire WakeLock for 30 seconds");
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "TelegramCallNotifier:AlarmWakeLock"
                );
                wakeLock.acquire(30 * 1000L);
                DebugLogger.log(context, "AlarmReceiver", "WakeLock acquired");
            } else {
                DebugLogger.log(context, "AlarmReceiver", "powerManager is null");
            }

            Intent wakeIntent = new Intent(context, WakeActivity.class);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            DebugLogger.log(context, "AlarmReceiver", "Starting WakeActivity");
            context.startActivity(wakeIntent);

            Intent serviceIntent = new Intent(context, ReportService.class);
            serviceIntent.setAction("ALARM_TRIGGER");
            serviceIntent.putExtra("sendTelegram", sendTelegram);

            try {
                DebugLogger.log(context, "AlarmReceiver", "Starting ReportService sendTelegram=" + sendTelegram);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                DebugLogger.log(context, "AlarmReceiver", "ReportService start requested successfully");
            } catch (Exception e) {
                DebugLogger.logError(context, "AlarmReceiver", e);
            }

        } catch (Exception e) {
            DebugLogger.logError(context, "AlarmReceiver", e);
        } finally {
            DebugLogger.log(context, "AlarmReceiver", "Scheduling next alarm");
            AlarmScheduler.scheduleNext(context, AlarmScheduler.TEST_INTERVAL_MS);

            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                    DebugLogger.log(context, "AlarmReceiver", "WakeLock released");
                } catch (Exception e) {
                    DebugLogger.logError(context, "AlarmReceiver", e);
                }
            }
        }
    }
}
