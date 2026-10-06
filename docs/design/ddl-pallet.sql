-- 托盘摆放逻辑表（Demo / 与生产对齐的字段语义）
-- mes_pallet_standard
CREATE TABLE mes_pallet_standard (
  id BIGINT PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  length_mm DOUBLE NOT NULL,
  width_mm DOUBLE NOT NULL,
  enabled TINYINT DEFAULT 1
);

-- mes_sale_order_pallet_plan
CREATE TABLE mes_sale_order_pallet_plan (
  id BIGINT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  batch_id VARCHAR(64) NOT NULL DEFAULT '',
  standard_id BIGINT,
  weight_limit DOUBLE,
  cargo_height_limit DOUBLE,
  allow_mixed_pallet TINYINT,
  allow_mixed_package_pallet TINYINT,
  allow_mixed_no_box_pallet TINYINT,
  total_pallet_count INT,
  total_box_count INT,
  avg_area_util DOUBLE,
  avg_height_util DOUBLE,
  calculation_input_hash VARCHAR(128),
  UNIQUE KEY uk_order_batch (order_id, batch_id)
);

-- mes_sale_order_pallet_group
CREATE TABLE mes_sale_order_pallet_group (
  id BIGINT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  group_type VARCHAR(32),
  product_keys VARCHAR(512),
  area_util DOUBLE
);

-- mes_sale_order_pallet_item
CREATE TABLE mes_sale_order_pallet_item (
  id BIGINT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  group_id BIGINT,
  pallet_no INT,
  layer_count INT,
  box_count INT,
  total_weight DOUBLE,
  box_layout JSON COMMENT '层号/坐标/外廓/旋转/packageMode...',
  stability_score DOUBLE
);
