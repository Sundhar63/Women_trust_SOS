"""
Test script to run the Voice Agent Hierarchical Classification Pipeline
against transcribed test audio files or direct custom text inputs.

Workflow:
  1. Main Violence Selection (step1_predict_violence)
  2. Sub-Violence Selection (step2_predict_sub_violences)
  3. Category Violence Selection (step3_predict_categories)
  4. Sub-Category Checkbox Extraction & Agent Severity (step4_extract_labels_and_assess_severity)
  5. 1-17 Rating Calculation & Action Alert (build_final_response)
"""

import os
import sys
import json
import asyncio
from typing import Dict, Any, List

# ---------------------------------------------------------------------------
# Path Resolution & Backend Discovery
# ---------------------------------------------------------------------------
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))

CANDIDATE_BACKEND_DIRS = [
    os.path.join(CURRENT_DIR, "python_backend"),
    os.path.join(CURRENT_DIR, "..", "python_backend"),
    os.path.join(CURRENT_DIR, "..", "..", "python_backend"),
    os.path.join(CURRENT_DIR, "Women_trust_SOS", "python_backend"),
]
BACKEND_DIR = None
for candidate in CANDIDATE_BACKEND_DIRS:
    if os.path.exists(candidate):
        BACKEND_DIR = os.path.abspath(candidate)
        break

if not BACKEND_DIR:
    raise RuntimeError(f"Could not locate python_backend directory from {CURRENT_DIR}")

if BACKEND_DIR not in sys.path:
    sys.path.insert(0, BACKEND_DIR)

# Load environment variables (.env)
from dotenv import load_dotenv
load_dotenv(os.path.join(BACKEND_DIR, ".env"))
load_dotenv(os.path.join(CURRENT_DIR, ".env"))
load_dotenv(os.path.join(CURRENT_DIR, "..", ".env"))
load_dotenv()

if not os.getenv("DB_CONNECTION_STRING"):
    os.environ["DB_CONNECTION_STRING"] = "postgresql://postgres:postgres@localhost:5432/nayeam_database"

# Import the voice agent router module
from routers import voice


# ---------------------------------------------------------------------------
# Helper Functions
# ---------------------------------------------------------------------------
def load_transcriptions() -> List[Dict[str, Any]]:
    """Load transcriptions generated from test audios."""
    candidates = [
        os.path.join(CURRENT_DIR, "test_audio_files", "transcriptions.json"),
        os.path.join(CURRENT_DIR, "..", "test_audio_files", "transcriptions.json"),
        os.path.join(CURRENT_DIR, "Women_trust_SOS", "test_audio_files", "transcriptions.json"),
        os.path.join(CURRENT_DIR, "transcriptions.json"),
        os.path.join(CURRENT_DIR, "..", "transcriptions.json"),
    ]
    for p in candidates:
        if os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                return json.load(f)
    print("[WARNING] transcriptions.json not found in test directories.")
    return []


def check_api_keys() -> bool:
    """Verify LLM API key availability."""
    groq_key = os.getenv("GROQ_API_KEY")
    openrouter_key = os.getenv("OPENROUTER_API_KEY")

    print("\n--- Voice Agent API Key Status ---")
    print(f"GROQ_API_KEY       : {'[CONFIGURED]' if groq_key else '[MISSING]'}")
    print(f"OPENROUTER_API_KEY : {'[CONFIGURED]' if openrouter_key else '[MISSING]'}")

    if not groq_key and not openrouter_key:
        print("\n[WARNING] Neither GROQ_API_KEY nor OPENROUTER_API_KEY found.")
        print("Please configure an API key in .env to run live agent classification.")
        return False
    return True


async def run_classification(text_to_process: str, label: str = "Test") -> Dict[str, Any]:
    """Runs the full 4-stage hierarchical voice agent on a given text string."""
    print(f"\n{'=' * 70}")
    print(f"Evaluating: {label}")
    print(f"Input Text: {text_to_process}")
    print(f"{'=' * 70}")

    result = await voice.classify_text(text_to_process)

    if not result:
        print("-> [ERROR] Model returned empty or classification failed.")
        return {"success": False, "error": "Classification returned empty"}

    # Extract classified components (handles direct dict or wrapped selected_checkbox)
    primary = result.get("classification") or result.get("selected_checkbox", {}).get("classification", {})
    violence = primary.get("voilence", "N/A")
    sub_violence = primary.get("sub_violence", "N/A")
    category = primary.get("category_violence", "N/A")
    matched_labels = primary.get("matched_sub_category_violences", [])

    rating = result.get("violence_rating", 0)
    alert = result.get("alert_action", "N/A")
    abuse_level = result.get("abuse_level", "N/A")
    codes = result.get("codes", {})

    # Determine display badge
    if rating >= 10 or abuse_level.lower() == "high":
        badge = "SEVERE / HIGH (Red)"
    elif rating >= 5 or abuse_level.lower() == "medium":
        badge = "MODERATE (Yellow)"
    else:
        badge = "MILD (Green)"

    print(f"\n[Agent Hierarchical Classification Output]")
    print(f"  • Hierarchy Path   : {violence} -> {sub_violence} -> {category}")
    print(f"  • Abuse Level      : {abuse_level.upper()} [{badge}]")
    print(f"  • Violence Rating  : {rating} / 17")
    print(f"  • Matched Labels   : {', '.join(matched_labels) if matched_labels else 'None'}")
    print(f"  • Emergency Action : {alert}")
    print(f"  • Database Codes   : {codes}")

    # If multiple categories were detected, display all of them
    all_classifications = result.get("all_classifications", [])
    if len(all_classifications) > 1:
        print(f"\n  [Additional Detected Categories ({len(all_classifications)})]")
        for idx, extra in enumerate(all_classifications[1:], start=2):
            extra_labels = extra.get("matched_sub_category_violences", [])
            print(f"    {idx}. {extra.get('voilence')} -> {extra.get('sub_violence')} -> {extra.get('category_violence')}")
            print(f"       Labels: {', '.join(extra_labels) if extra_labels else 'None'}")

    return {
        "success": True,
        "input_transcript": text_to_process,
        "classification": result
    }


async def main():
    has_keys = check_api_keys()
    if not has_keys:
        print("\nStopping: Add GROQ_API_KEY or OPENROUTER_API_KEY to proceed.")
        return

    # Option 1: Direct text input via CLI: python test_voice_agent.py --text "My husband hit me"
    if len(sys.argv) > 1 and sys.argv[1] == "--text":
        if len(sys.argv) > 2:
            custom_text = " ".join(sys.argv[2:])
            await run_classification(custom_text, label="CLI Custom Input")
        else:
            print("Please provide text after --text. Example: python test_voice_agent.py --text 'My husband beat me'")
        return

    # Option 2: Run from transcriptions.json
    transcriptions = load_transcriptions()
    if not transcriptions:
        print("No transcriptions found. You can test directly with: python test_voice_agent.py --text '<your transcript>'")
        return

    print(f"\nLoaded {len(transcriptions)} audio transcriptions from test files.")

    # Filter by specific ID if passed: python test_voice_agent.py 1
    if len(sys.argv) > 1 and sys.argv[1].isdigit():
        target_id = int(sys.argv[1])
        transcriptions = [t for t in transcriptions if t.get("id") == target_id]
        print(f"Filtering to Audio #{target_id} only.")

    results = []
    for item in transcriptions:
        audio_id = item.get("id", "N/A")
        filename = item.get("filename", "unknown")
        tamil_text = item.get("transcription", "")
        english_text = item.get("english_translation", "")
        text_to_process = tamil_text if tamil_text else english_text

        label = f"Audio #{audio_id} ({filename})"
        res = await run_classification(text_to_process, label=label)
        results.append({
            "id": audio_id,
            "filename": filename,
            "result": res
        })

    # Save output JSON report
    output_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(output_dir, "classification_results.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"\n[COMPLETE] Successfully processed {len(results)} transcriptions.")
    print(f"Full results saved to: {output_path}")


if __name__ == "__main__":
    asyncio.run(main())
