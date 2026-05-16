# 家庭账本 💰

> 一个本地运行的家庭收支管理系统，用于记录、分析和追踪家庭财务。

## 功能

### 📝 记账
- **记一笔** — 快速录入收支，支持选择分类、填写备注
- **自动记账** — 固定收支模板（房贷、公积金、会员等）每天15:00自动写入
- **CSV导入** — 支持随手记 / 喵喵记账导出数据批量导入，含分类映射

### 📊 统计与分析
- **仪表盘** — 本月收支概览、分类占比、近期交易
- **统计页** — 按月/按分类统计，饼图展示，支持下钻到明细
- **年度对比** — 历年收支汇总，收支流水趋势

### 📋 交易管理
- **交易列表** — 按日期/分类/金额/关键词搜索筛选
- **金额筛选** — 支持正负数搜索（输入 -5000 查大额支出）
- **分类管理** — 二级分类体系，支持启用/停用

### 💳 现金位置跟踪
- 每笔交易有 `cash_settled` 标记，自动追踪是否已计入现金
- 爸爸的收支 → 更新爸爸的招行余额
- 妈妈的收支 → 更新妈妈的个人储蓄
- 补记历史交易也能被正确捡到，不会遗漏

### ⏰ 定时任务
| 任务 | 时间 | 说明 |
|:----|:---:|:----|
| 自动记账 | 每天 15:00 | 扫描到期模板，写入固定收支 |
| 现金位置更新 | 每天 15:00 | 结算未标记交易，更新家庭资产文件 |
| 月度总结 | 每月1日 9:00 | 上月收支分析，推送微信 |
| 年度总结 | 每年1月1日 10:00 | 上年度收支分析，推送微信 |

## 技术栈

| 层 | 技术 |
|:---|:---|
| 后端 | Java 17, Spring Boot 3, MyBatis-Plus 3.5 |
| 前端 | Vue 3, Element Plus, Vite, ECharts |
| 数据库 | MySQL 8.0+（本地，127.0.0.1） |
| ORM | MyBatis-Plus（自动字段映射，下划线→驼峰） |
| 部署 | `java -jar` 后端 + Vite dev server 前端 |

## 项目结构

```
family-ledger/
├── backend/                     # Spring Boot 后端
│   └── src/main/java/com/familyledger/
│       ├── controller/          # API 控制器
│       │   ├── TransactionController.java
│       │   ├── StatsController.java
│       │   ├── CategoryController.java
│       │   ├── UserController.java
│       │   └── ProjectController.java
│       ├── service/             # 业务逻辑层
│       │   ├── TransactionService.java
│       │   ├── CategoryService.java
│       │   ├── UserService.java
│       │   └── ProjectService.java
│       ├── entity/              # 数据实体
│       │   ├── Transaction.java
│       │   ├── Category.java
│       │   ├── User.java
│       │   ├── Project.java
│       │   └── AutoTransaction.java
│       └── mapper/              # MyBatis-Plus Mapper
├── frontend/                    # Vue 3 前端
│   └── src/
│       ├── views/               # 页面组件
│       │   ├── DashboardPage.vue
│       │   ├── AddPage.vue
│       │   ├── TransactionListPage.vue
│       │   ├── StatsPage.vue
│       │   └── SettingsPage.vue
│       └── router/              # 路由配置
├── scripts/                     # 运维脚本
│   ├── daily_ledger_pipeline.sh # 每日自动记账流水线
│   ├── auto_ledger.py           # 固定收支模板扫描与写入
│   └── update_cash_position.py  # 现金位置结算与更新
└── docs/                        # 项目文档
```

## 快速开始

```bash
# 1. 启动 MySQL
sudo systemctl start mysql

# 2. 启动后端
cd backend
mvn spring-boot:run

# 3. 启动前端
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173` 即可使用。

## 数据库

- **数据库名**: `family_ledger`
- **端口**: 3306（本地）
- **应用用户**: `hermes@127.0.0.1`（仅 SELECT/INSERT/UPDATE）
- **金额**: `DECIMAL(12,2)`，正=收入，负=支出
- **记录数**: 4,160+ 笔（覆盖 2024–2026 年）

## 分类体系

两级分类（大类→细分），共 26 个大类、96 个子类：

| 大类 | 示例子类 |
|:----|:--------|
| 餐饮 | 超市买菜、外卖/在外就餐、零食、饮料酒水 |
| 购物 | 美容、服饰、电子订阅、图书 |
| 交通 | 停车费、自行车、加油/充电 |
| 人情 | 孝敬长辈、红包 |
| 家居 | 水费、电费、燃气费、物业费 |
| 游戏 | 购买、充值 |
| 医疗 | 药品、挂号 |
| 育儿 | 奶粉尿裤、用品、疫苗 |
| 收入 | 工资、红包（入）、收款 |

## API 示例

```
GET  /api/transactions?page=1&size=20&startDate=2026-01-01
POST /api/transactions          { "userId":1, "categoryId":111, "amount":-9.90, "note":"鸡蛋" }
GET  /api/stats/monthly?year=2026
GET  /api/stats/category?startDate=2026-01-01
```

统一响应格式：`{"code":200, "message":"success", "data":...}`

## Hermes 集成

该系统深度集成 Hermes Agent，通过微信即可记账、查账、对账：

```chat
📱 记账 鸡蛋30个 9.9
📱 看看这个月花了多少钱
📱 查一下信用卡日报有没有漏记的
```

自动记账 cron 通过 `daily_ledger_pipeline.sh` 运行，每天 15:00 无代理执行（不消耗 token）。
