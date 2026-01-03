# 分析引擎算法规范（强约束，可解释、可复现）

## 0. 原则（必须）
1) 输出必须可追溯到评论证据（reviewId + 内容摘要）。
2) NLP 算法允许通过独立服务实现（推荐 Docker 内 Python FastAPI：jieba/gensim/sklearn），后端负责落库与对外提供 API。
3) 需要可复现：随机过程（如 LDA/KMeans）必须固定 random_state/seed。

## 1. 输入/输出
输入：review 表中新插入或指定 productId + 时间窗口的评论（content_clean）
输出：
1) review.overall_sentiment_label/score
2) review_aspect_result（每条评论可对应多个维度结果）
3) review.tokens_json（分词结果，去停用词）
4) topic_result（LDA 主题）
5) cluster + review_cluster（TF-IDF + KMeans 聚类）
6) alert（趋势预警）
7) suggestion_instance（建议实例，含 evidence）
说明：
- 基础统计接口（overview/aspects/trend/keywords/priority）可通过 SQL/聚合计算得到。
- topics/clusters/alerts/suggestions 等扩展能力需要持久化结果表（见 docs/Data_Model_Import.md）。

## 2. 清洗（必须）
见 docs/Data_Model_Import.md：去 HTML、空白归一化、去不可见字符

## 3. 维度归因（必须：词典命中，多标签）
数据来源：aspect.keywords_json
- 对每条评论 content_clean：
  - 对每个维度 aspect：
    - 若命中该维度任一关键词（简单 contains 或分词后匹配均可）=> 该评论属于该维度
  - 命中关键词记录到 hit_keywords_json（数组）
  - 允许一条评论命中多个维度（多标签）

confidence 计算（必须）：
- sumWeight = 命中关键词 weight 之和（未设置 weight 则为 1）
- confidence = min(1.0, sumWeight / 5.0)

## 4. 情感词典与否定规则（必须）
词典来源：/data/sentiment_lexicon.json（启动加载到内存）
结构：
{
  "pos": ["好","不错","满意","清晰","稳定","舒服","值得","给力","优秀"],
  "neg": ["差","垃圾","失望","掉电快","断连","刺耳","漏音","疼","电流声","延迟","充不进"],
  "negation": ["不","没","无","不是","并不","不太","不够"]
}

情感计数规则（必须）：
- posCount = 命中 pos 词数量
- negCount = 命中 neg 词数量
- 否定处理：若出现 negation + posWord（间距<=2个字符或同一句内相邻）则 posCount-- 且 negCount++
（实现允许简化，但必须有“否定把正向翻成负向”的效果）

整体情感（必须写回 review 表）：
- if negCount - posCount >= 1 => overall NEG
- else if posCount - negCount >= 1 => overall POS
- else => overall NEU

score（必须写回）：
score = (posCount - negCount) / (posCount + negCount + 1)
范围 [-1,1]

维度情感（必须写入 review_aspect_result）：
- 对该维度命中的关键词上下文做同样的情感计数（允许直接复用整条评论计数，MVP 可复用整体计数）
- sentiment_label 与 sentiment_score 输出到 review_aspect_result

## 5. 分词（jieba）（必须）
- 对 review.content_clean 进行分词，输出 tokens（中文分词以 jieba 为准）。
- 过滤 /data/stopwords.txt 与空白/纯标点 token。
- 存储：写入 review.tokens_json（数组）。
要求：
- tokens 必须可用于后续关键词统计、LDA、TF-IDF。
- 分词与停用词过滤规则必须稳定可复现。

## 6. 关键词统计（必须）
- 关键词来源：优先使用 review.tokens_json（若不存在则退回到简单 n-gram），过滤 stopwords.txt
- 统计维度：
  a) 全局关键词（aspectId 为空）
  b) 按维度关键词（aspectId 指定）
- 每个关键词统计：
  - freq：出现次数
  - negFreq：在 NEG（整体或该维度 NEG）评论中出现次数

排序规则（必须）：
- keywords 接口返回 items 按 negFreq desc，再按 freq desc

## 7. 指标与趋势（必须）
- posRate/neuRate/negRate 基于 review.overall_sentiment_label
- trend 按天聚合（date=review_time 的日期；review_time 为空则按 created_at 日期）

trend 每天输出：
- count
- negRate
- pos/neu/neg 计数（analysis/trend 需要）

## 8. 优先级（必须）
周期：按天（用 start/end 形成“当前窗口”，并取其前一段等长窗口作为“上一周期”）

定义：
- NegRate：当前窗口内（维度或关键词）的负向率
- Volume：当前窗口内样本量（评论数或关键词出现数）
- Growth：当前窗口 NegRate / 上一周期 NegRate（若上一周期为 0，则 Growth=1.0）

公式（必须）：
Priority = NegRate * Growth * ln(1 + Volume)

输出：
- 维度级：对每个 aspect 计算 priority
- 关键词级：对每个 aspect 的 top 关键词计算 priority
- 返回 topN

要求：
- priority 必须可比较、可复现
- 返回时必须带上 negRate/growth/volume 便于解释

## 9. LDA 主题建模（必须）
输入：指定 productId + 时间窗口内的 tokens_json
输出：写入 topic_result.topics_json，并通过 GET /api/analysis/topics 返回
要求：
- topic_count 可配置（默认 6），且必须 >0
- topics_json 每个 topic 至少包含 topWords + weight
- 可追溯：每个 topic 至少提供若干 evidenceReviewIds（该 topic 概率最高的评论）

weight 建议定义（可复现）：
- weight = 时间窗口内各评论 topic 概率的均值（或总和后归一化）

## 10. 问题聚类（TF-IDF + KMeans）（必须）
输入：指定 productId + 时间窗口内的 tokens_json（拼接为文本或自定义 analyzer）
流程：
1) TF-IDF 向量化
2) KMeans 聚类（固定 random_state）
输出：
- cluster.top_terms_json：每簇 top terms
- cluster.size：簇内评论数量
- cluster.neg_rate：簇内 NEG 占比
- review_cluster：评论与簇的映射

代表评论（必须）：
- clusters 列表必须包含 representativeReviewIds
- cluster/{id} 至少返回 5 条代表评论（可用“距离质心最近”的方式选取）

## 11. 竞品对比（必须）
输入：productId + competitorId + 时间窗口
输出：GET /api/compare/aspects 返回 8 个维度的 posRate/neuRate/negRate + 差值（本品-竞品）+ 归一化值
归一化（必须二选一）：
- minmax：在对比维度内做 min-max 归一化
- z-score：在对比维度内做标准化

## 12. 趋势预警（必须）
规则：
- negRate 在最近窗口相比上一窗口上涨超过阈值 => 产生 alert

窗口建议：
- 以 start/end 为当前窗口；若不传 start/end，可默认最近 7 天为当前窗口，并取其前一段等长窗口为上一窗口。

要求：
- alert 必须可 ack（status: new -> ack）
- 去重：同 product + metric + aspect_id + window_start + window_end 不重复插入

## 13. 改进建议（必须）
数据来源：
- suggestion_template（人工维护模板）
- cluster / aspects / keywords 的统计结果

生成规则（最小实现）：
1) 选择 negRate 高、volume 高的 aspect 或 cluster 作为候选；
2) 按 match_type/match_value 匹配模板，生成 suggestion_instance；
3) evidence_json 必须包含 reviewId + snippet，并说明证据来源（如“聚类代表评论/命中关键词”）。

## 14. 活动/版本前后对比（闭环验证，必须）
输入：eventId（event.start_date/end_date）
输出：GET /api/evaluate/before-after 返回 before vs after：
- reviewCount/negRate
- 各维度 negRate
- 关键词变化（before/after 频次差异）

before/after 窗口建议：
- after：event.start_date ~ event.end_date
- before：取 event.start_date 前等长窗口

