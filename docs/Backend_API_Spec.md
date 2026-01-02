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

## 1. 导入
### POST /api/reviews/import
- form-data: file (CSV)
- 返回 data：
{
  "inserted": 1000,
  "skipped": 23,
  "errors": 2
}
强约束：导入完成后自动触发分析流水线（清洗->归因->情感->关键词->聚合查询可用）

## 2. 基础数据（用于前端下拉框）
### GET /api/meta/products
返回：[{id,name,brand,model}...]

### GET /api/meta/platforms
返回：[{id,name}...]

### GET /api/meta/aspects
返回：[{id,name}...]

## 3. 评论查询
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

## 4. 总览与分析
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

## 5. 决策支持
### GET /api/decision/priority
params: productId, start/end, topN(默认10)
返回 data：
{
  "items":[
    {"level":"ASPECT","aspectId":2,"name":"续航","priority":0.82,"negRate":0.45,"growth":1.6,"volume":320},
    {"level":"KEYWORD","aspectId":2,"name":"掉电快","priority":0.71,"negRate":0.40,"growth":1.9,"volume":150}
  ]
}

## 6. 分析触发（必须提供）
### POST /api/analysis/run
body:
{ "productId": 1 }   # 可选：start/end；MVP 允许只传 productId 做全量重算
返回：
{ "started": true }

强约束：
- /import 自动触发一次
- /analysis/run 允许人工重算一次
