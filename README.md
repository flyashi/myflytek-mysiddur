# My Siddur

An offline siddur for the de-clouded **iFlytek AiNote Air2** (e-ink, Android 11):
Nusach Ashkenaz, Tehillim and the Torah readings, shown for **today**. Framework
widgets and the system WebView only — no AndroidX, no Gradle, no permissions, no
network.

## What it does

* **Home** — today's Hebrew date and what's special about it (Rosh Chodesh, Chanukah,
  the Omer count tonight…), the occasional prayers that apply today/tonight, today's
  Tehillim portion, then every prayer by group.
* **Every Ashkenaz page of onlinesiddur.com** — Shacharit, Mincha, Maariv, Brachot,
  Birkat HaMazon, Bracha Achrona, Kriat Shema al HaMita, Kaddish, Tefilat HaDerech,
  Musaf Rosh Chodesh / Chol HaMoed, Hoshanot, Chanukah, Megillat Esther, Sefirat
  HaOmer, Birkat HaLevana, Selichot, Tisha B'Av, Brit Milah, Tikkun HaKlali, Tikkun
  Chatzot — plus Tehillim and the Torah readings.
* **Filtered for today** — parts for other days (Ya'ale Veyavo, Hallel, Tachanun, Al
  HaNisim, Mashiv HaRuach / V'ten Tal uMatar, Shir shel Yom, Hoshanot, the Omer
  count, the Selichot day…) are hidden; *Show all* brings them back. Night prayers
  after midday use the coming Hebrew day; meal-time pages (Birkat HaMazon…) have a
  day/night switch. Outside Israel by default (Settings › Location).
* **The Name** — YHVH (any vowels or cantillation, prefixes kept: לַד׳, אֶת־ד׳) and
  the printed יְיָ are written **ד'**; the divine אֲדֹנָי (kamatz under the nun) is
  written **א'**. אֲדֹנִי "my master" and אֲדֹנֵינוּ are left alone.
* **Reading** — sections list (only today's), current section in the title, A−/A+,
  page ▲▼ buttons, where-you-were memory (3 h for prayers, always for Tehillim),
  four Hebrew fonts, vowels+cantillation / vowels only / none, keep-screen-on.
* **Tehillim** — jump by day of the month (29/30-day months handled, Psalm 119
  split at verse 97), by day of the week, or by chapter.

## Build

```
./fetch-deps.sh          # r8 + android.jar into deps/
./build.sh               # -> build/myflytek-mysiddur.apk, signed with the mfk app key
```

The texts are committed in `content/`, so a build needs no network. To refresh them:

```
tools/fetch-source.sh    # onlinesiddur.com pages -> source/ (gitignored)
tools/import.py          # source/ -> content/  (fails if any Name is left unconverted)
```

`import.py` keeps each page's reading body and its day-rule CSS, replaces the Names,
and merges the pages' precomputed day tables into one `content/calendar.json`
(Israel/outside × day/night, one row per civil date; the current one runs 10 years
from 2026-10-13). Past its end nothing is hidden. Re-run the two tools to extend it.

| File | |
|---|---|
| `MainActivity` | home: date line, today's specials, the prayer list |
| `ReaderActivity` | the WebView reader, status line, sections, page buttons |
| `Calendar` | decodes `calendar.json` for a date + day/night |
| `HebDate` | Hebrew date (ICU HebrewCalendar), numerals, day labels |
| `Tehilim` | monthly/weekly portions and the jump list |
| `Nikud` | strips cantillation / all points |
| `app/assets/reader.css` | e-ink styles (black on white, no animation) |

## Credits

Texts and day rules from [onlinesiddur.com](https://onlinesiddur.com/) by Ethan
Piliavin, whose Hebrew texts come from [Sefaria](https://www.sefaria.org)'s open
library (see its per-text licences). Fonts: Frank Ruehl CLM and Shlomo SemiStam
(Culmus project), Noto Serif Hebrew and Cardo (SIL OFL). App code: MIT.
