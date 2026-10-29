/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which day-dependent parts apply on a date. The table (assets/calendar.json, built by
 * tools/import.py from onlinesiddur.com's precomputed tables) has one row per civil
 * date, for Israel and outside, for the day and the night: a bitset over the condition
 * tokens (URL-safe base64, 6 bits a character, low bit first).
 */
public final class Calendar {
    private static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private final LocalDate epoch;
    private final int days, w;
    private final String[] tokens;
    private final JSONObject il, gl;

    Calendar(JSONObject t) throws Exception {
        JSONArray e = t.getJSONArray("e");                 // [year, month 0-based, day]
        epoch = LocalDate.of(e.getInt(0), e.getInt(1) + 1, e.getInt(2));
        days = t.getInt("d");
        w = t.getInt("w");
        JSONArray n = t.getJSONArray("n");
        tokens = new String[n.length()];
        for (int i = 0; i < tokens.length; i++) tokens[i] = n.getString(i);
        il = t.getJSONObject("il");
        gl = t.getJSONObject("gl");
    }

    public LocalDate lastDate() { return epoch.plusDays(days - 1); }

    /**
     * A moment of prayer: the civil date whose row applies, and whether it's the night
     * set. A night set after midday belongs to tomorrow's row (the evening starts the
     * next Hebrew day); before midday it's still last night's.
     */
    public static final class When {
        public final LocalDate date;
        public final boolean night;
        When(LocalDate date, boolean night) { this.date = date; this.night = night; }
    }

    public static When when(LocalDateTime now, boolean night) {
        LocalDate d = now.toLocalDate();
        if (night && now.getHour() >= 12) d = d.plusDays(1);
        return new When(d, night);
    }

    /** The page kind (day / night / meal) at {@code now}; a meal page is night only when the reader says so. */
    public static When forPage(String daykind, LocalDateTime now, boolean afterNightfall) {
        boolean night = "night".equals(daykind) || ("meal".equals(daykind) && afterNightfall);
        return when(now, night);
    }

    /** The tokens that hold, or null past the table's range (then nothing is hidden). */
    public Set<String> conds(When when, boolean israel) {
        long i = ChronoUnit.DAYS.between(epoch, when.date);
        if (i < 0 || i >= days) return null;
        String rows = (israel ? il : gl).optString(when.night ? "night" : "day", null);
        if (rows == null || rows.length() < (i + 1) * w) return null;
        Set<String> out = new LinkedHashSet<>();
        int off = (int) i * w;
        for (int k = 0; k < w; k++) {
            int code = ALPHA.indexOf(rows.charAt(off + k));
            if (code < 0) return null;
            for (int j = 0; j < 6; j++) {
                if (((code >> j) & 1) != 0 && k * 6 + j < tokens.length) out.add(tokens[k * 6 + j]);
            }
        }
        return out;
    }
}
