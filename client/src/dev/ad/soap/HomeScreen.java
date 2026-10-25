package dev.ad.soap;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HomeScreen implements Screen {
    private static final String TAG = "SoapTv";
    private static final String ORDER = "home_order";
    static final String[] ROWS = {"Недавние", "Новые серии", "Новые сериалы", "Новые фильмы",
        "Популярные сериалы", "Популярные фильмы", "Мои сериалы", "Скоро"};

    private final MainActivity host;
    private final ScrollView page;
    private final LinearLayout root;
    private int generation;
    private boolean focusPlaced;
    private View firstCard;
    private String focusRow;
    private String focusTitle;
    private int focusIndex;
    private Runnable closing;

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

    static List<String> order(String saved) {
        List<String> order = new ArrayList<>();
        for (String name : saved.split(",")) if (List.of(ROWS).contains(name) && !order.contains(name)) order.add(name);
        for (String name : ROWS) if (!order.contains(name)) order.add(name);
        return order;
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
        firstCard = null;
        List<String> order = order(host.prefs().getString(ORDER, ""));
        new Thread(() -> {
            try {
                for (String name : order) addRow(mine, name, row(api, name));
            } catch (Exception e) {
                Log.w(TAG, "home failed: " + e.getMessage());
                host.runOnUiThread(() -> {
                    if (mine != generation) return;
                    if (String.valueOf(e.getMessage()).contains("401")) host.logout();
                    else if (firstCard == null) {
                        TextView text = new TextView(host);
                        text.setText("Ошибка: " + e.getMessage());
                        root.addView(text);
                    }
                });
            }
            host.runOnUiThread(() -> {
                if (mine == generation && !focusPlaced && firstCard != null) firstCard.requestFocus();
            });
        }).start();
    }

    private List<Cards.Card> row(Api api, String name) throws Exception {
        switch (name) {
            case "Недавние":
                return recent(api.array("/episodes/continue"));
            case "Новые серии":
                return fresh(api.array("/episodes/new"));
            case "Новые сериалы":
                return series(Data.sorted(Data.cached(api, "/soap/?nodesc"), "sid"), 30);
            case "Новые фильмы":
                return movies(Data.sorted(Data.cached(api, "/movies/"), "id"), 30);
            case "Популярные сериалы":
                return series(Data.list(Data.cached(api, "/soap/top/all")), 30);
            case "Популярные фильмы":
                return movies(Data.list(Data.cached(api, "/movies/popular")), 32);
            case "Мои сериалы":
                return unwatched(Data.sorted(api.array("/soap/my/?nodesc"), "updated"));
            default:
                return upcoming(api.array("/shedule/my/"));
        }
    }

    void arrange(View gear) {
        List<String> order = order(host.prefs().getString(ORDER, ""));
        String before = String.join(",", order);
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(Cards.dp(host, 32), Cards.dp(host, 24), Cards.dp(host, 32), Cards.dp(host, 24));
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xFF161C24);
        card.setCornerRadius(Cards.dp(host, 20));
        card.setStroke(Cards.dp(host, 1), 0x22FFFFFF);
        panel.setBackground(card);
        FrameLayout screen = new FrameLayout(host);
        screen.addView(panel, new FrameLayout.LayoutParams(Cards.dp(host, 440), -2, Gravity.CENTER));
        TextView heading = new TextView(host);
        heading.setText("Порядок строк");
        heading.setTextSize(20);
        heading.setTextColor(0xFFFFFFFF);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        TextView hint = new TextView(host);
        hint.setText("OK берёт строку, ↑↓ двигают, OK отпускает, «Назад» сохраняет");
        hint.setTextSize(14);
        hint.setTextColor(0xFF8B919A);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Cards.dp(host, 4), 0, Cards.dp(host, 10));
        panel.addView(heading, new LinearLayout.LayoutParams(-2, -2));
        panel.addView(hint, new LinearLayout.LayoutParams(-2, -2));
        List<TextView> items = new ArrayList<>();
        int[] picked = {-1};
        for (int i = 0; i < order.size(); i++) {
            int at = i;
            TextView item = item(order.get(i));
            item.setOnClickListener(v -> {
                picked[0] = picked[0] == at ? -1 : at;
                label(items, order, picked[0]);
            });
            item.setOnKeyListener((v, code, event) -> {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
                int step = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
                int to = at + step;
                if (step == 0) return false;
                if (to < 0 || to >= order.size()) return true;
                if (picked[0] != at) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    Collections.swap(order, at, to);
                    picked[0] = to;
                    label(items, order, to);
                    items.get(to).requestFocus();
                }
                return true;
            });
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2);
            gap.topMargin = Cards.dp(host, 6);
            panel.addView(item, gap);
            items.add(item);
        }
        Object back = null;
        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedCallback callback = this::closeArrange;
            host.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            back = callback;
        }
        Object registered = back;
        closing = () -> {
            closing = null;
            if (registered != null) host.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) registered);
            page.removeAllViews();
            page.addView(root);
            String after = String.join(",", order);
            if (!after.equals(before)) {
                host.prefs().edit().putString(ORDER, after).apply();
                load();
            }
            focusPlaced = true;
            gear.requestFocus();
        };
        page.removeAllViews();
        page.addView(screen);
        items.get(0).requestFocus();
    }

    boolean closeArrange() {
        if (closing == null) return false;
        closing.run();
        return true;
    }

    private TextView item(String text) {
        TextView item = new TextView(host);
        item.setText(text);
        item.setTextSize(16);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setPadding(Cards.dp(host, 30), Cards.dp(host, 8), Cards.dp(host, 30), Cards.dp(host, 8));
        int activated = android.R.attr.state_activated;
        int focused = android.R.attr.state_focused;
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {activated, focused}, pill(0xFF2B4C7E, true));
        background.addState(new int[] {activated}, pill(0xFF2B4C7E, false));
        background.addState(new int[] {focused}, pill(0x33FFFFFF, true));
        background.addState(new int[] {}, pill(Color.TRANSPARENT, false));
        item.setBackground(background);
        item.setTextColor(new ColorStateList(new int[][] {{activated}, {focused}, {}}, new int[] {0xFFFFFFFF, 0xFFFFFFFF, 0xFFB0B7C3}));
        return item;
    }

    private GradientDrawable pill(int color, boolean ring) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Cards.dp(host, 22));
        shape.setColor(color);
        if (ring) shape.setStroke(Cards.dp(host, 2), 0xFF8CB4E8);
        return shape;
    }

    private static void label(List<TextView> items, List<String> order, int picked) {
        for (int i = 0; i < items.size(); i++) {
            TextView item = items.get(i);
            item.setText((i == picked ? "⇅  " : "") + order.get(i));
            item.setActivated(i == picked);
            item.setTypeface(i == picked ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private List<Cards.Card> recent(JSONArray array) {
        List<Cards.Card> cards = new ArrayList<>();
        for (JSONObject e : Data.sorted(array, "played")) cards.add(episode(e, number(e), true));
        return cards;
    }

    private List<Cards.Card> fresh(JSONArray array) {
        List<Cards.Card> cards = new ArrayList<>();
        List<JSONObject> items = Data.list(array);
        items.sort((a, b) -> b.optString("first_aired").compareTo(a.optString("first_aired")));
        for (List<JSONObject> group : bySoap(items)) {
            JSONObject e = group.get(0);
            String date = e.optString("first_aired");
            String day = date.length() == 10 ? date.substring(8) + "." + date.substring(5, 7) + " · " : "";
            cards.add(group.size() == 1 ? episode(e, day + number(e), true) : episode(e, day + episodes(group.size()), false));
        }
        return cards;
    }

    private List<Cards.Card> upcoming(JSONArray array) {
        List<Cards.Card> cards = new ArrayList<>();
        for (List<JSONObject> group : bySoap(Data.list(array))) {
            JSONObject e = group.get(0);
            String date = e.optString("date");
            String day = date.length() >= 5 ? date.substring(0, 5) + " · " : "";
            cards.add(episode(e, day + (group.size() == 1 ? number(e) : episodes(group.size())), false));
        }
        return cards;
    }

    private static Collection<List<JSONObject>> bySoap(List<JSONObject> items) {
        Map<Integer, List<JSONObject>> groups = new LinkedHashMap<>();
        for (JSONObject e : items) groups.computeIfAbsent(e.optInt("sid"), sid -> new ArrayList<>()).add(e);
        return groups.values();
    }

    private static String number(JSONObject e) {
        return "S" + e.optInt("season") + "E" + e.optInt("episode");
    }

    static String episodes(int n) {
        int ones = n % 10;
        String word = n % 100 / 10 == 1 || ones == 0 || ones >= 5 ? "серий" : ones == 1 ? "серия" : "серии";
        return n + " " + word;
    }

    private Cards.Card episode(JSONObject e, String caption, boolean playable) {
        String ru = e.optString("soap_ru");
        String name = Api.text(ru.isEmpty() ? e.optString("soap") : ru);
        int sid = e.optInt("sid");
        Runnable open = () -> host.openSoap(sid, name);
        return new Cards.Card(Api.cover(sid), name, null, null, caption,
            playable ? () -> Playback.start(host, host.api(), sid, name, e.optJSONArray("files")) : open, playable ? open : null);
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
            boolean top = firstCard == null;
            LinearLayout strip = new LinearLayout(host);
            strip.setPadding(Cards.dp(host, 4), Cards.dp(host, 14), Cards.dp(host, 4), Cards.dp(host, 14));
            strip.setClipChildren(false);
            for (int i = 0; i < cards.size(); i++) {
                Cards.Card card = cards.get(i);
                int index = i;
                strip.addView(Cards.view(host, card, () -> {
                    focusPlaced = true;
                    focusRow = name;
                    focusTitle = card.title;
                    focusIndex = index;
                    if (top) page.smoothScrollTo(0, 0);
                }));
            }
            if (top) firstCard = strip.getChildAt(0);
            HorizontalScrollView scroll = new HorizontalScrollView(host);
            scroll.setClipChildren(false);
            scroll.setClipToPadding(false);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(strip);
            root.addView(scroll);
            if (!focusPlaced && (focusRow == null || name.equals(focusRow))) {
                focusPlaced = true;
                strip.getChildAt(focusRow == null ? 0 : target(cards)).requestFocus();
            }
        });
    }

    private int target(List<Cards.Card> cards) {
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).title.equals(focusTitle)) return i;
        return Math.min(focusIndex, cards.size() - 1);
    }
}
