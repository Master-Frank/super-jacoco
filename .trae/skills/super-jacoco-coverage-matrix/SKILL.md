---
name: super-jacoco-coverage-matrix
description: 在 super-jacoco 项目中执行覆盖率全流程（单/多模块、全量/增量、单测 Unit/环境 Env/本地 Local），并生成可访问报告；适用于排查 nodiffcode、子模块过滤、Windows jar 占用、Local 根目录校验等常见问题。
---

# super-jacoco 覆盖率全流程（Unit / Env / Local，Full / Diff，Single / Multi module）

## 0. 约定与前置条件

- 覆盖率服务（super-jacoco）默认监听：`http://127.0.0.1:8899`
- 被测服务（sample-tested-service 示例）默认监听：`http://127.0.0.1:18082`
- JaCoCo agent（tcpserver）默认端口：`18514`

关键配置（来自 [application.properties](file:///d:/project/super-jacoco/src/main/resources/application.properties)）：

- 报告根目录：`cov.paths.reportRoot=${user.dir}/report/`
- 本地覆盖率根目录（Local 模式白名单根）：`cov.paths.localCovRoot=${user.dir}/report/localcov/`
- JaCoCo CLI：`cov.paths.jacocoCliJar=${user.dir}/jacoco/org.jacoco.cli-0.8.14-nodeps.jar`

字段含义：

- `type=1`：全量覆盖率（Full）
- `type=2`：增量覆盖率（Diff），必须满足 `baseVersion != nowVersion`
- `subModule`：
  - 空字符串：对整个仓库/所有模块统计
  - 非空：只统计指定子模块（多模块场景）
  - 增量时特别注意：diff 文件过滤基于“路径包含 subModule 字符串”，改动不在该模块会判定无增量

安全：如果 `cov.security.enabled=true`，需要按配置的 Header/Token 传鉴权；本仓库默认 `false`。

## 1. 启动服务

### 1.1 启动 super-jacoco（H2 内存库模式）

在 `d:\project\super-jacoco` 下启动（PowerShell 示例）：

```powershell
java -jar target\super-jacoco.jar `
  --spring.datasource.driver-class-name=org.h2.Driver `
  --spring.datasource.url="jdbc:h2:mem:superjacoco;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'file:D:/project/super-jacoco/sql/coverage_report_h2.sql'\;RUNSCRIPT FROM 'file:D:/project/super-jacoco/sql/cumulative_coverage_h2.sql'" `
  --spring.datasource.username=sa `
  --spring.datasource.password=
```

### 1.2 启动被测服务（带 JaCoCo tcpserver agent）

```powershell
java -javaagent:D:\project\super-jacoco\jacoco\org.jacoco.agent-0.8.14-runtime.jar=includes=*,output=tcpserver,address=*,port=18514 `
  -jar D:\project\super-jacoco\sample-tested-service\sample-tested-service-app\target\sample-tested-service-app-1.0.0-SNAPSHOT.jar `
  --server.port=18082
```

如遇 JaCoCo 插桩报错（常见于三方依赖字节码不兼容），优先把 `includes` 收敛到业务包，避免插桩所有依赖：

```powershell
java -javaagent:D:\project\super-jacoco\jacoco\org.jacoco.agent-0.8.14-runtime.jar=includes=com.example.sampletestedservice.*,output=tcpserver,address=*,port=18514 `
  -jar D:\project\super-jacoco\sample-tested-service\sample-tested-service-app\target\sample-tested-service-app-1.0.0-SNAPSHOT.jar `
  --server.port=18082
```

验证：

```powershell
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:18082/demo/ping" -TimeoutSec 10
```

Windows 重要提示：如果要重新 `mvn package` 生成 jar，必须先停掉正在运行的被测服务进程，否则会出现 `Unable to rename ... .jar.original`。

## 2. Unit（单元测试）覆盖率

### 2.1 Unit 全量（type=1）

```powershell
$uuid = '<uuid>'
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  nowVersion='<nowCommit>'
  type=1
  subModule=''
  envType=''
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8899/cov/triggerUnitCover -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get  -Uri "http://127.0.0.1:8899/cov/getUnitCoverResult?uuid=$uuid" | ConvertTo-Json -Depth 8
```

### 2.2 Unit 增量（type=2）

```powershell
$uuid = '<uuid>'
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  baseVersion='<baseCommit>'
  nowVersion='<nowCommit>'
  type=2
  subModule=''  # 多模块想只看某模块可填模块名；跨模块改动建议留空
  envType=''
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8899/cov/triggerUnitCover -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get  -Uri "http://127.0.0.1:8899/cov/getUnitCoverResult?uuid=$uuid" | ConvertTo-Json -Depth 8
```

常见异常处理：

- 返回 `nodiffcode.html / 没有增量代码`：
  - 检查 `baseVersion == nowVersion`
  - 检查 `subModule` 是否导致过滤掉了所有变更（改动不在该模块）

## 3. Env（环境部署/运行时 dump）覆盖率

### 3.1 Env 全量（type=1）

```powershell
$uuid = '<uuid>'
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  nowVersion='<nowCommit>'
  type=1
  subModule=''
  address='127.0.0.1'
  port=18514
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8899/cov/triggerEnvCov -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get  -Uri "http://127.0.0.1:8899/cov/getEnvCoverResult?uuid=$uuid" | ConvertTo-Json -Depth 8
```

### 3.2 Env 增量（type=2）

```powershell
$uuid = '<uuid>'
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/sample-tested-service'
  baseVersion='<baseCommit>'
  nowVersion='<nowCommit>'
  type=2
  subModule=''
  address='127.0.0.1'
  port=18514
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8899/cov/triggerEnvCov -ContentType 'application/json' -Body $body
Invoke-RestMethod -Method Get  -Uri "http://127.0.0.1:8899/cov/getEnvCoverResult?uuid=$uuid" | ConvertTo-Json -Depth 8
```

运行时覆盖关键点：

- Env/Local 都依赖 dump 出来的 `jacoco.exec`，因此需要先对被测服务发起真实请求让执行轨迹产生

## 4. Local（本机源码 + 本机 class + dump exec）覆盖率

Local 模式会校验 `basePath/nowPath/classFilePath` 必须在 `cov.paths.localCovRoot` 之下（默认 `d:\project\super-jacoco\report\localcov\`）。

### 4.1 准备 base/now 两份代码目录（放到 localCovRoot 下）

示例目录结构（自行替换 commit）：

- `d:\project\super-jacoco\report\localcov\sts-local-base-<base>`
- `d:\project\super-jacoco\report\localcov\sts-local-now-<now>`

```powershell
$root = 'D:/project/super-jacoco/report/localcov'
$baseDir = Join-Path $root 'sts-local-base-<base>'
$nowDir  = Join-Path $root 'sts-local-now-<now>'

git clone 'D:/project/super-jacoco/sample-tested-service' $baseDir
Set-Location $baseDir; git checkout <baseCommit>

Set-Location $root
git clone 'D:/project/super-jacoco/sample-tested-service' $nowDir
Set-Location $nowDir; git checkout <nowCommit>

Set-Location $nowDir
mvn -q -pl sample-tested-service-app -am package -DskipTests
```

### 4.2 调用 Local 覆盖率接口

```powershell
$uuid = '<uuid>'
$body = @{
  uuid=$uuid
  gitUrl='D:/project/super-jacoco/report/localcov/sts-local-now-<now>'
  baseVersion='<baseCommit>'
  nowVersion='<nowCommit>'
  type=2
  address='127.0.0.1'
  port=18514
  subModule=''
  classFilePath='D:/project/super-jacoco/report/localcov/sts-local-now-<now>/sample-tested-service-app/target/classes'
  basePath='D:/project/super-jacoco/report/localcov/sts-local-base-<base>'
  nowPath='D:/project/super-jacoco/report/localcov/sts-local-now-<now>'
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8899/cov/getLocalCoverResult -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 10
```

如果返回 `basePath不合法 / nowPath不合法 / classFilePath不合法`：

- 检查路径是否真的落在 `d:\project\super-jacoco\report\localcov\` 下

补充：Local 接口里字段名是 `address`（小写），不要用 `Address`，否则会命中校验报 `Address不能为空`。

## 5. 单模块 vs 多模块的参数选择

- 单模块项目：通常 `subModule=''`；Unit/Env/Local 都能按默认单模块目录生成报告
- 多模块项目：
  - 想看“整个仓库”的覆盖：`subModule=''`
  - 想只看某子模块：
    - Unit/Env：`subModule='<moduleName>'`
    - 增量时必须保证变更文件路径包含该模块名，否则会被过滤为无增量

## 6. 执行中常见坑（实战补充）

- Env 全量（type=1）不要传 `baseVersion: ''` 空串；在 H2 场景会触发 DB `base_version` 非空约束导致 `code=-1 触发失败`。建议全量时直接不传 `baseVersion` 字段。
- Env/Local 出报告前必须对被测服务打真实请求，否则 `jacoco.exec` 里几乎没有轨迹，覆盖率会异常偏低或为空。
- 被测服务启动时若出现 JaCoCo `Error while instrumenting ...`，优先把 agent `includes` 收敛到业务包（见上文示例）。
- 启动 super-jacoco 若报 `Port 8899 was already in use`，先复用已启动服务或更换端口，不要重复起服务。
- 指定 `-Dtest=类名#方法名` 在旧 commit 可能不存在该方法并触发 `No tests were executed`；需先确认方法存在，或加 `-DfailIfNoTests=false` 并回退到存在的测试方法。
- 若目标是“3 个 package 的跨 commit 累计”，`run2` 不能只用 core 单模块 XML；必须使用包含 `com/example/sampletestedservice`、`.../service`、`.../web` 的 XML（通常来自 ENV dump 生成的 `ManualCoverage`）。

## 7. 跨 commit 累计覆盖率（含部分覆盖 run）验证流程

本节用于验证三件事：

- 确实发生了跨 commit 累计（run 的 commitId 发生变化）
- 报告数据采用 `cumulative/data/*.json.gz` 落盘
- 存在部分覆盖行（`branchCovered > 0` 且 `branchMissed > 0`）

### 7.1 每次实时生成输入产物（不复用历史文件）

每次验证都从源码重新生成 `jacoco.xml.gz`，并放到独立目录：

```powershell
$ErrorActionPreference = 'Stop'
$commitA = '718e00df9de4022cb04374b8f00dfb8d680379ee'
$commitB = 'b73faff10f3b1af5ea08784fd7aa164f39fa5f24'

$ts = Get-Date -Format 'yyyyMMddHHmmss'
$genRoot = "D:/project/super-jacoco/report/cumulative-e2e/generated-$ts"
$repoA = "$genRoot/repoA"
$repoB = "$genRoot/repoB"
New-Item -ItemType Directory -Force -Path $genRoot | Out-Null

git clone 'D:/project/super-jacoco/sample-tested-service' $repoA | Out-Null
git clone 'D:/project/super-jacoco/sample-tested-service' $repoB | Out-Null
Set-Location $repoA; git checkout $commitA | Out-Null
Set-Location $repoB; git checkout $commitB | Out-Null

function New-XmlGz([string]$repoPath,[string]$testCase,[string]$outGz){
  Set-Location $repoPath
  mvn -q -pl sample-tested-service-core clean `
    org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent `
    test -Dtest=$testCase -DfailIfNoTests=false `
    org.jacoco:jacoco-maven-plugin:0.8.14:report
  $xml = Join-Path $repoPath 'sample-tested-service-core/target/site/jacoco/jacoco.xml'
  if (!(Test-Path $xml)) { throw "jacoco.xml not found: $xml" }
  $in=[System.IO.File]::OpenRead($xml)
  $out=[System.IO.File]::Create($outGz)
  $gz=New-Object System.IO.Compression.GZipStream($out,[System.IO.Compression.CompressionLevel]::Optimal)
  $in.CopyTo($gz)
  $gz.Dispose(); $out.Dispose(); $in.Dispose()
}

$run1Gz = "$genRoot/run1-commitA.xml.gz"
$run2Gz = "$genRoot/run2-commitB.xml.gz"
$run3Gz = "$genRoot/run3-commitB-partial.xml.gz"

New-XmlGz $repoA 'CalculatorServiceTest#add_returnsSum' $run1Gz
New-XmlGz $repoB 'CalculatorServiceTest#sub_returnsDifference' $run2Gz
New-XmlGz $repoB 'CalculatorServiceTest#calcWithBranch_routesOperations' $run3Gz
```

说明：

- `run1`：commitA 低覆盖
- `run2`：commitB 低覆盖（用于触发跨 commit）
- `run3`：commitB 分支部分覆盖（通常会产生 `branchCovered=1, branchMissed=1` 的行）

### 7.1.1 三 package 场景的 run2 输入校验（推荐必做）

跨 commit 验证若要求最终报告包含 3 个 package，先校验 `run2` 的 `jacoco.xml.gz`：

```powershell
function Get-PackageListFromXmlGz([string]$xmlGzPath){
  $fs=[System.IO.File]::OpenRead($xmlGzPath)
  $gz=New-Object System.IO.Compression.GZipStream($fs,[System.IO.Compression.CompressionMode]::Decompress)
  $sr=New-Object System.IO.StreamReader($gz,[System.Text.Encoding]::UTF8)
  $txt=$sr.ReadToEnd()
  $sr.Close(); $gz.Close(); $fs.Close()
  [regex]::Matches($txt,'<package name="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
}

$pkgs = @(Get-PackageListFromXmlGz $run2Gz)
"run2PackageCount=$($pkgs.Count)"
$pkgs
```

期望至少包含：

- `com/example/sampletestedservice`
- `com/example/sampletestedservice/service`
- `com/example/sampletestedservice/web`

如果 `run2` 只有一个 package（常见是仅 core 或仅 app 模块），请替换 `run2Gz` 为包含三包的真实 XML（例如 ENV dump 产物或历史三包 run 的 XML），再继续 7.2/7.3。

### 7.2 创建 coverage set（累计口径）

```powershell
$setId = "cross-partial-e2e-$ts"
$createBody = @{
  coverageSetId = $setId
  gitUrl = 'D:/project/super-jacoco/sample-tested-service'
  repoLocalPath = 'D:/project/super-jacoco/sample-tested-service'
  branch = "cross-partial-e2e-$ts"
  type = 'FULL'
  fromType = 'ENV'
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8899/api/v1/coverage/sets' -ContentType 'application/json' -Body $createBody
```

### 7.3 追加三次 run（跨 commit + 部分覆盖）

```powershell
function Append-And-Wait([string]$setId,[string]$commitId,[string]$xmlPath,[string]$msg){
  $body=@{
    commitId=$commitId
    commitMessage=$msg
    xmlObjectKey=$xmlPath
  } | ConvertTo-Json
  $resp=Invoke-RestMethod -Method Post -Uri ("http://127.0.0.1:8899/api/v1/coverage/sets/{0}/runs" -f $setId) -ContentType 'application/json' -Body $body
  $runId=$resp.data.runId
  for($i=0;$i -lt 60;$i++){
    $r=Invoke-RestMethod -Method Get -Uri ("http://127.0.0.1:8899/api/v1/coverage/sets/{0}/runs/{1}" -f $setId,$runId)
    if($r.data.status -eq 'COMPLETED' -or $r.data.status -eq 'FAILED'){ return $r.data }
    Start-Sleep -Milliseconds 400
  }
  throw "run timeout: $runId"
}

$r1 = Append-And-Wait $setId $commitA $run1Gz 'commitA run1'
$r2 = Append-And-Wait $setId $commitB $run2Gz 'commitB run2 cross commit'
$r3 = Append-And-Wait $setId $commitB $run3Gz 'commitB run3 partial'
```

### 7.4 验证跨 commit 与累计结果

```powershell
$overview = Invoke-RestMethod -Method Get -Uri ("http://127.0.0.1:8899/api/v1/coverage/sets/{0}" -f $setId)
$overview | ConvertTo-Json -Depth 8
```

重点检查：

- `totalRuns >= 3`
- `currentCommitId = $commitB`
- `reportUrl` 可访问

### 7.5 验证 json.gz 落盘与部分覆盖行

```powershell
$base = "D:/project/super-jacoco/report/$setId/cumulative/data"
Test-Path "$base/summary.json.gz"
Test-Path "$base/classes/com/example/sampletestedservice/service/CalculatorService.json.gz"

$path = "$base/classes/com/example/sampletestedservice/service/CalculatorService.json.gz"
$fs=[System.IO.File]::OpenRead($path)
$gz=New-Object System.IO.Compression.GZipStream($fs,[System.IO.Compression.CompressionMode]::Decompress)
$sr=New-Object System.IO.StreamReader($gz,[System.Text.Encoding]::UTF8)
$txt=$sr.ReadToEnd()
$sr.Close(); $gz.Close(); $fs.Close()
$obj=$txt | ConvertFrom-Json
$partial = $obj.lines | Where-Object { (($_.branchCovered + $_.branchMissed) -gt 0) -and ($_.branchCovered -gt 0) -and ($_.branchMissed -gt 0) }
"partialCount=$((@($partial)).Count)"
$partial | Select-Object -First 5
```

输出中出现类似：

- `lineNo=xx, branchCovered=1, branchMissed=1`

即可判定“部分覆盖 run 生效”。
