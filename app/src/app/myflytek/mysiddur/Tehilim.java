/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.app.Activity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The monthly (30-day) and weekly divisions of Tehillim, and the jump list. */
public final class Tehilim {
    private Tehilim() { }

    /** the first chapter of each day of the month, 1…30 (days 25 and 26 split Psalm 119 at verse 97) */
    private static final int[] MONTH = {1, 10, 18, 23, 29, 35, 39, 44, 49, 55, 60, 66, 69, 72, 77, 79, 83,
            88, 90, 97, 104, 106, 108, 113, 119, 119, 120, 135, 140, 145};
    /** the first chapter of each day of the week, Sunday…Shabbat */
    private static final int[] WEEK = {1, 30, 51, 73, 90, 107, 120};

    public static final class Portion {
        public final String label, anchor;
        Portion(String label, String anchor) { this.label = label; this.anchor = anchor; }
    }

    private static String range(int day) {
        if (day == 25) return "קי״ט א–צ״ו";
        if (day == 26) return "קי״ט צ״ז–קע״ו";
        int from = MONTH[day - 1], to = day == 30 ? 150 : MONTH[day] - 1;
        return from == to ? HebDate.number(from) : HebDate.number(from) + "–" + HebDate.number(to);
    }

    public static Portion month(int day) {
        return new Portion("יום " + HebDate.number(day) + " · " + range(day), "day-" + day);
    }

    /** Today's day-of-month portion; on the 29th of a 29-day month, days 29 and 30 together. */
    public static Portion monthly(LocalDate today) {
        HebDate h = HebDate.of(today);
        if (h.day == 29 && HebDate.of(today.plusDays(1)).day == 1) {
            return new Portion("יום כ״ט–ל׳ · " + HebDate.number(MONTH[28]) + "–" + HebDate.number(150), "day-29");
        }
        return month(h.day);
    }

    public static Portion week(int dow) {          // 0 = Sunday … 6 = Shabbat
        int from = WEEK[dow], to = dow == 6 ? 150 : WEEK[dow + 1] - 1;
        String name = dow == 6 ? "שבת" : "יום " + HebDate.number(dow + 1);
        return new Portion(name + " · " + HebDate.number(from) + "–" + HebDate.number(to), "sec-" + (from - 1));
    }

    public static Portion weekly(LocalDate today) {
        return week(today.getDayOfWeek().getValue() % 7);
    }

    public interface Jump { void to(String anchor); }

    /** By day of the month, by day of the week, or by chapter. */
    public static void showIndex(Activity a, Jump jump) {
        final List<String> names = new ArrayList<>();
        final List<String> anchors = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Portion m = monthly(today), w = weekly(today);
        names.add("היום (חודשי): " + m.label); anchors.add(m.anchor);
        names.add("היום (שבועי): " + w.label); anchors.add(w.anchor);
        names.add("— לפי ימי החודש —"); anchors.add(null);
        for (int d = 1; d <= 30; d++) { names.add(month(d).label); anchors.add("day-" + d); }
        names.add("— לפי ימי השבוע —"); anchors.add(null);
        for (int d = 0; d < 7; d++) { Portion p = week(d); names.add(p.label); anchors.add(p.anchor); }
        names.add("— פרקים —"); anchors.add(null);
        for (int c = 1; c <= 150; c++) { names.add("פרק " + HebDate.number(c)); anchors.add("sec-" + (c - 1)); }
        Ui.list(a, "תהילים", names, -1, i -> { if (anchors.get(i) != null) jump.to(anchors.get(i)); });
    }
}
