## Overview

本设计实现覆盖率“同 commit 可累积、跨 commit 可继承、代码变更后自动失效”。核心方法是将 JaCoCo 执行结果转为源码级快照，并在 commit 变化时用 Git diff 进行行级映射与继承，再与新 run 覆盖做并集。

## Architecture

### Core Domain

- `coverage_set`：覆盖集合，绑定 `git_url + branch + type + from_type`。
- `coverage_set.repo_local_path`：本地 git 仓库路径（commit 变化时执行 diff 的必需输入）。
- `coverage_run`：一次覆盖采集（携带 `commit_id`、产物 key、处理状态）。
- `coverage_snapshot`：行级覆盖快照（OSS JSON.gz，运行态加载）。
- `coverage_node_stats`：Package/Class/Method 聚合统计（数据库）。

### Processing Pipeline

1. run 写入：记录 `coverage_run(PENDING)`，保存 xml/exec 产物 key。
2. 异步解析：从 `jacoco.xml.gz` 生成当前 run 快照。
3. commit 决策：
   - 相同 commit：直接与当前快照做 OR 合并。
   - 不同 commit：下载旧累计快照，使用 `repo_local_path` 执行 diff 继承后再合并新快照。
   - 若 commit 变化但 `repo_local_path` 缺失或非法：任务失败并标记 `FAILED`。
   - 首次 commit（`coverage_set.current_commit_id` 为空）：直接以新快照初始化累计快照。
4. 结果持久化：上传新快照到 OSS，更新 `coverage_set` 指针与覆盖摘要。
5. 聚合更新：重建或增量更新 `coverage_node_stats`。

## Snapshot Model

### Key Fields

- `metadata`: `coverage_set_id`, `commit_id`, `git_url`, `branch`, `generated_at`
- `files[path].lines[line_no]`: `status`, `hits`, `hash`
- `summary`: 总行数、覆盖行数、分支统计

### Merge Rules

- 行状态：`COVERED` 优先于 `MISSED`。
- 命中次数：`hits = max(base.hits, delta.hits)`。
- 行不存在：直接插入。

## Diff Inheritance

### Input

- `old_commit`, `new_commit`
- `git diff --find-renames --unified=0`
- 旧快照（按文件/行）

### Rules

- unchanged：继承
- modified / added：清零（新覆盖决定）
- deleted：丢弃
- moved：使用 `line_hash` 二次匹配，命中则继承

## Storage Design

### OSS

- `reports/{setId}/runs/{runId}/jacoco.xml.gz`
- `reports/{setId}/runs/{runId}/jacoco.exec.gz`（可选）
- `reports/{setId}/snapshots/{commitId}/coverage_snapshot.json.gz`
- `reports/{setId}/snapshots/{commitId}/coverage_stats.json`（可选冗余）
- 生命周期：
  - `runs/` 保留 30 天（可归档）
  - `snapshots/` 仅保留最新 commit 快照

### Database

- `coverage_set`
- `coverage_run`
- `coverage_node_stats`
- `report_artifact`
- `coverage_campaign`（可选）
- 关键约束：
  - `coverage_set.scope_key` 唯一（`git_url + branch + type + from_type`）
  - `coverage_run.status` 使用 `PENDING/PROCESSING/COMPLETED/FAILED`
  - 行级快照不入库

## API Contract

- `POST /api/v1/coverage/sets`：创建覆盖集合（可附带 campaign）
- `POST /api/v1/coverage/sets/{setId}/runs`：追加 run 并触发异步处理
- `POST /api/v1/coverage/sets/{setId}/refresh`：手动重算（可选）
- `GET /api/v1/coverage/sets/{setId}`：集合概览
- `GET /api/v1/coverage/sets/{setId}/nodes`：节点统计（支持 level/parent_key/sort/page）
- `GET /api/v1/coverage/sets/{setId}/source?class_key=...`：源码行级覆盖（不返回源码文本）
- `GET /api/v1/coverage/sets/{setId}/source` 支持 ETag 协商（`If-None-Match` 命中返回 304）

## Error Handling and Observability

- run 状态机：`PENDING -> PROCESSING -> COMPLETED|FAILED`
- 失败记录：保存 `error_message`，保证接口不返回伪成功数据
- 指标监控：处理时延、失败率、覆盖率异常波动
- 前端交互：run 处理期间提供状态查询与轮询刷新

## Compatibility

- 保持现有报告访问路径与 URL 兼容
- 复用现有 XML 生成能力，不改变既有调用入口语义
- 新流程以异步方式叠加，不阻断原有单次出报链路
- 与 `method-level-diff-coverage-report` 产物互通：XML 可直接作为快照输入
