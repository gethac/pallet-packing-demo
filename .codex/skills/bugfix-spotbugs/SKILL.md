---
name: bugfix-spotbugs
description: 根据 SpotBugs 告警、测试失败或核查发现的缺陷做批量修复时使用。
---

# bugfix-spotbugs
用途：根据 SpotBugs / 人工审查批量修复。
输入：告警列表或悬空/重叠截图。
输出：最小 diff（如 EI_EXPOSE_REP 防御拷贝、支撑校验补丁）+ 回归命令。
约束：不扩大重构面；修复后 mvn test / SpotBugs 再跑。
