# 分析引擎算法规范（强约束，可解释、可复现）

## 1. 输入/输出
输入：review 表中新插入或指定 productId 的评论（content_clean）
输出：
1) review.overall_sentiment_label/score
2) review_aspect_result（每条评论可对应多个维度结果）
3) 所有统计接口通过 SQL/聚合计算得到（不额外建聚合表）

## 2. 清洗（必须）
见 docs/01：去 HTML、空白归一化、去不可见字符

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

## 5. 关键词统计（必须）
- 关键词来源：基于 content_clean 分词（或简单 n-gram），过滤 stopwords.txt
- 统计维度：
  a) 全局关键词（aspectId 为空）
  b) 按维度关键词（aspectId 指定）
- 每个关键词统计：
  - freq：出现次数
  - negFreq：在 NEG（整体或该维度 NEG）评论中出现次数

排序规则（必须）：
- keywords 接口返回 items 按 negFreq desc，再按 freq desc

## 6. 指标与趋势（必须）
- posRate/neuRate/negRate 基于 review.overall_sentiment_label
- trend 按天聚合（date=review_time 的日期；review_time 为空则按 created_at 日期）

trend 每天输出：
- count
- negRate
- pos/neu/neg 计数（analysis/trend 需要）

## 7. 优先级（必须）
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
