#!/usr/bin/env python3
"""source/*.html (onlinesiddur.com pages) -> content/ (what the app bundles).

For each page this keeps only the reading body, the page's day-condition CSS rules
and its precomputed day table (window.__CONDS__), and writes:
  content/pages/<id>.html   <style>rules</style> + the article's inner HTML
  content/pages/<id>.json   titles, page kind, section list, day table
  content/index.json        the home-screen list
The divine names are replaced on the way: YHVH (any vowels/cantillation, with its
prefix letters kept) and the printed abbreviation יְיָ -> ד' ; the divine Adonai (kamatz under the nun) -> א'.
"""
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

# vowels, cantillation, dagesh, shin/sin dots, meteg, ZWJ/ZWNJ/CGJ — everything that
# can sit on a letter. NOT maqaf (05BE), paseq (05C0), sof pasuq (05C3), nun hafukha (05C6).
M = "[֑-ׇֽֿׁׂׅׄ‌‍͏]"
LET = "[א-ת]"
WORD_START = "(?<![א-ת֑-ׇֽֿׁׂׅׄ‌‍͏])"
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


def replace_names(s, stats):
    s, n1 = YHVH.subn(lambda m: m.group(1) + "ד'", s)
    s, n3 = YY.subn(_yy, s)
    s, n2 = ADNY.subn(_adny, s)
    stats["yhvh"] += n1 + n3
    stats["adny_seen"] += n2
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
        total["yhvh"] += stats["yhvh"]
        total["adny_seen"] += stats["adny_seen"]
        print("%-10s %-22s %7d bytes  %3d sections  %s  YHVH x%d" % (
            pid, en, len(frag.encode()), len(sections),
            ("table %s+%dd" % (conds["e"], conds["d"])) if conds else "no table", stats["yhvh"]))

    write_calendar(calendar)
    fetched = ""
    try:
        fetched = open(os.path.join(SRC, "fetched.txt")).read().strip()
    except OSError:
        pass
    with open(os.path.join(OUT, "index.json"), "w", encoding="utf-8") as f:
        json.dump({"fetched": fetched, "pages": index}, f, ensure_ascii=False, indent=1)
    print("YHVH replaced: %d; Adonai-shaped words seen: %d" % (total["yhvh"], total["adny_seen"]))
    if leftovers:
        print("LEFTOVER names:", leftovers)
        sys.exit(1)


if __name__ == "__main__":
    main()
