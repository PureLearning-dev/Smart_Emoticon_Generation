# 变量定义，方便后续修改
PYTHON_ENTRY = ai-kore.app.main:app
JAVA_DIR = smart_meter

.PHONY: run-ai run-java install clean help

# --- 启动命令 ---

# 启动 Python AI 服务
run-ai:
	uv run uvicorn $(PYTHON_ENTRY) --reload

# 启动 SpringBoot 服务
run-java:
	cd $(JAVA_DIR) && ./mvnw spring-boot:run

# 同时启动两个服务 (需要安装过 parallel 或者简单后台运行)
run-all:
	make -j 2 run-ai run-java

# --- 工具命令 ---

# 一键安装所有依赖
install:
	uv sync
	cd $(JAVA_DIR) && ./mvnw install

# 清理垃圾文件 (pycache, target文件夹等)
clean:
	find . -type d -name "__pycache__" -exec rm -rf {} +
	cd $(JAVA_DIR) && ./mvnw clean

# 帮助信息
help:
	@echo "可用命令:"
	@echo "  make run-ai    - 启动 FastAPI 后端"
	@echo "  make run-java  - 启动 SpringBoot 前端"
	@echo "  make clean     - 清理所有缓存和临时文件"