# 托盘摆放 · 需求规格说明（归档）

## 范围
销售订单流程中「托盘摆放」：包装结果 → 托盘方案计算 → 三维预览 → 草稿保存。
不含运费、评审、订单主数据维护。

## 用户故事
1. 作为计划员，选择发货批次与托盘标准，填写限重/限高与混托开关，一键计算托盘方案。
2. 作为计划员，在三维中核对盒/箱摆放，区分规格与包装方式，确认无悬空、无超边后保存草稿。

## 验收（Given/When/Then）
- Given 有效包装数据与托盘标准，When 计算，Then 返回托数/盒数/布局坐标且通过支撑校验（默认支撑率≥0.80）。
- Given 超限重或超限高输入，When 计算，Then 返回明确业务错误，不落库。
- Given 开启盒箱混托，When 计算 ORDER-MIX-PACK，Then 同托可含 box+carton 且多层稳定。

## 混托三开关
allowMixedPallet / allowMixedPackagePallet / allowMixedNoBoxPallet（与生产 normalizeAllowMixed*Flag 语义一致）。

## 非功能
订单级保存锁；CALCULATION_INPUT_HASH 跳过无变化重算；CI：mvn test + npm build。
