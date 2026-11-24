-- Table for storing suggestions, helplines, and action steps dynamically.
-- Updating rows here immediately changes what is displayed on the user's screen.

CREATE TABLE IF NOT EXISTS "ABUSE_CONTACT_MASTER" (
    "ID"                 SERIAL PRIMARY KEY,
    "CODE"               INTEGER,
    "VIOLENCE_CODE"      INTEGER,
    "SUB_VIOLENCE_CODE"  INTEGER,
    "CATEGORY_CODE"      INTEGER,
    "SUB_CATEGORY_CODE"  INTEGER,
    "ABUSE_LEVEL"        VARCHAR(32) NOT NULL,
    "COLOUR_RATING"      INTEGER NOT NULL DEFAULT 0,
    "MASTER_STATUS"      VARCHAR(32) NOT NULL DEFAULT 'Active',
    "IMAGE_NAME"         VARCHAR(128),
    "SNO"                INTEGER NOT NULL,
    "PARTICULAR"         TEXT NOT NULL,
    "TAMIL_PARTICULAR"   TEXT,
    "CONTACT_NO"         VARCHAR(32) DEFAULT '100',
    "CONTACT_MAIL_ID"    VARCHAR(128) DEFAULT 'support@womentrust.org',
    "CONTACT_WEBSITE"    VARCHAR(256) DEFAULT 'https://www.womentrust.org/help'
);

CREATE INDEX IF NOT EXISTS "ix_ABUSE_CONTACT_MASTER_LEVEL"
    ON "ABUSE_CONTACT_MASTER" ("ABUSE_LEVEL", "MASTER_STATUS");

-- Seed Default Suggestions & Helplines

-- 1. Mild (< 5)
INSERT INTO "ABUSE_CONTACT_MASTER" ("ABUSE_LEVEL", "COLOUR_RATING", "MASTER_STATUS", "IMAGE_NAME", "SNO", "PARTICULAR", "TAMIL_PARTICULAR", "CONTACT_NO", "CONTACT_WEBSITE")
VALUES
('Mild', 0, 'Active', 'save_logo.png', 1, '1 - Talk to trusted Peers, Mentors & Family Member', '1 - நம்பிக்கையான நண்பர்கள், வழிகாட்டிகள் மற்றும் குடும்பத்தினரிடம் பேசுங்கள்', '100', 'https://www.womentrust.org/help'),
('Mild', 0, 'Active', 'save_logo.png', 2, '2 - Strengthen yourself to Ignore & divert', '2 - புறக்கணிக்கவும் கவனத்தை திசைதிருப்பவும் உங்களை பலப்படுத்துங்கள்', '100', 'https://www.womentrust.org/help'),
('Mild', 0, 'Active', 'save_logo.png', 3, '3 - Assert to Say ''No''', '3 - ''வேண்டாம்'' என்று உறுதியாகச் சொல்ல பழகுங்கள்', '100', 'https://www.womentrust.org/help'),
('Mild', 0, 'Active', 'save_logo.png', 4, '4 - Use it as a learning Opportunity to cope', '4 - இதை சமாளிப்பதற்கான கற்றல் வாய்ப்பாகப் பயன்படுத்துங்கள்', '100', 'https://www.womentrust.org/help'),
('Mild', 0, 'Active', 'save_logo.png', 5, '5 - Do not cry, depressed or Shows Signs of Victimisation', '5 - அழவோ, மனச்சோர்வடையவோ அல்லது பாதிக்கப்பட்டவராகவோ காட்டாதீர்கள்', '100', 'https://www.womentrust.org/help')
ON CONFLICT DO NOTHING;

-- 2. Moderate (< 10)
INSERT INTO "ABUSE_CONTACT_MASTER" ("ABUSE_LEVEL", "COLOUR_RATING", "MASTER_STATUS", "IMAGE_NAME", "SNO", "PARTICULAR", "TAMIL_PARTICULAR", "CONTACT_NO", "CONTACT_WEBSITE")
VALUES
('Moderate', 5, 'Active', 'telemanas_logo.png', 1, '1 - Warn the harassar (Husband / in law family to Stop', '1 - துன்புறுத்துபவரை (கணவர் / மாமியார் குடும்பத்தினர்) நிறுத்துமாறு எச்சரிக்கவும்', '14416', 'https://telemanas.mohf.gov.in/home'),
('Moderate', 5, 'Active', 'telemanas_logo.png', 2, '2 - Take some chance to record the Harassment', '2 - துன்புறுத்தலை பதிவு செய்ய வாய்ப்பைப் பயன்படுத்தவும்', '14416', 'https://telemanas.mohf.gov.in/home'),
('Moderate', 5, 'Active', 'telemanas_logo.png', 3, '3 - Have a detailed log of Date of the Harassment, Time, location, witness', '3 - துன்புறுத்தல் நடந்த தேதி, நேரம், இடம், சாட்சிகள் பற்றிய விரிவான குறிப்பை வைத்திருக்கவும்', '14416', 'https://telemanas.mohf.gov.in/home'),
('Moderate', 5, 'Active', 'telemanas_logo.png', 4, '4 - Identify Early & report within family', '4 - ஆரம்பத்திலேயே கண்டறிந்து குடும்பத்திற்குள் தெரிவிக்கவும்', '14416', 'https://telemanas.mohf.gov.in/home'),
('Moderate', 5, 'Active', 'telemanas_logo.png', 5, '5 - ''No'' Means ''No''', '5 - ''இல்லை'' என்றால் ''இல்லை'' என்று உறுதியாக இருங்கள்', '14416', 'https://telemanas.mohf.gov.in/home'),
('Moderate', 5, 'Active', 'telemanas_logo.png', 6, '6 - Be cautious & aware of external and legal remedy', '6 - வெளிப்புற மற்றும் சட்டப்பூர்வ தீர்வுகள் குறித்து எச்சரிக்கையாகவும் விழிப்புணர்வுடனும் இருங்கள்', '14416', 'https://telemanas.mohf.gov.in/home'),
('Moderate', 5, 'Active', 'telemanas_logo.png', 7, '7 - Tele-MANAS 24x7 Toll-Free Helpline: 14416 / Alternative: 1-800-891-4416 (Free & Confidential Support - https://telemanas.mohf.gov.in/home)', '7 - டெலிமானாஸ் 24x7 கட்டணமில்லா உதவி எண்: 14416 / மாற்று எண்: 1-800-891-4416 (இலவச மற்றும் ரகசிய ஆதரவு - https://telemanas.mohf.gov.in/home)', '14416 / 1-800-891-4416', 'https://telemanas.mohf.gov.in/home')
ON CONFLICT DO NOTHING;

-- 3. Severe (>= 10)
INSERT INTO "ABUSE_CONTACT_MASTER" ("ABUSE_LEVEL", "COLOUR_RATING", "MASTER_STATUS", "IMAGE_NAME", "SNO", "PARTICULAR", "TAMIL_PARTICULAR", "CONTACT_NO", "CONTACT_WEBSITE")
VALUES
('Severe', 10, 'Active', 'tamilnadu_police_kaaval_uthavi.png', 1, '1 - Consult A Legal Advisor', '1 - சட்ட ஆலோசகரை அணுகவும்', '181', 'https://www.womentrust.org/help'),
('Severe', 10, 'Active', 'tamilnadu_police_kaaval_uthavi.png', 2, '2 - Talk To a family therapist Dr. Saranya Sundaraju 9500185855', '2 - குடும்ப ஆலோசகர் டாக்டர் சரண்யா சுந்தரராஜுவுடன் பேசவும் 9500185855', '9500185855', 'https://www.womentrust.org/help'),
('Severe', 10, 'Active', 'tamilnadu_police_kaaval_uthavi.png', 3, '3 - Save Counselling 9842868900', '3 - சேவ் ஆலோசனை மையம் 9842868900', '9842868900', 'https://www.womentrust.org/help'),
('Severe', 10, 'Active', 'telemanas_logo.png', 4, '4 - Telemanas Nimhans 24x7 Toll-Free Helpline: 14416 / Alternative: 1-800-891-4416 (Free & Confidential Support)', '4 - டெலிமானாஸ் நிம்ஹான்ஸ் 24x7 கட்டணமில்லா உதவி எண்: 14416 / மாற்று எண்: 1-800-891-4416 (இலவச மற்றும் ரகசிய ஆதரவு)', '14416 / 1-800-891-4416', 'https://telemanas.mohf.gov.in/home'),
('Severe', 10, 'Active', 'tamilnadu_police_kaaval_uthavi.png', 5, '5 - KAVALAN UDANI APP', '5 - காவலன் உதவி செயலி (Kavalan Uthavi App)', '100', 'https://www.womentrust.org/help'),
('Severe', 10, 'Active', 'tamilnadu_police_kaaval_uthavi.png', 6, '6 - NCW Women Helpline 181', '6 - தேசிய மகளிர் ஆணைய உதவி எண் 181', '181', 'https://ncw.nic.in')
ON CONFLICT DO NOTHING;
