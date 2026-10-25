package dev.ad.soap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

final class Api {
    private static final String HOST = "https://api.soap4uand.me/v2";
    private static final String USER_AGENT = "Android: soap4u_flutter v1.0.0";

    private final String token;
    private final String session;

    Api(String token, String session) {
        this.token = token;
        this.session = session;
    }

    static String[] login(String login, String password) throws IOException {
        String body = "login=" + URLEncoder.encode(login, "UTF-8") + "&password=" + URLEncoder.encode(password, "UTF-8");
        JSONObject json = parseObject(request("/auth", body, null, null));
        String token = json.optString("token", "");
        String session = json.optString("sid", "");
        if (token.isEmpty() || session.isEmpty()) throw new IOException(json.optString("message", "login failed"));
        return new String[] {token, session, json.optString("till", "")};
    }

    static String text(String html) {
        return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    static String cover(int soapId) {
        return "https://soap4youand.me/assets/covers/soap/big/" + soapId + ".jpg";
    }

    JSONObject play(int eid, int soapId, String fileHash) throws IOException {
        String body = "hash=" + md5(token + eid + soapId + fileHash) + "&eid=" + eid;
        return parseObject(request("/play/episode/" + eid, body, token, session));
    }

    void saveTime(int eid, long seconds) throws IOException {
        request("/play/episode/" + eid + "/savets/", "eid=" + eid + "&time=" + seconds, token, session);
    }

    void post(String path, String form) throws IOException {
        JSONObject json = parseObject(request(path, form, token, session));
        if (json.optInt("ok") != 1) throw new IOException(json.optString("msg", "ошибка сервера"));
    }

    void logout() throws IOException {
        request("/logout", "", token, session);
    }

    String raw(String path) throws IOException {
        return request(path, null, token, session);
    }

    JSONObject object(String path) throws IOException {
        return parseObject(request(path, null, token, session));
    }

    JSONArray array(String path) throws IOException {
        try {
            return new JSONArray(request(path, null, token, session));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    private static String md5(String text) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : java.security.MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject parseObject(String text) throws IOException {
        try {
            return new JSONObject(text);
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    private static String request(String path, String form, String token, String session) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(HOST + path).openConnection();
        c.setConnectTimeout(20_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", USER_AGENT);
        c.setRequestProperty("Accept-Encoding", "gzip");
        if (token != null) c.setRequestProperty("X-API-TOKEN", token);
        if (session != null) c.setRequestProperty("Cookie", "PHPSESSID=" + session);
        if (form != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.getOutputStream().write(form.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in != null && "gzip".equals(c.getContentEncoding())) in = new GZIPInputStream(in);
        String text = in == null ? "" : read(in);
        if (code >= 400) throw new IOException("HTTP " + code + " " + text);
        return text;
    }

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        return out.toString("UTF-8");
    }
}
