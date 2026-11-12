package dev.ad.soap;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentsActivity extends Activity {
    private static final String TAG = "SoapTv";

    private LinearLayout list;
    private ScrollView scroll;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        Api api = new Api(prefs.getString("token", ""), prefs.getString("session", ""));
        int sid = getIntent().getIntExtra("sid", 0);
        int season = getIntent().getIntExtra("season", 0);
        int episode = getIntent().getIntExtra("episode", 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F1318);
        root.setPadding(dp(48), dp(24), dp(48), dp(24));
        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra("title"));
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);
        status = new TextView(this);
        status.setText("Загрузка…");
        status.setTextSize(14);
        status.setTextColor(0xFF8B919A);
        status.setPadding(0, 0, 0, dp(12));
        root.addView(status);
        // Comments are plain text, so the remote scrolls the list itself and a long comment can be read to its end.
        scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        new Thread(() -> {
            try {
                JSONArray array = api.array("/comments/" + sid + "/" + season + "/" + episode + "/");
                runOnUiThread(() -> show(Data.list(array)));
            } catch (Exception e) {
                Log.w(TAG, "comments failed: " + e.getMessage());
                runOnUiThread(() -> status.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    private void show(List<JSONObject> comments) {
        status.setText(comments.isEmpty() ? "Комментариев нет" : Data.plural(comments.size(), "комментарий", "комментария", "комментариев"));
        SimpleDateFormat date = new SimpleDateFormat("dd.MM.yyyy", Locale.ROOT);
        Data.thread(comments, c -> c.optString("cid"), c -> c.optString("parent"), (c, depth) -> {
            int rating = c.optInt("rating");
            String head = c.optString("login") + "  ·  " + date.format(new Date(c.optLong("date") * 1000))
                + (rating != 0 ? "  ·  " + (rating > 0 ? "+" : "") + rating : "");
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(16), dp(10), dp(16), dp(12));
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xFF1A212B);
            background.setCornerRadius(dp(12));
            item.setBackground(background);
            TextView header = new TextView(this);
            header.setText(head);
            header.setTextSize(13);
            header.setTextColor(0xFF8CB4E8);
            item.addView(header);
            TextView text = new TextView(this);
            text.setText(Api.text(c.optString("text").replace("\n", "<br>")));
            text.setTextSize(17);
            text.setTextColor(0xFFE6E8EB);
            text.setPadding(0, dp(4), 0, 0);
            item.addView(text);
            LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2);
            margin.setMargins(dp(32) * Math.min(depth, 4), 0, 0, dp(8));
            list.addView(item, margin);
        });
        scroll.requestFocus();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
