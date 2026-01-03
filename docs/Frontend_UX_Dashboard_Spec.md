# 前端页面需求（MVP + 摘要补齐扩展）

## 0. 技术
- Vue3 + Element Plus + ECharts
- API BaseURL 通过环境变量 VITE_API_BASE_URL 配置

## 1. 登录与角色（必须）
- 登录页：/login
- 登录接口：POST /api/auth/login，返回 token + role（PM/MARKET/OPS）
- token 保存到 localStorage（或 pinia），请求头带 Authorization: Bearer <token>
- 路由守卫：未登录跳转 /login；登录后按 role 跳转对应看板入口
- 菜单与路由可见性：前端按 role 显示不同菜单与页面入口

## 2. 路由（必须）
公共：
- /login

基础分析页（可复用现有三页）：
- /overview   总览
- /analysis   维度分析
- /reviews    评论检索与详情

扩展能力页：
- /topics         主题建模（LDA）
- /clusters       问题聚类列表
- /clusters/:id   聚类详情（代表评论）
- /compare        竞品对比
- /alerts         趋势预警
- /suggestions    改进建议
- /events         活动/版本创建（最小表单即可）
- /before-after   活动/版本前后对比（eventId 查询）

角色看板入口（推荐）：
- /dashboard/pm
- /dashboard/market
- /dashboard/ops

## 3. 顶部全局选择器（建议）
通用：
- productId 下拉（GET /api/meta/products）
- 时间范围 start/end（date picker）

按页面补充：
- /compare：competitorId 下拉（从 products 里过滤 isCompetitor=1）
- /before-after：eventId 选择（可从 events 列表或创建后回填）

## 4. 页面规格

## 4.1 登录页 /login（必须）
组件：
- 用户名/密码输入框
- 登录按钮

交互：
- 成功：保存 token + role，跳转到 /dashboard/{role}
- 失败：toast 提示

## 4.2 角色看板（必须）
PM 看板 /dashboard/pm：
- 聚类结果（/api/analysis/clusters）+ 下钻详情
- 优先级 TopN（/api/decision/priority）
- 改进建议（/api/decision/suggestions）+ 证据下钻
- 活动/版本前后对比（/api/evaluate/before-after）

MARKET 看板 /dashboard/market：
- 正向关键词（可复用 /api/analysis/keywords，筛选 POS/或按 overall POS 统计的关键词，最小实现用现有关键词表即可）
- 主题分布（/api/analysis/topics）
- 竞品对比（/api/compare/aspects）

OPS 看板 /dashboard/ops：
- 趋势（可复用 /api/dashboard/overview 或 /api/analysis/trend）
- 预警列表（/api/alerts）+ ack
- 活动/版本前后对比（/api/evaluate/before-after）

## 4.3 主题页 /topics（必须）
展示：
- topicCount 与 topics 列表（topWords + weight）
- 每个 topic 提供 evidenceReviewIds（点击可跳转 /reviews 并按 reviewId 打开详情）
接口：
- GET /api/analysis/topics?productId=&start=&end=

## 4.4 聚类页 /clusters（必须）
列表展示字段（必须）：
- topTerms、size、negRate、representativeReviewIds（至少 5 条）
交互：
- 点击簇行进入 /clusters/:id
接口：
- GET /api/analysis/clusters?productId=&start=&end=

## 4.5 聚类详情 /clusters/:id（必须）
展示：
- topTerms、size、negRate
- 代表评论列表（至少 5 条）：reviewId + contentClean 摘要 + sentiment
接口：
- GET /api/analysis/clusters/{id}

## 4.6 竞品对比页 /compare（必须）
交互：
- 选择 competitorId 后请求接口，展示 8 个维度的对比项
展示：
- 表格：aspect、self/competitor 的 pos/neu/neg rate、diff、normalized
接口：
- GET /api/compare/aspects?productId=&competitorId=&start=&end=

## 4.7 预警页 /alerts（必须）
展示：
- 预警列表：metric、windowStart/windowEnd、current/prev/threshold、status
交互：
- status=new 的项可点击 ack
接口：
- GET /api/alerts?productId=&status=
- POST /api/alerts/ack?id=

## 4.8 建议页 /suggestions（必须）
展示：
- 建议列表：suggestionText
- evidence：reviewId + snippet（可点击打开评论详情抽屉）
接口：
- GET /api/decision/suggestions?productId=&start=&end=

## 4.9 活动/版本页 /events + /before-after（必须）
/events（最小实现）：
- 创建 event 表单（name/type/start/end）
- 调用 POST /api/events，创建成功后保存 eventId

/before-after（展示）：
- 选择 eventId 后调用 GET /api/evaluate/before-after
- 展示 before vs after 的 reviewCount/negRate/各维度 negRate/关键词变化

接口：
- POST /api/events
- GET /api/evaluate/before-after?eventId=

## 5. 评论页 /reviews（复用现有能力）
要求：
- 可用 reviewId 下钻（从 topics/clusters/suggestions/alerts 链接过来）
- 详情抽屉展示证据链：contentRaw/contentClean、overall sentiment、aspectResults

