package com.example.telegramcallnotifier;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportService extends Service {

    private static final String CHANNEL_ID = "report_service_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(2001, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Telegram Call Notifier")
                .setContentText("Background service running")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build());

        new Thread(() -> {
            try {
                Log.d("ReportService", "Sending report now");
                sendReportNow();
            } catch (Exception e) {
                Log.e("ReportService", "Error sending report", e);
            } finally {
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void sendReportNow() {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        TelegramSender sender = new TelegramSender(this);
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

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
