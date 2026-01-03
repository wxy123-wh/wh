# 06 对齐摘要的补功能需求（必须实现）

目标：在现有系统（导入/分析/优先级/基础看板）基础上，补齐《选题摘要》包含的功能：
- 多平台获取（模拟爬取）+ Excel/JSON 导入
- 分词（jieba）、主题建模（LDA）
- 问题聚类（TF-IDF + KMeans）
- 竞品对比
- 趋势预警
- 建议模板输出
- 角色化展示（PM/市场/运营）
- 活动/版本前后对比（闭环验证）

## 0. TODOLIST（未实现功能模块）
> 说明：以下清单用于后续逐步实现；以当前代码库为准（已实现：CSV 导入、/api/analysis/aspects|trend|keywords、/api/decision/priority、基础看板/评论列表）。  
> 勾选建议以“数据模型/接口/前端入口/验收用例”四个维度一起闭环。

- [ ] 导入扩展：`POST /api/reviews/import` 支持 XLSX
- [ ] 导入扩展：`POST /api/reviews/import` 支持 JSON（数组对象）
- [ ] 模拟爬取：新增 `POST /api/crawl/run`（读取 `data/crawl_samples/{platformName}/*`）
- [ ] 分词：`review.content_clean` -> tokens（`review_token` 表或 JSON 字段），停用词 `data/stopwords.txt`
- [ ] 主题建模：`topic_result` 表 + `GET /api/analysis/topics`
- [ ] 问题聚类：`cluster/review_cluster` 表 + `GET /api/analysis/clusters` / `GET /api/analysis/clusters/{id}`
- [x] 竞品对比：`product` 支持竞品标记（`is_competitor`）+ `GET /api/compare/aspects`
- [x] 趋势预警：`alert` 表 + `GET /api/alerts` + `POST /api/alerts/ack`
- [x] 改进建议：`suggestion_template/suggestion_instance` 表 + `GET /api/decision/suggestions`
- [x] 角色化展示：`POST /api/auth/login`（token+role）+ 前端按 role 菜单/路由
- [x] 活动/版本前后对比：`event` 表 + `POST /api/events` + `GET /api/evaluate/before-after`
- [x] 前端扩展：登录页 + PM/MARKET/OPS 三看板 + topics/clusters/compare/alerts/suggestions/before-after 页面入口与图表
- [ ] 验收：对照 `docs/Acceptance_Criteria_TestCases.md` 跑通 1-9 条

---

## 1. 范围与原则
1) 允许“模拟爬取”：从本地样例文件读取并解析入库，不做真实反爬登录。
2) NLP 算法允许通过独立服务实现（推荐 Docker 内 Python FastAPI：jieba/gensim/sklearn）。
3) 所有新功能必须有 API + 前端页面入口 + 验收用例。
4) 所有输出必须可解释：可追溯到评论证据。

---

## 2. 数据接入扩展（必须）
### 2.1 导入扩展
- 现有 POST /api/reviews/import 扩展支持：
  - CSV（已有）
  - Excel(xlsx)
  - JSON（数组对象）
返回 inserted/skipped/errors。

### 2.2 模拟爬取
新增 POST /api/crawl/run
- 入参：
  - platformName
  - productName
  - pages（模拟页数）
- 行为：读取 data/crawl_samples/{platformName}/*.json 或 *.html 样例，解析为评论写入 review 表
- 返回：
  - inserted/skipped/errors/batchId

验收：
- 运行一次 crawl/run 后，review 数量增长。

---

## 3. 分词与主题建模（必须）
### 3.1 分词（jieba）
- 对评论 content_clean 进行分词，存储为 tokens（可存在 JSON 字段或单独表 review_token）
- 去停用词：使用 data/stopwords.txt

### 3.2 LDA 主题建模
新增 topic_result 表（建议字段）：
- id, product_id, start_date, end_date, topic_count, topics_json, created_at
topics_json 示例：
[
  {"topicId":0,"topWords":["降噪","通话","风噪"],"weight":0.22},
  ...
]

新增接口：
- GET /api/analysis/topics?productId=&start=&end=
返回主题分布与 topWords。

验收：
- topics 接口返回 topicCount>0 且每个 topic 有 topWords。

---

## 4. 问题聚类（必须）
### 4.1 TF-IDF + KMeans 聚类
新增 cluster / review_cluster 表：
cluster：
- id, product_id, start_date, end_date, k, top_terms_json, size, negRate, created_at
review_cluster：
- id, review_id, cluster_id

新增接口：
- GET /api/analysis/clusters?productId=&start=&end=
- GET /api/analysis/clusters/{id}
clusters 列表必须包含：top_terms、size、negRate、代表评论 id 列表（或可分页查询）

验收：
- clusters 列表非空
- cluster/{id} 返回至少 5 条代表评论

---

## 5. 竞品对比（必须）
前提：product 支持标记竞品（is_competitor=1），并能选择 competitorId。

新增接口：
- GET /api/compare/aspects?productId=&competitorId=&start=&end=
返回每个维度的 negRate/posRate/neuRate + 差值（本品-竞品）+ 归一化值（minmax 或 z-score）

验收：
- 竞品对比能返回 8 个维度的对比项

---

## 6. 趋势预警（必须）
新增 alert 表：
- id, product_id, metric, aspect_id(nullable), window_start, window_end
- current_value, prev_value, threshold, status(new/ack), created_at

规则：
- negRate 在最近窗口相比上一窗口上涨超过阈值 => 产生 alert

接口：
- GET /api/alerts?productId=&status=
- POST /api/alerts/ack?id=

验收：
- 可通过构造数据触发至少一条 alert，并能 ack

---

## 7. 改进建议（必须）
新增 suggestion_template / suggestion_instance：
template：
- id, match_type(aspect/cluster/keyword), match_value, suggestion_text, created_at
instance：
- id, product_id, ref_type(aspect/cluster), ref_id, suggestion_text, evidence_json, created_at

接口：
- GET /api/decision/suggestions?productId=&start=&end=
返回建议列表（含证据评论 id/内容摘要）

验收：
- suggestions 返回非空，且每条有 evidence

---

## 8. 角色化展示（必须）
角色：PM / MARKET / OPS

最小实现：
- POST /api/auth/login（用户名密码写死在配置里也行）返回 token + role
- 前端根据 role 显示不同菜单与页面

页面要求：
PM 看板：
- 聚类结果 + 优先级 + 改进前后趋势 + 建议
MARKET 看板：
- 正向关键词 + 主题分布 + 竞品对比
OPS 看板：
- 趋势 + 预警 + 活动前后对比

验收：
- 不同 role 登录后可见页面不同

---

## 9. 活动/版本前后对比（闭环验证，必须）
新增 event 表：
- id, product_id, name, type(activity/version), start_date, end_date, created_at

接口：
- POST /api/events
- GET /api/evaluate/before-after?eventId=
返回：
- before vs after 的 reviewCount/negRate/各维度negRate/关键词变化

验收：
- before-after 返回对比数据非空

---

## 10. 前端扩展（必须）
在现有 frontend（若尚未实现则先实现）基础上新增：
- 登录页
- 三个角色看板路由与菜单
- topics/clusters/compare/alerts/suggestions/before-after 的图表与表格展示

---

## 11. 最终验收清单（必须全通过）
1) import 支持 CSV+XLSX+JSON
2) crawl/run 能写入数据
3) topics 返回主题分布
4) clusters 返回聚类簇与详情
5) compare 返回维度对比
6) alerts 能产生+ack
7) suggestions 输出建议+证据
8) 登录后角色不同页面不同
9) before-after 返回活动前后对比
