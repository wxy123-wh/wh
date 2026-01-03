# 数据模型与导入契约（CSV/XLSX/JSON + 摘要扩展）（强约束）

## 1. 数据库
- MySQL 8.x
- utf8mb4
- 所有表必须提供建表 SQL（/backend/src/main/resources/schema.sql）

## 2. 维度集合（固定）
aspectName ∈ {音质, 续航, 降噪, 连接, 佩戴, 麦克风, 外观, 性价比}

## 3. 表结构（必须实现）

### 3.1 platform
- id BIGINT PK AUTO_INCREMENT
- name VARCHAR(64) UNIQUE NOT NULL
- created_at DATETIME NOT NULL

### 3.2 product
- id BIGINT PK AUTO_INCREMENT
- name VARCHAR(128) NOT NULL
- brand VARCHAR(64) NULL
- model VARCHAR(64) NULL
- is_competitor TINYINT(1) NOT NULL DEFAULT 0   # 竞品标记（1=竞品）
- created_at DATETIME NOT NULL

### 3.3 review（主表，必须含 overall 情感）
- id BIGINT PK AUTO_INCREMENT
- platform_id BIGINT NOT NULL
- product_id BIGINT NOT NULL
- review_id_raw VARCHAR(128) NULL
- rating INT NULL
- content_raw TEXT NOT NULL
- content_clean TEXT NOT NULL
- tokens_json JSON NULL                         # 分词结果（去停用词）
- review_time DATETIME NULL
- like_count INT NULL
- batch_id VARCHAR(64) NULL                     # 可选：模拟爬取批次
- hash VARCHAR(64) NOT NULL UNIQUE

- overall_sentiment_label VARCHAR(8) NOT NULL DEFAULT 'NEU'  # POS/NEU/NEG
- overall_sentiment_score DOUBLE NOT NULL DEFAULT 0.0       # [-1,1]

- created_at DATETIME NOT NULL

索引：
- idx_review_product_time(product_id, review_time)
- idx_review_platform(platform_id)
- idx_review_overall_sent(overall_sentiment_label)
- idx_review_batch(batch_id)

### 3.4 aspect（维度词典）
- id BIGINT PK AUTO_INCREMENT
- name VARCHAR(32) UNIQUE NOT NULL
- keywords_json JSON NOT NULL    # 该维度关键词及权重
- created_at DATETIME NOT NULL

### 3.5 review_aspect_result（评论维度结果）
- id BIGINT PK AUTO_INCREMENT
- review_id BIGINT NOT NULL
- aspect_id BIGINT NOT NULL
- hit_keywords_json JSON NOT NULL
- sentiment_label VARCHAR(8) NOT NULL         # POS/NEU/NEG
- sentiment_score DOUBLE NOT NULL DEFAULT 0.0 # [-1,1]
- confidence DOUBLE NOT NULL DEFAULT 0.0      # [0,1]
- created_at DATETIME NOT NULL

索引：
- idx_rar_review(review_id)
- idx_rar_aspect(aspect_id)
- idx_rar_aspect_sent(aspect_id, sentiment_label)

外键（可选，但推荐）：
- review_aspect_result.review_id -> review.id
- review_aspect_result.aspect_id -> aspect.id

### 3.6 topic_result（LDA 主题结果）
- id BIGINT PK AUTO_INCREMENT
- product_id BIGINT NOT NULL
- start_date DATE NULL
- end_date DATE NULL
- topic_count INT NOT NULL
- topics_json JSON NOT NULL
- created_at DATETIME NOT NULL

topics_json 示例：
[
  {"topicId":0,"topWords":["降噪","通话","风噪"],"weight":0.22,"evidenceReviewIds":[1,2,3]},
  {"topicId":1,"topWords":["续航","充电","掉电"],"weight":0.18,"evidenceReviewIds":[4,5,6]}
]

索引：
- idx_topic_product_time(product_id, start_date, end_date)

### 3.7 cluster（聚类簇结果）
- id BIGINT PK AUTO_INCREMENT
- product_id BIGINT NOT NULL
- start_date DATE NULL
- end_date DATE NULL
- k INT NOT NULL
- top_terms_json JSON NOT NULL
- size INT NOT NULL
- neg_rate DOUBLE NOT NULL DEFAULT 0.0
- created_at DATETIME NOT NULL

索引：
- idx_cluster_product_time(product_id, start_date, end_date)

### 3.8 review_cluster（评论与簇映射）
- id BIGINT PK AUTO_INCREMENT
- review_id BIGINT NOT NULL
- cluster_id BIGINT NOT NULL
- created_at DATETIME NOT NULL

索引：
- idx_rc_review(review_id)
- idx_rc_cluster(cluster_id)

### 3.9 alert（趋势预警）
- id BIGINT PK AUTO_INCREMENT
- product_id BIGINT NOT NULL
- metric VARCHAR(32) NOT NULL
- aspect_id BIGINT NULL
- window_start DATE NOT NULL
- window_end DATE NOT NULL
- current_value DOUBLE NOT NULL
- prev_value DOUBLE NOT NULL
- threshold DOUBLE NOT NULL
- status VARCHAR(16) NOT NULL DEFAULT 'new'    # new/ack
- created_at DATETIME NOT NULL

索引：
- idx_alert_product_status(product_id, status)
- idx_alert_product_window(product_id, window_start, window_end)

### 3.10 suggestion_template（建议模板）
- id BIGINT PK AUTO_INCREMENT
- match_type VARCHAR(16) NOT NULL              # aspect/cluster/keyword
- match_value VARCHAR(128) NOT NULL
- suggestion_text TEXT NOT NULL
- created_at DATETIME NOT NULL

索引：
- idx_st_match(match_type, match_value)

### 3.11 suggestion_instance（建议实例）
- id BIGINT PK AUTO_INCREMENT
- product_id BIGINT NOT NULL
- ref_type VARCHAR(16) NOT NULL                # aspect/cluster
- ref_id BIGINT NOT NULL
- suggestion_text TEXT NOT NULL
- evidence_json JSON NOT NULL
- created_at DATETIME NOT NULL

evidence_json 示例：
[
  {"reviewId":123,"snippet":"续航太差，掉电很快...","reason":"聚类代表评论"},
  {"reviewId":456,"snippet":"充电充不进...","reason":"匹配模板关键词"}
]

索引：
- idx_si_product_time(product_id, created_at)

### 3.12 event（活动/版本）
- id BIGINT PK AUTO_INCREMENT
- product_id BIGINT NOT NULL
- name VARCHAR(128) NOT NULL
- type VARCHAR(16) NOT NULL                    # activity/version
- start_date DATE NOT NULL
- end_date DATE NOT NULL
- created_at DATETIME NOT NULL

索引：
- idx_event_product_time(product_id, start_date, end_date)

## 4. 导入格式（必须支持，字段固定）

### 4.1 CSV
文件编码：UTF-8
表头固定为：
platform_name,product_name,brand,model,rating,review_time,content,like_count,review_id_raw

字段规则：
- platform_name/product_name/content 必填
- review_time 允许为空；格式 yyyy-MM-dd HH:mm:ss
- rating/like_count 允许为空
- brand/model/review_id_raw 允许为空

### 4.2 Excel(xlsx)
- 默认读取第一个 sheet
- 表头与字段规则与 CSV 完全一致

### 4.3 JSON（数组对象）
JSON 结构：Array<Object>，字段与 CSV 表头一致（推荐使用 snake_case）。
示例：
[
  {
    "platform_name":"JD",
    "product_name":"XX蓝牙耳机",
    "brand":"XX",
    "model":"A1",
    "rating":2,
    "review_time":"2025-12-02 12:00:00",
    "content":"续航太差了，掉电快。",
    "like_count":10,
    "review_id_raw":"xxx"
  }
]

## 5. 清洗与去重（必须实现）
清洗规则：
1) 去 HTML 标签
2) 多空白归一化（连续空格/换行 -> 单空格）
3) 去除不可见字符
4) content_clean 不能为空；若清洗后为空则丢弃该条并计入 errors

去重规则：
hash = sha256(platform_name + '|' + product_name + '|' + content_clean + '|' + (review_time or ''))
- 若 hash 已存在：跳过插入，计入 skipped

## 6. 词典与样例文件（必须提供并可加载）
/data/aspects.json：初始化 aspect 表
/data/sentiment_lexicon.json：情感词典（后端启动时加载到内存）
/data/stopwords.txt：停用词（分词/关键词过滤）
/data/sample_reviews.csv：导入验收样例
/data/crawl_samples/{platformName}/*.(json|html)：模拟爬取样例

## 7. 数据导入/爬取后的动作（强约束）
- 导入与模拟爬取完成后必须自动触发分析流水线（见 docs/Analysis_Algorithm_Spec.md）
- 分析完成后，/api/dashboard/overview、/api/analysis/topics、/api/analysis/clusters 等接口必须立即可用

