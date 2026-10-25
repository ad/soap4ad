package dev.ad.soap;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Data {
    private static final long TTL_MS = 10 * 60_000L;
    private static final Map<String, JSONArray> CACHE = new HashMap<>();
    private static final Map<String, Long> CACHED_AT = new HashMap<>();

    private Data() {}

    static JSONArray cached(Api api, String path) throws Exception {
        synchronized (CACHE) {
            Long at = CACHED_AT.get(path);
            if (at != null && System.currentTimeMillis() - at < TTL_MS) return CACHE.get(path);
        }
        JSONArray array = api.array(path);
        synchronized (CACHE) {
            CACHE.put(path, array);
            CACHED_AT.put(path, System.currentTimeMillis());
        }
        return array;
    }

    static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
            CACHED_AT.clear();
        }
    }

    static List<JSONObject> list(JSONArray array) {
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o != null) items.add(o);
        }
        return items;
    }

    static List<JSONObject> sorted(JSONArray array, String key) {
        List<JSONObject> items = list(array);
        items.sort(Comparator.comparingLong((JSONObject o) -> o.optLong(key)).reversed());
        return items;
    }

    static Comparator<JSONObject> byTitle() {
        Collator collator = Collator.getInstance(new Locale("ru"));
        return (a, b) -> collator.compare(title(a), title(b));
    }

    static Comparator<JSONObject> byNumberDesc(String key) {
        return Comparator.comparingDouble((JSONObject o) -> o.optDouble(key, 0)).reversed();
    }

    static boolean flag(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return v != null && ("1".equals(v.toString()) || "true".equals(v.toString()));
    }

    static String title(JSONObject o) {
        String cached = o.optString("_t");
        if (!cached.isEmpty()) return cached;
        String ru = o.optString("title_ru");
        String title = Api.text(TextUtils.isEmpty(ru) ? o.optString("title") : ru);
        try {
            o.put("_t", title);
        } catch (org.json.JSONException ignored) {
        }
        return title;
    }

    static String episodeTitle(JSONObject e) {
        String ru = e.optString("title_ru");
        return Api.text(ru.isEmpty() ? e.optString("title_en") : ru);
    }

    static boolean sameTitle(JSONObject o, String title, int year, String originalKey) {
        int own = o.optInt("year");
        if (year > 0 && own > 0 && own != year) return false;
        String wanted = norm(title);
        return wanted.equals(norm(title(o))) || wanted.equals(norm(o.optString("title"))) || wanted.equals(norm(o.optString(originalKey)));
    }

    static String norm(String title) {
        return title.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static String cover(JSONObject o, int soapId) {
        JSONObject covers = o.optJSONObject("covers");
        String url = covers == null ? "" : covers.optString("big", covers.optString("small"));
        return url.isEmpty() && soapId > 0 ? Api.cover(soapId) : url.replaceFirst("^http://", "https://");
    }

    static String rating(JSONObject o) {
        for (String key : new String[] {"imdb_rating", "kinopoisk_rating"}) {
            double value = o.optDouble(key, 0);
            if (value > 0) return String.format(Locale.US, "%.1f", value);
        }
        return null;
    }
}
