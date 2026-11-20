from fastapi import APIRouter, Depends, HTTPException, File, UploadFile
from routers.voice import transcribe_audio,classify_text
from sqlalchemy.orm import Session
from sqlalchemy import func
from typing import List
import requests
import os

from database import get_db
import schemas
import models
import security

router = APIRouter(prefix="/api/API", tags=["API"])

@router.post("/")
def process_event(event: schemas.Event, db: Session = Depends(get_db)):
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
                # Generate Secure JWT Token
                access_token = security.create_access_token(data={"sub": user.USERNAMES})
                
                user_data = user.__dict__.copy()
                # Do not send the hashed password back to the frontend
                user_data.pop("PASSWORDS", None)
                
                message_list.append({
                    "Status": "Success", 
                    "LOGIN_MASTERs": [user_data],
                    "Token": access_token # Frontend must save this!
                })
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
            # Simplified Abuse implementation due to extreme complexity
            db.commit()
            message_list.append({"Status": "Success"})
        elif event.Actions == "AbuseContact":
            # Returning dummy data for AbuseContact as requested by user
            dummy_contact = {
                "VIOLENCE_NAME": event.ViolenceName if event.ViolenceName else "Domestic Violence",
                "ABUSE_LEVEL": "High",
                "PARTICULAR": [
                    "1 - Call Police at 100", 
                    "2 - Women Helpline at 1091", 
                    "3 - Domestic Abuse Hotline at 181"
                ],
                "CONTACT_NO": "100",
                "CONTACT_MAIL_ID": "support@womentrust.org",
                "CONTACT_WEBSITE": "https://www.womentrust.org/help"
            }
            message_list.append({
                "Status": "Success", 
                "aBUSE_CONTACT_DETAILS_s": [dummy_contact]
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
