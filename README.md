# 耳边 (erbian)

长按电源键唤醒的 AI Agent 消息频道。App 是耳朵和嘴巴，脑子在本机的 Hermes Agent：
快速记录一句话 → webhook 送到 Agent → 画像沉淀 / 待办写入系统日历 / 回复推回频道。

## 架构

```
长按电源键 (0.5s) → AOSP assist 路径 → VoiceInteractionSession 浮层（文本输入，语音交给输入法）
       ↓ 发送
App 队列(Room+文件快照) → HMAC 签名 POST 127.0.0.1:8644/webhook/erbian-record → Hermes Agent
       ↓ 处理
Agent: 画像更新 / 待办（明确直写日历，含糊先问）/ 回复
       ↓ 回程
Agent POST 127.0.0.1:8790/api/message → 频道插入；重要消息镜像 QQ/Telegram
```

内嵌服务（0.0.0.0:8790）：REST 回程 + **MCP**（暴露 App 文件系统/日志/队列/设置状态，本机 AI 可直查）。

## 唤醒链（HyperOS 实测）

三处系统设置把长按电源键从小爱切到本应用（Shizuku 可写，App 启动自检自愈）：

| 设置 | 值 |
|---|---|
| `Settings.System.long_press_power_key` | `none` |
| `Settings.Global.power_button_long_press` | `5` |
| `Settings.Secure.assistant` / `voice_interaction_service` | 本应用 VoiceInteractionService |

逆向证据链与切换实验记录见 `docs/design.md` §二。

## 开发

- 纯逻辑在 `core/`（零依赖，本地 `bash scripts/core-test.sh` 快速红绿，38 测试）
- `bash scripts/precheck.sh`：kotlinc 语法预检（无 Android SDK 环境下省 CI 轮次）
- 构建：GitHub Actions（push 即建，`build.yml`）；模拟器自动验收（`emulator-test.yml`：装机→配置唤醒链→长按电源键→截图+冒烟测试）
- 版本基线：AGP 8.5.2 / Kotlin 2.0.20 / Compose BOM 2024.09.00 / minSdk 31

## UI

Material 3 + 动态取色 + 纯黑模式；设计语言取自 FlClash：超椭圆形状体系（4/8/16/20/24/28/药丸）、
浮动药丸底部导航、字形图标动画、220ms fade 页切换。

## License

Private / personal use.
