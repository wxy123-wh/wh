# 验收用例（MVP + 摘要补齐扩展）（强约束：必须全部通过）

## 0) 认证准备（必做）
0.1 登录获取 token
- When: POST /api/auth/login
- Then: 返回 token + role（PM/MARKET/OPS）

0.2 后续接口统一带鉴权头
- Header: Authorization: Bearer <token>
- 说明：除 /api/auth/login 外，所有 /api/** 都需要该 Header（否则返回 401）

## 1) import 支持 CSV+XLSX+JSON
1.1 CSV 导入成功
- Given: /data/sample_reviews.csv
- When: POST /api/reviews/import
- Then: inserted > 0 且 errors == 0

1.2 XLSX 导入成功
- Given: 任意与 CSV 字段一致的 xlsx（可由 sample_reviews.csv 转存得到）
- When: POST /api/reviews/import
- Then: inserted > 0 且 errors == 0

1.3 JSON 导入成功
- Given: 任意与 CSV 字段一致的 JSON 数组对象（可由 sample_reviews.csv 转换得到）
- When: POST /api/reviews/import
- Then: inserted > 0 且 errors == 0

1.4 去重生效（任选一种格式重复导入）
- When: 再次导入同一份数据
- Then: skipped > 0 且 inserted == 0（或极小）

## 2) crawl/run 能写入数据
2.1 模拟爬取写入
- Given: data/crawl_samples/{platformName} 下存在样例文件
- When: POST /api/crawl/run
- Then: inserted > 0 且返回 batchId

2.2 review 数量增长
- When: 调用一次 crawl/run 前后对比 GET /api/dashboard/overview?productId=...
- Then: reviewCount 增长

## 3) topics 返回主题分布
3.1 topics 可用且有 topWords
- When: GET /api/analysis/topics?productId=...&start=...&end=...
- Then: topicCount > 0 且 items[*].topWords 非空

3.2 topics 可追溯
- Then: items[*].evidenceReviewIds 非空（或提供可下钻的证据字段）

## 4) clusters 返回聚类簇与详情
4.1 clusters 列表非空
- When: GET /api/analysis/clusters?productId=...&start=...&end=...
- Then: items 非空，且每项包含 topTerms/size/negRate

4.2 cluster 详情至少 5 条代表评论
- When: GET /api/analysis/clusters/{id}
- Then: representativeReviews 长度 >= 5

## 5) compare 返回维度对比
5.1 竞品对比返回 8 维度
- Given: product 至少存在 1 个 competitor（is_competitor=1）
- Tip: 可直接在库里标记（示例）`update product set is_competitor=1 where id=<competitorId>;`
- When: GET /api/compare/aspects?productId=...&competitorId=...&start=...&end=...
- Then: items 数量 == 8，且包含 negRate/posRate/neuRate + diff + normalized

## 6) alerts 能产生 + ack
6.1 产生预警
- Given: 构造数据使最近窗口 negRate - 上一窗口 negRate >= threshold
- When: GET /api/alerts?productId=...&status=new
- Then: 至少 1 条 alert

6.2 ack 生效
- When: POST /api/alerts/ack?id=...
- Then: 再次 GET /api/alerts?productId=...&status=new，该条不再出现（或 status 变为 ack）

## 7) suggestions 输出建议 + 证据
7.1 suggestions 非空
- When: GET /api/decision/suggestions?productId=...&start=...&end=...
- Then: items 非空

7.2 每条建议包含 evidence
- Then: items[*].evidence 非空（包含 reviewId + 内容摘要）

## 8) 登录后角色不同页面不同
8.1 登录返回 token + role
- When: POST /api/auth/login
- Then: 返回 token 且 role ∈ {PM, MARKET, OPS}

8.2 前端菜单按角色变化
- When: 使用不同 role 登录进入系统
- Then: 可见页面入口不同（PM/MARKET/OPS 看板不同，且对应功能页可访问）

## 9) before-after 返回活动前后对比
9.1 创建 event 成功
- When: POST /api/events
- Then: 返回 eventId

9.2 before-after 返回非空对比数据
- When: GET /api/evaluate/before-after?eventId=...
- Then: before/after 均非空，包含 reviewCount/negRate/各维度 negRate/关键词变化

## 附：性能下限（本地，建议保留）
- 导入 10,000 行 CSV：<= 30s
- 评论列表接口（pageSize=20）：<= 1s
