# Super-Jacoco

Super-Jacoco 是基于 JaCoCo + Git 二次开发的一站式 Java 代码覆盖率收集平台，支持全量/增量覆盖率，并提供可视化 HTML 报告。

## 功能特性

- 覆盖率采集
  - Unit：单元测试覆盖率（本地跑测试，生成覆盖率报告）
  - Env：环境覆盖率（对运行中 JVM 通过 JaCoCo tcpserver dump exec）
  - Local：本机覆盖率（覆盖率服务与被测代码在同机，直接读取源码/class 结合 dump exec）
- 口径与维度
  - 全量覆盖率（Full）
  - 增量覆盖率（Diff）
  - 跨 commit 累计覆盖率（Coverage Set / Runs：多次 run 叠加，run 可跨 commit）
- 报告输出
  - 传统报告：HTML（/cov 接口触发后生成）
  - 累计报告：模板页 + JSON 数据
    - 数据落盘为 gzip：`cumulative/data/summary.json.gz`、`cumulative/data/classes/**/*.json.gz`
    - 服务端对 `.json.gz` 返回 `Content-Encoding: gzip`
- 静态资源访问
  - 默认将 `${user.dir}/report/` 作为静态资源根目录，对外以 `http://<host>:8899/<reportPath>` 访问

## 环境要求

- JDK 17
- Maven 3
- Git
- MySQL（生产/长期使用）或 H2（本地快速验证）

## 克隆项目（含被测服务子模块）

另一台机器请使用以下命令拉取，确保 `sample-tested-service` 的 Git 历史一并拉下，才能直接做跨 commit 累计覆盖率验证：

```bash
git clone https://github.com/Master-Frank/super-jacoco
cd super-jacoco
git submodule update --init --recursive
```

## 快速开始（本地 H2，推荐用于验证）

### 1）编译打包

```bash
mvn -DskipTests package
```

产物默认在 `target/super-jacoco.jar`。

### 2）启动服务（H2 内存库）

Windows PowerShell：

```powershell
java -jar target\super-jacoco.jar `
  --spring.datasource.driver-class-name=org.h2.Driver `
  --spring.datasource.url="jdbc:h2:mem:superjacoco;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'file:D:/project/super-jacoco/sql/coverage_report_h2.sql'\;RUNSCRIPT FROM 'file:D:/project/super-jacoco/sql/cumulative_coverage_h2.sql'" `
  --spring.datasource.username=sa `
  --spring.datasource.password=
```

注意：上面的 `file:D:/project/super-jacoco/...` 需要替换成你本机的仓库绝对路径。
启动成功后访问：

- 服务端口：`http://127.0.0.1:8899`
- 报告静态资源根：`http://127.0.0.1:8899/<setId>/cumulative/index.html`（累计报告示例）

## 数据库初始化（MySQL）

创建数据库后执行：

- `sql/coverage_report.sql`（传统覆盖率任务表）
- `sql/cumulative_coverage.sql`（累计覆盖率 set/run 表）

对应数据源配置在 `src/main/resources/application.properties`：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

## 配置说明

常用配置在 `src/main/resources/application.properties`：

- 服务端口：`server.port`（默认 8899）
- 报告根目录：`cov.paths.reportRoot=${user.dir}/report/`
- 静态资源目录：`spring.web.resources.static-locations=file:${user.dir}/report`
- 安全开关：`cov.security.enabled`（开启后按 `cov.security.header/cov.security.token` 传鉴权）

## 覆盖率接口

### 1）传统覆盖率任务接口（/cov）

#### 触发 Unit 覆盖率

- URL：`POST /cov/triggerUnitCover`
- 入参：`UnitCoverRequest`（JSON）
- 查结果：`GET /cov/getUnitCoverResult?uuid=<uuid>`

#### 触发 Env 覆盖率

- URL：`POST /cov/triggerEnvCov`
- 入参：`EnvCoverRequest`（JSON，包含 JaCoCo tcpserver 的 `address/port`）
- 查结果：`GET /cov/getEnvCoverResult?uuid=<uuid>`

#### Local 覆盖率

- URL：`POST /cov/getLocalCoverResult`

### 2）跨 commit 累计覆盖率接口（/api/v1/coverage/sets）

> 核心概念：一个 set 对应一个累计覆盖率视图；每次 append run 会把该 run 的 jacoco.xml.gz 融合进 set 的累计结果，run 可跨 commit。

#### 创建 set

- URL：`POST /api/v1/coverage/sets`

#### 追加 run

- URL：`POST /api/v1/coverage/sets/{setId}/runs`
- 入参要点：
  - `commitId`：该 run 对应的 commit
  - `xmlObjectKey`：指向 `jacoco.xml.gz` 的本地绝对路径（默认使用本地文件存储）

#### 查询 set / run

- `GET /api/v1/coverage/sets/{setId}`
- `GET /api/v1/coverage/sets/{setId}/runs/{runId}`

#### 查询节点列表 / 源码

- `GET /api/v1/coverage/sets/{setId}/nodes`：按层级查询节点（package/class/method 等）
- `GET /api/v1/coverage/sets/{setId}/source?class_key=<classKey>`：查询类源码与覆盖详情（支持 ETag）

#### 刷新 set（重新计算）

- `POST /api/v1/coverage/sets/{setId}/refresh`

#### 报告与数据落盘结构

默认报告根目录（可配置）：`cov.paths.reportRoot=${user.dir}/report/`

累计报告结构示例：

```
report/<setId>/
  cumulative/
    index.html
    package.html
    class.html
    data/
      summary.json.gz
      classes/**/<ClassKey>.json.gz
  runs/<runId>/jacoco.xml.gz
  snapshots/<commitId>/coverage_snapshot.json.gz
```

## 常见问题

- `Port 8899 was already in use`：复用已启动服务或更换 `--server.port`
- Windows 下重新打包 jar 失败：先停掉占用 jar 的 Java 进程
- Env/Local 覆盖率结果为空：先对被测服务发真实请求，确保 `jacoco.exec` 有执行轨迹
- `No tests were executed`：旧 commit 可能不存在指定测试方法，可加 `-DfailIfNoTests=false` 并替换为存在的测试用例
