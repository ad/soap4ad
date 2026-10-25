package dev.ad.soap;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "SoapTv";
    private static final int BACKGROUND = 0xFF0F1318;
    private static final String[] TABS = {"Главная", "Сериалы", "Фильмы", "Поиск", "Профиль"};

    private SharedPreferences prefs;
    private Api api;
    private LinearLayout tabBar;
    private FrameLayout content;
    private final TextView[] tabViews = new TextView[TABS.length];
    private final Screen[] screens = new Screen[TABS.length];
    private int tab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("auth", MODE_PRIVATE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BACKGROUND);
        tabBar = new LinearLayout(this);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setBackgroundColor(0xFF141A22);
        tabBar.setPadding(0, Cards.dp(this, 8), 0, Cards.dp(this, 8));
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            TextView view = new TextView(this);
            view.setText(TABS[i]);
            view.setTextSize(16);
            view.setGravity(Gravity.CENTER);
            view.setFocusable(true);
            view.setPadding(Cards.dp(this, 30), Cards.dp(this, 8), Cards.dp(this, 30), Cards.dp(this, 8));
            view.setOnClickListener(v -> showTab(index));
            view.setOnFocusChangeListener((v, focused) -> {
                if (focused && tab != index && tabBar.getVisibility() == View.VISIBLE) showTab(index);
                paintTabs();
            });
            LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-2, -2);
            margin.setMargins(Cards.dp(this, 6), 0, Cards.dp(this, 6), 0);
            tabBar.addView(view, margin);
            tabViews[i] = view;
        }
        View divider = new View(this);
        divider.setBackgroundColor(0xFF222B36);
        content = new FrameLayout(this);
        shell.addView(tabBar, new LinearLayout.LayoutParams(-1, -2));
        shell.addView(divider, new LinearLayout.LayoutParams(-1, Cards.dp(this, 1)));
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(shell);
        if (prefs.getString("token", null) == null) {
            showLogin();
            return;
        }
        start();
        String title = getIntent().getStringExtra("title");
        if (title != null) find(title, getIntent().getIntExtra("year", 0), getIntent().getStringExtra("type"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Update.check(this);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (screens[tab] != null) screens[tab].refresh();
    }

    SharedPreferences prefs() {
        return prefs;
    }

    Api api() {
        return api;
    }

    void openSoap(int sid, String title) {
        startActivity(new Intent(this, SoapActivity.class).putExtra("sid", sid).putExtra("title", title));
    }

    void openMovie(int id, String title) {
        startActivity(new Intent(this, MovieActivity.class).putExtra("id", id).putExtra("title", title));
    }

    void logout() {
        Api old = api;
        new Thread(() -> {
            try {
                if (old != null) old.logout();
            } catch (Exception e) {
                Log.w(TAG, "logout failed: " + e.getMessage());
            }
        }).start();
        prefs.edit().clear().apply();
        Data.clear();
        Arrays.fill(screens, null);
        showLogin();
    }

    private void start() {
        api = new Api(prefs.getString("token", ""), prefs.getString("session", ""));
        tabBar.setVisibility(View.VISIBLE);
        Arrays.fill(screens, null);
        showTab(0);
    }

    private void find(String title, int year, String type) {
        showTab(3);
        ((SearchScreen) screens[3]).query(title);
        tabViews[3].requestFocus();
        Api current = api;
        new Thread(() -> {
            List<Runnable> found = new ArrayList<>();
            try {
                if (!"movie".equals(type)) {
                    for (JSONObject s : Data.list(Data.cached(current, "/soap/?nodesc"))) {
                        if (!Data.sameTitle(s, title, year, "title")) continue;
                        int sid = s.optInt("sid");
                        String name = Data.title(s);
                        found.add(() -> openSoap(sid, name));
                    }
                }
                if (!"tv".equals(type)) {
                    for (JSONObject m : Data.list(Data.cached(current, "/movies/"))) {
                        if (!Data.sameTitle(m, title, year, "title_original")) continue;
                        int id = m.optInt("id");
                        String name = Data.title(m);
                        found.add(() -> openMovie(id, name));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "title lookup failed: " + e.getMessage());
            }
            if (found.size() == 1) runOnUiThread(() -> {
                if (!isDestroyed()) found.get(0).run();
            });
        }).start();
    }

    private void showTab(int index) {
        tab = index;
        if (screens[index] == null) {
            switch (index) {
                case 0:
                    screens[index] = new HomeScreen(this);
                    break;
                case 1:
                    screens[index] = new GridScreen(this, false);
                    break;
                case 2:
                    screens[index] = new GridScreen(this, true);
                    break;
                case 3:
                    screens[index] = new SearchScreen(this);
                    break;
                default:
                    screens[index] = new ProfileScreen(this);
            }
        }
        content.removeAllViews();
        content.addView(screens[index].view());
        paintTabs();
    }

    private void paintTabs() {
        for (int i = 0; i < tabViews.length; i++) {
            TextView view = tabViews[i];
            boolean selected = i == tab;
            boolean focused = view.hasFocus();
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Cards.dp(this, 22));
            shape.setColor(selected ? 0xFF2B4C7E : focused ? 0x33FFFFFF : Color.TRANSPARENT);
            if (focused) shape.setStroke(Cards.dp(this, 2), 0xFF8CB4E8);
            view.setBackground(shape);
            view.setTextColor(selected ? 0xFFFFFFFF : 0xFFB0B7C3);
            view.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void showLogin() {
        tabBar.setVisibility(View.GONE);
        content.removeAllViews();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setGravity(Gravity.CENTER_HORIZONTAL);
        form.setPadding(Cards.dp(this, 36), Cards.dp(this, 28), Cards.dp(this, 36), Cards.dp(this, 24));
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xFF161C24);
        card.setCornerRadius(Cards.dp(this, 20));
        card.setStroke(Cards.dp(this, 1), 0x22FFFFFF);
        form.setBackground(card);

        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadBanner(getPackageManager()));
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable round = new GradientDrawable();
        round.setCornerRadius(Cards.dp(this, 14));
        logo.setBackground(round);
        logo.setClipToOutline(true);
        form.addView(logo, new LinearLayout.LayoutParams(Cards.dp(this, 192), Cards.dp(this, 108)));
        TextView heading = new TextView(this);
        heading.setText("Вход в аккаунт");
        heading.setTextSize(22);
        heading.setTextColor(0xFFFFFFFF);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, Cards.dp(this, 16), 0, Cards.dp(this, 2));
        form.addView(heading);
        TextView hint = new TextView(this);
        hint.setText("Логин и пароль от soap4youand.me");
        hint.setTextSize(14);
        hint.setTextColor(0xFF8B919A);
        form.addView(hint);

        EditText login = field("Логин");
        EditText password = field("Пароль");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xFFFF8A80);
        status.setGravity(Gravity.CENTER);
        TextView enter = Cards.button(this, "Войти");
        enter.setOnClickListener(v -> signIn(login.getText().toString(), password.getText().toString(), status));
        password.setImeOptions(EditorInfo.IME_ACTION_DONE);
        password.setOnEditorActionListener((v, action, event) -> enter.performClick());
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
        row.setMargins(0, Cards.dp(this, 14), 0, 0);
        form.addView(login, row);
        form.addView(password, row);
        form.addView(enter, row);
        form.addView(status, row);
        content.addView(form, new FrameLayout.LayoutParams(Cards.dp(this, 420), -2, Gravity.CENTER));
        login.requestFocus();
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine();
        field.setTextSize(17);
        field.setTextColor(0xFFFFFFFF);
        field.setHintTextColor(0xFF6E7681);
        field.setPadding(Cards.dp(this, 18), Cards.dp(this, 11), Cards.dp(this, 18), Cards.dp(this, 11));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused}, fieldShape(2, 0xFF8CB4E8));
        background.addState(new int[] {}, fieldShape(1, 0x33FFFFFF));
        field.setBackground(background);
        return field;
    }

    private GradientDrawable fieldShape(int stroke, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF1B222C);
        shape.setCornerRadius(Cards.dp(this, 12));
        shape.setStroke(Cards.dp(this, stroke), color);
        return shape;
    }

    private void signIn(String login, String password, TextView status) {
        status.setTextColor(0xFF8B919A);
        status.setText("Вход…");
        new Thread(() -> {
            try {
                String[] auth = Api.login(login, password);
                long till = 0;
                try {
                    till = Math.round(Double.parseDouble(auth[2]));
                } catch (NumberFormatException ignored) {
                }
                prefs.edit().putString("token", auth[0]).putString("session", auth[1])
                    .putString("login", login).putLong("till", till).apply();
                runOnUiThread(this::start);
            } catch (Exception e) {
                Log.w(TAG, "login failed: " + e.getMessage());
                runOnUiThread(() -> {
                    status.setTextColor(0xFFFF8A80);
                    status.setText("Не удалось войти: " + e.getMessage());
                });
            }
        }).start();
    }
}
