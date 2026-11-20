"""
Fixes wrong Tamil in SubCategoryViolenceMaster (the labels).

  python fix_tamil_labels.py          -> shows what would change (database is NOT changed)
  python fix_tamil_labels.py --apply  -> saves the changes, and writes the old values to tamil_labels_backup.csv

A row is only changed if it still holds the exact old Tamil text.
"""
import csv
import sys

from database import SessionLocal
import models

RAPE = "பாலியல் வன்கொடுமை (வல்லுறவு)"

# (code, expected old Tamil, new Tamil)
FIXES = [
    (21, "கொந்தளித்தல் மற்றும் கடுமையான சுட்டுதல்", "கத்துதல் மற்றும் திட்டுதல்"),
    (22, "தவறை சுமத்தல்", "குற்றம் சாட்டுதல்"),
    (23, "தவறான குற்றம் ஒதுக்குதல்", "பலிகடா ஆக்குதல்"),
    (26, "தேவைகளை புறக்கணித்தல்", "தேவைகளை மறுத்தல்"),
    (29, "வலியுறுத்தப்பட்ட தனிமை", "கட்டாயத் தனிமைப்படுத்தல்"),
    (30, "மனப்பயன் செய்தல்", "மனதளவில் கையாளுதல்"),
    (47, "காமத் தாக்குதல்", "பாலியல் தாக்குதல்"),
    (48, "விண்ணை", RAPE),
    (49, "வலியுறுத்தப்பட்ட பாலியல் உறவு", "கட்டாயப்படுத்தி பாலியல் உறவு கொள்ளுதல்"),
    (50, "காமச் செயலுக்கு மிரட்டல்", "பாலியல் செயலுக்கு மிரட்டுதல்"),
    (51, "காமச் செயலுக்கு ஏமாற்றல்", "பாலியல் செயலுக்கு ஏமாற்றி சம்மதிக்க வைத்தல்"),
    (52, "காமச் செயலுக்கு மது அல்லது மருந்து பயன்படுத்துதல்", "பாலியல் செயலுக்காக மது அல்லது போதைப்பொருள் பயன்படுத்துதல்"),
    (54, "நோயாக அல்லது சோர்வாக", "நோயுற்றிருக்கும்போது அல்லது சோர்வாக இருக்கும்போது பாலியல் வற்புறுத்தல்"),
    (55, "போர்ன் படங்களை பதிவு செய்ய அழுத்துதல்", "ஆபாசப் படங்களைப் பதிவு செய்ய வற்புறுத்துதல்"),
    (57, "பாலியல் அவமதித்தல்", "பாலியல் ரீதியாக அவமதித்தல் அல்லது சங்கடப்படுத்துதல்"),
    (58, "கருப்பைத் தடுப்பை பயன்படுத்துவதை கட்டுப்படுத்துதல் அல்லது மறுத்தல்", "கருத்தடையைப் பயன்படுத்துவதைக் கட்டுப்படுத்துதல் அல்லது மறுத்தல்"),
    (61, "உடை கட்டுப்படுத்துவதற்கு பொறாமையை காரணமாகக் கொள்வது", "உடை விஷயத்தில் கட்டுப்படுத்த பொறாமையை காரணம் காட்டுதல்"),
    (173, "காம உதவியை கோருதல்", "பாலியல் ரீதியான உதவிகளைக் கோருதல்"),
    (174, "விருப்பமற்ற காம முன்மொழிவுகள் அல்லது நடத்தைகள்", "விரும்பத்தகாத பாலியல் அணுகுமுறைகள் அல்லது நடத்தை"),
    (176, "நியமனதாரர் அறியப்பட்ட காம தொல்லை சம்பவத்தை கவனிக்கத் தவறுதல்", "வேலையளிப்பவர் அறியப்பட்ட பாலியல் தொல்லை சம்பவத்தைக் கவனிக்கத் தவறுதல்"),
    (179, "அதிகாரத்தில் உள்ள தாக்குபவர்", "அதிகாரப் பதவியில் இருப்பவரால் தாக்குதல்"),
    (249, "குரல் அழைப்பு", "கேலியாகக் கூச்சலிடுதல் / விசிலடித்தல்"),
    (250, "காட்சிப்படுத்தல்", "ஆபாசமாக உடலைக் காட்டுதல்"),
    (351, "உள்ளே நுழைவு", "பாலியல் ஊடுருவல்"),
    (352, "வலியுறுத்தி பாலியல் வன்முறை", RAPE),
    (353, "வாய் பாலியல் வற்புறுத்தல்", "வாய்வழி பாலியல் செயலுக்கு வற்புறுத்துதல்"),
    (354, "உள்ளே நுழையாத பாலியல் செயல்கள்", "ஊடுருவல் இல்லாத பாலியல் செயல்கள்"),
    (356, "எதிர்கால துஷ்பிரயோகத்திற்காக ஃப்ளாஸிங் அல்லது கிரூமிங்",
          "ஆபாசமாகக் காட்டுதல் அல்லது நம்பிக்கை பெற்று எதிர்கால துஷ்பிரயோகத்திற்குத் தயார்படுத்துதல்"),
]


def main(apply):
    db = SessionLocal()
    backup, changes = [], 0
    try:
        M = models.SubCategoryViolenceMaster
        for code, old, new in FIXES:
            row = db.query(M).filter(M.CODE == code).first()
            if row is None:
                print(f"skip   {code}: row not found")
                continue
            cur = (row.TAMIL_SUB_CATEGORY or "").strip()
            if cur != old:
                print(f"skip   {code} ({row.SUB_CATEGORY}): current Tamil is not the expected old text: {cur!r}")
                continue
            print(f"change {code} ({row.SUB_CATEGORY}): {old}  ->  {new}")
            backup.append((code, row.SUB_CATEGORY, old, new))
            row.TAMIL_SUB_CATEGORY = new
            changes += 1
        if apply and changes:
            with open("tamil_labels_backup.csv", "w", newline="", encoding="utf-8-sig") as f:
                w = csv.writer(f)
                w.writerow(["code", "english", "old_tamil", "new_tamil"])
                w.writerows(backup)
            db.commit()
            print(f"\n{changes} rows saved. Old values are in tamil_labels_backup.csv")
        else:
            db.rollback()
            print(f"\n{changes} changes would be made. Nothing was saved. Run with --apply to save them.")
    finally:
        db.close()


if __name__ == "__main__":
    main("--apply" in sys.argv)
