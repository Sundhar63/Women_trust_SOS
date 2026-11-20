"""
Fills the TAMIL_ columns of the violence master tables.

  python translate_db.py                           -> translates and writes tamil_review.csv (database is NOT changed)
  python translate_db.py --apply tamil_review.csv  -> saves the CSV (after you reviewed it) into the database

Only rows whose Tamil is empty or still the English placeholder are touched.
Tamil that is already filled in is never overwritten.
"""
import csv
import json
import os
import sys
import time

import requests
from dotenv import load_dotenv

from database import SessionLocal
import models

load_dotenv()
GROQ_API_KEY = os.getenv("GROQ_API_KEY")

# model class name, English column, Tamil column
TABLES = [
    ("ViolenceMaster", "VIOLENCE", "TAMIL_VIOLENCE"),
    ("SubViolenceMaster", "SUB_VIOLENCE", "TAMIL_SUB_VIOLENCE"),
    ("CategoryViolenceMaster", "CATEGORY", "TAMIL_CATEGORY"),
    ("SubCategoryViolenceMaster", "SUB_CATEGORY", "TAMIL_SUB_CATEGORY"),
]

RULES = (
    "These labels come from an abuse and violence support system in Tamil Nadu, and counselors will read the Tamil. "
    "Translate the MEANING in this context, not word for word. "
    "Many English words have a different everyday meaning here, so choose the meaning that fits abuse and violence. "
    "'Partner' means a husband, wife or spouse, not a relative. "
    "Use noun forms for actions (ending in -தல் or -த்தல்). "
    "Use simple, natural Tamil that a counselor would say. "
    "Do not leave English words unless there is no Tamil word.\n"
)

BATCH = 15
CSV_FILE = "tamil_review.csv"


MODELS = ["openai/gpt-oss-120b", "openai/gpt-oss-20b"]     # the llama models were shut down by Groq on 16 Aug 2026


def chat(prompt):
    """One reply from Groq. Retries on rate limits, shows the real error text, and retries without JSON mode. Returns the text or None."""
    for model in MODELS:
        for use_json in (True, False):
            for attempt in range(4):
                try:
                    body = {"model": model, "temperature": 0.0,
                            "messages": [{"role": "user", "content": prompt}]}
                    if use_json:
                        body["response_format"] = {"type": "json_object"}
                    r = requests.post(
                        "https://api.groq.com/openai/v1/chat/completions",
                        headers={"Authorization": f"Bearer {GROQ_API_KEY}", "Content-Type": "application/json"},
                        json=body, timeout=90,
                    )
                    if r.status_code == 429:
                        print(f"  rate limited, waiting 20 s ({model})")
                        time.sleep(20)
                        continue
                    if r.status_code >= 400:
                        print(f"  {model} ({'json' if use_json else 'text'} mode): HTTP {r.status_code} {r.text[:250]}")
                        break
                    return r.json()["choices"][0]["message"]["content"].strip()
                except Exception as e:
                    print(f"  {model}: {e}")
                    break
    return None


def parse(raw, key):
    if not raw:
        return {}
    try:
        s, e = raw.find("{"), raw.rfind("}")
        d = json.loads(raw[s:e + 1]).get(key, {})
        return d if isinstance(d, dict) else {}
    except Exception:
        return {}


def needs_tamil(en, ta):
    return not (ta and ta.strip()) or ta.strip().lower() == en.strip().lower()


def collect(db):
    """Rows that still need Tamil, each with its full path as context."""
    v = {r.CODE: r.VIOLENCE for r in db.query(models.ViolenceMaster)}
    sv = {r.CODE: r.SUB_VIOLENCE for r in db.query(models.SubViolenceMaster)}
    cv = {r.CODE: r.CATEGORY for r in db.query(models.CategoryViolenceMaster)}
    rows = []
    for r in db.query(models.ViolenceMaster).order_by(models.ViolenceMaster.CODE):
        if needs_tamil(r.VIOLENCE, r.TAMIL_VIOLENCE):
            rows.append(("ViolenceMaster", r.CODE, r.VIOLENCE, r.VIOLENCE))
    for r in db.query(models.SubViolenceMaster).order_by(models.SubViolenceMaster.CODE):
        if needs_tamil(r.SUB_VIOLENCE, r.TAMIL_SUB_VIOLENCE):
            rows.append(("SubViolenceMaster", r.CODE, r.SUB_VIOLENCE,
                         f"{v.get(r.VIOLENCE_CODE)} > {r.SUB_VIOLENCE}"))
    for r in db.query(models.CategoryViolenceMaster).order_by(models.CategoryViolenceMaster.CODE):
        if needs_tamil(r.CATEGORY, r.TAMIL_CATEGORY):
            rows.append(("CategoryViolenceMaster", r.CODE, r.CATEGORY,
                         f"{v.get(r.VIOLENCE_CODE)} > {sv.get(r.SUB_VIOLENCE_CODE)} > {r.CATEGORY}"))
    for r in db.query(models.SubCategoryViolenceMaster).order_by(models.SubCategoryViolenceMaster.CODE):
        if needs_tamil(r.SUB_CATEGORY, r.TAMIL_SUB_CATEGORY):
            rows.append(("SubCategoryViolenceMaster", r.CODE, r.SUB_CATEGORY,
                         f"{v.get(r.VIOLENCE_CODE)} > {sv.get(r.SUB_VIOLENCE_CODE)} > {cv.get(r.CATEGORY_CODE)} > {r.SUB_CATEGORY}"))
    return rows


def _translate_batch(batch):
    items = {str(i): {"text": en, "context": ctx} for i, (_, _, en, ctx) in enumerate(batch)}

    # pass 1: translate (the context is only there to give the meaning)
    raw = chat(RULES
               + 'Each item has "text" (translate this) and "context" (the full path it belongs to, for meaning only). '
                 'Return only JSON: {"translations": {"<id>": "<tamil>"}}\n'
               + json.dumps(items, ensure_ascii=False))
    m = parse(raw, "translations")
    if not m:
        return {}

    # pass 2: the model checks its own Tamil against the English meaning and corrects it
    pairs = {i: {"english": items[i]["text"], "context": items[i]["context"], "tamil": m.get(i, "")} for i in items}
    raw = chat(RULES
               + "For each item, first write in English what the Tamil actually means. Compare that with the English text "
                 "in its context. If the meaning differs or the Tamil sounds unnatural, give a corrected Tamil, "
                 "otherwise repeat the same Tamil.\n"
                 'Return only JSON: {"checked": {"<id>": {"meaning": "<english>", "tamil": "<final Tamil>"}}}\n'
               + json.dumps(pairs, ensure_ascii=False))
    checked = parse(raw, "checked")
    for i in items:
        e = checked.get(i)
        if isinstance(e, dict) and e.get("tamil"):
            m[i] = e["tamil"]
    return m


def translate_batch(batch):
    m = _translate_batch(batch)
    if m or len(batch) <= 4:
        return m
    half = len(batch) // 2          # the batch failed: try each half on its own
    first = translate_batch(batch[:half])
    second = translate_batch(batch[half:])
    out = dict(first)
    out.update({str(int(k) + half): v for k, v in second.items()})
    return out


def load_previous():
    """Rows from an earlier run (or that you already reviewed), so work is never repeated."""
    prev = {}
    if os.path.exists(CSV_FILE):
        with open(CSV_FILE, newline="", encoding="utf-8-sig") as f:
            for r in csv.DictReader(f):
                prev[(r["table"], int(r["code"]))] = r
    return prev


def save_csv(rows, result):
    with open(CSV_FILE, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=["table", "code", "english", "context", "tamil"])
        w.writeheader()
        for table, code, en, ctx in rows:
            r = result.get((table, code))
            w.writerow({"table": table, "code": code, "english": en, "context": ctx,
                        "tamil": (r or {}).get("tamil", "")})


def dry_run():
    db = SessionLocal()
    try:
        rows = collect(db)
    finally:
        db.close()
    print(f"{len(rows)} rows need Tamil")
    if not rows:
        return

    # keep Tamil from an earlier run (including your own corrections); translate only what is missing
    prev = load_previous()
    result = {k: v for k, v in prev.items() if (v.get("tamil") or "").strip()}
    todo = [r for r in rows if (r[0], r[1]) not in result]
    if len(todo) < len(rows):
        print(f"{len(rows) - len(todo)} rows already in {CSV_FILE}, {len(todo)} left to translate")

    for start in range(0, len(todo), BATCH):
        batch = todo[start:start + BATCH]
        print(f"translating {start + 1}-{start + len(batch)} of {len(todo)}")
        m = translate_batch(batch)
        for i, (table, code, en, ctx) in enumerate(batch):
            if m.get(str(i)):
                result[(table, code)] = {"tamil": m[str(i)]}
        save_csv(rows, result)          # saved after every batch, so an interruption loses nothing

    save_csv(rows, result)
    empty = sum(1 for t, c, _, _ in rows if not (result.get((t, c)) or {}).get("tamil"))
    if empty:
        print(f"saved {CSV_FILE}, but {empty} rows have no Tamil yet. Run the same command again to translate only those.")
    else:
        print(f"saved {CSV_FILE}. Review it, then run:")
        print(f"  python translate_db.py --apply {CSV_FILE}")


def apply(path):
    cols = {t: (en_col, ta_col) for t, en_col, ta_col in TABLES}
    db = SessionLocal()
    done = skipped = 0
    try:
        with open(path, newline="", encoding="utf-8-sig") as f:
            for r in csv.DictReader(f):
                tamil = (r.get("tamil") or "").strip()
                if not tamil or r["table"] not in cols:
                    skipped += 1
                    continue
                model = getattr(models, r["table"])
                en_col, ta_col = cols[r["table"]]
                row = db.query(model).filter(model.CODE == int(r["code"])).first()
                if row is None or not needs_tamil(getattr(row, en_col), getattr(row, ta_col)):
                    skipped += 1          # missing row, or Tamil is already filled in: do not overwrite
                    continue
                setattr(row, ta_col, tamil)
                done += 1
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()
    print(f"saved Tamil for {done} rows, skipped {skipped}")


if __name__ == "__main__":
    if not GROQ_API_KEY and "--apply" not in sys.argv:
        sys.exit("GROQ_API_KEY is not set in .env")
    if len(sys.argv) >= 3 and sys.argv[1] == "--apply":
        apply(sys.argv[2])
    else:
        dry_run()
