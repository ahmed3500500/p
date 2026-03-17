package com.example.telegramcallnotifier;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportService extends Service {

    private static final String TAG = "ReportService";
    private static final String CHANNEL_ID = "report_service_channel";
    private static final int NOTIFICATION_ID = 2001;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        DebugLogger.log(this, TAG, "onCreate");
        DebugLogger.logState(this, TAG, "service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean sendTelegram = intent != null && intent.getBooleanExtra("sendTelegram", false);
        String action = intent != null ? intent.getAction() : "null";

        DebugLogger.log(this, TAG, "onStartCommand action=" + action + " flags=" + flags + " startId=" + startId + " sendTelegram=" + sendTelegram);
        DebugLogger.logState(this, TAG, "onStartCommand");

        try {
            startForeground(NOTIFICATION_ID, new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Telegram Call Notifier")
                    .setContentText("Service running in background")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setOngoing(true)
                    .build());

            DebugLogger.log(this, TAG, "startForeground success");
        } catch (Exception e) {
            DebugLogger.logError(this, TAG, e);
        }

        if (sendTelegram) {
            new Thread(() -> {
                try {
                    DebugLogger.log(ReportService.this, TAG, "Background task started. sendTelegram=true");
                    DebugLogger.log(ReportService.this, TAG, "sendReportNow requested");
                    sendReportNow();
                } catch (Exception e) {
                    DebugLogger.logError(ReportService.this, TAG, e);
                }
            }, "ReportServiceWorker").start();
        } else {
            DebugLogger.log(this, TAG, "Background task skipped because sendTelegram=false");
        }

        return START_STICKY;
    }

    private void sendReportNow() {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        TelegramSender sender = new TelegramSender(this);
        DebugLogger.log(this, TAG, "sendReportNow building message time=" + time);
        sender.sendStatusMessage("⏰ Alarm report\nTime: " + time);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Report Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        DebugLogger.log(this, TAG, "onDestroy");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
