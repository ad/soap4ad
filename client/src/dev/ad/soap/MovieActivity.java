package dev.ad.soap;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MovieActivity extends Activity {
    private static final String TAG = "SoapTv";

    private Api api;
    private int id;
    private LinearLayout info;
    private TextView play;
    private TextView favorite;
    private TextView mine;
    private TextView seen;
    private TextView title;
    private boolean liked;
    private boolean watched;
    private String streamUrl = "";
    private int startMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        api = new Api(prefs.getString("token", ""), prefs.getString("session", ""));
        id = getIntent().getIntExtra("id", 0);

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(0xFF0F1318);
        root.setPadding(dp(32), dp(24), dp(32), dp(24));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0xFF22262D);
        root.addView(poster, new LinearLayout.LayoutParams(dp(200), dp(300)));
        poster.setTag("poster");

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(28), 0, 0, 0);
        title = new TextView(this);
        title.setText(getIntent().getStringExtra("title"));
        title.setTextSize(26);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        right.addView(title);
        info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.addView(info);
        play = Cards.button(this, "Загрузка…");
        play.setEnabled(false);
        play.setOnClickListener(v -> start());
        favorite = Cards.button(this, "В избранное ТВ");
        favorite.setEnabled(false);
        mine = Cards.button(this, "+ В Мои");
        mine.setEnabled(false);
        mine.setOnClickListener(v -> Actions.post(this, api, () -> {
            liked = !liked;
            paintMarks();
        }, "/movies/like/" + id + "/", "id=" + id + "&sub=like&do=" + (liked ? "unlike" : "like")));
        seen = Cards.button(this, "Посмотрел");
        seen.setEnabled(false);
        seen.setOnClickListener(v -> Actions.post(this, api, () -> {
            watched = !watched;
            paintMarks();
        }, "/movies/" + (watched ? "unwatch/" : "watch/") + id + "/", "id=" + id));
        LinearLayout buttons = new LinearLayout(this);
        for (TextView button : new TextView[] {play, favorite, mine, seen}) {
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
            gap.setMarginEnd(dp(12));
            buttons.addView(button, gap);
        }
        HorizontalScrollView buttonScroll = new HorizontalScrollView(this);
        buttonScroll.setHorizontalScrollBarEnabled(false);
        buttonScroll.setFocusable(false);
        buttonScroll.addView(buttons);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
        row.setMargins(0, dp(14), 0, dp(4));
        right.addView(buttonScroll, row);
        right.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        setContentView(root);
        load(poster);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        load(null);
    }

    private void load(ImageView poster) {
        new Thread(() -> {
            try {
                JSONObject movie = api.object("/movies/description/" + id);
                runOnUiThread(() -> show(movie, poster));
            } catch (Exception e) {
                Log.w(TAG, "movie failed: " + e.getMessage());
                runOnUiThread(() -> play.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    private void show(JSONObject m, ImageView poster) {
        if (poster != null) Images.load(poster, Data.cover(m, 0));
        title.setText(Data.title(m));
        favorite.setOnClickListener(v -> TvHome.favorite(this, false, id, Data.title(m), m.optInt("year"), Data.cover(m, 0)));
        favorite.setEnabled(true);
        liked = Data.flag(m, "liked");
        watched = Data.flag(m, "watched");
        paintMarks();
        mine.setEnabled(true);
        seen.setEnabled(true);
        info.removeAllViews();
        String original = Api.text(m.optString("title_original"));
        if (!original.isEmpty() && !original.equals(Data.title(m))) line(original, 16, 0xFFB6BBC4, false);

        List<String> meta = new ArrayList<>();
        if (m.optInt("year") > 0) meta.add(String.valueOf(m.optInt("year")));
        if (m.optInt("runtime") > 0) meta.add(m.optInt("runtime") + " мин");
        String countries = join(m.optJSONArray("countries"));
        if (!countries.isEmpty()) meta.add(countries);
        String genres = join(m.optJSONArray("genres"));
        if (!genres.isEmpty()) meta.add(genres);
        line(String.join(" · ", meta), 14, 0xFF8B919A, false);

        List<String> ratings = new ArrayList<>();
        if (m.optDouble("imdb_rating", 0) > 0) ratings.add(String.format("IMDb %.1f", m.optDouble("imdb_rating")));
        if (m.optDouble("kinopoisk_rating", 0) > 0) ratings.add(String.format("Кинопоиск %.1f", m.optDouble("kinopoisk_rating")));
        if (!ratings.isEmpty()) line(String.join("   ", ratings), 15, 0xFFE0B85C, true);

        String about = Api.text(m.optString("description_ru"));
        if (about.isEmpty()) about = Api.text(m.optString("description"));
        line(about, 15, 0xFFD7DAE0, false);
        String actors = join(m.optJSONArray("actors"));
        if (!actors.isEmpty()) line("В ролях: " + actors, 13, 0xFF8B919A, false);

        streamUrl = m.optString("stream_url");
        android.net.Uri stream = android.net.Uri.parse(streamUrl);
        Log.i(TAG, "movie stream host=" + stream.getHost() + " path=" + stream.getPath());
        int seconds = 0;
        try {
            seconds = Integer.parseInt(m.optString("start_from", "0"));
        } catch (NumberFormatException ignored) {
        }
        startMs = seconds * 1000;
        play.setEnabled(!streamUrl.isEmpty());
        play.setText(streamUrl.isEmpty() ? "Поток недоступен"
            : seconds > 0 ? "▶  Продолжить с " + seconds / 60 + ":" + String.format("%02d", seconds % 60) : "▶  Смотреть");
        play.requestFocus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        TvHome.result(favorite, requestCode, resultCode);
    }

    private void paintMarks() {
        mine.setText(liked ? "✓ В Моих" : "+ В Мои");
        seen.setText(watched ? "✓ Посмотрел" : "Посмотрел");
    }

    private void start() {
        startActivity(new Intent(this, PlayerActivity.class).putExtra("url", streamUrl).putExtra("eid", 0).putExtra("start", startMs)
            .putExtra("title", title.getText().toString()));
    }

    private void line(String text, int size, int color, boolean bold) {
        if (text.isEmpty()) return;
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(8), 0, 0);
        info.addView(view);
    }

    private static String join(JSONArray array) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; array != null && i < array.length() && parts.size() < 8; i++) {
            Object item = array.opt(i);
            String text = item instanceof JSONObject
                ? ((JSONObject) item).optString("name_ru", ((JSONObject) item).optString("name", ((JSONObject) item).optString("title")))
                : String.valueOf(item);
            if (!text.isEmpty() && !text.equals("null")) parts.add(Api.text(text));
        }
        return String.join(", ", parts);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
