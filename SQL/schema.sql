-- 创建用户表（存储用户信息）
-- 支持账号密码登录；openid 可选，用于后续微信小程序登录

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID，主键，自增',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '登录账号，唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码密文（如 bcrypt/argon2），不可逆',
    openid VARCHAR(64) NULL UNIQUE COMMENT '微信小程序 openid，可选，后续小程序登录用',
    nickname VARCHAR(100) COMMENT '用户昵称',
    avatar_url VARCHAR(255) COMMENT '用户头像URL地址',
    status TINYINT DEFAULT 1 COMMENT '用户状态：1正常，0禁用',
    user_type TINYINT DEFAULT 1 COMMENT '用户类型：1普通用户，2管理员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY idx_username (username),
    KEY idx_openid (openid),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表（账号密码登录，openid 预留小程序）';

-- 创建公共广场内容表（支持展示表情包/文章等公开内容）

CREATE TABLE IF NOT EXISTS plaza_contents (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '广场内容ID，主键',
  content_type TINYINT NOT NULL COMMENT '内容类型：1表情包，2文章',
  title VARCHAR(255) NOT NULL COMMENT '内容标题',
  summary VARCHAR(500) COMMENT '内容摘要（列表展示）',
  cover_url VARCHAR(500) COMMENT '封面图URL',
  tag_name VARCHAR(100) COMMENT '展示标签（如热门、教程、精选）',
  ref_meme_asset_id BIGINT COMMENT '关联表情包ID（content_type=1时可用）',
  article_url VARCHAR(500) COMMENT '文章详情链接（content_type=2时可用）',
  sort_order INT DEFAULT 0 COMMENT '排序权重，值越大越靠前',
  status TINYINT DEFAULT 1 COMMENT '状态：1上架，0下架',
  create_user_id BIGINT COMMENT '创建人用户ID（管理员或运营）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  KEY idx_status_sort_ctime (status, sort_order, create_time, id),
  KEY idx_type_status_sort (content_type, status, sort_order),
  KEY idx_ref_meme_asset_id (ref_meme_asset_id),
  KEY idx_create_user_id (create_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共广场内容表';

-- 创建公共广场文章详情表（用于点击文章后展示正文）

CREATE TABLE IF NOT EXISTS plaza_articles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文章ID，主键',
  plaza_content_id BIGINT NOT NULL COMMENT '关联广场内容ID，对应plaza_contents.id',
  content_body LONGTEXT NOT NULL COMMENT '文章正文内容（支持长文本）',
  author_name VARCHAR(100) COMMENT '作者名称',
  source_name VARCHAR(100) COMMENT '文章来源名称（如官方、社区）',
  source_url VARCHAR(500) COMMENT '文章来源URL（可选）',
  read_count INT DEFAULT 0 COMMENT '阅读量',
  like_count INT DEFAULT 0 COMMENT '点赞量',
  status TINYINT DEFAULT 1 COMMENT '状态：1发布，0下线',
  publish_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  UNIQUE KEY uk_plaza_content_id (plaza_content_id),
  KEY idx_status_publish_time (status, publish_time),
  KEY idx_author_status_publish (author_name, status, publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共广场文章详情表';

-- 创建用户生成图片记录表（用于“我的生成”页面）

CREATE TABLE IF NOT EXISTS user_generated_images (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '生成记录ID，主键',
  user_id BIGINT NOT NULL COMMENT '用户ID，对应users.id',
  source_meme_asset_id BIGINT COMMENT '来源素材ID（可选，对应meme_assets.id）',
  source_image_url VARCHAR(500) COMMENT '生成前源图片URL（可选）',
  prompt_text TEXT COMMENT '生成提示词/输入文案',
  generated_text TEXT COMMENT '生成文案内容（可选）',
  generated_image_url VARCHAR(500) NOT NULL COMMENT '生成后图片URL',
  style_tag VARCHAR(100) COMMENT '生成风格标签（如搞笑、治愈）',
  usage_scenario VARCHAR(100) COMMENT '使用场景（如职场、情侣、朋友、节日、日常，用于广场瀑布流展示）',
  embedding_id VARCHAR(64) COMMENT 'Milvus 向量主键，与 meme_embeddings 集合关联，便于文本/图搜检索',
  generation_status TINYINT DEFAULT 1 COMMENT '生成状态：1成功，0失败，2处理中',
  is_public TINYINT DEFAULT 0 COMMENT '是否公开到广场：1公开，0私有',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  KEY idx_user_status_ctime (user_id, generation_status, create_time, id),
  KEY idx_user_public_ctime (user_id, is_public, create_time),
  KEY idx_source_meme_asset_id (source_meme_asset_id),
  KEY idx_style_status_ctime (style_tag, generation_status, create_time),
  KEY idx_usage_scenario_ctime (usage_scenario, is_public, create_time),
  KEY idx_embedding_id (embedding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户生成图片记录表';


-- 创建表情包表（存储从网络爬虫获取的表情包）

CREATE TABLE IF NOT EXISTS meme_assets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '表情包ID，主键',
  title VARCHAR(255) COMMENT '表情包标题',
  file_url VARCHAR(500) NOT NULL COMMENT '原始图片存储地址（OSS/MinIO）',
  thumbnail_url VARCHAR(500) COMMENT '缩略图地址',
  ocr_text TEXT COMMENT 'OCR识别出的文本内容',
  description TEXT COMMENT '图片语义描述信息（可用于RAG增强）',
  content_text TEXT COMMENT '用于向量化的统一语义文本（title+ocr+description+tag）',
  style_tag VARCHAR(100) COMMENT '风格标签（如搞笑、情侣、动漫等）',
  usage_scenario VARCHAR(100) COMMENT '使用场景（如职场、情侣、朋友、节日、日常，用于广场瀑布流展示）',
  source_type TINYINT DEFAULT 1 COMMENT '来源类型：1系统采集底图，2用户创作成品',
  source VARCHAR(100) COMMENT '图片来源（爬虫站名或用户UID）',
  embedding_id VARCHAR(64) COMMENT '对应Milvus中的向量ID',
  status TINYINT DEFAULT 1 COMMENT '状态：1正常，0下架',
  is_public TINYINT DEFAULT 1 COMMENT '是否公开到广场：1公开，0私有',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  KEY idx_public_status_ctime (is_public, status, create_time, id),
  KEY idx_style_public_status_ctime (style_tag, is_public, status, create_time),
  KEY idx_usage_scenario_public_status (usage_scenario, is_public, status),
  KEY idx_source_type_source_ctime (source_type, source, create_time),
  UNIQUE KEY uk_embedding_id (embedding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表情包主数据表';