## 1. Baseline

- [x] 1.1 停止当前运行中的旧版本进程（super-jacoco、sample-tested-service）
- [x] 1.2 盘点主项目与 sample-tested-service 的当前 Java/Spring/JaCoCo 版本

## 2. Upgrade Main Project

- [x] 2.1 将主项目构建目标升级到 Java 17
- [x] 2.2 升级主项目 Spring Boot 版本并修复 Jakarta 迁移相关问题（如有）
- [x] 2.3 升级主项目 JaCoCo 依赖与 Maven 插件到 0.8.14

## 3. Upgrade sample-tested-service

- [x] 3.1 将 sample-tested-service 构建目标升级到 Java 17
- [x] 3.2 升级 sample-tested-service Spring Boot 版本并修复 Jakarta 迁移相关问题（如有）
- [x] 3.3 升级 sample-tested-service JaCoCo 相关配置/插件到 0.8.14

## 4. Coverage Pipeline Alignment

- [x] 4.1 更新仓库内引用到的 JaCoCo agent runtime jar 到 0.8.14（如存在固定版本文件）
- [x] 4.2 验证覆盖率采集与报告生成链路在 Java 17 下可正常执行

## 5. Verification

- [x] 5.1 主项目执行 Maven 测试并通过
- [x] 5.2 sample-tested-service 执行 Maven 测试并通过
- [x] 5.3 启动 sample-tested-service 并验证 `/demo/ping` 与 `/demo/calc` 可用
