from fastapi import APIRouter, HTTPException, File, UploadFile
from dotenv import load_dotenv
import os
import re
import json
import asyncio
import requests

load_dotenv()

router = APIRouter(prefix="/api/Voice", tags=["Voice"])

# These names must match your .env file exactly
SPEECHMATICS_API_KEY = os.getenv("SPEECHMATICS_API_KEY")
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")


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

TAMIL_INSTRUCTION = """
**Language rule:** The transcript is in Tamil.
Keep "classification" and "alert_action" exactly in English as defined above, because they are used for matching.
Additionally, add this field to the JSON, translating every value into natural, simple Tamil:

"classification_ta": {
  "voilence": "<Tamil>",
  "sub_violence": "<Tamil>",
  "category_violence": "<Tamil>",
  "matched_sub_category_violences": ["<Tamil label_1>", "<Tamil label_2>"]
}

Translation rules:
- These labels come from a domestic violence and abuse support system in Tamil Nadu. Translate by meaning, not word for word.
- Use noun forms for actions, for example "hitting" as அடித்தல்.
- Use these exact Tamil forms when these labels appear:
  "domestic violence" = குடும்ப வன்முறை
  "intimate partner violence" = நெருங்கிய துணையால் நிகழும் வன்முறை
  "physical abuse" = உடல் ரீதியான துன்புறுத்தல்
  "scalding & burning" = சூடு வைத்தல் / எரித்தல் (never காய்ச்சல், which means fever)
  "restricting movement" = நடமாட்டத்தைக் கட்டுப்படுத்துதல்
  "with hold food" = உணவு மறுத்தல்
- The Tamil list must have the same number of items, in the same order, as the English list.
"""
PROMPT_TEMPLATE = """
**Role:** 
You are an expert crisis assessment and classification AI. Your task is to analyze user transcripts (transcribed from audio) and accurately classify the nature of the violence or harassment described, following a strict hierarchical structure. Based on the number of specific violations detected, you will generate a mandatory safety alert.

**Context (The Knowledge Base):**
Below is the hierarchical JSON database containing the valid mappings of voilence -> sub violence -> category violence -> sub-category-violence. You must strictly rely on this database for your classification.

<json_database>
{
  "domestic violence": {
    "intimate partner violence": {
      "physical abuse": [
        "hitting", "pushing", "kicking", "hair pulling", "slapping", "assault & ashame", "rough handling", "scalding & burning", "physical punishments", "inappropriate uncomfortable feeling", "involuntary isolation of confinement", "misuse of medication", "forcing an action", "with hold food", "restricting movement", "unauthorised command to obey", "others"
      ],
      "emotional abuse": [
        "humiliating", "constantly critising", "threatening", "shouting & scolding", "blaming", "scape goating", "forcing to do degrading acts", "neglecting emotionally", "beholding needs", "exposing to upsetting events", "failing to address needs", "forced isolation", "manipulating", "failing to express positive feeling", "others"
      ],
      "finacial abuse": [
        "sabotaging job", "preventing to opt a career", "purposefully spoiling work", "with holding necessities", "controlling with mone", "power game with money", "constantly pleading for money", "voluntary usage without inform consent", "controlling the finances", "blaming finances", "controlling spending activities", "with drawing from financial commitment", "frequently pawn brokering", "others"
      ],
      "sexual abuse": [
        "sexual assault", "rape", "forced sex", "threatening for sexual activity", "manipulation for sexual activity", "uses alcohol or drugs to engage in sexual activity", "demands sex when i am injured", "sick or tired", "pressures to record pornography", "forces to try unusual sex postures", "insults or embarrasses me on a sexual way", "controls access to or refuse to use contraception", "takes sexual images for pleasure & fun", "shares sexual images of their partner \\"revenge\\"", "using jealousy as an excuse to control the dress", "others"
      ],
      "social abuse": [
        "scaring for unknown reasons", "controlling day to day activity", "stops me from seeing friends", "family & others", "isolation", "withdraws information", "preventing from participation in social activities", "publicizing the participation", "needing to know everyone you have seen or see planning", "checking or interfering with phone", "mail", "social media", "using social media to spread lies and damages", "tell lies about you to friends & family", "deliberately make me delayed to events", "appointments or meetings", "embarrasses me in front of every one", "restrict access to car & other form of transport", "others"
      ],
      "varbal abuse": [
        "yelling", "criticising", "swearing and labelling by hurtful names", "demanding", "ordering", "gas lighting", "manipulating", "patronising", "blaming", "ridiculing", "shaming", "swearing / belittle", "insulting", "silent treatment", "others"
      ],
      "cultural abuse": [
        "tells me you are \\"too much\\" or \\"not enough\\" of their culture", "labels i am not obey cultural / religion ethics", "forcing cultural / spiritual practices upon someone", "constructing false beliefs on cultural / religious prospects", "preventing from involving in religious praticies", "ignores & forces to go against dietary constraints", "twisting the ideologies to go against the belief of god", "threatening to out of my own beliefs", "others"
      ],
      "patenal parental abuse": [
        "maltreatment", "wrong guidance by the parents", "unhelpful to render support at hard times", "opposing at difficult situations", "neglected by parents for shelter", "maintenance", "creating obstacles for self development", "presenting contradictory ideas on well being", "negative or unfavourable influence", "exploiting for the benefit of finance", "others"
      ]
    },
    "family violence": {
      "dowry related abuse": [
        "intentional setting to fire or acid attacks", "psychological manipulation", "voluntary drive to fed worthless", "isolating", "compelled loneliness", "obliging to forced demands", "preventing me from accessing my own resources", "controls my daily life and expands power", "others"
      ],
      "intra women relational conflict": [
        "exercising harm in public", "disturbing the emotional well being", "escalation to abusive situations", "blocking support network", "leaving me financially vulnerable", "others"
      ],
      "violence by any member of the family": [
        "making me powerless", "forcing to obey irrational set of practices", "blaming on the character", "robbing the sense of identify", "burdening with household responsibilities", "abolishing the recreating or rehabilitation activities", "others"
      ]
    },
    "substance use disorder": {
      "finacuial explotiation": [
        "drain in family finances due to direct purchase costs", "destabilizing household like selling jewels", "vessels & ones", "increased medical coast as effect of alcoholism & addiction", "financial burden of debits", "loans and high interest exorbitant", "per petuating cycle of poverty"
      ],
      "smoking and drug consumption": [
        "lack of trust issues", "emotional stress of witnessing the consumption activity", "unhealthy communication patterns", "violence due to dosing behaviour", "side effects of secondary smoking"
      ],
      "women ban for work": [
        "underestimating the ability to work", "discouraging from exhibiting skills", "foreign to quit job", "operating the background by constant follow up chasing", "gathering evidence of functions at work with co workers"
      ],
      "suspecting women on affairs at work place": [
        "none recognizing the inherent worth", "devaluing the work contributions", "with holding growing opportunities", "failure to create a respectable environment", "encouraging anonymous communication"
      ]
    }
  },
  "workplace violence": {
    "criminal intent": {
      "commiting a crime": [
        "motivating to involve in robbery", "forcing to be involved in strikes", "decreased productivity", "disturbing workplace environment", "increasing stress by work target", "creating the risk to health", "exchanging cash", "changing shifts without", "bullying", "emotional abuse & abuse", "threatened beliefs about the future", "others"
      ]
    },
    "ideological violence": {
      "sexual harassment": [
        "request for sexual favour", "unwelcome sexual advances or conduct", "injustices in punishment", "employer fails to address known instance of sexual harassment", "failing to investigate the complaints", "gender favours", "attacker occupying the position of power", "lack of adequate security & protection system", "failure to acknowledge individual right to file a law suit", "others"
      ],
      "political, religious threats": [
        "manifestation of opposing ideologies", "occurrence of hate crimes", "target attacks", "active shooter incidents", "working against organisation myths", "suspicion with former co-workers", "others"
      ],
      "intimidated by specific group": [
        "terrorism", "religious extremism", "involve in ideology", "back stabbing deviating from own beliefs", "involvement in political violence", "targeting opposing ideologies", "others"
      ],
      "intimidated by unions": [
        "forced involvement in labour disputes", "weakening existing unions", "not obeying union rules", "resistance to strikes and lock out", "resisting union ideologies", "against the trust of the union policies", "suppressing union participation", "others"
      ]
    },
    "worker on worker": {
      "aggression": [
        "violent behaviour", "damaging social status", "stalking", "lack of mediations to conflict", "others"
      ],
      "humiliation bullying": [
        "helplessness to cope humiliation", "hostility", "witnessing unfairness", "devaluation of the self", "others"
      ],
      "groupism": [
        "inducting in unhealthy behaviours", "involving in delinquency", "suggestion to adopt reckless activities", "creating a toxic environment", "others"
      ],
      "competition in perfermance": [
        "jealousy", "back stabbing", "pulling legs", "lack of empathy", "others"
      ]
    },
    "personal relationalship": {
      "victims of domestic violence": [
        "troubled both at home and workplace", "lost productivity", "increased stress", "unbalanced power distribution", "others"
      ],
      "spouses of co workers": [
        "suspicion", "public labelling", "engaging in conflict in public", "annoying & negotiation", "others"
      ],
      "family members as co workers": [
        "lost trust in work place", "neglected well being", "irrealistic perception", "negative attitudes", "others"
      ],
      "extramarital affairs at work place": [
        "behaviour changes", "vulnerability to new relationships", "volatile situation or work place", "others"
      ]
    }
  },
  "sexual violence": {
    "sexual harasment": {
      "bad touch during travel": [
        "experience of a harmful acts", "climate o fear", "environment of discomforts", "restricted freedom of movement", "others"
      ],
      "unwelcoming sexual remarks": [
        "catcalling", "exhibition", "received a suggestive comments about the appearance", "offensive jokes of sexual nature", "others"
      ],
      "sexual assault, kidnap threat": [
        "stalking to connect through phone or social media", "one side love chasing", "refuse gestures and comments", "others"
      ],
      "gestures, postures and leering": [
        "staring without a specific purpose", "unwanted attension of sexual nature", "hashing", "inapproriate digital messages", "others"
      ],
      "body shaming": [
        "uncomfortable remarks about the body shape", "breach of boundaries", "alarming emotional attacks", "distressful questioning", "others"
      ]
    },
    "assault & rape": {
      "demonstration of power": [
        "exerting influence on decision making", "with holding freedom of speech", "unreasonable restriction in public places", "strict adherence to rules without regularities", "others"
      ],
      "irrational sexual demands": [
        "dis obedience to obliged empathy", "power struggles at public spaces", "denial of rights", "lack of respected and dignity", "others"
      ],
      "isolation tactics": [
        "physical distancing from others", "lieng & manipulating about a social dysfunction", "limiting interaction with public", "restricting & avoiding gathering", "others"
      ],
      "usage of drugs and induction": [
        "public toxication", "drug possession", "public consumption of illegal substance", "creating public nuisance", "others"
      ]
    },
    "relationship affairs": {
      "anonymous communication": [
        "inducing shyness", "lack of accountability in communication", "sharing sensitive information", "erosion of trust statements", "others"
      ],
      "emotional attacks": [
        "creating a confused state of mind", "inducing loneliness by with drawal", "fostering emotional dependence", "creating a sense of guilt", "others"
      ],
      "provocation by appearances": [
        "voluntarity hurting the partner", "blackmailing", "creating a insecure environment", "provoking jealousy", "others"
      ],
      "inducing psychological pain": [
        "inducing negative feeling of separation", "sucidal ideation", "frequently embarking relationship conflicts", "manifesting a feeling of emptiness", "shame", "debihitating pain", "others"
      ],
      "distracting": [
        "excessive mobile phone engagement", "pre occupied thoughts", "exagerating mis understanding", "work alcoholism of one of the partner", "others"
      ]
    },
    "eve teasing": {
      "sexually suggestive remarks": [
        "whistling", "using vulgar language", "intimidating looks", "cyber stalking", "others"
      ],
      "brusting to gain attension": [
        "induction to feel ashamed"
      ],
      "catcalls, grouping & expanding vulgarity": [
        "playful, hurtful teasing", "others"
      ]
    },
    "shame, mapping, acid attacks": {
      "inducing fear": [
        "demonstration of suicide attends"
      ],
      "taking control over bodies": [
        "exaggerated feeling of love & care"
      ],
      "fear of public mark of shame": [
        "retribution for women's rejection", "others"
      ]
    }
  },
  "harassment / other violence": {
    "abuses & harassment": {
      "physical": [
        "standing too close", "intimidating", "aggressive postures", "touching body", "unwanted kissing", "forcing the child to touch them", "sending nude pictures", "forcing to watch porn", "flashing / genital exposure", "rape attempt", "with holding information", "beating", "pushing", "burning", "un coelcoming postures", "taking pictures", "others"
      ],
      "emotional": [
        "bullying & black mail", "silent treatment", "isolating from peers", "ridiculing", "mocking", "ill treatment", "others"
      ],
      "sexual abuse": [
        "penetration", "rape", "oral sex induction", "non penetrating activity", "forcing / inviting for sexual gratification", "flasing or grooming for fecture abuse", "others"
      ],
      "neglect": [
        "failure in addressing child's basic needs", "impairment of child health", "unattending to child's psychological needs", "in attension to emotional needs", "abandonment", "allowing the child to exterience harm", "others"
      ]
    }
  }
}
</json_database>

**Instructions:**
1. **Analyze the Transcript:** Review the text carefully, keeping in mind it comes from a voice transcription (so look past phonetic transcription errors or filler words to find the underlying intent).
2. **Execute Step-by-Step Reasoning:**
  - First, isolate the root context (Is this happening at home/partner, work, or general public/sexual context?).
  - Select the exact `voilence` umbrella from the JSON keys.
  - Drill down to the single best `sub violence`.
  - Drill down to the single best `category violence`.
  - Scan all elements in that specific category's array and extract **only** the sub-category labels that explicitly match the behavior mentioned in the transcript. Never pick labels from a different category branch.
  - Extract EVERY matching label in the chosen category, not only the most obvious one. Check each label in the category's list one by one against the full transcript.
  - Copy each label exactly as written in the database (same spelling), in English.
3. **Alert Generation Logic:**
  - Count the total number of matched sub-categories.
  - Count < 5: Output "Counseling with our client"
  - Count == 5: Output "Counseling is needed"
  - Count > 5: Output "Call the police"

**Output Format:**
You must respond strictly in valid JSON format matching the schema below. Do not include markdown formatting outside the JSON object.

{
  
  "classification": {
    "voilence": "<Matched main violence>",
    "sub_violence": "<Matched sub violence>",
    "category_violence": "<Matched category violence>",
    "matched_sub_category_violences": [
      "<label_1>",
      "<label_2>"
    ]
  },
  "metrics": {
    "matched_count": 0
  },
  "alert_action": "<The required alert based on the count logic>"
}

If the transcript is a casual conversation, greeting, question, chit-chat,
general discussion, or does NOT describe any violence, abuse, harassment,
threat, coercion, or crisis, DO NOT classify it into any violence category.

Instead return:

{
  "classification": {
    "voilence": "Casual Conversation",
    "sub_violence": "None",
    "category_violence": "None",
    "matched_sub_category_violences": []
  },
  "metrics": {
    "matched_count": 0
  },
  "alert_action": "No Action Required"
}

**User Input:**
Transcript: \"\"\"{{USER_TRANSCRIPT}}\"\"\"
"""

async def match_labels(text, path):
    v, s, c = path
    labels = [x for x in DB[v][s][c] if x != "others"]
    prompt = (
        "Read this transcript (from voice transcription, so ignore filler words and spelling errors). "
        "Go through the labels below ONE BY ONE and decide for each whether the transcript describes it, "
        "either explicitly or clearly by meaning. Return EVERY label that applies, not just the most obvious.\n"
        'Return only JSON: {"matched": ["<label>", ...]} using the labels exactly as written.\n\n'
        f"Labels: {json.dumps(labels)}\n\nTranscript: \"\"\"{text}\"\"\""
    )
    raw = await _chat([{"role": "user", "content": prompt}])
    if not raw:
        return None
    try:
        got = _parse_json(raw).get("matched", [])
        return [x for x in dict.fromkeys(got) if x in labels]   # keeps only real labels
    except Exception as e:
        print(f"[match_labels] parse failed: {e}")
        return None



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


async def _chat(messages):
    """Send messages to Groq models, then OpenRouter. Returns the reply text or None."""
    if GROQ_API_KEY:
        for g_model in ["openai/gpt-oss-120b", "llama-3.3-70b-versatile", "openai/gpt-oss-20b"]:
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


def _tamil_ok(result):
    """True if classification_ta exists and matches the English list length."""
    ta = result.get("classification_ta")
    en = result.get("classification", {}).get("matched_sub_category_violences", [])
    return (
        isinstance(ta, dict)
        and isinstance(ta.get("matched_sub_category_violences"), list)
        and len(ta["matched_sub_category_violences"]) == len(en)
        and all(k in ta for k in ("voilence", "sub_violence", "category_violence"))
    )


async def ensure_tamil(result):
    """If the model skipped or broke the Tamil block, translate it in a second small call."""
    if _tamil_ok(result):
        return
    print("[tamil] classification_ta missing or mismatched, translating separately")
    c = result.get("classification", {})
    """  prompt = (
        "Translate every value of this JSON into natural, simple Tamil. "
        "Keep the same keys and keep the list in the same order with the same number of items. "
        "Return only valid JSON.\n" + json.dumps(c, ensure_ascii=False)
    )"""
    prompt = (
        "These labels come from a domestic violence and abuse support system in Tamil Nadu. "
        "Translate every value of this JSON into natural, simple Tamil by meaning, not word for word. "
        "'Intimate partner' means a husband, wife or partner (நெருங்கிய துணை), not a relative. "
        "Use noun forms for actions, for example 'hitting' as அடித்தல். "
        "Keep the same keys and keep the list in the same order with the same number of items. "
        "Return only valid JSON.\n" + json.dumps(c, ensure_ascii=False)
    )

    raw = await _chat([{"role": "user", "content": prompt}])
    result.pop("classification_ta", None)
    if raw:
        try:
            result["classification_ta"] = _parse_json(raw)
            if not _tamil_ok(result):
                result.pop("classification_ta", None)
        except Exception as e:
            print(f"[tamil] translation parse failed: {e}")

async def classify_text(text):
    prompt = PROMPT_TEMPLATE.replace("{{USER_TRANSCRIPT}}", text)
    tamil = is_tamil(text)

    if tamil:
        marker = "**User Input:**"
        if marker in prompt:
            prompt = prompt.replace(marker, TAMIL_INSTRUCTION + "\n" + marker)
        else:
            prompt = prompt + "\n" + TAMIL_INSTRUCTION
    messages = [{"role": "user", "content": prompt}]

    raw = await _chat(messages)
    if not raw:
        return None

    try:
        result = _parse_json(raw)

        c = result.setdefault("classification", {})

        # remove duplicate labels, keep order
        matched = list(dict.fromkeys(c.get("matched_sub_category_violences", [])))
        c["matched_sub_category_violences"] = matched

        # count and alert are computed in code, not by the model
        count = len(matched)
        result["metrics"] = {"matched_count": count}

        if c.get("voilence") == "Casual Conversation":
            alert = "No Action Required"
        elif count < 5:
            alert = "Counseling with our client"
        elif count == 5:
            alert = "Counseling is needed"
        else:
            alert = "Call the police"
        result["alert_action"] = alert

        if tamil:
            result["alert_action_ta"] = ALERTS_TA[alert]
            await ensure_tamil(result)

            # Tamil only: replace the English values with the Tamil ones
            if _tamil_ok(result):
                result["classification_en"] = dict(result["classification"])  # optional: remove if you don't need English
                result["classification"] = result.pop("classification_ta")
                result["alert_action"] = result.pop("alert_action_ta")

        return result
    except Exception as e:
        print(f"Failed to parse JSON: {e}")
        return None
