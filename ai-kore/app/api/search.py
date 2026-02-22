from fastapi import APIRouter

router = APIRouter()

@router.get("/pictures")
def search_meme(query: str):
    return {"message": "success" + query}