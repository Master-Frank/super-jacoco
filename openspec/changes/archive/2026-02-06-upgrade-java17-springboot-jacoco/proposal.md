## Why

本仓库当前 Java/Spring Boot/JaCoCo 版本组合偏旧，影响在现代 JDK 环境下的可运行性、依赖生态兼容性与安全修复获取。
将主项目与被测项目同步升级到 Java 17、较新的 Spring Boot 与 JaCoCo 0.8.14，降低构建与运行风险，并保证覆盖率采集/报告链路持续可用。

## What Changes

- 将本项目 Java 版本升级到 Java 17
- 升级 Spring Boot 版本，并同步更新相关依赖与 Maven 插件版本
- 将 JaCoCo 升级到 0.8.14
- 将被测项目 `sample-tested-service` 同步升级到 Java 17 与匹配的 Spring Boot/依赖
- 通过项目现有测试与构建验证升级后功能正常

## Capabilities

### New Capabilities

- `platform-upgrade`: 统一升级到 Java 17，并确保构建、测试与覆盖率链路可用

### Modified Capabilities

(无)

## Impact

- 构建与运行环境：要求 JDK 17；需要统一主项目与 `sample-tested-service` 的编译/运行版本
- 依赖与插件：Spring Boot BOM 与 Maven 插件（编译/测试/打包）版本需要对齐并兼容 Java 17
- 覆盖率链路：JaCoCo 0.8.14（依赖 ASM 9.9）对字节码分析能力更强；需验证 agent 与报告生成在升级后仍可用
- 兼容性风险：Spring Boot 版本升级可能带来配置项/依赖坐标变化；需以现有测试与启动验证作为回归门槛
