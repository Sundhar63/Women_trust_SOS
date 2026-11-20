from sqlalchemy import Column, Integer, String, DateTime
from database import Base

class LoginMaster(Base):
    __tablename__ = "LOGIN_MASTER"

    ID = Column(Integer, primary_key=True, index=True)
    CODE = Column(Integer)
    NAME = Column(String)
    MOBILE = Column(String)
    ALTERNATIVE_MOBILE = Column(String)
    EMAIL = Column(String)
    AADHAR_NO = Column(String)
    ADDRESS = Column(String)
    USERNAMES = Column(String)
    PASSWORDS = Column(String)
    DEVICE_ID = Column(String)
    MASTER_STATUS = Column(String)
    USERNAME = Column(String)
    ROLE = Column(String)
    UPDATED = Column(DateTime)
    UPDATETIME = Column(String)
    LANGUAGES = Column(String)

class ViolenceMaster(Base):
    __tablename__ = "VIOLENCE_MASTER"

    ID = Column(Integer, primary_key=True, index=True)
    CODE = Column(Integer)
    VIOLENCE = Column(String)
    TAMIL_VIOLENCE = Column(String)
    MASTER_STATUS = Column(String)
    USERNAME = Column(String)
    ROLE = Column(String)
    UPDATED = Column(DateTime)
    UPDATETIME = Column(String)

class SubViolenceMaster(Base):
    __tablename__ = "SUB_VIOLENCE_MASTER"

    ID = Column(Integer, primary_key=True, index=True)
    CODE = Column(Integer)
    VIOLENCE_CODE = Column(Integer)
    SUB_VIOLENCE = Column(String)
    TAMIL_SUB_VIOLENCE = Column(String)
    MASTER_STATUS = Column(String)

class CategoryViolenceMaster(Base):
    __tablename__ = "CATEGORY_VIOLENCE_MASTER"

    ID = Column(Integer, primary_key=True, index=True)
    CODE = Column(Integer)
    VIOLENCE_CODE = Column(Integer)
    SUB_VIOLENCE_CODE = Column(Integer)
    CATEGORY = Column(String)
    TAMIL_CATEGORY = Column(String)
    MASTER_STATUS = Column(String)

class SubCategoryViolenceMaster(Base):
    __tablename__ = "SUB_CATEGORY_VIOLENCE_MASTER"

    ID = Column(Integer, primary_key=True, index=True)
    CODE = Column(Integer)
    VIOLENCE_CODE = Column(Integer)
    SUB_VIOLENCE_CODE = Column(Integer)
    CATEGORY_CODE = Column(Integer)
    SUB_CATEGORY = Column(String)
    TAMIL_SUB_CATEGORY = Column(String)
    MASTER_STATUS = Column(String)

class AbuseMaster(Base):
    __tablename__ = "ABUSE_MASTER"

    ID = Column(Integer, primary_key=True, index=True)
    CODE = Column(Integer)
    LOGIN_CODE = Column(Integer)
    ABUSE_CONTACT_CODE = Column(Integer)
    COLOUR = Column(String)
    COLOUR_RATING = Column(Integer)
    MASTER_STATUS = Column(String)
    USERNAME = Column(String)
    ROLE = Column(String)
    UPDATED = Column(DateTime)
    UPDATETIME = Column(String)

class AbuseContactMaster(Base):
    __tablename__ = "ABUSE_CONTACT_MASTER"

    ID = Column(Integer, primary_key=True, index=True)
    CODE = Column(Integer)
    VIOLENCE_CODE = Column(Integer)
    SUB_VIOLENCE_CODE = Column(Integer)
    CATEGORY_CODE = Column(Integer)
    SUB_CATEGORY_CODE = Column(Integer)
    ABUSE_LEVEL = Column(String)
    COLOUR_RATING = Column(Integer)
    MASTER_STATUS = Column(String)
    IMAGE_NAME = Column(String)
    SNO = Column(Integer)
    PARTICULAR = Column(String)
