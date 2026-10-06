# AGENTS.md — 托盘摆放 Demo 协作约定

本仓库是从生产 MES「销售订单托盘摆放」抽取的可演示工程。与 Codex 协作时必须遵守：

## 边界
- 只改托盘装载链路：`PalletPackingEngine` / Assembler / Controller / 前端预览组件。
- 禁止臆造生产接口、表名、业务字段；Demo 已有的 Mock 订单与表结构以代码为准。
- 三维：原版 `PackagePalletPreview` 只适配不重写；交互增强放在 `PackagePalletPreviewEnhanced`。

## 质量门禁
- `cd backend && mvn test` 必须通过（含混装单测、`PackingSupportValidatorTest`、benchmark）。
- `cd frontend && npm run build` 必须通过。
- 装托结果须通过支撑校验：默认最小支撑率 0.80、默认不允许超边。

## Skills
见 `.codex/skills/`：`requirement-spec`、`prototype-ui`、`db-design`、`pallet-engine-dev`、`bugfix-spotbugs`。
