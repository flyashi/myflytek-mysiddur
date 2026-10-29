/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Typed access to the Settings screen's values, plus a little reading state. */
public final class Prefs {
    private final SharedPreferences p;

    public Prefs(Context c) {
        p = c.getSharedPreferences(c.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() { return p; }

    public boolean israel() { return "israel".equals(p.getString("location", "diaspora")); }
    public boolean filter() { return p.getBoolean("filter", true); }
    public boolean rubrics() { return p.getBoolean("rubrics", false); }
    public boolean headings() { return p.getBoolean("headings", true); }
    public boolean pageButtons() { return p.getBoolean("page_buttons", true); }
    public boolean awake() { return p.getBoolean("awake", true); }
    public String font() { return p.getString("font", "ruehl"); }
    public String nikud() { return p.getString("nikud", "all"); }

    public int size() {
        try { return Math.max(14, Math.min(60, Integer.parseInt(p.getString("size", "24")))); }
        catch (NumberFormatException e) { return 24; }
    }

    public void setSize(int s) { p.edit().putString("size", String.valueOf(s)).apply(); }

    public String lineHeight() {
        String s = p.getString("lh", "1.8");
        return s.matches("\\d(\\.\\d+)?") ? s : "1.8";
    }

    // ---- "it's after nightfall" for the meal-time pages (Birkat HaMazon, Kaddish…):
    // holds until the next midday, like onlinesiddur.com's switch

    public boolean afterNightfall() { return p.getLong("night_until", 0) > System.currentTimeMillis(); }

    public void setAfterNightfall(boolean on) {
        long until = 0;
        if (on) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime noon = now.toLocalDate().atTime(12, 0);
            if (!noon.isAfter(now)) noon = noon.plusDays(1);
            until = noon.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        p.edit().putLong("night_until", until).apply();
    }

    // ---- where you were in each page

    public void savePosition(String page, float fraction) {
        p.edit().putFloat("pos_" + page, fraction).putLong("pos_t_" + page, System.currentTimeMillis()).apply();
    }

    /** The saved position, if it's recent enough to still be the same prayer (or always, for Tehillim). */
    public float position(String page, long maxAgeMs) {
        long t = p.getLong("pos_t_" + page, 0);
        if (maxAgeMs > 0 && System.currentTimeMillis() - t > maxAgeMs) return 0;
        return p.getFloat("pos_" + page, 0);
    }
}
