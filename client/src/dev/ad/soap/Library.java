package dev.ad.soap;

import org.json.JSONObject;

final class Library {
    private Library() {}

    static Cards.Card seriesCard(MainActivity host, JSONObject s, String corner) {
        int sid = s.optInt("sid");
        String name = Data.title(s);
        return new Cards.Card(Data.cover(s, sid), name, Data.rating(s), corner, null, () -> host.openSoap(sid, name), null);
    }

    static Cards.Card movieCard(MainActivity host, JSONObject m) {
        int id = m.optInt("id");
        String name = Data.title(m);
        int year = m.optInt("year");
        return new Cards.Card(Data.cover(m, 0), name, Data.rating(m), Data.flag(m, "4k") ? "4K" : null,
            year > 0 ? String.valueOf(year) : null, () -> host.openMovie(id, name), null);
    }
}
