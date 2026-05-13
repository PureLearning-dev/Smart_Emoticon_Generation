-- =============================================================================
-- 管理后台演示造数脚本（可重复执行，尽量使用 NOT EXISTS / INSERT IGNORE 避免重复）
-- 库名：请与 smart_meter application.yaml 中一致（如 smart_meter_system）
-- 用法：mysql -uroot -p smart_meter_system < SQL/seed_admin_demo.sql
--
-- 种子用户默认登录密码（与 Spring BCryptPasswordEncoder 一致）：Demo123456
-- 对应 password_hash（strength=10）：
-- =============================================================================

USE smart_meter_system;

-- 以下为 Spring BCrypt 对明文「Demo123456」编码结果（项目生成，勿手改）
SET @pwd_demo := '$2a$10$SmyfsBLbFPJlSMPe2hpdT.aXCRqzh.JonZ7UPI4DOrLtHM9W5D9Ze';

-- 12 条演示用户（username / openid 唯一；若已存在则忽略该行）
INSERT IGNORE INTO users (username, password_hash, openid, nickname, status, user_type) VALUES
('demo_seed_u01', @pwd_demo, 'oseed2026demo01', '演示种子用户01', 1, 1),
('demo_seed_u02', @pwd_demo, 'oseed2026demo02', '演示种子用户02', 1, 1),
('demo_seed_u03', @pwd_demo, 'oseed2026demo03', '演示种子用户03', 1, 1),
('demo_seed_u04', @pwd_demo, 'oseed2026demo04', '演示种子用户04', 1, 1),
('demo_seed_u05', @pwd_demo, 'oseed2026demo05', '演示种子用户05', 1, 1),
('demo_seed_u06', @pwd_demo, 'oseed2026demo06', '演示种子用户06', 1, 1),
('demo_seed_u07', @pwd_demo, 'oseed2026demo07', '演示种子用户07', 1, 1),
('demo_seed_u08', @pwd_demo, 'oseed2026demo08', '演示种子用户08', 1, 1),
('demo_seed_u09', @pwd_demo, 'oseed2026demo09', '演示种子用户09', 1, 1),
('demo_seed_u10', @pwd_demo, 'oseed2026demo10', '演示种子用户10', 1, 1),
('demo_seed_u11', @pwd_demo, 'oseed2026demo11', '演示种子用户11', 1, 1),
('demo_seed_u12', @pwd_demo, 'oseed2026demo12', '演示种子用户12', 1, 1);

-- 广场文章索引 + 正文（各一条，按标题去重）
INSERT INTO plaza_contents (content_type, title, summary, status, sort_order)
SELECT 2, '【种子数据】答辩演示文章', '用于管理后台与首页文章推荐演示', 1, 999
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM plaza_contents WHERE title = '【种子数据】答辩演示文章');

SET @pc_demo := (SELECT id FROM plaza_contents WHERE title = '【种子数据】答辩演示文章' LIMIT 1);

INSERT INTO plaza_articles (plaza_content_id, content_body, author_name, status)
SELECT @pc_demo, '<p>本文为种子数据，可在管理后台删除对应广场内容与文章。</p>', '系统种子', 1
FROM DUAL
WHERE @pc_demo IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM plaza_articles a WHERE a.plaza_content_id = @pc_demo);

-- 用户生成图示例（挂在 demo_seed_u01 / u02 上，按 prompt 标记去重）
INSERT INTO user_generated_images (user_id, generated_image_url, generation_status, is_public, prompt_text, usage_scenario, style_tag)
SELECT u.id, 'https://picsum.photos/seed/smartmeter-ug1/480/480', 1, 1, '【种子数据】生成图1', '日常', '演示'
FROM users u
WHERE u.username = 'demo_seed_u01'
  AND NOT EXISTS (SELECT 1 FROM user_generated_images g WHERE g.prompt_text = '【种子数据】生成图1')
LIMIT 1;

INSERT INTO user_generated_images (user_id, generated_image_url, generation_status, is_public, prompt_text, usage_scenario, style_tag)
SELECT u.id, 'https://picsum.photos/seed/smartmeter-ug2/480/480', 1, 0, '【种子数据】生成图2', '职场', '搞笑'
FROM users u
WHERE u.username = 'demo_seed_u02'
  AND NOT EXISTS (SELECT 1 FROM user_generated_images g WHERE g.prompt_text = '【种子数据】生成图2')
LIMIT 1;

-- 素材库示例（embedding_id 唯一）
INSERT INTO meme_assets (title, file_url, embedding_id, status, is_public, usage_scenario, style_tag)
SELECT '【种子】演示素材1', 'https://picsum.photos/seed/smartmeter-m1/400/400', 'seed_embedding_demo_001', 1, 1, '日常', '搞笑'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM meme_assets WHERE embedding_id = 'seed_embedding_demo_001');

INSERT INTO meme_assets (title, file_url, embedding_id, status, is_public, usage_scenario, style_tag)
SELECT '【种子】演示素材2', 'https://picsum.photos/seed/smartmeter-m2/400/400', 'seed_embedding_demo_002', 1, 1, '节日', '温馨'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM meme_assets WHERE embedding_id = 'seed_embedding_demo_002');

-- 执行后可在管理后台仪表盘查看各表 COUNT；也可在 MySQL 中核对：
-- SELECT COUNT(*) AS user_cnt FROM users;
-- SELECT COUNT(*) FROM user_generated_images WHERE prompt_text LIKE '【种子数据】%';
