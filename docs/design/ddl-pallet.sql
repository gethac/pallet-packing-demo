-- =====================================================================
-- 销售订单托盘摆放 · 生产库表结构（MySQL 5.7，InnoDB，utf8mb4）
-- 依据：生产实体 PalletStandard / SaleOrderPalletPlan / SaleOrderPalletGroup /
--       SaleOrderPalletItem（均继承 ExtDataEntity）及对应 Mapper XML。
-- 说明：
--   1. 列名与列集合逐一取自实体 @TableField 与 Mapper resultMap / insertBatch。
--   2. 数据类型按 Mapper 中的 jdbcType 映射：VARCHAR→VARCHAR，DOUBLE→DOUBLE，
--      DECIMAL→DECIMAL，INTEGER→INT，TIMESTAMP→DATETIME，LONGVARCHAR→LONGTEXT。
--      长度、精度为设计取值，上线以生产迁移脚本为准。
--   3. 公共字段来自 ExtDataEntity：租户、组织、审计、乐观锁、逻辑删除、10 个扩展字段。
--   4. 托盘结果（plan/group/item）按 (ORDER_ID, BATCH_ID) 整体替换：先物理删除
--      （deleteByOrderIdAndBatchId / deletePhysicalByPlanId），再插入；并发由
--      SaleOrderPalletSaveLock（MySQL GET_LOCK，锁名含租户与订单+批次 MD5）串行化。
--      所以唯一性由应用层保证，表上只建查询索引。
--   5. 箱子级明细（SaleOrderPalletBox）不单独建表，序列化为 JSON 存于
--      mes_sale_order_pallet_item.BOX_LAYOUT；托盘内产品构成存于 PRODUCT_COMPOSITION。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 托盘标准（基础资料）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_pallet_standard (
  ID              VARCHAR(64)  NOT NULL COMMENT '主键',
  CODE            VARCHAR(64)  NOT NULL COMMENT '托盘标准编码（规格覆盖欧标、美标、国标）',
  LENGTH          DOUBLE       NOT NULL COMMENT '托盘长度(mm)',
  WIDTH           DOUBLE       NOT NULL COMMENT '托盘宽度(mm)',
  ENABLED         VARCHAR(1)   NOT NULL DEFAULT '1' COMMENT '是否启用：1 启用，0 停用（listEnabled 只取 1）',
  TENANT_ID       VARCHAR(64)  NULL COMMENT '租户',
  ORG_ID          VARCHAR(64)  NULL COMMENT '所属组织',
  CREATED_BY      VARCHAR(64)  NULL COMMENT '创建人',
  CREATED_BY_ORG  VARCHAR(64)  NULL COMMENT '创建人组织（分页按 authOrg 过滤）',
  CREATED_TIME    DATETIME     NULL COMMENT '创建时间',
  UPDATED_BY      VARCHAR(64)  NULL COMMENT '修改人',
  UPDATED_BY_ORG  VARCHAR(64)  NULL COMMENT '修改人组织',
  UPDATED_TIME    DATETIME     NULL COMMENT '修改时间',
  REVISION        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  DELETED         INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 删除',
  FIELD0 VARCHAR(255) NULL, FIELD1 VARCHAR(255) NULL, FIELD2 VARCHAR(255) NULL,
  FIELD3 VARCHAR(255) NULL, FIELD4 VARCHAR(255) NULL, FIELD5 VARCHAR(255) NULL,
  FIELD6 VARCHAR(255) NULL, FIELD7 VARCHAR(255) NULL, FIELD8 VARCHAR(255) NULL,
  FIELD9 VARCHAR(255) NULL COMMENT 'FIELD0~FIELD9：平台扩展字段',
  PRIMARY KEY (ID),
  KEY idx_pallet_std_enabled (DELETED, ENABLED, LENGTH, WIDTH)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='托盘标准';

-- ---------------------------------------------------------------------
-- 2. 托盘方案（每个订单每个批次一条）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_sale_order_pallet_plan (
  ID                         VARCHAR(64)   NOT NULL COMMENT '主键',
  ORDER_ID                   VARCHAR(64)   NOT NULL COMMENT '销售订单ID',
  BATCH_ID                   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '销售批次ID；空串表示未分批（隐式单批次）',
  PALLET_STANDARD_ID         VARCHAR(64)   NULL COMMENT '托盘标准ID（计算时快照）',
  PALLET_STANDARD_CODE       VARCHAR(64)   NULL COMMENT '托盘标准编码（快照）',
  PALLET_LENGTH              DOUBLE        NULL COMMENT '托盘长度(mm)（快照）',
  PALLET_WIDTH               DOUBLE        NULL COMMENT '托盘宽度(mm)（快照）',
  WEIGHT_LIMIT               DECIMAL(12,3) NULL COMMENT '货物重量限制(kg)，必须 >0',
  CARGO_HEIGHT_LIMIT         DECIMAL(12,3) NULL COMMENT '货物高度限制(mm)，必须 >0',
  TOTAL_PALLET_COUNT         INT           NULL COMMENT '托盘总数',
  TOTAL_BOX_COUNT            INT           NULL COMMENT '箱（盒）总数',
  PACKAGE_MODE               VARCHAR(16)   NULL COMMENT '包装模式：box 盒装 / carton 纸箱 / virtual 无盒 / mixed 混合',
  TOTAL_PRODUCT_WEIGHT       DOUBLE        NULL COMMENT '货物总重(kg)',
  HAS_MIXED_GROUP            VARCHAR(1)    NULL COMMENT '是否存在混托分组：1/0',
  ALLOW_MIXED_PALLET         VARCHAR(1)    NULL COMMENT '允许混托（不同产品同托），默认 1',
  ALLOW_MIXED_PACKAGE_PALLET VARCHAR(1)    NULL COMMENT '允许盒箱混托，默认 0',
  ALLOW_MIXED_NO_BOX_PALLET  VARCHAR(1)    NULL COMMENT '允许无盒产品混托，默认 0',
  AVG_AREA_UTILIZATION       DOUBLE        NULL COMMENT '平均面积利用率(0~1)',
  AVG_HEIGHT_UTILIZATION     DOUBLE        NULL COMMENT '平均高度利用率(0~1)',
  CALCULATED_TIME            DATETIME      NULL COMMENT '计算时间',
  CALCULATION_INPUT_HASH     VARCHAR(128)  NULL COMMENT '计算输入指纹；输入不变时复用已存结果',
  TENANT_ID       VARCHAR(64)  NULL COMMENT '租户',
  ORG_ID          VARCHAR(64)  NULL COMMENT '所属组织',
  CREATED_BY      VARCHAR(64)  NULL COMMENT '创建人',
  CREATED_BY_ORG  VARCHAR(64)  NULL COMMENT '创建人组织',
  CREATED_TIME    DATETIME     NULL COMMENT '创建时间',
  UPDATED_BY      VARCHAR(64)  NULL COMMENT '修改人',
  UPDATED_BY_ORG  VARCHAR(64)  NULL COMMENT '修改人组织',
  UPDATED_TIME    DATETIME     NULL COMMENT '修改时间',
  REVISION        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  DELETED         INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  FIELD0 VARCHAR(255) NULL, FIELD1 VARCHAR(255) NULL, FIELD2 VARCHAR(255) NULL,
  FIELD3 VARCHAR(255) NULL, FIELD4 VARCHAR(255) NULL, FIELD5 VARCHAR(255) NULL,
  FIELD6 VARCHAR(255) NULL, FIELD7 VARCHAR(255) NULL, FIELD8 VARCHAR(255) NULL,
  FIELD9 VARCHAR(255) NULL,
  PRIMARY KEY (ID),
  KEY idx_pallet_plan_order_batch (ORDER_ID, BATCH_ID, DELETED)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单托盘方案';

-- ---------------------------------------------------------------------
-- 3. 托盘分组（相同产品组合的托盘归为一组：single 单品 / mixed 混托）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_sale_order_pallet_group (
  ID                 VARCHAR(64)   NOT NULL COMMENT '主键',
  PLAN_ID            VARCHAR(64)   NOT NULL COMMENT '托盘方案ID',
  ORDER_ID           VARCHAR(64)   NOT NULL COMMENT '销售订单ID（冗余，便于按订单清理）',
  GROUP_NO           VARCHAR(32)   NULL COMMENT '分组号',
  GROUP_TYPE         VARCHAR(16)   NULL COMMENT '分组类型：single / mixed',
  PRODUCT_KEYS       VARCHAR(2000) NULL COMMENT '组内产品键，逗号分隔',
  PALLET_COUNT       INT           NULL COMMENT '托盘数',
  BOX_COUNT          INT           NULL COMMENT '箱（盒）数',
  TOTAL_WEIGHT       DOUBLE        NULL COMMENT '总重(kg)',
  AREA_UTILIZATION   DOUBLE        NULL COMMENT '面积利用率',
  HEIGHT_UTILIZATION DOUBLE        NULL COMMENT '高度利用率',
  SORT               INT           NULL COMMENT '排序',
  REMARK             VARCHAR(500)  NULL COMMENT '备注',
  TENANT_ID       VARCHAR(64)  NULL, ORG_ID VARCHAR(64) NULL,
  CREATED_BY      VARCHAR(64)  NULL, CREATED_BY_ORG VARCHAR(64) NULL, CREATED_TIME DATETIME NULL,
  UPDATED_BY      VARCHAR(64)  NULL, UPDATED_BY_ORG VARCHAR(64) NULL, UPDATED_TIME DATETIME NULL,
  REVISION        INT          NOT NULL DEFAULT 0,
  DELETED         INT          NOT NULL DEFAULT 0,
  FIELD0 VARCHAR(255) NULL, FIELD1 VARCHAR(255) NULL, FIELD2 VARCHAR(255) NULL,
  FIELD3 VARCHAR(255) NULL, FIELD4 VARCHAR(255) NULL, FIELD5 VARCHAR(255) NULL,
  FIELD6 VARCHAR(255) NULL, FIELD7 VARCHAR(255) NULL, FIELD8 VARCHAR(255) NULL,
  FIELD9 VARCHAR(255) NULL,
  PRIMARY KEY (ID),
  KEY idx_pallet_group_plan (PLAN_ID, DELETED, SORT),
  KEY idx_pallet_group_order (ORDER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单托盘分组';

-- ---------------------------------------------------------------------
-- 4. 托盘明细（每托一条，含箱子三维布局 JSON）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_sale_order_pallet_item (
  ID                  VARCHAR(64)   NOT NULL COMMENT '主键',
  PLAN_ID             VARCHAR(64)   NOT NULL COMMENT '托盘方案ID',
  GROUP_ID            VARCHAR(64)   NULL COMMENT '托盘分组ID',
  ORDER_ID            VARCHAR(64)   NOT NULL COMMENT '销售订单ID（冗余）',
  PALLET_NO           VARCHAR(32)   NULL COMMENT '托盘号',
  PRODUCT_KEYS        VARCHAR(2000) NULL COMMENT '本托产品键',
  PRODUCT_COMPOSITION TEXT          NULL COMMENT '产品构成 JSON：productKey/orderProductId/batchId/customerNo/internalNo/packageMode/boxCount',
  BOX_LAYOUT          LONGTEXT      NULL COMMENT '箱子布局 JSON（SaleOrderPalletBox 列表：位置、尺寸、旋转、层号、支撑来源）',
  LAYER_COUNT         INT           NULL COMMENT '层数',
  BOX_COUNT           INT           NULL COMMENT '箱（盒）数',
  TOTAL_WEIGHT        DOUBLE        NULL COMMENT '本托总重(kg)',
  TOTAL_HEIGHT        DOUBLE        NULL COMMENT '货物高度(mm)',
  AREA_UTILIZATION    DOUBLE        NULL COMMENT '面积利用率',
  HEIGHT_UTILIZATION  DOUBLE        NULL COMMENT '高度利用率',
  STABILITY_SCORE     DOUBLE        NULL COMMENT '稳定性评分(0~100)',
  SORT                INT           NULL COMMENT '排序',
  REMARK              VARCHAR(500)  NULL COMMENT '备注',
  TENANT_ID       VARCHAR(64)  NULL, ORG_ID VARCHAR(64) NULL,
  CREATED_BY      VARCHAR(64)  NULL, CREATED_BY_ORG VARCHAR(64) NULL, CREATED_TIME DATETIME NULL,
  UPDATED_BY      VARCHAR(64)  NULL, UPDATED_BY_ORG VARCHAR(64) NULL, UPDATED_TIME DATETIME NULL,
  REVISION        INT          NOT NULL DEFAULT 0,
  DELETED         INT          NOT NULL DEFAULT 0,
  FIELD0 VARCHAR(255) NULL, FIELD1 VARCHAR(255) NULL, FIELD2 VARCHAR(255) NULL,
  FIELD3 VARCHAR(255) NULL, FIELD4 VARCHAR(255) NULL, FIELD5 VARCHAR(255) NULL,
  FIELD6 VARCHAR(255) NULL, FIELD7 VARCHAR(255) NULL, FIELD8 VARCHAR(255) NULL,
  FIELD9 VARCHAR(255) NULL,
  PRIMARY KEY (ID),
  KEY idx_pallet_item_plan (PLAN_ID, DELETED, SORT),
  KEY idx_pallet_item_plan_no (PLAN_ID, PALLET_NO),
  KEY idx_pallet_item_order (ORDER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单托盘明细';

-- ---------------------------------------------------------------------
-- 演示仓库（H2 + JPA）与生产表的对应：
--   pallet_standard ↔ mes_pallet_standard
--   pallet_plan     ↔ mes_sale_order_pallet_plan
--   pallet_group    ↔ mes_sale_order_pallet_group
--   pallet_item     ↔ mes_sale_order_pallet_item
--   演示表保留全部业务列（分组、明细的 sortNo ↔ SORT），省略租户/组织/审计/REVISION/DELETED/
--   FIELD0~9 以及分组、明细的 REMARK；结构由 JPA ddl-auto 生成，仅用于本地演示。
-- ---------------------------------------------------------------------
