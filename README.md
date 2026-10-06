# 托盘摆放 Demo（pallet-packing-demo）

从生产 MES「销售订单 · 托盘摆放」功能中抽取的**可独立运行最小实现（模拟版）**，用于 AI CODING 作业的 git 地址与演示。

> 本仓库**不是**完整业务系统拷贝：已剥离公司内部框架、私有包名、客户数据与内网配置；订单/包装上游使用内存模拟数据；装载引擎为保留核心语义的简化实现。

## 功能说明

- 选择托盘标准、货物限重/限高、混托开关
- 调用装载引擎计算托盘方案（分组、实例、盒子坐标）
- 方案落库到 H2（plan / group / item，`BOX_LAYOUT` JSON）
- 按托盘号预览三维摆放（Vue3 + Three.js）
- 输入指纹复用、订单级保存锁（简化版）

## 目录结构

```
pallet-packing-demo/
├── README.md
├── .gitignore
├── backend/                 # Spring Boot 3 + JPA + H2
│   ├── pom.xml
│   └── src/main/java/com/example/pallet/
│       ├── engine/          # PalletPackingEngine 等
│       ├── entity/          # Plan/Group/Item/Standard
│       ├── service/         # 计算、保存、Mock 订单
│       └── controller/      # REST API
└── frontend/                # Vue3 + Vite + Three.js
    └── src/
        ├── App.vue
        └── components/PalletPreview.vue
```

## 启动方式

### 1. 后端

```bash
cd backend
mvn spring-boot:run
```

默认端口：`http://localhost:8080`  
H2 控制台：`http://localhost:8080/h2-console`（JDBC URL: `jdbc:h2:mem:pallet`）

### 2. 前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 Vite 提示的地址（默认 `http://localhost:5173`），API 经代理转发到 8080。

### 3. 单元测试

```bash
cd backend
mvn test
```

## 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/api/pallet-standard/listEnabled` | 启用中的托盘标准 |
| GET | `/api/sale-order/pallet/{orderId}` | 查询计算上下文/已存方案 |
| GET | `/api/sale-order/pallet/{orderId}/view` | 只读查看已保存方案 |
| GET | `/api/sale-order/pallet/{orderId}/preview?palletNo=` | 单托盘盒子布局 |
| POST | `/api/sale-order/pallet/{orderId}/calculate` | 计算并保存 |
| PUT | `/api/sale-order/pallet/{orderId}/draft` | 保存草稿（同计算保存） |

示例订单号：`ORDER-SINGLE`、`ORDER-MIX`、`ORDER-TAIL`、`ORDER-HEAVY`、`ORDER-TALL`、`ORDER-EMPTY`。

## 保留 / 简化 / 模拟对照

| 类别 | 内容 |
|------|------|
| 保留 | 托盘标准、方案/分组/实例模型、BOX_LAYOUT JSON、限重限高、混托三开关语义、利用率、预览坐标、指纹与保存锁概念、REST 形态 |
| 简化 | 装载引擎（去除生产版 Excel 布局/复杂分区等），锁为进程内 ReentrantLock，指纹为 SHA-256 字符串 |
| 模拟 | 销售订单与包装结果（`MockOrderDataService`），无真实客户/物料主数据，H2 内存库 |

## 说明

- 包名使用通用名 `com.example.pallet`，不包含公司域名或内部组件。
- 不包含密码、内网地址、真实客户名或生产连接串。
- 请勿将生产库数据或 `.class` 编译产物提交到本仓库。
