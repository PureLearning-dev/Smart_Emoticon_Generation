"""
日志系统配置

职责：
- 统一日志级别与输出格式
- 便于调试与生产环境切换
"""

import logging
import sys

# 默认格式：时间 | 级别 | 模块 | 消息
DEFAULT_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)s | %(message)s"
DEFAULT_DATE_FMT = "%Y-%m-%d %H:%M:%S"


def get_logger(name: str, level: int = logging.INFO) -> logging.Logger:
    """
    获取具名 logger，若尚未配置 handler 则添加一个。

    Args:
        name: logger 名称，通常为 __name__
        level: 日志级别，默认 INFO

    Returns:
        配置好的 Logger 实例
    """
    logger = logging.getLogger(name)
    logger.setLevel(level)
    # 禁止向 root 传播，避免与 uvicorn 等默认 handler 重复输出
    logger.propagate = False

    if not logger.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setLevel(level)
        handler.setFormatter(logging.Formatter(DEFAULT_FORMAT, DEFAULT_DATE_FMT))
        logger.addHandler(handler)

    return logger
