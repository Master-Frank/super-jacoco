## Context

该仓库包含主项目（super-jacoco）与被测示例项目（sample-tested-service）。当前版本组合偏旧，存在以下问题：

- 在 Java 17+ 环境下构建/运行兼容性不确定
- Spring Boot 旧版本依赖生态与安全修复获取受限
- JaCoCo 旧版本对新字节码/Kotlin/新编译器生成代码的处理能力不足，且与升级后的 JDK 组合存在潜在不兼容

本次变更要求主项目与 sample-tested-service 同步升级到 Java 17，并将 JaCoCo 升级到 0.8.14，保证覆盖率采集与报告生成链路可用。

## Goals / Non-Goals

**Goals:**

- 主项目与 sample-tested-service 统一升级到 Java 17 可稳定构建与运行
- Spring Boot 升级到 Java 17 兼容的主版本，并完成必要的依赖/插件对齐
- JaCoCo 升级到 0.8.14，并验证 agent 与报告生成在升级后正常工作
- 通过现有测试与构建验证确保功能回归

**Non-Goals:**

- 不新增对外功能能力或更改对外 API 语义
- 不引入新的运行时架构（例如容器化、服务拆分）
- 不为升级单独编写新的测试用例

## Decisions

- 选择 Spring Boot 2.7.x（落地版本为 2.7.18）作为目标主版本
  - 理由：在满足 Java 17 目标的前提下，最大化兼容当前代码与依赖生态（例如 `javax.validation.*` 注解、Springfox 3.x、现有 MyBatis 相关依赖），避免一次性引入 `jakarta.*` 全量迁移带来的风险与改动面。
  - 备选：升级到 Spring Boot 3.x（同样以 Java 17 为基础，但需要完成 `javax.*` → `jakarta.*` 的迁移，并替换/升级与之不兼容的依赖）。

- Maven 编译目标统一使用 `release=17`
  - 理由：避免 source/target 不一致，确保编译产物与 Java 17 运行时一致

- JaCoCo 统一升级到 0.8.14
  - 理由：官方支持更高版本 Java，且对 Kotlin/编译器生成分支的过滤与字节码处理更完善；0.8.14 依赖 ASM 9.9，字节码解析能力更强

## Risks / Trade-offs

- Spring Boot 3.x 依赖 Spring Framework 6，Servlet/JPA 等相关 API 迁移到 `jakarta.*`
  - 可能影响：若代码或依赖仍引用 `javax.*`（例如 `javax.servlet`），需要完成包名迁移
  - 缓解：先全仓扫描 `javax.` 引用与依赖树，逐项迁移；以构建与现有测试作为回归门槛

- Maven 插件与测试运行器版本不兼容导致构建失败
  - 缓解：对齐 `maven-compiler-plugin`、`maven-surefire-plugin`、`jacoco-maven-plugin` 等版本到 Java 17 / Boot 3 兼容范围

- 覆盖率采集链路中 agent 版本与主项目解析/报告生成版本不一致
  - 缓解：主项目与 sample-tested-service 同步升级 JaCoCo 相关依赖；若仓库内存在内置 agent jar，也同步替换到 0.8.14

## Migration Plan

- 停止当前运行中的旧版本服务（如 sample-tested-service 的 Java 8 + JaCoCo agent 进程）
- 升级主项目与 sample-tested-service 的 Java 与 Spring Boot 版本配置
- 升级 JaCoCo 依赖与 Maven 插件到 0.8.14，并更新仓库内引用到的 agent jar（如存在）
- 在本地执行构建与测试验证（主项目与 sample-tested-service 分别执行）
- 执行一次端到端链路验证：启动 sample-tested-service、触发覆盖率采集、生成报告
- 如遇不兼容：优先回退到可构建状态，再逐步定位并解决依赖/包名迁移问题

## Open Questions

- 当前项目是否存在对 Servlet/JPA 等 `javax.*` 依赖的直接引用，需要迁移到 `jakarta.*`
- 覆盖率采集链路是否依赖仓库内固定版本的 agent jar（而非 Maven 依赖），需要确认并统一替换
