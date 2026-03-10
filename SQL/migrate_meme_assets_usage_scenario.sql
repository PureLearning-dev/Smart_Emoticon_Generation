-- 为 meme_assets 表增加 usage_scenario 列（若建表时未包含该列则执行）
-- 若报错 Duplicate column name 'usage_scenario' 说明列已存在，可忽略。
-- 执行前请备份数据。

ALTER TABLE meme_assets
  ADD COLUMN usage_scenario VARCHAR(100) NULL COMMENT '使用场景（如职场、情侣、朋友、节日、日常，用于广场瀑布流展示）' AFTER style_tag;

-- 可选：为 usage_scenario 建索引（若 schema.sql 中已有可跳过）
-- ALTER TABLE meme_assets ADD KEY idx_usage_scenario_public_status (usage_scenario, is_public, status);
