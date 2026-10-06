# Skill: db-design
用途：从实体与 Mapper 反推逻辑/物理模型。
输入：Plan/Group/Item/Standard 字段与 BOX_LAYOUT JSON 结构。
输出：ER、字段字典、索引与删除策略。
约束：盒子坐标不建明细表；batchId 空串语义写清。
