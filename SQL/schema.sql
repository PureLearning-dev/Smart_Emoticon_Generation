-- 创建用户表（存储用户信息）

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID，主键，自增',
    openid VARCHAR(64) NOT NULL UNIQUE COMMENT '微信小程序唯一标识openid',
    nickname VARCHAR(100) COMMENT '用户昵称',
    avatar_url VARCHAR(255) COMMENT '用户头像URL地址',
    status TINYINT DEFAULT 1 COMMENT '用户状态：1正常，0禁用',
    user_type TINYINT DEFAULT 1 COMMENT '用户类型：1普通用户，2管理员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 创建表情包表（存储从网络爬虫获取的表情包）

CREATE TABLE meme_assets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '表情包ID，主键',
  title VARCHAR(255) COMMENT '表情包标题',
  file_url VARCHAR(500) NOT NULL COMMENT '原始图片存储地址（OSS/MinIO）',
  thumbnail_url VARCHAR(500) COMMENT '缩略图地址',
  ocr_text TEXT COMMENT 'OCR识别出的文本内容',
  description TEXT COMMENT '图片语义描述信息（可用于RAG增强）',
  content_text TEXT COMMENT '用于向量化的统一语义文本（title+ocr+description+tag）',
  style_tag VARCHAR(100) COMMENT '风格标签（如搞笑、情侣、动漫等）',
  source_type TINYINT DEFAULT 1 COMMENT '来源类型：1系统采集底图，2用户创作成品',
  source VARCHAR(100) COMMENT '图片来源（爬虫站名或用户UID）',
  embedding_id VARCHAR(64) COMMENT '对应Milvus中的向量ID',
  status TINYINT DEFAULT 1 COMMENT '状态：1正常，0下架',
  is_public TINYINT DEFAULT 1 COMMENT '是否公开到广场：1公开，0私有',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  KEY idx_public_status_ctime (is_public, status, create_time, id),
  KEY idx_style_public_status_ctime (style_tag, is_public, status, create_time),
  KEY idx_source_type_source_ctime (source_type, source, create_time),
  UNIQUE KEY uk_embedding_id (embedding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表情包主数据表';