from fastapi import APIRouter, HTTPException, File, UploadFile
from dotenv import load_dotenv
import os
import re
import json
import asyncio
import time
from typing import Dict, Any, List, Optional, Tuple
import requests

from database import SessionLocal
import models

load_dotenv()

router = APIRouter(prefix="/api/Voice", tags=["Voice"])

SPEECHMATICS_API_KEY = os.getenv("SPEECHMATICS_API_KEY")
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")

# Android App Scale: 1-4 Mild (Green), 5-9 Moderate (Yellow), 10+ Severe (Red)
MAX_RATING = 17
HIGH_FLOOR = 11      # Sets "Severe (தீவிரமானது)" in Red on Android
MEDIUM_FLOOR = 5    # Sets "Moderate (மிதமானது)" in Yellow
CRITICAL_FLOOR = 15  # For Rape, POCSO, Child sexual abuse, Acid attacks

ALERTS_EN = {
    "high": "Call the police",
    "medium": "Counseling is needed",
    "low": "Counseling with our client",
    "none": "No Action Required"
}
ALERTS_TA = {
    "Call the police": "காவல்துறையை அழைக்கவும்",
    "Counseling is needed": "ஆலோசனை தேவை",
    "Counseling with our client": "வாடிக்கையாளருடன் ஆலோசனை",
    "No Action Required": "நடவடிக்கை தேவையில்லை"
}


# =====================================================================
# IN-MEMORY HIERARCHICAL TREE (Cached from PostgreSQL Master Tables)
# =====================================================================
TAXONOMY_TTL = 60
_TREE_CACHE = {"tree": None, "by_category_code": {}, "at": 0.0}

def _build_hierarchy_tree_from_db():
    """Builds a structured 4-level navigation tree from the DB."""
    db = SessionLocal()
    try:
        def get_active(model):
            return db.query(model).filter(model.MASTER_STATUS == "Active").order_by(model.CODE).all()

        v_list = get_active(models.ViolenceMaster)
        sv_list = get_active(models.SubViolenceMaster)
        cat_list = get_active(models.CategoryViolenceMaster)
        sub_cat_list = get_active(models.SubCategoryViolenceMaster)
    finally:
        db.close()

    tree = {}
    by_category_code = {}

    for v in v_list:
        tree[v.CODE] = {
            "code": v.CODE,
            "name": v.VIOLENCE,
            "name_ta": v.TAMIL_VIOLENCE or v.VIOLENCE,
            "sub_violences": {}
        }

    for sv in sv_list:
        if sv.VIOLENCE_CODE in tree:
            tree[sv.VIOLENCE_CODE]["sub_violences"][sv.CODE] = {
                "code": sv.CODE,
                "name": sv.SUB_VIOLENCE,
                "name_ta": sv.TAMIL_SUB_VIOLENCE or sv.SUB_VIOLENCE,
                "categories": {}
            }

    labels_by_cat = {}
    for sc in sub_cat_list:
        labels_by_cat.setdefault(sc.CATEGORY_CODE, []).append({
            "code": sc.CODE,
            "name": sc.SUB_CATEGORY,
            "name_ta": sc.TAMIL_SUB_CATEGORY or sc.SUB_CATEGORY
        })

    for cat in cat_list:
        v_code = cat.VIOLENCE_CODE
        sv_code = cat.SUB_VIOLENCE_CODE
        if v_code in tree and sv_code in tree[v_code]["sub_violences"]:
            cat_labels = labels_by_cat.get(cat.CODE, [])
            cat_obj = {
                "code": cat.CODE,
                "v_code": v_code,
                "sv_code": sv_code,
                "v_name": tree[v_code]["name"],
                "v_name_ta": tree[v_code]["name_ta"],
                "sv_name": tree[v_code]["sub_violences"][sv_code]["name"],
                "sv_name_ta": tree[v_code]["sub_violences"][sv_code]["name_ta"],
                "name": cat.CATEGORY,
                "name_ta": cat.TAMIL_CATEGORY or cat.CATEGORY,
                "labels": cat_labels
            }
            tree[v_code]["sub_violences"][sv_code]["categories"][cat.CODE] = cat_obj
            by_category_code[cat.CODE] = cat_obj

    return tree, by_category_code


async def get_taxonomy_tree():
    now = time.time()
    if _TREE_CACHE["tree"] and (now - _TREE_CACHE["at"] < TAXONOMY_TTL):
        return _TREE_CACHE["tree"], _TREE_CACHE["by_category_code"]

    tree, by_code = await asyncio.to_thread(_build_hierarchy_tree_from_db)
    _TREE_CACHE["tree"] = tree
    _TREE_CACHE["by_category_code"] = by_code
    _TREE_CACHE["at"] = now
    return tree, by_code


# =====================================================================
# FAST LLM CALL HELPER (Groq / OpenRouter)
# =====================================================================
async def _call_llm_json(prompt: str) -> Optional[Dict]:
    messages = [{"role": "user", "content": prompt}]
    if GROQ_API_KEY:
        for model in ["llama-3.3-70b-versatile", "openai/gpt-oss-120b"]:
            try:
                resp = await asyncio.to_thread(
                    requests.post,
                    "https://api.groq.com/openai/v1/chat/completions",
                    headers={"Authorization": f"Bearer {GROQ_API_KEY}", "Content-Type": "application/json"},
                    json={"model": model, "messages": messages, "temperature": 0.0, "response_format": {"type": "json_object"}},
                    timeout=20
                )
                if resp.status_code == 200:
                    return json.loads(resp.json()["choices"][0]["message"]["content"])
            except Exception as e:
                print(f"[groq:{model}] error: {e}")

    if OPENROUTER_API_KEY:
        try:
            resp = await asyncio.to_thread(
                requests.post,
                "https://openrouter.ai/api/v1/chat/completions",
                headers={"Authorization": f"Bearer {OPENROUTER_API_KEY}", "Content-Type": "application/json"},
                json={"model": "meta-llama/llama-3.3-70b-instruct:free", "messages": messages, "temperature": 0.0, "response_format": {"type": "json_object"}},
                timeout=25
            )
            if resp.status_code == 200:
                return json.loads(resp.json()["choices"][0]["message"]["content"])
        except Exception as e:
            print(f"[openrouter] error: {e}")

    return None


# =====================================================================
# AGENT HIERARCHICAL DRILL-DOWN STEPS
# =====================================================================

# STEP 1: Main Violence Classifier
async def step1_predict_violence(text: str, tree: Dict) -> Tuple[Optional[int], bool]:
    options = [{"code": v["code"], "name": v["name"]} for v in tree.values()]
    prompt = (
        "You are an expert Crisis Evaluation Agent. Read this voice transcript (English or Tamil).\n"
        "Identify the primary Main Violence type based on the perpetrator and location context.\n"
        "Context Guidelines:\n"
        "- 'domestic violence': Husband, partner, in-laws, or family members at home.\n"
        "- 'workplace violence': Boss, manager, colleague, coworker, or workplace.\n"
        "- 'sexual violence': Strangers, harassment during travel, street eve-teasing.\n"
        "- 'harassment / other violence': General violence, non-family physical harm, or abuse.\n"
        "If it is casual conversation or describes NO abuse, set is_casual to true.\n\n"
        f"Available Main Violences: {json.dumps(options)}\n\n"
        "Respond ONLY in JSON:\n"
        '{"is_casual": false, "selected_violence_code": <int>, "reason": "<explanation>"}\n\n'
        f'Transcript: """{text}"""'
    )
    res = await _call_llm_json(prompt)
    if not res or res.get("is_casual", False):
        return None, True
    return res.get("selected_violence_code"), False


# STEP 2: Sub-Violence Classifier (Pruned under selected Violence)
async def step2_predict_sub_violences(text: str, v_code: int, tree: Dict) -> List[int]:
    sv_dict = tree.get(v_code, {}).get("sub_violences", {})
    if not sv_dict:
        return []

    options = [{"code": sv["code"], "name": sv["name"]} for sv in sv_dict.values()]
    prompt = (
        "Read the transcript. From the sub-branches below, choose ALL that apply (up to 2).\n"
        "Note: Look closely for partner conflicts, family violence, or substance abuse (alcohol/drugs).\n\n"
        f"Available Sub-Violences: {json.dumps(options)}\n\n"
        "Respond ONLY in JSON:\n"
        '{"selected_sub_violence_codes": [<int>, ...]}\n\n'
        f'Transcript: """{text}"""'
    )
    res = await _call_llm_json(prompt)
    if not res:
        return [list(sv_dict.keys())[0]]
    codes = res.get("selected_sub_violence_codes", [])
    valid = [c for c in codes if c in sv_dict]
    return valid if valid else [list(sv_dict.keys())[0]]


# STEP 3: Category Violence Classifier (Pruned under selected Sub-Violences)
async def step3_predict_categories(text: str, v_code: int, sv_codes: List[int], tree: Dict) -> List[int]:
    cat_options = []
    v_data = tree.get(v_code, {})
    for sv_c in sv_codes:
        categories = v_data.get("sub_violences", {}).get(sv_c, {}).get("categories", {})
        for cat in categories.values():
            cat_options.append({"code": cat["code"], "name": cat["name"], "sub_violence": cat["sv_name"]})

    if not cat_options:
        return []

    prompt = (
        "Read the transcript. From the categories below, choose ALL that apply to what the speaker experienced (up to 3).\n\n"
        f"Available Categories: {json.dumps(cat_options)}\n\n"
        "Respond ONLY in JSON:\n"
        '{"selected_category_codes": [<int>, ...]}\n\n'
        f'Transcript: """{text}"""'
    )
    res = await _call_llm_json(prompt)
    if not res:
        return [cat_options[0]["code"]]
    codes = res.get("selected_category_codes", [])
    valid_codes = {c["code"] for c in cat_options}
    chosen = [c for c in codes if c in valid_codes]
    return chosen if chosen else [cat_options[0]["code"]]


# STEP 4: Sub-Category Checkbox Extractor & Agent Clinical Severity Assessment
async def step4_extract_labels_and_assess_severity(text: str, chosen_cat_codes: List[int], by_code: Dict) -> List[Dict]:
    tasks = []
    for code in chosen_cat_codes:
        cat_data = by_code.get(code)
        if not cat_data:
            continue
        valid_labels = [l["name"] for l in cat_data["labels"] if l["name"].strip().lower() != "others"]
        prompt = (
            f"You are the Crisis Assessment Agent evaluating the category '{cat_data['name']}'.\n"
            "Review the voice transcript (Tamil or English; understand the true meaning and colloquial slang).\n\n"
            f"Allowed Labels: {json.dumps(valid_labels)}\n\n"
            "INSTRUCTIONS:\n"
            "1. Extract EVERY label that the speaker described (directly or by colloquial/implicit meaning).\n"
            "2. CLINICAL SEVERITY RULES (Agent Decision):\n"
            "   - If there is ANY physical abuse (striking, pushing, bodily harm, physical injury, trampling), "
            "ANY alcoholic/substance abuse (intoxication, drinking, addiction debts, booze-fueled violence), "
            "or ANY sexual abuse (assault, rape, non-consensual acts): You MUST set 'severity' to 'high' (do NOT classify as mild or moderate).\n"
            "   - If it is verbal/emotional abuse, non-violent harassment, or non-physical stalking: set 'severity' to 'medium'.\n"
            "   - If it is minor dispute without threats or violence: set 'severity' to 'low'.\n"
            "   - Set 'is_critical' to true only if there is rape, child sexual abuse/POCSO, acid attack, or life threats.\n\n"
            "Respond ONLY in JSON:\n"
            "{\n"
            '  "matched_labels": ["<label>", ...],\n'
            '  "severity": "high|medium|low",\n'
            '  "is_critical": true|false,\n'
            '  "clinical_rationale": "<short 1-sentence reasoning for the severity>"\n'
            "}\n\n"
            f'Transcript: """{text}"""'
        )
        tasks.append((cat_data, _call_llm_json(prompt)))

    results = []
    for cat_data, coro in tasks:
        res = await coro
        matched = res.get("matched_labels", []) if res else []
        severity = str(res.get("severity", "low")).lower() if res else "low"
        is_critical = bool(res.get("is_critical", False)) if res else False

        valid_set = {l["name"].strip().lower(): l for l in cat_data["labels"]}
        verified = [valid_set[m.strip().lower()]["name"] for m in matched if m.strip().lower() in valid_set]

        if not verified and cat_data["labels"]:
            verified = [cat_data["labels"][0]["name"]]

        results.append({
            "category_data": cat_data,
            "matched_labels": verified,
            "severity": severity,
            "is_critical": is_critical
        })

    return results


# =====================================================================
# STEP 5: FINAL PACKAGING (Zero Hardcoded String Matching)
# =====================================================================
def is_tamil(text: str) -> bool:
    tamil = len(re.findall(r"[\u0B80-\u0BFF]", text))
    letters = len(re.findall(r"[^\W\d_]", text))
    return letters > 0 and (tamil / letters) > 0.3


def build_final_response(results: List[Dict], transcript: str, tamil: bool) -> Dict[str, Any]:
    all_classifications = []
    all_codes = []
    all_matched_labels = []

    for item in results:
        cat = item["category_data"]
        labels_en = item["matched_labels"]

        label_map = {l["name"]: l for l in cat["labels"]}
        labels_ta = [label_map[m]["name_ta"] for m in labels_en if m in label_map]
        codes_sub = [label_map[m]["code"] for m in labels_en if m in label_map]

        all_matched_labels.extend(labels_en)
        all_classifications.append({
            "voilence": cat["v_name_ta"] if tamil else cat["v_name"],
            "sub_violence": cat["sv_name_ta"] if tamil else cat["sv_name"],
            "category_violence": cat["name_ta"] if tamil else cat["name"],
            "matched_sub_category_violences": labels_ta if tamil else labels_en
        })
        all_codes.append({
            "violence_code": cat["v_code"],
            "sub_violence_code": cat["sv_code"],
            "category_code": cat["code"],
            "sub_category_codes": codes_sub
        })

    total_count = len(set(all_matched_labels))

    # =============================================================
    # PURELY AGENT-DRIVEN SEVERITY DECISION (NO HARDCODED REGEX/STRINGS)
    # =============================================================
    severity_order = {"high": 2, "medium": 1, "low": 0}
    # Pick the highest severity decided by the agent across the categories
    agent_level = max(
        (item.get("severity", "low") for item in results),
        key=lambda x: severity_order.get(x, 0)
    )
    is_critical = any(item.get("is_critical", False) for item in results)

    if is_critical:
        floor = CRITICAL_FLOOR  # 15
        level = "high"
    elif agent_level == "high":
        floor = HIGH_FLOOR      # 11 -> Severe (Red) on Android App
        level = "high"
    elif agent_level == "medium" or total_count >= 5:
        floor = MEDIUM_FLOOR    # 5  -> Moderate (Yellow) on Android App
        level = "medium"
    else:
        floor = 1               # 1-4 -> Mild (Green)
        level = "low"

    final_rating = min(MAX_RATING, max(total_count, floor))

    alert_en = ALERTS_EN.get(level, "Counseling with our client")
    alert_final = ALERTS_TA.get(alert_en, alert_en) if tamil else alert_en

    return {
        "classification": all_classifications[0],
        "all_classifications": all_classifications,
        "metrics": {
            "matched_count": total_count,
            "categories_count": len(all_classifications),
            "violence_types": list(dict.fromkeys(c["voilence"] for c in all_classifications)),
            "sub_violence_types": list(dict.fromkeys(c["sub_violence"] for c in all_classifications))
        },
        "alert_action": alert_final,
        "abuse_level": {"high": "High", "medium": "Medium", "low": "Low"}[level],
        "violence_rating": final_rating,
        "codes": all_codes[0],
        "all_codes": all_codes
    }


def _build_casual_response(tamil: bool) -> Dict[str, Any]:
    alert = ALERTS_TA["No Action Required"] if tamil else "No Action Required"
    return {
        "classification": {
            "voilence": "சாதாரண உரையாடல்" if tamil else "Casual Conversation",
            "sub_violence": "இல்லை" if tamil else "None",
            "category_violence": "இல்லை" if tamil else "None",
            "matched_sub_category_violences": []
        },
        "all_classifications": [],
        "metrics": {"matched_count": 0, "categories_count": 0, "violence_types": [], "sub_violence_types": []},
        "alert_action": alert,
        "abuse_level": "",
        "violence_rating": 0
    }


# =====================================================================
# MAIN WORKFLOW & ENDPOINTS
# =====================================================================
async def classify_text(text: str) -> Optional[Dict[str, Any]]:
    """Executes the exact 4-stage hierarchical drill-down."""
    tamil = is_tamil(text)
    tree, by_code = await get_taxonomy_tree()

    # Step 1: Predict Main Violence (prunes other 3 main violences)
    v_code, is_casual = await step1_predict_violence(text, tree)
    if is_casual or not v_code or v_code not in tree:
        return _build_casual_response(tamil)

    # Step 2: Predict Sub-Violence under selected Violence
    sv_codes = await step2_predict_sub_violences(text, v_code, tree)

    # Step 3: Predict Category Violence under selected Sub-Violences
    cat_codes = await step3_predict_categories(text, v_code, sv_codes, tree)

    # Step 4: Extract Sub-Category Labels & Evaluate Severity via Agent
    results = await step4_extract_labels_and_assess_severity(text, cat_codes, by_code)

    if not results:
        return _build_casual_response(tamil)

    # Step 5: Purely Agent-Driven Response Formatting
    return build_final_response(results, text, tamil)


@router.post("/ProcessAudio")
async def process_audio(file: UploadFile = File(...)):
    if not file:
        raise HTTPException(status_code=400, detail="No file uploaded.")

    try:
        audio_bytes = await file.read()

        transcribed_text = await transcribe_audio(audio_bytes, file.filename)
        if not transcribed_text:
            raise HTTPException(status_code=422, detail="Could not transcribe audio.")

        cleaned = transcribed_text.strip().lower().strip(".!?, ")
        if len(transcribed_text.split()) < 3 or cleaned in {"you", "thank you", "thanks for watching", "bye"}:
            raise HTTPException(status_code=422, detail="No clear speech detected. Please speak again.")

        classification_json = await classify_text(transcribed_text)
        if not classification_json:
            raise HTTPException(status_code=502, detail="Classification failed. Check server logs.")

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

    if SPEECHMATICS_API_KEY:
        try:
            sm_url = "https://asr.api.speechmatics.com/v2/jobs"
            sm_headers = {"Authorization": f"Bearer {SPEECHMATICS_API_KEY}"}
            config_data = {"type": "transcription", "transcription_config": {"language": "auto"}}

            response = await asyncio.to_thread(
                requests.post, sm_url, headers=sm_headers,
                data={"config": json.dumps(config_data)},
                files={"data_file": (filename, audio_bytes)}, timeout=60
            )
            response.raise_for_status()
            job_id = response.json()["id"]

            for _ in range(120):
                status_resp = await asyncio.to_thread(requests.get, f"{sm_url}/{job_id}", headers=sm_headers, timeout=30)
                status = status_resp.json()["job"]["status"]
                if status == "done":
                    break
                elif status in ["rejected", "deleted", "expired"]:
                    raise Exception(f"Job failed with status: {status}")
                await asyncio.sleep(1)

            transcript_resp = await asyncio.to_thread(requests.get, f"{sm_url}/{job_id}/transcript?format=txt", headers=sm_headers, timeout=30)
            transcribed_text = transcript_resp.content.decode("utf-8").strip()
        except Exception as e:
            print(f"[speechmatics] error: {e}")

    if not transcribed_text and GROQ_API_KEY:
        try:
            url = "https://api.groq.com/openai/v1/audio/transcriptions"
            headers = {"Authorization": f"Bearer {GROQ_API_KEY}"}
            files = {"file": (filename, audio_bytes, "application/octet-stream")}
            data = {"model": "whisper-large-v3", "response_format": "json"}

            response = await asyncio.to_thread(requests.post, url, headers=headers, files=files, data=data, timeout=60)
            transcribed_text = (response.json().get("text") or "").strip()
        except Exception as e:
            print(f"[groq-whisper] error: {e}")

    return transcribed_text
