## ADDED Requirements

### Requirement: Build and test on Java 17
主项目与 `sample-tested-service` SHALL 在 Java 17 环境下完成构建，并通过其现有测试。

#### Scenario: Maven tests succeed on Java 17
- **WHEN** 使用 Java 17 执行两项目的 Maven 测试
- **THEN** 构建成功且现有测试全部通过

### Requirement: sample-tested-service runs on Java 17
`sample-tested-service` SHALL 在 Java 17 环境下成功启动，并按现有接口定义提供服务。

#### Scenario: Ping endpoint responds
- **WHEN** 服务启动后请求 `/demo/ping`
- **THEN** 返回 200 且响应体为 `pong`

### Requirement: Coverage pipeline works with JaCoCo 0.8.14
覆盖率采集与报告生成链路 SHALL 使用 JaCoCo 0.8.14，并在 Java 17 环境下可执行且无异常退出。

#### Scenario: Report generation completes
- **WHEN** 在 Java 17 环境下对运行中的 `sample-tested-service` 执行覆盖率采集与报告生成
- **THEN** 成功生成报告产物且流程无异常

