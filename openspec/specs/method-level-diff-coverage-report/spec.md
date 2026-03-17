# Method-level Diff Coverage Report

## Purpose
Define requirements for generating method-level incremental (diff-based) coverage reports using in-process JaCoCo APIs.
## Requirements
### Requirement: 仅使用 JaCoCo 0.8.14 生成增量报告
系统 MUST 在进程内使用 JaCoCo 0.8.14 的 core/report API 生成 HTML 与 XML 报告产物，并 MUST 不依赖 `org.jacoco.cli-1.0.2-SNAPSHOT-nodeps.jar` 及其扩展参数。

#### Scenario: 生成增量报告不需要 SNAPSHOT CLI
- **WHEN** 触发增量覆盖率报告生成且 diffSpec 不为空
- **THEN** 系统使用进程内报告生成器完成报告输出

#### Scenario: 同时输出 HTML 与 XML
- **WHEN** 报告生成成功
- **THEN** 报告目录中同时存在 HTML 入口页与 XML 报告文件

### Requirement: diffSpec 解析与规则一致
系统 SHALL 解析现有 diffSpec 字符串格式（来源于 diffMethod 字段），并 SHALL 将其映射为“类级纳入”或“方法级纳入”的过滤规则。

#### Scenario: diffSpec key 与 class name 对齐
- **WHEN** diffSpec 的 key 形如 `<module>/src/main/java/com/foo/Bar`
- **THEN** 系统 SHALL 归一化并命中 JaCoCo class name `com/foo/Bar`

#### Scenario: diffSpec 包含整类纳入
- **WHEN** diffSpec 中某个类的 value 为 `true`
- **THEN** 该类所有方法的覆盖率 SHALL 被纳入报告

#### Scenario: diffSpec 包含方法列表
- **WHEN** diffSpec 中某个类的 value 为以 `#` 分隔的方法签名列表
- **THEN** 仅列表中匹配的方法覆盖率 SHALL 被纳入报告

### Requirement: 方法签名匹配兼容现有生成规则
系统 MUST 将 JaCoCo 方法描述符转换为与现有 diffSpec 一致的签名形式 `name,ParamTypeSimpleName,...`，并 MUST 按现有规则跳过 primitive 参数类型，以保证历史 diffMethod 的匹配语义不变。

#### Scenario: 兼容 MethodParser 的参数规则
- **WHEN** diffSpec 中的方法签名来自 `MethodParser` 生成
- **THEN** 报告生成时的方法匹配 MUST 能命中对应的 JaCoCo 方法覆盖率条目

#### Scenario: 重载冲突不漏算
- **WHEN** 某个 diffSpec 方法签名因跳过 primitive 参数而命中多个 JaCoCo 方法
- **THEN** 这些命中的方法 MUST 全部纳入增量过滤结果

### Requirement: 覆盖率统计口径与过滤结果一致
系统 MUST 基于过滤后的方法集合重新计算并展示 line/branch 覆盖率，统计结果 MUST 仅反映被纳入的方法范围内的行与分支。

#### Scenario: 指标计算不依赖 HTML 解析
- **WHEN** 报告生成完成并需要写入 line/branch 覆盖率指标
- **THEN** 系统 MUST 基于结构化数据（覆盖率模型或 XML）计算指标

#### Scenario: 过滤后统计不包含非增量方法
- **WHEN** 某类仅包含部分方法在 diffSpec 中
- **THEN** 报告汇总与单文件汇总的 line/branch 统计 MUST 不包含未纳入方法对应的行与分支

### Requirement: 报告产物路径与多模块合并保持兼容
系统 SHALL 在保持现有报告输出目录结构与访问 URL 形式兼容的前提下，持续输出可被累积覆盖流程消费的 XML 报告产物，并在多模块场景继续产出合并结果。

#### Scenario: 报告兼容且可复用
- **WHEN** 生成方法级增量覆盖报告
- **THEN** 原有报告访问路径保持不变，且同批产物可直接供快照生成器解析

#### Scenario: 多模块输出结构不变
- **WHEN** 请求指定 subModule 或模块列表
- **THEN** 系统在每个模块目录生成 HTML，并将合并后的入口页输出为 `index.html`

#### Scenario: 产物缺失触发失败
- **WHEN** 需要消费 XML 但产物不存在或不可读
- **THEN** 系统将对应 run 标记为失败并记录明确错误原因

### Requirement: 失败行为可定位且不产生误导结果
系统 MUST 在 diffSpec 解析失败、方法匹配规则异常、或报告生成失败时将任务置为失败并记录可定位的错误信息，且 MUST 不返回成功状态的报告链接。

#### Scenario: diffSpec 不可解析
- **WHEN** diffSpec 为空但报告类型要求增量，或 diffSpec 格式错误
- **THEN** 系统标记任务失败并返回明确错误信息

