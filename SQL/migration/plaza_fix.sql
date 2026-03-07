UPDATE plaza_contents
SET
  title = CASE id
    WHEN 1007 THEN '如何快速找到合适的表情包'
    WHEN 1008 THEN '图搜图为什么能提高找图效率'
    WHEN 1009 THEN '高频聊天场景下的表情使用建议'
    WHEN 1010 THEN '表情包内容整理与推荐思路说明'
    WHEN 1011 THEN '为什么聊天产品需要内容推荐页'
    WHEN 1012 THEN '如何设计更适合移动端阅读的文章卡片'
    WHEN 1013 THEN '从静态页面到真实接口页面的改造思路'
    WHEN 1014 THEN '如何让公共广场内容看起来更丰富'
    WHEN 1015 THEN '表情包系统中的文章内容应该写什么'
    WHEN 1016 THEN '中期答辩前如何准备稳定的演示数据'
  END,
  summary = CASE id
    WHEN 1007 THEN '从关键词、情绪、聊天语境三个角度，帮助你更快匹配到合适内容。'
    WHEN 1008 THEN '解释图搜图的核心思路，以及在模糊记忆场景下的实际价值。'
    WHEN 1009 THEN '围绕职场、朋友群、情侣聊天三个场景，介绍更自然的用图方式。'
    WHEN 1010 THEN '介绍公共广场内容为什么要按索引表与详情表拆分，以及这样设计的实际好处。'
    WHEN 1011 THEN '从用户浏览路径、停留时长和内容分发效率角度，说明推荐页的价值。'
    WHEN 1012 THEN '介绍标题、摘要、封面和标签如何组合，才能兼顾点击率与阅读体验。'
    WHEN 1013 THEN '总结前端静态占位页如何逐步替换为后端接口驱动页面。'
    WHEN 1014 THEN '通过排序、标签、摘要长度和封面图风格控制，提升页面整体观感。'
    WHEN 1015 THEN '说明教程型、说明型、运营型文章在表情包产品中的作用与写法。'
    WHEN 1016 THEN '围绕账号、样例数据、固定流程和容错设计，整理答辩前的数据准备方法。'
  END,
  tag_name = CASE id
    WHEN 1007 THEN '教程'
    WHEN 1008 THEN '科普'
    WHEN 1009 THEN '指南'
    WHEN 1010 THEN '系统设计'
    WHEN 1011 THEN '产品'
    WHEN 1012 THEN '体验设计'
    WHEN 1013 THEN '开发实践'
    WHEN 1014 THEN '运营'
    WHEN 1015 THEN '内容策划'
    WHEN 1016 THEN '答辩准备'
  END
WHERE id IN (1007,1008,1009,1010,1011,1012,1013,1014,1015,1016);