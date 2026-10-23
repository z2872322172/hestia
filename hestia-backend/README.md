# Hestia · 家政服务预约平台（后端）

> Hestia（赫斯提亚）是希腊神话中的炉灶与家庭守护女神，对应本项目的家政、入户服务领域。
> 后端目录 `hestia-backend`，前端预留 `hestia-web`；包名保持 `com.housekeeping.platform`。

## 当前状态

**批次 0（技术底座）+ 批次 1（identity / customer / location）+ 批次 2（catalog / workforce / scheduling）+ 批次 3 第一轮（booking 全层 + dispatch 骨架）全部完成**，可编译、可测试、可真实启动并跑通业务闭环。

| 项 | 状态 |
| --- | --- |
| Maven 单工程 / Spring Boot 3.5.13 / Java 17 | 完成 |
| 统一响应、异常、traceId、分页 | 完成 |
| Flyway `V1__technical_baseline.sql`（6 张技术表） | 完成 |
| Flyway `V2__identity_customer_location.sql`（13 张业务表 + 序号表） | 完成 |
| Flyway `V3__service_catalog_workforce.sql`（11 张表，含人员 5 张预留表） | 完成 |
| Flyway `V4__scheduling.sql`（3 张表：时间窗 / 排班 / 时段库存 + 种子窗口） | 完成 |
| Flyway `V5__booking_dispatch.sql`（9 张表：订单 5 + 派单 4 + 默认派单策略） | 完成 |
| MyBatis-Plus（分页 + 乐观锁 + 审计字段填充） | 完成 |
| Redis（JSON 序列化 + key 规范 + Redisson） | 完成 |
| RocketMQ 5.x（Outbox → Relay → 幂等消费） | 完成（真实 Broker 联调见下文） |
| MinIO（公开/私有桶、预签名 URL、文件元数据） | 完成 |
| 地图 Provider（Mock / 高德可切换） | 完成 |
| WebSocket / STOMP（`SYSTEM_PING` 心跳） | 完成 |
| Actuator 健康检查（db/redis/minio/rocketMq/map） | 完成 |
| Swagger / OpenAPI | 完成 |
| **JWT 认证**（HS256、token_version、jti 撤销） | 完成 |
| **注册 / 登录 / 改密 / 角色切换 / 邀请激活** | 完成 |
| **客户档案 + 地址簿**（默认唯一、越权隔离、下单快照） | 完成 |
| **行政区字典 + 服务区域匹配**（STREET 优先于 DISTRICT） | 完成 |
| **服务分类树 + 服务项**（草稿/上架/下架状态机、乐观锁改版） | 完成 |
| **服务定价**（固定价 / 面积阶梯 / 时长阶梯 / 数量阶梯、附加项、纯函数计价） | 完成 |
| **服务标准快照**（上架前必须有标准、下单快照带版本） | 完成 |
| **人员档案与资质**（开户激活、审核入职、技能/证书/片区、可派单资格） | 完成 |
| **排班与可用性**（时间窗、批量排班、请假/停排、时段状态机、三级可用性过滤） | 完成 |
| **订单**（下单预览/创建、三段式幂等、支付确认、改期、取消违约金、超时关单） | 完成 |
| **派单骨架**（任务/候选/分配三表、支付激活、取消联动、改期改派链） | 完成 |
| `mvn clean verify` | **通过（170 个测试）** |
| 业务闭环冒烟（批次 1：20 / 批次 2 目录：26 / 人员：22 / 排班：27 / 订单：24 项断言） | **均通过**（见「本次验证记录」） |
| payment（支付单 / 回调 / 对账）、dispatch 接单与履约、售后 | 未开始，批次 3 第二轮推进 |

## 技术栈与版本

版本已锁定，避免依赖漂移（依据 `docs/03` 第 3.4 节）：

```text
Java            17 (LTS)
Spring Boot     3.5.13
MyBatis-Plus    3.5.16  + mybatis-plus-jsqlparser（分页插件已拆为独立模块）
Flyway          Spring Boot BOM 管理（含 flyway-mysql）
Redisson        3.52.0
RocketMQ        rocketmq-client-java 5.0.7（gRPC，手工装配，不用 Starter）
MinIO           8.0.3
JTS             1.20.0
Springdoc       2.8.13
ArchUnit        1.4.1
Testcontainers  1.21.4（BOM）
jjwt            0.11.2（api / impl / jackson，HS256）
```

## 目录结构

```text
hestia-backend/
├─ pom.xml / mvnw / mvnw.cmd / .mvn/wrapper
├─ .env.example / .gitignore
├─ docker/
│  ├─ compose.yaml                  MySQL / Redis / MinIO / minio-init / RocketMQ(NS+Broker+Proxy)
│  ├─ mysql/init/01-init.sql        建库与授权（表结构仍由 Flyway 管理）
│  └─ rocketmq/broker.conf
├─ scripts/dev/
│  ├─ start-infra.ps1 / stop-infra.ps1 / reset-infra.ps1
│  ├─ init-rocketmq.ps1
│  ├─ smoke-test.ps1              技术底座冒烟
│  ├─ smoke-batch1.py             批次 1 业务闭环冒烟（20 项断言）
│  ├─ smoke-batch2.py             批次 2 服务目录与报价冒烟（26 项断言）
│  ├─ smoke-workforce.py          批次 2 人员与资质冒烟（22 项断言）
│  ├─ smoke-scheduling.py         批次 2 排班与可用性冒烟（27 项断言）
│  └─ smoke-booking.py            批次 3 订单与派单冒烟（24 项断言）
└─ src/
   ├─ main/java/com/housekeeping/platform/
   │  ├─ HousekeepingPlatformApplication.java
   │  ├─ bootstrap/
   │  │  ├─ config/     Security、MyBatis-Plus、OpenAPI、REST 401/403 处理
   │  │  ├─ job/        OutboxRelayJob（定时投递）
   │  │  └─ web/        TraceIdFilter、系统自检 Controller、VO
   │  ├─ common/
   │  │  ├─ api/        Result、PageResult、ErrorCode、RequestContext
   │  │  ├─ exception/  BusinessException、GlobalExceptionHandler
   │  │  ├─ id/         IdGenerator（ULID）
   │  │  ├─ pagination/ PageQuery
   │  │  ├─ persistence/ AuditMetaObjectHandler、RequiresPersistence
   │  │  └─ security/   CurrentAccountProvider、CurrentAccountResolver、AuthenticatedAccount
   │  ├─ identity/      账号、角色、邀请、登录日志（JWT 签发与校验）
   │  │  ├─ domain/       AccountStatus、RoleCode、PhoneNumber、PasswordPolicy…
   │  │  ├─ persistence/  6 个实体 + 5 个 Mapper
   │  │  ├─ application/  AuthApplicationService、AccountQueryService、InvitationService…
   │  │  └─ api/          AuthController、AdminAccountController、JwtAuthenticationFilter
   │  ├─ customer/      客户档案与地址簿
   │  │  ├─ domain/       AddressMasking、AddressLabel、CustomerStatus…
   │  │  ├─ persistence/  CustomerProfile / CustomerAddress
   │  │  ├─ application/  CustomerProfileService、CustomerAddressService、contract/
   │  │  └─ api/          CustomerProfileController、CustomerAddressController
   │  ├─ location/      行政区字典与服务区域
   │  │  ├─ domain/       RegionLevel、CoverageLevel、MatchRanking（纯函数）
   │  │  ├─ persistence/  administrative_region / service_area / coverage
   │  │  ├─ application/  ServiceAreaMatchService、ServiceAreaAdminService、RegionDictionaryService
   │  │  └─ api/          PublicLocationController、Operator/Admin 管理接口
   │  ├─ catalog/       服务目录、定价与服务标准
   │  │  ├─ domain/       PricingMode、TierType、PricedTier、PriceCalculator（纯函数）
   │  │  ├─ persistence/  category / item / option / option_value / price_tier / standard_item
   │  │  ├─ application/  ServiceCategoryService、ServiceItemAdminService、
   │  │  │                 ServiceCatalogQueryService、PriceQuoteService
   │  │  └─ api/          PublicCatalogController、OperatorCatalogController
   │  ├─ workforce/     人员档案、技能、证书与服务片区
   │  │  ├─ domain/       WorkStatus、AuditStatus、CertificateValidity、EligibilityEvaluator
   │  │  ├─ persistence/  profile / skill / certificate / service_area / qualification
   │  │  ├─ application/  WorkerProfileService、WorkerAdminService、WorkerEligibilityService
   │  │  └─ api/          WorkerSelfController、OperatorWorker/QualificationController
   │  ├─ scheduling/    排班、时间窗与可用时段
│  │  ├─ domain/       ScheduleType、SlotStatus（状态机）、WindowTimeline（纯函数）
│  │  ├─ persistence/  service_window / worker_schedule / worker_slot
│  │  ├─ application/  ServiceWindowService、WorkerScheduleService、
│  │  │                 AvailabilityQueryService、WorkerSlotReservationService（订单契约）
│  │  └─ api/          PublicAvailability、WorkerSchedule、OperatorSchedule/WindowController
│  ├─ booking/      订单：下单、支付确认、改期、取消、超时关单
│  │  ├─ domain/       BookingStatus（7 状态机）、CancellationFeeCalculator/Policy（纯函数）
│  │  ├─ persistence/  order / item / status_log / schedule_history / cancellation
│  │  ├─ application/  BookingApplicationService（下单编排）、BookingQueryService、
│  │  │                 BookingStatusCommandService、BookingPaymentTimeoutJob、BookingProperties
│  │  └─ api/          Customer/Worker/OperatorBookingController
│  ├─ dispatch/     派单：任务、候选、分配（批次 3 骨架，接单/履约待做）
│  │  ├─ persistence/  dispatch_policy / dispatch_job / dispatch_candidate / order_assignment
│  │  └─ application/  DispatchApplicationService（建任务/激活/取消/改派）
│  └─ infrastructure/
│     ├─ cache/      RedisConfig、RedisKeyNames
│     ├─ messaging/  Outbox、Relay、幂等消费、事件信封
│     │  └─ rocketmq/ Producer/Consumer、SmokeEventService
│     ├─ persistence/idempotency/  IdempotencyService（三段式幂等协议）
│     ├─ persistence/sequence/  BusinessNoGenerator（业务编号，允许跳号不允许重号）
│     ├─ security/   JwtTokenService、TokenRevocationService、SecurityContext 适配
│     ├─ storage/minio/  MinioClient、ObjectStoragePort、适配器、文件元数据
│     ├─ map/        MapProvider、Mock/高德实现、MapQueryService
│     ├─ realtime/   WebSocketConfig、SYSTEM_PING
│     └─ observability/ minio / rocketMq / map 健康检查、集成状态
   └─ main/resources/
      ├─ application.yml + application-{local,test,demo,prod}.yml
      ├─ logback-spring.xml
      ├─ db/migration/V1__technical_baseline.sql
      ├─ db/migration/V2__identity_customer_location.sql
      ├─ db/migration/V3__service_catalog_workforce.sql
      ├─ db/migration/V4__scheduling.sql
      ├─ db/migration/V5__booking_dispatch.sql
      └─ mapper/ 、static/
```

> 各空目录里的 `.gitkeep` 仅用于占位，写入真实文件后可手动删除（本机安全策略禁止脚本删除，未自动清理）。

## 快速开始

### 方式一：Docker Compose（推荐）

```powershell
cd D:\毕业设计\hestia-backend
.\scripts\dev\start-infra.ps1          # 拉起 MySQL / Redis / MinIO / RocketMQ 并等待健康
.\mvnw.cmd clean verify                # 构建 + 单元测试
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

另开一个窗口做冒烟验证：

```powershell
.\scripts\dev\smoke-test.ps1           # 技术底座：健康检查 / Swagger / 地图 / 事件链路 / 文件上传
python .\scripts\dev\smoke-batch1.py   # 批次 1 业务闭环：注册 / 登录 / 地址 / 片区匹配 / 越权
python .\scripts\dev\smoke-batch2.py   # 批次 2 服务目录：分类树 / 详情 / 报价 / 上下架 / 版本冲突
python .\scripts\dev\smoke-workforce.py    # 批次 2 人员：开户 / 审核 / 技能证书 / 可派单资格
python .\scripts\dev\smoke-scheduling.py   # 批次 2 排班：时间窗 / 批量排班 / 请假停排 / 可用性
python .\scripts\dev\smoke-booking.py      # 批次 3 订单：下单幂等 / 支付 / 改期 / 取消 / 超时
```

> `smoke-batch1.py` 每次运行都会生成新的手机号，可以在同一个库上反复执行；
> 输出里只打印令牌前 8 位与脱敏手机号，不会把凭据写进终端历史。
> `smoke-batch2.py` 默认只跑公开侧；加 `--operator-phone <手机号>` 才会跑运营侧
> （上下架、改标准、版本冲突），该账号需要先在库里授 `OPERATOR` 角色，脚本会打印授权 SQL。

停止与重置：

```powershell
.\scripts\dev\stop-infra.ps1                  # 停止容器，保留数据卷
.\scripts\dev\reset-infra.ps1 -ConfirmReset   # 破坏性：删除数据卷，回到空库
```

### 方式二：使用本机已安装的 MySQL

本机已有 MySQL 8 时不需要 Docker，但需要手动建库并注入环境变量（Redis 仍然必需，
否则 Redisson 在启动阶段连不上会直接失败）：

```sql
CREATE DATABASE hestia DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'hestia'@'%' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON hestia.* TO 'hestia'@'%';
```

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/hestia?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="hestia"
$env:DB_PASSWORD="你的密码"
$env:REDIS_HOST="localhost"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### 方式三：使用虚拟机上的中间件（当前开发环境）

本地虚拟机 `192.168.150.101`（CentOS 7 + Docker）上已部署全部中间件，后端直接连它，
不需要在本机装 MySQL/Redis/MinIO/RocketMQ：

| 组件 | 地址 | 账号 |
| --- | --- | --- |
| MySQL 8.0 | `192.168.150.101:3306` | 库 `hestia` / 用户 `hestia` / 密码 `hestia-dev-secret` |
| Redis 7 | `192.168.150.101:6379` | `requirepass 123321` |
| MinIO | `http://192.168.150.101:9000`（Console `:9001`） | `minioadmin` / `minioadmin` |
| RocketMQ 5.3.2 | Proxy `192.168.150.101:8081`（gRPC），Namesrv `:9876` | 无鉴权 |

连接信息写在项目根目录的 `.env`（已被 `.gitignore` 忽略），启动：

```powershell
.\scripts\dev\run-local.cmd          # 读取 .env 注入环境变量后启动 jar
```

> PowerShell 默认执行策略会拦截 `.ps1`，`.cmd` 内部已带 `-ExecutionPolicy Bypass`；
> 直接调脚本请用 `powershell -ExecutionPolicy Bypass -File .\scripts\dev\run-local.ps1`。

使用该环境时有三点与 compose 方案不同：

1. **Redis 只有一个库**：该实例 `redis.conf` 里 `databases=1`，`REDIS_DATABASE` 只能是 `0`。
   db0 中还存着另一个项目（tjxt）的 32 个 key，Hestia 靠 `app:cache:` 等前缀隔离，
   不要对这个实例执行 `FLUSHDB` / `FLUSHALL`。
2. **RocketMQ 健康检查在首条消息前是 DOWN**：Producer 懒初始化，`/actuator/health`
   里的 `rocketMq` 要等第一次发消息后才变 UP，这是设计行为，不是故障。
3. **MySQL 上还有别的项目的库**（`tj_*`、`verda` 等），Hestia 只使用独立的 `hestia` 库，
   Flyway 也只在这个库里建表。

## 环境变量

见 `.env.example`。约定：

- 生产环境所有密钥（DB、Redis、MinIO、高德）**必须**来自环境变量，`application-prod.yml` 中缺省为空，缺失即启动失败；
- `local` 使用 compose 的开发凭据（`minioadmin/minioadmin`），仅限本机；
- 敏感值不得写入代码、配置、测试与 README。

## 接口清单

### 系统自检（不是业务 API）

| 方法 | 路径 | 说明 | 可用 profile |
| --- | --- | --- | --- |
| GET | `/api/v1/system/ping` | 应用名、profile、服务器时间、traceId | 全部 |
| GET | `/api/v1/system/integrations` | 聚合 Actuator 健康状态 + 当前地图 Provider | 全部 |
| GET | `/api/v1/system/smoke/geo?address=` | 地理编码冒烟 | local / demo / test |
| POST | `/api/v1/system/smoke/event` | 写入 `SCAFFOLD_PING` 到 `event_outbox`，返回 `eventId` | local / demo / test |
| GET | `/api/v1/system/smoke/event/{eventId}` | 查询投递状态（outbox）与消费状态（consumer_message） | local / demo / test |
| POST | `/api/v1/system/smoke/file` | multipart 上传，返回对象键 + 预签名 URL | local / demo / test |
| GET | `/actuator/health` | db / redis / minio / rocketMq / map | 全部 |
| GET | `/v3/api-docs`、`/swagger-ui/index.html` | 接口文档 | 全部 |
| WS | `/ws` → 发 `/app/system.ping`，订 `/topic/system.ping` | 心跳回声 `SYSTEM_PING` | 全部 |

### 批次 1 业务 API

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/customer/register` | 公开 | 消费者注册（同事务建账号 + 角色 + 客户档案，返回令牌） |
| POST | `/api/v1/auth/login` | 公开 | 手机号密码登录，失败计数与锁定 |
| POST | `/api/v1/auth/worker/activate` | 公开 | 服务人员凭邀请令牌设置密码并登录 |
| POST | `/api/v1/auth/logout` | 已登录 | 撤销当前 jti |
| GET | `/api/v1/auth/me` | 已登录 | 当前账号（手机号脱敏） |
| POST | `/api/v1/auth/switch-role` | 已登录 | 切换活动角色，换发令牌 |
| PUT | `/api/v1/auth/password` | 已登录 | 改密，`token_version` 自增使旧令牌全部失效 |
| GET | `/api/v1/customer/profile` | CUSTOMER | 客户档案 |
| GET/POST | `/api/v1/customer/addresses` | CUSTOMER | 地址列表 / 新增（首条自动默认） |
| GET/PUT/DELETE | `/api/v1/customer/addresses/{addressNo}` | CUSTOMER | 详情 / 修改（乐观锁）/ 软删除 |
| PUT | `/api/v1/customer/addresses/{addressNo}/default` | CUSTOMER | 设为默认 |
| POST | `/api/v1/customer/addresses/{addressNo}/availability-check` | CUSTOMER | 重新匹配服务区域 |
| GET | `/api/v1/customer/addresses/{addressNo}/snapshot` | CUSTOMER | 下单地址快照（带行政区名称） |
| GET | `/api/v1/public/service-areas?cityCode=` | 公开 | 城市在售片区 |
| POST | `/api/v1/public/service-area-match` | 公开 | 地址是否在服务范围内 |
| GET/POST | `/api/v1/admin/accounts` | ADMIN | 账号分页 / 详情 / 停用 / 启用 / 改角色 / 改片区 |
| POST | `/api/v1/operator/workers/invitations` | OPERATOR / ADMIN | 签发服务人员邀请 |

### 批次 2 业务 API（服务目录与报价）

公开侧（无需登录，草稿与下架项在 SQL 层就被过滤掉，看起来与"不存在"完全一致）：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/public/service-categories` | 公开 | 可见分类树（父级禁用则整棵子树隐藏） |
| GET | `/api/v1/public/service-items?categoryNo=` | 公开 | 已上架服务项列表，可按分类过滤 |
| GET | `/api/v1/public/service-items/{itemNo}` | 公开 | 服务项详情（含选项、阶梯、服务标准） |
| POST | `/api/v1/public/service-items/{itemNo}/price-preview` | 公开 | 报价试算，请求体**不含金额**，金额只由服务端算 |

运营侧：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/operator/service-categories` | OPERATOR / ADMIN | 分类树（含禁用项，运营视角） |
| POST | `/api/v1/operator/service-categories` | OPERATOR / ADMIN | 新建分类（父级必须 ACTIVE，深度上限 3） |
| PUT | `/api/v1/operator/service-categories/{categoryNo}` | OPERATOR / ADMIN | 改名/排序/启停（禁用前校验无在售服务项；父级不可变更） |
| GET | `/api/v1/operator/service-items` | OPERATOR / ADMIN | 服务项分页（含草稿/下架） |
| POST | `/api/v1/operator/service-items` | OPERATOR / ADMIN | 新建服务项（初始 DRAFT） |
| GET | `/api/v1/operator/service-items/{itemNo}` | OPERATOR / ADMIN | 运营视角详情（含草稿） |
| PUT | `/api/v1/operator/service-items/{itemNo}` | OPERATOR / ADMIN | 改价/改时长/改选项（必须带 `expectedVersion`） |
| POST | `/api/v1/operator/service-items/{itemNo}/publish` | OPERATOR / ADMIN | 上架（4 道校验） |
| POST | `/api/v1/operator/service-items/{itemNo}/offline` | OPERATOR / ADMIN | 下架 |
| GET/PUT | `/api/v1/operator/service-items/{itemNo}/standards` | OPERATOR / ADMIN | 读取 / 整体替换服务标准 |

> 运营接口要求账号已授 `OPERATOR`。`docs/06` 明确不提供默认管理员，注册接口也不会签发运营角色，
> 所以验证用账号需自行入库（脚本会打印这段 SQL）：
> ```sql
> INSERT INTO account_role (account_id, role_code, status)
> SELECT id, 'OPERATOR', 'ACTIVE' FROM account WHERE phone = '<手机号>';
> ```
> 注意 `RoleCombinationPolicy` 不允许同一账号同时持有 CUSTOMER 与 OPERATOR，
> 所以不能拿消费者账号直接追加角色，要单独建一个账号（`account_no` 手工给一个不重复的值即可，
> `password_hash` 可从任一已注册账号复制，仅用于本地验证）。

响应结构：

```json
{ "code": "OK", "message": "success", "data": {}, "traceId": "01J..." }
```

### 批次 2 业务 API（人员与资质 / 排班与可用性）

人员自助侧（`WORKER`，人员身份由令牌解析，接口不接受 `workerNo` 参数）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/worker/profile`（`/detail`） | 本人档案摘要 / 详情（技能、证书、区域） |
| PUT | `/api/v1/worker/profile` | 修改基础信息（仅性别与自我介绍） |
| GET/POST | `/api/v1/worker/certificates` | 证书列表 / 提交证书（一律待审核） |
| GET | `/api/v1/worker/skills`、`/service-areas` | 本人技能 / 服务区域 |
| GET/POST | `/api/v1/worker/schedules` | 本人排班 / 自助报班（仅 WORK、须提前一天） |
| POST | `/api/v1/worker/schedules/{id}/cancel` | 取消本人排班（有订单占用则拒绝） |
| POST | `/api/v1/worker/leaves` | 提交请假（生成 LEAVE 排班 + BLOCKED 时段） |

运营侧（`OPERATOR / ADMIN`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/operator/workers` | 创建人员档案并开户（返回一次性激活凭证） |
| GET | `/api/v1/operator/workers/{workerNo}` | 人员详情 / 分页 / 修改 |
| POST | `/api/v1/operator/workers/{workerNo}/approve` / `suspend` / `reactivate` / `leave` | 入职 / 暂停（联动角色）/ 恢复 / 离职 |
| PUT | `/api/v1/operator/workers/{workerNo}/skills` / `service-areas` | 整体替换技能 / 片区 |
| POST | `/api/v1/operator/workers/{workerNo}/certificates/{id}/review` | 审核证书 |
| GET | `/api/v1/operator/workers/{workerNo}/eligibility` | 可派单资格（返回全部不通过原因） |
| PUT | `/api/v1/operator/service-items/{itemNo}/qualification-requirements` | 配置资质要求 |
| GET/POST/PUT | `/api/v1/operator/service-windows` | 时间窗列表 / 新建 / 修改（乐观锁，重叠拒绝） |
| GET/POST | `/api/v1/operator/schedules` | 排班查询（按日期区间，可按人员过滤）/ 批量排班 |
| PUT | `/api/v1/operator/schedules/{id}` | 修改排班（仅空闲时段） |
| POST | `/api/v1/operator/schedules/{id}/leave` / `disable` | 替人员请假 / 停排 |

公开侧（无需登录）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/public/service-windows` | 启用中的标准时间窗 |
| GET | `/api/v1/public/availability?serviceItemNo=&serviceAreaId=&dateFrom=&dateTo=` | 聚合可约时段（区间 ≤ 14 天；不暴露人员名单与剩余数量） |

### 批次 3 业务 API（订单，当前阶段）

客户侧（`CUSTOMER`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/customer/bookings/preview` | 下单前报价预览（金额只由服务端重算） |
| POST | `/api/v1/customer/bookings` | 创建订单；带 `Idempotency-Key` 头启用三段式幂等（同键重放返回同一单，同键改内容 409） |
| GET | `/api/v1/customer/bookings?status=&page=&size=` | 我的订单分页 |
| GET | `/api/v1/customer/bookings/{orderNo}` | 订单详情（含金额明细、派单状态、取消原因） |
| POST | `/api/v1/customer/bookings/{orderNo}/cancel` | 取消（待支付免费；已支付按快照算违约金，远期超免费窗口为 0） |
| POST | `/api/v1/customer/bookings/{orderNo}/reschedule` | 改期（仅 CONFIRMED；换锁时段 + 改派，原时段释放） |

服务人员侧（`WORKER`，归属由令牌解析）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/worker/bookings?page=&size=` | 被派订单列表（按 `current_worker_id`） |
| GET | `/api/v1/worker/bookings/{orderNo}` | 任务详情 |

运营侧（`OPERATOR / ADMIN`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/operator/bookings?status=&appointmentDate=&page=&size=` | 全量订单监控分页 |
| GET | `/api/v1/operator/bookings/{orderNo}` | 运营视角详情 |

订单冒烟端点（local / demo / test，生产 denyAll）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/system/smoke/booking/{orderNo}/mark-paid` | 模拟支付成功回调（金额不符 422；重复回调幂等） |
| POST | `/api/v1/system/smoke/booking/timeout-sweep?asOf=` | 手动触发支付超时扫描，`asOf` 可注入模拟时钟 |

`prod` 下 `/api/v1/system/smoke/**` 被 `denyAll`，不是"接口不注册"这种可绕过的方式。

## 关键设计说明

1. **Outbox 两段式投递**：`OutboxClaimService` 用 `SELECT ... FOR UPDATE SKIP LOCKED` 在短事务里把 `PENDING` 改成 `SENDING`，事务提交后再发送 MQ。发送失败按 `base * 2^(n-1)` 退避重试，超过 `max-retry` 置 `DEAD` 等人工处理，不会静默丢事件。
2. **消费幂等**：先插入 `consumer_message(consumer_group, message_id)` 唯一键记录，冲突即视为重复消息直接确认成功；业务异常时记录 `FAILED` 并返回 `ConsumeResult.FAILURE` 触发重试。
3. **投递懒初始化**：Producer 首次发送时才建连，Broker 未就绪不会导致应用启动失败；Consumer 启动失败按 `consumer-start-retry-ms` 定时重试。
4. **无基础设施也能跑测试**：`test` profile 关闭数据源、Flyway、MyBatis、Redis、Redisson、RocketMQ、MinIO，`mvn clean verify` 不依赖 Docker。
5. **架构约束用测试守住**：`ArchitectureRulesTest` 校验 common 不依赖基础设施/业务包、Controller 不直接依赖 Mapper、Controller 不得出现在全局平铺包中。
6. **V1 迁移的外键说明**：模块 13 的 `audit_log` / `system_config` 原 DDL 带 `account` 外键，但 `account` 属于 V2；为避免迁移顺序倒挂，V1 先不建该外键，由 V2 建表后补充。
7. **登录方法刻意不加 `@Transactional`**：`LoginAttemptService` 用 `REQUIRES_NEW` 独立事务累加失败次数，如果外层再开一个事务持有 `account` 行锁，内层会等外层释放，形成自锁。代价是"失败计数"与"登录日志"不在同一事务，可接受——它们本来就是审计数据。
8. **失败计数用 SQL 自增而不是读改写**：`failed_login_count = failed_login_count + 1` 一条语句完成，并发登录失败不会丢计数；`lockAccount` 不再用 `status='ACTIVE'` 兜底（否则已锁定的账号永远无法升级锁定），另设 `releaseExpiredLock` 只在 `locked_until <= NOW()` 时恢复。
9. **令牌失效分两层**：`token_version` 处理账号级失效（改密、停用、换角色、改片区），不依赖 Redis；jti 撤销列表处理"单设备退出"，TTL 等于令牌剩余有效期，Redis 挂了只记 WARN 不阻断退出。
10. **默认地址唯一靠生成列**：`active_default_customer_id = CASE WHEN is_default=1 AND status='ACTIVE' THEN customer_id END STORED` 加唯一索引，非默认地址该列为 `NULL`（唯一索引不互斥）。实体里该字段标 `@TableField(exist = false)`，MyBatis-Plus 永远不会写它。地址条数上限用 `SELECT ... FOR UPDATE` 锁客户行来串行化。
11. **服务区域匹配是纯函数**：排序规则 `coverage_level(STREET < DISTRICT)` → `priority DESC` → `effective_from DESC` 全部在 `MatchRanking` 里，三个键全平才返回 `AMBIGUOUS`，可单元测试、不依赖数据库排序规则。
12. **跨模块只给契约不给实体**：`CustomerQueryService` / `AddressSnapshotQueryService` 按 `docs/07` 第 11.2 节定义，返回 `CustomerRef` 与 `AddressSnapshot`，不暴露客户表实体。快照入参用 `accountId` 而不是 `customerId`，归属校验必须由客户模块自己做——漏一次就是越权读别人的地址。
13. **业务编号允许跳号不允许重号**：`business_no_sequence` 先 `UPDATE current_value + 1` 再回读，回读失败或并发冲突就重试，宁可跳号也不重复（订单号重号比跳号严重得多）。该表是 `docs/07` 六张技术表之外新增的第 7 张，属于批次 1 的必要补充。
14. **计价是纯函数，不是服务**：`PriceCalculator` 全是静态方法，不注入 Spring、不碰数据库，入参是 `PriceRequest`（定价模式 + 基础价 + 阶梯 + 选项 + 数量 + 时长）。好处有两个：可以用穷举单测把边界钉死；下单时能在订单事务内直接算钱，不用为"查价"再开一次读，也就不会出现"预览价与下单价不一致"。
15. **阶梯是左闭右开 `[min, max)`，`max` 为空表示不封顶**：相邻档位因此天然不重叠，60 平米只可能落进 `[60, 90)` 一档。**重叠档位在上架和报价两处都会被拒绝**（`SERVICE_PRICE_TIER_CONFLICT`），而不是"取第一个命中的档"——取第一个会让价格取决于插入顺序，是数据事故的典型来源。
16. **种子数据的 `numeric_value` 必须填档位下界**：面积选项"60 平米以内"填 `0`、"60-90 平米"填 `60`。如果按直觉把"60 平米以内"填成 `60`，它会落进 `[60, 90)` 那一档，报价直接错一档。这个坑在 V3 的 SQL 注释里写明了，改种子时别照着中文标签反推数字。
17. **服务项的 `version` 一列两用**：既是 MyBatis-Plus 乐观锁版本，也是业务版本（改价/改时长后 +1，下单快照记它）。因此更新**不能**用 `updateById`——它跳过 null 字段，导致"取消数量上限"（把 `max_quantity` 置空）永远写不进库。代码里统一用 `LambdaUpdateWrapper ... .eq(version, expected).set(...)` 显式赋值，顺便把乐观锁交给 SQL 的 `WHERE version = ?`。
18. **子集合整体替换而不是 diff**：选项、选项值、价格阶梯、服务标准在一次事务里全删再全插。原因不是偷懒，而是这些集合带跨行约束（档位不重叠、必选组必须有值、单选组 `maxSelect` 必须为 1），做增量 diff 的复杂度远高于重写，且很难证明不留脏数据。
19. **状态流转靠条件更新兜底**：`publish` 的 SQL 是 `WHERE id=? AND status IN ('DRAFT','OFFLINE')`，`offline` 是 `WHERE status='PUBLISHED'`，影响行数为 0 就抛 `SERVICE_ITEM_STATUS_CONFLICT`。这样两个运营同时点"上架"时，只有一个成功，另一个拿到明确的 409，而不是双写。
20. **上架 4 道校验**：分类仍为 ACTIVE、至少一条有效服务标准、需要阶梯的定价模式必须配好阶梯、阶梯之间不重叠。前两条防止"上架了却没人能下单/没人知道服务内容"，后两条防止价格算不出来。
21. **分类可见性用自连接一次过滤**：`selectVisible` 在 SQL 里 join 父级和祖父级并校验 `status='ACTIVE'`，禁用父级时整棵子树一次性消失，不会出现"子分类还在但点进去空白"的半状态；同时避免了在 Java 里逐层递归查询的 N+1。分类树深度上限 3 由业务层保证，`depthOf` 最多向上走 4 步，即使数据成环也不会把请求线程挂死。
22. **`SERVICE_ITEM_NOT_PUBLISHED` / `SERVICE_ITEM_OFFLINE` / `SERVICE_PRICE_CHANGED` 目前没有调用点**，是批次 3 下单校验预留：公开查询刻意让草稿项与"不存在"表现完全一致（否则靠错误码就能探测未上架商品），只有下单时"客户购物车里的项目刚被下架/价格变了"才需要区分，这两个场景在批次 3 才出现。三个常量都在注释里写明了这一点，不是忘记删的死代码。
23. **时段状态机 + 条件更新是并发的全部**：`AVAILABLE → HELD → BOOKED` 等迁移用 `UPDATE ... WHERE status = 期望值 AND version = ?` 落库，两个订单竞争同一时段只有一个成功；`chk_worker_slot_order_binding` CHECK 约束兜底"AVAILABLE 必须无订单、HELD 必须带锁定到期时间"等绑定不变量。BOOKED 不允许直改 BLOCKED——已支付时段只能走改派或改期。
24. **可用性是三级过滤的聚合结果**：库存（`worker_slot` 有 AVAILABLE）→ 接单上限（人员档案的 `max_daily_orders` 对比当日 BOOKED 数）→ 资格（workforce 的技能/证书/账号/区域判定）。消费者只看到"能不能约"，不暴露人员名单与剩余数量；服务时长放不进的窗口整窗不出现，而不是报错。
25. **跨模块取数不 join 别人的表**：可用性聚合里 scheduling 只查自己的 `worker_slot` 拿候选人员，接单上限走 workforce 的 `maxDailyOrdersByIds`，资格判定走 `WorkerEligibilityService.anyEligible`。`WorkerSlotReservationService` 是订单模块（批次 3）的锁定契约，15 分钟到期释放已就绪。
26. **排班变更遇占用一律拒绝**：请假/停排/修改排班遇到 HELD/BOOKED 时段抛 `SCHEDULE_CHANGE_HAS_BOOKED_ORDER`，绝不静默吞掉已占用时段；条件更新成功后必须同步内存实体——否则响应里的时段状态是更新前的旧值（冒烟抓出过这个 bug）。
27. **下单编排破解"先有 orderId 才能 hold"的死锁**：先 insert 订单（`worker_slot_id` 暂填 0，CHECK 约束允许）→ 拿主键 hold 时段 → `LambdaUpdateWrapper` 回填 slotId。全部在订单事务内，中途失败整体回滚，不会留下"订单在但时段没锁住"的半状态。订单号、金额明细、地址快照、服务标准快照（带版本）都在下单瞬间定格，之后目录改价不影响已建订单。
28. **三段式幂等协议**：`IdempotencyService` 按"查 SUCCEEDED 回放 → begin 插 PROCESSING 占位 → markSucceeded 存响应快照"工作，占位与业务写库同事务（回滚即消失）。幂等键按 **键 + 账号 + 接口路径** 三重隔离；回放前必须比对 `request_hash`——同键不同内容直接 409 `IDEMPOTENCY_CONFLICT`，绝不能把旧响应静默回放给内容已变的新请求（冒烟抓出过这个缺陷）。
29. **订单摘要列与派单表在同一事务里同步**：`booking_order` 的 `assignment_status` 等摘要列是详情页/任务页的读取源，派单推进（支付激活 → PENDING_ACCEPT、取消 → CANCELED）时必须回填，否则人员任务列表（按 `current_worker_id` 查）与运营监控读到滞后状态（冒烟抓出过）。回填用 `updateById` 的"只更新非 null 字段"特性做最小 patch。
30. **支付超时任务是条件更新兜底的可重试扫描**：每轮 `UPDATE ... WHERE order_status='PENDING_PAYMENT'` 影响行数 0 就跳过（客户恰好同时支付/取消不重复释放），逐单 try/catch 容错。`runOnce(asOf)` 重载支持注入模拟时钟，冒烟不用真等 15 分钟。

## 测试

```powershell
.\mvnw.cmd clean verify                 # 默认：单元 / 契约 / 架构 / 上下文，共 170 个测试
.\mvnw.cmd test "-Dgroups=integration"  # 需要 Docker：Flyway(Testcontainers MySQL)、Redis、MinIO
```

`integration` 组测试使用 `@Testcontainers(disabledWithoutDocker = true)`，无 Docker 时自动跳过而不是失败。
`RocketMqSmokeTest` 目前 `@Disabled`：RocketMQ 5.x 需要 NameServer + Broker + Proxy 三容器，Testcontainers 无官方 5.x 组合模块，改用 `scripts/dev/smoke-test.ps1` 做真实链路验证（原因写在类注释里）。

批次 1 新增的单元测试（全部不依赖外部基础设施）：

| 测试类 | 覆盖点 | 数量 |
| --- | --- | --- |
| `PhoneNumberTest` | `+86` 归一化、去分隔符、脱敏 | 5 |
| `PasswordPolicyTest` | 长度、字母+数字、不得含手机号 | 4 |
| `RoleCombinationPolicyTest` | 消费者/服务人员可并存，运营/管理员不可叠加 | 4 |
| `JwtTokenServiceTest` | 签发、解析、过期、篡改、密钥长度下限 | 5 |
| `MatchRankingTest` | STREET 优先、priority、effective_from、AMBIGUOUS | 6 |
| `ServiceAreaStatusTest` | 状态机合法迁移 | 4 |
| `AddressMaskingTest` | 姓名/手机号/地址脱敏 | 3 |
| `AuthApplicationServiceTest` | 注册建档案、重复注册、改密、角色切换 | 7 |
| `CustomerAddressServiceTest` | 首条默认、清默认→提升→软删（`InOrder`） | 7 |

批次 2 新增的单元测试（计价部分是纯函数测试，不需要 Mock，也没有一个数据库调用）：

| 测试类 | 覆盖点 | 数量 |
| --- | --- | --- |
| `PriceCalculatorTest` | 固定价取整、阶梯左闭右开边界、不封顶档、缺档报错、重叠档报错、相邻档合法、附加项累加、时长为 0 钳到 1 | 12 |
| `PricedTierTest` | `contains` 边界、`usesFixedPrice`、`overlaps` 的四种相对位置 | 6 |
| `ServiceItemStatusTest` | DRAFT/PUBLISHED/OFFLINE 合法迁移与非法迁移 | 4 |
| `PriceQuoteServiceTest` | 固定价无选项、缺必选项、单选组超选、未知选项、未知选项值、重复选择、未上架、按 `numericValue` 命中面积档 | 8 |

批次 2 workforce / scheduling 新增的单元测试（全部不依赖外部基础设施）：

| 测试类 | 覆盖点 | 数量 |
| --- | --- | --- |
| `EligibilityEvaluatorTest` | 资格判定的账号、审核、技能、证书、区域各原因与短路 | 14 |
| `CertificateValidityTest` | 证书有效期与可用性推导 | 8 |
| `WorkStatusTest` | 人员在岗状态机 | 10 |
| `SlotStatusTest` | 时段状态机 5×5 迁移矩阵穷举、订单占用、按库值解析 | 7 |
| `WindowTimelineTest` | 区间合法性、左闭右开重叠、批量校验、时长匹配边界 | 16 |

批次 3 新增的单元测试（全部不依赖外部基础设施）：

| 测试类 | 覆盖点 | 数量 |
| --- | --- | --- |
| `BookingStatusTest` | 订单 7 状态迁移矩阵穷举、cancellable/reschedulable/paid 判定、按库值解析 | 9 |
| `CancellationFeeCalculatorTest` | 24h 边界免费、费率/固定金额主导、上取整、封顶、已开始收费、0 窗口全收费 | 8 |

业务闭环不写成 Testcontainers 集成测试，而是 `scripts/dev/smoke-batch1.py` 与 `smoke-batch2.py`：
需要真实 MySQL + 真实 HTTP 栈才有意义，用容器跑一遍反而验证不到"迁移后的种子数据对不对"。
批次 2 尤其如此——阶梯档位的 `numeric_value` 填错只有在真实库上跑报价才会暴露。

## 本次验证记录（2026-10-23 · 批次 1）

| 验证项 | 结果 |
| --- | --- |
| `mvn clean verify` | **BUILD SUCCESS**，Tests run: 65, Failures: 0, Errors: 0 |
| Flyway V1 + V2 在空库执行 | 通过，21 张表，`flyway_schema_history` 两条记录均 success=1 |
| 种子数据 | 4 角色 / 5 权限 / 7 条角色权限 / 6 个行政区 / 2 个片区 + 2 条覆盖 |
| 真实启动（本机 MySQL 8.0.37，无 Redis/MinIO/RocketMQ） | 通过，20s 内就绪 |
| `scripts/dev/smoke-batch1.py` | **20 项断言全部通过** |
| 其中：服务区域匹配 | 五山街道 → 天河区五山片区（STREET）；员村街道 → 天河区核心片区（DISTRICT） |
| 其中：默认地址唯一 | 库内 `active_default_customer_id` 一条为 2、一条为 NULL，唯一索引生效 |
| 其中：越权隔离 | 账号 B 读账号 A 的地址返回 404 `ADDRESS_NOT_FOUND` |
| 其中：改密失效 | `token_version` 0 → 1，旧令牌访问 `/auth/me` 返回 401 `AUTH_INVALID_TOKEN` |
| Flyway / Redis / MinIO 集成测试 | 已编写，**未执行**（本机无 Docker，Testcontainers 自动跳过） |

> 环境说明：本机无 Docker，也没有可用的 Redis；冒烟时用了一个临时 MySQL 实例（端口 3307，
> 数据目录在 `%TEMP%\hestia-mysql-data`），因此 `/actuator/health` 因 Redis 不可达返回 DOWN，
> 这是**如实反映**而不是缺陷——Redis 确实是本项目的必需依赖（缓存与令牌撤销）。

## 本次验证记录（2026-10-23 · 批次 2 · catalog）

| 验证项 | 结果 |
| --- | --- |
| `mvn clean verify`（批次 2 完成后全量） | **BUILD SUCCESS**，Tests run: 95, Failures: 0, Errors: 0，用时 1:33 |
| 编译规模 | 234 个源文件（含新增 catalog 包与测试） |
| Flyway V3 增量执行（在已有 V1+V2 的库上） | 通过 |
| Flyway V1→V3 在空库执行 | 通过，**32 张表**，`flyway_schema_history` 三条记录均 success=1 |
| 种子数据 | 4 个分类（1 父 + 3 子）/ 2 个已上架服务项 / 2 组选项 + 6 个选项值 / 4 档面积阶梯 / 服务标准 6 + 4 条 |
| `scripts/dev/smoke-batch2.py`（公开侧 15 项） | 通过：分类树 1+3、已上架 2 项、分类过滤、详情含 6 条标准、固定价 199 |
| 其中：附加项与时长 | 199 + 140 = 339，时长 240 + 50 = 290 分钟 |
| 其中：面积阶梯 | 4 档分别报价 399 / 499 / 599 / 720（不封顶档按单价 × 面积） |
| 其中：拒绝路径 | 缺必选项 400、未知选项值 400、未知服务项 404、未登录 401、越权 403 |
| `smoke-batch2.py`（运营侧 11 项） | 通过：草稿不可见 → 无标准上架被拒 `SERVICE_STANDARD_REQUIRED` → 配标准后上架 → 可见 → 旧版本号 409 → 下架 → 不可见 → 重叠阶梯 409 |
| 批次 1 回归（`smoke-batch1.py`） | **20 项断言仍然全部通过** |

> 期间发现并修正的一处**种子自相矛盾**：`SVC202610230002` 一开始被种成 `PUBLISHED` 却没有任何
> 服务标准，违反模块自己"上架必须有标准"的规则。已补 4 条标准并重建空库重跑全量迁移。
> 这类问题单测抓不到（单测不读种子），只有对着真实库做一次完整性检查才会露出来。

## 本次验证记录（2026-10-23 · 批次 2 · workforce + scheduling）

| 验证项 | 结果 |
| --- | --- |
| `mvn clean verify`（批次 2 完成后全量） | **BUILD SUCCESS**，Tests run: 153, Failures: 0, Errors: 0 |
| Flyway V4 增量执行 | 通过：`service_window` / `worker_schedule` / `worker_slot` 3 张表 + 3 个种子时间窗 |
| `scripts/dev/smoke-workforce.py`（22 项） | 通过：开户 → 激活 → 未配置不可派单（原因齐全）→ 证书审核 → 技能/片区 → 入职 → 可派单 → 暂停联动角色失效 → 离职终态 |
| `scripts/dev/smoke-scheduling.py`（27 项） | 通过：种子窗口 → 未排班不可约（时长不足的窗口整窗过滤）→ 时间窗重叠/编码重复/版本冲突 → 批量排班生成 AVAILABLE → 排班后可用性 true → 自助报班/请假 → 运营替请假/停排/修改排班 → 越权 403 |
| 批次 1/2 回归（`smoke-batch1.py` / `smoke-batch2.py`） | **20 项 / 26 项断言仍然全部通过** |

> 期间冒烟抓出的两个真实 bug（单测覆盖不到，因为它们只在 HTTP 响应组装时出现）：
> 1. **insert 后 version 回填缺失**：MyBatis-Plus 只回填 id，`service_window.version` 的数据库默认值
>    不写回实体，新建窗口响应组装时 NPE → 500。已在 `toView` 做 null 保护。
> 2. **条件更新后内存实体未同步**：`markLeave` 用 `blockIfFree` 条件 SQL 更新了库，
>    但响应用的是更新前的实体，客户端看到 `slotStatus=AVAILABLE` 的"已请假"排班。
>    已在更新成功后同步状态。教训写进了关键设计说明第 26 条。

## 本次验证记录（2026-10-23 · 批次 3 第一轮 · booking + dispatch 骨架）

| 验证项 | 结果 |
| --- | --- |
| `mvn clean verify`（批次 3 第一轮后全量） | **BUILD SUCCESS**，Tests run: 170, Failures: 0, Errors: 0 |
| Flyway V5 增量执行 | 通过：9 张表（booking 5 + dispatch 4）+ 默认派单策略种子 `POL_DEFAULT` |
| `scripts/dev/smoke-booking.py`（24 项） | **全部通过**：预览价确定性 → 下单 PENDING_PAYMENT（价与预览一致）→ 同键重放同单 → 同键改内容 409 `IDEMPOTENCY_CONFLICT` → 跨客户读 404 → 下单冻结时段（可用性聚合变 false）→ 取消待支付 + 摘要同步 CANCELED → 重复取消 409 → 时段释放恢复可约 → 支付金额不符 422 → 支付成功 CONFIRMED + 派单 PENDING_ACCEPT + 锁定人员 → 重复回调幂等 → 改期换日且派单保持 → 人员任务列表可见 → 已支付远期取消 → 支付超时扫描自动关单（`PAYMENT_TIMEOUT`）→ 运营列表含全部订单 |
| 回归 `smoke-batch1.py` | 20 项全部通过 |
| 回归 `smoke-batch2.py` | 15 项通过（1 项按设计跳过） |
| 回归 `smoke-workforce.py` | 22 项全部通过 |
| 回归 `smoke-scheduling.py` | 27 项全部通过（改为逐日探测"无遗留排班"的干净日期） |

> 期间冒烟抓出的三个真实缺陷（单测覆盖不到，因为都只在"完整业务链路 + 真实库"上出现）：
> 1. **幂等回放不比对 request_hash**：同键改数量重放拿到 200 + 旧订单响应，
>    违反 docs/07 §12.5"同键不同内容必须冲突"。已加 hash 校验（关键设计说明第 28 条）。
> 2. **支付激活不回填订单**：`activateForPayment` 只推进派单表，订单的
>    `current_worker_id` / `assignment_status` 摘要列停在 WAITING_PAYMENT，
>    人员任务列表恒为空。已改为激活返回 workerId、booking 侧同事务 patch。
> 3. **取消/超时不同步摘要列**：取消后 `assignment_status` 停在 PENDING_ACCEPT。
>    已在取消链路（客户取消 + 超时任务）同步 CANCELED。

> 冒烟脚本之间的脏数据免疫也在这轮建立：smoke-booking 每轮遗留的排班
> （订单取消后时段释放、排班仍在）会污染 smoke-scheduling 的"未排班 → 不可约"断言。
> booking 用时间戳轮转远期日期（`today + 5 + ts % 40`），scheduling 改为
> `find_clean_dates()` 逐日探测——两套脚本从此可以在同一个库上任意顺序反复执行。

## 下一步（批次 3 第二轮：payment / dispatch 接单 / 履约售后）

1. **payment**：支付单（`payment_order`）、回调验签、退款与对账；
   `BookingStatusCommandService.markPaid` 已预留为支付回调内部命令，接入后替换 smoke 端点。
2. **dispatch 接单**：人员接单 / 拒单 API（`PENDING_ACCEPT → ACCEPTED/REASSIGNING`）、
   接单超时自动改派、评分派单策略（替换简单递减分）。
3. **履约与售后**：出发/开始/完成打卡（`EN_ROUTE → IN_SERVICE → PENDING_CONFIRM → COMPLETED`）、
   售后申请与退款闭环（`after_sale_status`）。
4. **事件化**：订单状态变更写 `event_outbox`（`BOOKING_PAID` / `BOOKING_CANCELED` 等），
   打通已有 Outbox → RocketMQ 链路。

已知待补（非阻塞）：

- 行政区字典目前只有广州 6 条种子，`AdminRegionController` 的批量导入接口需要真实数据才能验收。
- 运营端片区管理接口（`/api/v1/operator/service-areas`）尚未写冒烟脚本。
- `service_qualification_requirement` 已有运营端配置入口与资格判定闭环，真实证书规则数据待业务确认。

