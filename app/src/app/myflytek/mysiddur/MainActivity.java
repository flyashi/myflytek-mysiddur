/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Home: today's date and what's special about it, then every prayer by group. */
public final class MainActivity extends Activity {
    private Prefs prefs;
    private Siddur siddur;
    private LinearLayout list;
    private String shownFor = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        siddur = Siddur.get(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(getColor(R.color.eink_bar));
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView name = new TextView(this);
        name.setText("סידור  ·  My Siddur");
        name.setTextColor(Color.BLACK);
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(Ui.dp(this, 16), 0, 0, 0);
        bar.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton settings = Ui.icon(this, R.drawable.ic_menu, "Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(settings);
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        col.addView(Ui.rule(this));

        ScrollView sv = new ScrollView(this);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 32));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(col);
    }

    @Override protected void onResume() {
        super.onResume();
        // rebuild when the day (or its half) or the location changed
        LocalDateTime now = LocalDateTime.now();
        String key = now.toLocalDate() + "|" + (now.getHour() >= 12) + "|" + prefs.israel();
        if (!key.equals(shownFor)) { shownFor = key; build(now); }
    }

    private void build(LocalDateTime now) {
        list.removeAllViews();
        Calendar cal = siddur.calendar();
        boolean il = prefs.israel();
        Calendar.When dayW = Calendar.when(now, false), nightW = Calendar.when(now, true);
        Set<String> day = orEmpty(cal.conds(dayW, il));
        Set<String> night = orEmpty(cal.conds(nightW, il));
        boolean afternoon = now.getHour() >= 12;
        // before midday the night set is last night's (a night page then still means last
        // night, e.g. dawn Selichot); "tonight" on the home screen is the coming evening
        Set<String> tonight = afternoon ? night
                : orEmpty(cal.conds(new Calendar.When(now.toLocalDate().plusDays(1), true), il));

        // the date card
        LocalDate today = now.toLocalDate();
        StringBuilder line1 = new StringBuilder(HebDate.weekday(today)).append(" · ").append(HebDate.of(today));
        for (String l : HebDate.labels(cal.conds(dayW, il), today, il)) line1.append(" · ").append(l);
        TextView d1 = text(line1.toString(), 22, true);
        d1.setPadding(0, Ui.dp(this, 8), 0, 0);
        list.addView(d1);
        StringBuilder line2 = new StringBuilder();
        if (afternoon) {
            line2.append("הערב: ליל ").append(HebDate.of(nightW.date));
            List<String> nl = HebDate.labels(cal.conds(nightW, il), nightW.date, il);
            nl.removeAll(HebDate.labels(cal.conds(dayW, il), today, il));
            for (String l : nl) line2.append(" · ").append(l);
            line2.append("   ");
        }
        line2.append(il ? "ארץ ישראל" : "חוץ לארץ");
        if (cal.conds(dayW, il) == null) line2.append(" · past the calendar table (").append(cal.lastDate()).append(")");
        list.addView(text(line2.toString(), 16, false));

        // what's for today beyond the daily prayers
        addHeader("היום");
        for (Siddur.Page p : siddur.pages) {
            String why = special(p.id, day, night, tonight, afternoon);
            if (why != null) addRow(p, why, "torah".equals(p.id) ? siddur.anchor("torah", why) : null);
        }
        Tehilim.Portion tm = Tehilim.monthly(today);
        addRow(siddur.page("tehilim"), "חודשי: " + tm.label + "   ·   שבועי: " + Tehilim.weekly(today).label, tm.anchor);

        String[][] groups = {{"daily", "תפילות"}, {"tehilim", "תהילים"}, {"times", "זמנים מיוחדים"}, {"other", "שונות"}};
        for (String[] g : groups) {
            addHeader(g[1]);
            for (Siddur.Page p : siddur.pages) if (p.group.equals(g[0])) addRow(p, null, null);
        }
        TextView src = text("Texts: onlinesiddur.com (from Sefaria), fetched " + siddur.fetched
                + ". Nusach Ashkenaz. The Name is written ד׳ / א׳.", 13, false);
        src.setPadding(0, Ui.dp(this, 24), 0, 0);
        src.setTextDirection(View.TEXT_DIRECTION_LTR);
        src.setGravity(Gravity.LEFT);
        list.addView(src);
    }

    /** why an occasional page is for today/tonight, or null */
    private static String special(String id, Set<String> day, Set<String> night, Set<String> tonight,
                                  boolean afternoon) {
        switch (id) {
            case "musaf": return day.contains("rosh-chodesh") ? "ראש חודש" : null;
            case "musafmoed": return day.contains("chol-hamoed") ? "חול המועד" : null;
            case "hoshanot": return day.contains("sukkot") || day.contains("hoshana-rabba") ? "סוכות" : null;
            case "nerot": return tonight.contains("chanukah") ? "הלילה" : null;
            case "megillah": return day.contains("purim") || night.contains("purim") ? "פורים" : null;
            case "omer": {
                // before midday: last night's count is today's (count without a bracha if missed)
                for (String s : night) {
                    if (s.startsWith("omer-")) {
                        return (afternoon ? "הלילה: " : "היום: ") + HebDate.number(Integer.parseInt(s.substring(5))) + " לעומר";
                    }
                }
                return null;
            }
            case "slichot": {
                for (String s : night) if (s.startsWith("slichot-") && !s.equals("slichot-none")) return "סליחות";
                return null;
            }
            case "tishabav": return day.contains("tisha-bav") || night.contains("tisha-bav") ? "תשעה באב" : null;
            case "torah": return Siddur.torahToday(day);
            default: return null;
        }
    }

    private static Set<String> orEmpty(Set<String> s) { return s == null ? new HashSet<>() : s; }

    private TextView text(String s, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.BLACK);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setGravity(Gravity.RIGHT);
        return t;
    }

    private void addHeader(String s) {
        TextView t = text(s, 15, true);
        t.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 4));
        list.addView(t);
        list.addView(Ui.rule(this));
    }

    private void addRow(final Siddur.Page p, String note, final String jump) {
        if (p == null) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setMinimumHeight(Ui.dp(this, 64));
        row.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView he = text(p.he, 24, true);
        texts.addView(he);
        if (note != null) {
            TextView n = text(note, 16, false);
            n.setMaxLines(2);                    // the Tehillim line (monthly + weekly) needs two
            n.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(n);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView en = new TextView(this);
        en.setText(p.en);
        en.setTextColor(Color.BLACK);           // greys dither on e-ink
        en.setTextSize(15);
        row.addView(en);
        row.setOnClickListener(v -> startActivity(new Intent(this, ReaderActivity.class)
                .putExtra(ReaderActivity.EXTRA_PAGE, p.id).putExtra(ReaderActivity.EXTRA_JUMP, jump)));
        list.addView(row);
        View sep = new View(this);
        sep.setBackgroundColor(0xFFBBBBBB);
        list.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }
}
