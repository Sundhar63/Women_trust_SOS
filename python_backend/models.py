from sqlalchemy import Column, Integer, String, DateTime, Float
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

class UserSession(Base):
    __tablename__ = "USER_SESSION"

    ID = Column(Integer, primary_key=True, index=True)
    LOGIN_ID = Column(Integer, index=True, nullable=False)
    REFRESH_TOKEN_HASH = Column(String(64), unique=True, index=True, nullable=False)
    DEVICE_ID = Column(String(128))
    USER_AGENT = Column(String(256))
    IP_HASH = Column(String(64))
    CREATED_AT = Column(DateTime, nullable=False)
    LAST_USED_AT = Column(DateTime)
    EXPIRES_AT = Column(DateTime, nullable=False)
    REVOKED_AT = Column(DateTime)
    ROTATED_FROM = Column(Integer)


class UserLocation(Base):
    __tablename__ = "USER_LOCATION"

    ID = Column(Integer, primary_key=True, index=True)
    LOGIN_ID = Column(Integer, index=True, nullable=False)
    LATITUDE = Column(Float, nullable=False)
    LONGITUDE = Column(Float, nullable=False)
    ACCURACY = Column(Float)
    PROVIDER = Column(String(32))
    DEVICE_ID = Column(String(128))
    CAPTURED_AT = Column(DateTime)
    CREATED_AT = Column(DateTime, nullable=False)


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
    TAMIL_PARTICULAR = Column(String)
    CONTACT_NO = Column(String)
    CONTACT_MAIL_ID = Column(String)
    CONTACT_WEBSITE = Column(String)
