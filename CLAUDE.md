# 家庭账本系统 · Agent 约束

> 以下约束在 `family-ledger` 项目下工作时**始终生效**。
> 任何提交、依赖引入、架构调整，如果与某条冲突，必须先更新本文档再实施。

---

## 技术选型（不可变）

| 维度 | 值 |
|:----|:---|
| 后端 | **Java 17+ / Spring Boot 3.x / Maven 3.8+** |
| ORM | **MyBatis-Plus 或 JPA 二选一**，不可混用 |
| 数据库 | **MySQL 8.0+**（本地，仅 127.0.0.1）|
| 前端 | **Vue 3 + Element Plus + Vite** |
| 部署 | **`java -jar` + nginx/Vite dev server**，无 Docker/云服务 |
| 端口 | 后端 8080，前端 5173，MySQL 3306 |

**不可：** Kotlin/Scala/Groovy/Python/Go 做后端。不可用 React/Angular。不可引入 Docker/K8s/Spring Cloud。

---

## 项目结构

```
family-ledger/
├── backend/       ← Maven（Spring Boot），无前端代码
├── frontend/      ← Vite + Vue 3，无后端代码
├── docs/
└── scripts/       ← 工具脚本，避免大型依赖
```

前后端同仓库不同目录，不拆 repo。

---

## 层级依赖方向

```
Controller (API) → Service (业务) → Mapper/Repository (数据) → MySQL
```

- 数据层不可调业务层，业务层不可调 Controller
- Controller 不应含业务逻辑，Service 不应含 SQL/JPQL
- 全局异常用 `@RestControllerAdvice`，不在 Controller 里 try-catch

---

## API 响应格式

统一包装：

```json
{"code": 200, "message": "success", "data": ..., "timestamp": ...}
```

分页：

```json
{"code": 200, "data": {"records": [...], "total": N, "page": 1, "size": 20}}
```

不可有裸数组/裸对象响应。

---

## 数据约束

- **`amount` 用有符号数**：正=收入，负=支出。**不允许加 `type` 字段区分收支**。
- 分类最多**两级**（大类→细分），交易只关联二级分类
- 所有时间用北京时间（UTC+8），`trans_date DATE`，不含时区逻辑
- 金额用 `DECIMAL(12,2)`，Java 侧用 `BigDecimal`，禁止 `FLOAT`/`DOUBLE`
- 历史数据归属人默认=汤圆爸爸

---

## 性能 & 索引

- 列表查询 ≤500ms，统计 ≤2s（基于 3,000–30,000 笔）
- 单条写入 ≤200ms
- 必须创建索引：`idx_user_date`、`idx_category`、`idx_project`、`idx_date`
- 不在数据库端做 JSON 聚合，统一 Java 层处理

---

## 安全

- Hermes 数据写入口用户：`hermes@127.0.0.1`，权限仅 `SELECT, INSERT, UPDATE`
- 数据库密码在 `~/.hermes/.env`，不提交 Git
- `application.yml` 不提交，只提交 `application-template.yml`
- 禁止 `${param}` 拼接 SQL 片段（极少数例外需注释说明）

---

## 可引入/不可引入的依赖

**允许（按需，不多加）：**
- `spring-boot-starter-web / data-jpa / validation / test`
- `mybatis-plus-boot-starter`（如果选 MyBatis-Plus）
- `mysql-connector-j` / `lombok`（可选）

**不允许：**
- `spring-boot-starter-security / actuator / oauth2 / data-redis`
- `spring-cloud-*`
- 需要外部中间件的库（RabbitMQ、Kafka 客户端）
- 重量级框架（Activiti、Camunda、ES 客户端）
- 任何版本使用 `RELEASE`/`LATEST`，必须显式声明

---

## 代码风格

- Java：阿里巴巴 Java 开发手册规范
- 缩进：4 空格（Tab）
- 命名：驼峰（Java）、小驼峰（JS/TS）、下划线（SQL）
- 禁止 `System.out.println`，用 SLF4J Logger
- 禁止魔法数字，用常量/枚举

---

## 兼容性

- 已有 API 路径不可随意修改，改前先更新 `plans/family-ledger-plan.md`
- 数据库 DDL 变更必须写增量迁移脚本（`V2__xxx.sql`）
- 前端仅支持 Chrome/Edge/Firefox 最新两版本，不兼容 IE11/Safari 旧版
