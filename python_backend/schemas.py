from pydantic import BaseModel
from typing import List, Optional

# Match the Event class from C#
class Event(BaseModel):
    Code: Optional[int] = None
    ViolenceCode: Optional[int] = None
    ViolenceName: Optional[str] = None
    SubViolenceCode: Optional[int] = None
    SubViolenceName: Optional[str] = None
    CategoryCode: Optional[int] = None
    CategoryName: Optional[str] = None
    SubCategoryCode: Optional[int] = None
    SubCategoryName: Optional[str] = None
    Actions: Optional[str] = None
    ln: Optional[str] = None
    UserName: Optional[str] = None
    Password: Optional[str] = None
    ColourRating: Optional[int] = None
    
    # We will map these dictionaries/models based on how they are sent
    LOGIN_MASTERs: Optional[List[dict]] = None
    aBUSE_MASTERs: Optional[List[dict]] = None
    aBUSES_MASTER: Optional[List[dict]] = None

class JsonMessage(BaseModel):
    Status: str
    LOGIN_MASTERs: Optional[List[dict]] = None
    vIOLENCE_MASTERs: Optional[List[dict]] = None
    Sub_Violences: Optional[List[dict]] = None
    cATEGORY_VIOLENCE_MASTERs: Optional[List[dict]] = None
    sUB_CATEGORY_VIOLENCE_MASTERs: Optional[List[dict]] = None
    aBUSE_CONTACT_DETAILS_s: Optional[List[dict]] = None
