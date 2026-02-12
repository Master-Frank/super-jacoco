## Context

super-jacoco 当前的增量覆盖率报告生成依赖 `org.jacoco.cli-1.0.2-SNAPSHOT-nodeps.jar` 在 `report` 命令中扩展的 `--diffFile` 参数。该 jar 不是官方发布版本，且与项目中其他 JaCoCo 组件版本不统一。

现有增量输入来自 `DiffMethodsCalculator/JDiffFiles/MethodParser` 生成的 `diffMethod` 字符串（实际为“内联 diffSpec”，不是文件路径），并在生成报告时通过命令行参数透传。

目标是完全移除 SNAPSHOT CLI，统一使用 JaCoCo 0.8.14，并保持“方法级增量”能力与现有接口/产物兼容。

## Goals / Non-Goals

**Goals:**

- 移除 `org.jacoco.cli-1.0.2-SNAPSHOT-nodeps.jar` 与 `cov.paths.jacocoCliDiffJar` 配置项。
- 使用 JaCoCo 0.8.14 core/report API 在 super-jacoco 进程内生成 HTML 报告。
- 报告对人可读依然以 HTML 为准，同时输出 XML 作为稳定的结构化数据源。
- 复用现有 `diffMethod` 语义，实现方法级增量过滤，并对 line/branch 汇总统计口径与报告页面一致。
- 多模块/单模块两种路径与现有行为兼容（输出目录结构、合并报告逻辑、URL 结构保持不变）。

**Non-Goals:**

- 不修改 diffMethod 的生成逻辑与格式（除非发现与历史实现不一致的缺陷）。
- 不改变 dump exec 的方式（继续用官方 CLI 0.8.14 的 `dump`）。
- 不引入新的外部服务或存储（数据库表结构与接口协议不做扩展）。

## Decisions

### 1) 报告生成从外部 CLI 切换到进程内 API

**Decision:** 在 super-jacoco 内部新增报告生成器，使用 JaCoCo 0.8.14 的 `org.jacoco.core` 与 `org.jacoco.report` 产出 HTML（给人看）并同时产出 XML（给机器处理）。

**Rationale:**

- 官方 CLI 0.8.14 的 `report` 不支持 `--diffFile`，继续依赖 CLI 无法实现方法级过滤。
- 进程内 API 方式可完全掌控过滤逻辑与汇总口径，同时去除外部命令拼接与平台差异（Windows 路径/转义）。
- 结构化的 XML 产物更适合做覆盖率指标计算与入库，减少对 HTML 结构的耦合。

**Alternatives considered:**

- 继续维护 SNAPSHOT CLI：供应链风险与维护成本不可接受。
- 仅做类级增量（传入变更 classfiles）：口径退化，不满足“一步到位到方法级”。
- 通过后处理 HTML 隐藏/改写：难以保证汇总统计一致且稳定。

### 2) 过滤策略：构造“过滤后的覆盖率模型”再生成报告

**Decision:** 先对 classfiles 做完整分析得到覆盖率模型，再基于 diffSpec 过滤出只包含目标方法的覆盖率树，并重新计算 counters，最终将过滤后的模型交给 HTML 报告生成器。

**Rationale:**

- JaCoCo exec 的探针粒度在 class 级，无法通过裁剪 exec 精确到方法。
- 报告汇总（line/branch）必须与过滤后的方法集合一致，否则报表与数据库统计会出现口径漂移。

**Implementation approach:**

- 使用 `ExecFileLoader` 读取 `jacoco.exec`。
- 使用 `Analyzer` + `CoverageBuilder` 分析 classfiles，得到 `IBundleCoverage`（以及 packages/classes/methods/source files）。
- 解析 diffSpec 形成规则表：
  - key：相对类路径（去掉 `.java`），与现有 diffMethod key 保持一致。
  - value：`true` 表示整类纳入；否则是方法签名列表（以 `#` 分隔）。
- 为报告生成提供一套“过滤覆盖率视图”对象：
  - `FilteredBundleCoverage` / `FilteredPackageCoverage` / `FilteredClassCoverage` / `FilteredMethodCoverage` / `FilteredSourceFileCoverage`。
  - 这些对象对外实现 JaCoCo 的 coverage interfaces（如 `IBundleCoverage`/`IClassCoverage`/`IMethodCoverage`），内部委托原始对象，同时暴露过滤后的 children 与 counters。
- counters 计算：实现一个轻量 `Counter`（实现 `ICounter`）来累加 missed/covered；
  - `Method`：直接使用原始 method counters（不改）。
  - `Class`/`Package`/`Bundle`：对保留的 children counters 做归并。
  - `Line/Branch`：基于“被保留方法的行区间”构造过滤后的 line 视图（区间外视为 0/0），并据此计算 line/branch counters。

### 3) 方法匹配：以“现有 diffMethod 签名规则”为准

**Decision:** 方法匹配使用 `MethodParser` 产出的签名规则对齐 JaCoCo 的方法描述符，确保历史 diff 语义不变。

**Rationale:**

`MethodParser` 生成的签名不是 JVM descriptor，而是 `name,ParamTypeSimpleName,...`，并且会跳过 primitive 参数类型。若不做对齐，增量方法集合将出现大量漏匹配，导致报告缺失。

**Implementation approach:**

- 对每个 `IMethodCoverage` 读取 `getName()` 与 `getDesc()`（JVM method descriptor）。
- 用 ASM `Type.getArgumentTypes(desc)` 得到参数类型数组：
  - 引用类型：取 simple name（例如 `com.foo.Bar` → `Bar`）。
  - 数组类型：取 element type 的 simple name（例如 `Bar[]` 仍按 `Bar` 处理）。
  - primitive：按现有规则跳过，不加入签名。
- 组装与 diffSpec 同形的 key：`name,Bar,Baz`。

**Overload handling:**

- 若同一个 diffSpec 方法签名匹配到多个 `IMethodCoverage`（由于 primitive 参数被跳过导致重载冲突），则该签名命中的所有方法 MUST 全部纳入过滤结果，以避免漏算增量。

### 4) diffSpec key 到 JaCoCo class 的对齐：模块内归一化 + 兜底匹配

**Decision:** 过滤时对 diffSpec 的 key 做模块内归一化，转换为 JaCoCo class name（`com/foo/Bar`）后匹配；无法转换时在模块内做 suffix 兜底。

**Rationale:**

- 当前 diffSpec key 来源于 git diff 的 `newPath`（形如 `<module>/src/main/java/com/foo/Bar`），与 JaCoCo class name（`com/foo/Bar`）不一致。
- 先按模块隔离可避免多模块同名类污染；归一化后再精确匹配可最大化兼容历史 diffSpec。

**Implementation approach:**

- 生成某 module 报告时，仅处理 key 以 `<module>/` 开头的 diffSpec 条目。
- 对每个 key：
  - 若包含 `src/main/java/`：取其后的子串作为 class name（并将 `\\` 与 `/` 都视为分隔符）。
  - 否则在模块内兜底：当 `key` 以 class name 结尾时视为命中（suffix match）。
- class name 命中后再应用 `true`（整类）或方法列表过滤。

### 5) 多模块输出与合并保持兼容

**Decision:** 生成报告时保持现有模块循环逻辑：

- 单模块：生成 `jacocoreport/index.html`。
- 多模块：每个 module 生成 `jacocoreport/<module>/index.html`，再用现有 `MergeReportHtml.mergeHtml` 合并。

**Rationale:**

尽量减少前端/链接/资源路径的变化，降低上线风险。

## Risks / Trade-offs

- [方法行区间与 HTML 源码标注不一致] → 以 JaCoCo 的 `IMethodCoverage.getFirstLine/getLastLine` 为基础，区间外 line 视为 0/0，并在关键场景回归比对旧报告。
- [diffSpec key 路径与 class/package 映射不一致] → 统一规范化：以 class 的源文件相对路径（或 VM 名称转换）与 diffSpec 对齐，避免 Windows 路径分隔符导致不匹配。
- [性能开销上升] → 仍然只分析目标 module 的 classfiles（保持现有范围），过滤在内存中执行；并复用现有报告生成超时时间控制。
- [依赖引入导致 jar 体积变大] → 仅新增 `org.jacoco.report`，版本锁定 0.8.14，与现有 `org.jacoco.core` 一致。

## Migration Plan

1. 增加 `org.jacoco:org.jacoco.report:${jacoco.version}` 依赖。
2. 实现进程内报告生成器，并在 `CodeCovServiceImpl` 两条路径（env/local）替换 report 阶段的 CLI 调用。
3. 保留 dump 阶段继续使用 `org.jacoco.cli-0.8.14-nodeps.jar dump`。
4. 删除 `cov.paths.jacocoCliDiffJar` 配置与 `org.jacoco.cli-1.0.2-SNAPSHOT-nodeps.jar` 文件。
5. 上线后如遇紧急问题，临时将增量模式降级为全量生成（diffSpec 为空时路径不变），作为回滚兜底。

## Open Questions

None.
