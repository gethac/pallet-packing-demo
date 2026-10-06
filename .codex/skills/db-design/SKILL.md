---
name: db-design
description: 维护托盘相关表结构、ER 图、DDL、迁移脚本，或核对实体/Mapper 与表结构是否一致时使用。
---

# db-design
用途：从生产实体与 Mapper 反推并维护托盘表结构（ER、字段字典、DDL）。
输入：PalletStandard / SaleOrderPalletPlan / SaleOrderPalletGroup / SaleOrderPalletItem 实体的 @TableField，
      以及 Mapper 的 resultMap、insertBatch 列清单。
规则：
1. 列名、列集合只能取自实体与 Mapper，不得新增或改名；类型按 jdbcType 映射。
2. 四张表都继承 ExtDataEntity：TENANT_ID、ORG_ID、CREATED_BY、CREATED_BY_ORG、CREATED_TIME、
   UPDATED_BY、UPDATED_BY_ORG、UPDATED_TIME、REVISION、DELETED、FIELD0~FIELD9。
3. 查询统一带 DELETED = 0；数据权限用 CREATED_BY_ORG in authOrg。
4. 托盘结果按 (ORDER_ID, BATCH_ID) 整体替换（物理删除后插入），BATCH_ID 空串表示未分批。
5. 箱子明细不建表，存 BOX_LAYOUT JSON；产品构成存 PRODUCT_COMPOSITION JSON。
6. 迁移脚本面向 MySQL 5.7，可重复执行，大表加列优先在线加列。
输出：docs/design/ddl-pallet.sql、er-diagram.png、字段字典；附与演示表的对应关系。
