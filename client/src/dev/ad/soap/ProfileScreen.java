package dev.ad.soap;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

final class ProfileScreen implements Screen {
    private static final String TAG = "SoapTv";

    private final MainActivity host;
    private final LinearLayout root;
    private final LinearLayout panel;
    private final TextView avatar;
    private final TextView name;
    private final TextView premium;

    ProfileScreen(MainActivity host) {
        this.host = host;
        root = new LinearLayout(host);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(0, Cards.dp(host, 32), 0, 0);

        panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Cards.dp(host, 32), Cards.dp(host, 28), Cards.dp(host, 32), Cards.dp(host, 24));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF1B222C);
        shape.setCornerRadius(Cards.dp(host, 18));
        panel.setBackground(shape);

        LinearLayout head = new LinearLayout(host);
        head.setGravity(Gravity.CENTER_VERTICAL);
        avatar = new TextView(host);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextSize(26);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setTextColor(0xFFFFFFFF);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF2B4C7E);
        avatar.setBackground(circle);
        head.addView(avatar, new LinearLayout.LayoutParams(Cards.dp(host, 64), Cards.dp(host, 64)));
        LinearLayout who = new LinearLayout(host);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(Cards.dp(host, 18), 0, 0, 0);
        name = new TextView(host);
        name.setTextSize(24);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(0xFFFFFFFF);
        premium = new TextView(host);
        premium.setTextSize(15);
        premium.setTextColor(0xFFB6BBC4);
        who.addView(name);
        who.addView(premium);
        head.addView(who);
        panel.addView(head);

        Button logout = new Button(host);
        logout.setText("Выйти из аккаунта");
        logout.setTextColor(0xFFFF8A80);
        logout.setOnClickListener(v -> host.logout());
        LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(-1, -2);
        at.topMargin = Cards.dp(host, 24);
        panel.addView(logout, at);
        root.addView(panel, new LinearLayout.LayoutParams(Cards.dp(host, 520), -2));

        String login = host.prefs().getString("login", "");
        showName(login);
        load();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void refresh() {}

    private void showName(String login) {
        String shown = login.isEmpty() ? "Аккаунт soap4" : login;
        name.setText(shown);
        avatar.setText(shown.substring(0, 1).toUpperCase());
    }

    private void load() {
        Api api = host.api();
        new Thread(() -> {
            try {
                JSONObject account = api.object("/auth/check");
                JSONArray mySeries = api.array("/soap/my/?nodesc");
                JSONArray myMovies = api.array("/movies/my");
                int unwatched = 0;
                for (int i = 0; i < mySeries.length(); i++) unwatched += mySeries.getJSONObject(i).optInt("unwatched");
                int fresh = unwatched;
                host.runOnUiThread(() -> fill(account, mySeries.length(), fresh, myMovies.length()));
            } catch (Exception e) {
                Log.w(TAG, "profile failed: " + e.getMessage());
            }
        }).start();
    }

    private void fill(JSONObject account, int series, int unwatched, int movies) {
        showName(account.optString("login"));
        long till = account.optLong("till");
        if (till > 0) {
            long days = (till * 1000 - System.currentTimeMillis()) / 86_400_000L;
            premium.setText("Подписка до " + DateFormat.getDateInstance().format(new Date(till * 1000))
                + (days >= 0 ? " · осталось " + days + " дн." : " · истекла"));
            premium.setTextColor(days >= 0 ? 0xFF7FD18B : 0xFFFF8A80);
        }
        JSONArray family = account.optJSONArray("family");
        String families = "";
        for (int i = 0; family != null && i < family.length(); i++) {
            JSONObject f = family.optJSONObject(i);
            if (f != null) families += (families.isEmpty() ? "" : ", ") + f.optString("name");
        }
        int position = 1;
        position = row(position, "4K UHD", Data.flag(account, "4k") ? "доступно" : "нет");
        if (!families.isEmpty()) position = row(position, "Семья", families);
        position = row(position, "Мои сериалы", series + (unwatched > 0 ? " · новых серий: " + unwatched : ""));
        row(position, "Мои фильмы", String.valueOf(movies));
    }

    private int row(int index, String label, String value) {
        LinearLayout row = new LinearLayout(host);
        row.setPadding(0, Cards.dp(host, 10), 0, Cards.dp(host, 10));
        TextView left = new TextView(host);
        left.setText(label);
        left.setTextSize(16);
        left.setTextColor(0xFF8B919A);
        TextView right = new TextView(host);
        right.setText(value);
        right.setTextSize(16);
        right.setTextColor(0xFFFFFFFF);
        right.setGravity(Gravity.END);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1.4f));
        panel.addView(row, index);
        return index + 1;
    }
}
