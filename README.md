# 家庭账本系统 · 项目需求说明书

> 基于本地 MySQL + Java 后端的多人家庭记账系统，提供 API 和 Web 界面。

---

## 一、项目背景

用户当前使用「喵喵记账」App 记录日常开销，两年累计 3,030 笔数据，涉及 40+ 个分类。但存在几个痛点：

1. **单人记账**：无法多人共同记录，区分不清是汤圆爸爸还是汤圆妈妈的消费
2. **缺少母婴类别**：宝宝出生后，纸尿裤、奶粉、疫苗、婴儿用品等开支无对应分类
3. **无自动记帐**：每月固定支出（房贷、停车费、会员订阅等）需手动录入
4. **统计能力弱**：按人、按类别、按月/年交叉统计需导出CSV后手动分析
5. **数据归在第三方App**：无法离线备份和二次开发

本系统旨在替代喵喵记账，补上以上缺口。

---

## 二、技术栈

| 层 | 技术 | 说明 |
|:---|:-----|:-----|
| **数据库** | MySQL 8.x | 本地运行，单机部署 |
| **后端** | Java 17+ / Spring Boot 3.x | RESTful API |
| **ORM** | MyBatis-Plus 或 JPA | — |
| **前端** | 自选（Vue 3 / React / 纯 HTML+JS） | 优先推荐 Vue 3 + Element Plus |
| **构建** | Maven 或 Gradle | — |
| **部署** | 本地 JAR 包运行，WSL 或 Windows 均可 | — |

---

## 三、数据模型

### 3.1 用户表（user）

```sql
CREATE TABLE user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    nickname    VARCHAR(50) NOT NULL COMMENT '昵称',
    avatar_url  VARCHAR(255) COMMENT '头像',
    is_active   TINYINT(1) DEFAULT 1,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

**初始种子数据：** 汤圆爸爸 / 汤圆妈妈

### 3.2 分类体系（大类 + 细分分类）

> **⚠️ 以下分类表仅为初始种子数据。系统上线后支持随时增/删/改名/调级，不限制在本文档列出的范围。**

分类支持两级：**大类（一级）** → **细分（二级）**。二级分类继承一级的统计属性，同时可以做精细化分析。

```sql
CREATE TABLE category (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id   BIGINT DEFAULT NULL COMMENT '父分类ID，NULL=一级分类',
    name        VARCHAR(30) NOT NULL COMMENT '分类名称',
    icon        VARCHAR(50) COMMENT 'emoji图标',
    sort_order  INT DEFAULT 0 COMMENT '排序（同级内）',
    is_active   TINYINT(1) DEFAULT 1,
    FOREIGN KEY (parent_id) REFERENCES category(id)
);
```

**约束：**
- `parent_id IS NULL` = 一级分类（大类）
- `parent_id IS NOT NULL` = 二级分类（细分），必须指向一个有效的一级分类
- 交易记录关联到 **二级分类**（细分类），通过 `parent_id` 向上汇总到大类统计
- 不允许三级分类（够用为止）

**分类体系（基于历史数据 + 补充）：**

| 一级分类 | 说明 | 是否已有 | 备注 |
|:--------|:-----|:--------|:----|
| 🚗 交通 | 充电、加油、停车费、公交地铁、打车、自行车、保险、违章罚款 | ✅ 历史大户（974笔） | 8个二级分类 |
| 🍜 餐饮 | 外卖/在外就餐、超市买菜、零食、饮料酒水 | ✅ 历史大户（951笔，含原饮品） | 拆分为4个二级分类 |
| 🛍️ **购物** | **图书、服饰、美容、饰品、电子订阅、其他** | ⭐ **重组** | 吸收原书籍/服饰/美容，新增饰品/电子订阅 |
| 🏠 家庭→家居 | 电费、水费、煤气、话费、网费、房租、物业、家用电器、装修、家具 | ✅ 原家庭+通讯合并 | 10个二级分类，与原家庭+通讯+住房部分合并 |
| 👶 **母婴** | **医疗、玩具、衣服、用品、教育** | ⭐ **新增，核心** | 5个二级分类即可 |
| 🐱 宠物 | 猫粮、猫砂、医疗、玩具 | ✅（30笔） | — |
| 🏥 医疗 | 就诊、检查、药费、产检 | ✅ | 含体检 |
| 📱 数码 | 硬件、配件 | ✅ | 会员订阅已移至购物·电子订阅 |
| 🏪 超市 | 日用品集中采购 | ✅ | — |
| 🛒 日用 | 垃圾袋、清洁用品等 | ✅ | — |
| 🎮 游戏 | 充值、购买 | ✅ | — |
| 🎬 娱乐 | 电影、门票、玩乐 | ✅ | — |
| 🧧 人情 | 红包（出）、孝敬长辈、礼物、份子钱/随礼 | ✅ 原礼品重构 | 4个二级分类，均为支出方向 |
| 🏡 住房 | 房贷（月供9,612）、维修 | ✅ | 原家具移至家居 |
| 🚙 汽车 | 保险（年3,207）、保养、配件 | ✅ | — |
| 🎒 旅行 | 机票、酒店、门票 | ✅ | — |
| 📦 快递 | 邮费、物流 | ✅ | — |
| 🏋️ 运动 | 健身、装备 | ✅ | — |
| 💼 办公 | 工作相关支出 | ✅ | — |
| 🏦 金融 | 还款、借款利息、保险 | ✅ | — |
| 🧰 维修 | 家电/设备维修 | ✅ | — |
| 🎓 **教育** | **课程、培训、学习资料** | ⭐ **新增** | 给宝宝预留 |
| 📈 **投资** | **理财、基金、黄金、股票** | ⭐ **新增** | 和支出区分开 |
| 🍺 烟酒 | — | ✅（1笔） | 非主流保留 |
| 💰 收入 | 工资、分红、红包（入）、退款 | ✅ | 红包流入在此；红包流出归🧧人情 |
| 📦 **其他** | 未分类杂项 | ⭐ **新增** | 兜底 |

> **一级分类约30个，每个可带二级标签。** 母婴类建议二级分类：奶粉/纸尿裤/疫苗/婴儿服饰/玩具绘本/辅食/洗护/育儿器材/其他。

### 3.3 交易记录表（transaction）

```sql
CREATE TABLE transaction (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL COMMENT '记录人',
    category_id  BIGINT NOT NULL COMMENT '细分分类ID（二级）',
    project_id   BIGINT DEFAULT NULL COMMENT '所属项目（可选）',
    tags         VARCHAR(200) COMMENT '标签（逗号分隔）',
    amount       DECIMAL(12,2) NOT NULL COMMENT '金额（正=收入，负=支出）',
    trans_date   DATE NOT NULL COMMENT '交易日期',
    trans_time   TIME COMMENT '交易时间（可选）',
    note         VARCHAR(500) COMMENT '备注',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (category_id) REFERENCES category(id),
    FOREIGN KEY (project_id) REFERENCES project(id),
    INDEX idx_user_date (user_id, trans_date),
    INDEX idx_category (category_id),
    INDEX idx_project (project_id),
    INDEX idx_date (trans_date)
);
```

> 说明：`amount` 用正负号区分收支。消费记录为负值，收入为正值。也可以拆分成 `type` 字段（INCOME/EXPENSE）+ `amount`（绝对值）。

### 3.5 项目表（project）

> 用于标记跨越多个分类的**专项开支**（如欧洲蜜月、装修、生娃费用等），可对这些项目进行独立的穿透统计。

```sql
CREATE TABLE project (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT '项目名称',
    description VARCHAR(500) COMMENT '项目说明',
    start_date  DATE COMMENT '开始日期（统计用）',
    end_date    DATE COMMENT '结束日期（统计用）',
    budget      DECIMAL(12,2) COMMENT '预算（可选）',
    is_archived TINYINT(1) DEFAULT 0 COMMENT '是否归档',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

**项目 vs 分类的区别：**
- 分类是**横向的、固定的**（交通/餐饮/母婴…）
- 项目是**纵向的、临时的**（跨分类、有起止时间）
- 一笔交易可以 **同时属于一个分类和一个项目**
- 项目结束后可归档，历史数据保留

**典型项目：**
| 项目 | 涉及交易 | 跨哪些分类 |
|:----|:---------|:----------|
| 🇪🇺 **2025欧洲蜜月** | 机票、酒店、餐饮、购物、门票 | 交通/住宿/餐饮/购物/娱乐 |
| 🏠 **弘善装修** | 建材、人工、家具、家电 | 住房/购物 |
| 👶 **生娃费用** | 产检、住院、月子中心、婴儿用品 | 医疗/母婴 |
| 🚙 **买车** | 购车款、保险、上牌 | 汽车/金融 |

### 3.6 交易记录表（transaction）

修改 `transaction` 表，增加 `project_id` 字段：

```sql
CREATE TABLE auto_transaction (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id        BIGINT NOT NULL COMMENT '归属人',
    category_id    BIGINT NOT NULL COMMENT '分类',
    amount         DECIMAL(12,2) NOT NULL COMMENT '固定金额',
    note           VARCHAR(200) COMMENT '备注',
    -- 以下三种模式选一：
    mode           ENUM('monthly','weekly','date') NOT NULL,
    -- mode='monthly': 每月第N天（1-31），超月取末
    monthly_day    INT COMMENT 'mode=monthly时，每月的几号',
    -- mode='weekly': 每周周几（1-7=周一到周日）
    weekly_day     INT COMMENT 'mode=weekly时',
    -- mode='date': 每年某月某日
    yearly_month   INT COMMENT 'mode=date时，月份',
    yearly_day     INT COMMENT 'mode=date时，日期',
    next_run_date  DATE COMMENT '下次执行日期（系统计算）',
    is_active      TINYINT(1) DEFAULT 1,
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (category_id) REFERENCES category(id)
);
```

**自动交易的场景举例：**
| 场景 | mode | 值 | 金额 |
|:----|:----|:---|:---:|
| 房贷月供（9,612元，~每月20日） | monthly | 20日 | -9,612 |
| 华为会员（19元/月） | monthly | 某日 | -19 |
| 车险年费（3,207元，每年6月） | date | 6月某日 | -3,207 |
| 停车费（每周一） | weekly | 周一 | -50 |
| 综合所得月度计算 | monthly | 1日 | 自动 |

> 自动交易在指定日生成一条预填充的 transaction 草稿，用户可确认/修改/删除，**不自动产生实际记录**（避免忘记修改导致数据不准确）。也可提供"自动确认"开关。

---

## 四、API 设计

### 4.1 交易管理

| 方法 | 路径 | 说明 |
|:----|:----|:-----|
| GET | /api/transactions | 列表，支持分页+筛选 |
| GET | /api/transactions/{id} | 单条明细 |
| POST | /api/transactions | 新增交易 |
| PUT | /api/transactions/{id} | 修改交易 |
| DELETE | /api/transactions/{id} | 删除交易 |
| POST | /api/transactions/batch | 批量导入（支持CSV） |

**筛选参数：**
- `user_id` — 按人
- `category_id` — 按分类
- `start_date` / `end_date` — 时间范围
- `keyword` — 备注搜索
- `page` / `size` — 分页

### 4.2 统计接口

| 方法 | 路径 | 说明 |
|:----|:----|:-----|
| GET | /api/stats/monthly | 按月统计（选年份，选用户） |
| GET | /api/stats/yearly | 按年统计 |
| GET | /api/stats/by-category | 按分类统计（时间范围+用户） |
| GET | /api/stats/by-project | 项目专项统计（选项目ID）|
| GET | /api/stats/by-user | 按人统计（时间范围） |
| GET | /api/stats/trend | 趋势数据（月/季度/年） |

**项目统计返回示例（/api/projects/{id}/stats 或 /api/stats/by-project?id=1）：**
```json
{
  "project": { "id": 1, "name": "2025欧洲蜜月" },
  "period": { "start": "2025-05-10", "end": "2025-05-25" },
  "total_expense": 48500.00,
  "budget": 50000.00,
  "budget_remaining": 1500.00,
  "by_category": [
    { "category": "交通", "amount": 12500.00, "percentage": 25.8 },
    { "category": "餐饮", "amount": 8600.00, "percentage": 17.7 },
    { "category": "住宿", "amount": 15000.00, "percentage": 30.9 },
    { "category": "购物", "amount": 8400.00, "percentage": 17.3 },
    { "category": "娱乐", "amount": 4000.00, "percentage": 8.2 }
  ],
  "by_user": [
    { "user": "汤圆爸爸", "amount": 28500.00 },
    { "user": "汤圆妈妈", "amount": 20000.00 }
  ]
}
```

**返回示例（by-category）：**
```json
{
  "period": { "start": "2026-01-01", "end": "2026-05-13" },
  "total_expense": 48250.00,
  "total_income": 150000.00,
  "items": [
    { "category": "母婴", "amount": 3250.00, "percentage": 6.7, "count": 18 },
    { "category": "交通", "amount": 1240.50, "percentage": 2.6, "count": 42 }
  ],
  "by_user": [
    { "user": "汤圆爸爸", "amount": 38000.00 },
    { "user": "汤圆妈妈", "amount": 10250.00 }
  ]
}
```

### 4.3 自动交易管理

| 方法 | 路径 | 说明 |
|:----|:----|:-----|
| GET | /api/auto-transactions | 列表 |
| POST | /api/auto-transactions | 创建模板 |
| PUT | /api/auto-transactions/{id} | 修改 |
| DELETE | /api/auto-transactions/{id} | 删除 |
| GET | /api/auto-transactions/pending | 查看待确认的自动生成交易 |

### 4.5 项目管理

| 方法 | 路径 | 说明 |
|:----|:----|:-----|
| GET | /api/projects | 项目列表（可过滤已归档） |
| POST | /api/projects | 创建项目 |
| PUT | /api/projects/{id} | 修改项目 |
| DELETE | /api/projects/{id} | 删除项目（软删除/归档） |
| GET | /api/projects/{id}/transactions | 查看项目下所有交易（跨分类） |
| GET | /api/projects/{id}/stats | 项目专项统计（总支出、分类分布、每人贡献） |

### 4.6 用户与分类

| 方法 | 路径 | 说明 |
|:----|:----|:-----|
| GET | /api/users | 用户列表 |
| GET | /api/categories | 分类列表（含icon） |
| POST | /api/categories | 新增自定义分类 |

---

## 五、前端页面

建议 **Vue 3 + Element Plus**，单页应用，部署为静态文件，后端 API 传输 JSON。

### 页面清单

| 页面 | 内容 |
|:----|:-----|
| 📊 **仪表盘** | 本月支出/收入概览、分类饼图、趋势折线图、各人占比 |
| 📝 **记一笔** | 快速记账表单（时间/分类/金额/备注/归属人/所属项目） |
| 📋 **交易列表** | 筛选+分页表格，支持按项目过滤，支持导出 |
| 📈 **统计** | 切换月/年/自定义区间，分类明细（可下钻到二级），每人明细，专项项目统计 |
| 🏷️ **项目管理** | 创建/编辑专项项目，查看项目总账（跨分类穿透），与预算对比，每人贡献 |
| ⏰ **自动交易** | 管理固定支出模板，查看待确认项 |
| ⚙️ **管理** | 用户管理、分类管理（大类+子类维护）、数据导入（CSV上传） |

---

## 六、非功能需求

| 需求 | 说明 |
|:----|:-----|
| **本地运行** | 所有组件跑在本地（WSL或Windows），无云端依赖 |
| **离线可用** | 不依赖外网，纯本地网络（127.0.0.1） |
| **数据迁移** | 支持从喵喵记账CSV导入历史数据，含分类映射 |
| **响应速度** | 列表查询 < 500ms，统计 < 2s（本地MySQL） |
| **端口统一** | 后端 8080，前端可代理或直连 |

---

## 七、边界与限制

| 边界 | 说明 |
|:----|:-----|
| **不涉及** | 预算管理、账单提醒通知、短信导入、OCR识别、图表导出图片 |
| **不涉及** | 账户/银行卡余额自动同步、银行API对接 |
| **不涉及** | 多设备实时同步（单机本地部署） |
| **不涉及** | 权限管理（家庭内部使用，所有用户可见所有数据） |
| **暂不支持** | 分摊（AA制）——一笔支出可由多人平分 |
| **暂不支持** | 图片/小票附件上传 |
| **暂不支持** | 外币记账 |

---

## 八、开发顺序建议

| 阶段 | 内容 | 预计工时 |
|:----|:-----|:--------|
| **Phase 1** | 数据库建表 + 基础 CRUD API（交易、分类、用户） | 2-3天 |
| **Phase 2** | 前端：记一笔 + 交易列表 + 仪表盘 | 2-3天 |
| **Phase 3** | 统计分析 API + 前端统计页 | 1-2天 |
| **Phase 4** | 自动交易模板 + 定时生成 + 确认流程 | 1-2天 |
| **Phase 5** | CSV导入（映射历史分类）、数据初始化 | 1天 |
| **Phase 6** | 多用户支持、按人统计 | 1天 |
| **Phase 7** | 打磨、部署文档 | 1天 |

**总计约 9-13 天（单人全栈开发）。**

---

## 九、历史数据导入说明

喵喵记账CSV格式（3,030笔）：
```
分类，时间，金额，账户，账本，货币，备注
交通，2024.12.31 18:06:04，-24.56，，默认账本，人民币，充电
```

导入时：
1. 解析 `分类` → 映射到系统分类表
2. `时间` → `trans_date` + `trans_time`
3. `备注` → `note`
4. 所有历史数据归属人默认设为**汤圆爸爸**
5. `金额` 保持原样（负=支出，正=收入）

已存在分类映射表（40个分类 → 系统30个分类），如 `零食`→`餐饮:零食标签`、`红包`→`收入:红包` 等。

---

## 十、MySQL 初始化

启动时自动建库建表（Spring Boot `schema.sql`），或提供独立初始化脚本。用户只需：
1. 本地安装 MySQL 8.x
2. 创建数据库 `family_ledger`
3. 运行项目，自动初始化表结构和种子数据
