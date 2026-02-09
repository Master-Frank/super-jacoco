# sample-tested-service 接口说明

该示例服务是一个独立的 Spring Boot 工程，位于仓库根目录下的 `sample-tested-service` 目录，用于验证 super-jacoco 的覆盖率收集能力。

启动方式示例：

```bash
cd sample-tested-service
mvn -DskipTests package
java -jar target/sample-tested-service-1.0.0-SNAPSHOT.jar
```

服务默认监听端口为 `18080`。

## super-jacoco 两种使用模式

super-jacoco 计算覆盖率主要有两种使用方式：

1）本地模式（本机路径模式）：`POST /cov/getLocalCoverResult`

- 适用场景：覆盖率服务（super-jacoco）与源码/编译产物在同一台机器上，你可以直接把源码目录、class 目录告诉它。
- 输入参数特点：需要显式提供 `basePath` / `nowPath` / `classFilePath`。
- 代码来源：不通过服务端“克隆+编译”链路，直接读取你给的本机路径。
- 任务模型：同步计算（接口返回结果里直接带 reportUrl/覆盖率；如果失败会直接返回错误信息）。

2）非本机路径模式（克隆编译模式 / 环境覆盖链路）：`POST /cov/triggerEnvCov` + `GET /cov/getEnvCoverResult`

- 适用场景：覆盖率服务所在机器没有现成源码/产物，需要由 super-jacoco 自己根据 `gitUrl` 拉代码并编译；或者你想用异步任务+轮询方式拿结果。
- 输入参数特点：不需要 `basePath` / `nowPath` / `classFilePath`，只需要提供 `gitUrl` + 版本信息（`baseVersion`/`nowVersion`）+ 目标服务的 JaCoCo agent 地址（`address`/`port`）。
- 代码来源：服务端拉取到 `cov.paths.codeRoot` 目录（默认 `${user.home}/app/super_jacoco/clonecode/`），并在该目录下执行 `mvn clean compile`。
- 任务模型：异步计算（先触发任务入库，后续轮询结果接口获取状态与 reportUrl；环境覆盖拉取 exec 与生成报告由后台调度执行）。

两种模式的共同点：目标服务都需要以 JaCoCo Agent 的 tcpserver 模式启动（见下文“带 JaCoCo Agent 启动”），覆盖率服务会通过 `jacoco-cli dump` 去拉取执行轨迹（jacoco.exec）。

### 覆盖率报告路径逻辑与查看方式

无论是本地模式还是非本机路径模式，最终对外可访问的报告都会被归档到 super-jacoco 的“报告根目录”下，并通过静态资源方式暴露。

1）对外 URL 生成规则

- 报告入口：`<baseUrl>/<uuid>/index.html`
- 任务日志（非本机路径模式必有）：`<baseUrl>/logs/<uuid>.log`

其中 `<baseUrl>` 来自服务端逻辑：

- Windows：固定返回 `http://127.0.0.1:8899/`
- Linux：返回 `http://<本机网卡IP>:8899/`（取不到时回退到 127.0.0.1）

2）报告文件落盘路径（服务端）

- 报告归档根目录：`cov.paths.reportRoot`（本仓库默认是 `${user.dir}/report/`）
- 因此某次任务的归档目录一般是：`${cov.paths.reportRoot}/<uuid>/`

为什么能直接通过 HTTP 访问：

- Spring Boot 静态资源目录配置为 `file:${user.dir}/report`
- 并且静态路径匹配为 `/**`

3）本地模式（/cov/getLocalCoverResult）的报告路径逻辑

- 报告生成：在你提供的 `nowPath` 下生成 `./jacocoreport/index.html`
- 报告归档：生成成功后，会把 `nowPath/jacocoreport` 复制到 `${cov.paths.reportRoot}/<uuid>/`
- 返回地址：接口返回的 `data.reportUrl` 会被改写为：`<baseUrl>/<uuid>/index.html`

查看方式：

- 直接看接口返回的 `data.reportUrl`
- 或者你已知 uuid 时，直接打开：`http://localhost:8899/<uuid>/index.html`

4）非本机路径模式（/cov/triggerEnvCov）的报告路径逻辑

- 代码目录：服务端会把代码拉到 `cov.paths.codeRoot/<uuid>/<nowVersion>/`（并在该目录执行 `mvn clean compile`）
- 报告生成：拉取 `jacoco.exec` 后，在代码目录下生成 `jacocoreport/index.html`
- 报告归档：生成成功后，会把代码目录里的 `jacocoreport` 复制到 `${cov.paths.reportRoot}/<uuid>/`
- 记录与返回：成功时会把 `report_url` 写入 MySQL（表 `diff_coverage_report`），并通过查询接口返回

查看方式：

- 轮询 `GET /cov/getEnvCoverResult?uuid=<uuid>`，成功时 `data.reportUrl` 即报告地址
- 失败/卡住时：打开 `data.logFile` 查看 clone/compile/dump/report 的执行日志


## 本地联调：配合 super-jacoco 生成覆盖率报告


### 1) 准备 MySQL（首次）

super-jacoco 默认需要 MySQL（本仓库已将默认连接设置为 `127.0.0.1:3306`，账号 `root` / 密码 `root`）。

初始化库表：

```bash
mysql -uroot -proot -h 127.0.0.1 -P 3306 -e 'CREATE DATABASE IF NOT EXISTS `super-jacoco` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
mysql -uroot -proot -h 127.0.0.1 -P 3306 super-jacoco < ./sql/coverage_report.sql
```

### 2) 启动 sample-tested-service（带 JaCoCo Agent）

先编译：

```bash
cd sample-tested-service
mvn -DskipTests package
```

再启动（开启 JaCoCo TCP Server，端口 18513）：

```bash
java -javaagent:d:\\project\\super-jacoco\\jacoco\\org.jacoco.agent-0.8.14-runtime.jar=includes=*,output=tcpserver,address=*,port=18513 -jar target/sample-tested-service-1.0.0-SNAPSHOT.jar
```

### 3) 启动 super-jacoco

```bash
cd ..
mvn -DskipTests package
java -jar target/super-jacoco.jar
```

默认端口：`8899`。

### 4) 发起真实请求产生执行轨迹

```bash
curl -i "http://localhost:18080/demo/ping"
curl -i "http://localhost:18080/demo/calc?op=add&a=1&b=2"
curl -i "http://localhost:18080/demo/calc?op=sub&a=5&b=3"
curl -i "http://localhost:18080/demo/calc?op=mul&a=4&b=6"
```

### 5) 调用 super-jacoco 生成报告

请求 `POST /cov/getLocalCoverResult`。

示例（PowerShell）：

```bash
$json='{
  "uuid":"<uuid>",
  "gitUrl":"https://github.com/jacoco/jacoco",
  "baseVersion":"v1",
  "nowVersion":"v1",
  "type":1,
  "address":"127.0.0.1",
  "port":18513,
  "subModule":"",
  "classFilePath":"d:\\project\\super-jacoco\\sample-tested-service\\target\\classes",
  "basePath":"d:\\project\\super-jacoco\\sample-tested-service",
  "nowPath":"d:\\project\\super-jacoco\\sample-tested-service"
}';
Invoke-RestMethod -Method Post -Uri 'http://localhost:8899/cov/getLocalCoverResult' -ContentType 'application/json' -Body $json
```

返回的 `data.reportUrl` 为 HTML 报告地址，例如：

```bash
http://localhost:8899/<uuid>/index.html
```

### 6) 非本机路径模式（triggerEnvCov）使用方式

该模式用于“覆盖率服务自行拉代码并编译，然后再从目标服务拉 exec 生成报告”。需要 MySQL 已初始化（步骤 1），并且 super-jacoco 能访问到 Maven（Windows 环境下通常是 `mvn.cmd` 在 PATH 中）。

#### 6.1 触发任务

请求 `POST /cov/triggerEnvCov`。

PowerShell 示例：

```bash
$json='{
  "uuid":"<uuid>",
  "gitUrl":"<git 仓库地址或本机代码路径>",
  "baseVersion":"<base 分支或 commitId>",
  "nowVersion":"<now 分支或 commitId>",
  "type":1,
  "address":"127.0.0.1",
  "port":18513,
  "subModule":""
}';
Invoke-RestMethod -Method Post -Uri 'http://localhost:8899/cov/triggerEnvCov' -ContentType 'application/json' -Body $json
```

参数说明：

- `type`：1=全量，2=增量。
- `address`/`port`：目标服务 JaCoCo TCP Server 地址（示例服务按本文默认是 127.0.0.1:18513）。
- `gitUrl`：
  - 如果是远端仓库：填 http/https/git@ 形式的仓库地址；
  - 如果是本机路径：可填 `d:\\path\\to\\repo` 或 `file:///...`。
  - 注意：当 `gitUrl` 为“普通目录（非 git 仓库）”时，仅支持 `type=1`（全量）；`type=2` 需要 git 仓库以便对比两版本。

#### 6.2 轮询任务结果

请求 `GET /cov/getEnvCoverResult?uuid=<uuid>`。

PowerShell 示例：

```bash
Invoke-RestMethod -Method Get -Uri 'http://localhost:8899/cov/getEnvCoverResult?uuid=<uuid>'
```

返回说明：

- `data.coverStatus`：`0` 进行中；`1` 成功；`-1` 失败。
- `data.reportUrl`：成功时为 HTML 报告入口 URL（形如 `http://localhost:8899/<uuid>/index.html`）。
- `data.logFile`：本次任务的服务端执行日志 URL（用于排查 clone/compile/dump/report 失败原因）。

## 接口列表

### 1. 健康检查接口

- 方法：GET
- 路径：`/demo/ping`
- 描述：用于快速验证示例服务是否启动成功。

示例 curl：

```bash
curl -i "http://localhost:18080/demo/ping"
```

期望返回：`200 OK`，响应体为：`pong`

### 2. 计算接口

- 方法：GET
- 路径：`/demo/calc`
- 描述：对两个整数执行简单运算，用于制造带分支逻辑的被测方法。
- 请求参数：
  - `op`：运算类型，支持 `add`、`sub`、`mul`
  - `a`：第一个整数
  - `b`：第二个整数

#### 2.1 加法示例

```bash
curl -i "http://localhost:18080/demo/calc?op=add&a=1&b=2"
```

期望返回：`200 OK`，响应体为：`3`

#### 2.2 减法示例

```bash
curl -i "http://localhost:18080/demo/calc?op=sub&a=5&b=3"
```

期望返回：`200 OK`，响应体为：`2`

#### 2.3 乘法示例

```bash
curl -i "http://localhost:18080/demo/calc?op=mul&a=4&b=6"
```

期望返回：`200 OK`，响应体为：`24`

#### 2.4 非法运算符示例

```bash
curl -i "http://localhost:18080/demo/calc?op=div&a=4&b=2"
```

期望：服务返回 4xx 或 5xx 错误，用于触发分支中的异常路径，从而在覆盖率报告中体现未命中或异常分支的覆盖情况。
