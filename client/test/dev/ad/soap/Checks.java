package dev.ad.soap;

public class Checks {
    public static void main(String[] args) {
        check(Data.norm("Ёлки 2: Новый год!").equals(Data.norm("елки 2 новый год")), "norm folds case, ё and punctuation");
        check(!Data.norm("11.22.63").equals(Data.norm("11.22.64")), "norm keeps digits");
        check(Playback.translateName(4).equals("Перевод") && Playback.translateName(9).equals("Вариант 9"), "translate names");
        check(Playback.score(4, 1, 4, 0) > Playback.score(3, 3, 4, 0), "wanted translate beats quality");
        check(Playback.score(4, 3, 4, 0) > Playback.score(4, 4, 4, 0), "UltraHD only when picked");
        check(Playback.score(4, 4, 4, 4) > Playback.score(4, 3, 4, 4), "picked UltraHD wins");
        check(Playback.score(4, 1, 4, 1) > Playback.score(4, 2, 4, 1), "picked SD wins");
        check(Playback.score(4, 3, 4, 1) > Playback.score(4, 4, 4, 1), "nearest above the picked quality");
        check(Playback.qualityName(3).equals("FullHD") && Playback.qualityName(0).equals("—"), "quality names");
        System.out.println("client checks ok");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
