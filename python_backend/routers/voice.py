from fastapi import APIRouter, HTTPException, File, UploadFile
from dotenv import load_dotenv
import os
import re
import json
import asyncio
import time
from collections import Counter
import requests

from database import SessionLocal
import models

load_dotenv()

router = APIRouter(prefix="/api/Voice", tags=["Voice"])

# These names must match your .env file exactly
SPEECHMATICS_API_KEY = os.getenv("SPEECHMATICS_API_KEY")
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")


@router.post("/ProcessAudio")
async def process_audio(file: UploadFile = File(...)):
    if not file:
        raise HTTPException(status_code=400, detail="No file uploaded.")

    try:
        audio_bytes = await file.read()

        # STEP 1: Transcription
        transcribed_text = await transcribe_audio(audio_bytes, file.filename)
        if not transcribed_text:
            raise HTTPException(status_code=422, detail="Could not transcribe audio.")

        cleaned = transcribed_text.strip().lower().strip(".!?, ")
        if len(transcribed_text.split()) < 3 or cleaned in {"you", "thank you", "thanks for watching", "bye"}:
            raise HTTPException(status_code=422, detail="No clear speech detected. Please speak again.")

        # STEP 2: Classification
        classification_json = await classify_text(transcribed_text)
        if not classification_json:
            raise HTTPException(status_code=502, detail="Classification failed. Check the server logs.")

        # STEP 3: Response
        return {
            "success": True,
            "transcription": transcribed_text,
            "selected_checkbox": classification_json,
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


async def transcribe_audio(audio_bytes, filename):
    transcribed_text = None

    print(f"[transcribe] file={filename} bytes={len(audio_bytes)}")

    # 1st Priority: Speechmatics
    if SPEECHMATICS_API_KEY:
        try:
            sm_url = "https://asr.api.speechmatics.com/v2/jobs"
            sm_headers = {"Authorization": f"Bearer {SPEECHMATICS_API_KEY}"}
            config_data = {
                "type": "transcription",
                "transcription_config": {"language": "auto"},
            }

            response = await asyncio.to_thread(
                requests.post, sm_url,
                headers=sm_headers,
                data={"config": json.dumps(config_data)},
                files={"data_file": (filename, audio_bytes)},
                timeout=60,
            )
            print("[speechmatics] submit:", response.status_code)
            response.raise_for_status()

            job_id = response.json()["id"]

            for _ in range(120):  # up to ~2 minutes
                status_resp = await asyncio.to_thread(
                    requests.get, f"{sm_url}/{job_id}", headers=sm_headers, timeout=30
                )
                status_resp.raise_for_status()
                status = status_resp.json()["job"]["status"]

                if status == "done":
                    break
                elif status in ["rejected", "deleted", "expired"]:
                    raise Exception(f"Job failed with status: {status}")

                await asyncio.sleep(1)
            else:
                raise Exception("Speechmatics job timed out")

            transcript_resp = await asyncio.to_thread(
                requests.get, f"{sm_url}/{job_id}/transcript?format=txt",
                headers=sm_headers, timeout=30,
            )
            transcript_resp.raise_for_status()
            transcribed_text = transcript_resp.content.decode("utf-8").strip()
        except Exception as e:
            print(f"[speechmatics] FAILED: {e}")

    # 2nd Priority: Groq Whisper
    if not transcribed_text and GROQ_API_KEY:
        try:
            url = "https://api.groq.com/openai/v1/audio/transcriptions"
            headers = {"Authorization": f"Bearer {GROQ_API_KEY}"}
            files = {"file": (filename, audio_bytes, "application/octet-stream")}
            data = {"model": "whisper-large-v3", "response_format": "json"}

            response = await asyncio.to_thread(
                requests.post, url, headers=headers, files=files, data=data, timeout=60
            )
            print("[groq-whisper] response:", response.status_code)
            response.raise_for_status()
            transcribed_text = (response.json().get("text") or "").strip()
        except Exception as e:
            print(f"[groq-whisper] FAILED: {e}")

    if not transcribed_text:
        print("[transcribe] all providers failed or returned empty text")

    return transcribed_text


# ---------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------
def is_tamil(text: str) -> bool:
    tamil = len(re.findall(r"[\u0B80-\u0BFF]", text))
    letters = len(re.findall(r"[^\W\d_]", text))
    return letters > 0 and tamil / letters > 0.3


ALERTS_TA = {
    "Counseling with our client": "வாடிக்கையாளருடன் ஆலோசனை",
    "Counseling is needed": "ஆலோசனை தேவை",
    "Call the police": "காவல்துறையை அழைக்கவும்",
    "No Action Required": "நடவடிக்கை தேவையில்லை",
}

CASUAL_EN = {
    "voilence": "Casual Conversation",
    "sub_violence": "None",
    "category_violence": "None",
    "matched_sub_category_violences": [],
}
CASUAL_TA = {
    "voilence": "சாதாரண உரையாடல்",
    "sub_violence": "இல்லை",
    "category_violence": "இல்லை",
    "matched_sub_category_violences": [],
}


async def _chat(messages):
    """Send messages to Groq models, then OpenRouter. Returns the reply text or None."""
    if GROQ_API_KEY:
        for g_model in ["openai/gpt-oss-120b", "openai/gpt-oss-20b"]:
            try:
                response = await asyncio.to_thread(
                    requests.post,
                    "https://api.groq.com/openai/v1/chat/completions",
                    headers={
                        "Authorization": f"Bearer {GROQ_API_KEY}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": g_model,
                        "messages": messages,
                        "temperature": 0.0,
                        "response_format": {"type": "json_object"},
                    },
                    timeout=60,
                )
                print(f"[groq:{g_model}]", response.status_code)
                response.raise_for_status()
                return response.json()["choices"][0]["message"]["content"].strip()
            except Exception as e:
                print(f"[groq:{g_model}] FAILED: {e}")

    if OPENROUTER_API_KEY:
        try:
            response = await asyncio.to_thread(
                requests.post,
                "https://openrouter.ai/api/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {OPENROUTER_API_KEY}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": "meta-llama/llama-3.3-70b-instruct:free",
                    "messages": messages,
                    "temperature": 0.0,
                },
                timeout=60,
            )
            print("[openrouter]", response.status_code)
            response.raise_for_status()
            return response.json()["choices"][0]["message"]["content"].strip()
        except Exception as e:
            print(f"[openrouter] FAILED: {e}")

    return None


def _parse_json(raw):
    start = raw.find("{")
    end = raw.rfind("}")
    if start != -1 and end != -1 and end > start:
        raw = raw[start:end + 1]
    return json.loads(raw)


# ---------------------------------------------------------------
# Taxonomy: read from the master tables, so new violences added there are used automatically
# ---------------------------------------------------------------
TAXONOMY_TTL = 60          # seconds; new rows in the master tables appear within this time
_TAX = {"data": None, "at": 0.0}


def _load_taxonomy_sync():
    db = SessionLocal()
    try:
        def active(model):
            return (db.query(model)
                      .filter(model.MASTER_STATUS == "Active")
                      .order_by(model.CODE).all())

        v_map = {v.CODE: (v.VIOLENCE, v.TAMIL_VIOLENCE) for v in active(models.ViolenceMaster)}
        sv_map = {s.CODE: (s.SUB_VIOLENCE, s.TAMIL_SUB_VIOLENCE) for s in active(models.SubViolenceMaster)}
        labels = {}
        for x in active(models.SubCategoryViolenceMaster):
            labels.setdefault(x.CATEGORY_CODE, []).append(
                {"code": x.CODE, "en": x.SUB_CATEGORY, "ta": x.TAMIL_SUB_CATEGORY})
        cats = [(c.CODE, c.VIOLENCE_CODE, c.SUB_VIOLENCE_CODE, c.CATEGORY, c.TAMIL_CATEGORY)
                for c in active(models.CategoryViolenceMaster)]
    finally:
        db.close()

    paths = []
    for code, v_code, sv_code, name, name_ta in cats:
        if v_code not in v_map or sv_code not in sv_map:
            continue                                   # parent is missing or inactive
        paths.append({
            "v": v_map[v_code][0], "v_ta": v_map[v_code][1], "v_code": v_code,
            "s": sv_map[sv_code][0], "s_ta": sv_map[sv_code][1], "s_code": sv_code,
            "c": name, "c_ta": name_ta, "c_code": code,
            "labels": labels.get(code, []),
        })
    return paths


async def get_taxonomy():
    now = time.time()
    if _TAX["data"] and now - _TAX["at"] < TAXONOMY_TTL:
        return _TAX["data"]

    # Neon pauses idle databases, so the first query after a pause can fail: retry a few times
    paths, error = None, None
    for attempt in range(3):
        try:
            paths = await asyncio.to_thread(_load_taxonomy_sync)
            break
        except Exception as e:
            error = e
            print(f"[taxonomy] load attempt {attempt + 1} failed: {e}")
            if attempt < 2:
                await asyncio.sleep(1.5)

    if paths:
        _TAX["data"], _TAX["at"] = paths, now
        return paths

    if paths is not None:
        error = RuntimeError("violence master tables are empty or inactive")
    if _TAX["data"]:
        print(f"[taxonomy] using last loaded copy: {error}")
        return _TAX["data"]
    raise error


def _is_others(name):
    return str(name).strip().lower() == "others"


# ---------------------------------------------------------------
# Step 1: pick the category. Step 2: check that category's labels.
# ---------------------------------------------------------------
MAX_CATEGORIES = 4

# Who did it / where it happened decides the violence TYPE (domestic, workplace, sexual, other)
_PARTNER_RE = re.compile(r"\b(husband|wife|spouse|partner|boyfriend|ex[- ]?husband|ex[- ]?boyfriend|fianc[eé]|in[- ]?laws?|mother[- ]?in[- ]?law|father[- ]?in[- ]?law|brother[- ]?in[- ]?law)\b|கணவர்|கணவன்|மாமியார்|மாமனார்|நாத்தனார்|காதலன்|கணவரின்|புருஷன்|புருசன்|மனைவி|மாமியாரும்|மைத்துனர்|கொழுந்தன்|முன்னாள் கணவர்|காதலர்|வீட்டுக்காரர்", re.I)
_FAMILY_RE = re.compile(r"\b(father|mother|dad|mom|brother|sister|uncle|aunt|cousin|grandfather|grandmother|son|daughter|family member|relative)\b|அப்பா|அம்மா|அண்ணன்|தம்பி|அக்கா|தங்கை|மாமா|சித்தப்பா|பெரியப்பா|உறவினர்|குடும்ப உறுப்பினர்|அண்ணா|அத்தை|பாட்டி|தாத்தா|மகன்|மகள்|அப்பாவும்|அம்மாவும்|சகோதரன்|சகோதரி|சொந்தக்காரர்|மச்சான்|அத்தான்", re.I)
_WORK_RE = re.compile(r"\b(colleagues?|co[- ]?workers?|coworkers?|boss|manager|supervisor|team ?lead|employer|employee|office|workplace|work ?place|factory|company|hr|senior|junior|client|customer|shop|staff|workers?)\b|சக ஊழியர்|உடன் வேலை|மேலாளர்|முதலாளி|அலுவலகம்|வேலை செய்யும் இடம்|தொழிற்சாலை|கம்பெனி|நிறுவனம்|வேலை செய்யும்|வேலை செய்கிற|வேலை செய்பவர்|என்னோடு வேலை|உடன் பணி|மேனேஜர்|பாஸ்|சூப்பர்வைசர்|சக பணியாளர்|கூட வேலை|ஆபீஸ்|அலுவலக|பணியிடம்|வேலை இடம்|வேலை பார்க்கும்|கடை முதலாளி|ஊழியர்|தொழிலாளி|ஹெச்ஆர்|மேலதிகாரி|கம்பெனியில்|கடையில்|ஃபேக்டரி|பேக்டரி", re.I)
_PUBLIC_RE = re.compile(r"\b(stranger|bus|train|street|road|public|market|auto driver|passenger|stalker|stalking|unknown (man|person|guy))\b|பேருந்து|பஸ்|ரயில்|தெரு|அறிமுகமில்லாத|முன்பின் தெரியாத|பேருந்தில்|பஸ்ஸில்|ரயிலில்|சாலையில்|வீதியில்|பொது இடம்|சந்தை|ஆட்டோ டிரைவர்|ஆட்டோக்காரர்|பின்தொடர்|தெரியாத ஆள்|தெரியாத நபர்|கல்லூரி|பள்ளி", re.I)
_CTX_SEXUAL_RE = re.compile(r"\b(rape|raped|sexual|sex|touch(ed|ing)?|grop(e|ed|ing)|molest\w*|harass\w*|eve[- ]?teas\w*|obscene|vulgar|kiss\w*|bad touch)\b|பாலியல்|பலாத்காரம்|வன்புணர்வு|கற்பழிப்பு|தவறான தொடுதல்|தவறாக தொட|தொட்டார்|தொட்டான்|கட்டிப்பிடி|முத்தம்|ஆபாச|பாலுறவு|உறவுக்கு கட்டாய|உடலை தொட|பாலியல்", re.I)


def _vtype(p):
    v = str(p["v"]).lower()
    if "domestic" in v:
        return "domestic"
    if "workplace" in v or "work place" in v:
        return "workplace"
    if "sexual" in v:
        return "sexual"
    return "other"


async def detect_context(text):
    """Who the abuser is and where it happened -> {"partner":bool,"family":bool,"work":bool,"public":bool,"sexual":bool}.
    Regex gives a baseline; a small model call corrects it (it understands "my manager", "the man next to me")."""
    ctx = {"partner": bool(_PARTNER_RE.search(text)), "family": bool(_FAMILY_RE.search(text)),
           "work": bool(_WORK_RE.search(text)), "public": bool(_PUBLIC_RE.search(text)),
           "sexual": bool(_CTX_SEXUAL_RE.search(text) or _CRITICAL_RE.search(text) or _CRITICAL_TA_RE.search(text))}
    prompt = (
        "Read this transcript (voice transcription; may be English or Tamil). Identify EVERY abuser and the place.\n"
        "Return only JSON: {\"abusers\": [\"partner\"|\"family\"|\"work\"|\"stranger\"|\"other\", ...], "
        "\"places\": [\"home\"|\"work\"|\"public\"|\"online\"|\"unknown\", ...], \"sexual\": true|false}\n"
        "partner = husband, wife, boyfriend, ex-partner, in-laws. family = parent, sibling, relative living with or related to her. "
        "work = colleague, boss, manager, supervisor, employer, co-worker, client at the workplace, or anyone at the workplace. "
        "stranger = unknown person, person on bus/street/public place, teacher/neighbour/other known non-family person goes in other. "
        "sexual = true only if the abuse has a sexual nature (touching, remarks, rape, harassment). Tamil examples: கணவர்/மாமியார் = partner/in-laws; அப்பா/அண்ணன்/மாமா = family; மேனேஜர்/முதலாளி/சக ஊழியர்/ஆபீஸ் = work; பேருந்து/தெரு/தெரியாத நபர் = stranger/public.\n\n"
        f'Transcript: """{text}"""'
    )
    raw = await _chat([{"role": "user", "content": prompt}])
    if raw:
        try:
            d = _parse_json(raw)
            ab = {str(x).lower() for x in (d.get("abusers") or [])}
            pl = {str(x).lower() for x in (d.get("places") or [])}
            ctx["partner"] = "partner" in ab or ctx["partner"] and not ab
            ctx["family"] = "family" in ab or ctx["family"] and not ab
            ctx["work"] = "work" in ab or "work" in pl or ctx["work"] and not (ab or pl)
            ctx["public"] = "stranger" in ab or "public" in pl or ctx["public"] and not (ab or pl)
            ctx["sexual"] = bool(d.get("sexual")) or ctx["sexual"] and "sexual" not in d
        except Exception as e:
            print(f"[context] parse failed, using keywords: {e}")
    print("[context]", ctx)
    return ctx


def filter_paths(paths, ctx, text):
    """Keep only the violence types that fit the abuser/place. Returns (filtered paths, hint text)."""
    allowed = set()
    if ctx["partner"] or ctx["family"]:
        allowed.add("domestic")
    if ctx["work"]:
        allowed.add("workplace")
    if ctx["public"]:
        allowed.add("other")
    if ctx["sexual"] or _CRITICAL_RE.search(text) or _CRITICAL_TA_RE.search(text):
        allowed.add("sexual")
    if not (ctx["partner"] or ctx["family"] or ctx["work"] or ctx["public"]):
        return paths, ""                           # abuser/place unknown: let the model decide from all types
    if not (ctx["partner"] or ctx["family"] or ctx["work"]):
        allowed.add("other")                       # generic "harassment / other" only when no home/work abuser is known
    rape = bool(_CRITICAL_RE.search(text) or _CRITICAL_TA_RE.search(text))
    if ctx["work"] and ctx["sexual"] and not rape:
        allowed.discard("other")                   # workplace sexual harassment has its own category
    if rape:
        allowed.add("other")                       # the "Rape" label only exists under domestic and harassment/other
    kept = [p for p in paths if _vtype(p) in allowed]
    if not kept:
        return paths, ""
    hint = ("Abuser/place detected: " + ", ".join(k for k in ("partner", "family", "work", "public") if ctx[k]) +
            (", sexual nature" if ctx["sexual"] else "") + ". Only the matching violence types are listed.")
    return kept, hint


async def pick_categories(text, paths, hint=""):
    """Return (category numbers most relevant first, model severity "low|medium|high"), or None on failure.
    An empty category list means no violence was described."""
    def sample(p):
        names = [l["en"] for l in p["labels"] if not _is_others(l["en"])][:6]
        return "; ".join(names)

    listing = "\n".join(f"{i}: {p['v']} > {p['s']} > {p['c']}  [e.g. {sample(p)}]" for i, p in enumerate(paths))
    prompt = (
        "Read this transcript (from voice transcription, so ignore filler words and spelling errors). "
        "It may be English or Tamil. Tamil terms: பலாத்காரம் / வன்புணர்வு / பாலியல் வல்லுறவு / கற்பழிப்பு = rape; "
        "பாலியல் தொல்லை / பாலியல் துன்புறுத்தல் = sexual harassment; தவறான தொடுதல் = bad touch; "
        "மிரட்டல் = threat. "
        f"Choose ALL category numbers from the list that match any violence or abuse described, up to {MAX_CATEGORIES}, "
        "most relevant first. One transcript can involve several types (for example child abuse and physical violence "
        "and sexual violence together), so include each type that is described. "
        "Rules: (1) Attempted acts count as the act itself (an attempted rape is rape; an attempted assault is assault). "
        "(2) Touching without consent, comments about the body, trying to be alone with someone, trying to enter her "
        "room, and threats to keep silent are sexual abuse / sexual harassment, plus a threat or intimidation category "
        "when a threat is made. (3) A girl or boy under 18 is a child: any sexual behaviour towards a child is child "
        "sexual abuse and is the most serious kind. (4) If the abuser is a relative or family member, also include "
        "the family-violence category. (5) Prefer sexual-violence categories for sexual acts. "
        "(6) The violence TYPE depends on WHO the abuser is and WHERE it happened, not on the act: "
        "hitting, beating, shouting, bullying or threats by a colleague, boss, manager, co-worker or at the workplace "
        "is WORKPLACE violence (for example worker on worker > aggression), never domestic violence. "
        "Domestic violence is ONLY for a husband, partner, in-laws or family/household member. "
        "Strangers or people in public places are sexual violence (if sexual) or harassment/other, not domestic. "
        "Do not choose workplace categories unless a workplace or colleague/boss is mentioned. "
        "Do not choose domestic categories unless a husband, partner, in-laws or family member is the abuser. "
        "Ignore the example labels in brackets when they conflict with this rule. "
        "If it describes no violence, abuse, harassment, threat or coercion, return an empty list.\n"
        "Also rate the overall severity of what is described:\n"
        '"high" = rape or attempted rape, sexual assault, ANY sexual abuse or sexual harassment of a child (under 18), '
        "any physical abuse of a child, kidnapping, acid attack, "
        "burning, strangling, use of a weapon, threat to kill, life-threatening or repeated serious physical violence.\n"
        '"medium" = sexual harassment of an adult, groping or bad touch of an adult, stalking, blackmail, beating or hitting, '
        "dowry harassment, serious threats, confinement, forced isolation.\n"
        '"low" = verbal abuse, shouting, insults, controlling behaviour, one-off minor incidents.\n'
        'Return only JSON: {"categories": [<number>, ...], "severity": "low|medium|high"}\n\n'
        f"{hint}\nCategories:\n{listing}\n\nTranscript: \"\"\"{text}\"\"\""
    )
    raw = await _chat([{"role": "user", "content": prompt}])
    if not raw:
        return None
    try:
        data = _parse_json(raw)
        got = data.get("categories", [])
        if not isinstance(got, list):
            got = [got]
        out = []
        for g in got:
            i = int(g)
            if 0 <= i < len(paths) and i not in out:
                out.append(i)
        severity = str(data.get("severity", "")).strip().lower()
        return out[:MAX_CATEGORIES], (severity if severity in ("low", "medium", "high") else "")
    except Exception as e:
        print(f"[pick_categories] parse failed: {e}")
        return None


async def _match_once(text, path):
    names = [l["en"] for l in path["labels"] if not _is_others(l["en"])]
    prompt = (
        "Read this transcript (from voice transcription, so ignore filler words and spelling errors). "
        "Go through the labels below ONE BY ONE and decide for each whether the transcript describes it, "
        "either explicitly or clearly by meaning. Return EVERY label that applies, not just the most obvious.\n"
        'Return only JSON: {"matched": ["<label>", ...]} using the labels exactly as written.\n\n'
        f"Labels: {json.dumps(names)}\n\nTranscript: \"\"\"{text}\"\"\""
    )
    raw = await _chat([{"role": "user", "content": prompt}])
    if not raw:
        return None
    try:
        got = _parse_json(raw).get("matched", [])
        lookup = {n.strip().lower(): n for n in names}          # case-insensitive, real labels only
        out = []
        for g in got:
            real = lookup.get(str(g).strip().lower())
            if real and real not in out:
                out.append(real)
        return out
    except Exception as e:
        print(f"[match_labels] parse failed: {e}")
        return None


async def match_labels(text, path, runs=1):
    """runs=1: one check. runs=3: majority vote (a label must appear in 2 of 3 runs). Uses more API calls."""
    results = await asyncio.gather(*[_match_once(text, path) for _ in range(runs)])
    results = [r for r in results if r is not None]
    if not results:
        return None
    counts = Counter(x for r in results for x in r)
    need = len(results) // 2 + 1
    return [l["en"] for l in path["labels"] if counts[l["en"]] >= need]    # keeps table order


# ---------------------------------------------------------------
# Tamil: use the TAMIL_ columns from the tables; the model only translates what is missing
# ---------------------------------------------------------------
TAMIL_RULES = (
    "These labels come from an abuse and violence support system in Tamil Nadu, and counselors will read the Tamil. "
    "Translate the MEANING in this context, not word for word. "
    "Many English words have a different everyday meaning here, so choose the meaning that fits abuse and violence. "
    "'Partner' means a husband, wife or spouse, not a relative. "
    "Use noun forms for actions (ending in -தல் or -த்தல்). "
    "Use simple, natural Tamil that a counselor would say. "
    "Do not leave English words unless there is no Tamil word.\n"
)


def _json_map(raw, key):
    if not raw:
        return {}
    try:
        d = _parse_json(raw).get(key, {})
        return d if isinstance(d, dict) else {}
    except Exception as e:
        print(f"[tamil] parse failed: {e}")
        return {}


def _tamil_ok(result):
    """True if every Tamil classification exists and matches its English list length."""
    en_list = result.get("all_classifications", [])
    ta_list = result.get("all_classifications_ta")
    if not isinstance(ta_list, list) or len(ta_list) != len(en_list):
        return False
    for en, ta in zip(en_list, ta_list):
        if not (isinstance(ta, dict)
                and isinstance(ta.get("matched_sub_category_violences"), list)
                and len(ta["matched_sub_category_violences"]) == len(en["matched_sub_category_violences"])
                and all(k in ta for k in ("voilence", "sub_violence", "category_violence"))):
            return False
    return True


def _known_tamil(paths_matched):
    """Tamil already stored in the tables. A value equal to the English text is a placeholder, so it is ignored."""
    pairs = []
    for path, matched in paths_matched:
        by_name = {l["en"]: l["ta"] for l in path["labels"]}
        pairs += [(path["v"], path["v_ta"]), (path["s"], path["s_ta"]), (path["c"], path["c_ta"])]
        pairs += [(m, by_name.get(m)) for m in matched]
    return {en: ta.strip() for en, ta in pairs
            if ta and ta.strip() and ta.strip().lower() != str(en).strip().lower()}


async def _translate(items):
    """Translate, then the model checks its own Tamil against the English meaning and corrects it."""
    raw = await _chat([{"role": "user", "content": TAMIL_RULES
        + 'Return only JSON: {"translations": {"<english>": "<tamil>"}} using each English string exactly as the key.\n'
        + json.dumps(items, ensure_ascii=False)}])
    m = _json_map(raw, "translations")
    if not m:
        return {}

    pairs = {en: m.get(en, "") for en in items}
    raw = await _chat([{"role": "user", "content": TAMIL_RULES
        + "Below are English labels and their Tamil translations. For each one, first write in English what the Tamil "
          "actually means. Then compare that meaning with the English label in the context of abuse and violence. "
          "If the meaning differs, or the Tamil sounds unnatural, give a corrected Tamil. Otherwise repeat the same Tamil.\n"
          'Return only JSON: {"checked": {"<english>": {"meaning": "<english meaning of the Tamil>", "tamil": "<final Tamil>"}}}\n'
        + json.dumps(pairs, ensure_ascii=False)}])
    checked = _json_map(raw, "checked")
    for en in items:
        entry = checked.get(en)
        if isinstance(entry, dict) and entry.get("tamil"):
            if entry["tamil"] != m.get(en):
                print(f"[tamil] corrected: {en}")
            m[en] = entry["tamil"]
    return {en: m[en] for en in items if m.get(en)}


async def ensure_tamil(result, known):
    all_c = result.get("all_classifications", [])
    items = []
    for c in all_c:
        items += [c.get("voilence"), c.get("sub_violence"), c.get("category_violence")]
        items += c.get("matched_sub_category_violences", [])
    items = [x for x in dict.fromkeys(items) if x]

    m = dict(known)
    todo = [x for x in items if x not in m]
    if todo:
        translated = await _translate(todo)
        if not translated:
            print("[tamil] translation failed")
            return                              # English result is returned instead
        m.update(translated)
        print("[tamil] translated by model (not in the tables):", list(translated))

    def t(x):
        return m.get(x) or x                    # falls back to English, so list lengths always match

    def to_ta(c):
        return {
            "voilence": t(c.get("voilence")),
            "sub_violence": t(c.get("sub_violence")),
            "category_violence": t(c.get("category_violence")),
            "matched_sub_category_violences": [t(x) for x in c.get("matched_sub_category_violences", [])],
        }

    result["all_classifications_ta"] = [to_ta(c) for c in all_c]
    result["classification_ta"] = to_ta(result["classification"])
    result["metrics_ta"] = {
        "violence_types": [t(x) for x in result["metrics"]["violence_types"]],
        "sub_violence_types": [t(x) for x in result["metrics"]["sub_violence_types"]],
    }


# ---------------------------------------------------------------
# Main classification
# ---------------------------------------------------------------
# Severity. The rating the app shows must not depend only on how many labels matched:
# one label such as "rape" or child abuse is already critical.
MAX_RATING = 17            # the app's scale: 1-4 Mild/Low, 5-9 Moderate/Medium, 10+ Severe/High
HIGH_FLOOR, MEDIUM_FLOOR, CRITICAL_FLOOR = 11, 5, 15

_HIGH_RE = re.compile(
    r"\b(rap(e|ed|es|ing|ist|ists)|gang\s*rap\w*|sexual(ly)?\s+(assault\w*|abus\w*|violat\w*)|molest\w*|"
    r"child\s+(abus\w*|sexual\w*|molest\w*|rape\w*|marriage)|pocso|incest\w*|penetrat\w*|sodomi\w*|"
    r"kill(ed|ing|s)?|murder\w*|acid(\s+attack\w*)?|burn(ed|t|ing)?\s+(alive|me|her|him)|set\s+(me|her)\s+on\s+fire|"
    r"strangl\w*|chok(e|ed|ing)|kidnap\w*|abduct\w*|trafficked|trafficking|knife|stab(bed|bing)?|gun|"
    r"forced\s+(me\s+)?(to\s+have\s+)?sex|forced\s+himself|undress\w*\s+(me|her)|nude\s+(photo|video)s?)\b",
    re.I)
_MEDIUM_RE = re.compile(
    r"\b(harass\w*|grop\w*|bad\s+touch\w*|touch(es|ed|ing)?\s+(me|her)|stalk\w*|blackmail\w*|"
    r"beat(ing|en|s)?|hit(ting|s)?|slap\w*|kick\w*|punch\w*|dowry|threat\w*|eve\s*teas\w*|"
    r"lock(ed)?\s+(me|her)\s+(in|up)|confin\w*|molest\w*|sexual\w*|pressur\w*\s+for\s+sex)\b",
    re.I)
_HIGH_TA = ["பாலியல் வன்கொடுமை", "பலாத்கார", "வன்புணர்", "கற்பழி", "வல்லுறவு", "பாலியல் வல்லுறவு",
            "சீரழி", "ரேப்", "குழந்தை துஷ்பிரயோகம்", "குழந்தை பாலியல்", "சிறுமி", "சிறுவன்",
            "கொலை", "கொன்று", "கொன்று விடு", "ஆசிட்", "கடத்த", "கத்தி", "அரிவாள்", "துப்பாக்கி"]
_MEDIUM_TA = ["துன்புறுத்த", "தொந்தரவு", "அடித்", "அடி", "மிரட்ட", "வரதட்சணை", "பாலியல்", "தவறான தொடு"]

_HIGH_PATH = ["rape", "child", "pocso", "incest", "acid", "penetration", "sexual assault"]
_MEDIUM_PATH = ["sexual", "harass", "eve teasing", "assault", "dowry", "kidnap", "shame"]
_HIGH_LABELS = ["rape", "penetration", "sexual assault", "oral sex", "forced sex", "kidnap",
                "acid", "fire", "burning", "scalding"]


# Critical cases get a higher floor on the 1-17 rating: rape, or any sexual act towards a child
_CRITICAL_RE = re.compile(r"\b(rap(e|ed|es|ing|ist|ists)|gang\s*rap\w*|sexual(ly)?\s+assault\w*|molest\w*|pocso|"
                          r"incest\w*|penetrat\w*|sodomi\w*|acid\s+attack\w*|kill(ed|ing)?\s+(me|her)|murder\w*)\b", re.I)
_CRITICAL_TA_RE = re.compile(r"பலாத்கார|பலாத்கா|வன்புணர்|கற்ப(?!னை)[ழலளா]|கற்பை|வல்லுறவு|வன்கொடுமை|ரேப்|சீரழி|பாலியல் வன்")
_CRITICAL_TA = ["பலாத்கார", "வன்புணர்", "கற்பழி", "வல்லுறவு", "பாலியல் வன்கொடுமை", "ரேப்", "சீரழி", "கொலை", "ஆசிட்"]
_CHILD_RE = re.compile(r"\b(\d{1,2})[\s-]*(year|yr)s?[\s-]*old|\bage[d]?\s*(of\s*)?(\d{1,2})\b|(?<!\d)(\d{1,2})\s*வயது|"
                       r"\b(child|minor|schoolgirl|school\s+girl|little\s+girl|boy|toddler|kid)\b", re.I)
_SEXUAL_RE = re.compile(r"\b(touch\w*|grop\w*|body|bedroom|alone\s+with|sexual\w*|private\s+parts?|undress\w*|kiss\w*|"
                        r"molest\w*|rap\w*|inappropriate)\b", re.I)


def _is_child(text):
    for m in _CHILD_RE.finditer(text):
        num = m.group(1) or m.group(4) or m.group(5)
        if num is None or int(num) < 18:
            return True
    return any(w in text for w in ("சிறுமி", "சிறுவன்", "குழந்தை", "பள்ளி மாணவி"))


def _is_critical(text, all_labels):
    labels_text = " ".join(all_labels).lower()
    if _CRITICAL_RE.search(text) or _CRITICAL_TA_RE.search(text) or any(w in text for w in _CRITICAL_TA) or "rape" in labels_text:
        return True
    sexual = bool(_SEXUAL_RE.search(text)) or any(w in text for w in ("தொட", "பாலியல்", "உடல்", "படுக்கையறை", "தனியாக"))
    return _is_child(text) and sexual


def _severity_level(text, all_c, all_labels, model_severity):
    """Return "high", "medium" or "low" from the transcript words, the chosen categories/labels and the model's rating."""
    low = text.lower()
    path_text = " ".join(" ".join(str(f.get(k)) for k in ("voilence", "sub_violence", "category_violence"))
                         for f in all_c).lower()
    labels_text = " ".join(all_labels).lower()

    if (_is_critical(text, all_labels) or _HIGH_RE.search(text) or any(w in text for w in _HIGH_TA)
            or any(w in path_text for w in _HIGH_PATH)
            or any(w in labels_text for w in _HIGH_LABELS)
            or model_severity == "high"):
        return "high"
    if (_MEDIUM_RE.search(text) or any(w in text for w in _MEDIUM_TA)
            or any(w in path_text for w in _MEDIUM_PATH)
            or model_severity == "medium"):
        return "medium"
    return "low"


def _severity_rank(f):
    """Order categories so the most serious one becomes the primary (the one the app pre-selects)."""
    level = _severity_level("", [f], f["matched_sub_category_violences"], "")
    return {"high": 2, "medium": 1, "low": 0}[level]


async def _classify_category(text, path):
    matched = await match_labels(text, path, runs=1)        # runs=3 for majority vote
    if matched is None:
        return None
    # a label named in the transcript (e.g. "rape") must never be missed by the model
    low = text.lower()
    for l in path["labels"]:
        name = l["en"].strip().lower()
        if not _is_others(name) and len(name) >= 4 and name in low and l["en"] not in matched:
            matched.append(l["en"])
    matched = [l["en"] for l in path["labels"] if l["en"] in matched]   # keep table order
    by_name = {l["en"]: l["code"] for l in path["labels"]}
    return {
        "voilence": path["v"], "sub_violence": path["s"], "category_violence": path["c"],
        "matched_sub_category_violences": matched,
        "codes": {"violence_code": path["v_code"], "sub_violence_code": path["s_code"],
                  "category_code": path["c_code"],
                  "sub_category_codes": [by_name[m] for m in matched]},
    }


async def classify_text(text):
    tamil = is_tamil(text)

    try:
        paths = await get_taxonomy()
    except Exception as e:
        print(f"[taxonomy] could not load violence master tables: {e}")
        return None

    ctx = await detect_context(text)
    paths, hint = filter_paths(paths, ctx, text)
    picked = await pick_categories(text, paths, hint)
    if picked is None:
        return None
    idxs, model_severity = picked

    # rape named in the transcript (English or Tamil): the category holding a "Rape" label must be checked
    if _CRITICAL_RE.search(text) or _CRITICAL_TA_RE.search(text):
        rape_idxs = [i for i, p in enumerate(paths) if any(l["en"].strip().lower() == "rape" for l in p["labels"])]
        idxs = (rape_idxs[:1] + [i for i in idxs if i not in rape_idxs[:1]])[:MAX_CATEGORIES]

    # sexual abuse by someone at work: the workplace sexual-harassment category must be checked first
    if ctx["work"] and ctx["sexual"] and idxs is not None:
        wp = [i for i, p in enumerate(paths) if _vtype(p) == "workplace" and "sexual" in str(p["c"]).lower()]
        if wp:
            idxs = (wp[:1] + [i for i in idxs if i != wp[0]])[:MAX_CATEGORIES]

    casual = not idxs
    if casual:
        all_c = [dict(CASUAL_EN)]
        primary = all_c[0]
        pm = []
    else:
        found = await asyncio.gather(*[_classify_category(text, paths[i]) for i in idxs])
        pairs = [(paths[i], f) for i, f in zip(idxs, found) if f is not None]
        if not pairs:
            return None
        all_c = [f for _, f in pairs]
        pm = [(p, f["matched_sub_category_violences"]) for p, f in pairs]
        # primary = the most serious category, then the one with the most matched labels (first wins a tie)
        primary = max(all_c, key=lambda f: (_severity_rank(f), len(f["matched_sub_category_violences"])))

    # total over ALL categories, each label counted once
    count = 0 if casual else sum(len(f["matched_sub_category_violences"]) for f in all_c)
    violence_types = [] if casual else list(dict.fromkeys(f["voilence"] for f in all_c))
    sub_types = [] if casual else list(dict.fromkeys(f["sub_violence"] for f in all_c))
    all_labels = [] if casual else [m for f in all_c for m in f["matched_sub_category_violences"]]
    print("[match]", count, [(f["category_violence"], f["matched_sub_category_violences"]) for f in all_c])

    # rating, level and alert are computed in code, not by the model
    if casual:
        level, rating, alert = "none", 0, "No Action Required"
    else:
        level = _severity_level(text, all_c, all_labels, model_severity)
        floor = {"high": HIGH_FLOOR, "medium": MEDIUM_FLOOR, "low": 1}[level]
        if _is_critical(text, all_labels):
            level, floor = "high", CRITICAL_FLOOR
        rating = min(MAX_RATING, max(count, floor))
        # a high count can raise the level too (10+ matched labels is severe even without a critical word)
        if rating >= HIGH_FLOOR:
            level = "high"
        elif rating >= MEDIUM_FLOOR:
            level = "medium"
        alert = {"high": "Call the police", "medium": "Counseling is needed",
                 "low": "Counseling with our client"}[level]
    print("[severity]", level, rating, "model:", model_severity)

    def public(f):
        return {k: v for k, v in f.items() if k != "codes"}

    result = {
        "classification": public(primary),
        "all_classifications": [public(f) for f in all_c],
        "metrics": {"matched_count": count, "categories_count": 0 if casual else len(all_c),
                    "violence_types": violence_types, "sub_violence_types": sub_types},
        "alert_action": alert,
        "abuse_level": {"high": "High", "medium": "Medium", "low": "Low"}.get(level, ""),   # always English
        "violence_rating": rating,
    }
    if not casual:
        result["codes"] = primary["codes"]
        result["all_codes"] = [f["codes"] for f in all_c]

    if tamil:
        result["alert_action_ta"] = ALERTS_TA[alert]
        if casual:
            result["classification_ta"] = dict(CASUAL_TA)
            result["all_classifications_ta"] = [dict(CASUAL_TA)]
            result["metrics_ta"] = {"violence_types": [], "sub_violence_types": []}
        else:
            await ensure_tamil(result, _known_tamil(pm))

        # Tamil only: replace the English values with the Tamil ones
        if _tamil_ok(result):
            result["classification_en"] = dict(result["classification"])   # remove if you don't need English
            result["classification"] = result.pop("classification_ta")
            result["all_classifications"] = result.pop("all_classifications_ta")
            result["metrics"].update(result.pop("metrics_ta"))
            result["alert_action"] = result.pop("alert_action_ta")
        else:
            for k in ("classification_ta", "all_classifications_ta", "metrics_ta"):
                result.pop(k, None)
            result.pop("alert_action_ta", None)

    return result
