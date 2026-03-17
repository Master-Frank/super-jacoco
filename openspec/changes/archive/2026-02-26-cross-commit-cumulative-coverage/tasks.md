## 1. 数据模型与存储

- [x] 1.1 新增并落库 `coverage_set`、`coverage_run`、`coverage_node_stats`、`report_artifact`（可选 `coverage_campaign`）
- [x] 1.2 为 `coverage_set.scope_key` 建立唯一约束（`git_url + branch + type + from_type`）
- [x] 1.3 为 `coverage_run` 建立状态字段与索引（`PENDING/PROCESSING/COMPLETED/FAILED`，`set+commit`、`created_at`）
- [x] 1.4 定义并实现 OSS 目录规范与对象 key（`reports/{setId}/runs/{runId}`、`reports/{setId}/snapshots/{commitId}`）
- [x] 1.5 建立快照文件格式（`coverage_snapshot.json.gz`）及序列化/反序列化组件
- [x] 1.6 落地生命周期策略：`runs/` 保留 30 天；`snapshots/` 仅保留最新 commit 快照

## 2. 采集与快照生成

- [x] 2.1 接入 run 写入链路：记录 `commit_id`、产物 key、状态机并触发异步处理
- [x] 2.2 将 `jacoco.xml.gz` 解析为行级快照（文件/行/status/hits/hash）
- [x] 2.3 计算并写入 `line_hash`（行内容规范化后 hash）
- [x] 2.4 实现同 commit 多 run 的快照合并（`covered OR covered`，`hits=max`）
- [x] 2.5 实现首次 commit 初始化：无历史快照时直接以本次快照作为累计快照

## 3. 跨 commit 继承

- [x] 3.1 实现 Git diff 执行器（`git diff --find-renames --unified=0 <old> <new>`）
- [x] 3.2 实现 diff 解析器：文件重命名、hunk 区间、行级变化映射
- [x] 3.3 实现行级继承规则（unchanged 继承、modified/added 清零、deleted 丢弃）
- [x] 3.4 实现 `line_hash` 二次匹配，支持行号移动但内容不变的继承
- [x] 3.5 串联 commit 变化分支：旧快照下载 -> 继承映射 -> 与新快照合并 -> 回写新快照
- [x] 3.6 处理异常场景：diff 失败、旧快照缺失、commit 不可达时安全失败并记录错误

## 4. 聚合统计与查询 API

- [x] 4.1 实现快照聚合为 Package/Class/Method 统计并写入 `coverage_node_stats`
- [x] 4.2 提供集合管理接口：`POST /api/v1/coverage/sets`
- [x] 4.3 提供 run 追加接口：`POST /api/v1/coverage/sets/{setId}/runs`
- [x] 4.4 提供概览接口：`GET /api/v1/coverage/sets/{setId}`
- [x] 4.5 提供节点接口：`GET /api/v1/coverage/sets/{setId}/nodes`（支持 `level/parent_key/sort/page/page_size`）
- [x] 4.6 提供源码覆盖接口：`GET /api/v1/coverage/sets/{setId}/source?class_key=...`（仅覆盖数据，不返回源码文本）
- [x] 4.7 提供可选重算接口：`POST /api/v1/coverage/sets/{setId}/refresh`

## 5. 异步处理、缓存与可靠性

- [x] 5.1 完成异步编排：`PENDING -> PROCESSING -> COMPLETED|FAILED`
- [x] 5.2 失败收敛：解析失败/diff 失败/写入失败统一标记 `FAILED` 并落 `error_message`
- [x] 5.3 增加幂等与重试保护，避免重复 run 导致统计抖动
- [x] 5.4 增加缓存层：快照热点缓存（Redis 可选）与本地内存缓存
- [x] 5.5 增加 HTTP 缓存策略（`Cache-Control`/`ETag`）提升读取性能
- [x] 5.6 增加监控与告警：处理时延、失败率、覆盖率异常波动

## 6. 验证、回归与发布

- [x] 6.1 验证同 commit 多次 run 的累积正确性与可追溯性
- [x] 6.2 验证跨 commit 继承与变更行清零行为
- [x] 6.3 验证 rename/移动行场景下 `line_hash` 继承准确性
- [x] 6.4 验证 API 合同、分页排序和错误码一致性
- [x] 6.5 验证性能目标（列表查询、源码详情首访与缓存命中）
- [x] 6.6 补齐回归测试与灰度发布检查清单（回滚与数据修复预案）

