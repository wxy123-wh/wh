# 电商产品口碑监测与迭代优化辅助系统（蓝牙耳机）— 需求范围（MVP + 摘要补齐扩展）

## 1. 目标（必须实现）
系统把“电商评论”自动转成“维度化口碑指标 + 优先级问题列表 + 可视化仪表盘”，用于支持产品迭代决策。

### 1.1 基础能力（现有系统能力）
1) 支持评论数据导入（批量入库、去重、清洗）；
2) 自动做维度归因（音质/续航/降噪/连接/佩戴/麦克风/外观/性价比）；
3) 自动做情感分析（POS/NEU/NEG + score）；
4) 自动做关键词 TopN（支持按维度统计）；
5) 自动输出“问题优先级 Top 列表”（维度级 + 关键词级）；
6) 基础看板可用：总览、维度分析、评论检索/详情；
7) 导入后自动触发分析流水线，分析结果可被前端查询展示。

### 1.2 摘要补齐扩展（必须实现，见 docs/Extend_To_Abstract.md）
- 多平台获取（模拟爬取）+ Excel/JSON 导入
- 分词（jieba）、主题建模（LDA）
- 问题聚类（TF-IDF + KMeans）
- 竞品对比
- 趋势预警
- 建议模板输出
- 角色化展示（PM/市场/运营）
- 活动/版本前后对比（闭环验证）

## 2. 范围与原则（强约束）
1) 允许“模拟爬取”：从本地样例文件读取并解析入库，不做真实反爬登录。
2) NLP 算法允许通过独立服务实现（推荐 Docker 内 Python FastAPI：jieba/gensim/sklearn）。
3) 所有新功能必须有 API + 前端页面入口 + 验收用例（见 docs/Acceptance_Criteria_TestCases.md）。
4) 所有输出必须可解释：可追溯到评论证据（reviewId + 内容摘要）。

## 3. 用户与核心使用流程（必须跑通）
角色：PM / MARKET / OPS

流程：
1) 登录获取 token + role；
2) 通过导入（CSV/XLSX/JSON）或模拟爬取写入评论；
3) 自动/手动触发分析，生成主题/聚类/预警/建议等结果；
4) 进入角色看板：
   - PM：聚类结果 + 优先级 + 改进前后趋势 + 建议；
   - MARKET：正向关键词 + 主题分布 + 竞品对比；
   - OPS：趋势 + 预警 + 活动前后对比；
5) 在聚类/预警/建议等页面可下钻查看证据评论。

## 4. 模块拆分（必须按模块实现）
A. 数据接入（Ingestion）
- 导入：CSV/XLSX/JSON
- 模拟爬取：读取 data/crawl_samples/{platformName} 样例解析入库
- 去重、校验、入库

B. 清洗与基础分析（Processing + Baseline Analysis）
- 清洗（去 HTML/规范空白/过滤噪声）
- 维度归因（词典命中）
- 情感分析（情感词典 + 否定词规则）
- 关键词统计（频次 + NEG 频次）

C. NLP 扩展分析（NLP Extension）
- 分词（jieba）+ 停用词过滤
- 主题建模（LDA）
- 问题聚类（TF-IDF + KMeans）

D. 决策与对比（Decision + Compare）
- 问题优先级
- 竞品对比（维度对比 + 差值 + 归一化）
- 改进建议（模板匹配 + evidence）

E. 预警与闭环（Alert + Evaluation）
- 趋势预警（alert + ack）
- 活动/版本事件管理（event）
- before/after 对比评估（闭环验证）

F. 展示（UI）
- 登录页 + 角色化菜单
- PM/MARKET/OPS 三类看板
- topics/clusters/compare/alerts/suggestions/before-after 页面入口

## 5. 技术栈（建议）
- 后端：Spring Boot（REST API）+ MySQL 8.x
- 前端：Vue3 + Element Plus + ECharts
- NLP：可选独立 Python FastAPI 服务（jieba/gensim/sklearn），由后端调用并持久化结果

## 6. 项目结构（强约束）
/docs  需求与规格
/data  词典与样例数据（aspects.json、sentiment_lexicon.json、stopwords.txt、sample_reviews.csv、crawl_samples/...）
/backend Spring Boot 工程
/frontend Vue 工程
/infra  部署与 compose（如有）

## 7. 交付验收（必须）
- 后端：可启动、建表脚本可执行、导入/爬取可用、API 全部可用
- 前端：登录可用，三角色菜单与页面可用且能展示真实数据
- docs/Acceptance_Criteria_TestCases.md 的验收用例全部通过

