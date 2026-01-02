# 数据模型与 CSV 导入契约（强约束）

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
- created_at DATETIME NOT NULL

### 3.3 review（主表，必须含 overall 情感）
- id BIGINT PK AUTO_INCREMENT
- platform_id BIGINT NOT NULL
- product_id BIGINT NOT NULL
- review_id_raw VARCHAR(128) NULL
- rating INT NULL
- content_raw TEXT NOT NULL
- content_clean TEXT NOT NULL
- review_time DATETIME NULL
- like_count INT NULL
- hash VARCHAR(64) NOT NULL UNIQUE

- overall_sentiment_label VARCHAR(8) NOT NULL DEFAULT 'NEU'  # POS/NEU/NEG
- overall_sentiment_score DOUBLE NOT NULL DEFAULT 0.0       # [-1,1]

- created_at DATETIME NOT NULL

索引：
- idx_review_product_time(product_id, review_time)
- idx_review_platform(platform_id)
- idx_review_overall_sent(overall_sentiment_label)

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

## 4. CSV 导入格式（必须支持，字段固定）
文件编码：UTF-8
表头固定为：
platform_name,product_name,brand,model,rating,review_time,content,like_count,review_id_raw

字段规则：
- platform_name/product_name/content 必填
- review_time 允许为空；格式 yyyy-MM-dd HH:mm:ss
- rating/like_count 允许为空
- brand/model/review_id_raw 允许为空

## 5. 清洗与去重（必须实现）
清洗规则：
1) 去 HTML 标签
2) 多空白归一化（连续空格/换行 -> 单空格）
3) 去除不可见字符
4) content_clean 不能为空；若清洗后为空则丢弃该条并计入 errors

去重规则：
hash = sha256(platform_name + '|' + product_name + '|' + content_clean + '|' + (review_time or ''))
- 若 hash 已存在：跳过插入，计入 skipped

## 6. 词典文件（必须提供并可加载）
/data/aspects.json：初始化 aspect 表
/data/sentiment_lexicon.json：情感词典（后端启动时加载到内存）
/data/stopwords.txt：停用词（关键词统计过滤）

## 7. 数据导入后的动作（强约束）
- 导入完成后必须自动触发分析流水线（见 docs/03）
- 分析完成后，/api/dashboard/overview 等接口必须立即可用
