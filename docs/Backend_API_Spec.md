# 后端 API 规格（强约束，全做）

## 0. 通用约定
BasePath: /api
响应格式：
{
  "code": 0,
  "msg": "ok",
  "data": ...
}
错误：
- code=400 参数错误
- code=404 资源不存在
- code=500 服务异常

分页：
- page 从 1 开始
- pageSize 默认 20

时间范围：
- start, end 格式 yyyy-MM-dd（不传则全量）

认证（最小实现）：
- 登录：POST /api/auth/login 返回 token + role
- 除 /api/auth/login 外，接口请求头携带：Authorization: Bearer <token>

## 1. 认证
### POST /api/auth/login
body：
{
  "username": "pm",
  "password": "123456"
}
返回 data：
{
  "token": "xxx",
  "role": "PM"
}

## 2. 导入与采集
### POST /api/reviews/import
支持：
- multipart/form-data：file（CSV / XLSX / JSON 文件）
- （可选）application/json：JSON 数组对象

返回 data：
{
  "inserted": 1000,
  "skipped": 23,
  "errors": 2
}
强约束：导入完成后自动触发分析流水线（清洗->归因->情感->分词->关键词->主题->聚类->预警/建议可用）

### POST /api/crawl/run
body：
{
  "platformName": "JD",
  "productName": "XX蓝牙耳机",
  "pages": 3
}
行为：读取 data/crawl_samples/{platformName}/*.json 或 *.html 样例，解析为评论写入 review 表
返回 data：
{
  "inserted": 200,
  "skipped": 10,
  "errors": 0,
  "batchId": "crawl_20260101_001"
}

## 3. 基础数据（用于前端下拉框）
### GET /api/meta/products
返回：[{id,name,brand,model,isCompetitor}...]

### GET /api/meta/platforms
返回：[{id,name}...]

### GET /api/meta/aspects
返回：[{id,name}...]

## 4. 评论查询
### GET /api/reviews
params:
- productId (必选)
- platformId (可选)
- aspectId (可选)          # 过滤属于某维度的评论
- sentiment (可选)         # POS/NEU/NEG，指 overall_sentiment_label
- keyword (可选)           # content_clean 模糊
- start/end (可选)
- page/pageSize
返回 data：
{
  "page":1,
  "pageSize":20,
  "total":1234,
  "items":[
    {
      "id":1,
      "platformName":"JD",
      "productName":"XX蓝牙耳机",
      "rating":2,
      "reviewTime":"2025-12-02 12:00:00",
      "contentClean":"续航太差了，掉电快。",
      "overallSentiment":"NEG",
      "overallScore":-0.67,
      "aspects":[
        {"aspectId":2,"aspectName":"续航","sentiment":"NEG","score":-0.7}
      ]
    }
  ]
}

### GET /api/reviews/{id}
返回 data：
{
  "id":1,
  "platformName":"JD",
  "productName":"XX蓝牙耳机",
  "rating":2,
  "reviewTime":"2025-12-02 12:00:00",
  "contentRaw":"...",
  "contentClean":"...",
  "overallSentiment":"NEG",
  "overallScore":-0.67,
  "aspectResults":[
    {
      "aspectId":2,
      "aspectName":"续航",
      "hitKeywords":["续航","掉电快"],
      "sentiment":"NEG",
      "score":-0.7,
      "confidence":0.8
    }
  ]
}

## 5. 总览与基础分析
### GET /api/dashboard/overview
params: productId (必选), start/end (可选)
返回 data：
{
  "reviewCount": 1234,
  "posRate": 0.55,
  "neuRate": 0.22,
  "negRate": 0.23,
  "trend": [
    {"date":"2025-12-01","count":120,"negRate":0.18},
    {"date":"2025-12-02","count":200,"negRate":0.25}
  ],
  "topPriorities":[
    {"level":"ASPECT","aspectId":2,"name":"续航","priority":0.82,"negRate":0.45,"growth":1.6,"volume":320},
    {"level":"KEYWORD","aspectId":2,"name":"掉电快","priority":0.71,"negRate":0.40,"growth":1.9,"volume":150}
  ]
}

### GET /api/analysis/aspects
params: productId, start/end
返回 data：
{
  "items":[
    {"aspectId":1,"aspectName":"音质","volume":400,"posRate":0.70,"neuRate":0.18,"negRate":0.12},
    {"aspectId":2,"aspectName":"续航","volume":320,"posRate":0.30,"neuRate":0.25,"negRate":0.45}
  ]
}

### GET /api/analysis/trend
params: productId, aspectId(可选), start/end
返回 data：
{
  "series":[
    {"date":"2025-12-01","count":120,"pos":50,"neu":30,"neg":40,"negRate":0.33},
    {"date":"2025-12-02","count":200,"pos":80,"neu":40,"neg":90,"negRate":0.45}
  ]
}

### GET /api/analysis/keywords
params: productId, aspectId(可选), start/end, topN(默认20)
返回 data：
{
  "items":[
    {"keyword":"掉电快","freq":120,"negFreq":100},
    {"keyword":"充不进","freq":60,"negFreq":55}
  ]
}

## 6. NLP 扩展分析
### GET /api/analysis/topics
params: productId (必选), start/end (可选)
返回 data：
{
  "topicCount": 3,
  "items":[
    {"topicId":0,"topWords":["降噪","通话","风噪"],"weight":0.22,"evidenceReviewIds":[1,2,3]},
    {"topicId":1,"topWords":["续航","充电","掉电"],"weight":0.18,"evidenceReviewIds":[4,5,6]}
  ]
}
强约束：topics 必须可追溯（至少提供 evidenceReviewIds 以便下钻）

### GET /api/analysis/clusters
params: productId (必选), start/end (可选)
返回 data：
{
  "items":[
    {"id":10,"topTerms":["掉电","续航","充电"],"size":120,"negRate":0.61,"representativeReviewIds":[1,2,3,4,5]}
  ]
}

### GET /api/analysis/clusters/{id}
返回 data：
{
  "id":10,
  "topTerms":["掉电","续航","充电"],
  "size":120,
  "negRate":0.61,
  "representativeReviews":[
    {"id":1,"reviewTime":"2025-12-02 12:00:00","contentClean":"...","overallSentiment":"NEG"}
  ]
}
强约束：cluster/{id} 至少返回 5 条代表评论

## 7. 决策支持
### GET /api/decision/priority
params: productId, start/end, topN(默认10)
返回 data：
{
  "items":[
    {"level":"ASPECT","aspectId":2,"name":"续航","priority":0.82,"negRate":0.45,"growth":1.6,"volume":320},
    {"level":"KEYWORD","aspectId":2,"name":"掉电快","priority":0.71,"negRate":0.40,"growth":1.9,"volume":150}
  ]
}

### GET /api/decision/suggestions
params: productId (必选), start/end (可选)
返回 data：
{
  "items":[
    {
      "id":1,
      "refType":"ASPECT",
      "refId":2,
      "suggestionText":"建议优化充电策略，降低掉电异常。",
      "evidence":[
        {"reviewId":123,"snippet":"续航太差，掉电很快..."}
      ]
    }
  ]
}
强约束：每条建议必须包含 evidence（reviewId + 摘要）

## 8. 竞品对比
### GET /api/compare/aspects
params: productId (必选), competitorId (必选), start/end (可选)
返回 data：
{
  "items":[
    {
      "aspectId":1,
      "aspectName":"音质",
      "self":{"posRate":0.70,"neuRate":0.18,"negRate":0.12},
      "competitor":{"posRate":0.62,"neuRate":0.20,"negRate":0.18},
      "diff":{"posRate":0.08,"neuRate":-0.02,"negRate":-0.06},
      "normalized":{"negRate":0.32}
    }
  ]
}
强约束：返回 8 个维度的对比项

## 9. 趋势预警
### GET /api/alerts
params:
- productId (必选)
- status (可选)  # new/ack
返回 data：
{
  "items":[
    {
      "id":1,
      "metric":"negRate",
      "aspectId":null,
      "windowStart":"2025-12-01",
      "windowEnd":"2025-12-07",
      "currentValue":0.35,
      "prevValue":0.20,
      "threshold":0.10,
      "status":"new",
      "createdAt":"2025-12-08 10:00:00"
    }
  ]
}

### POST /api/alerts/ack?id=
返回 data：
{ "acked": true }

## 10. 活动/版本前后对比（闭环验证）
### POST /api/events
body：
{
  "productId": 1,
  "name": "双11活动",
  "type": "activity",
  "startDate": "2025-11-01",
  "endDate": "2025-11-11"
}
返回 data：
{ "id": 1 }

### GET /api/events
params: productId (必选)
返回 data：
[
  {"id":1,"name":"双11活动","type":"activity","startDate":"2025-11-01","endDate":"2025-11-11"}
]

### GET /api/evaluate/before-after?eventId=
返回 data：
{
  "event":{"id":1,"name":"双11活动","type":"activity","startDate":"2025-11-01","endDate":"2025-11-11"},
  "before":{"reviewCount":800,"negRate":0.22,"aspects":[{"aspectId":2,"negRate":0.40}]},
  "after":{"reviewCount":1200,"negRate":0.28,"aspects":[{"aspectId":2,"negRate":0.55}]},
  "keywordChanges":[{"keyword":"掉电","beforeFreq":10,"afterFreq":30,"diff":20}]
}

## 11. 分析触发（必须提供）
### POST /api/analysis/run
body:
{ "productId": 1 }   # 可选：start/end；允许只传 productId 做全量重算
返回：
{ "started": true }

强约束：
- /import 与 /crawl/run 自动触发一次
- /analysis/run 允许人工重算一次
