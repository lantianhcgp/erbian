# 「耳边」App 设计文档

**日期：** 2026-10-14
**状态：** 待评审
**架构方案：** 方案一（原生 Kotlin/Compose 单包，双服务内嵌）
**仓库：** `lantianhcgp/erbian`（私有）+ 独立本地目录 `~/erbian`

---

## 一、产品定位

个人使用的 Android 消息频道 App。**核心原则：App 是耳朵和嘴巴，脑子在 Agent（Hermes）侧。**

- 长按电源键唤醒（小爱式），浮层里输入文字（语音转写交给输入法）→ 发给 Agent 处理
- 界面形态 = 像 QQ 一样的**双向消息频道**：用户发记录，Agent 回回复/追问/确认
- 继承 AI 日记本的三件套、**砍掉日记**：
  1. 快速记录（随手一句话）
  2. 用户画像（Agent 提炼，App 只读展示）
  3. 待办提取 → 写入手机系统日历（混合确认策略）
- 打开 App 时收到一条**摘要**："上次之后发生了什么"（待办已建、日程已加、需要回应的事）
- 重要消息（待办确认、主动追问）**镜像推送 QQ/Telegram**（走 Agent 已有通道）
- 内嵌**全套 MCP 系统**（延续 lite-widget 定位：暴露 App 自己的文件系统 + 完整日志）

**不做：** 日记时间线、日/周/月报告、端侧 AI 分析、语音识别（输入法负责）。

---

## 二、唤醒链（逆向已实证，小爱 7.511.2 + HyperOS OS1.0/Android 13）

手机上存在两条长按电源键路径：

### 路径 A：MIUI 专用快车道（默认，写死小爱，第三方不可用）
```
BaseMiuiPhoneWindowManager 拦截
→ 单键规则 Settings.System.long_press_power_key = "launch_voice_assistant"
  （由小爱设置 Settings.System.long_press_power_launch_xiaoai 驱动）
→ ShortCutActionsUtils.launchVoiceAssistant()
→ Intent(ACTION_ASSIST).setPackage("com.miui.voiceassist")
    .setComponent(资源0x110f00a5指定的组件)   ← 写死
→ startForegroundServiceAsUser → 小爱 VoiceService
```

### 路径 B：AOSP 标准助理路径（设置驱动，完全开放 ← 我们走这条）
```
Settings.Global.power_button_long_press  (0=无 1=电源菜单 4=语音助理 5=助理)
→ PhoneWindowManager.powerLongPress()（SettingsObserver 实时监听）
→ case5: SearchManager.launchAssist() → 查 Settings.Secure.assistant /
         voice_interaction_service → 我们的 VoiceInteractionService 会话窗口
→ case4: 隐式 Intent(ACTION_VOICE_ASSIST)
```

### 切换方案（三处设置，Shizuku WRITE_SECURE_SETTINGS 可写，已实测）
| 设置项 | 原值 | 切换值 | 作用 |
|---|---|---|---|
| `Settings.System.long_press_power_key` | `launch_voice_assistant` | `none` | 关掉 MIUI 专用拦截 |
| `Settings.Global.power_button_long_press` | (null) | `5` | AOSP 走"助理"case |
| `Settings.Secure.assistant` + `voice_interaction_service` | 小爱 AssistInteractionService | 我们的 VoiceInteractionService | 助理目标指向我们 |

**实测证据：** 切换后 `logcat` 出现 `WindowManager: powerLongPress: eventTime=... mLongPressOnPowerBehavior=5`，且无 MIUI 拦截日志；恢复脚本已把原值写回验证通过。

**自愈机制：** 用户在小爱设置里拨回开关会把规则改回去 → App 集成 Shizuku API 做**启动时 + 前台时自检自愈**；Shizuku 不可用时设置页红条引导（不闪退不静默）。Agent 侧 rish 脚本兜底。

**待实现期验证：** behavior=5 的最后一跳（SearchManager.launchAssist → 会话窗口弹出）需服务注册后真机确认。

### App 注册内容（标准三件套）
- `AssistInteractionService`（BIND_VOICE_INTERACTION + `android.service.voice.VoiceInteractionService` intent-filter + `android.voice_interaction` meta-data）
- `VoiceInteractionSession`（承载浮层 UI；系统级窗口，锁屏可弹，零悬浮窗权限）
- Manifest 声明 `ACTION_ASSIST` / `VOICE_COMMAND` intent-filter

---

## 三、整体架构与数据流

```
┌───────────────────────────────────────────────┐
│ 耳边 App（单 APK，Kotlin + Compose）           │
│ ├ 表现层: 频道 / 画像 / 日程 / 设置（4页）       │
│ ├ 唤醒层: VoiceInteractionService + Session 浮层│
│ ├ 本地服务: HTTP server 0.0.0.0:8790           │
│ │   ├ REST: /api/message /api/profile /health  │
│ │   └ MCP: streamable-http（全套，见第五节）     │
│ ├ 数据: Room（频道消息/队列/画像缓存/待办缓存）   │
│ └ 权限桥: Shizuku API（设置自检自愈）           │
├───────────────────────────────────────────────┤
│ Termux 侧 = Hermes Agent（脑子）                │
│ ├ 上行: webhook 127.0.0.1:8644（HMAC）          │
│ ├ 回程/主动: curl 127.0.0.1:8790                │
│ ├ 处理: 待办提取→Shizuku写日历 / 画像更新 / 回复  │
│ └ 镜像: QQ/Telegram（已有网关通道）              │
└───────────────────────────────────────────────┘
```

### 数据流
1. **上行（发送）：** 浮层或频道输入 → `POST 127.0.0.1:8644/webhook/<name>`（HMAC 签名，prompt 模板携带原文 + 消息 id + 来源）→ 触发 Agent run
2. **处理：** Agent 提取待办（混合策略）→ Shizuku 写系统日历（专属日历「耳边待办」）→ 更新画像（Agent 侧 memory/fact_store）→ 组织回复
3. **回程（回复）：** Agent `POST 127.0.0.1:8790/api/message` → 频道插入消息（id 去重）
4. **主动/重要消息：** 同 3 + 镜像发 QQ/Telegram
5. **摘要：** App 每次进入前台（30 秒去重窗口）→ POST webhook（摘要请求）→ Agent 回推一条汇总消息（静默合并进频道，不弹通知；无新内容时 Agent 回"无变化"，App 不插入）
6. **画像同步：** Agent 侧画像更新时主动 POST `/api/profile` → App 缓存渲染
7. **镜像范围（明确）：** 仅 `kind ∈ {todo_confirm, proactive, error_notice}` 三类镜像发 QQ/Telegram；reply/summary 留在 App 频道内
8. **待办确认：** 含糊待办 → Agent 发确认消息（频道 + QQ 镜像）→ 用户在频道或日程页点确认/否决 → 回复走 webhook 回我

### 消息 Schema（App ↔ Agent 通用）
```json
{
  "id": "雪花id或uuid",
  "kind": "user_record | reply | proactive | summary | todo_confirm | todo_result | error_notice",
  "body": "文本",
  "ts": 1760000000000,
  "status": "sending | sent | failed",
  "meta": { "todo_id": "...", "confidence": 0.9, "origin": "float | channel" }
}
```
- 去重：按 id；Webhook 收到的消息落库后回 200，超时 30s 重试一次，仍失败标红可点重发
- 离线：Agent 不可达 → 消息进本地队列（Room），状态"通道离线"，连通后自动补投；摘要请求离线时降级为"本地未处理记录数 + 上次摘要"

---

## 四、唤醒浮层（VoiceInteractionSession）

- 形态：屏幕底部上滑出的**半高会话面板**（小爱式），系统级窗口
- 内容：多行文本输入框（默认聚焦，唤起输入法）+ 发送按钮 + 最近 1-2 条消息预览 + 关闭手势
- 交互：输入 → 发送 → 面板收起（乐观入库：先落库显示"发送中"，webhook 成功后变"已送达"）；失败则频道标红
- 不做录音、不做转写、不做建议气泡（YAGNI）

---

## 五、内嵌 Server 与 MCP（0.0.0.0:8790）

单端口双协议，共享 token 鉴权（`Authorization: Bearer <token>`，token 存本地 + 设置页可见）：

### REST
| 端点 | 方向 | 说明 |
|---|---|---|
| `POST /api/message` | Agent→App | 插入消息（reply/summary/todo_confirm 等 kind） |
| `POST /api/profile` | Agent→App | 全量画像卡推送（缓存渲染） |
| `GET /health` | 双向 | 连通性检查 |

### MCP（streamable-http，`/mcp`）
延续 lite-widget 定位——**暴露 App 自己的文件系统 + 完整日志**，本机 AI 连 127.0.0.1。工具面：

| 工具 | 说明 |
|---|---|
| `fs_list` / `fs_read` | 浏览/读取 App 数据目录（频道 DB 导出、缓存的画像 JSON、日志文件） |
| `log_tail` | 读取运行日志（网络、队列、自检动作全量记录） |
| `channel_history` | 结构化读取频道消息（含状态） |
| `queue_status` | 上行队列状态（积压/失败条目） |
| `settings_state` | 三件唤醒设置的实时值 + Shizuku 状态（自检结果） |
| `todo_state` | 待办缓存（已建/待确认/被否决） |

Agent 用途：排障（看日志）、兜底（队列卡住时读状态）、离线数据核对。

---

## 六、页面设计（全家桶 4 页）

底部导航 4 项（浮动药丸 dock，见第七节），页序：**频道 / 日程 / 画像 / 设置**。

### 1. 频道（首页）
- 双向对话流：用户消息右侧、Agent 消息左侧（配色区分），消息带状态（发送中/已送达/失败重发）
- 顶部：连接状态点（webhook 绿/红、Shizuku 绿/红）+ 摘要触发按钮（打开时自动请求）
- 底部：常驻输入框（发送同 webhook 通道）
- 消息长按：复制 / 重发 / "这条不对"（回给 Agent 修正）

### 2. 日程
- 上半：系统日历「耳边待办」只读列表（READ_CALENDAR，按日分组，含已过期折叠）
- 下半：**待确认含糊待办卡片**（确认/否决/改写）
- 空态：插画式空态 + "说点什么就会有"

### 3. 画像
- 只读卡片流：类别（价值观/性格/兴趣/情绪模式/基本信息…）+ 标题 + 内容 + 置信度条 + 证据摘要（折叠）
- 卡片底部"这条不对" → 输入一句话 → webhook 回 Agent 修正
- 顶部：最近更新时间 + 同步状态
- **不可手动编辑**（AI 日记本铁律：人不能改客观提取）

### 4. 设置
- 唤醒状态卡：三件设置实时值（表格）+ "修复"按钮（Shizuku 一键写回）+ 指纹级说明"改了会怎样"
- 权限引导卡：Shizuku（状态/去启动）、通知（POST_NOTIFICATIONS）、日历读取
- MCP 卡：端口、token（点按复制）、开关、连接数
- 通道卡：webhook 地址、连通测试按钮
- 关于：版本、开源许可

---

## 七、UI 设计语言（FlClash 源码研究结论）

研究对象：`chen08209/FlClash`（本地留档 `~/flclash-study`，raw 源码 `~/xiaoai_re/flclash_src/`）。提炼的基因，**全部采纳**：

### 颜色
- **Material 3 + 动态取色**（Dynamic Color / Material You，Android 12 壁纸取色）；浅色/深色跟随系统 + **纯黑 OLED 模式开关**（surface 置黑、容器色按 HCT tone 平移）
- 衍生色规则：`secondaryContainer/primaryContainer` 按亮度 blendDarken（浅色向黑、深色向白，因子 0.1-0.3）做次级底色
- 语义色：success=green、warning=orange，**均与 primary harmonize**；error 用 M3 error
- 透明度梯度统一：80/60/50/38/30/15/12/10/3；modalScrim=32%

### 形状（超椭圆体系）
- 圆角阶：`xs=4, sm=8, md=16, lg=20, xl=24, xxl=28, full=1000(药丸)`
- **超椭圆（squircle）而非普通圆角**：Compose 自实现 SuperellipseShape，不可行时退化为同级圆角
- 组件映射：card=16 / dialog=28 / bottomSheet=顶部28 / input=16 / chip=8 / snackbar=4 / **全部按钮=药丸** / 导航指示=药丸

### 动效
- Tab 切换：fade 220ms easeInOut（可选 slide）；**弹簧曲线**（SpringCurve）用于关键转场
- 浮层进场/出场用系统会话动画 + 自定义弹簧收尾

### 图标与排版
- **字形图标**（Material Symbols 可变字形，选中态 outline→filled 动画过渡）
- M3 typography（material2021），中文系统字体回退链
- 紧凑密度：小按钮 padding 8、icon 20、卡片间距 12

### 布局
- 移动端签名布局：**底部浮动药丸导航 dock**（NavigationDock 形态，非贴边 NavigationBar）——耳边的 4 页导航用这个
- 卡片流网格自适应（min 宽度 300、列距 12）
- 频道页例外：消息流 + 底部输入框时 dock 收起为贴底输入栏（导航在页内切换，避免双底栏）

### 落地约束
- Compose 实现超椭圆 `Shape`（描边+填充同路径）；动态取色用 `material3- adaptive` / `dynamiccolor` 库
- 图标用 Material Symbols 字体资源（带 filled 插值动画）

---

## 八、错误处理矩阵

| 场景 | 行为 |
|---|---|
| Termux/Hermes 没跑（发送时） | 本地队列 + 状态"通道离线" + 连通后自动补投；顶栏离线条 |
| webhook 超时 | 30s 超时，自动重试 1 次，仍失败标红、可点重发 |
| Shizuku 没起 | 设置页红条 + 引导启动；唤醒设置不自动改（不静默失败） |
| 唤醒设置被改回 | App 启动/前台时自检 → Shizuku 写回 → 频道提示"已修复唤醒设置" |
| 日历写入失败（Agent 侧） | Agent 在频道发 error_notice 附原文，不丢 |
| 消息重复投递 | id 去重（DB unique index） |
| MCP token 不符 | 401 + 日志记录 |
| App 被杀（消息在途） | 上行：webhook 由系统发出后不管；回程：丢失的消息由下次打开触发的摘要补齐（摘要必含"上次摘要后所有回复"） |

## 九、权限清单

| 权限 | 用途 | 获取方式 |
|---|---|---|
| INTERNET | 本地 socket 通信 | 安装时 |
| READ_CALENDAR | 日程页读「耳边待办」 | 运行时（拒绝则日程页降级为待办缓存视图） |
| POST_NOTIFICATIONS | 重要消息通知 | 运行时 |
| FOREGROUND_SERVICE / 特殊权限 | VoiceInteractionService 系统绑定 | 系统自动（签名级） |
| Shizuku（binder） | 写 settings | 运行时 Shizuku 授权 |
| 悬浮窗 | **不需要**（会话窗口是系统窗口） | — |

## 十、测试与验收

**单测：** 上行队列状态机、消息协议序列化/去重、设置自检 diff 逻辑、超椭圆 Shape 路径、混合待办确认策略（输入样例→决策）。

**真机验收清单：**
1. 长按电源唤醒（亮屏 + 锁屏两态）
2. 浮层输入 → 发送 → 我处理 → 频道回写（含 30s 内往返）
3. 明确待办 → 直写日历 + 频道报告（日历 App 可见「耳边待办」）
4. 含糊待办 → 先发确认卡片，确认后才写入
5. 在小爱设置拨开关 → App 自检修复唤醒设置
6. Termux 杀掉 → 发送排队 → 重启后补投
7. MCP 从 Termux 侧连通：log_tail / channel_history 可读
8. 重要消息 QQ 镜像到达
9. App 打开收到摘要消息
10. 纯黑模式 / 动态取色 / 浅深切换正常

## 十一、项目结构与构建

```
~/erbian/                     独立本地目录
lantianhcgp/erbian            私有仓
├ app/                        单 module（Kotlin + Compose）
│ ├ ui/                       频道/日程/画像/设置 + 浮层
│ ├ voice/                    VoiceInteractionService/Session
│ ├ server/                   内嵌 HTTP + MCP（Ktor CIO 引擎 + 官方 MCP Kotlin SDK）
│ ├ data/                     Room + 队列
│ └ shizuku/                  设置自检自愈
├ .github/workflows/          Actions 构建 APK（签名 keystore 存 Secret）
└ docs/                       设计文档副本
```

- 上行 webhook 订阅：`hermes webhook subscribe erbian-record ...`（prompt 模板 + HMAC）
- 交付：APK 落 `/storage/emulated/0/Documents/` 或用户指定目录
- 流程遵循：实现前先 writing-plans 出分步计划

## 附录 A：逆向产物留档
- 小爱 APK：`/storage/emulated/0/MT2/apks/超级小爱_7.511.2.0021.apk` + 工作区 `xiaoai.apk`
- manifest 解码：`~/xiaoai_re/manifest.txt`
- 框架 jar/dex/smali：`/storage/emulated/0/MT2/mcp/{services,miui-services,miui-framework}.jar`、`~/xiaoai_re/jardex/smali_*`
- 切换实验脚本：`~/rish/{probe2,switch_test,restore}.sh`
- rish 全套：`~/rish/`（RISH_APPLICATION_ID=com.termux）

## 附录 B：FlClash 研究产物
- 源码：`~/flclash-study/`（main 分支）
- 关键文件摘录：`~/xiaoai_re/flclash_src/`（theme/shape/motion/color/home/navigation/setting 等 21 个）
