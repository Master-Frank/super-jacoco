## MODIFIED Requirements

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
