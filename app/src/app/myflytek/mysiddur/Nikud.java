/*
 * My Siddur — an offline e-ink siddur for the myFLYTEK AiNote. MIT.
 */
package app.myflytek.mysiddur;

import java.util.regex.Pattern;

/** Take the cantillation, or all the marks, off the Hebrew. (Marks never occur inside tags.) */
public final class Nikud {
    private Nikud() { }

    /** te'amim U+0591–05AF, plus meteg U+05BD */
    private static final Pattern TEAMIM = Pattern.compile("[֑-ֽ֯]");
    /** every point: te'amim, vowels, dagesh, rafe, shin/sin dots, meteg (keeps maqaf, paseq, sof pasuq) */
    private static final Pattern ALL = Pattern.compile("[֑-ׇֽֿׁׂׅׄ]");

    public static String apply(String html, String mode) {
        if ("vowels".equals(mode)) return TEAMIM.matcher(html).replaceAll("");
        if ("none".equals(mode)) return ALL.matcher(html).replaceAll("");
        return html;
    }
}
