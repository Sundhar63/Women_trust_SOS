import os
import requests
import sys
import time
from dotenv import load_dotenv
import json

load_dotenv()

API_KEY = os.getenv("SPEECHMATICS_API_KEY")
BASE_URL ="https://eu1.asr.api.speechmatics.com/v2"
HEADERS = {"Authorization": f"Bearer {API_KEY}"}
print("Key loaded:", bool(API_KEY), "length:", len(API_KEY or ""))

if not API_KEY:
    sys.exit("SPEECHMATICS_API_KEY is missing from .env")

audio_path = sys.argv[1] if len(sys.argv) > 1 else "sample.wav"

config = {
    "type": "transcription",
    "transcription_config": {"language": "en"},  # use "ta" for Tamil, "hi" for Hindi
}

# 1. Submit the job
with open(audio_path, "rb") as f:
    resp = requests.post(
        f"{BASE_URL}/jobs/",
        headers=HEADERS,
        files={"data_file": f},
        data={"config": json.dumps(config)},
    )
print("Submit:", resp.status_code, resp.text)
resp.raise_for_status()
job_id = resp.json()["id"]

# 2. Poll until finished
while True:
    status = requests.get(f"{BASE_URL}/jobs/{job_id}", headers=HEADERS).json()["job"]["status"]
    print("Status:", status)
    if status == "done":
        break
    if status in ("rejected", "deleted", "expired"):
        sys.exit(f"Job failed: {status}")
    time.sleep(3)

# 3. Fetch the transcript
result = requests.get(
    f"{BASE_URL}/jobs/{job_id}/transcript",
    headers=HEADERS,
    params={"format": "txt"},
)
print("\nTranscript:\n", result.text)


