import uvicorn
from fastapi import FastAPI


app = FastAPI(title="智能表情包生成系统-Python端")

if __name__ == "__main__":
    uvicorn.run("app.main:app --reload", host="127.0.0.1", port=8000, reload=True)