/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Small view helpers (framework widgets only). */
public final class Ui {
    private Ui() { }

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    public static ImageButton icon(Context c, int res, String desc) {
        ImageButton b = new ImageButton(c, null, 0, R.style.BarIcon);
        b.setImageResource(res);
        b.setContentDescription(desc);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setLayoutParams(new ViewGroup.LayoutParams(dp(c, 48), dp(c, 48)));
        return b;
    }

    public static Button barButton(Context c, String text) {
        Button b = new Button(c, null, 0, R.style.BarButton);
        b.setText(text);
        b.setTextSize(18);
        return b;
    }

    /** an outlined button in the status line */
    public static Button chip(Context c, String text) {
        Button b = new Button(c, null, 0, R.style.BarButton);
        b.setText(text);
        b.setTextSize(15);
        b.setBackgroundResource(R.drawable.box);
        b.setMinHeight(dp(c, 40));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 40));
        lp.setMarginStart(dp(c, 8));
        b.setLayoutParams(lp);
        return b;
    }

    public static ImageButton pageButton(Context c, int res, String desc) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(res);
        b.setContentDescription(desc);
        b.setBackgroundResource(R.drawable.box);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = dp(c, 12);
        b.setPadding(p, p, p, p);
        b.setLayoutParams(new ViewGroup.LayoutParams(dp(c, 60), dp(c, 60)));
        return b;
    }

    public static View rule(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.BLACK);
        v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    public interface Pick { void picked(int i); }

    /** A list to pick from, right-to-left, scrolled to {@code current}. Rows starting with "—" are headers. */
    public static void list(Activity a, String title, final List<String> items, int current, final Pick pick) {
        final AlertDialog[] d = new AlertDialog[1];
        ListView lv = new ListView(a);
        lv.setDivider(null);
        lv.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, items) {
            @Override public View getView(int pos, View recycled, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, recycled, parent);
                String s = items.get(pos);
                boolean header = s.startsWith("—");
                t.setTextColor(Color.BLACK);
                t.setTextSize(header ? 15 : 19);
                t.setTypeface(null, header || pos == current ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                t.setGravity(header ? Gravity.CENTER : Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                t.setTextDirection(View.TEXT_DIRECTION_RTL);
                t.setBackgroundColor(header ? 0xFFEEEEEE : pos == current ? 0xFFDDDDDD : Color.WHITE);
                t.setMinHeight(dp(a, header ? 36 : 54));
                return t;
            }
            @Override public boolean isEnabled(int pos) { return !items.get(pos).startsWith("—"); }
        });
        lv.setOnItemClickListener((p, v, i, id) -> { d[0].dismiss(); pick.picked(i); });
        if (current > 2) lv.setSelection(current - 2);
        d[0] = new AlertDialog.Builder(a).setTitle(title).setView(lv).setNegativeButton("Close", null).create();
        if (d[0].getWindow() != null) d[0].getWindow().setWindowAnimations(0);     // e-ink: no fade
        d[0].show();
    }
}
