package dev.ad.soap;

import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class HomeScreen implements Screen {
    private static final String TAG = "SoapTv";

    private final MainActivity host;
    private final ScrollView page;
    private final LinearLayout root;
    private int generation;
    private boolean focusPlaced;

    HomeScreen(MainActivity host) {
        this.host = host;
        root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Cards.dp(host, 24), Cards.dp(host, 4), Cards.dp(host, 24), Cards.dp(host, 12));
        page = new ScrollView(host);
        page.setFillViewport(true);
        page.setFocusable(false);
        page.addView(root);
        load();
    }

    @Override
    public View view() {
        return page;
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        Api api = host.api();
        int mine = ++generation;
        root.removeAllViews();
        focusPlaced = false;
        new Thread(() -> {
            try {
                addRow(mine, "Недавние", recent(api.array("/episodes/continue")));
                addRow(mine, "Новые сериалы", series(Data.sorted(Data.cached(api, "/soap/?nodesc"), "sid"), 30));
                addRow(mine, "Новые фильмы", movies(Data.sorted(Data.cached(api, "/movies/"), "id"), 30));
                addRow(mine, "Популярные сериалы", series(Data.list(Data.cached(api, "/soap/top/all")), 30));
                addRow(mine, "Популярные фильмы", movies(Data.list(Data.cached(api, "/movies/popular")), 32));
                addRow(mine, "Мои сериалы", unwatched(Data.sorted(api.array("/soap/my/?nodesc"), "updated")));
            } catch (Exception e) {
                Log.w(TAG, "home failed: " + e.getMessage());
                host.runOnUiThread(() -> {
                    if (mine != generation) return;
                    if (String.valueOf(e.getMessage()).contains("401")) host.logout();
                    else if (root.getChildCount() == 0) {
                        TextView text = new TextView(host);
                        text.setText("Ошибка: " + e.getMessage());
                        root.addView(text);
                    }
                });
            }
        }).start();
    }

    private List<Cards.Card> recent(JSONArray array) {
        List<Cards.Card> cards = new ArrayList<>();
        for (JSONObject e : Data.sorted(array, "played")) {
            String ru = e.optString("soap_ru");
            String name = Api.text(ru.isEmpty() ? e.optString("soap") : ru);
            int sid = e.optInt("sid");
            cards.add(new Cards.Card(Api.cover(sid), name, null, null, "S" + e.optInt("season") + "E" + e.optInt("episode"),
                () -> Playback.start(host, host.api(), sid, name, e.optJSONArray("files")), () -> host.openSoap(sid, name)));
        }
        return cards;
    }

    private List<Cards.Card> series(List<JSONObject> items, int limit) {
        List<Cards.Card> cards = new ArrayList<>();
        for (JSONObject s : items.subList(0, Math.min(limit, items.size()))) cards.add(Library.seriesCard(host, s, null));
        return cards;
    }

    private List<Cards.Card> movies(List<JSONObject> items, int limit) {
        List<Cards.Card> cards = new ArrayList<>();
        for (JSONObject m : items.subList(0, Math.min(limit, items.size()))) cards.add(Library.movieCard(host, m));
        return cards;
    }

    private List<Cards.Card> unwatched(List<JSONObject> items) {
        List<Cards.Card> cards = new ArrayList<>();
        for (JSONObject s : items) {
            if (s.optInt("unwatched") <= 0) continue;
            cards.add(Library.seriesCard(host, s, "+" + s.optInt("unwatched")));
            if (cards.size() == 40) break;
        }
        return cards;
    }

    private void addRow(int mine, String name, List<Cards.Card> cards) {
        if (cards.isEmpty()) return;
        host.runOnUiThread(() -> {
            if (mine != generation) return;
            TextView title = new TextView(host);
            title.setText(name);
            title.setTextSize(18);
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setPadding(Cards.dp(host, 8), Cards.dp(host, 10), 0, Cards.dp(host, 2));
            root.addView(title);
            boolean top = root.getChildCount() == 1;
            LinearLayout strip = new LinearLayout(host);
            strip.setPadding(Cards.dp(host, 4), Cards.dp(host, 14), Cards.dp(host, 4), Cards.dp(host, 14));
            strip.setClipChildren(false);
            View first = null;
            for (Cards.Card card : cards) {
                View view = Cards.view(host, card, top ? () -> page.smoothScrollTo(0, 0) : null);
                if (first == null) first = view;
                strip.addView(view);
            }
            HorizontalScrollView scroll = new HorizontalScrollView(host);
            scroll.setClipChildren(false);
            scroll.setClipToPadding(false);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(strip);
            root.addView(scroll);
            if (!focusPlaced) {
                focusPlaced = true;
                first.requestFocus();
            }
        });
    }
}
