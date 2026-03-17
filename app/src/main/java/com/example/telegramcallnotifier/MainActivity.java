package com.example.telegramcallnotifier;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int BATTERY_OPTIMIZATION_REQUEST_CODE = 200;
    private static final int EXACT_ALARM_REQUEST_CODE = 201;

    private TelegramSender telegramSender;
    private Button btnToggleService;
    private Button btnViewLogs;
    private TextView textStatus;
    private CustomExceptionHandler exceptionHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        exceptionHandler = new CustomExceptionHandler(this);
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);

        DebugLogger.log(this, "MainActivity", "onCreate intent=" + getIntent());
        DebugLogger.logState(this, "MainActivity", "onCreate");

        setContentView(R.layout.activity_main);

        telegramSender = new TelegramSender(this);

        btnToggleService = findViewById(R.id.btnToggleService);
        btnViewLogs = findViewById(R.id.btnViewLogs);
        textStatus = findViewById(R.id.textStatus);

        btnToggleService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DebugLogger.log(MainActivity.this, "MainActivity", "btnToggleService clicked isServiceRunning=" + isServiceRunning());
                if (isServiceRunning()) {
                    stopService();
                } else {
                    checkPermissionsAndStartService();
                }
            }
        });

        btnViewLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DebugLogger.log(MainActivity.this, "MainActivity", "btnViewLogs clicked");
                openLogFile();
            }
        });

        checkPermissionsAndStartService();
        updateUI();

        DebugLogger.log(this, "MainActivity", "App Started. SDK=" + Build.VERSION.SDK_INT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        DebugLogger.log(this, "MainActivity", "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        DebugLogger.log(this, "MainActivity", "onResume");
        DebugLogger.logState(this, "MainActivity", "onResume");
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        DebugLogger.log(this, "MainActivity", "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DebugLogger.log(this, "MainActivity", "onDestroy");
    }

    private void updateUI() {
        boolean running = isServiceRunning();
        DebugLogger.log(this, "MainActivity", "updateUI running=" + running);
        if (running) {
            btnToggleService.setText("RUNNING");
            btnToggleService.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            textStatus.setText("Status: Service is Active");
        } else {
            btnToggleService.setText("START");
            btnToggleService.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336")));
            textStatus.setText("Status: Service Stopped");
        }
    }

    private void showLogs() {
        String logs = CustomExceptionHandler.getLogContent(this);
        textStatus.setText(logs);
    }

    private void openLogFile() {
        File logFile = CustomExceptionHandler.getLogFile(this);
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(this, "No logs found.", Toast.LENGTH_LONG).show();
            showLogs();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", logFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "text/plain");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            DebugLogger.log(this, "MainActivity", "Opening log file path=" + logFile.getAbsolutePath());
            startActivity(Intent.createChooser(intent, "Open logs"));
        } catch (Exception e) {
            DebugLogger.logError(this, "MainActivity", e);
            showLogs();
        }
    }

    private boolean isServiceRunning() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (CallMonitorService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void stopService() {
        DebugLogger.log(this, "MainActivity", "stopService requested");

        try {
            Intent callIntent = new Intent(this, CallMonitorService.class);
            stopService(callIntent);
        } catch (Throwable e) {
            DebugLogger.logError(this, "MainActivity", e);
        }

        try {
            Intent reportIntent = new Intent(this, ReportService.class);
            stopService(reportIntent);
        } catch (Throwable e) {
            DebugLogger.logError(this, "MainActivity", e);
        }

        try {
            AlarmScheduler.cancel(this);
        } catch (Throwable e) {
            DebugLogger.logError(this, "MainActivity", e);
        }

        updateUI();
        new android.os.Handler().postDelayed(this::updateUI, 500);
    }

    private boolean needsExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            boolean needs = alarmManager != null && !alarmManager.canScheduleExactAlarms();
            DebugLogger.log(this, "MainActivity", "needsExactAlarmPermission=" + needs);
            return needs;
        }
        return false;
    }

    private void checkExactAlarmAndStart() {
        DebugLogger.log(this, "MainActivity", "checkExactAlarmAndStart called");
        if (needsExactAlarmPermission()) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, EXACT_ALARM_REQUEST_CODE);
                Toast.makeText(this, "Please allow exact alarms so periodic reports work while the screen is off", Toast.LENGTH_LONG).show();
                DebugLogger.log(this, "MainActivity", "Requested exact alarm permission");
                return;
            } catch (Exception e) {
                DebugLogger.logError(this, "MainActivity", e);
            }
        }
        checkBatteryAndStart();
    }

    private void checkBatteryAndStart() {
        DebugLogger.log(this, "MainActivity", "checkBatteryAndStart called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE);
                    DebugLogger.log(this, "MainActivity", "Requested battery optimization ignore");
                    return;
                } catch (Exception e) {
                    DebugLogger.logError(this, "MainActivity", e);
                }
            }
        }
        startAllCoreServices();
    }

    private void checkPermissionsAndStartService() {
        DebugLogger.log(this, "MainActivity", "checkPermissionsAndStartService called");
        List<String> permsList = new ArrayList<>();
        permsList.add(Manifest.permission.READ_PHONE_STATE);
        permsList.add(Manifest.permission.READ_CALL_LOG);
        permsList.add(Manifest.permission.ANSWER_PHONE_CALLS);
        permsList.add(Manifest.permission.CALL_PHONE);

        if (Build.VERSION.SDK_INT >= 26) {
            permsList.add(Manifest.permission.READ_PHONE_NUMBERS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permsList.add(Manifest.permission.FOREGROUND_SERVICE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsList.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (Build.VERSION.SDK_INT >= 34) {
            permsList.add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC);
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permsList) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        DebugLogger.log(this, "MainActivity", "Missing permissions count=" + listPermissionsNeeded.size() + " list=" + listPermissionsNeeded);

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            checkExactAlarmAndStart();
        }
    }

    private void startService() {
        startAllCoreServices();
    }

    private void startAllCoreServices() {
        DebugLogger.log(this, "MainActivity", "startAllCoreServices called");

        requestDefaultDialerRoleIfNeeded();

        try {
            Intent callIntent = new Intent(this, CallMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(callIntent);
            } else {
                startService(callIntent);
            }
            DebugLogger.log(this, "MainActivity", "CallMonitorService start requested");
        } catch (Throwable e) {
            DebugLogger.logError(this, "MainActivity", e);
        }

        try {
            Intent reportIntent = new Intent(this, ReportService.class);
            reportIntent.setAction("START_FOREGROUND_SERVICE");
            reportIntent.putExtra("sendTelegram", false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(reportIntent);
            } else {
                startService(reportIntent);
            }
            DebugLogger.log(this, "MainActivity", "ReportService start requested");
        } catch (Throwable e) {
            DebugLogger.logError(this, "MainActivity", e);
        }

        try {
            AlarmScheduler.scheduleNext(this, AlarmScheduler.TEST_INTERVAL_MS);
            DebugLogger.log(this, "MainActivity", "AlarmScheduler.scheduleNext requested");
        } catch (Throwable e) {
            DebugLogger.logError(this, "MainActivity", e);
        }

        new android.os.Handler().postDelayed(this::updateUI, 500);
    }

    private void requestDefaultDialerRoleIfNeeded() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.app.role.RoleManager roleManager = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (roleManager != null
                        && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)
                        && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                    Intent intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER);
                    startActivity(intent);
                    DebugLogger.log(this, "MainActivity", "Requested default dialer role");
                }
            } else {
                android.telecom.TelecomManager telecomManager = (android.telecom.TelecomManager) getSystemService(TELECOM_SERVICE);
                if (telecomManager != null && !getPackageName().equals(telecomManager.getDefaultDialerPackage())) {
                    Intent intent = new Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                    intent.putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
                    startActivity(intent);
                    DebugLogger.log(this, "MainActivity", "Requested default dialer package");
                }
            }
        } catch (Exception e) {
            DebugLogger.logError(this, "MainActivity", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DebugLogger.log(this, "MainActivity", "onRequestPermissionsResult requestCode=" + requestCode + " grantCount=" + grantResults.length);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = grantResults.length > 0;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            DebugLogger.log(this, "MainActivity", "permissions allGranted=" + allGranted);

            if (allGranted) {
                checkExactAlarmAndStart();
            } else {
                Toast.makeText(this, "Permissions are required for the app to work", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        DebugLogger.log(this, "MainActivity", "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode + " data=" + data);

        if (requestCode == BATTERY_OPTIMIZATION_REQUEST_CODE) {
            startAllCoreServices();
        } else if (requestCode == EXACT_ALARM_REQUEST_CODE) {
            checkBatteryAndStart();
        }
    }
}
