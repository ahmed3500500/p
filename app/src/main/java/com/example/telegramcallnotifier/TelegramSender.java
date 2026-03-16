package com.example.telegramcallnotifier;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TelegramSender {

    private static final String TAG = "TelegramSender";
    private static final String SERVER_URL = "http://37.49.226.139:5000/send";
    private static final String SERVER_API_KEY = "A7f9xP22sKp90ZqLm";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TelegramSender(Context context) {
        this.context = context;
    }

    public void sendMessage(String message) {
        DebugLogger.log(context, TAG, "CALL sendMessage() called. msg=" + truncate(message, 500));
        sendToServer("call", message);
    }

    public void sendStatusMessage(String message) {
        DebugLogger.log(context, TAG, "REPORT sendStatusMessage() called. msg=" + truncate(message, 500));
        sendToServer("report", message);
    }

    public void sendPing() {
        DebugLogger.log(context, TAG, "PING sendPing() called");
        sendToServer("ping", "alive");
    }

    public boolean sendMessageSync(String message) {
        DebugLogger.log(context, TAG, "CALL sendMessageSync() called. msg=" + truncate(message, 500));
        return sendToServerSync("call", message);
    }

    public void sendToServer(String type, String text) {
        if (text == null || text.isEmpty()) {
            DebugLogger.log(context, TAG, "sendToServer skipped: empty text. type=" + type);
            return;
        }
        final String finalType = (type == null || type.isEmpty()) ? "unknown" : type;
        final String finalText = text;

        DebugLogger.log(context, TAG, "sendToServer start. type=" + finalType + " text=" + truncate(finalText, 500));
        DebugLogger.logState(context, TAG, "before http request");

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(SERVER_URL);
                DebugLogger.log(context, TAG, "Opening connection to " + SERVER_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                String json = "{"
                        + "\"api_key\":\"" + escapeJson(SERVER_API_KEY) + "\","
                        + "\"type\":\"" + escapeJson(finalType) + "\","
                        + "\"text\":\"" + escapeJson(finalText) + "\""
                        + "}";

                DebugLogger.log(context, TAG, "JSON payload ready. len=" + json.length());
                byte[] payload = json.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(payload.length);

                OutputStream os = conn.getOutputStream();
                os.write(payload);
                os.flush();
                os.close();
                DebugLogger.log(context, TAG, "Payload sent bytes=" + payload.length);

                int responseCode = conn.getResponseCode();
                String responseBody = readBody(conn, responseCode >= 200 && responseCode < 300);

                DebugLogger.log(context, TAG, "Server response code=" + responseCode);
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d(TAG, "Server OK: " + responseCode);
                } else {
                    Log.e(TAG, "Server failed: " + responseCode);
                }

                DebugLogger.log(context, TAG, "Server response body=" + truncate(responseBody, 2000));
            } catch (Exception e) {
                DebugLogger.log(context, TAG, "sendToServer exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                Log.e(TAG, "Error sending to server", e);
                DebugLogger.logError(context, TAG, e);
            } finally {
                if (conn != null) {
                    try {
                        conn.disconnect();
                        DebugLogger.log(context, TAG, "Connection closed");
                    } catch (Exception e) {
                        DebugLogger.logError(context, TAG, e);
                    }
                }
            }
        });
    }

    public boolean sendToServerSync(String type, String text) {
        if (text == null || text.isEmpty()) {
            DebugLogger.log(context, TAG, "sendToServerSync skipped: empty text. type=" + type);
            return false;
        }
        String finalType = (type == null || type.isEmpty()) ? "unknown" : type;

        PowerManager.WakeLock wl = null;
        HttpURLConnection conn = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TelegramCallNotifier:SendLock");
                wl.acquire(15000);
            }

            URL url = new URL(SERVER_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            String json = "{"
                    + "\"api_key\":\"" + escapeJson(SERVER_API_KEY) + "\","
                    + "\"type\":\"" + escapeJson(finalType) + "\","
                    + "\"text\":\"" + escapeJson(text) + "\""
                    + "}";

            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);

            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            String responseBody = readBody(conn, responseCode >= 200 && responseCode < 300);

            DebugLogger.log(context, TAG, "sendToServerSync response code=" + responseCode);
            DebugLogger.log(context, TAG, "sendToServerSync response body=" + truncate(responseBody, 2000));

            return isOkResponse(responseCode, responseBody);
        } catch (Exception e) {
            DebugLogger.log(context, TAG, "sendToServerSync exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            DebugLogger.logError(context, TAG, e);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
            if (wl != null) {
                try {
                    if (wl.isHeld()) wl.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean isOkResponse(int code, String body) {
        if (code != 200 || body == null) return false;
        String compact = body.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "");
        return compact.contains("\"ok\":true");
    }

    private static String readBody(HttpURLConnection conn, boolean successStream) {
        InputStream is = null;
        try {
            is = successStream ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return "";
            BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append('\n');
            }
            return response.toString().trim();
        } catch (Exception e) {
            return "";
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c <= 0x1F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
