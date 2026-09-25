# Thyra AVD 聊天与网络检测总结

日期：2026-09-25  
设备：`emulator-5554`，AVD 数据目录位于 `D:\AndroidAvd\thyra_security_test.avd`（D 盘）  
测试会话：Shio 现有会话（此前已获授权）

## 结论

Sol 修复后，本轮未复现 Android 聊天连接故障。Cloud WebSocket 连接、普通问答、较长消息、跨轮上下文以及网络恢复后的发送均成功。检测期间没有修改应用源代码。

## 之前发现的网络问题

此前的诊断日志显示，Android 客户端在 WebSocket 握手前请求 `POST https://app.memoh.net/api/v1/ws-tickets`，服务端返回 HTTP 403，因此没有进入 WebSocket 阶段。网页端使用的认证/连接路径不同：网页客户端通过 `sdkAuthQuery()` 获取认证查询参数并直接连接 WebSocket。两端协议路径的差异是定位 Android 与网页表现不一致的重要线索。

本轮使用 Sol 修复后的构建重新登录并测试，未再观察到 ticket 403；Android 能连接并完成多轮对话。这证明当前测试路径可用，但本报告不对未核实的具体代码改动作推断。

## AVD 检测结果

- 启动 Thyra 后，既有 Shio 历史记录正常加载，连接状态显示已连接。
- 后台切回前台后，Thyra 进程仍存活、会话内容保留，连接恢复为已连接。
- 普通问答：`THYRA_AVD_TEST_20260925_PLAIN` 收到 `PONG`。
- 长文本与标点：约 120 字符的混合输入完整显示；bot 原样回传 `A1-B2-C3-D4-E5-F6-G7-H8-I9-J0` 并确认测试通过。
- 连续上下文：下一轮准确回忆并返回上一轮 token。
- 网络恢复：短暂关闭 AVD Wi-Fi 与移动数据后，立即重新开启；网络恢复并通过验证后，`THYRA_AVD_TEST_20260925_RECOVERY` 收到 `online`。Wi-Fi 与移动数据最终均恢复开启。
- 构建与测试：`.\gradlew.bat :app:assembleDebug` 成功；`.\gradlew.bat test --rerun-tasks` 成功（169 项任务执行）。

## 检测限制与非应用异常

- 离线期间 Android layout 查询返回 `Unrecognized response from instrumentation server`，未能采集断网瞬间的界面状态。恢复网络后，布局查询恢复，Thyra 进程和聊天记录仍正常；因此离线错误状态的 UI 呈现尚未验证。
- Logcat 中的 `UiAutomationService ... already registered` 来自 `com.android.commands.uiautomator.Launcher` 的 UIAutomator DumpCommand，不是 Thyra 进程崩溃。Thyra 进程当时仍存活。
- 会话中原有若干历史回复显示 `The external agent could not complete this turn.`；本轮新发送的检测消息均成功得到回复。未确认这些旧失败的具体原因。

## 建议后续检查

1. 在可稳定采集断网界面的测试环境复测离线提示、已有 transcript 保留及自动重连状态。
2. 对照 Memoh Cloud ticket handler 与网页客户端认证流程，确认 Android 修复后的 ticket 刷新、重连和权限错误处理均符合服务端协议。
3. 将历史 `The external agent could not complete this turn.` 与对应服务端运行记录关联，确认是否为独立的 Agent/backend 故障。
