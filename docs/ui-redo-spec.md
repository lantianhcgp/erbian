# 耳边 UI 重做规格（v3 · 高级感）

2026-006 参照源：FlClash（用户选定设计语言，本地21文件）+ RikkaHub 聊天结构（compose-mobile-ui skill）
+ ReadYou（Material You 工艺标杆，clone 中）。原则：修结构不修症状；一次做对。

## 0. 缺陷清单（来自用户两张截图，全部要消失）
1. 浮动 dock 与系统手势条冲突（无系统 inset 边距，贴底/被压）→ §2
2. dock 内图标文字拥挤、选中态弱、无呼吸感 → §2
3. 输入栏通贴边、无容器感 → §3
4. 状态栏与内容无分层（标题顶到时钟）→ §1
5. 气泡平淡无方向感、无时间/发送者层级 → §3
6. 整体缺深度与细节（无 hairline、无柔影、间距无节奏）→ §1/§4

## 1. 全局地基
- **edge-to-edge + 分层**：内容一律 `WindowInsets` 处理——topBar 加 `statusBarsPadding()`，
  底部一律 `navigationBarsPadding()`；任何贴边元素必须声明 inset 来源。
- **色**：动态取色保留；纯黑 OLED 保留；hairline = `outlineVariant × 0.5`；
  卡片/底色用 surfaceContainer 阶梯拉深度（页面底=surface，卡=surfaceContainerLow+hairline）。
- **字**：标题 titleMedium 600 + 字距 0；小节 labelLarge（primary 色，字距 +0.4）；
  正文 bodyMedium；辅助 labelSmall @50%。全部用 M3 字阶，不自造。
- **形**：药丸 full；卡 16；控件 12-20；气泡 18+方向尾 4。
- **动效**：页面 fade220（已有）；dock 选中=透镜 pill 位移 400ms spring（`tween(spring)`）；
  按压 scale 0.96 120ms；列表项入场 fade+8dp 上移。克制，无弹跳滥用。
- **柔影**（高级感核心，抄 FlClash `_dockShadows`）：
  层1 `Y+8 blur24 @8%`（暗色×3）、层2 `Y+1 blur3 @4%`——只给 dock、FAB、浮层。

## 2. 浮动 Dock（重写，根治导航栏问题）
- 高 62dp，左右 `21.dp`，**bottom = max(21dp, navigationBarsHeight)**；
  顶部留 8dp 影子空间；内容页 bottom inset = dock高 + bottom边距（滚动必须滚过 dock 下方而不是被它压住）。
- 结构：全屏 Box（不参与 scaffold 布局）→ 右下定位；pill = surfaceContainerHigh@92% + hairline + 双柔影。
- 项：icon24 + label10sp（gap2）；选中 = 底层 animated pill（primary@12%）+ icon/label 上 primary；
  未选中 onSurfaceVariant；按压整项 scale 0.96。
- contentDescription 契约保留："0"-"3"。

## 3. 频道页（RikkaHub 结构，skill 已有完整骨架）
- topBar：`statusBarsPadding` + 标题「频道」+ 右侧两颗 StatusDot（hairline 分隔或透明底）。
- 消息：行级列（尾随对齐），agent = surfaceContainerHigh 气泡 `18,18,18,4`，user = primaryContainer
  `18,18,4,18`；上行 labelSmall 发送者/时间；`spacedBy(12)`；kind=SUMMARY=弱化条（描边+@70%字）；
  todo_confirm=卡片带「确认/不用」；failed=点状态重发（保留契约）。
- 输入栏 = **bottomBar**（skill 结构）：imePadding+navigationBarsPadding，外层圆角20容器+hairline，
  TextField 透明容器/指示线，右下 30dp 圆形发送按钮状态机（skill 规格逐条落）。
- 空态：图标+两行字，走同一 padding 分支（不 fillMaxSize 抢高）。

## 4. 日程/画像/设置
- 统一：`statusBarsPadding` 的 topBar（标题+副信息）、section 卡（superellipse16+hairline+内距16）、
  section 头 labelLarge；卡片间12；页底 inset=dock inset。
- 日程：日期分组头（primary 色 labelLarge）+ 事件行（时间轴点+线）；待确认卡同频道样式。
- 画像：置信度进度条（primary，4dp 圆）+「这条不对」TextButton；空态文案不变（测试钩子）。
- 设置：每行 图标(圆角方块 tinted) + 标题 + 摘要 + 右控件；唤醒状态三行表格（绿/红点语义保留）。

## 5. 验收门（不出问题）
- 测试钩子契约不变：dock contentDescription "0"-"3"、placeholder「说点什么」、按钮「发送」、
  文本「频道/耳边待办/画像/唤醒状态」、引号「」。
- `bash scripts/precheck.sh` OK + core 38 绿 + CI 绿 + emulator UI_CHECK_PASS。
- 交付前：真机逐页截图亲验（每页至少1张）+ 用户验收。
