import uvicorn
from fastapi import FastAPI
from app.api import search

app = FastAPI(title="AI Meme Service")
app.include_router(search.router, prefix="/api/search", tags=["search"])

if __name__ == "__main__":
    uvicorn.run("app.main:app --reload", host="127.0.0.1", port=8000, reload=True)