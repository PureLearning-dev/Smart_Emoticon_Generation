"""
style_tag 全项目默认枚举（逗号分隔）。

职责：
- 供 app.core.config 的 STYLE_TAG_LIST 默认值、视觉管线 vision_metadata、文生图 image_gen 降级读取共用；
- 部署时可通过环境变量 STYLE_TAG_LIST 覆盖，三处逻辑均解析同一 CSV，避免枚举漂移。

说明：
- 在「固定枚举」前提下扩充情绪/态度类，减少将生气、吐槽类误判为「搞笑」；
- 与 MySQL meme_assets.style_tag VARCHAR(100) 兼容，单标签宜为短中文词。
"""

# 默认 21 类：情绪态度 + 场景 + 画风调性；与 VL 提示词中的判别细则一致
STYLE_TAG_LIST_DEFAULT = (
    "搞笑,生气,吐槽,无语,震惊,敷衍,认同,阴阳怪气,撒娇,社死,"
    "治愈,励志,毒鸡汤,萌系,复古,简约,职场,情侣,朋友,节日,日常"
)
