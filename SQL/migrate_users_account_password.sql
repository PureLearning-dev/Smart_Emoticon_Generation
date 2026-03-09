-- 已有 users 表时：从「仅 openid」升级为「账号密码 + openid 可选」
-- 执行前请备份数据。若表为空或可重建，可直接删表后按 schema.sql 重建。

-- 1. 新增字段
ALTER TABLE users
  ADD COLUMN username VARCHAR(64) NULL COMMENT '登录账号，唯一' AFTER id,
  ADD COLUMN password_hash VARCHAR(255) NULL COMMENT '密码密文（如 bcrypt）' AFTER username;

-- 2. 去掉 openid 非空约束（MySQL 8.0 可 MODIFY 改为 NULL）
ALTER TABLE users MODIFY COLUMN openid VARCHAR(64) NULL COMMENT '微信小程序 openid，可选';

-- 3. 为已有数据生成占位账号（按 id），避免 UNIQUE 冲突；无数据可跳过
-- UPDATE users SET username = CONCAT('user_', id), password_hash = '' WHERE username IS NULL;

-- 4. 将 username 设为 NOT NULL 并加唯一索引（无数据或已补全后再执行）
-- ALTER TABLE users MODIFY COLUMN username VARCHAR(64) NOT NULL COMMENT '登录账号，唯一';
-- ALTER TABLE users ADD UNIQUE KEY uk_username (username);
-- ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(255) NOT NULL COMMENT '密码密文';

-- 5. 索引（若 schema 中已有可忽略）
-- ALTER TABLE users ADD KEY idx_username (username);
-- ALTER TABLE users ADD KEY idx_openid (openid);
-- ALTER TABLE users ADD KEY idx_status (status);
