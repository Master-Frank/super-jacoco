# super-jacoco 启动与使用指南（Unit / Env / Local，全量 / 增量）

本文档面向本仓库的 `super-jacoco` 服务与示例工程 `sample-tested-service`，给出从启动到各场景（Unit/Env/Local、Full/Diff）跑通覆盖率报告的可执行步骤。

相关参考：

- [README.md](file:///D:/project/super-jacoco/README.md)
- [SAMPLE_TESTED_SERVICE_API.md](file:///D:/project/super-jacoco/SAMPLE_TESTED_SERVICE_API.md)

## 0. 关键概念与默认端口

- 覆盖率服务（super-jacoco）：`http://127.0.0.1:8899`
- 被测服务（sample-tested-service 示例）：`http://127.0.0.1:18082`（可通过 `--server.port` 自定义）
- JaCoCo Agent（tcpserver）端口：`18514`

报告与日志（服务端落盘与对外访问）：

- 报告根目录：`${user.dir}/report/`（见 [application.properties](file:///D:/project/super-jacoco/src/main/resources/application.properties) 的 `cov.paths.reportRoot`）
- 报告入口 URL：`http://127.0.0.1:8899/<uuid>/index.html`
- 任务日志 URL（Env/Unit 异步链路常用）：`http://127.0.0.1:8899/logs/<uuid>.log`

## 1. 启动方式

### 1.1 构建 super-jacoco

在仓库根目录：

```powershell
cd D:\project\super-jacoco
mvn -DskipTests package
```

### 1.2 启动 super-jacoco（两种 DB 方式）

#### A) 本地开发快速跑通：H2 内存库

适合快速验证链路，无需 MySQL：

```powershell
java -jar target\super-jacoco.jar `
  --spring.datasource.driver-class-name=org.h2.Driver `
  --spring.datasource.url="jdbc:h2:mem:superjacoco;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'file:D:/project/super-jacoco/sql/coverage_report_h2.sql'" `
  --spring.datasource.username=sa `
  --spring.datasource.password=
```

#### B) 生产/常规：MySQL

按 [application.properties](file:///D:/project/super-jacoco/src/main/resources/application.properties) 配置 `spring.datasource.*`，并初始化库表（SQL 在 `sql/coverage_report.sql`）。

### 1.3 构建并启动 sample-tested-service（带 JaCoCo tcpserver agent）

先构建：

```powershell
cd D:\project\super-jacoco\sample-tested-service
mvn -DskipTests package
```

再启动（端口与 agent 端口按本文默认值）：

```powershell
java -javaagent:D:\project\super-jacoco\jacoco\org.jacoco.agent-0.8.14-runtime.jar=includes=*,output=tcpserver,address=*,port=18514 `
  -jar D:\project\super-jacoco\sample-tested-service\sample-tested-service-app\target\sample-tested-service-app-1.0.0-SNAPSHOT.jar `
  --server.port=18082
```

如果出现 JaCoCo 插桩报错（常见于三方依赖字节码不兼容），建议把 `includes` 收敛到业务包，避免插桩全部依赖：

```powershell
java -javaagent:D:\project\super-jacoco\jacoco\org.jacoco.agent-0.8.14-runtime.jar=includes=com.example.sampletestedservice.*,output=tcpserver,address=*,port=18514 `
  -jar D:\project\super-jacoco\sample-tested-service\sample-tested-service-app\target\sample-tested-service-app-1.0.0-SNAPSHOT.jar `
  --server.port=18082
```

### 1.4 产生执行轨迹（Env/Local 必做）

Env/Local 都依赖从运行中 JVM dump 出 `jacoco.exec`，因此需要先对被测服务发起真实请求让轨迹产生：

```powershell
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:18082/demo/ping" -TimeoutSec 10
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:18082/demo/calc?op=add&a=1&b=2" -TimeoutSec 10
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:18082/demo/calc?op=sub&a=5&b=3" -TimeoutSec 10
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:18082/demo/calc?op=mul&a=4&b=6" -TimeoutSec 10
```

## 2. 参数速查

通用字段：

- `uuid`：任务唯一标识，用于查询结果与访问报告
- `gitUrl`：仓库地址或本机路径
- `type`：`1`=全量，`2`=增量
- `subModule`：多模块时限定模块；留空表示全仓统计

增量字段：

- `baseVersion`：基线 commit/分支
- `nowVersion`：当前 commit/分支

运行时 dump 字段（Env/Local）：

- `address`：被测服务机器 IP（本机一般 `127.0.0.1`）
- `port`：JaCoCo tcpserver 端口（本文默认 `18514`）

Local 特有字段：

- `basePath`：基线版本代码目录（必须在 `cov.paths.localCovRoot` 下）
- `nowPath`：当前版本代码目录（必须在 `cov.paths.localCovRoot` 下）
- `classFilePath`：当前版本编译产物 `target/classes` 目录（必须在 `cov.paths.localCovRoot` 下）

## 3. Unit（单元测试覆盖率，异步）

### 3.1 Unit 全量（type=1）

```powershell
$uuid = [guid]::NewGuid().ToString('N')
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  nowVersion='b73faff10f3b1af5ea08784fd7aa164f39fa5f24'
  type=1
  subModule=''
  envType=''
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8899/cov/triggerUnitCover' -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get  -Uri "http://127.0.0.1:8899/cov/getUnitCoverResult?uuid=$uuid" | ConvertTo-Json -Depth 10
```

成功后：打开 `data.reportUrl`。

### 3.2 Unit 增量（type=2）

```powershell
$uuid = [guid]::NewGuid().ToString('N')
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  baseVersion='718e00df9de4022cb04374b8f00dfb8d680379ee'
  nowVersion='b73faff10f3b1af5ea08784fd7aa164f39fa5f24'
  type=2
  subModule=''
  envType=''
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8899/cov/triggerUnitCover' -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get  -Uri "http://127.0.0.1:8899/cov/getUnitCoverResult?uuid=$uuid" | ConvertTo-Json -Depth 10
```

提示：如果出现 `nodiffcode.html / 没有增量代码`，检查 `baseVersion` 与 `nowVersion` 是否相同，或 `subModule` 是否把变更过滤掉。

## 4. Env（环境覆盖率，异步：克隆+编译+dump+report）

### 4.1 Env 全量（type=1）

```powershell
$uuid = [guid]::NewGuid().ToString('N')
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  nowVersion='b73faff10f3b1af5ea08784fd7aa164f39fa5f24'
  type=1
  subModule=''
  address='127.0.0.1'
  port=18514
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8899/cov/triggerEnvCov' -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get  -Uri "http://127.0.0.1:8899/cov/getEnvCoverResult?uuid=$uuid" | ConvertTo-Json -Depth 10
```

注意（H2 场景）：全量时不要显式传 `baseVersion: ''` 空串。

### 4.2 Env 增量（type=2）

```powershell
$uuid = [guid]::NewGuid().ToString('N')
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  baseVersion='718e00df9de4022cb04374b8f00dfb8d680379ee'
  nowVersion='b73faff10f3b1af5ea08784fd7aa164f39fa5f24'
  type=2
  subModule=''
  address='127.0.0.1'
  port=18514
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8899/cov/triggerEnvCov' -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get  -Uri "http://127.0.0.1:8899/cov/getEnvCoverResult?uuid=$uuid" | ConvertTo-Json -Depth 10
```

排障入口：`data.logFile`。

## 5. Local（本机路径模式，同步：直接用你给的源码与 class）

Local 模式会校验 `basePath/nowPath/classFilePath` 必须落在 `cov.paths.localCovRoot` 之下（默认 `D:/project/super-jacoco/report/localcov/`）。

### 5.1 准备 base/now 两份代码目录

示例目录结构（目录名可自定义，关键是路径必须在 localCovRoot 下）：

- `D:/project/super-jacoco/report/localcov/sts-local-base-<base>`
- `D:/project/super-jacoco/report/localcov/sts-local-now-<now>`

示例命令：

```powershell
$root = 'D:/project/super-jacoco/report/localcov'
$baseDir = Join-Path $root 'sts-local-base-718e00d'
$nowDir  = Join-Path $root 'sts-local-now-b73faff'

git clone 'D:/project/super-jacoco/sample-tested-service' $baseDir
Set-Location $baseDir; git checkout 718e00df9de4022cb04374b8f00dfb8d680379ee

Set-Location $root
git clone 'D:/project/super-jacoco/sample-tested-service' $nowDir
Set-Location $nowDir; git checkout b73faff10f3b1af5ea08784fd7aa164f39fa5f24

Set-Location $nowDir
mvn -DskipTests package
```

### 5.2 Local 全量（type=1）

```powershell
$nowDir='D:/project/super-jacoco/report/localcov/sts-local-now-b73faff'
$baseDir='D:/project/super-jacoco/report/localcov/sts-local-base-718e00d'

$uuid = [guid]::NewGuid().ToString('N')
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  baseVersion='718e00df9de4022cb04374b8f00dfb8d680379ee'
  nowVersion='b73faff10f3b1af5ea08784fd7aa164f39fa5f24'
  type=1
  address='127.0.0.1'
  port=18514
  subModule=''
  classFilePath=($nowDir+'/sample-tested-service-app/target/classes')
  basePath=$baseDir
  nowPath=$nowDir
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8899/cov/getLocalCoverResult' -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 10
```

### 5.3 Local 增量（type=2）

```powershell
$nowDir='D:/project/super-jacoco/report/localcov/sts-local-now-b73faff'
$baseDir='D:/project/super-jacoco/report/localcov/sts-local-base-718e00d'

$uuid = [guid]::NewGuid().ToString('N')
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  baseVersion='718e00df9de4022cb04374b8f00dfb8d680379ee'
  nowVersion='b73faff10f3b1af5ea08784fd7aa164f39fa5f24'
  type=2
  address='127.0.0.1'
  port=18514
  subModule=''
  classFilePath=($nowDir+'/sample-tested-service-app/target/classes')
  basePath=$baseDir
  nowPath=$nowDir
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8899/cov/getLocalCoverResult' -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 10
```

注意：Local 接口字段名是 `address`（小写），不要用 `Address`。

## 6. 常见问题

- Windows 重新打包 jar 报 `Unable to rename ... .jar.original`：通常是 jar 正在运行被占用，先停掉对应 Java 进程再构建。
- Env/Local 覆盖率为 0 或明显偏低：先确认已对被测服务发起过真实请求（见 1.4）。
- Env 任务卡住/失败：优先打开 `data.logFile` 查 clone/compile/dump/report 具体失败点。
