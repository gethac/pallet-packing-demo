# AGENTS.md — 托盘摆放 Demo 协作约定

本仓库是从生产 MES「销售订单托盘摆放」抽取的可演示工程。

> 本文件和 `.codex/skills/` 是 2026-10-06 作业整理时写成的，内容来自生产开发中已经在用的约定：
> 项目 `rules.md`（提交规范、VTable 规则）、用户全局 `~/.codex/AGENTS.md`（2026-08-13，CodeGraph 优先）、
> 以及会话里固定使用的提示词“提交代码 / 最小提交原则 不要都提交成一个 按照功能拆分”（2026-08-10 起）。

与 Codex 协作时必须遵守：

## 边界
- 只改托盘装载链路：`PalletPackingEngine` / Assembler / Controller / 前端预览组件。
- 禁止臆造生产接口、表名、业务字段；Demo 已有的 Mock 订单与表结构以代码为准。
- 三维：原版 `PackagePalletPreview` 只适配不重写；交互增强放在 `PackagePalletPreviewEnhanced`。

## 质量门禁
- `cd backend && mvn test` 必须通过（含混装单测、`PackingSupportValidatorTest`、benchmark）。
- `cd frontend && npm run build` 必须通过。
- 装托结果须通过支撑校验：默认最小支撑率 0.80、默认不允许超边。

## Skills
见 `.codex/skills/`：`requirement-spec`、`prototype-ui`、`db-design`、`pallet-engine-dev`、`bugfix-spotbugs`、`minimal-commit`。

## 提交
- 按功能点拆分提交，中文 conventional commit（feat/fix/perf/style/test/docs/chore）。
- 共享文件按补丁块暂存，提交前跑定向测试、构建和 `git diff --check`。
- 不改写已有历史，不擅自推送。
