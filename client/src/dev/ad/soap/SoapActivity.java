package dev.ad.soap;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;

public class SoapActivity extends Activity {
    private static final String TAG = "SoapTv";

    private Api api;
    private int sid;
    private final List<JSONObject> episodes = new ArrayList<>();
    private final List<TextView> seasonViews = new ArrayList<>();
    private final List<Integer> seasons = new ArrayList<>();
    private int season = -1;
    private LinearLayout seasonRow;
    private LinearLayout episodeList;
    private TextView about;
    private TextView meta;
    private TextView title;
    private TextView favorite;
    private TextView mine;
    private int year;
    private boolean watching;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        api = new Api(prefs.getString("token", ""), prefs.getString("session", ""));
        sid = getIntent().getIntExtra("sid", 0);

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(0xFF0F1318);
        root.setPadding(dp(32), dp(24), dp(32), dp(24));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setFocusable(false);
        leftScroll.addView(left);
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0xFF2A2A2A);
        left.addView(poster, new LinearLayout.LayoutParams(dp(200), dp(300)));
        Images.load(poster, Api.cover(sid));
        title = new TextView(this);
        title.setText(getIntent().getStringExtra("title"));
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(12), 0, dp(4));
        left.addView(title, new LinearLayout.LayoutParams(dp(300), -2));
        favorite = Cards.button(this, "В избранное ТВ");
        favorite.setId(View.generateViewId());
        favorite.setNextFocusUpId(favorite.getId());
        favorite.setNextFocusLeftId(favorite.getId());
        favorite.setEnabled(false);
        favorite.setOnClickListener(v -> TvHome.favorite(this, true, sid, title.getText().toString(), year, null));
        mine = Cards.button(this, "+ В Мои");
        mine.setId(View.generateViewId());
        mine.setNextFocusUpId(mine.getId());
        mine.setEnabled(false);
        mine.setOnClickListener(v -> Actions.post(this, api, () -> {
            watching = !watching;
            paintMine();
        }, "/soap/" + (watching ? "unwatch/" : "watch/") + sid + "/", "sid=" + sid));
        View.OnKeyListener scrollLeft = (v, key, event) -> {
            int step = key == KeyEvent.KEYCODE_DPAD_DOWN ? dp(120)
                : key == KeyEvent.KEYCODE_DPAD_UP && leftScroll.getScrollY() > 0 ? -dp(120) : 0;
            if (step != 0 && event.getAction() == KeyEvent.ACTION_DOWN) leftScroll.smoothScrollBy(0, step);
            return step != 0;
        };
        favorite.setOnKeyListener(scrollLeft);
        mine.setOnKeyListener(scrollLeft);
        LinearLayout buttons = new LinearLayout(this);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
        gap.setMarginEnd(dp(12));
        buttons.addView(favorite, gap);
        buttons.addView(mine);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-2, -2);
        row.setMargins(0, dp(6), 0, dp(6));
        left.addView(buttons, row);
        meta = new TextView(this);
        meta.setTextSize(13);
        meta.setTextColor(0xFF9AA0A6);
        left.addView(meta, new LinearLayout.LayoutParams(dp(300), -2));
        about = new TextView(this);
        about.setTextSize(14);
        about.setPadding(0, dp(8), 0, 0);
        left.addView(about, new LinearLayout.LayoutParams(dp(300), -2));
        TextView hint = new TextView(this);
        hint.setText("Удерживайте OK на серии, чтобы отметить просмотренное");
        hint.setTextSize(12);
        hint.setTextColor(0xFF6E7681);
        hint.setPadding(0, dp(8), 0, 0);
        left.addView(hint, new LinearLayout.LayoutParams(dp(300), -2));
        root.addView(leftScroll, new LinearLayout.LayoutParams(dp(330), -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView seasonScroll = new HorizontalScrollView(this);
        seasonScroll.setHorizontalScrollBarEnabled(false);
        seasonRow = new LinearLayout(this);
        seasonScroll.addView(seasonRow);
        right.addView(seasonScroll, new LinearLayout.LayoutParams(-1, -2));
        ScrollView episodeScroll = new ScrollView(this);
        episodeList = new LinearLayout(this);
        episodeList.setOrientation(LinearLayout.VERTICAL);
        episodeScroll.addView(episodeList);
        right.addView(episodeScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));

        setContentView(root);
        load();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        load();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        TvHome.result(favorite, requestCode, resultCode);
    }

    private void load() {
        new Thread(() -> {
            try {
                JSONArray array = api.object("/episodes/" + sid + "?nodesc").getJSONArray("episodes");
                JSONObject description = null;
                try {
                    description = api.object("/soap/description/" + sid);
                } catch (Exception e) {
                    Log.w(TAG, "description failed: " + e.getMessage());
                }
                JSONObject info = description;
                runOnUiThread(() -> show(array, info));
            } catch (Exception e) {
                Log.w(TAG, "episodes failed: " + e.getMessage());
                runOnUiThread(() -> about.setText("Ошибка: " + e.getMessage()));
            }
        }).start();
    }

    private void show(JSONArray array, JSONObject info) {
        if (info != null) {
            title.setText(Data.title(info));
            year = info.optInt("year");
            watching = Data.flag(info, "watching");
            paintMine();
            mine.setEnabled(true);
            about.setText(Api.text(info.optString("description")));
            List<String> parts = new ArrayList<>();
            for (String key : new String[] {"network", "country"}) {
                if (!info.optString(key).isEmpty()) parts.add(info.optString(key));
            }
            meta.setText(String.join(" · ", parts));
        }
        favorite.setEnabled(title.length() > 0);
        episodes.clear();
        TreeSet<Integer> found = new TreeSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject e = array.optJSONObject(i);
            if (e == null) continue;
            episodes.add(e);
            found.add(e.optInt("season"));
        }
        seasons.clear();
        seasons.addAll(found);
        if (season < 0 || !found.contains(season)) season = firstOpenSeason();
        buildSeasons();
        buildEpisodes(true);
    }

    private void paintMine() {
        mine.setText(watching ? "✓ В Моих" : "+ В Мои");
    }

    private void marks(JSONObject e, int index) {
        int s = e.optInt("season");
        int number = e.optInt("episode");
        boolean watched = e.optInt("watched") == 1;
        String[] labels = {
            watched ? "Снять отметку с серии " + number : "Отметить серию " + number + " просмотренной",
            "Отметить сезон " + s + " просмотренным",
            "Отметить весь сериал просмотренным",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title.getText())
            .setItems(labels, (dialog, which) -> {
                if (which == 0) {
                    Actions.post(this, api, () -> mark(index, x -> x == e, !watched),
                        "/episodes/" + (watched ? "unwatch/" : "watch/") + sid + "/" + s + "/" + number + "/",
                        "sid=" + sid + "&season=" + s + "&episode=" + number);
                } else if (which == 1) {
                    Actions.post(this, api, () -> mark(index, x -> x.optInt("season") == s, true), seasonWatch(s));
                } else {
                    List<String> calls = new ArrayList<>();
                    for (int each : seasons) calls.addAll(List.of(seasonWatch(each)));
                    Actions.post(this, api, () -> mark(index, x -> true, true), calls.toArray(new String[0]));
                }
            })
            .show();
    }

    private String[] seasonWatch(int s) {
        return new String[] {"/episodes/watch/full/" + sid + "/" + s + "/", "sid=" + sid + "&season=" + s};
    }

    private void mark(int index, Predicate<JSONObject> which, boolean watched) {
        for (JSONObject e : episodes) {
            if (!which.test(e)) continue;
            try {
                e.put("watched", watched ? 1 : 0);
            } catch (JSONException ignored) {
            }
        }
        buildSeasons();
        buildEpisodes(false);
        View row = episodeList.getChildAt(index);
        if (row != null) row.requestFocus();
    }

    private int firstOpenSeason() {
        for (int s : seasons) {
            for (JSONObject e : episodes) {
                if (e.optInt("season") == s && e.optInt("watched") != 1) return s;
            }
        }
        return seasons.isEmpty() ? -1 : seasons.get(seasons.size() - 1);
    }

    private void buildSeasons() {
        seasonRow.removeAllViews();
        seasonViews.clear();
        for (int s : seasons) {
            TextView view = new TextView(this);
            view.setId(View.generateViewId());
            view.setText("Сезон " + s + (seasonWatched(s) ? "  ✓" : ""));
            view.setTextSize(16);
            view.setFocusable(true);
            view.setGravity(Gravity.CENTER);
            view.setPadding(dp(20), dp(8), dp(20), dp(8));
            view.setOnFocusChangeListener((v, focused) -> {
                if (focused) selectSeason(s);
                paintSeasons();
            });
            // A mouse click does not focus the view, so the click itself must switch the season.
            view.setOnClickListener(v -> {
                selectSeason(s);
                paintSeasons();
                if (v.hasFocus()) episodeList.requestFocus();
            });
            view.setNextFocusLeftId(seasonViews.isEmpty() ? mine.getId() : View.NO_ID);
            LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-2, -2);
            margin.setMargins(0, 0, dp(10), dp(14));
            seasonRow.addView(view, margin);
            seasonViews.add(view);
        }
        paintSeasons();
    }

    private void selectSeason(int s) {
        if (season == s) return;
        season = s;
        buildEpisodes(false);
    }

    private void paintSeasons() {
        for (int i = 0; i < seasonViews.size(); i++) {
            TextView v = seasonViews.get(i);
            boolean selected = seasons.get(i) == season;
            v.setBackground(v.hasFocus() ? shape(0xFF2B4C7E, 0xFF8CB4E8, 20) : selected ? shape(0xFF8CB4E8, 0, 20) : shape(0xFF1F2733, 0x33FFFFFF, 20));
            v.setTextColor(selected && !v.hasFocus() ? 0xFF0F1318 : v.hasFocus() ? 0xFFFFFFFF : 0xFFB6BBC4);
            v.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void buildEpisodes(boolean focus) {
        episodeList.removeAllViews();
        List<JSONObject> shown = new ArrayList<>();
        for (JSONObject e : episodes) if (e.optInt("season") == season) shown.add(e);
        shown.sort(Comparator.comparingInt((JSONObject e) -> e.optInt("episode")));
        View target = null;
        View firstOpen = null;
        for (JSONObject e : shown) {
            View row = episodeRow(e, episodeList.getChildCount());
            episodeList.addView(row);
            if (target == null && e.optInt("start_from") > 0) target = row;
            if (firstOpen == null && e.optInt("watched") != 1) firstOpen = row;
        }
        if (episodeList.getChildCount() > 0 && !seasonViews.isEmpty()) {
            episodeList.getChildAt(0).setNextFocusUpId(seasonViews.get(seasons.indexOf(season)).getId());
        }
        if (target == null) target = firstOpen;
        if (target == null && episodeList.getChildCount() > 0) target = episodeList.getChildAt(0);
        for (View season : seasonViews) {
            season.setNextFocusDownId(target != null ? target.getId() : View.NO_ID);
        }
        mine.setNextFocusRightId(target != null ? target.getId() : mine.getId());
        if (focus && target != null) target.requestFocus();
    }

    private View episodeRow(JSONObject e, int index) {
        int resume = e.optInt("start_from");
        boolean watched = e.optInt("watched") == 1 && resume == 0;
        LinearLayout row = new LinearLayout(this);
        row.setId(View.generateViewId());
        row.setFocusable(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setNextFocusLeftId(mine.getId());
        row.setPadding(dp(12), dp(10), dp(20), dp(10));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, shape(0xFF2B4C7E, 0xFF8CB4E8, 12));
        background.addState(new int[] {}, shape(watched ? 0 : 0xFF1A212B, 0, 12));
        row.setBackground(background);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2);
        gap.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(gap);

        TextView number = new TextView(this);
        number.setText(String.valueOf(e.optInt("episode")));
        number.setTextSize(16);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        number.setGravity(Gravity.CENTER);
        number.setTextColor(resume > 0 ? 0xFF0F1318 : watched ? 0xFF6E7681 : 0xFF8CB4E8);
        number.setBackground(shape(resume > 0 ? 0xFF8CB4E8 : watched ? 0 : 0x338CB4E8, watched ? 0x33FFFFFF : 0, 10));
        row.addView(number, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView label = new TextView(this);
        label.setText(Data.episodeTitle(e));
        label.setTextSize(18);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(watched ? 0xFF6E7681 : 0xFFFFFFFF);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.setMargins(dp(16), 0, dp(16), 0);
        row.addView(label, labelParams);

        if (resume > 0 || watched) {
            TextView status = new TextView(this);
            status.setTextSize(15);
            status.setText(watched ? "✓  просмотрено" : "▶  " + resume / 60 + ":" + String.format("%02d", resume % 60));
            status.setTextColor(watched ? 0xFF6FBF73 : 0xFF8CB4E8);
            row.addView(status);
        }
        row.setOnClickListener(v -> Playback.start(this, api, sid, this.title.getText().toString(), e.optJSONArray("files")));
        row.setOnLongClickListener(v -> {
            marks(e, index);
            return true;
        });
        return row;
    }

    private boolean seasonWatched(int s) {
        for (JSONObject e : episodes) if (e.optInt("season") == s && e.optInt("watched") != 1) return false;
        return true;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(2), stroke);
        return shape;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
