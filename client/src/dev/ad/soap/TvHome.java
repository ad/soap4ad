package dev.ad.soap;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;
import android.widget.Toast;

final class TvHome {
    private static final int REQUEST = 41;

    private TvHome() {}

    static void favorite(Activity activity, boolean tv, int id, String title, int year, String poster) {
        Uri.Builder uri = Uri.parse("adtv://fav/add").buildUpon().appendQueryParameter("soap", String.valueOf(id))
            .appendQueryParameter("media", tv ? "tv" : "movie").appendQueryParameter("title", title);
        if (year > 0) uri.appendQueryParameter("year", String.valueOf(year));
        if (poster != null && !poster.isEmpty()) uri.appendQueryParameter("poster", poster);
        try {
            activity.startActivityForResult(new Intent(Intent.ACTION_VIEW, uri.build()).setPackage("dev.ad.tv"), REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "Установите TV Home", Toast.LENGTH_LONG).show();
        }
    }

    static void result(TextView button, int requestCode, int resultCode) {
        if (requestCode != REQUEST) return;
        button.setText(resultCode == Activity.RESULT_OK ? "✓ В избранном ТВ"
            : resultCode == Activity.RESULT_FIRST_USER ? "✓ В избранном ТВ" : "Не удалось");
    }
}
