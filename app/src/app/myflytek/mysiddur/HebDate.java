/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.icu.util.HebrewCalendar;
import android.icu.util.TimeZone;

import java.time.LocalDate;
import java.util.ArrayList;
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

    /** The day's notable tokens, in Hebrew, for the date line. */
    public static List<String> labels(Set<String> t) {
        List<String> out = new ArrayList<>();
        if (t == null) return out;
        if (t.contains("rosh-hashana")) out.add("ראש השנה");
        if (t.contains("ayt")) out.add("עשרת ימי תשובה");
        if (t.contains("hoshana-rabba")) out.add("הושענא רבה");
        else if (t.contains("chol-hamoed-sukkot")) out.add("חול המועד סוכות");
        else if (t.contains("sukkot")) out.add("סוכות");
        if (t.contains("shemini-atzeret")) out.add("שמיני עצרת");
        if (t.contains("chol-hamoed-pesach")) out.add("חול המועד פסח");
        else if (t.contains("pesach")) out.add("פסח");
        if (t.contains("shavuot")) out.add("שבועות");
        if (t.contains("rosh-chodesh")) out.add("ראש חודש");
        if (t.contains("chanukah")) out.add("חנוכה");
        if (t.contains("purim")) out.add("פורים");
        if (t.contains("tisha-bav")) out.add("תשעה באב");
        else if (t.contains("fast-day")) out.add("תענית ציבור");
        for (String s : t) {
            if (s.startsWith("omer-")) out.add(number(Integer.parseInt(s.substring(5))) + " לעומר");
        }
        return out;
    }
}
