import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base
from dotenv import load_dotenv

load_dotenv()

# We use the DB_CONNECTION_STRING from .env, falling back to local PostgreSQL if not specified
SQLALCHEMY_DATABASE_URL = os.getenv(
    "DB_CONNECTION_STRING",
    "postgresql://postgres:postgres@localhost:5432/nayeam_database"
)

engine = create_engine(SQLALCHEMY_DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

# Dependency
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
