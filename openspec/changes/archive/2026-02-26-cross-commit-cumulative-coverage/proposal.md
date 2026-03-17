## Why

当前覆盖率结果按单次任务产出，无法持续累积多次测试覆盖；代码发布后也缺少可靠的覆盖失效与继承机制，容易出现旧覆盖误算到新代码。需要建立一套可跨 run、跨 commit 演进且可追溯的覆盖率模型。

## What Changes

- 引入 `coverage_set` / `coverage_run` 语义，支持同一统计口径下多次 run 累积覆盖。
- 将覆盖结果从 `jacoco.xml` 解析为源码级快照（按文件/行），作为跨 commit 继承基础。
- 在 commit 变化时基于 `git diff --find-renames --unified=0` 进行行级继承：未变更行继承，变更/新增行清零，删除行丢弃。
- 引入 `line_hash` 辅助移动行匹配，降低重排导致的误判。
- 使用混合存储：OSS 保存 run 产物与快照文件，数据库保存元数据与聚合统计（不存行级明细）。
- 增加查询 API：集合概览、节点统计（Package/Class/Method）、源码行级覆盖详情。
- 明确生命周期：`runs/` 默认保留 30 天；`snapshots/` 仅保留最新 commit 快照（覆盖写入）。
- 增加异步处理链路与状态可观测性，确保 run 可追踪、失败可定位。

## Capabilities

### New Capabilities
- `cross-commit-cumulative-coverage`: 支持覆盖率在同一集合内跨 run 累积、跨 commit 差异继承与查询展示。

### Modified Capabilities
- `method-level-diff-coverage-report`: 覆盖产物从一次性报告扩展为可被快照生成与聚合流程复用的稳定输入。

## Impact

- 受影响代码：覆盖采集、报告生成、异步任务、数据持久化与查询接口相关服务。
- 数据结构变更：新增 `coverage_set`、`coverage_run`、`coverage_node_stats`、`report_artifact`（及可选 `coverage_campaign`）。
- 存储变更：新增 OSS 快照目录规范与生命周期策略。
- API 变更：新增 `/api/v1/coverage/sets`、`/api/v1/coverage/sets/{setId}/runs`、`/api/v1/coverage/sets/{setId}`、`/api/v1/coverage/sets/{setId}/nodes`、`/api/v1/coverage/sets/{setId}/source`。
- 运维影响：需要 OSS 路径管理、异步任务监控、缓存与失败重试策略。
