package dev.ad.soap;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.SystemClock;
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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "SoapTv";
    private static final int BACKGROUND = 0xFF0F1318;
    private static final String[] TABS = {"Главная", "Сериалы", "Фильмы", "Поиск", "Профиль"};

    private SharedPreferences prefs;
    private Api api;
    private LinearLayout tabBar;
    private TextView gear;
    private FrameLayout content;
    private final TextView[] tabViews = new TextView[TABS.length];
    private final Screen[] screens = new Screen[TABS.length];
    private int tab;
    private volatile int pairing;

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
        FrameLayout top = new FrameLayout(this);
        top.addView(tabBar, new FrameLayout.LayoutParams(-1, -2));
        gear = Cards.button(this, "⚙");
        gear.setTextSize(20);
        gear.setPadding(0, 0, 0, 0);
        gear.setOnClickListener(v -> ((HomeScreen) screens[0]).arrange(gear));
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(Cards.dp(this, 40), Cards.dp(this, 40), Gravity.END | Gravity.CENTER_VERTICAL);
        corner.setMarginEnd(Cards.dp(this, 24));
        top.addView(gear, corner);
        View divider = new View(this);
        divider.setBackgroundColor(0xFF222B36);
        content = new FrameLayout(this);
        shell.addView(top, new LinearLayout.LayoutParams(-1, -2));
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
    protected void onStop() {
        super.onStop();
        pairing++;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (prefs.getString("token", null) == null) showLogin();
        if (screens[tab] != null) screens[tab].refresh();
    }

    @Override
    public void onBackPressed() {
        if (screens[0] instanceof HomeScreen && ((HomeScreen) screens[0]).closeArrange()) return;
        super.onBackPressed();
    }

    SharedPreferences prefs() {
        return prefs;
    }

    Api api() {
        return api;
    }

    void openSoap(int sid, String title) {
        openSoap(sid, title, -1, -1);
    }

    void openSoap(int sid, String title, int season, int episode) {
        startActivity(new Intent(this, SoapActivity.class).putExtra("sid", sid).putExtra("title", title)
            .putExtra("season", season).putExtra("episode", episode));
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
        pairing++;
        api = new Api(prefs.getString("token", ""), prefs.getString("session", ""));
        tabBar.setVisibility(View.VISIBLE);
        Arrays.fill(screens, null);
        showTab(0);
        // Built early so the Profile tab opens with its data already loaded.
        screens[4] = new ProfileScreen(this);
    }

    void reload() {
        // Focus left inside the removed screen would jump to another tab and switch to it mid-removal.
        tabViews[tab].requestFocus();
        Arrays.fill(screens, null);
        showTab(tab);
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
        gear.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
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
        LinearLayout form = loginCard(LinearLayout.HORIZONTAL);
        int size = Cards.dp(this, 200);
        ImageView qr = new ImageView(this);
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(Cards.dp(this, 12));
        qr.setBackground(white);
        form.addView(qr, new LinearLayout.LayoutParams(size, size));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(Cards.dp(this, 32), 0, 0, 0);
        TextView heading = heading("Вход с телефона");
        heading.setPadding(0, 0, 0, Cards.dp(this, 6));
        text.addView(heading);
        TextView hint = new TextView(this);
        hint.setText("Откройте soap4youand.me/tvcode и введите код или наведите камеру на QR");
        hint.setTextSize(15);
        hint.setTextColor(0xFF8B919A);
        text.addView(hint);
        TextView code = new TextView(this);
        code.setTextSize(44);
        code.setTextColor(0xFFFFFFFF);
        code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        code.setLetterSpacing(0.1f);
        code.setText("······");
        text.addView(code);
        TextView status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(0xFF8B919A);
        text.addView(status);
        TextView password = Cards.button(this, "Войти с паролем");
        password.setOnClickListener(v -> showPasswordLogin());
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
        row.setMargins(0, Cards.dp(this, 14), 0, 0);
        text.addView(password, row);
        form.addView(text, new LinearLayout.LayoutParams(Cards.dp(this, 360), -2));
        content.addView(form, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        password.requestFocus();
        pair(pairing, qr, code, status);
    }

    private void pair(int run, ImageView qr, TextView code, TextView status) {
        int size = Cards.dp(this, 200);
        new Thread(() -> {
            while (run == pairing) {
                try {
                    JSONObject pc = Api.pairCode();
                    Bitmap bitmap = qr(pc.optString("qr"), size);
                    String digits = pc.optString("code");
                    runOnUiThread(() -> {
                        qr.setImageBitmap(bitmap);
                        code.setText(digits);
                        status.setText("");
                    });
                    long end = SystemClock.elapsedRealtime() + pc.optInt("ttl", 600) * 1000L;
                    while (run == pairing && SystemClock.elapsedRealtime() < end) {
                        Thread.sleep(3000);
                        String[] auth;
                        try {
                            auth = Api.pairCheck(pc.optString("sid"));
                        } catch (IOException e) {
                            String state = String.valueOf(e.getMessage());
                            if (state.equals("expired") || state.equals("cancelled")) break;
                            Log.w(TAG, "pair check failed: " + state);
                            continue;
                        }
                        if (auth != null) {
                            runOnUiThread(() -> {
                                if (run == pairing) signedIn(auth, auth[3]);
                            });
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "pair failed: " + e.getMessage());
                    runOnUiThread(() -> status.setText("Не удалось получить код, пробуем ещё раз"));
                    // The server limits how often codes are issued, so a retry waits.
                    SystemClock.sleep(30_000);
                }
            }
        }).start();
    }

    private static Bitmap qr(String text, int size) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
            Collections.singletonMap(EncodeHintType.MARGIN, 2));
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.TRANSPARENT;
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    private LinearLayout loginCard(int orientation) {
        pairing++;
        tabBar.setVisibility(View.GONE);
        gear.setVisibility(View.GONE);
        content.removeAllViews();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(orientation);
        form.setGravity(orientation == LinearLayout.VERTICAL ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        form.setPadding(Cards.dp(this, 36), Cards.dp(this, 28), Cards.dp(this, 36), Cards.dp(this, 24));
        GradientDrawable card = new GradientDrawable();
        card.setColor(0xFF161C24);
        card.setCornerRadius(Cards.dp(this, 20));
        card.setStroke(Cards.dp(this, 1), 0x22FFFFFF);
        form.setBackground(card);
        return form;
    }

    private TextView heading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(22);
        heading.setTextColor(0xFFFFFFFF);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        return heading;
    }

    private void showPasswordLogin() {
        LinearLayout form = loginCard(LinearLayout.VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageDrawable(getApplicationInfo().loadBanner(getPackageManager()));
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable round = new GradientDrawable();
        round.setCornerRadius(Cards.dp(this, 14));
        logo.setBackground(round);
        logo.setClipToOutline(true);
        form.addView(logo, new LinearLayout.LayoutParams(Cards.dp(this, 192), Cards.dp(this, 108)));
        TextView heading = heading("Вход в аккаунт");
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
        TextView byCode = Cards.button(this, "Войти по коду");
        byCode.setOnClickListener(v -> showLogin());
        form.addView(byCode, row);
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
                runOnUiThread(() -> signedIn(auth, login));
            } catch (Exception e) {
                Log.w(TAG, "login failed: " + e.getMessage());
                runOnUiThread(() -> {
                    status.setTextColor(0xFFFF8A80);
                    status.setText("Не удалось войти: " + e.getMessage());
                });
            }
        }).start();
    }

    private void signedIn(String[] auth, String login) {
        long till = 0;
        try {
            till = Math.round(Double.parseDouble(auth[2]));
        } catch (NumberFormatException ignored) {
        }
        prefs.edit().putString("token", auth[0]).putString("session", auth[1])
            .putString("login", login).putLong("till", till).apply();
        start();
    }
}
