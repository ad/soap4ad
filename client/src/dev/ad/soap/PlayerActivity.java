package dev.ad.soap;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class PlayerActivity extends Activity {
    private static final String TAG = "SoapTv";
    private static final int STEP_MS = 10_000;
    private static final int SAVE_EVERY_MS = 15_000;
    private static final int NEXT_DELAY_S = 5;
    private static final double WATCHED_FRACTION = 0.9;
    private static final int[] WIDTHS = {0, 720, 1280, 1920, 3840};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private SurfaceView surface;
    private TextView overlay;
    private LinearLayout panel;
    private TextView panelLabel;
    private TextView panelTime;
    private TextView panelDuration;
    private View fill;
    private View rest;
    private long seekTotal;
    private long seekAt;
    private Api api;
    private int eid;
    private int sid;
    private final List<JSONObject> episodes = new ArrayList<>();
    private JSONObject episode;
    private int countdown;
    private JSONObject upcoming;
    private boolean pressed;
    private boolean held;
    private int previousQuality = -1;
    private boolean watched;

    private final Runnable hideOverlay = () -> overlay.setVisibility(TextView.GONE);
    private final Runnable hidePanel = () -> panel.setVisibility(View.GONE);
    private final Runnable longPress = () -> {
        held = true;
        showInfo();
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (countdown <= 0) {
                startEpisode(upcoming);
                return;
            }
            overlay.setText("Серия " + upcoming.optInt("episode") + " через " + countdown-- + " с   OK — сейчас, Назад — выход");
            overlay.setVisibility(TextView.VISIBLE);
            handler.postDelayed(this, 1000);
        }
    };
    private final Runnable save = new Runnable() {
        @Override
        public void run() {
            saveTime();
            handler.postDelayed(this, SAVE_EVERY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.content.SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        api = new Api(prefs.getString("token", ""), prefs.getString("session", ""));
        eid = getIntent().getIntExtra("eid", 0);
        sid = getIntent().getIntExtra("sid", 0);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        surface = new SurfaceView(this);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> handler.post(this::fit));
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        overlay = new TextView(this);
        overlay.setTextSize(20);
        overlay.setTextColor(0xFFFFFFFF);
        overlay.setBackground(rounded(0xCC1B222C, 14));
        overlay.setPadding(dp(20), dp(10), dp(20), dp(10));
        overlay.setVisibility(TextView.GONE);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        corner.setMargins(0, dp(28), dp(36), 0);
        root.addView(overlay, corner);
        root.addView(buildPanel(), new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        player.setVideoSurfaceView(surface);
        TrackSelectionParameters.Builder params = sized(player.getTrackSelectionParameters().buildUpon(), prefs.getInt("quality", 0));
        String language = prefs.getString("audio_language", null);
        if (language != null) params.setPreferredAudioLanguage(language);
        player.setTrackSelectionParameters(params.build());
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException e) {
                Log.w(TAG, "player error " + e.getErrorCodeName() + ": " + e.getMessage());
                undoQuality();
                Toast.makeText(PlayerActivity.this, "Не удалось воспроизвести (" + e.getErrorCodeName() + ")", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onVideoSizeChanged(VideoSize size) {
                fit();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                Log.i(TAG, "player state " + state);
                if (state == Player.STATE_READY) previousQuality = -1;
                if (state == Player.STATE_ENDED) ended();
            }
        });
        play(getIntent().getStringExtra("url"), getIntent().getIntExtra("start", 0));
        if (sid > 0) loadEpisodes();
    }

    private void play(String url, long startMs) {
        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));
        // Movie streams are HLS under /hls/ with no .m3u8 suffix, so ExoPlayer cannot infer the type.
        if (url.contains("/hls/")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build(), startMs);
        player.prepare();
        player.play();
    }

    private void fit() {
        VideoSize video = player.getVideoSize();
        View root = (View) surface.getParent();
        if (video.width == 0 || video.height == 0 || root.getWidth() == 0) return;
        float ratio = video.width * video.pixelWidthHeightRatio / video.height;
        int width = root.getWidth();
        int height = root.getHeight();
        if (width > height * ratio) width = Math.round(height * ratio);
        else height = Math.round(width / ratio);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (params.width == width && params.height == height) return;
        params.width = width;
        params.height = height;
        surface.setLayoutParams(params);
    }

    private void loadEpisodes() {
        new Thread(() -> {
            try {
                JSONArray array = api.object("/episodes/" + sid + "?nodesc").getJSONArray("episodes");
                runOnUiThread(() -> {
                    for (JSONObject e : Data.list(array)) {
                        episodes.add(e);
                        if (fileOf(e, eid) != null) episode = e;
                    }
                    episodes.sort(Comparator.comparingInt((JSONObject e) -> e.optInt("season")).thenComparingInt(e -> e.optInt("episode")));
                });
            } catch (Exception e) {
                Log.w(TAG, "player episodes failed: " + e.getMessage());
            }
        }).start();
    }

    private static JSONObject fileOf(JSONObject episode, int eid) {
        JSONArray files = episode.optJSONArray("files");
        for (JSONObject f : files == null ? List.<JSONObject>of() : Data.list(files)) if (f.optInt("eid") == eid) return f;
        return null;
    }

    private void ended() {
        if (episode == null) {
            finish();
            return;
        }
        markWatched();
        int index = episodes.indexOf(episode);
        if (index + 1 >= episodes.size()) {
            finish();
            return;
        }
        upcoming = episodes.get(index + 1);
        countdown = NEXT_DELAY_S;
        handler.removeCallbacks(hideOverlay);
        handler.post(tick);
    }

    private void markWatched() {
        if (watched || episode == null) return;
        JSONObject marked = episode;
        int s = episode.optInt("season");
        int number = episode.optInt("episode");
        Actions.post(this, api, () -> watched |= episode == marked, "/episodes/watch/" + sid + "/" + s + "/" + number + "/",
            "sid=" + sid + "&season=" + s + "&episode=" + number);
    }

    private void startEpisode(JSONObject next) {
        handler.removeCallbacks(tick);
        upcoming = null;
        JSONObject file = Playback.pickFile(this, next.optJSONArray("files"), 0);
        if (file == null) {
            finish();
            return;
        }
        switchTo(next, file, -1);
    }

    // A negative position resumes where the server says this file was left.
    private void switchTo(JSONObject next, JSONObject file, long positionMs) {
        saveTime();
        new Thread(() -> {
            try {
                JSONObject response = Playback.stream(api, sid, file);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    if (next != episode) watched = false;
                    episode = next;
                    eid = file.optInt("eid");
                    // Mac.apk keeps showing the old video sink after a new stream, so the surface is recreated.
                    surface.setVisibility(View.GONE);
                    surface.post(() -> surface.setVisibility(View.VISIBLE));
                    play(response.optString("stream"), positionMs >= 0 ? positionMs : Playback.startMs(response));
                    show("Сезон " + next.optInt("season") + ", серия " + next.optInt("episode") + "   " + Playback.translateName(file.optInt("translate")));
                });
            } catch (Exception e) {
                Log.w(TAG, "switch failed: " + e.getMessage());
                runOnUiThread(this::undoQuality);
                runOnUiThread(() -> Toast.makeText(this, "Не удалось запустить: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private View buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(48), dp(48), dp(48), dp(28));
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xE6000000, 0x00000000}));
        panel.setVisibility(View.GONE);
        LinearLayout top = new LinearLayout(this);
        panelLabel = text(26, 0xFFFFFFFF);
        panelLabel.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(panelLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView hint = text(14, 0xFF9AA0A6);
        hint.setText("↑ ↓  озвучка     удерживайте OK — меню");
        top.addView(hint);
        panel.addView(top);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        panelTime = text(16, 0xFFFFFFFF);
        panelDuration = text(16, 0xFF9AA0A6);
        LinearLayout track = new LinearLayout(this);
        track.setBackground(rounded(0x40FFFFFF, 3));
        fill = new View(this);
        fill.setBackground(rounded(0xFF8CB4E8, 3));
        rest = new View(this);
        track.addView(fill, new LinearLayout.LayoutParams(0, -1, 0f));
        track.addView(rest, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(0, dp(6), 1f);
        trackParams.setMargins(dp(16), 0, dp(16), 0);
        bar.addView(panelTime);
        bar.addView(track, trackParams);
        bar.addView(panelDuration);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, -2);
        barParams.setMargins(0, dp(10), 0, 0);
        panel.addView(bar, barParams);
        return panel;
    }

    private void showPanel(String label, long position) {
        long duration = Math.max(player.getDuration(), 0);
        panelLabel.setText(label);
        panelTime.setText(format(position));
        panelDuration.setText(duration > 0 ? format(duration) : "");
        ((LinearLayout.LayoutParams) fill.getLayoutParams()).weight = duration > 0 ? position : 0;
        ((LinearLayout.LayoutParams) rest.getLayoutParams()).weight = duration > 0 ? Math.max(duration - position, 0) : 1;
        fill.requestLayout();
        panel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hidePanel);
        if (player.getPlayWhenReady()) handler.postDelayed(hidePanel, 2500);
    }

    private TextView text(int size, int color) {
        TextView view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static final class Options {
        final List<String> names = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        int selected;
        int chosen;

        void add(String name, Runnable action, boolean current) {
            if (current) selected = names.size();
            names.add(name);
            actions.add(action);
        }

        String current() {
            return names.isEmpty() ? "—" : names.get(selected) + "   " + (selected + 1) + " из " + names.size();
        }
    }

    private Options voices() {
        Options tracks = new Options();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                int track = i;
                tracks.add(format.label != null ? format.label : format.language != null ? languageName(format.language) : "Дорожка " + (tracks.names.size() + 1), () -> {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setPreferredAudioLanguage(format.language)
                        .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), track)).build());
                    if (format.language != null) {
                        getSharedPreferences("auth", MODE_PRIVATE).edit().putString("audio_language", format.language).apply();
                    }
                }, group.isTrackSelected(i));
            }
        }
        JSONObject current = episode == null ? null : fileOf(episode, eid);
        if (tracks.names.size() >= 2 || current == null) return tracks;
        Options files = new Options();
        TreeSet<Integer> kinds = new TreeSet<>();
        for (JSONObject f : Data.list(episode.optJSONArray("files"))) kinds.add(f.optInt("translate"));
        for (int kind : kinds) {
            files.add(Playback.translateName(kind), () -> getSharedPreferences("auth", MODE_PRIVATE).edit().putInt("translate", kind).apply(),
                kind == current.optInt("translate"));
        }
        return files;
    }

    private static final java.util.Map<String, String> LANGUAGES = java.util.Map.of(
        "rus", "Русский", "eng", "Английский", "ukr", "Украинский", "fra", "Французский", "deu", "Немецкий",
        "spa", "Испанский", "ita", "Итальянский", "jpn", "Японский", "kor", "Корейский", "zho", "Китайский");

    // Mac.apk has no Russian locale data, so getDisplayLanguage answers in English there.
    private static String languageName(String code) {
        String known = null;
        try {
            known = LANGUAGES.get(new Locale(code).getISO3Language());
        } catch (java.util.MissingResourceException ignored) {
        }
        if (known != null) return known;
        String name = Locale.forLanguageTag(code).getDisplayLanguage(new Locale("ru"));
        return name.isEmpty() ? code : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private Options qualities() {
        Options options = new Options();
        JSONObject current = episode == null ? null : fileOf(episode, eid);
        if (current != null) {
            int translate = current.optInt("translate");
            TreeSet<Integer> levels = new TreeSet<>();
            for (JSONObject f : Data.list(episode.optJSONArray("files"))) if (f.optInt("translate") == translate) levels.add(f.optInt("quality"));
            for (int q : levels) {
                options.add(Playback.qualityName(q), () -> {
                    previousQuality = getSharedPreferences("auth", MODE_PRIVATE).getInt("quality", 0);
                    setQuality(q);
                }, q == current.optInt("quality"));
            }
            return options;
        }
        TreeSet<Integer> levels = new TreeSet<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) if (group.isTrackSupported(i)) levels.add(level(group.getTrackFormat(i).width));
        }
        Format playing = player.getVideoFormat();
        for (int q : levels) options.add(Playback.qualityName(q), () -> setQuality(q), playing != null && q == level(playing.width));
        return options;
    }

    // Episode translations and qualities are separate files, so their options only save the choice and this switches once.
    private void refile() {
        JSONObject current = episode == null ? null : fileOf(episode, eid);
        if (current == null) return;
        JSONObject wanted = Playback.pickFile(this, episode.optJSONArray("files"), 0);
        if (wanted != null && wanted.optInt("eid") != eid) switchTo(episode, wanted, player.getCurrentPosition());
    }

    private void setQuality(int quality) {
        getSharedPreferences("auth", MODE_PRIVATE).edit().putInt("quality", quality).apply();
        player.setTrackSelectionParameters(sized(player.getTrackSelectionParameters().buildUpon(), quality).build());
    }

    // A quality the stream fails on would otherwise stick and break every later episode that has it.
    private void undoQuality() {
        if (previousQuality >= 0) setQuality(previousQuality);
        previousQuality = -1;
    }

    private static TrackSelectionParameters.Builder sized(TrackSelectionParameters.Builder params, int quality) {
        boolean known = quality > 0 && quality < WIDTHS.length;
        return params.setMaxVideoSize(known ? WIDTHS[quality] : Integer.MAX_VALUE, Integer.MAX_VALUE)
            .setMinVideoSize(known ? WIDTHS[quality] * 9 / 10 : 0, 0);
    }

    // Movie variants are 16:9 or wider with varying height, so the width tells the quality.
    private static int level(int width) {
        int q = WIDTHS.length - 1;
        while (q > 1 && width < WIDTHS[q] * 9 / 10) q--;
        return q;
    }

    private void cycleVoice(int direction) {
        Options voices = voices();
        if (voices.names.size() < 2) {
            show("Других озвучек нет");
            return;
        }
        int next = Math.floorMod(voices.selected + direction, voices.names.size());
        voices.actions.get(next).run();
        refile();
        show("Озвучка: " + voices.names.get(next) + "   " + (next + 1) + "/" + voices.names.size());
    }

    private void showInfo() {
        player.pause();
        long position = player.getCurrentPosition();
        long duration = Math.max(player.getDuration(), 0);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(24), dp(28), dp(20));
        TextView name = text(22, 0xFFFFFFFF);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setText(getIntent().getStringExtra("title"));
        content.addView(name);
        if (episode != null) {
            String title = Data.episodeTitle(episode);
            TextView line = text(16, 0xFFFFFFFF);
            line.setText("Сезон " + episode.optInt("season") + ", серия " + episode.optInt("episode") + (title.isEmpty() ? "" : "  ·  " + title));
            content.addView(line);
        }
        TextView time = text(16, 0xFF9AA0A6);
        time.setText(duration > 0 ? format(position) + " / " + format(duration) + "   осталось " + format(duration - position) : format(position));
        content.addView(time);
        Options voices = voices();
        Options qualities = qualities();
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(content)
            .setOnCancelListener(d -> showPanel("❚❚  Пауза", player.getCurrentPosition()))
            .create();
        Runnable apply = () -> {
            dialog.dismiss();
            for (Options options : List.of(voices, qualities)) {
                if (options.chosen != options.selected) options.actions.get(options.chosen).run();
            }
            refile();
            player.play();
            showPanel("▶", player.getCurrentPosition());
        };
        TextView first = optionRow("Перевод", voices, apply);
        content.addView(first, rowParams(dp(18)));
        content.addView(optionRow("Качество", qualities, apply), rowParams(dp(8)));
        TextView hint = text(14, 0xFF9AA0A6);
        hint.setText("↑ ↓  выбор     ← →  изменить     OK  применить     Назад  закрыть");
        content.addView(hint, rowParams(dp(18)));
        // The menu opens while OK is still held, so the rest of that press must not reach it as a click.
        boolean[] fresh = {false};
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) fresh[0] = true;
            return !fresh[0];
        });
        dialog.show();
        first.requestFocus();
    }

    private TextView optionRow(String label, Options options, Runnable apply) {
        options.chosen = options.selected;
        TextView row = text(18, 0xFFFFFFFF);
        row.setFocusable(true);
        // Mac.apk stays in touch mode, where a plain focusable row never shows the selection.
        row.setFocusableInTouchMode(true);
        row.setPadding(dp(18), dp(12), dp(18), dp(12));
        StateListDrawable background = new StateListDrawable();
        GradientDrawable focused = rounded(0xFF2B4C7E, 12);
        focused.setStroke(dp(2), 0xFF8CB4E8);
        background.addState(new int[] {android.R.attr.state_focused}, focused);
        background.addState(new int[] {}, rounded(0xFF1F2733, 12));
        row.setBackground(background);
        Runnable paint = () -> row.setText(options.names.size() < 2 ? label + ":   " + options.current()
            : label + ":   ‹  " + options.names.get(options.chosen) + "  ›      " + (options.chosen + 1) + " из " + options.names.size());
        paint.run();
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            // Mac.apk does not move focus between the rows by itself.
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                android.view.ViewGroup parent = (android.view.ViewGroup) v.getParent();
                View next = parent.getChildAt(parent.indexOfChild(v) + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1));
                if (next != null && next.isFocusable()) next.requestFocus();
                return true;
            }
            if (options.names.size() < 2 || keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            options.chosen = Math.floorMod(options.chosen + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1), options.names.size());
            paint.run();
            return true;
        });
        row.setOnClickListener(v -> apply.run());
        return row;
    }

    private LinearLayout.LayoutParams rowParams(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, top, 0, 0);
        return params;
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(save, SAVE_EVERY_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(save);
        handler.removeCallbacks(longPress);
        saveTime();
        player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
        player.release();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (upcoming != null && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            startEpisode(upcoming);
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                cycleVoice(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MENU:
                cycleVoice(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-STEP_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(STEP_MS);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (event.getRepeatCount() == 0) {
                    pressed = true;
                    held = false;
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (event.getRepeatCount() == 0) togglePause();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER || !pressed) return super.onKeyUp(keyCode, event);
        pressed = false;
        handler.removeCallbacks(longPress);
        if (!held) togglePause();
        return true;
    }

    private void seek(int delta) {
        long duration = player.getDuration();
        long target = Math.max(0, duration > 0 ? Math.min(duration - 1000, player.getCurrentPosition() + delta) : player.getCurrentPosition() + delta);
        player.seekTo(target);
        long now = SystemClock.uptimeMillis();
        seekTotal = now - seekAt < 1500 && Long.signum(seekTotal) == Integer.signum(delta) ? seekTotal + delta : delta;
        seekAt = now;
        long seconds = Math.abs(seekTotal) / 1000;
        String amount = seconds >= 60 ? seconds / 60 + " мин" + (seconds % 60 > 0 ? " " + seconds % 60 + " с" : "") : seconds + " с";
        showPanel((seekTotal > 0 ? "»  +" : "«  −") + amount, target);
    }

    private void togglePause() {
        player.setPlayWhenReady(!player.getPlayWhenReady());
        showPanel(player.getPlayWhenReady() ? "▶" : "❚❚  Пауза", player.getCurrentPosition());
    }

    private void show(String text) {
        overlay.setText(text);
        overlay.setVisibility(TextView.VISIBLE);
        handler.removeCallbacks(hideOverlay);
        handler.postDelayed(hideOverlay, 2000);
    }

    private void saveTime() {
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (duration > 0 && position >= duration * WATCHED_FRACTION) markWatched();
        long seconds = position / 1000;
        if (eid == 0 || seconds <= 0) return;
        new Thread(() -> {
            try {
                api.saveTime(eid, seconds);
            } catch (Exception e) {
                Log.w(TAG, "savets failed: " + e.getMessage());
            }
        }).start();
    }

    private static String format(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }
}
