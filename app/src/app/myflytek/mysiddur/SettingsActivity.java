/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

/** The Settings screen (framework preferences — no AndroidX). */
@SuppressWarnings("deprecation")
public final class SettingsActivity extends PreferenceActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        addPreferencesFromResource(R.xml.settings);
        ActionBar ab = getActionBar();
        if (ab != null) ab.setDisplayHomeAsUpEnabled(true);
        Preference about = findPreference("about");
        if (about != null) {
            String v = "?";
            try { v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
            Siddur s = Siddur.get(this);
            about.setTitle("My Siddur " + v);
            about.setSummary("Nusach Ashkenaz, Tehillim and the Torah readings, all offline. Texts from "
                    + "onlinesiddur.com (based on Sefaria's open texts), fetched " + s.fetched
                    + "; day rules and calendar from onlinesiddur.com, valid to " + s.calendar().lastDate()
                    + ". The Name is written ד׳ and א׳. Fonts: Frank Ruehl CLM and Shlomo SemiStam "
                    + "(Culmus project), Noto Serif Hebrew and Cardo (SIL OFL).");
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
