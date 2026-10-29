/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.icu.util.HebrewCalendar;
import android.icu.util.TimeZone;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** The Hebrew date of a civil date, in Hebrew letters, and names for the day's tokens. */
public final class HebDate {
    public final int year, month, day;     // month: HebrewCalendar.TISHRI (0) … ELUL (12)
    public final boolean leap;

    private HebDate(int year, int month, int day) {
        this.year = year; this.month = month; this.day = day;
        leap = ((7 * year + 1) % 19) < 7;
    }

    public static HebDate of(LocalDate d) {
        HebrewCalendar c = new HebrewCalendar(TimeZone.GMT_ZONE);
        c.clear();
        // set the Gregorian date via a Gregorian calendar's millis (noon, so no zone edge)
        android.icu.util.GregorianCalendar g = new android.icu.util.GregorianCalendar(TimeZone.GMT_ZONE);
        g.clear();
        g.set(d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth(), 12, 0, 0);
        c.setTimeInMillis(g.getTimeInMillis());
        return new HebDate(c.get(HebrewCalendar.EXTENDED_YEAR), c.get(HebrewCalendar.MONTH),
                c.get(HebrewCalendar.DAY_OF_MONTH));
    }

    private static final String[] MONTHS = {
            "תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר א׳", "אדר", "ניסן", "אייר", "סיון", "תמוז", "אב", "אלול"};

    public String monthName() {
        if (month == HebrewCalendar.ADAR && leap) return "אדר ב׳";
        return MONTHS[month];
    }

    /** e.g. י״ח חשון תשפ״ז */
    public String toString() {
        return number(day) + " " + monthName() + " " + number(year % 1000);
    }

    private static final String ONES = "אבגדהוזחט", TENS = "יכלמנסעפצ", HUNDREDS = "קרשת";

    /** A number in Hebrew letters with geresh / gershayim (15 = ט״ו, 16 = ט״ז). */
    public static String number(int n) {
        StringBuilder s = new StringBuilder();
        while (n >= 400) { s.append('ת'); n -= 400; }
        if (n >= 100) { s.append(HUNDREDS.charAt(n / 100 - 1)); n %= 100; }
        if (n == 15) s.append("טו");
        else if (n == 16) s.append("טז");
        else {
            if (n >= 10) { s.append(TENS.charAt(n / 10 - 1)); n %= 10; }
            if (n > 0) s.append(ONES.charAt(n - 1));
        }
        if (s.length() == 1) return s + "׳";
        return s.substring(0, s.length() - 1) + "״" + s.charAt(s.length() - 1);
    }

    private static final String[] DAYS = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};

    /** יום חמישי … for java.time's Monday=1 … Sunday=7 */
    public static String weekday(LocalDate d) {
        int i = d.getDayOfWeek().getValue() % 7;      // Sunday → 0
        return i == 6 ? "שבת" : "יום " + DAYS[i];
    }

    /** Rosh Chodesh by the date: the 30th, or the 1st of any month but Tishri. */
    public boolean roshChodesh() { return day == 30 || (day == 1 && month != HebrewCalendar.TISHRI); }

    /**
     * The day's notable names, in Hebrew, for the date line: from its tokens (null past the
     * table) and from its Hebrew date (Shabbat Mevarchim, Purim Katan, Lag BaOmer…).
     */
    public static List<String> labels(Set<String> t, LocalDate d, boolean israel) {
        List<String> out = new ArrayList<>();
        if (t == null) t = Collections.emptySet();
        HebDate h = of(d);
        int m = h.month, n = h.day;
        boolean tishri = m == HebrewCalendar.TISHRI, adar = m == HebrewCalendar.ADAR;
        if (t.contains("rosh-hashana")) out.add("ראש השנה");
        if (tishri && n == 9) out.add("ערב יום כיפור");
        if (tishri && n == 10) out.add("יום כיפור");
        else if (t.contains("ayt")) out.add("עשרת ימי תשובה");
        if (t.contains("hoshana-rabba")) out.add("הושענא רבה");
        else if (t.contains("chol-hamoed-sukkot")) out.add("חול המועד סוכות");
        else if (t.contains("sukkot")) out.add("סוכות");
        if (tishri && n == 22) out.add(israel ? "שמיני עצרת · שמחת תורה" : "שמיני עצרת");
        if (tishri && n == 23 && !israel) out.add("שמחת תורה");
        if (m == HebrewCalendar.NISAN && n == 14) out.add("ערב פסח");
        if (t.contains("chol-hamoed-pesach")) out.add("חול המועד פסח");
        else if (t.contains("pesach")) out.add("פסח");
        if (t.contains("shavuot")) out.add("שבועות");
        if (h.roshChodesh()) out.add("ראש חודש " + (n == 30 ? of(d.plusDays(1)) : h).monthName());
        if (t.contains("chanukah")) out.add("חנוכה");
        if (m == HebrewCalendar.SHEVAT && n == 15) out.add("ט״ו בשבט");
        if (m == HebrewCalendar.ADAR_1 && n == 14) out.add("פורים קטן");
        if (m == HebrewCalendar.ADAR_1 && n == 15) out.add("שושן פורים קטן");
        if (t.contains("purim")) out.add("פורים");
        if (adar && n == 15) out.add("שושן פורים");
        if (m == HebrewCalendar.IYAR && n == 14) out.add("פסח שני");
        if (m == HebrewCalendar.IYAR && n == 18) out.add("ל״ג בעומר");
        if (t.contains("tisha-bav")) out.add("תשעה באב");
        else if (t.contains("fast-day")) {
            out.add(tishri ? "צום גדליה" : m == HebrewCalendar.TEVET ? "עשרה בטבת" : adar ? "תענית אסתר"
                    : m == HebrewCalendar.TAMUZ ? "שבעה עשר בתמוז" : "תענית ציבור");
        }
        if (m == HebrewCalendar.AV && n == 15) out.add("ט״ו באב");
        if (m == HebrewCalendar.ELUL && n == 29) out.add("ערב ראש השנה");
        // the Shabbat before Rosh Chodesh (not before Tishri): Rosh Chodesh is 1-7 days later
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY && m != HebrewCalendar.ELUL && n >= 23 && n <= 29) {
            out.add("שבת מברכים " + of(d.plusDays(8)).monthName());
        }
        for (String s : t) {
            if (s.startsWith("omer-")) out.add(number(Integer.parseInt(s.substring(5))) + " לעומר");
        }
        return out;
    }
}
