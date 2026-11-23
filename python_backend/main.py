from fastapi import FastAPI
from dotenv import load_dotenv

from routers import api, auth, voice
from database import engine
import models

from fastapi.middleware.cors import CORSMiddleware

load_dotenv()

# Create all database tables (if they don't exist)
models.Base.metadata.create_all(bind=engine)

app = FastAPI(title="Naeyam Foundation API", description="Python migration of the C# Backend")

# Add CORS Middleware for production security
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], # In production, change "*" to your actual frontend domain
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api.router)
app.include_router(auth.router)
app.include_router(voice.router)

@app.get("/")
def read_root():
    return {"message": "Naeyam Foundation Python Backend API is running"}
