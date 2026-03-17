package com.example.telegramcallnotifier;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = (intent != null ? intent.getAction() : null);
        DebugLogger.log(context, TAG, "onReceive action=" + action);
        DebugLogger.logState(context, TAG, "boot receiver");

        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {

            try {
                Intent callMonitorIntent = new Intent(context, CallMonitorService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(callMonitorIntent);
                } else {
                    context.startService(callMonitorIntent);
                }
                DebugLogger.log(context, TAG, "CallMonitorService start requested after boot");
            } catch (Exception e) {
                DebugLogger.logError(context, TAG, e);
            }

            try {
                Intent reportIntent = new Intent(context, ReportService.class);
                reportIntent.setAction("START_FOREGROUND_SERVICE");
                reportIntent.putExtra("sendTelegram", false);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(reportIntent);
                } else {
                    context.startService(reportIntent);
                }
                DebugLogger.log(context, TAG, "ReportService start requested after boot");
            } catch (Exception e) {
                DebugLogger.logError(context, TAG, e);
            }

            try {
                boolean canSchedule = true;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                    canSchedule = alarmManager != null && alarmManager.canScheduleExactAlarms();
                }

                DebugLogger.log(context, TAG, "canScheduleExactAlarm(after boot)=" + canSchedule);

                if (canSchedule) {
                    AlarmScheduler.scheduleNext(context, AlarmScheduler.TEST_INTERVAL_MS);
                    DebugLogger.log(context, TAG, "Alarm scheduled after boot");
                } else {
                    DebugLogger.log(context, TAG, "Exact alarm permission not granted after boot; skip scheduling for now");
                }
            } catch (Exception e) {
                DebugLogger.logError(context, TAG, e);
            }

            DebugLogger.log(context, TAG, "Boot flow finished");
        }
    }
}
