package dev.ad.soap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

final class Playback {
    private static final String TAG = "SoapTv";
    private static final int MAX_QUALITY = 3;
    private static final String[] TRANSLATES = {"", "Оригинал", "Оригинал, англ. субтитры", "Оригинал, рус. субтитры", "Перевод"};
    private static final String[] QUALITIES = {"", "SD", "HD", "FullHD", "UltraHD"};

    private Playback() {}

    static void start(Activity activity, Api api, int soapId, String title, JSONArray files) {
        JSONObject file = pickFile(activity, files, 0);
        if (file == null) {
            Toast.makeText(activity, "Нет доступных файлов", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                JSONObject response = stream(api, soapId, file);
                Intent intent = new Intent(activity, PlayerActivity.class).putExtra("url", response.optString("stream"))
                    .putExtra("eid", file.optInt("eid")).putExtra("start", startMs(response)).putExtra("sid", soapId).putExtra("title", title);
                activity.runOnUiThread(() -> activity.startActivity(intent));
            } catch (Exception e) {
                Log.w(TAG, "play failed: " + e.getMessage());
                activity.runOnUiThread(() ->
                    Toast.makeText(activity, "Не удалось запустить: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    static JSONObject stream(Api api, int soapId, JSONObject file) throws Exception {
        JSONObject response = api.play(file.optInt("eid"), soapId, file.optString("hash"));
        if (response.optString("stream").isEmpty()) throw new Exception(response.optString("message", "нет потока"));
        return response;
    }

    static int startMs(JSONObject response) {
        try {
            return Integer.parseInt(response.optString("start_from", "0")) * 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String translateName(int translate) {
        return translate >= 1 && translate < TRANSLATES.length ? TRANSLATES[translate] : "Вариант " + translate;
    }

    static String qualityName(int quality) {
        return quality >= 1 && quality < QUALITIES.length ? QUALITIES[quality] : "—";
    }

    // translate 0 means the one the viewer picked last time in the player.
    static JSONObject pickFile(Context context, JSONArray files, int translate) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        int wanted = translate > 0 ? translate : prefs.getInt("translate", 0);
        int quality = prefs.getInt("quality", 0);
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; files != null && i < files.length(); i++) {
            JSONObject f = files.optJSONObject(i);
            if (f == null) continue;
            int score = score(f.optInt("translate"), f.optInt("quality"), wanted, quality);
            if (score > bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return best;
    }

    static int score(int translate, int quality, int wantedTranslate, int wantedQuality) {
        int cap = wantedQuality > 0 ? wantedQuality : MAX_QUALITY;
        return (translate == wantedTranslate ? 100 : 0) + (quality > cap ? -quality : quality) * 10 + translate;
    }
}
