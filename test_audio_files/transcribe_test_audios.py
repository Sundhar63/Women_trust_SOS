import os
import glob
import json
import subprocess
import tempfile
import urllib.parse
import urllib.request
import speech_recognition as sr

def translate_to_en(text):
    if not text:
        return ""
    try:
        url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=t&q=" + urllib.parse.quote(text)
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        res = urllib.request.urlopen(req, timeout=10).read().decode("utf-8")
        data = json.loads(res)
        return "".join([part[0] for part in data[0] if part[0]])
    except Exception as e:
        return ""

def get_duration(file_path):
    try:
        cmd = ["ffprobe", "-i", file_path, "-show_entries", "format=duration", "-v", "quiet", "-of", "csv=p=0"]
        out = subprocess.check_output(cmd, text=True).strip()
        return round(float(out), 2)
    except Exception:
        return None

def transcribe_audio_file(file_path, recognizer):
    temp_dir = tempfile.mkdtemp()
    out_pattern = os.path.join(temp_dir, "chunk_%03d.wav")

    try:
        # Segment into 15-second mono 16kHz WAV files
        cmd = [
            "ffmpeg", "-y", "-i", file_path,
            "-f", "segment", "-segment_time", "15",
            "-ar", "16000", "-ac", "1",
            out_pattern
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)

        chunks = sorted([os.path.join(temp_dir, c) for c in os.listdir(temp_dir) if c.endswith(".wav")])
        tamil_parts = []
        english_fallback_parts = []

        for chunk_path in chunks:
            with sr.AudioFile(chunk_path) as src:
                aud = recognizer.record(src)

            # Try Tamil first
            chunk_text = ""
            try:
                chunk_text = recognizer.recognize_google(aud, language="ta-IN")
                tamil_parts.append(chunk_text)
            except sr.UnknownValueError:
                pass
            except Exception as e:
                pass

            # If Tamil failed for this chunk, attempt English
            if not chunk_text:
                try:
                    eng_chunk = recognizer.recognize_google(aud, language="en-IN")
                    english_fallback_parts.append(eng_chunk)
                except Exception:
                    pass

        tamil_full = " ".join([p.strip() for p in tamil_parts if p.strip()]).strip()
        if tamil_full:
            return {
                "detected_language": "ta-IN",
                "text": tamil_full,
                "english_translation": translate_to_en(tamil_full)
            }

        eng_full = " ".join([p.strip() for p in english_fallback_parts if p.strip()]).strip()
        if eng_full:
            return {
                "detected_language": "en-IN",
                "text": eng_full,
                "english_translation": eng_full
            }

        return {
            "detected_language": "unknown",
            "text": "",
            "english_translation": "",
            "note": "No clear speech detected in any segment"
        }

    finally:
        for c in os.listdir(temp_dir):
            try:
                os.remove(os.path.join(temp_dir, c))
            except OSError:
                pass
        try:
            os.rmdir(temp_dir)
        except OSError:
            pass

def main():
    audio_dir = os.path.join("Women_trust_SOS", "test_audio_files")
    output_json = os.path.join(audio_dir, "transcriptions.json")

    audio_files = sorted(glob.glob(os.path.join(audio_dir, "*.ogg")))
    print(f"Found {len(audio_files)} test audio files in {audio_dir}...", flush=True)

    recognizer = sr.Recognizer()
    results = []

    for idx, file_path in enumerate(audio_files, 1):
        filename = os.path.basename(file_path)
        duration = get_duration(file_path)
        file_size_bytes = os.path.getsize(file_path)
        print(f"[{idx}/{len(audio_files)}] Processing '{filename}' ({duration}s)...", flush=True)

        res = transcribe_audio_file(file_path, recognizer)
        entry = {
            "id": idx,
            "filename": filename,
            "duration_seconds": duration,
            "file_size_bytes": file_size_bytes,
            "language": res.get("detected_language"),
            "transcription": res.get("text", ""),
            "english_translation": res.get("english_translation", "")
        }
        if "note" in res:
            entry["note"] = res["note"]

        snippet = entry["transcription"][:70] if entry["transcription"] else "(No speech)"
        print(f"       -> [{entry['language']}] {snippet}...", flush=True)
        results.append(entry)

    # Save to JSON
    with open(output_json, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"\nAll done! Successfully saved {len(results)} transcriptions to: {output_json}", flush=True)

if __name__ == "__main__":
    main()
