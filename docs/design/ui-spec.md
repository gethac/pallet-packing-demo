# 销售订单托盘摆放 · UI 规范

> 依据：生产页面 `SaleOrderPalletContent.vue`、生产组件文档页 `package-pallet-preview`、项目规则 rules.md 中的 VTable 列宽规则（2026-09-10 更新），以及开发会话中确定的界面口径。

## 1 页面结构

| 区域 | 内容 | 控件 |
|---|---|---|
| 参数区 | 发货批次（多批次时显示）、托盘标准（下拉）、货物限重 / 货物限高（数字输入）、混托 / 盒箱混托 / 无盒混托（开关） | el-select、el-input-number、el-switch |
| 托盘清单 | 标题带“共 N 托”，列出托盘号、类型（single / mixed）、产品构成、层数、箱数、重量、高度；点击行切换预览 | VTable |
| 托盘预览 | 当前托盘的三维视图，可旋转、缩放、切换视角 | PackagePalletPreview |
| 产品装箱 | 每个产品的包装模式、外尺寸、单箱重、箱数 | 表格 |
| 完整产品清单 | 订单产品与装托结果对照，可导出 | 表格 + 导出按钮 |
| 空态 | 无数据时显示“暂无数据” | — |

## 2 规则

| 编号 | 规则 | 来源 |
|---|---|---|
| UI-01 布局 | 页签内上方参数区，下方左右两栏：左侧托盘清单与产品装箱，右侧三维预览 | 生产页面 SaleOrderPalletContent.vue |
| UI-02 控件 | 表单用 Element Plus；列表统一用 VTable；开关用 el-switch，默认值与业务规则一致（混托开、盒箱混托关、无盒混托关） | 生产页面 |
| UI-03 列宽 | VTable 列只设 width 控制初始宽度，不再单独设 minWidth；未设置时默认最小宽 10px | rules.md（2026-09-10 更新） |
| UI-04 组件边界 | 三维预览组件只负责展示、切换视角和产品分色，不处理计算、保存和接口请求；页面负责取数与参数 | 组件文档页 package-pallet-preview |
| UI-05 包装形态 | packageMode 为 carton 时绘制纸箱顶盖折页和封箱胶带；盒装为彩盒无胶带；无盒为刹车盘模型 | 组件文档页、2026-08-28 提交 a94e310 |
| UI-06 状态同步 | 限重、限高输入的禁用状态（含 aria-disabled）与加载状态一致 | 2026-07-31 修复 |
| UI-07 文案 | 用户可见编号统一为 SKU，保留原有大小写（SKU No / SKU NO.）；导出列名同步 | 2026-09-10 会话 |
| UI-08 提示 | 空态显示“暂无数据”；超限重、超限高给出具体数值；参数变更后提示“请重新计算后再保存” | 需求规格第 7 章 |
| UI-09 自适应 | 预览组件监听容器尺寸变化，自动调整渲染器与相机，弹窗和页面可复用同一组件 | 组件文档页 |

## 3 PackagePalletPreview 接口

组件只通过 Props 驱动，没有插槽、事件和实例方法。

| 属性 | 说明 | 类型 |
|---|---|---|
| `palletLength / palletWidth` | 托盘长、宽（mm） | number | string |
| `palletHeight` | 托盘自身高度（mm），默认 0 | number | string |
| `palletItems` | 托盘实例列表，决定总览模式渲染哪些托盘 | array |
| `boxes` | 包装单元坐标与尺寸；packageMode=carton 时渲染纸箱 | array |
| `selectedPalletNo` | 指定后只聚焦渲染该托盘 | string | number |
| `selectedBoxKeys` | 需要高亮边线的箱体 key | array |
| `compact / showLabels` | 紧凑模式 / 是否显示托盘标签 | boolean |
| `showCartonTape` | 是否显示纸箱封箱胶带 | boolean |
| `viewRotation / rotationScopeKey / palletRotationScopes` | 默认环绕视角及按托盘组保存视角 | number / string / object |
| `showRotateControls` | 是否显示视角切换按钮 | boolean |

## 4 演示仓库补充

- 预览背景 #eef2f6；工具栏旋转 ±90°，缩放步进 0.12。
- 增强版预览（PackagePalletPreviewEnhanced）：动画播放、预设视角、分层 / 爆炸、规格图例（画布外）、HUD（含最小支撑率）。
- 盒为彩盒无胶带；纸箱为牛皮纸 + 胶带；可按规格着色。
