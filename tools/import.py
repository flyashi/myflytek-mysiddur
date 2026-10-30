#!/usr/bin/env python3
"""source/*.html (onlinesiddur.com pages) -> content/ (what the app bundles).

For each page this keeps only the reading body, the page's day-condition CSS rules
and its precomputed day table (window.__CONDS__), and writes:
  content/pages/<id>.html   <style>rules</style> + the article's inner HTML
  content/pages/<id>.json   titles, page kind, section list, day table
  content/index.json        the home-screen list
The divine names are replaced on the way: YHVH (any vowels/cantillation, with its
prefix letters kept), the printed abbreviations יְיָ and ה' -> ד' ; the divine Adonai (kamatz under the nun) -> א'.
The merged day table also gets a few Ashkenaz corrections (see fix_calendar).
"""
import datetime as dt
import html as htmllib
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "source")
OUT = os.path.join(ROOT, "content")

# (id, Hebrew, English, group)
PAGES = [
    ("shacharit", "שחרית", "Shacharit", "daily"),
    ("mincha", "מנחה", "Mincha", "daily"),
    ("maariv", "ערבית", "Maariv", "daily"),
    ("brachot", "ברכות", "Blessings", "daily"),
    ("birkat", "ברכת המזון", "Birkat HaMazon", "daily"),
    ("bracha", "ברכה מעין שלוש", "Bracha Achrona", "daily"),
    ("ksham", "קריאת שמע על המיטה", "Kriat Shema al HaMita", "daily"),
    ("kadish", "קדיש", "Kaddish", "daily"),
    ("derech", "תפילת הדרך", "Tefilat HaDerech", "daily"),
    ("tehilim", "תהילים", "Tehillim", "tehilim"),
    ("musaf", "מוסף לראש חודש", "Musaf Rosh Chodesh", "times"),
    ("musafmoed", "מוסף לחול המועד", "Musaf Chol HaMoed", "times"),
    ("hoshanot", "הושענות", "Hoshanot", "times"),
    ("nerot", "הדלקת נרות חנוכה", "Chanukah Candles", "times"),
    ("megillah", "מגילת אסתר", "Megillat Esther", "times"),
    ("omer", "ספירת העומר", "Sefirat HaOmer", "times"),
    ("levana", "ברכת הלבנה", "Birkat HaLevana", "times"),
    ("slichot", "סליחות", "Selichot", "times"),
    ("tishabav", "תשעה באב", "Tisha B'Av", "times"),
    ("torah", "קריאת התורה", "Torah Readings", "times"),
    ("milah", "ברית מילה", "Brit Milah", "other"),
    ("klali", "תיקון הכללי", "Tikkun HaKlali", "other"),
    ("chatzot", "תיקון חצות", "Tikkun Chatzot", "other"),
]

# ---------------------------------------------------------------- divine names

# vowels, cantillation, dagesh, shin/sin dots, meteg, ZWJ/ZWNJ/LRM/RLM/CGJ — everything that
# can sit on a letter. NOT maqaf (05BE), paseq (05C0), sof pasuq (05C3), nun hafukha (05C6).
M = "[֑-ׇֽֿׁׂׅׄ‌‎‏‍͏]"
LET = "[א-ת]"
WORD_START = "(?<![א-ת֑-ׇֽֿׁׂׅׄ‌‎‏‍͏])"
PREFIX = "((?:[ובכלמשהד]" + M + "*){0,3})"
END = "(?!" + LET + "|" + M + ")"
YHVH = re.compile(WORD_START + PREFIX + "י" + M + "*ה" + M + "*ו" + M + "*ה" + M + "*" + END)
YY = re.compile(WORD_START + PREFIX + "י" + M + "*י(" + M + "*)" + END)   # יְיָ, the printed abbreviation
ADNY = re.compile(WORD_START + PREFIX + "א" + M + "*ד" + M + "*נ(" + M + "*)י" + M + "*" + END)
KAMATZ = "ָ"


def _adny(m):
    nun = m.group(2)
    word = m.group(0)
    unvocalized = not re.search(M, word)
    if KAMATZ in nun or unvocalized:
        return m.group(1) + "א'"
    return word          # אֲדֹנִי "my master", אֲדֹנֵי… — not the Name


def _yy(m):
    # יְיָ / לַייָ: kamatz on the last yud (בִּכְיִי "my weeping" has a hiriq);
    # unvocalized יי only bare or with one prefix letter
    word = m.group(0)
    if KAMATZ in m.group(2) or (not re.search(M, word) and len(m.group(1)) <= 1):
        return m.group(1) + "ד'"
    return word


# ה' / ה׳ as the Name ("ברוך אתה ה'", "עולה לה'"), but not the letter or the numeral 5
# ("אות ה'", "פרק ה׳", "יום ה׳", "תהילים ה׳")
HE = re.compile(WORD_START + PREFIX + "ה" + M + "*['׳](?![א-ת])")
NOT_NAME_BEFORE = {"אות", "פרק", "יום", "תהילים", "מזמור", "סימן", "פסוק", "חלק", "שער", "דף", "ליל"}


def _he(m, s):
    before = re.findall("[א-ת]+", strip_marks(re.sub("<[^>]*>", " ", s[max(0, m.start() - 40):m.start()])))
    if not m.group(1) and before and (before[-1] in NOT_NAME_BEFORE or before[-1][1:] in NOT_NAME_BEFORE
                                      and before[-1][0] == "ו"):
        return m.group(0)
    return m.group(1) + "ד'"


# ---- the other Names: אֱלֹהִים → אֱלֹקִים, אֵל → קֵל, יָהּ → קָהּ, שַׁ-דַּי, צְבָ-אוֹת
# Profane uses (other gods, judges, "these", Beit-El…) are left as they are; see _profane.

HOLAM = "ֹֺ"
MAPPIQ = "ּ"
VOWEL = "[ְ-ׇֻ]"
ELOHIM = re.compile(WORD_START + PREFIX + "א(" + M + "*)ל(" + M + "*)(ו" + M + "*)?ה(" + M + "*)((?:[ינוךכםהן]" + M + "*)*)" + END)
ELOHIM_SUFFIX = {"", "י", "ים", "ינו", "יך", "יכם", "יכן", "יו", "יה", "יהם", "יהן"}
ELAHA = re.compile(WORD_START + PREFIX + "א(" + M + "*)ל(" + M + "*)ה(" + M + "*)א(" + M + "*)" + END)   # Aramaic אֱלָהָא
EL = re.compile(WORD_START + PREFIX + "א(" + M + "*)ל(" + M + "*)" + END)
YAH = re.compile(WORD_START + PREFIX + "י(" + M + "*)ה(" + M + "*)" + END)
SHADDAI = re.compile(WORD_START + PREFIX + "ש(" + M + "*)ד(" + M + "*)י(" + M + "*)" + END)
TZVAOT = re.compile(WORD_START + PREFIX + "צ(" + M + "*)ב(" + M + "*)(א" + M + "*ו" + M + "*ת" + M + "*)" + END)


def vowels(marks):
    return set(re.findall(VOWEL, marks))


def real_prefix(p):
    """ו only first, ש only first or after ו; a prefix letter never carries a holam,
    hiriq or qubuts (so שׁוֹאֵל, הוֹאֵל, שְׁמוּאֵל, שִׁבְיָהּ are not a prefix + a Name)."""
    letters = re.sub(M, "", p)
    if "ו" in letters[1:] or "ש" in letters.lstrip("ו")[1:] or re.search("[" + HOLAM + "\u05B4\u05BB]", p):
        return False                                     # no holam / hiriq / qubuts on a prefix
    return not re.search("ש" + M + "*\u05B0", p)         # שֶׁ, never שְׁ (שִׁבְיָהּ, שְׁמוּאֵל)


def context(s, start, end):
    """The bare words just before and just after s[start:end] (tags, marks, maqaf dropped)."""
    def words(t):
        t = re.sub(r"<[^>]*>", " ", t).replace("־", " ")
        return re.findall("[א-ת]+", strip_marks(t))
    return words(s[max(0, start - 120):start]), words(s[end:end + 120])


# other gods, judges and "gods" (Ps 82, 86:8, 95-97, 135:5, 136:2, 138:1, Dt 10:17)
GOD_PROFANE_NEXT = {"אחרים", "אחר", "חדשים", "מסכה", "אתם"}
GOD_PROFANE_PREV = {"כל", "מכל", "וכל", "בכל", "אחרי", "פסילי", "ופסילי", "נגד", "בקרב", "כמוך", "איןכמוך"}
EL_PROFANE_NEXT = {"זר", "נכר", "אחר", "הנקבר"}
EL_PROFANE_PREV = {"הארצת", "הארצות", "הגוים", "בית", "לבית", "בבית", "ובית"}   # הָאֵל "these"; Beit-El
NAME_BEFORE_TZVAOT = {"ד", "לד", "וד", "בד", "א", "אלהי", "אלהים", "אלקי", "אלקים", "ואלהי", "ואלקי"}


def _profane(kind, word, prev, nxt):
    p = prev[-1] if prev else ""
    n = nxt[0] if nxt else ""
    if kind == "god":
        if n in GOD_PROFANE_NEXT or p in GOD_PROFANE_PREV:
            return True
        if word == "ובאלהיהם" or (word == "לאלהיהם" and p == "וזבחו"):   # Nu 33:4, Ex 34:15
            return True
        if word in ("האלהים", "האלקים") and p in ("אלהי", "לאלהי", "אלקי", "לאלקי"):   # God of gods
            return True
        if nxt[:2] == ["אשר", "לא"] and nxt[2:3] and nxt[2].startswith("ידע"):   # gods "they did not know"
            return True
    if kind == "el":
        if n in EL_PROFANE_NEXT or p in EL_PROFANE_PREV or (word == "לאל" and n.startswith("יד")):   # לְאֵל יָדִי = power
            return True
    return False


def _elohim(m, s, stats):
    pre, aleph, lamed, vav, he, suf = m.groups()
    word = m.group(0)
    bare = strip_marks(word)
    unvocalized = not re.search(VOWEL, word)
    ok = (real_prefix(pre)
          and strip_marks(suf) in ELOHIM_SUFFIX
          and vowels(aleph) <= {"ֱ", "ֶ"} and (vowels(aleph) or pre or unvocalized)
          and vowels(lamed) <= set(HOLAM) and MAPPIQ not in lamed
          and (not vav or re.search("[" + HOLAM + "]", vav) or unvocalized)
          and (strip_marks(suf) or MAPPIQ in he))       # bare אלה only as אֱלוֹהַּ / אֱלֹהַּ, never אֵלֶּה
    if not ok:
        return word
    if _profane("god", bare, *context(s, m.start(), m.end())):
        stats["kept"].append(word)
        return word
    stats["elohim"] += 1
    return pre + "א" + aleph + "ל" + lamed + (vav or "") + "ק" + he + suf


def _elaha(m, s, stats):
    pre, aleph, lamed, he, a = m.groups()
    if not real_prefix(pre) or "ָ" not in lamed or "ָ" not in he:
        return m.group(0)
    stats["elohim"] += 1
    return pre + "א" + aleph + "ל" + lamed + "ק" + he + "א" + a


def _el(m, s, stats):
    pre, aleph, lamed = m.groups()
    if not real_prefix(pre) or "ֵ" not in aleph or vowels(lamed):
        return m.group(0)
    if _profane("el", strip_marks(m.group(0)), *context(s, m.start(), m.end())):
        stats["kept"].append(m.group(0))
        return m.group(0)
    stats["el"] += 1
    return pre + "ק" + aleph + "ל" + lamed


def _yah(m, s, stats):
    pre, yud, he = m.groups()
    if (not real_prefix(pre) or "ָ" not in yud or MAPPIQ not in he
            or pre and not vowels(re.split("[א-ת]", pre)[-1]) <= {"ְ"}):   # בְּיָהּ, not שְׁבִיָּה
        return m.group(0)
    prev, _ = context(s, m.start(), m.end())
    if prev and prev[-1] == "הללו":                     # הַלְלוּ־יָהּ stays, like הַלְלוּיָהּ
        return m.group(0)
    stats["yah"] += 1
    return pre + "ק" + yud + "ה" + he


def _shaddai(m, s, stats):
    pre, shin, dalet, yud = m.groups()
    if (not real_prefix(pre) or "ׁ" not in shin or not vowels(shin) & {"ַ", "ָ"}
            or MAPPIQ not in dalet or not vowels(dalet) & {"ַ", "ָ"}):
        return m.group(0)                                # שָׂדַי "field", שְׁדֵי, לְשַׁדִּי
    stats["shaddai"] += 1
    return pre + "ש" + shin + "-ד" + dalet + "י" + yud


def _tzvaot(m, s, stats):
    pre, tz, bet, rest = m.groups()
    prev, _ = context(s, m.start(), m.end())
    if pre or "ְ" not in tz or not prev or prev[-1] not in NAME_BEFORE_TZVAOT:
        return m.group(0)                                # הַצְּבָאוֹת, הֲמוֹן צְבָאוֹת, מַלְכֵי צְבָאוֹת
    stats["tzvaot"] += 1
    return "צ" + tz + "ב" + bet + "-" + rest


def replace_names(s, stats):
    for k in ("elohim", "el", "yah", "shaddai", "tzvaot"):
        stats.setdefault(k, 0)
    stats.setdefault("kept", [])
    s, n1 = YHVH.subn(lambda m: m.group(1) + "ד'", s)
    s, n3 = YY.subn(_yy, s)
    s, n2 = ADNY.subn(_adny, s)
    src = s
    s, n4 = HE.subn(lambda m: _he(m, src), s)
    n4 -= len(HE.findall(s))          # the ones left alone (letter / numeral) are still there
    stats["yhvh"] += n1 + n3 + n4
    stats["adny_seen"] += n2
    for pat, fn in ((ELOHIM, _elohim), (ELAHA, _elaha), (EL, _el), (YAH, _yah), (SHADDAI, _shaddai), (TZVAOT, _tzvaot)):
        src = s
        s = pat.sub(lambda m: fn(m, src, stats), s)
    return s


def strip_marks(s):
    return re.sub("[֑-ׇ‌‍͏]", "", s)


# ---------------------------------------------------------------- tehillim extras

def gematria(n):
    """1..30 in Hebrew letters with geresh/gershayim (15 = ט״ו)."""
    ones, tens = "אבגדהוזחט", "יכל"
    s = {15: "טו", 16: "טז"}.get(n) or ((tens[n // 10 - 1] if n >= 10 else "") + (ones[n % 10 - 1] if n % 10 else ""))
    return s + "׳" if len(s) == 1 else s[:-1] + "״" + s[-1]


def tehilim_fixups(art):
    """Give each day-of-month marker an id, and add the missing day 26 (Psalm 119:97)."""
    n = [0]

    def day_id(m):
        num = re.search(r"יום (\d+) לחודש", m.group(0)).group(1)
        return ('<div class="tehilim-day" id="day-%s">' % num
                + m.group(1).replace("יום %s לחודש" % num, "יום %s לחודש" % gematria(int(num))))
    art = re.sub(r'<div class="tehilim-day">(.*?</div>)', day_id, art, flags=re.S)
    j = art.find('id="sec-118"')
    p0 = art.find("<p", j)
    p1 = art.find("</p>", p0)
    para = art[p0:p1]
    cut, count = -1, 0
    for i, ch in enumerate(para):
        if ch == "׃":
            count += 1
            if count == 96:
                cut = i + 1
                break
    if cut < 0:
        sys.exit("tehilim: Psalm 119 has no verse 96")
    rest = para[cut:].lstrip()
    open_tag = re.match(r"<p[^>]*>", para).group(0)
    marker = ('</p>\n<div class="tehilim-day" id="day-26"><span data-lang-en>Day 26</span>'
              '<span data-lang-he lang="he">יום כ״ו לחודש</span></div>\n' + open_tag)
    art = art[:p0] + para[:cut] + marker + rest + art[p1:]
    n[0] += 1
    return art


# ---------------------------------------------------------------- day tables

# Each page carries the site's precomputed table: one row per civil date, for Israel
# ("il") and outside ("gl"), for the day and/or the night, as a bitset over the page's
# own condition tokens (URL-safe base64, 6 bits a char, low bit first). The tables
# agree wherever they share a token, so they merge into one calendar.json for all pages.
ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"


def decode_row(T, rows, i):
    w, out = T["w"], set()
    for k in range(w):
        code = ALPHA.index(rows[i * w + k])
        for j in range(6):
            if (code >> j) & 1 and k * 6 + j < len(T["n"]):
                out.add(T["n"][k * 6 + j])
    return out


def merge_table(cal, T, pid):
    if "e" in cal and (cal["e"] != T["e"] or cal["d"] != T["d"]):
        sys.exit("%s: day table has a different range" % pid)
    cal["e"], cal["d"] = T["e"], T["d"]
    sets = cal.setdefault("sets", {})
    for loc in ("il", "gl"):
        for kind, rows in T[loc].items():
            days = sets.setdefault((loc, kind), [dict() for _ in range(T["d"])])
            for i in range(T["d"]):
                on = decode_row(T, rows, i)
                for t in T["n"]:
                    v = t in on
                    if days[i].get(t, v) != v:
                        sys.exit("%s: token %s disagrees with another page on day %d" % (pid, t, i))
                    days[i][t] = v


# A little Hebrew calendar (Calendrical Calculations arithmetic) for the fixes below.
HEBREW_EPOCH = -1373427            # fixed day (proleptic Gregorian ordinal) of 1 Tishri AM 1


def heb_leap(y):
    return (7 * y + 1) % 19 < 7


def _heb_elapsed(y):
    months = (235 * y - 234) // 19
    parts = 12084 + 13753 * months
    day = 29 * months + parts // 25920
    return day + 1 if (3 * (day + 1)) % 7 < 3 else day


def heb_new_year(y):
    ny0, ny1, ny2 = _heb_elapsed(y - 1), _heb_elapsed(y), _heb_elapsed(y + 1)
    corr = 2 if ny2 - ny1 == 356 else 1 if ny1 - ny0 == 382 else 0
    return HEBREW_EPOCH + ny1 + corr


def heb_months(y):
    """[(name, length)] from Tishri; Adar is ADAR1 + ADAR2 in a leap year."""
    n = heb_new_year(y + 1) - heb_new_year(y)
    m = [("TISHRI", 30), ("CHESHVAN", 30 if n % 10 == 5 else 29), ("KISLEV", 29 if n % 10 == 3 else 30),
         ("TEVET", 29), ("SHEVAT", 30)]
    m += [("ADAR1", 30), ("ADAR2", 29)] if heb_leap(y) else [("ADAR", 29)]
    return m + [("NISAN", 30), ("IYAR", 29), ("SIVAN", 30), ("TAMUZ", 29), ("AV", 30), ("ELUL", 29)]


def heb_date(d):
    """civil date -> (year, month name, day)"""
    fixed = d.toordinal()
    y = (fixed - HEBREW_EPOCH) // 366 + 1
    while heb_new_year(y + 1) <= fixed:
        y += 1
    off = fixed - heb_new_year(y)
    for name, length in heb_months(y):
        if off < length:
            return y, name, off + 1
        off -= length
    raise ValueError(d)


def heb_to_civil(y, month, day):
    fixed = heb_new_year(y)
    for name, length in heb_months(y):
        if name == month:
            return dt.date.fromordinal(fixed + day - 1)
        fixed += length
    raise KeyError(month)


def table_fixes(d):
    """Ashkenaz corrections to onlinesiddur.com's table for one civil date: {token: value}.
    Applied to whichever day/night rows carry the token, in Israel and outside."""
    y, m, day = heb_date(d)
    fix = {}
    # Chanukah: the site has full Hallel on 24 Kislev and no Tachanun the day after the 8th day
    if m == "KISLEV" and day == 24:
        fix["hallel-full"] = False
    if m in ("KISLEV", "TEVET") and (d - heb_to_civil(y, "KISLEV", 25)).days == 8 and d.weekday() != 5:
        fix["tachanun-day"] = fix["lamenatzeach"] = True
        fix["tachanun-mincha"] = d.weekday() != 4          # Friday Mincha never has it
    # no Tachanun (morning or Mincha): Shushan Purim Katan, Pesach Sheni, Sivan 1-12 (also after
    # Isru Chag, until the 12th), and from Yom Kippur to the end of Tishri
    if ((m == "ADAR1" and day == 15) or (m == "IYAR" and day == 14) or (m == "SIVAN" and day <= 12)
            or (m == "TISHRI" and 11 <= day <= 29)):
        fix["tachanun-day"] = fix["tachanun-mincha"] = False
    return fix


def fix_calendar(cal):
    """Lamenatzeach gets its own token (the site ties it to Tachanun; it keeps the site's days),
    then the table_fixes corrections."""
    epoch = dt.date(cal["e"][0], cal["e"][1] + 1, cal["e"][2])
    changed = 0
    for (loc, kind), days in cal["sets"].items():
        for i, row in enumerate(days):
            if kind == "day" and "tachanun-day" in row:
                row["lamenatzeach"] = row["tachanun-day"]
            for tok, v in table_fixes(epoch + dt.timedelta(i)).items():
                if tok in row and row[tok] != v:
                    row[tok] = v
                    changed += 1
    print("calendar fixes: %d cells changed" % changed)


LAMENATZEACH = re.compile(r'<div data-when="tachanun-day">(\s*<h3 class="section"><span>([^<]*)</span>)')


def lamenatzeach_fixup(art, rules):
    """Shacharit: give the Lamenatzeach block its own condition (see fix_calendar)."""
    n = 0

    def sub(m):
        nonlocal n
        if strip_marks(m.group(2)) != "למנצח":
            return m.group(0)
        n += 1
        return '<div data-when="lamenatzeach">' + m.group(1)
    art = LAMENATZEACH.sub(sub, art)
    if n != 1:
        sys.exit("shacharit: expected one Lamenatzeach block under tachanun-day, found %d" % n)
    rules += ("\nhtml[data-filter='on'][data-conds]:not([data-conds~='lamenatzeach']) "
              "[data-when='lamenatzeach']{display:none;}")
    return art, rules


def write_calendar(cal):
    tokens = sorted({t for days in cal["sets"].values() for d in days for t in d})
    w = (len(tokens) + 5) // 6
    out = {"e": cal["e"], "d": cal["d"], "n": tokens, "w": w, "il": {}, "gl": {}}
    for (loc, kind), days in sorted(cal["sets"].items()):
        rows = []
        for d in days:
            for k in range(w):
                code = 0
                for j in range(6):
                    idx = k * 6 + j
                    if idx < len(tokens) and d.get(tokens[idx]):
                        code |= 1 << j
                rows.append(ALPHA[code])
        out[loc][kind] = "".join(rows)
    with open(os.path.join(OUT, "calendar.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, separators=(",", ":"))
    print("calendar.json: %d tokens, %d days from %s" % (len(tokens), cal["d"], cal["e"]))


# ---------------------------------------------------------------- main

def main():
    os.makedirs(os.path.join(OUT, "pages"), exist_ok=True)
    index = []
    calendar = {}
    total = {"yhvh": 0, "adny_seen": 0}
    leftovers = {}
    for pid, he, en, group in PAGES:
        path = os.path.join(SRC, pid + ".html")
        h = open(path, encoding="utf-8").read()
        # the reading = everything between the page header and the end ornament (an
        # <article class="reading-body"> on most pages, the 3-column fork layout on Bracha)
        a = h.index("</header>", h.index('class="reading-head"')) + len("</header>")
        b = h.index('<div class="end-orn">', a)
        art = re.sub(r"<template\b.*?</template>", "", h[a:b], flags=re.S).strip()   # no-nikud copies
        dk = re.search(r'<html[^>]*data-daykind="([^"]*)"', h)
        daykind = dk.group(1) if dk else ""
        rules = re.search(r"<style data-when-rules>(.*?)</style>", h, re.S)
        rules = rules.group(1).strip() if rules else ""
        conds = re.search(r"window\.__CONDS__\s*=\s*(\{.*?\});", h, re.S)
        conds = json.loads(conds.group(1)) if conds else None

        # section list: the jump panel says which sections are day-dependent
        when = {}
        for m in re.finditer(r'<li( data-when="([^"]*)")?><button[^>]*data-jump="(sec-\d+)"', h):
            if m.group(2):
                when[m.group(3)] = m.group(2)
        stats = {"yhvh": 0, "adny_seen": 0}
        art = replace_names(art, stats)
        if pid == "tehilim":
            art = tehilim_fixups(art)
        if pid == "shacharit":
            art, rules = lamenatzeach_fixup(art, rules)
        sections = []
        for m in re.finditer(r'<h3 class="section" id="(sec-\d+)"><span>(.*?)</span>', art):
            name = htmllib.unescape(re.sub(r"<[^>]+>", "", m.group(2))).strip()
            s = {"id": m.group(1), "he": name}
            if m.group(1) in when:
                s["when"] = when[m.group(1)]
            sections.append(s)

        # anything that still spells a Name?
        for w in re.findall("[א-ת֑-ׇ‌‍͏]+", art):
            bare = strip_marks(w)
            if re.search("יהוה", bare) or re.search("י" + M + "*י" + M + "*\u05B8" + M + "*$", w) or (re.search("(^|[ובכלמשהד])אדני$", bare) and KAMATZ in w):
                leftovers[w] = leftovers.get(w, 0) + 1

        frag = ("<style>" + rules + "</style>\n" if rules else "") + art + "\n"
        with open(os.path.join(OUT, "pages", pid + ".html"), "w", encoding="utf-8") as f:
            f.write(frag)
        meta = {"id": pid, "he": he, "en": en, "daykind": daykind, "sections": sections,
                "filtered": bool(conds)}
        if conds:
            merge_table(calendar, conds, pid)
        with open(os.path.join(OUT, "pages", pid + ".json"), "w", encoding="utf-8") as f:
            json.dump(meta, f, ensure_ascii=False, separators=(",", ":"))
        index.append({"id": pid, "he": he, "en": en, "group": group, "daykind": daykind,
                      "filtered": bool(conds)})
        for k, v in stats.items():
            total[k] = total.get(k, type(v)()) + v
        print("%-10s %-22s %7d bytes  %3d sections  %s  YHVH x%d" % (
            pid, en, len(frag.encode()), len(sections),
            ("table %s+%dd" % (conds["e"], conds["d"])) if conds else "no table", stats["yhvh"]))

    fix_calendar(calendar)
    write_calendar(calendar)
    fetched = ""
    try:
        fetched = open(os.path.join(SRC, "fetched.txt")).read().strip()
    except OSError:
        pass
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump({"fetched": fetched, "pages": index}, f, ensure_ascii=False, indent=1)
    print("YHVH replaced: %d; Adonai-shaped words seen: %d" % (total["yhvh"], total["adny_seen"]))
    print("Elohim -> ק: %d; El: %d; Yah: %d; Shaddai: %d; Tzvaot: %d; profane uses kept: %d" % (
        total["elohim"], total["el"], total["yah"], total["shaddai"], total["tzvaot"], len(total["kept"])))
    if leftovers:
        print("LEFTOVER names:", leftovers)
        sys.exit(1)


if __name__ == "__main__":
    main()
