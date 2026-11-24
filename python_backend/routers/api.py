from fastapi import APIRouter, Depends, HTTPException, File, UploadFile, Header
from routers.voice import transcribe_audio,classify_text
from sqlalchemy.orm import Session
from sqlalchemy import func
from typing import List, Optional
from datetime import datetime
import requests
import os

from database import get_db
import schemas
import models
import security
from routers import auth

router = APIRouter(prefix="/api/API", tags=["API"])

def get_abuse_contact_details(db: Session, rating: int = 0, level: Optional[str] = None, ln: Optional[str] = None):
    if not level:
        if rating >= 10:
            level = "Severe"
        elif rating >= 5:
            level = "Moderate"
        else:
            level = "Mild"

    # Query active suggestions and helplines directly from database
    contacts = db.query(models.AbuseContactMaster).filter(
        models.AbuseContactMaster.ABUSE_LEVEL == level,
        models.AbuseContactMaster.MASTER_STATUS == "Active"
    ).order_by(models.AbuseContactMaster.SNO.asc()).all()

    particulars = []
    image_names = []
    contact_no = "100"
    contact_mail = "support@womentrust.org"
    contact_website = "https://www.womentrust.org/help"

    if contacts:
        for c in contacts:
            text = c.TAMIL_PARTICULAR if ln == "ta" and c.TAMIL_PARTICULAR else c.PARTICULAR
            if text:
                particulars.append(text)
            if c.IMAGE_NAME and c.IMAGE_NAME not in image_names:
                image_names.append(c.IMAGE_NAME)
        first = contacts[0]
        contact_no = first.CONTACT_NO or contact_no
        contact_mail = first.CONTACT_MAIL_ID or contact_mail
        contact_website = first.CONTACT_WEBSITE or contact_website

    # Fallback to defaults if database rows not yet inserted
    if not particulars:
        if level == "Severe":
            particulars = [
                "1 - Consult A Legal Advisor",
                "2 - Talk To a family therapist Dr. Saranya Sundaraju 9500185855",
                "3 - Save Counselling 9842868900",
                "4 - Telemanas Nimhans 24x7 Toll-Free Helpline: 14416 / Alternative: 1-800-891-4416 (Free & Confidential Support)",
                "5 - KAVALAN UDANI APP",
                "6 - NCW Women Helpline 181",
            ] if ln != "ta" else [
                "1 - சட்ட ஆலோசகரை அணுகவும்",
                "2 - குடும்ப சிகிச்சையாளர் டாக்டர் சரண்யா சுந்தரராஜுவிடம் பேசவும் 9500185855",
                "3 - சேவ் ஆலோசனை மையம் 9842868900",
                "4 - டெலிமானாஸ் நிம்ஹான்ஸ் 24x7 கட்டணமில்லா உதவி எண்: 14416 / மாற்று எண்: 1-800-891-4416 (இலவச மற்றும் ரகசிய ஆதரவு)",
                "5 - காவலன் உதவி செயலி (Kavalan Uthavi App)",
                "6 - தேசிய மகளிர் ஆணைய உதவி எண் 181",
            ]
            image_names = ["tamilnadu_police_kaaval_uthavi.png", "telemanas_logo.png"]
            contact_no = "181 / 14416"
        elif level == "Moderate":
            particulars = [
                "1 - Warn the harassar (Husband / in law family to Stop",
                "2 - Take some chance to record the Harassment",
                "3 - Have a detailed log of Date of the Harassment, Time, location, witness",
                "4 - Identify Early & report within family",
                "5 - 'No' Means 'No'",
                "6 - Be cautious & aware of external and legal remedy",
                "7 - Tele-MANAS 24x7 Toll-Free Helpline: 14416 / Alternative: 1-800-891-4416 (Free & Confidential Support - https://telemanas.mohf.gov.in/home)",
            ] if ln != "ta" else [
                "1 - துன்புறுத்துபவரை (கணவர் / மாமியார் குடும்பத்தினர்) நிறுத்துமாறு எச்சரிக்கவும்",
                "2 - துன்புறுத்தலை பதிவு செய்ய வாய்ப்பைப் பயன்படுத்தவும்",
                "3 - துன்புறுத்தல் நடந்த தேதி, நேரம், இடம், சாட்சிகள் பற்றிய விரிவான குறிப்பை வைத்திருக்கவும்",
                "4 - ஆரம்பத்திலேயே கண்டறிந்து குடும்பத்திற்குள் தெரிவிக்கவும்",
                "5 - 'இல்லை' என்றால் 'இல்லை' என்று உறுதியாக இருங்கள்",
                "6 - வெளிப்புற மற்றும் சட்டப்பூர்வ தீர்வுகள் குறித்து எச்சரிக்கையாகவும் விழிப்புணர்வுடனும் இருங்கள்",
                "7 - டெலிமானாஸ் 24x7 கட்டணமில்லா உதவி எண்: 14416 / மாற்று எண்: 1-800-891-4416 (இலவச மற்றும் ரகசிய ஆதரவு - https://telemanas.mohf.gov.in/home)",
            ]
            image_names = ["telemanas_logo.png"]
            contact_no = "14416 / 1-800-891-4416"
            contact_website = "https://telemanas.mohf.gov.in/home"
        else:
            particulars = [
                "1 - Talk to trusted Peers, Mentors & Family Member",
                "2 - Strengthen yourself to Ignore & divert",
                "3 - Assert to Say 'No'",
                "4 - Use it as a learning Opportunity to cope",
                "5 - Do not cry, depressed or Shows Signs of Victimisation",
                "6 - Tele-MANAS 24x7 Toll-Free Helpline: 14416 / Alternative: 1-800-891-4416 (Free & Confidential Support)",
            ] if ln != "ta" else [
                "1 - நம்பிக்கையான நண்பர்கள், வழிகாட்டிகள் மற்றும் குடும்பத்தினரிடம் பேசுங்கள்",
                "2 - புறக்கணிக்கவும் கவனத்தை திசைதிருப்பவும் உங்களை பலப்படுத்துங்கள்",
                "3 - 'வேண்டாம்' என்று உறுதியாகச் சொல்ல பழகுங்கள்",
                "4 - இதை சமாளிப்பதற்கான கற்றல் வாய்ப்பாகப் பயன்படுத்துங்கள்",
                "5 - அழவோ, மனச்சோர்வடையவோ அல்லது பாதிக்கப்பட்டவராகவோ காட்டாதீர்கள்",
                "6 - டெலிமானாஸ் 24x7 கட்டணமில்லா உதவி எண்: 14416 / மாற்று எண்: 1-800-891-4416 (இலவச மற்றும் ரகசிய ஆதரவு)",
            ]
            image_names = ["save_logo.png", "telemanas_logo.png"]
            contact_no = "14416 / 1-800-891-4416"

    if not image_names:
        image_names = ["save_logo.png"]

    return {
        "ABUSE_LEVEL": level,
        "VIOLENCE_RATING": rating,
        "IMAGE_NAME": image_names,
        "PARTICULAR": particulars,
        "CONTACT_NO": contact_no,
        "CONTACT_MAIL_ID": contact_mail,
        "CONTACT_WEBSITE": contact_website,
    }

@router.post("/")
def process_event(
    event: schemas.Event,
    db: Session = Depends(get_db),
    authorization: Optional[str] = Header(default=None),
):
    message_list = []
    
    try:
        if event.Actions == "LoginCreation":
            for db_login in event.LOGIN_MASTERs or []:
                code_obj = db.query(models.LoginMaster).order_by(models.LoginMaster.CODE.desc()).first()
                new_code = 1 if not code_obj or not code_obj.CODE else code_obj.CODE + 1
                
                new_login = models.LoginMaster(
                    CODE=new_code,
                    NAME=db_login.get("NAME"),
                    MOBILE=db_login.get("MOBILE"),
                    ALTERNATIVE_MOBILE=db_login.get("ALTERNATIVE_MOBILE"),
                    EMAIL=db_login.get("EMAIL"),
                    AADHAR_NO=db_login.get("AADHAR_NO"),
                    ADDRESS=db_login.get("ADDRESS"),
                    USERNAMES=db_login.get("USERNAMES"),
                    PASSWORDS=security.get_password_hash(db_login.get("PASSWORDS")), # Secure Hashing
                    DEVICE_ID=db_login.get("DEVICE_ID"),
                    MASTER_STATUS="Active",
                    USERNAME=db_login.get("USERNAME"),
                    ROLE=db_login.get("ROLE")
                )
                db.add(new_login)
            db.commit()
            message_list.append({"Status": "Success"})

        elif event.Actions == "LoginMaster":
            items = db.query(models.LoginMaster).order_by(models.LoginMaster.USERNAMES.asc()).all()
            if items:
                message_list.append({"Status": "Success", "LOGIN_MASTERs": [u.__dict__ for u in items]})
            else:
                message_list.append({"Status": "Not Success", "LOGIN_MASTERs": None})

        elif event.Actions == "CheckLogin":
            user = db.query(models.LoginMaster).filter(
                models.LoginMaster.USERNAMES == event.UserName,
                models.LoginMaster.MASTER_STATUS == "Active"
            ).first()
            
            if user and security.verify_password(event.Password, user.PASSWORDS):
                # Issue a short-lived access token plus a rotating refresh token.
                access_token, refresh_token = auth.create_session(
                    db,
                    user,
                    device_id=event.DeviceId,
                )

                message_list.append(auth.session_response(access_token, refresh_token, user))
            else:
                message_list.append({"Status": "Not Success", "LOGIN_MASTERs": None})

        elif event.Actions == "CheckUserName":
            user = db.query(models.LoginMaster).filter(
                models.LoginMaster.USERNAMES == event.UserName,
                models.LoginMaster.MASTER_STATUS == "Active"
            ).first()
            if user:
                message_list.append({"Status": "Success"})
            else:
                message_list.append({"Status": "Not Success"})

        elif event.Actions == "SaveUserLocation":
            user = auth.resolve_user_from_header(authorization, db)
            if not user:
                message_list.append({"Status": "Not Success", "Message": "Not authenticated"})
            elif event.Latitude is None or event.Longitude is None:
                message_list.append({"Status": "Not Success", "Message": "Latitude and longitude are required"})
            elif not (-90.0 <= event.Latitude <= 90.0) or not (-180.0 <= event.Longitude <= 180.0):
                message_list.append({"Status": "Not Success", "Message": "Invalid coordinates"})
            else:
                captured_at = datetime.utcnow()
                if event.CapturedAt:
                    try:
                        captured_at = datetime.fromisoformat(event.CapturedAt.replace("Z", "+00:00")).replace(tzinfo=None)
                    except ValueError:
                        pass

                location = models.UserLocation(
                    LOGIN_ID=user.ID,
                    LATITUDE=event.Latitude,
                    LONGITUDE=event.Longitude,
                    ACCURACY=event.Accuracy,
                    PROVIDER=(event.Provider or "")[:32] or None,
                    DEVICE_ID=(event.DeviceId or "")[:128] or None,
                    CAPTURED_AT=captured_at,
                    CREATED_AT=datetime.utcnow(),
                )
                db.add(location)
                db.commit()
                db.refresh(location)
                message_list.append({"Status": "Success", "LocationId": location.ID})

        elif event.Actions == "TypeOfViolence":
            items = db.query(models.ViolenceMaster).filter(
                models.ViolenceMaster.MASTER_STATUS == "Active"
            ).all()
            if items:
                result = []
                for c in items:
                    c_dict = c.__dict__.copy()
                    c_dict["VIOLENCE"] = c.TAMIL_VIOLENCE if event.ln == "ta" else c.VIOLENCE
                    result.append(c_dict)
                message_list.append({"Status": "Success", "vIOLENCE_MASTERs": result})
            else:
                message_list.append({"Status": "Not Success", "vIOLENCE_MASTERs": None})

        elif event.Actions == "SubViolence":
            items = db.query(models.SubViolenceMaster, models.ViolenceMaster).join(
                models.ViolenceMaster, models.SubViolenceMaster.VIOLENCE_CODE == models.ViolenceMaster.CODE
            ).filter(
                models.SubViolenceMaster.VIOLENCE_CODE == event.ViolenceCode,
                models.SubViolenceMaster.MASTER_STATUS == "Active",
                models.ViolenceMaster.MASTER_STATUS == "Active"
            ).all()
            if items:
                result = []
                for c, d in items:
                    result.append({
                        "ID": c.ID,
                        "CODE": c.CODE,
                        "VIOLENCE": d.TAMIL_VIOLENCE if event.ln == "ta" else d.VIOLENCE,
                        "SUB_VIOLENCE": c.TAMIL_SUB_VIOLENCE if event.ln == "ta" else c.SUB_VIOLENCE
                    })
                message_list.append({"Status": "Success", "Sub_Violences": result})
            else:
                message_list.append({"Status": "Not Success", "Sub_Violences": None})

        elif event.Actions == "CategoryViolence":
            items = db.query(models.CategoryViolenceMaster).join(
                models.SubViolenceMaster, models.SubViolenceMaster.CODE == models.CategoryViolenceMaster.SUB_VIOLENCE_CODE
            ).join(
                models.ViolenceMaster, models.SubViolenceMaster.VIOLENCE_CODE == models.ViolenceMaster.CODE
            ).filter(
                models.CategoryViolenceMaster.VIOLENCE_CODE == event.ViolenceCode,
                models.CategoryViolenceMaster.SUB_VIOLENCE_CODE == event.SubViolenceCode,
                models.SubViolenceMaster.MASTER_STATUS == "Active",
                models.ViolenceMaster.MASTER_STATUS == "Active",
                models.CategoryViolenceMaster.MASTER_STATUS == "Active"
            ).all()
            if items:
                result = []
                for e in items:
                    e_dict = e.__dict__.copy()
                    e_dict["CATEGORY"] = e.TAMIL_CATEGORY if event.ln == "ta" else e.CATEGORY
                    result.append(e_dict)
                message_list.append({"Status": "Success", "cATEGORY_VIOLENCE_MASTERs": result})
            else:
                message_list.append({"Status": "Not Success", "cATEGORY_VIOLENCE_MASTERs": None})

        elif event.Actions == "SubCategoryViolence":
            items = db.query(models.SubCategoryViolenceMaster).join(
                models.CategoryViolenceMaster, models.CategoryViolenceMaster.CODE == models.SubCategoryViolenceMaster.CATEGORY_CODE
            ).join(
                models.SubViolenceMaster, models.SubViolenceMaster.CODE == models.CategoryViolenceMaster.SUB_VIOLENCE_CODE
            ).join(
                models.ViolenceMaster, models.SubViolenceMaster.VIOLENCE_CODE == models.ViolenceMaster.CODE
            ).filter(
                models.SubCategoryViolenceMaster.VIOLENCE_CODE == event.ViolenceCode,
                models.SubCategoryViolenceMaster.SUB_VIOLENCE_CODE == event.SubViolenceCode,
                models.SubCategoryViolenceMaster.CATEGORY_CODE == event.CategoryCode,
                models.SubViolenceMaster.MASTER_STATUS == "Active",
                models.ViolenceMaster.MASTER_STATUS == "Active",
                models.CategoryViolenceMaster.MASTER_STATUS == "Active",
                models.SubCategoryViolenceMaster.MASTER_STATUS == "Active"
            ).all()
            if items:
                result = []
                for f in items:
                    f_dict = f.__dict__.copy()
                    f_dict["SUB_CATEGORY"] = f.TAMIL_SUB_CATEGORY if event.ln == "ta" else f.SUB_CATEGORY
                    result.append(f_dict)
                message_list.append({"Status": "Success", "sUB_CATEGORY_VIOLENCE_MASTERs": result})
            else:
                message_list.append({"Status": "Not Success", "sUB_CATEGORY_VIOLENCE_MASTERs": None})

        elif event.Actions == "Abuse":
            # Extract rating from event or aBUSES_MASTER
            rating = 0
            if event.ColourRating is not None:
                rating = event.ColourRating
            elif event.aBUSES_MASTER and len(event.aBUSES_MASTER) > 0:
                raw_rating = event.aBUSES_MASTER[0].get("ColourRating")
                try:
                    rating = int(raw_rating) if raw_rating is not None else 0
                except (ValueError, TypeError):
                    rating = 0

            contact_detail = get_abuse_contact_details(db, rating=rating, ln=event.ln)
            message_list.append({
                "Status": "Success",
                "aBUSE_CONTACT_DETAILS_s": [contact_detail]
            })

        elif event.Actions == "AbuseContact":
            rating = 0
            if event.ColourRating is not None:
                rating = event.ColourRating
            contact_detail = get_abuse_contact_details(db, rating=rating, ln=event.ln)
            message_list.append({
                "Status": "Success",
                "aBUSE_CONTACT_DETAILS_s": [contact_detail]
            })
            
        else:
            message_list.append({"Status": "Not Success", "Message": "Action not found."})

    except Exception as e:
        db.rollback()
        message_list.append({"Status": "Not Success", "Error": str(e)})

    return message_list

@router.post("/ProcessAudio")
async def process_audio(audio_file: UploadFile = File(...)):
    if not audio_file:
        raise HTTPException(status_code=400, detail="No audio file attached.")

    try:
        contents = await audio_file.read()

        text = await transcribe_audio(contents, audio_file.filename)
        if not text:
            raise HTTPException(status_code=422, detail="Could not transcribe audio.")

        result = await classify_text(text)

        return {
            "success": True,
            "transcription": text,
            "selected_checkbox": result,
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
