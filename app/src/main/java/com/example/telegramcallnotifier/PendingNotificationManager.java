package com.example.telegramcallnotifier;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public class PendingNotificationManager {
    private static final String PREF_NAME = "pending_notifications";
    private static final String KEY_ITEMS = "items";

    public static synchronized void addPending(Context context, String id, String text) {
        try {
            JSONArray arr = getAll(context);

            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("text", text);
            obj.put("createdAt", System.currentTimeMillis());
            obj.put("sent", false);
            obj.put("retryCount", 0);
            obj.put("lastTry", 0);

            arr.put(obj);
            saveAll(context, arr);

            CustomExceptionHandler.log(context, "Pending added: " + id);
        } catch (Exception e) {
            CustomExceptionHandler.log(context, "addPending error: " + e.getMessage());
            CustomExceptionHandler.logError(context, e);
        }
    }

    public static synchronized JSONArray getAll(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String raw = prefs.getString(KEY_ITEMS, "[]");
            return new JSONArray(raw);
        } catch (Exception e) {
            CustomExceptionHandler.log(context, "getAll error: " + e.getMessage());
            return new JSONArray();
        }
    }

    public static synchronized void markSent(Context context, String id) {
        try {
            JSONArray arr = getAll(context);
            JSONArray newArr = new JSONArray();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (!id.equals(obj.optString("id"))) {
                    newArr.put(obj);
                }
            }

            saveAll(context, newArr);
            CustomExceptionHandler.log(context, "Pending marked sent/removed: " + id);
        } catch (Exception e) {
            CustomExceptionHandler.log(context, "markSent error: " + e.getMessage());
            CustomExceptionHandler.logError(context, e);
        }
    }

    public static synchronized void markRetry(Context context, String id) {
        try {
            JSONArray arr = getAll(context);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (id.equals(obj.optString("id"))) {
                    obj.put("retryCount", obj.optInt("retryCount", 0) + 1);
                    obj.put("lastTry", System.currentTimeMillis());
                    break;
                }
            }

            saveAll(context, arr);
            CustomExceptionHandler.log(context, "Pending retry updated: " + id);
        } catch (Exception e) {
            CustomExceptionHandler.log(context, "markRetry error: " + e.getMessage());
            CustomExceptionHandler.logError(context, e);
        }
    }

    private static synchronized void saveAll(Context context, JSONArray arr) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply();
    }
}
