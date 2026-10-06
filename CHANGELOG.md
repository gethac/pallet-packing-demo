# Changelog

本项目为从生产 MES「销售订单托盘摆放」抽取的**模拟最小实现**（Mock 数据 + 可运行引擎/三维），用于 AI Coding 作业演示。

## [1.0.0] — 2026-10-06

### 亮点
- 装托引擎：分组满托、尾托合并、混托三开关
- 支撑约束：默认底面支撑率 ≥ 80%、重心落在支撑区、默认无超边；独立 `PackingSupportValidator`
- 盒箱混装 Mock（ORDER-MIX-PACK / ORDER-MIX-SIZE）与规格着色图例
- 增强三维：动画、预设视角、分层/爆炸、点选信息卡、HUD（含最小支撑率）
- GitHub Actions：`mvn test` + `npm run build`
- 设计与证据归档：`docs/design/`、`docs/evidence/`；Codex：`AGENTS.md`、`.codex/skills/`

### Benchmark（以 `docs/benchmark-results.json` 为准）
| Suite | Engine | FirstFit | Naive | Eng 面积利用率 | FF 面积利用率 |
|-------|-------:|---------:|------:|---------------:|--------------:|
| single-12x400 | 1 | 1 | 2 | 0.7500 | 0.7500 |
| weight-tight-36 | 3 | 3 | 6 | 0.7500 | 0.7500 |
| mixed-sku-sizes | 2 | 3 | 6 | 0.7682 | 0.3553 |
| tail-merge-like | 1 | 1 | 3 | 1.0000 | 0.8333 |
| bulk-96 | 4 | 4 | 24 | 0.6942 | 0.6942 |
| height-tight-40 | 4 | 4 | 20 | 0.6875 | 0.6875 |

Engine / FirstFit / Naive 最小支撑率均为 1.0。

## 迭代摘要

### v11 — 支撑约束与悬空修复
人工审核发现混装上层悬空/半边支撑。引入 `PackingConstraints` + `PackingSupportValidator`，层铺满优先，对比基线同步过支撑校验。

| 修复前（v10） | 修复后（v11） |
|---------------|---------------|
| ![before](docs/history/ui-v10-mix-pack-iso-closeup.png) | ![after](docs/ui-v11-mix-pack-iso-closeup.png) |

### v10 — 完整态截图与图例外置
动画中截图半透明问题修复；图例移出画布；规格色加强。历史图见 `docs/history/ui-v10-*`。

### v9 — 盒箱混装可见性
新增混装 Mock、规格着色、图例与 HUD。历史图见 `docs/history/ui-v9-*`。

### v7–v8 — 基线与三维底座
主对比改为分层 First-Fit（Naive 作下限）；增强版回到原版渲染底座再叠加交互。

### 更早
SpotBugs 防御拷贝（`3b7bb76`）；原版 PackagePalletPreview 移植；CI workflow。
