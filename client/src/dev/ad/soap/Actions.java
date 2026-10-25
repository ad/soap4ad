package dev.ad.soap;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

final class Actions {
    private static final String TAG = "SoapTv";

    private Actions() {}

    static void post(Activity activity, Api api, Runnable done, String... pathsAndForms) {
        new Thread(() -> {
            try {
                for (int i = 0; i < pathsAndForms.length; i += 2) api.post(pathsAndForms[i], pathsAndForms[i + 1]);
                Data.clear();
                activity.runOnUiThread(done);
            } catch (Exception e) {
                Log.w(TAG, "action failed: " + e.getMessage());
                activity.runOnUiThread(() -> Toast.makeText(activity, "Не удалось: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
