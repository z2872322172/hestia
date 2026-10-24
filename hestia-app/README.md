# Hestia 家政 · HarmonyOS 客户端

毕业设计《基于 Spring Boot 的家政服务预约平台》的移动端部分，配套后端在 `../hestia-backend`。

设计基线见 `../docs/08-鸿蒙客户端设计.md`，本工程严格按该文档实现。

---

## 1. 环境要求

| 项 | 版本 |
| --- | --- |
| DevEco Studio | 5.0.0 Release 及以上 |
| HarmonyOS SDK | API 12（5.0.0） |
| 设备 | 手机模拟器或真机（HarmonyOS NEXT） |

工程 `compatibleSdkVersion` 为 `5.0.0(12)`，如果本地 SDK 版本不同，打开工程时 DevEco 会提示同步，按提示改即可。

---

## 2. 打开与运行

1. DevEco Studio → **Open** → 选择 `hestia-app` 目录（**不要**选里面的 `entry`）。
2. 等待 Sync 完成（首次会下载 hvigor 依赖）。
3. 配置签名：`File → Project Structure → Signing Configs` → 勾选 **Automatically generate signature**（需登录华为账号）。
4. 选设备 → Run。

> 工程没有提交签名文件。不配签名只能跑模拟器预览，装不到真机。

---

## 3. 后端地址（第一次运行必改）

位置：`entry/src/main/ets/api/ApiClient.ets`

```ts
static readonly BASE_URL: string = 'http://10.0.2.2:8080/api/v1';
```

| 运行环境 | 应该填什么 |
| --- | --- |
| DevEco 模拟器 | `http://10.0.2.2:8080/api/v1`（默认值，10.0.2.2 是模拟器访问宿主机的固定地址） |
| 真机 + 同一 WiFi | `http://<电脑局域网IP>:8080/api/v1`，例如 `http://192.168.1.8:8080/api/v1` |
| 真机 + USB | `hdc rport tcp:8080 tcp:8080` 后用 `http://127.0.0.1:8080/api/v1` |

真机调试时确认：

- 后端监听 `0.0.0.0`（Spring Boot 默认就是）；
- Windows 防火墙放行 8080；
- 手机和电脑在同一网段。

**明文 HTTP 已经配好了**（`resources/base/profile/network_config.json` + `module.json5` 的 metadata），不用再改。如果删了这两处，所有请求都会失败且报错信息看不出原因。

---

## 4. 启动后端

```bash
cd ../hestia-backend
# 需要 MySQL 8 + Redis，本机没装 Docker 的话用 demo profile 或本地 MySQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

后端起来后先验证：

```bash
curl http://localhost:8080/api/v1/system/ping
curl http://localhost:8080/api/v1/public/service-categories
```

第二个能返回分类数据，App 首页才有内容。

---

## 5. 页面与路由

| 路由名 | 页面 | 入口 |
| --- | --- | --- |
| `WELCOME` | 欢迎页 | 冷启动 |
| `LOGIN` | 登录 / 注册 | 欢迎页 / 退出登录 |
| `MAIN` | Tab 容器 | 登录成功 / 已有 token |
| `SERVICE_LIST` | 服务列表 | 首页宫格、查看全部 |
| `SERVICE_DETAIL` | 服务详情 | 列表卡片、首页热门 |
| `BOOKING` | 预约确认 | 详情页"立即预约" |
| `PAYMENT` | 支付 | 下单成功、待支付订单 |
| `ORDER_DETAIL` | 订单详情 | 订单列表、消息 |
| `REVIEW` | 评价 | 订单详情、订单卡 |
| `ADDRESS` | 地址管理 | 我的、预约页选地址 |

4 个 Tab 在 `MAIN` 内：首页 / 订单 / 消息 / 我的。

---

## 6. 目录结构

```
entry/src/main/ets/
├─ entryability/EntryAbility.ets   全屏布局 + Preferences 初始化
├─ pages/
│  ├─ Index.ets                    唯一 @Entry，承载 Navigation
│  ├─ WelcomePage.ets              01
│  ├─ LoginPage.ets                02
│  ├─ MainPage.ets                 03–06 的 Tab 容器
│  ├─ ServiceListPage.ets          07
│  ├─ ServiceDetailPage.ets        08
│  ├─ BookingPage.ets              09
│  ├─ PaymentPage.ets              10
│  ├─ OrderDetailPage.ets          11
│  ├─ ReviewPage.ets               12
│  └─ AddressPage.ets              13
├─ view/tabs/                      HomeTab / OrderTab / MessageTab / ProfileTab
├─ components/                     NavIcon / PriceText / StatusTag / ServiceCard
│                                  OrderCard / StepTimeline / SectionHeader / EmptyState
├─ model/                          ApiTypes / Catalog / Booking / Customer
│                                  Message / Review / Enums
├─ api/                            ApiClient + 各域 Api
├─ mock/MockData.ets               后端不可用时的兜底数据
└─ common/                         Theme / IconPath / Nav / RouteName
                                   Storage / Format / Toast / Bar
```

---

## 7. 实现状态

### 已完成（13 个页面全部可跑）

| 能力 | 说明 |
| --- | --- |
| 4 Tab 导航 | 自定义 tabBar，消息未读红点 |
| 登录 / 注册 | 手机号密码，记住手机号，错误码映射 |
| 首页 | 分类宫格、热门服务来自真实接口 |
| 服务列表 | 分类筛选 + 本地排序（综合/价格/时长） |
| 服务详情 | 规格选择、服务标准分组、真实价格 |
| 预约 | 地址、14 天时段（真实排班）、服务端报价 |
| 下单 | `Idempotency-Key` 防重复提交 |
| 支付 | 倒计时、支付成功态 |
| 订单 | 5 个分组、取消、确认完成 |
| 订单详情 | 6 段进度时间线（真实履约数据） |
| 评价 | 4 维度星级、总分自动计算 |
| 地址 | 增删改、设默认、片区校验 |
| 消息 | 筛选、已读、全部已读 |
| 断网降级 | 读接口回落 Mock，页面顶部显示"演示数据" |

### 明确没做（不是忘了，是后端没有对应接口）

| 功能 | 原因 |
| --- | --- |
| 评价图片上传 | 后端只有 `/system/smoke/file`，prod 下 `denyAll`，没有正式的客户文件接口 |
| 地址省市区三级联动 | 后端只有 `/public/service-areas` 和 `/public/service-area-match`，行政区固定为广州天河 |
| 改期 | 后端有 `POST /customer/bookings/{orderNo}/reschedule`，但需要重新选时段，本期在订单详情里只做提示 |
| 服务人员姓名/评分 | `BookingDetail` 只返回 `currentWorkerId`，没有客户侧的 worker 详情接口 |
| 首页真实评价 | 后端没有公开评价列表接口，用的是静态示例文案 |
| 优惠券 | 后端无优惠券模块 |

这些在设计文档第 13 节都有登记和应对方案。

---

## 8. 几个必须知道的坑

### 8.1 支付渠道只能是 MOCK

`PaymentPage` 界面上有微信/支付宝/余额三个选项，但提交时 `channel` 固定传 `'MOCK'`。

原因：后端 `PaymentApplicationService` 里有一句

```java
if (!gateway.channel().equals(channel)) { throw ... }
```

而首版只注册了 `MockPaymentGateway`（channel = `"MOCK"`）。传 `WECHAT` 会直接 400，报错是"支付渠道暂不可用"，很容易误判成后端没启动。

### 8.2 订单"待服务"要发两个请求

后端 `GET /customer/bookings?status=` 只接受**单个**状态值。前端"待服务" = `CONFIRMED` + `EN_ROUTE`，"服务中" = `IN_SERVICE` + `PENDING_CONFIRM`。

`BookingApi.listByStatuses()` 会发多个请求再合并排序。不要改成"取全部再前端过滤"，分页会乱。

### 8.3 评价维度的 dimensionScores 不能是 Map

`SubmitReviewBody.dimensionScores` 用的是固定字段的 `DimensionScores`（SERVICE/ATTITUDE/QUALITY/PUNCTUALITY），不是 `Map` 或 `Record`。

原因：`JSON.stringify(new Map())` 会输出 `{}`，后端收到空对象必然 400；而 ArkTS 严格模式又禁止动态索引赋值，拼不出 `{ [code]: score }`。

### 8.4 幂等键在页面初始化时生成

`BookingPage` 的 `idemKey` 是页面级成员，不是每次点击重新生成。这样"手抖点两次提交"只会建一单；用户返回修改内容后重进页面会拿到新键。

### 8.5 时间线数据不是假的

订单详情的 6 段进度来自 `GET /customer/bookings/{orderNo}/fulfillment`：

```
departedAt / checkInAt / startedAt / finishedAt / completionRequestedAt / customerConfirmedAt
```

未派单时该接口 404，代码里 `catch` 掉，时间线只显示前两段。

---

## 9. 自检脚本

本机没装 DevEco，无法真正编译，所以工程里带了一个静态检查脚本：

```bash
python .selfcheck.py
```

它检查四类问题：

1. 相对 import 的路径是否存在；
2. import 里点名的符号在目标文件里是否真的 export；
3. 括号/引号是否配对（已处理 `http://` 被误判成注释的问题）；
4. 已知的 ArkTS 严格模式写法问题（ForEach 用字面量数组、显式 any、展开运算符）。

**注意**：它不能替代真机编译。ArkTS 的类型检查、装饰器规则、组件属性合法性只有 DevEco 能验。

---

## 10. 答辩演示顺序

1. 冷启动 → 欢迎页
2. 登录（先故意输错密码看错误提示）
3. 首页（说明分类来自接口）
4. 切分类 → 列表 → 排序
5. 详情 → 选规格（说明价格由 `price-preview` 服务端算）
6. 预约 → 选日期看真实时段 → 提交
7. 支付 → 倒计时 → 支付成功
8. 订单详情 → 6 段进度时间线
9. 评价 → 4 维度打分
10. 我的 → 地址管理 → 退出登录
11. **关掉后端**，回首页看"演示数据"角标（这一手很加分）

---

## 11. 版本

v1.0.0 · 2026-10-24
