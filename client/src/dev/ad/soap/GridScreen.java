package dev.ad.soap;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class GridScreen implements Screen {
    private static final String TAG = "SoapTv";
    private static final String[] SERIES_FILTERS = {"Все", "Только с новыми эпизодами", "Идущие", "Завершённые", "Популярные"};
    private static final String[] MOVIE_SORTS = {"По названию", "По рейтингу", "По году", "Новые"};
    private static final Map<String, String> GENRES_RU = Map.ofEntries(
        Map.entry("Action", "Боевик"), Map.entry("Adventure", "Приключения"), Map.entry("Animation", "Мультфильм"),
        Map.entry("Biography", "Биография"), Map.entry("Comedy", "Комедия"), Map.entry("Crime", "Криминал"),
        Map.entry("Documentary", "Документальный"), Map.entry("Drama", "Драма"), Map.entry("Family", "Семейный"),
        Map.entry("Fantasy", "Фэнтези"), Map.entry("History", "История"), Map.entry("Horror", "Ужасы"),
        Map.entry("Music", "Музыка"), Map.entry("Musical", "Мюзикл"), Map.entry("Mystery", "Детектив"),
        Map.entry("Romance", "Мелодрама"), Map.entry("Sci-Fi", "Фантастика"), Map.entry("Sport", "Спорт"),
        Map.entry("Thriller", "Триллер"), Map.entry("War", "Военный"), Map.entry("Western", "Вестерн"),
        Map.entry("Superhero", "Супергерои"), Map.entry("Dark Comedy", "Чёрная комедия"));

    private final MainActivity host;
    private final boolean movies;
    private final LinearLayout root;
    private final List<TextView> modeChips = new ArrayList<>();
    private TextView watchedChip;
    private TextView genreChip;
    private TextView yearChip;
    private String genre;
    private int yearFrom;
    private int yearTo;
    private final CardGrid grid;
    private int mode;
    private int option;
    private int watched;
    private int generation;
    private int applyGeneration;
    private JSONArray all;
    private JSONArray mine;
    private JSONArray popular;

    GridScreen(MainActivity host, boolean movies) {
        this.host = host;
        this.movies = movies;
        root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        grid = new CardGrid(host);

        HorizontalScrollView chipScroll = new HorizontalScrollView(host);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipChildren(false);
        LinearLayout chips = new LinearLayout(host);
        chips.setPadding(Cards.dp(host, 20), Cards.dp(host, 8), Cards.dp(host, 20), Cards.dp(host, 8));
        String[] modes = {"Все", "Мои", "4K UHD"};
        SharedPreferences prefs = host.prefs();
        mode = prefs.getInt(key("mode"), 0);
        watched = prefs.getInt(key("watched"), 0);
        genre = prefs.getString(key("genre"), "");
        yearFrom = prefs.getInt(key("year_from"), 0);
        yearTo = prefs.getInt(key("year_to"), 0);
        // Mode 3 was the old "Просмотренные" chip before it became a separate three-state filter.
        if (mode == 3) {
            mode = 0;
            watched = 1;
        }
        mode = Math.min(mode, modes.length - 1);
        option = Math.min(prefs.getInt(key("option"), 0), (movies ? MOVIE_SORTS : SERIES_FILTERS).length - 1);
        for (int i = 0; i < modes.length; i++) {
            int index = i;
            TextView chip = chip(modes[i], () -> {
                mode = index;
                host.prefs().edit().putInt(key("mode"), mode).apply();
                paint();
                apply();
            });
            modeChips.add(chip);
            chips.addView(chip);
        }
        if (movies) {
            watchedChip = chip("Просмотренные", () -> {
                watched = (watched + 1) % 3;
                host.prefs().edit().putInt(key("watched"), watched).putInt(key("mode"), mode).apply();
                paint();
                apply();
            });
            chips.addView(watchedChip);
            genreChip = chip("Жанр", this::chooseGenre);
            yearChip = chip("Год", this::chooseYear);
            chips.addView(genreChip);
            chips.addView(yearChip);
        }
        chips.addView(chip(movies ? "Сортировка" : "Фильтр", this::choose));
        chipScroll.addView(chips);
        root.addView(chipScroll, new LinearLayout.LayoutParams(-1, -2));
        root.addView(grid.view(), new LinearLayout.LayoutParams(-1, 0, 1f));
        paint();
        load();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void refresh() {
        Data.clear();
        load();
    }

    private void load() {
        Api api = host.api();
        int mineGeneration = ++generation;
        new Thread(() -> {
            try {
                JSONArray a = Data.cached(api, movies ? "/movies/" : "/soap/?nodesc");
                JSONArray m = api.array(movies ? "/movies/my" : "/soap/my/?nodesc");
                JSONArray p = movies ? new JSONArray() : Data.cached(api, "/soap/top/all");
                host.runOnUiThread(() -> {
                    if (mineGeneration != generation) return;
                    all = a;
                    mine = m;
                    popular = p;
                    apply();
                });
            } catch (Exception e) {
                Log.w(TAG, "grid failed: " + e.getMessage());
                if (String.valueOf(e.getMessage()).contains("401")) host.runOnUiThread(host::logout);
            }
        }).start();
    }

    private void apply() {
        if (all == null) return;
        int mineGeneration = ++applyGeneration;
        int mode = this.mode;
        int option = this.option;
        int watched = this.watched;
        String genre = this.genre;
        int yearFrom = this.yearFrom;
        int yearTo = this.yearTo;
        new Thread(() -> {
            List<Cards.Card> cards = build(mode, option, watched, genre, yearFrom, yearTo);
            host.runOnUiThread(() -> {
                if (mineGeneration == applyGeneration) grid.show(cards);
            });
        }).start();
    }

    private List<Cards.Card> build(int mode, int option, int watched, String genre, int yearFrom, int yearTo) {
        List<JSONObject> items;
        if (!movies && option == 4) {
            items = Data.list(popular);
        } else {
            items = Data.list(mode == 1 ? mine : all);
            if (mode == 2) items = keep(items, o -> Data.flag(o, "4k"));
            if (movies && watched > 0) items = keep(items, o -> Data.flag(o, "watched") == (watched == 1));
            if (movies && !genre.isEmpty()) items = keep(items, o -> genres(o).contains(genre));
            if (movies && (yearFrom > 0 || yearTo > 0)) {
                items = keep(items, o -> o.optInt("year") >= yearFrom && (yearTo == 0 || o.optInt("year") <= yearTo));
            }
            if (!movies && option == 1) items = keep(items, o -> o.optInt("unwatched") > 0);
            if (!movies && option == 2) items = keep(items, o -> o.optInt("status") == 0);
            if (!movies && option == 3) items = keep(items, o -> o.optInt("status") != 0);
            items.sort(!movies || option == 0 ? Data.byTitle()
                : option == 1 ? Data.byNumberDesc("imdb_rating")
                : option == 2 ? Data.byNumberDesc("year") : Data.byNumberDesc("id"));
        }
        List<Cards.Card> cards = new ArrayList<>();
        for (JSONObject o : items) {
            if (movies) cards.add(Library.movieCard(host, o));
            else cards.add(Library.seriesCard(host, o, mode == 1 && o.optInt("unwatched") > 0 ? "+" + o.optInt("unwatched") : null));
        }
        return cards;
    }

    private static List<JSONObject> keep(List<JSONObject> items, Predicate<JSONObject> test) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject o : items) if (test.test(o)) out.add(o);
        return out;
    }

    private static List<String> genres(JSONObject o) {
        List<String> names = new ArrayList<>();
        JSONArray interests = o.optJSONArray("interests");
        for (int i = 0; interests != null && i < interests.length(); i++) {
            JSONObject interest = interests.optJSONObject(i);
            if (interest != null) names.add(interest.optString("name"));
        }
        return names;
    }

    private static String genreLabel(String name) {
        return GENRES_RU.getOrDefault(name, name);
    }

    private void chooseGenre() {
        if (all == null) return;
        Map<String, Integer> counts = new HashMap<>();
        for (JSONObject o : Data.list(all)) for (String name : genres(o)) counts.merge(name, 1, Integer::sum);
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() >= 10) names.add(e.getKey());
        names.sort((a, b) -> counts.get(b) - counts.get(a));
        names.add(0, "");
        String[] labels = new String[names.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = i == 0 ? "Все жанры" : genreLabel(names.get(i)) + " · " + counts.get(names.get(i));
        }
        new AlertDialog.Builder(host, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Жанр")
            .setSingleChoiceItems(labels, Math.max(0, names.indexOf(genre)), (dialog, which) -> {
                genre = names.get(which);
                host.prefs().edit().putString(key("genre"), genre).apply();
                dialog.dismiss();
                paint();
                apply();
            })
            .show();
    }

    private void chooseYear() {
        if (all == null) return;
        int top = 0;
        for (JSONObject o : Data.list(all)) top = Math.max(top, o.optInt("year"));
        List<String> labels = new ArrayList<>(List.of("Любой год"));
        List<int[]> ranges = new ArrayList<>(List.of(new int[] {0, 0}));
        for (int y = top; y > top - 10; y--) {
            labels.add(String.valueOf(y));
            ranges.add(new int[] {y, y});
        }
        for (int decade = (top - 10) / 10 * 10; decade >= 1990; decade -= 10) {
            labels.add(decade + "-е");
            ranges.add(new int[] {decade, decade + 9});
        }
        labels.add("1980-е и раньше");
        ranges.add(new int[] {0, 1989});
        int checked = 0;
        for (int i = 0; i < ranges.size(); i++) if (ranges.get(i)[0] == yearFrom && ranges.get(i)[1] == yearTo) checked = i;
        new AlertDialog.Builder(host, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Год выпуска")
            .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, which) -> {
                yearFrom = ranges.get(which)[0];
                yearTo = ranges.get(which)[1];
                host.prefs().edit().putInt(key("year_from"), yearFrom).putInt(key("year_to"), yearTo).apply();
                dialog.dismiss();
                paint();
                apply();
            })
            .show();
    }

    private void choose() {
        String[] labels = movies ? MOVIE_SORTS : SERIES_FILTERS;
        new AlertDialog.Builder(host, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(movies ? "Сортировка" : "Фильтр сериалов")
            .setSingleChoiceItems(labels, option, (dialog, which) -> {
                option = which;
                host.prefs().edit().putInt(key("option"), option).apply();
                dialog.dismiss();
                apply();
            })
            .show();
    }

    private String key(String name) {
        return (movies ? "movies_" : "series_") + name;
    }

    private void paint() {
        for (int i = 0; i < modeChips.size(); i++) {
            TextView chip = modeChips.get(i);
            chip.setTypeface(i == mode ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            chip.setTextColor(i == mode ? 0xFFFFFFFF : 0xFFB6BBC4);
        }
        if (watchedChip == null) return;
        watchedChip.setText(watched == 2 ? "Не просмотренные" : "Просмотренные");
        paintOn(watchedChip, watched > 0);
        genreChip.setText(genre.isEmpty() ? "Жанр" : "Жанр: " + genreLabel(genre));
        paintOn(genreChip, !genre.isEmpty());
        yearChip.setText(yearFrom == 0 && yearTo == 0 ? "Год" : "Год: " + yearLabel());
        paintOn(yearChip, yearFrom > 0 || yearTo > 0);
    }

    private String yearLabel() {
        if (yearFrom == yearTo) return String.valueOf(yearFrom);
        return yearFrom == 0 ? "до " + (yearTo + 1) : yearFrom + "-е";
    }

    private static void paintOn(TextView chip, boolean on) {
        chip.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setTextColor(on ? 0xFFFFFFFF : 0xFFB6BBC4);
    }

    private TextView chip(String text, Runnable onClick) {
        TextView chip = new TextView(host);
        chip.setText(text);
        chip.setTextSize(14);
        chip.setTextColor(0xFFB6BBC4);
        chip.setFocusable(true);
        chip.setPadding(Cards.dp(host, 16), Cards.dp(host, 7), Cards.dp(host, 16), Cards.dp(host, 7));
        chip.setBackground(outline(0x55FFFFFF));
        chip.setOnFocusChangeListener((v, focused) -> v.setBackground(outline(focused ? 0xFF5B9BE6 : 0x55FFFFFF)));
        chip.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-2, -2);
        margin.setMarginEnd(Cards.dp(host, 10));
        chip.setLayoutParams(margin);
        return chip;
    }

    private GradientDrawable outline(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Cards.dp(host, 8));
        shape.setStroke(Cards.dp(host, 1), color);
        return shape;
    }
}
