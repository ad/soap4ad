package dev.ad.soap;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

// Theme.NoDisplay must finish before onResume, so the async title lookup runs in MainActivity.
public class LinkActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        Intent next = new Intent(this, MainActivity.class);
        if (uri != null && getSharedPreferences("auth", MODE_PRIVATE).getString("token", null) != null) {
            String sid = id(uri.getQueryParameter("sid"));
            String movie = id(uri.getQueryParameter("movie"));
            String title = uri.getQueryParameter("title");
            title = title == null ? "" : title.trim();
            String year = uri.getQueryParameter("year");
            if (sid != null) {
                next = new Intent(this, SoapActivity.class).putExtra("sid", Integer.parseInt(sid)).putExtra("title", title);
            } else if (movie != null) {
                next = new Intent(this, MovieActivity.class).putExtra("id", Integer.parseInt(movie)).putExtra("title", title);
            } else if (!title.isEmpty()) {
                next.putExtra("title", title).putExtra("type", uri.getQueryParameter("type"))
                    .putExtra("year", year != null && year.matches("[0-9]{4}") ? Integer.parseInt(year) : 0);
            }
        }
        startActivity(next);
        finish();
    }

    private static String id(String value) {
        return value != null && value.matches("[1-9][0-9]{0,8}") ? value : null;
    }
}
