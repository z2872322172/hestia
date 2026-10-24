# Hestia 家政 · 用户端 UI 设计说明

> 设计稿：`hestia-app-ui.html`（浏览器直接打开，左侧点页面切换，页面内元素可点击跳转）
> 设计基准：360 × 780 vp（HarmonyOS 手机），A4 论文截图建议用 3× 缩放

---

## 1. 设计定位

家政服务的核心情绪是**温暖、干净、可信**，不是"科技感"。所以：

- 主色用暖橘（来自 Hestia 炉灶女神的火），传递居家温度；
- 底色用米白而非纯白，比纯白柔和，贴合家居场景；
- 全站只有一个品牌色，语义色（成功/警告/危险）只用在状态标签上，保证色彩统一；
- 不用大面积深色、不用高饱和撞色，避免"促销感"。

App 名：**Hestia 家政**；Slogan：**让每一次归家，都干净如初**。

---

## 2. 视觉规范

### 2.1 色板

| 用途 | 色值 | 说明 |
| --- | --- | --- |
| 主色 Brand | `#F0653A` | 按钮、价格、选中态、品牌头 |
| 主色深 | `#DC5528` | 按钮按下态、深色小标签 |
| 主色浅 | `#FFE7DC` | 标签底、图标底 |
| 主色极浅 | `#FFF4EF` | 选中行底色 |
| 正文 | `#1C1917` | 标题、主要信息 |
| 次要文字 | `#6B6560` | 描述、说明 |
| 辅助文字 | `#A29A93` | 时间、计数、提示 |
| 分割线 | `#F0EAE3` | 卡片内分隔 |
| 页面底色 | `#FBF8F5` | 米白 |
| 卡片 | `#FFFFFF` | |
| 成功 | `#1F9D6B` | 已完成、可服务 |
| 警告 | `#D98324` | 待支付、倒计时 |
| 危险 | `#D64545` | 未读点、错误 |

分类图标底色（低饱和，保持统一）：
保洁橙 `#FFE3D2`、深度绿 `#DDF0E6`、开荒蓝 `#E4E9F7`、暖沙 `#FBE9D8`。

### 2.2 字号与字重

| 层级 | 字号 | 字重 | 用途 |
| --- | --- | --- | --- |
| 页面大标题 | 26 | 600 | 登录页"欢迎回来" |
| 区块标题 | 15 | 600 | "热门服务""服务标准" |
| 卡片标题 | 14 | 500 | 服务名 |
| 正文 | 13 | 400 | 描述 |
| 次要 | 12 | 400 | 补充说明 |
| 辅助 | 11 | 400 | 时间、提示 |
| 价格大 | 22–28 | 600 | 详情页价格 |
| 价格小 | 16 | 600 | 列表卡片价格 |

价格与所有数字统一用 `tabular-nums`，避免跳动。

### 2.3 间距与圆角

- 页面左右边距：16
- 卡片内边距：14
- 卡片间距：10–12
- 圆角：卡片 16、按钮 14、图标底 13–14、标签 6、手机屏 38
- 底部操作栏高度：按钮 46 + 上下 10/14

### 2.4 组件约定

- **卡片**：白底、圆角 16、**无投影**（只用极浅边框/底色区分层次，投影会显廉价）
- **主按钮**：主色实心、圆角 14、白字 15/500
- **次按钮**：白底 + 1px 描边
- **状态标签**：圆角 6、10.5 号字、浅底深字
- **价格**：`¥` 号小、数字大，`/次`、`起` 用 11 号辅助色

---

## 3. 页面结构与课程要求对照

课程要求：≥4 个 Tab + 若干内容页 + 详情页，且必须有欢迎页、登录页、首页、列表页、详情页、我的页面。

| 要求 | 本方案对应 | 说明 |
| --- | --- | --- |
| 4 个 Tab | 首页 / 订单 / 消息 / 我的 | 4 个，符合"至少 4 个" |
| 欢迎页 | 01 欢迎页 | 品牌 + slogan + 开始使用 |
| 登录页 | 02 登录 / 注册 | 手机号 + 密码，含第三方与协议 |
| 首页 | 03 首页（Tab1） | 定位、搜索、分类宫格、Banner、热门服务、保障、评价 |
| 列表页 | 07 服务列表 | 分类筛选 + 排序 + 服务卡片 |
| 详情页 | 08 服务详情 | 轮播、价格、服务标准、评价、预约入口 |
| 我的 | 06 我的（Tab4） | 资料、订单入口、功能列表 |
| 若干内容页 | 09 预约确认、10 支付、11 订单详情、12 评价、13 地址管理、05 消息 | 补齐业务闭环 |

共 **14 个页面**。

### 页面清单

| 编号 | 页面 | 类型 | 核心内容 |
| --- | --- | --- | --- |
| 01 | 欢迎页 | 入口 | Logo、slogan、开始使用、已开通区域 |
| 02 | 登录 / 注册 | 入口 | 手机号密码、记住手机号、协议 |
| 03 | 首页 | Tab | 定位、搜索、4 分类宫格、新客券、热门服务、服务保障、评价 |
| 04 | 订单 | Tab | 状态 Tab、订单卡片、改期/取消/支付操作 |
| 05 | 消息 | Tab | 订单通知/系统消息筛选、未读点、全部已读 |
| 06 | 我的 | Tab | 头部数据、订单快捷入口、地址/优惠券/客服/设置 |
| 07 | 服务列表 | 内容 | 排序、筛选、服务卡片（价格/销量/评分） |
| 08 | 服务详情 | 详情 | 大图、价格、服务标准 6 项、维度评分、立即预约 |
| 09 | 预约确认 | 内容 | 地址、日期、时段、加项、费用明细 |
| 10 | 支付 | 内容 | 金额、倒计时、支付方式、支付成功态 |
| 11 | 订单详情 | 详情 | 状态头、服务人员、6 段进度时间线、服务信息、费用 |
| 12 | 评价 | 内容 | 总分 + 4 维度星级、文字、图片、匿名 |
| 13 | 地址管理 | 内容 | 地址卡、默认标签、片区校验结果、增删改 |

---

## 4. 页面与后端接口映射

接口前缀 `/api/v1`，响应统一 `{ code, message, data, traceId }`，分页 `PageResult`。

| 页面 | 接口 | 用途 |
| --- | --- | --- |
| 欢迎页 | — | 本地读 token，有则直接进首页 |
| 登录页 | `POST /auth/login` | 手机号密码登录 |
| | `POST /auth/customer/register` | 注册（同事务建账号 + 角色 + 客户档案） |
| 首页 | `GET /public/service-categories` | 分类树（宫格取二级分类） |
| | `GET /public/service-items` | 热门服务（按销量/排序取前 N） |
| 服务列表 | `GET /public/service-items?categoryNo=` | 按分类过滤已上架服务项 |
| 服务详情 | `GET /public/service-items/{itemNo}` | 详情：选项、阶梯、服务标准 |
| 预约确认 | `GET /customer/addresses` | 地址列表，取默认地址 |
| | `GET /public/availability?serviceItemNo=&serviceAreaId=&dateFrom=&dateTo=` | 可约时段（区间 ≤ 14 天） |
| | `POST /public/service-items/{itemNo}/price-preview` | 报价试算（请求体不含金额） |
| 支付 | `POST /customer/bookings/preview` | 下单前价格预览 |
| | `POST /customer/bookings` | 创建订单，**必须带 `Idempotency-Key` 头** |
| | `POST /customer/bookings/{orderNo}/pay` | 发起支付 |
| 订单列表 | `GET /customer/bookings?status=&page=&size=` | 我的订单分页 |
| 订单详情 | `GET /customer/bookings/{orderNo}` | 金额明细、派单状态、取消原因 |
| | `POST /customer/bookings/{orderNo}/cancel` | 取消（待支付免费，已支付按快照算违约金） |
| | `POST /customer/bookings/{orderNo}/reschedule` | 改期（仅 CONFIRMED） |
| 消息 | `GET /notifications?onlyUnread=` | 收件箱分页 |
| | `GET /notifications/unread-count` | Tab 红点数字 |
| | `PUT /notifications/{notificationNo}/read` | 标记已读 |
| | `PUT /notifications/read-all` | 全部已读 |
| 我的 | `GET /auth/me` | 当前账号（手机号脱敏） |
| | `GET /customer/profile` | 客户档案 |
| 评价 | `POST /customer/bookings/{orderNo}/reviews` | 提交评价（总分 + 维度分） |
| | `GET /customer/reviews` | 我的评价 |
| 地址管理 | `GET/POST /customer/addresses` | 列表 / 新增（首条自动默认） |
| | `PUT/DELETE /customer/addresses/{addressNo}` | 修改（乐观锁）/ 软删除 |
| | `PUT /customer/addresses/{addressNo}/default` | 设为默认 |
| | `POST /customer/addresses/{addressNo}/availability-check` | 重新匹配服务区域 |
| 售后（订单详情底部） | `POST /customer/bookings/{orderNo}/after-sales` | 申请售后 |

### 订单状态 → 界面文案

后端 `BookingStatus` 枚举（7 个）：

| 枚举值 | 界面文案 | 标签色 |
| --- | --- | --- |
| `PENDING_PAYMENT` | 待支付 | 警告色 |
| `CONFIRMED` | 待服务 | 警告色 |
| `EN_ROUTE` | 服务人员已出发 | 主色 |
| `IN_SERVICE` | 服务中 | 主色 |
| `PENDING_CONFIRM` | 待你确认 | 主色 |
| `COMPLETED` | 已完成 | 辅助色 |
| `CANCELED` | 已取消 | 辅助色 |

> 设计稿订单 Tab 里的"全部/待支付/待服务/服务中/已完成"是**前端聚合分组**，
> 后端只有 7 个状态，`CONFIRMED + EN_ROUTE` 归入"待服务"，`PENDING_CONFIRM` 归入"服务中"。

### 评价维度

后端 `review_dimension` 种子固定 4 个，设计稿严格对应：
`SERVICE 服务效果` / `ATTITUDE 服务态度` / `QUALITY 服务质量` / `PUNCTUALITY 准时上门`，
均为 0–5 分、权重各 0.25。**不要在前端硬编码维度名，从接口读。**

---

## 5. HarmonyOS 实现要点

### 5.1 工程结构建议

```
entry/src/main/ets/
├─ pages/
│  ├─ WelcomePage.ets        01
│  ├─ LoginPage.ets          02
│  ├─ MainPage.ets           承载 Tabs（03–06）
│  ├─ ServiceListPage.ets    07
│  ├─ ServiceDetailPage.ets  08
│  ├─ BookingPage.ets        09
│  ├─ PaymentPage.ets        10
│  ├─ OrderDetailPage.ets    11
│  ├─ ReviewPage.ets         12
│  └─ AddressPage.ets        13
├─ view/tabs/                HomeTab / OrderTab / MessageTab / ProfileTab
├─ components/               ServiceCard / OrderCard / StatusTag / PriceText / StepTimeline
├─ model/                    Category / ServiceItem / Booking / Notification / Address
├─ api/                      ApiClient（统一拦截 code / traceId）+ 各域 Service
├─ common/                   Theme（色板常量）/ Router / Storage
└─ mock/                     MockData（后端未启动时的兜底数据）
```

### 5.2 主题常量

把第 2 节的色板写成 `common/Theme.ets`，**不要在页面里写死色值**，否则后期换色要改十几个文件。

### 5.3 四个 Tab

用 `Tabs` + `TabContent` + 自定义 `tabBar`（`@Builder`），不要用默认样式——
默认 TabBar 的图标尺寸和间距跟设计稿对不上。底部红点用 `Badge` 组件包住图标。

### 5.4 状态栏

首页、我的、订单详情三个页面是**沉浸式**（主色背景延伸到状态栏下）。
在 `onPageShow` 里调 `window.setWindowSystemBarProperties` 把状态栏文字设为白色，
离开时改回深色。设计稿已按这个效果出图。

### 5.5 数据层

- 统一 `ApiClient` 里处理 `code !== 'OK'` 抛错，`traceId` 打到日志，方便答辩演示排查；
- token 存 `Preferences`，`401` 时清 token 跳登录页；
- 后端未启动时用 `mock/` 兜底，保证答辩现场网络异常也能演示 UI。

### 5.6 下单幂等

`POST /customer/bookings` 必须带 `Idempotency-Key` 头（UUID）。
这是后端三段式幂等的入口，**同键改内容会返回 409**——演示时别重复点提交按钮，
按钮点击后要立刻 `disabled`。

---

## 6. 设计稿里的示例数据说明

设计稿中的文案尽量对齐后端真实种子数据，但有几处是为了展示效果补充的，
接入前需要补种子数据（或改文案）：

| 位置 | 设计稿 | 后端现状 |
| --- | --- | --- |
| 首页/列表服务项 | 日常保洁·两居室 ¥199、深度保洁·按面积 ¥399 起 | ✅ 已有种子 |
| 列表"三居室 ¥269""单间 ¥139" | 用于展示列表 | ❌ 需补种子（或删掉这两个卡片） |
| 开荒保洁分类 | 首页宫格第 3 个 | ✅ 分类有种子，但**无已上架服务项**，点进去会是空列表 |
| 增值服务 | 冰箱清洗 +80、玻璃清洗 +60、油烟机拆洗 +120 | 前两项✅已有，油烟机需补 |
| 服务标准 6 项 | 客厅/厨房/卫生间… | ✅ 已有 6 条标准 |
| 评价维度 4 项 | 服务效果/态度/质量/准时 | ✅ 已有种子 |
| 行政区 | 天河区五山街道 | ✅ 已有种子 |
| 服务人员"李秀兰 4.9·326 单" | 订单详情展示 | 需补 `worker_profile` 数据 |

**建议**：先跑 `smoke-batch1/2/workforce/scheduling/booking` 把数据灌起来，
再从真实库导出 JSON 做 mock，这样设计稿和真机效果一致，答辩更稳。

---

## 7. 后续可选增强

1. **骨架屏**：列表和详情加载时用灰色块占位，比转圈体面。
2. **空状态**：无订单、无消息、无地址各画一个插图 + 一句引导，答辩演示"没数据"时不会尴尬。
3. **下拉刷新**：订单列表和消息列表加 `Refresh` 组件。
4. **订单进度动效**：服务中状态的时间线圆点做呼吸动画，演示时很抓眼球。
5. **深色模式**：色板已按单一主色设计，加一套深色变量即可，属于加分项。
