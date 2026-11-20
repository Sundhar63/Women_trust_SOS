"""
Fixes Tamil names in the violence master tables that were translated wrongly.

  python fix_tamil.py          -> shows what would change (database is NOT changed)
  python fix_tamil.py --apply  -> saves the changes

Each fix only applies if the row still has the exact old Tamil text,
so anything you or a reviewer changed in the meantime is never overwritten.
Have a Tamil speaker confirm the wording before you use --apply.
"""
import re
import sys

from database import SessionLocal
import models

# model class, English column, Tamil column
TABLES = {
    "ViolenceMaster": ("VIOLENCE", "TAMIL_VIOLENCE"),
    "SubViolenceMaster": ("SUB_VIOLENCE", "TAMIL_SUB_VIOLENCE"),
    "CategoryViolenceMaster": ("CATEGORY", "TAMIL_CATEGORY"),
    "SubCategoryViolenceMaster": ("SUB_CATEGORY", "TAMIL_SUB_CATEGORY"),
}

# (table, code, old Tamil, new Tamil)
FIXES = [
    # sub violence
    ("SubViolenceMaster", 2, "குடும்ப வன்முறை", "குடும்ப உறுப்பினர் வன்முறை"),            # was identical to "Domestic Violence"
    ("SubViolenceMaster", 3, "மருந்து பயன்பாட்டு பிரச்சனை", "போதைப் பொருள் பயன்பாட்டுக் கோளாறு"),  # "மருந்து" means medicine
    ("SubViolenceMaster", 8, "லிங்கத் துன்புறுத்தல்", "பாலியல் தொல்லை"),
    ("SubViolenceMaster", 9, "அதிகாரம் மற்றும் பாலியல் வன்முறை", "தாக்குதல் மற்றும் பாலியல் வன்கொடுமை"),  # "assault", not "authority"
    # category
    ("CategoryViolenceMaster", 4, "லிங்க துன்புறுத்தல்", "பாலியல் துன்புறுத்தல்"),
    ("CategoryViolenceMaster", 9, "மணமக்கள் தொடர்பான துன்புறுத்தல்", "வரதட்சணை தொடர்பான துன்புறுத்தல்"),  # dowry, not bride and groom
    ("CategoryViolenceMaster", 13, "புகைபிடித்தல் மற்றும் மருந்து உட்கொள்வது", "புகைபிடித்தல் மற்றும் போதைப்பொருள் பயன்பாடு"),
    ("CategoryViolenceMaster", 16, "குற்றம் செய்யுதல்", "குற்றம் செய்தல்"),               # standard spelling
    ("CategoryViolenceMaster", 17, "லிங்க பீடிப்பு", "பாலியல் தொல்லை"),
    ("CategoryViolenceMaster", 21, "அதிரடி", "ஆக்ரோஷம்"),                                   # "அதிரடி" means a sudden dramatic move
    ("CategoryViolenceMaster", 30, "அவமதிப்பான லிங்கக் கருத்துகள்", "விரும்பத்தகாத பாலியல் கருத்துகள்"),
    ("CategoryViolenceMaster", 31, "லிங்கத் தாக்குதல், கடத்தல் அச்சுறுத்தல்", "பாலியல் தாக்குதல், கடத்தல் மிரட்டல்"),
    ("CategoryViolenceMaster", 32, "சைகைகள், உடல் நிலை மற்றும் கண் பார்", "சைகைகள், தோரணைகள் மற்றும் முறைத்துப் பார்த்தல்"),  # was cut off
    ("CategoryViolenceMaster", 35, "தார்மீகமற்ற பாலியல் கோரிக்கைகள்", "நியாயமற்ற பாலியல் கோரிக்கைகள்"),  # "irrational", not "immoral"
    ("CategoryViolenceMaster", 37, "மருந்து பயன்பாடு மற்றும் ஊக்குவித்தல்", "போதைப்பொருள் பயன்பாடு மற்றும் தூண்டுதல்"),
]

# "Sexual" was translated as லிங்க in many places; the standard word is பாலியல்
WORD_FIX = (re.compile(r"லிங்க(?:த்|க்)?\s*"), "பாலியல் ")


def main(apply):
    db = SessionLocal()
    changes = 0
    try:
        # 1) exact fixes by table and code
        for table, code, old, new in FIXES:
            model = getattr(models, table)
            en_col, ta_col = TABLES[table]
            row = db.query(model).filter(model.CODE == code).first()
            if row is None:
                print(f"skip   {table} {code}: row not found")
                continue
            cur = (getattr(row, ta_col) or "").strip()
            if cur != old:
                print(f"skip   {table} {code} ({getattr(row, en_col)}): current Tamil is not the expected old text: {cur!r}")
                continue
            print(f"change {table} {code} ({getattr(row, en_col)}): {old}  ->  {new}")
            setattr(row, ta_col, new)
            changes += 1

        db.flush()

        # 2) the same wrong word in any other row (mostly labels)
        pattern, repl = WORD_FIX
        for table, (en_col, ta_col) in TABLES.items():
            model = getattr(models, table)
            for row in db.query(model).order_by(model.CODE):
                cur = getattr(row, ta_col) or ""
                if "லிங்க" not in cur:
                    continue
                new = pattern.sub(repl, cur).strip()
                print(f"change {table} {row.CODE} ({getattr(row, en_col)}): {cur}  ->  {new}")
                setattr(row, ta_col, new)
                changes += 1

        if apply:
            db.commit()
            print(f"\nsaved {changes} changes")
        else:
            db.rollback()
            print(f"\n{changes} changes would be made. Nothing was saved. Run with --apply to save them.")
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


if __name__ == "__main__":
    main("--apply" in sys.argv)
