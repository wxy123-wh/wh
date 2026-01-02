# MVP 验收用例（强约束：必须全部通过）

## A. 导入与去重
A1 导入成功
- Given: /data/sample_reviews.csv
- When: POST /api/reviews/import
- Then: inserted > 0 且 errors == 0

A2 去重生效
- When: 再次导入同一文件
- Then: skipped > 0 且 inserted == 0（或极小）

## B. 分析产出正确（可解释）
B1 维度归因存在
- When: 导入后调用 GET /api/reviews?page=1&pageSize=20&productId=...
- Then: items[*].aspects 非空（至少部分评论包含维度标签）

B2 详情可解释
- When: GET /api/reviews/{id}
- Then: aspectResults[*] 必须包含 aspectName、hitKeywords、sentiment、score、confidence

B3 情感输出存在
- Then: review 列表中 overallSentiment 必须存在且为 POS/NEU/NEG 之一

## C. 总览与分析接口可用
C1 总览
- When: GET /api/dashboard/overview?productId=...
- Then: reviewCount>0 且 trend 非空 且 topPriorities 非空

C2 维度分析
- When: GET /api/analysis/aspects?productId=...
- Then: items 数量==8（固定维度数），且每项包含 volume 与 negRate

C3 维度趋势
- When: GET /api/analysis/trend?productId=...&aspectId=...
- Then: series 非空，且每项包含 date、count、negRate

C4 关键词
- When: GET /api/analysis/keywords?productId=...&aspectId=...&topN=20
- Then: items 长度<=20 且按 negFreq desc 排序

C5 优先级
- When: GET /api/decision/priority?productId=...&topN=10
- Then: items 长度==10（或小于10但非空），且每项包含 priority/negRate/growth/volume

## D. 前端可用性
D1 三页可打开且能看到数据
- /overview 显示 KPI、趋势、TopPriorities
- /analysis 点击维度可联动趋势与关键词
- /reviews 可筛选、可分页、可打开详情抽屉

## E. 性能下限（本地）
- 导入 10,000 行 CSV：<= 30s
- 评论列表接口（pageSize=20）：<= 1s
