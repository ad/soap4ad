package dev.ad.soap;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SearchScreen implements Screen {
    private static final String TAG = "SoapTv";
    private static final int LIMIT = 35;

    private final MainActivity host;
    private final LinearLayout root;
    private final EditText input;
    private final LinearLayout box;
    private final CardGrid grid;
    private JSONArray series;
    private JSONArray movies;

    SearchScreen(MainActivity host) {
        this.host = host;
        root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        input = new EditText(host);
        input.setHint("Название сериала или фильма");
        input.setHintTextColor(0xFF6E7681);
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(18);
        input.setSingleLine();
        input.setBackground(null);
        input.setPadding(Cards.dp(host, 8), Cards.dp(host, 10), Cards.dp(host, 16), Cards.dp(host, 10));
        ImageView icon = new ImageView(host);
        icon.setImageResource(android.R.drawable.ic_menu_search);
        icon.setColorFilter(0xFF8B919A);
        box = new LinearLayout(host);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Cards.dp(host, 18), 0, Cards.dp(host, 8), 0);
        paintBox(false);
        input.setOnFocusChangeListener((v, focused) -> paintBox(focused));
        box.addView(icon, new LinearLayout.LayoutParams(Cards.dp(host, 24), Cards.dp(host, 24)));
        box.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                search();
            }
        });
        grid = new CardGrid(host);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, -2);
        boxParams.setMargins(Cards.dp(host, 28), Cards.dp(host, 10), Cards.dp(host, 28), Cards.dp(host, 6));
        root.addView(box, boxParams);
        root.addView(grid.view(), new LinearLayout.LayoutParams(-1, 0, 1f));
        load();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void refresh() {}

    void query(String text) {
        input.setText(text);
    }

    private void load() {
        Api api = host.api();
        new Thread(() -> {
            try {
                JSONArray s = Data.cached(api, "/soap/?nodesc");
                JSONArray m = Data.cached(api, "/movies/");
                host.runOnUiThread(() -> {
                    series = s;
                    movies = m;
                    search();
                });
            } catch (Exception e) {
                Log.w(TAG, "search data failed: " + e.getMessage());
            }
        }).start();
    }

    private void search() {
        String query = input.getText().toString().trim().toLowerCase(Locale.ROOT);
        grid.clear();
        if (series == null) {
            grid.message("Загрузка каталога…");
            return;
        }
        if (query.length() < 2) {
            grid.message("Введите хотя бы две буквы");
            return;
        }
        List<Cards.Card> found = new ArrayList<>();
        for (JSONObject s : Data.list(series)) {
            if (matches(s, query, "title") && found.size() < LIMIT) found.add(Library.seriesCard(host, s, null));
        }
        List<Cards.Card> films = new ArrayList<>();
        for (JSONObject m : Data.list(movies)) {
            if (matches(m, query, "title_original") && films.size() < LIMIT) films.add(Library.movieCard(host, m));
        }
        if (found.isEmpty() && films.isEmpty()) {
            grid.message("Ничего не найдено");
            return;
        }
        if (!found.isEmpty()) grid.section("Сериалы · " + found.size(), found);
        if (!films.isEmpty()) grid.section("Фильмы · " + films.size(), films);
    }

    private void paintBox(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF1B222C);
        shape.setCornerRadius(Cards.dp(host, 26));
        shape.setStroke(Cards.dp(host, focused ? 2 : 1), focused ? 0xFF8CB4E8 : 0x33FFFFFF);
        box.setBackground(shape);
    }

    private static boolean matches(JSONObject o, String query, String originalKey) {
        return Data.title(o).toLowerCase(Locale.ROOT).contains(query)
            || o.optString("title").toLowerCase(Locale.ROOT).contains(query)
            || o.optString(originalKey).toLowerCase(Locale.ROOT).contains(query);
    }
}
