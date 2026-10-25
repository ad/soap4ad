package dev.ad.soap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;

final class Update {
    private static final String TAG = "SoapTv";
    private static final String LATEST = "https://api.github.com/repos/ad/soap4ad/releases/latest";

    private static final long INTERVAL = 3 * 3_600_000L;
    private static long checked;

    private Update() {}

    static void check(Activity activity) {
        long now = System.currentTimeMillis();
        if (checked != 0 && now - checked < INTERVAL) return;
        checked = now;
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(Api.read(open(LATEST).getInputStream()));
                String tag = release.optString("tag_name");
                long installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
                if (build(tag) <= installed) return;
                JSONArray assets = release.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if (!asset.optString("name").endsWith(".apk")) continue;
                    String url = asset.optString("browser_download_url");
                    activity.runOnUiThread(() -> offer(activity, tag, url));
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "update check failed: " + e.getMessage());
            }
        }).start();
    }

    static int build(String tag) {
        try {
            return Integer.parseInt(tag.substring(tag.lastIndexOf('.') + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void offer(Activity activity, String tag, String url) {
        if (activity.isDestroyed()) return;
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Доступно обновление " + tag)
            .setPositiveButton("Обновить", (d, w) -> install(activity, url))
            .setNegativeButton("Позже", null)
            .show();
    }

    private static void install(Activity activity, String url) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            checked = 0;
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            } catch (ActivityNotFoundException e) {
                toast(activity, "Разрешите Soap TV установку приложений в настройках безопасности");
            }
            return;
        }
        toast(activity, "Скачиваю обновление…");
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            PackageInstaller installer = app.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            int id = -1;
            try {
                id = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(id)) {
                    try (InputStream in = open(url).getInputStream(); OutputStream out = session.openWrite("apk", 0, -1)) {
                        byte[] buffer = new byte[65536];
                        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
                        session.fsync(out);
                    }
                    Intent done = new Intent(app, Receiver.class);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    session.commit(PendingIntent.getBroadcast(app, 0, done, flags).getIntentSender());
                }
            } catch (Exception e) {
                Log.w(TAG, "update failed: " + e.getMessage());
                if (id != -1) installer.abandonSession(id);
                activity.runOnUiThread(() -> toast(app, "Не удалось обновить: " + e.getMessage()));
            }
        }).start();
    }

    // The TV's first TLS handshake to GitHub is often reset, the second one passes.
    private static HttpURLConnection open(String url) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(20_000);
                c.setReadTimeout(30_000);
                if (c.getResponseCode() >= 400) throw new IOException("HTTP " + c.getResponseCode());
                return c;
            } catch (SocketException e) {
                if (attempt == 3) throw e;
            }
        }
    }

    private static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
    }

    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                toast(context, "Не удалось обновить: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    }
}
