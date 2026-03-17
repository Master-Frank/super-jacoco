## ADDED Requirements

### Requirement: 覆盖集合必须支持同 commit 多次 run 累积
系统 MUST 将同一 `coverage_set` 下、同一 `commit_id` 的多次 run 覆盖做并集累积（covered OR covered），并确保覆盖率随新增测试单调不下降（排除代码变更场景）。

#### Scenario: 同 commit 追加 run
- **WHEN** 同一个 `coverage_set` 连续提交多个 `coverage_run` 且 `commit_id` 相同
- **THEN** 系统将新 run 覆盖与当前快照合并并更新累计覆盖结果

#### Scenario: 累积后覆盖提升可解释
- **WHEN** 新 run 补充了此前未覆盖的行
- **THEN** 新累计结果中的对应行由 `MISSED` 变为 `COVERED`，并可追溯到 run

### Requirement: commit 变化时必须按源码语义继承覆盖
系统 MUST 在 `commit_id` 变化时执行基于 Git diff 的行级继承，仅继承未变更代码行覆盖，变更/新增行清零，删除行移除，避免旧覆盖误算到新代码。

#### Scenario: 未变更行继承
- **WHEN** 新 commit 与旧 commit 对比后某行被识别为 unchanged
- **THEN** 系统继承该行历史覆盖状态

#### Scenario: 变更行清零
- **WHEN** 某行在 diff 中被识别为 modified 或 added
- **THEN** 系统将该行标记为未覆盖，等待新 run 覆盖

#### Scenario: 删除行移除
- **WHEN** 某行在 diff 中被识别为 deleted
- **THEN** 系统不再保留该行覆盖记录

### Requirement: 行移动匹配必须支持 hash 二次校验
系统 SHALL 为行级快照记录 `line_hash`，并在行号变化时通过 hash 二次匹配识别“位置变化但内容未变”，以提高继承准确性。

#### Scenario: 行号变化但内容相同
- **WHEN** 旧行与新行行号不同但 `line_hash` 相同
- **THEN** 系统将其视为可继承行并保留覆盖状态

### Requirement: 覆盖快照与聚合统计必须采用混合存储
系统 MUST 将行级覆盖快照存储在 OSS（JSON.gz），并将集合元数据与节点聚合统计存储在数据库；系统 MUST NOT 将全量行级明细直接入库。

#### Scenario: 写入 run 后生成快照
- **WHEN** run 进入处理流程并成功解析 `jacoco.xml`
- **THEN** 系统在 OSS 写入对应快照文件并更新 `coverage_set` 当前快照指针

#### Scenario: 列表查询不依赖快照全量加载
- **WHEN** 前端查询 Package/Class/Method 节点列表
- **THEN** 系统从数据库聚合表返回结果，无需下载整份快照

#### Scenario: 快照与 run 生命周期管理
- **WHEN** 快照与 run 产物持续增长
- **THEN** 系统对 `runs/` 执行保留期策略（默认 30 天），并仅保留最新 commit 的累计快照

### Requirement: 必须提供集合级写入与读取 API
系统 MUST 提供覆盖集合创建、run 追加、概览查询、节点查询与源码行级覆盖查询能力，以支持前端按需展示。

#### Scenario: 追加 run 触发异步处理
- **WHEN** 客户端调用追加 run 接口并提交 commit 与产物信息
- **THEN** 系统记录 `PENDING` 状态并异步推进解析、继承、合并与聚合流程

#### Scenario: 源码详情按需加载
- **WHEN** 客户端查询某类源码覆盖详情
- **THEN** 系统按需读取快照并返回该文件行级覆盖与摘要

#### Scenario: API 路径与参数约定
- **WHEN** 客户端调用覆盖率 API
- **THEN** 系统提供以下稳定接口：`POST /api/v1/coverage/sets`、`POST /api/v1/coverage/sets/{setId}/runs`、`GET /api/v1/coverage/sets/{setId}`、`GET /api/v1/coverage/sets/{setId}/nodes`、`GET /api/v1/coverage/sets/{setId}/source`

### Requirement: 处理失败必须可观测且不可误报成功
系统 MUST 在解析失败、diff 失败、快照合并失败或持久化失败时标记 run 为 `FAILED` 并记录错误信息；系统 MUST NOT 返回伪成功覆盖结果。

#### Scenario: diff 执行失败
- **WHEN** commit 变化且 Git diff 命令失败
- **THEN** run 状态更新为 `FAILED`，并记录可定位的错误原因

### Requirement: 首次 commit 必须从零初始化累计快照
系统 MUST 在 `coverage_set` 首次接收 run 且不存在历史快照时，直接以当前 run 快照初始化累计结果，不执行继承逻辑。

#### Scenario: 新建 set 首次写入
- **WHEN** `coverage_set.current_commit_id` 与 `current_snapshot_key` 为空
- **THEN** 系统直接将当前 run 快照作为累计快照并更新 set 指针

### Requirement: commit 变化时必须依赖有效本地仓库路径
系统 MUST 在 commit 变化场景使用 `coverage_set.repo_local_path` 执行 Git diff；当路径缺失或非法时 MUST 失败，不得降级为全量继承。

#### Scenario: commit 变化但仓库路径缺失或非法
- **WHEN** `commit_id` 变化且 `coverage_set.repo_local_path` 为空或不是有效 git 仓库
- **THEN** run 状态 MUST 更新为 `FAILED`，并返回明确错误信息

### Requirement: source 接口应支持条件缓存
系统 SHALL 为 `GET /api/v1/coverage/sets/{setId}/source` 提供 ETag 条件请求能力，以降低重复下载。

#### Scenario: ETag 命中返回 304
- **WHEN** 客户端携带 `If-None-Match` 且与当前 ETag 一致
- **THEN** 服务端返回 `304 Not Modified`
