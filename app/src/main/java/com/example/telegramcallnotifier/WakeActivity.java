package com.example.telegramcallnotifier;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

public class WakeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DebugLogger.log(this, "WakeActivity", "onCreate");
        DebugLogger.logState(this, "WakeActivity", "screen wake start");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            DebugLogger.log(this, "WakeActivity", "Applied setShowWhenLocked/setTurnScreenOn");
        }

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
        DebugLogger.log(this, "WakeActivity", "Wake flags applied");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            DebugLogger.log(WakeActivity.this, "WakeActivity", "finish after 3 seconds");
            finish();
        }, 3000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DebugLogger.log(this, "WakeActivity", "onResume");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DebugLogger.log(this, "WakeActivity", "onDestroy");
    }
}
