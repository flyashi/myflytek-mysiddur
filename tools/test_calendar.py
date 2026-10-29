#!/usr/bin/env python3
"""Cheap checks of content/calendar.json, the Hebrew-date arithmetic and the Name replacement.

    tools/test_calendar.py        (no network, no Android; build.sh runs it)

Known dates are from the printed luach (Ashkenaz). "N" rows are the night that *starts* the
civil date's Hebrew day (the evening before), "D" rows the day itself — as the app reads them.
"""
import datetime as dt
import importlib.util
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location("siddur_import", os.path.join(ROOT, "tools", "import.py"))
imp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(imp)

CAL = json.load(open(os.path.join(ROOT, "content", "calendar.json"), encoding="utf-8"))
EPOCH = dt.date(CAL["e"][0], CAL["e"][1] + 1, CAL["e"][2])
D = dt.date
fails = []


def check(ok, what):
    if not ok:
        fails.append(what)


def conds(d, il, night):
    i = (d - EPOCH).days
    assert 0 <= i < CAL["d"], d
    rows, w, out = CAL["il" if il else "gl"]["night" if night else "day"], CAL["w"], set()
    for k in range(w):
        code = imp.ALPHA.index(rows[i * w + k])
        for j in range(6):
            if (code >> j) & 1 and k * 6 + j < len(CAL["n"]):
                out.add(CAL["n"][k * 6 + j])
    return out


def has(d, tok, il=False, night=False, want=True):
    got = tok in conds(d, il, night)
    check(got == want, "%s %s %s %s: want %s=%s" % (d, d.strftime("%a"), "IL" if il else "GL",
                                                     "N" if night else "D", tok, want))


def both(d, tok, want=True, night=False):
    has(d, tok, False, night, want)
    has(d, tok, True, night, want)


# ---------------------------------------------------------------- Hebrew dates
for civil, heb in [(D(2026, 10, 29), (5787, "CHESHVAN", 18)), (D(2026, 12, 5), (5787, "KISLEV", 25)),
                   (D(2027, 2, 21), (5787, "ADAR1", 14)), (D(2027, 3, 23), (5787, "ADAR2", 14)),
                   (D(2027, 4, 22), (5787, "NISAN", 15)), (D(2027, 10, 2), (5788, "TISHRI", 1)),
                   (D(2028, 3, 12), (5788, "ADAR", 14)), (D(2029, 7, 22), (5789, "AV", 10)),
                   (D(2025, 9, 23), (5786, "TISHRI", 1)), (D(2000, 1, 1), (5760, "TEVET", 23))]:
    check(imp.heb_date(civil) == heb, "heb_date(%s) = %s, want %s" % (civil, imp.heb_date(civil), heb))
    check(imp.heb_to_civil(*heb) == civil, "heb_to_civil%s != %s" % (heb, civil))
d = EPOCH
while d < EPOCH + dt.timedelta(CAL["d"]):            # round trip over the whole table
    if imp.heb_to_civil(*imp.heb_date(d)) != d:
        check(False, "round trip %s" % d)
    d += dt.timedelta(1)

# ---------------------------------------------------------------- the table (site rules we rely on)
both(D(2026, 12, 5), "tal-umatar", night=True)                  # diaspora: from the evening of Dec 4 ...
has(D(2026, 12, 4), "tal-umatar", want=False)
has(D(2027, 12, 6), "tal-umatar", night=True)                   # ... Dec 5 before a civil leap year
has(D(2027, 12, 5), "tal-umatar", night=True, want=False)
has(D(2027, 11, 7), "tal-umatar", il=True, night=True)          # Israel: from Maariv of 7 Cheshvan
has(D(2027, 11, 6), "tal-umatar", il=True, night=True, want=False)
both(D(2027, 10, 23), "geshem")                                 # Shemini Atzeret ... to 14 Nisan
both(D(2028, 4, 10), "geshem")
both(D(2028, 4, 11), "geshem", want=False)
both(D(2027, 4, 23), "omer-1", night=True)                      # counted the night of 16 Nisan
both(D(2027, 4, 22), "omer", night=True, want=False)
both(D(2027, 5, 25), "omer-33", night=True)
both(D(2026, 11, 10), "rosh-chodesh")                           # 30 Cheshvan + 1 Kislev
both(D(2026, 11, 11), "rosh-chodesh")
both(D(2027, 1, 9), "rosh-chodesh")                             # 1 Shevat: one day
both(D(2027, 1, 8), "rosh-chodesh", want=False)
both(D(2026, 12, 5), "chanukah-1", night=True)
both(D(2026, 12, 12), "chanukah")                               # 2 Tevet, Kislev full: 8th day
both(D(2026, 12, 13), "chanukah", want=False)
both(D(2029, 12, 9), "chanukah")                                # 3 Tevet, Kislev short: 8th day
both(D(2027, 3, 23), "purim")                                   # Adar II in a leap year ...
both(D(2027, 2, 21), "purim", want=False)                       # ... not Purim Katan
both(D(2028, 3, 9), "fast-day")                                 # Ta'anit Esther on Thursday
both(D(2029, 7, 22), "tisha-bav")                               # 9 Av on Shabbat -> Sunday
both(D(2029, 7, 21), "tisha-bav", want=False)
both(D(2027, 9, 12), "ayt", want=False)
both(D(2027, 10, 11), "ayt")                                    # Yom Kippur
has(D(2027, 4, 23), "yom-tov", il=False)                        # second day outside Israel
has(D(2027, 4, 23), "yom-tov", il=True, want=False)
has(D(2027, 4, 23), "chol-hamoed", il=True)
has(D(2027, 4, 25), "motzaei-shabbat", night=True)

# ---------------------------------------------------------------- fixes (fix_calendar)
both(D(2026, 12, 4), "hallel-full", want=False)                 # 24 Kislev is not Chanukah
both(D(2026, 12, 13), "tachanun-day")                           # 3 Tevet, the day after Chanukah
both(D(2026, 12, 13), "tachanun-mincha")
both(D(2029, 12, 10), "tachanun-day")                           # 4 Tevet after a short Kislev
both(D(2027, 2, 22), "tachanun-day", want=False)                # Shushan Purim Katan
both(D(2027, 5, 21), "tachanun-day", want=False)                # Pesach Sheni
both(D(2028, 5, 10), "tachanun-mincha", want=False)
both(D(2028, 5, 9), "tachanun-mincha")                          # Mincha before Pesach Sheni: said
for day in range(9, 13):
    both(D(2027, 6, 5 + day), "tachanun-day", want=False)       # 9-12 Sivan 5787
both(D(2027, 6, 18), "tachanun-day")                            # 13 Sivan
both(D(2027, 10, 26), "tachanun-day", want=False)               # 25 Tishri 5788
both(D(2027, 10, 26), "tachanun-day", want=False, night=True)   # (Tikkun Chatzot's night row)
both(D(2027, 11, 2), "tachanun-day")                            # 2 Cheshvan
both(D(2027, 10, 26), "lamenatzeach")                           # Lamenatzeach still said then
both(D(2026, 12, 13), "lamenatzeach")
both(D(2026, 12, 12), "lamenatzeach", want=False)

# sweep: the corrected rules hold every year of the table
d = EPOCH
while d < EPOCH + dt.timedelta(CAL["d"]):
    y, m, day = imp.heb_date(d)
    for il in (False, True):
        c = conds(d, il, False)
        omit = (m == "ADAR1" and day in (14, 15)) or (m == "IYAR" and day in (14, 18)) or \
               (m == "SIVAN" and day <= 12) or (m == "TISHRI" and (day <= 2 or day >= 9)) or m == "NISAN"
        if omit:
            check("tachanun-day" not in c and "tachanun-mincha" not in c, "%s tachanun on %d %s" % (d, day, m))
        if m == "KISLEV" and day == 24:
            check("hallel-full" not in c, "%s hallel on 24 Kislev" % d)
        if m in ("KISLEV", "TEVET") and (d - imp.heb_to_civil(y, "KISLEV", 25)).days == 8 and d.weekday() != 5:
            check("tachanun-day" in c, "%s no tachanun the day after Chanukah" % d)
    d += dt.timedelta(1)

# ---------------------------------------------------------------- the Name
def names(s):
    return imp.replace_names(s, {"yhvh": 0, "adny_seen": 0})


for src, want in [("בָּרוּךְ אַתָּה יְהֹוָה", "בָּרוּךְ אַתָּה ד'"), ("לַיהוָה", "לַד'"), ("יְיָ אֱלֹהֵינוּ", "ד' אֱלֹהֵינוּ"),
                  ("בִּכְיִי", "בִּכְיִי"), ("אֲדֹנָי שְׂפָתַי", "א' שְׂפָתַי"), ("אֲדֹנִי הַמֶּלֶךְ", "אֲדֹנִי הַמֶּלֶךְ"),
                  ("וַאדֹנָי", "וַא'"), ("עוֹלָה לַה'.", "עוֹלָה לַד'."), ("ברוך אתה ה', שומע", "ברוך אתה ד', שומע"),
                  ("<span>ברוך ה׳</span>", "<span>ברוך ד'</span>"), ("<span>פרק ה׳</span>", "<span>פרק ה׳</span>"),
                  ("יום ה׳ לחודש", "יום ה׳ לחודש"), ("אוֹת י' וְאוֹת ה' בְּשֵׁם", "אוֹת י' וְאוֹת ה' בְּשֵׁם"),
                  ("שִׁבְיִי", "שִׁבְיִי"), ("יְהוּדָה", "יְהוּדָה")]:
    got = names(src)
    check(got == want, "names(%r) = %r, want %r" % (src, got, want))

if fails:
    print("FAILED %d:" % len(fails))
    for f in fails[:50]:
        print("  " + f)
    sys.exit(1)
print("calendar/name tests: all passed")
