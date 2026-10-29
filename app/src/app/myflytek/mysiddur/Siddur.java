/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** The bundled texts (assets/siddur/, made by tools/import.py). */
public final class Siddur {
    private static Siddur instance;

    public static synchronized Siddur get(Context c) {
        if (instance == null) instance = new Siddur(c.getApplicationContext());
        return instance;
    }

    public static final class Page {
        public final String id, he, en, group, daykind;
        public final boolean filtered;
        Page(JSONObject o) {
            id = o.optString("id"); he = o.optString("he"); en = o.optString("en");
            group = o.optString("group"); daykind = o.optString("daykind");
            filtered = o.optBoolean("filtered");
        }
    }

    public static final class Section {
        public final String id, he, when;
        Section(String id, String he, String when) { this.id = id; this.he = he; this.when = when; }
    }

    private final Context app;
    public final List<Page> pages = new ArrayList<>();
    public final String fetched;
    private Calendar calendar;

    private Siddur(Context app) {
        this.app = app;
        String f = "";
        try {
            JSONObject idx = new JSONObject(asset("siddur/index.json"));
            f = idx.optString("fetched");
            JSONArray a = idx.getJSONArray("pages");
            for (int i = 0; i < a.length(); i++) pages.add(new Page(a.getJSONObject(i)));
        } catch (Exception e) {
            throw new IllegalStateException("bad siddur/index.json", e);
        }
        fetched = f;
    }

    public Page page(String id) {
        for (Page p : pages) if (p.id.equals(id)) return p;
        return null;
    }

    public synchronized Calendar calendar() {
        if (calendar == null) {
            try { calendar = new Calendar(new JSONObject(asset("siddur/calendar.json"))); }
            catch (Exception e) { throw new IllegalStateException("bad siddur/calendar.json", e); }
        }
        return calendar;
    }

    public List<Section> sections(String id) {
        List<Section> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(asset("siddur/pages/" + id + ".json")).getJSONArray("sections");
            for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.getJSONObject(i);
                out.add(new Section(s.getString("id"), s.getString("he"), s.optString("when", null)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /**
     * The special Torah reading for a day (the section's name in the Torah page), or null on
     * an ordinary day. Rosh Chodesh Tevet in Chanukah starts with the Rosh Chodesh reading.
     */
    public static String torahToday(java.util.Set<String> day) {
        if (day.contains("tisha-bav")) return "תשעה באב";
        if (day.contains("fast-day")) return "תענית ציבור";
        boolean shabbat = day.contains("d7");
        if (day.contains("chol-hamoed-pesach") && !shabbat) return "חול המועד פסח";
        if (day.contains("chol-hamoed-sukkot") && !shabbat) return "חול המועד סוכות";
        if (day.contains("rosh-chodesh")) return "ראש חודש";
        if (day.contains("chanukah")) return "חנוכה";
        if (day.contains("purim")) return "פורים";
        return null;
    }

    /** The id of the section of page {@code id} named {@code he}, or null. */
    public String anchor(String id, String he) {
        if (he == null) return null;
        for (Section s : sections(id)) if (s.he.equals(he)) return s.id;
        return null;
    }

    /** The page body: its day-rule CSS and the reading's HTML. */
    public String body(String id) throws IOException {
        return asset("siddur/pages/" + id + ".html");
    }

    public String asset(String path) throws IOException {
        try (InputStream in = app.getAssets().open(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(8192, in.available()));
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
