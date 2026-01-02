# 前端页面需求（强约束：只做三页）

## 0. 技术
- Vue3 + Element Plus + ECharts
- API BaseURL 通过环境变量 VITE_API_BASE_URL 配置

## 1. 路由（固定）
- /overview  总览
- /analysis  维度分析
- /reviews   评论检索与详情

顶部全局选择器（固定存在于三页）：
- productId 下拉（GET /api/meta/products）
- 时间范围 start/end（date picker）

## 2. 页面规格

## 2.1 总览页 /overview（必须）
组件：
1) KPI 卡片：reviewCount、negRate、posRate、neuRate（/api/dashboard/overview）
2) 趋势折线：trend.date vs trend.negRate（/api/dashboard/overview）
3) TopPriorities 表格：显示 level/name/priority/negRate/growth/volume（/api/dashboard/overview）
交互：
- 点击 TopPriorities 行：
  - 若 level=ASPECT：跳转 /analysis 并选中该 aspect
  - 若 level=KEYWORD：跳转 /reviews 并带上 aspectId+keyword 筛选

## 2.2 维度分析页 /analysis（必须）
左侧：
- 维度列表表格（/api/analysis/aspects）
  展示：aspectName, volume, negRate, posRate, neuRate

右侧：
1) 当前选中维度趋势图（/api/analysis/trend?aspectId=xxx）
2) 当前选中维度关键词 TopN（/api/analysis/keywords?aspectId=xxx）

交互：
- 点击维度行 => 更新右侧趋势与关键词

## 2.3 评论页 /reviews（必须）
筛选栏（必须）：
- platformId（下拉 /api/meta/platforms）
- aspectId（下拉 /api/meta/aspects）
- sentiment（POS/NEU/NEG）
- keyword（输入框）
- start/end

列表（必须）：
- 表格列：时间、平台、评分、情感、内容摘要、维度标签
- 分页：page/pageSize

详情（必须）：
- 点击某条打开抽屉（GET /api/reviews/{id}）
- 展示：
  - contentRaw/contentClean
  - overall sentiment + score
  - aspectResults：每个维度的命中关键词 + sentiment + score
- 命中关键词高亮显示（简单字符串高亮即可）

## 3. 前端工程结构（固定）
/src/api      封装 API
/src/views    三个页面
/src/components 公共组件（筛选栏/图表卡片）
/src/router   路由

## 4. 图表要求（固定）
- overview 趋势：折线图
- analysis 趋势：折线图
- analysis 维度情感：可用表格展示（不额外增加更多图，避免范围扩大）
