/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One prayer (or Tehillim, or the Torah readings) in a WebView, filtered for today. */
public final class ReaderActivity extends Activity {
    public static final String EXTRA_PAGE = "page";
    public static final String EXTRA_JUMP = "jump";

    /** a prayer reopened within this long continues where you were; later it starts at the top */
    private static final long RESUME_MS = 3 * 3600 * 1000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private Siddur siddur;
    private Siddur.Page page;
    private WebView wv;
    private TextView title, status;
    private Button filterBtn, nightBtn, todayBtn, weekBtn;
    private LinearLayout pageButtons;

    private boolean filterOn;
    private String loadedLook;           // the settings the page was rendered with
    private String loadedConds;          // the condition tokens it was rendered with
    private String pendingJump;
    private float pendingFraction = -1;
    private boolean loaded;
    private String section = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        siddur = Siddur.get(this);
        page = siddur.page(getIntent().getStringExtra(EXTRA_PAGE));
        if (page == null) { finish(); return; }
        filterOn = prefs.filter();
        pendingJump = getIntent().getStringExtra(EXTRA_JUMP);
        if (b != null) {
            filterOn = b.getBoolean("filter", filterOn);
            pendingFraction = b.getFloat("fraction", -1);
            pendingJump = null;
        } else if (pendingJump == null) {
            pendingFraction = prefs.position(page.id, "tehilim".equals(page.id) ? 0 : RESUME_MS);
        }
        buildUi();
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("filter", filterOn);
        out.putFloat("fraction", lastFraction);
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs.awake()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pageButtons.setVisibility(prefs.pageButtons() ? View.VISIBLE : View.GONE);
        if (!loaded) return;
        if (!look().equals(loadedLook)) {
            // Settings changed under us: re-render where we are
            readFraction(f -> { pendingFraction = f; render(); });
        } else {
            applyConds();                // the day may have moved on (midday, midnight)
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (loaded) readFraction(f -> prefs.savePosition(page.id, f));
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (wv != null) { wv.stopLoading(); wv.destroy(); wv = null; }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        // top bar: ← | title (tap: sections) | A− A+ | ⋮
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(getColor(R.color.eink_bar));
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        ImageButton back = Ui.icon(this, R.drawable.ic_back, "Back");
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        title = new TextView(this);
        title.setTextColor(Color.BLACK);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        title.setTextDirection(View.TEXT_DIRECTION_RTL);
        title.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        title.setText(page.he + "  ▾");
        title.setOnClickListener(v -> showSections());
        bar.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        Button smaller = Ui.barButton(this, "A−");
        smaller.setOnClickListener(v -> resize(-2));
        Button bigger = Ui.barButton(this, "A+");
        bigger.setOnClickListener(v -> resize(2));
        bar.addView(smaller);
        bar.addView(bigger);
        ImageButton menu = Ui.icon(this, R.drawable.ic_menu, "Menu");
        menu.setOnClickListener(this::showMenu);
        bar.addView(menu);
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));
        col.addView(Ui.rule(this));

        // status line: the day, and the filter / nightfall / Tehillim-portion buttons
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        line.setPadding(Ui.dp(this, 10), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
        status = new TextView(this);
        status.setTextColor(Color.BLACK);
        status.setTextSize(15);
        status.setTextDirection(View.TEXT_DIRECTION_RTL);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        filterBtn = Ui.chip(this, "");
        filterBtn.setOnClickListener(v -> { filterOn = !filterOn; applyConds(); });
        nightBtn = Ui.chip(this, "");
        nightBtn.setOnClickListener(v -> { prefs.setAfterNightfall(!prefs.afterNightfall()); applyConds(); });
        todayBtn = Ui.chip(this, "");
        weekBtn = Ui.chip(this, "");
        line.addView(todayBtn);
        line.addView(weekBtn);
        line.addView(nightBtn);
        line.addView(filterBtn);
        col.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(Ui.rule(this));

        FrameLayout frame = new FrameLayout(this);
        wv = new WebView(this);
        setupWebView();
        frame.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageButtons = new LinearLayout(this);
        pageButtons.setOrientation(LinearLayout.VERTICAL);
        ImageButton up = Ui.pageButton(this, R.drawable.ic_up, "Page up");
        up.setOnClickListener(v -> pageBy(-1));
        ImageButton down = Ui.pageButton(this, R.drawable.ic_down, "Page down");
        down.setOnClickListener(v -> pageBy(1));
        pageButtons.addView(up);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60));
        dlp.topMargin = Ui.dp(this, 12);
        pageButtons.addView(down, dlp);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        plp.leftMargin = Ui.dp(this, 10);
        plp.bottomMargin = Ui.dp(this, 24);
        frame.addView(pageButtons, plp);
        col.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(col);

        boolean tehilim = "tehilim".equals(page.id);
        todayBtn.setVisibility(tehilim ? View.VISIBLE : View.GONE);
        weekBtn.setVisibility(tehilim ? View.VISIBLE : View.GONE);
        if (tehilim) {
            LocalDate today = LocalDate.now();
            final Tehilim.Portion m = Tehilim.monthly(today), w = Tehilim.weekly(today);
            todayBtn.setText("היום: " + m.label);
            todayBtn.setOnClickListener(v -> jump(m.anchor));
            weekBtn.setText("שבועי: " + w.label);
            weekBtn.setOnClickListener(v -> jump(w.anchor));
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBlockNetworkLoads(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wv.setBackgroundColor(Color.WHITE);
        wv.setDefaultFocusHighlightEnabled(false);
        wv.setLongClickable(true);
        wv.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public void onPageFinished(WebView v, String url) { afterLoad(); }
        });
        wv.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            ui.removeCallbacks(trackSection);
            ui.postDelayed(trackSection, 250);
        });
    }

    // ---------------------------------------------------------------- rendering

    /** the settings a render depends on */
    private String look() {
        return prefs.size() + "|" + prefs.lineHeight() + "|" + prefs.font() + "|" + prefs.nikud() + "|"
                + prefs.headings() + "|" + prefs.israel() + "|" + prefs.rubrics() + "|" + prefs.pageButtons();
    }

    private static String fontStack(String id) {
        switch (id) {
            case "noto": return "'Noto Serif Hebrew', serif";
            case "cardo": return "'Cardo', serif";
            case "stam": return "'Shlomo SemiStam', serif";
            case "system": return "serif";
            default: return "'Frank Ruehl CLM', serif";
        }
    }

    private void render() {
        loaded = false;
        loadedLook = look();
        String body;
        try { body = siddur.body(page.id); }
        catch (IOException e) { body = "<p>Couldn't open this page.</p>"; }
        body = Nikud.apply(body, prefs.nikud());
        String conds = condsAttr();
        loadedConds = conds;
        StringBuilder d = new StringBuilder(body.length() + 4096);
        d.append("<!doctype html><html lang=\"he\" dir=\"rtl\" data-filter=\"").append(filterOn ? "on" : "off")
                .append("\" data-headings=\"").append(prefs.headings() ? "on" : "off")
                .append("\" data-pb=\"").append(prefs.pageButtons() ? "on" : "off").append('"');
        if (conds != null) d.append(" data-conds=\"").append(conds).append('"');
        d.append("><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<link rel=\"stylesheet\" href=\"reader.css\">")
                .append("<style>:root{--size:").append(prefs.size()).append("px;--lh:").append(prefs.lineHeight())
                .append(";--he:").append(fontStack(prefs.font())).append(";}</style>")
                .append("</head><body><main>")
                .append(body)
                .append("</main><div class=\"endpad\">✦</div></body></html>");
        wv.loadDataWithBaseURL("file:///android_asset/", d.toString(), "text/html", "utf-8", null);
        updateStatus();
    }

    /** the data-conds value for now, or null (past the table: nothing hidden) */
    private String condsAttr() {
        if (!page.filtered) return null;
        Calendar.When when = Calendar.forPage(page.daykind, LocalDateTime.now(), prefs.afterNightfall());
        Set<String> c = siddur.calendar().conds(when, prefs.israel());
        if (c == null) return null;
        if (prefs.rubrics()) c.add("rubric");
        return TextUtils.join(" ", c);
    }

    private void afterLoad() {
        loaded = true;
        if (pendingJump != null) { jump(pendingJump); pendingJump = null; }
        else if (pendingFraction > 0) {
            wv.evaluateJavascript("(function(f){var h=document.documentElement.scrollHeight-innerHeight;"
                    + "window.scrollTo(0,Math.round(f*h));})(" + pendingFraction + ")", null);
        }
        pendingFraction = -1;
        ui.postDelayed(trackSection, 300);
    }

    /** filter switch / nightfall switch / a new day: change the attributes, no reload */
    private void applyConds() {
        updateStatus();
        if (!loaded) return;
        String conds = condsAttr();
        String js = "(function(){var r=document.documentElement,el=document.elementFromPoint(innerWidth/2,4),"
                + "t=el?el.getBoundingClientRect().top:0;"
                + "r.dataset.filter='" + (filterOn ? "on" : "off") + "';"
                + (conds == null ? "delete r.dataset.conds;" : "r.dataset.conds='" + conds + "';")
                + "if(el&&el.offsetParent)window.scrollBy(0,el.getBoundingClientRect().top-t);})()";
        loadedConds = conds;
        wv.evaluateJavascript(js, null);
        ui.postDelayed(trackSection, 300);
    }

    private void updateStatus() {
        LocalDateTime now = LocalDateTime.now();
        boolean meal = "meal".equals(page.daykind);
        Calendar.When when = Calendar.forPage(page.daykind, now, prefs.afterNightfall());
        StringBuilder s = new StringBuilder();
        if (when.night) s.append("ליל ").append(HebDate.weekday(when.date).replace("יום ", "")).append(" · ");
        else s.append(HebDate.weekday(when.date)).append(" · ");
        s.append(HebDate.of(when.date));
        Set<String> c = siddur.calendar().conds(when, prefs.israel());
        for (String l : HebDate.labels(c)) s.append(" · ").append(l);
        if (page.filtered) {
            s.append(prefs.israel() ? " · א״י" : " · חו״ל");
            if (c == null) s.append(" · (no calendar for this date: showing everything)");
        }
        status.setText(s);
        filterBtn.setVisibility(page.filtered && c != null ? View.VISIBLE : View.GONE);
        filterBtn.setText(filterOn ? "הצג הכל" : "רק היום");
        nightBtn.setVisibility(page.filtered && meal ? View.VISIBLE : View.GONE);
        nightBtn.setText(prefs.afterNightfall() ? "זמן: לילה" : "זמן: יום");
    }

    // ---------------------------------------------------------------- navigation

    private void pageBy(int dir) {
        wv.evaluateJavascript("window.scrollBy(0," + dir + "*Math.round(innerHeight*0.9))", null);
    }

    private void resize(int delta) {
        int s = Math.max(14, Math.min(60, prefs.size() + delta));
        if (s == prefs.size()) return;
        prefs.setSize(s);
        loadedLook = look();
        wv.evaluateJavascript("(function(){var el=document.elementFromPoint(innerWidth/2,4),"
                + "t=el?el.getBoundingClientRect().top:0;"
                + "document.documentElement.style.setProperty('--size','" + s + "px');"
                + "if(el)window.scrollBy(0,el.getBoundingClientRect().top-t);})()", null);
        Ui.toast(this, "Text size " + s);
    }

    private void jump(String anchor) {
        wv.evaluateJavascript("(function(){var e=document.getElementById('" + anchor + "');"
                + "if(e){if(!e.offsetParent){document.documentElement.dataset.filter='off';}"
                + "e.scrollIntoView(true);}})()", null);
        ui.postDelayed(trackSection, 300);
    }

    private float lastFraction;

    private interface FloatDone { void done(float f); }

    private void readFraction(FloatDone d) {
        if (wv == null) return;
        wv.evaluateJavascript("(function(){var h=document.documentElement.scrollHeight-innerHeight;"
                + "return h>0?scrollY/h:0;})()", v -> {
            float f = 0;
            try { f = Float.parseFloat(v); } catch (Exception ignored) { }
            lastFraction = f;
            d.done(f);
        });
    }

    /** the heading at the top of the screen goes in the title */
    private final Runnable trackSection = () -> {
        if (wv == null || !loaded) return;
        wv.evaluateJavascript("(function(){var hs=document.querySelectorAll('h3.section[id]'),n='';"
                + "for(var i=0;i<hs.length;i++){var h=hs[i];if(!h.offsetParent)continue;"
                + "if(h.getBoundingClientRect().top>innerHeight*0.25)break;n=h.textContent;}"
                + "var h=document.documentElement.scrollHeight-innerHeight;"
                + "return JSON.stringify([n,h>0?scrollY/h:0]);})()", v -> {
            try {
                JSONArray a = new JSONArray(new JSONArray("[" + v + "]").getString(0));
                section = a.getString(0).trim();
                lastFraction = (float) a.getDouble(1);
                title.setText(section.isEmpty() || section.equals(page.he)
                        ? page.he + "  ▾" : page.he + " · " + section + "  ▾");
            } catch (Exception ignored) { }
        });
    };

    private void showSections() {
        if ("tehilim".equals(page.id)) { Tehilim.showIndex(this, this::jump); return; }
        // the sections that are showing right now (hidden ones are for other days)
        wv.evaluateJavascript("(function(){var o=[];document.querySelectorAll('h3.section[id]').forEach(function(h){"
                + "o.push([h.id,h.textContent,!!h.offsetParent]);});return JSON.stringify(o);})()", v -> {
            final List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(new JSONArray("[" + v + "]").getString(0));
                for (int i = 0; i < a.length(); i++) {
                    JSONArray r = a.getJSONArray(i);
                    if (!r.getBoolean(2) && filterOn) continue;
                    ids.add(r.getString(0));
                    names.add(r.getString(1).trim() + (r.getBoolean(2) ? "" : "   (not today)"));
                }
            } catch (Exception ignored) { }
            if (ids.isEmpty()) { Ui.toast(this, "No sections"); return; }
            int cur = Math.max(0, names.indexOf(section));
            Ui.list(this, page.he, names, cur, i -> jump(ids.get(i)));
        });
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Sections…");
        m.getMenu().add(0, 2, 0, "Back to the start");
        if (page.filtered) m.getMenu().add(0, 3, 0, filterOn ? "Show everything" : "Only what's said today");
        m.getMenu().add(0, 4, 0, "Settings");
        m.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1: showSections(); break;
                case 2: wv.evaluateJavascript("window.scrollTo(0,0)", null); break;
                case 3: filterOn = !filterOn; applyConds(); break;
                case 4: startActivity(new Intent(this, SettingsActivity.class)); break;
            }
            return true;
        });
        m.show();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_PAGE_DOWN || code == KeyEvent.KEYCODE_VOLUME_DOWN) { pageBy(1); return true; }
        if (code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_VOLUME_UP) { pageBy(-1); return true; }
        return super.onKeyDown(code, e);
    }
}
