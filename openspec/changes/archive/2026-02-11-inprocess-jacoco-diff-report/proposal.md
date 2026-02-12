## Why

当前增量覆盖率报告依赖 `org.jacoco.cli-1.0.2-SNAPSHOT-nodeps.jar` 的自定义 `--diffFile` 扩展参数，属于非官方 SNAPSHOT 产物，存在供应链与维护风险；同时 JaCoCo 版本不统一，增加 Java 17 字节码兼容与排障成本。

## What Changes

- 移除对 `org.jacoco.cli-1.0.2-SNAPSHOT-nodeps.jar` 的依赖与引用，仅保留官方 `org.jacoco.cli-0.8.14-nodeps.jar` 用于 dump。
- 在 super-jacoco 进程内使用 JaCoCo 0.8.14 core/report API 生成报告产物：HTML（给人看）与 XML（给机器处理）。
- 在进程内实现方法级增量过滤：复用现有 diffMethod 计算结果，对覆盖率模型进行过滤并重新汇总 line/branch 统计。
- 维持对外接口与报告产物路径行为不变（URL、目录结构、合并报告逻辑保持兼容）。

## Capabilities

### New Capabilities

- `method-level-diff-coverage-report`: 使用 JaCoCo 0.8.14 API 生成方法级增量覆盖率报告（HTML+XML，不依赖自定义 CLI 参数）。

### Modified Capabilities

- 

## Impact

- 受影响代码：`CodeCovServiceImpl` 的报告生成逻辑、`DiffMethodsCalculator/JDiffFiles/MethodParser` 的 diffMethod 语义对齐、报告复制与合并逻辑。
- 依赖变更：新增 `org.jacoco:org.jacoco.report`（版本对齐 0.8.14），并清理 SNAPSHOT CLI 路径配置。
- 运维与配置：删除 `cov.paths.jacocoCliDiffJar` 配置项与对应 jar 文件；确保生成报告的静态资源仍可被 super-jacoco 正常访问。
