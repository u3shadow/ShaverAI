# ShaverAI Android ↔ Backend 联调

此分支把聊天与意图决策交给 Python 后端；Android 保留真实设备能力执行器。当前对接 `POST /agent/chat`、`POST /rules`、`POST /device/event`、`POST /device/callback`。

## 启动与地址

1. 在后端项目启动服务，监听 `0.0.0.0:8000`，确认 `GET /health` 返回 `status: ok`。
2. Android 模拟器默认通过 `http://10.0.2.2:8000/` 访问宿主机。真机调试时，在本项目未跟踪的 `local.properties` 中配置 `BACKEND_BASE_URL=http://电脑局域网IP:8000/`，并确保手机能访问该地址。
3. Debug 版本允许本地 HTTP；发布版本请配置 HTTPS 服务地址。DeepSeek Key 留在 Python 后端，不再打包进 APK。

## 交互流程

- 聊天输入统一调用 `/agent/chat`。`type=answer/clarification` 展示 `answer`；`type=action_plan` 经客户端白名单与参数范围校验后才交给本地 `ActionExecutor`。Direct 可在同一计划中返回最多 3 个独立设备动作；Android 先校验整批，再按顺序执行并逐项回调，执行失败不会自动回滚。`set_volume.level` 的后端范围 0–15 会映射到现有 Android 音量百分比 0–100。
- `type=confirmation_required` 且计划是 `create_rule` 时弹出确认框。用户确认后调用 `POST /rules` 保存；取消则不保存。
- WiFi 连接变化调用 `/device/event`。命中后执行动作并调用 `/device/callback`，沿用原动作计划响应头的 `X-Request-ID`。没有服务器规则或服务器不可达时，尝试现有本地 WiFi 连接场景。
- 后端目前只处理 WiFi **连接**规则。现有本地 WiFi 断开场景、手动场景编辑和 WiFi 别名仍由 Android 本地保存和执行；这些本地数据不会自动同步到服务器。

## 设备权限

- WiFi SSID：首次进入主界面申请精确位置和附近 WiFi 设备权限；系统定位开关也需要开启。聊天本身不要求 WiFi 权限，但未授权时不会携带 SSID。
- 亮度：收到 `set_brightness` 且缺少“修改系统设置”特殊权限时，先弹说明，再打开系统授权页；返回后重新检查，允许才继续执行整个计划。
- 蓝牙：收到蓝牙动作且缺少 `BLUETOOTH_CONNECT` 时先请求运行时权限。音量使用普通的 `MODIFY_AUDIO_SETTINGS` 权限，无运行时弹窗。
- 通知：Android 13 及以上缺少 `POST_NOTIFICATIONS` 时申请；拒绝只影响通知，不阻断聊天或设备动作。
- 一个计划包含多个动作时，先检查整批权限，避免因后续动作缺权限而先执行前面的动作。拒绝权限时计划不会执行，并上报失败回调。系统中途撤销权限或设备操作本身失败仍可能造成部分成功，不自动回滚。
- 本地桌面小组件从后台启动时不能可靠地弹出授权界面；请先在主 App 中完成授权。未授权的本地亮度操作会报告失败，不再显示成功。

## 验证

运行 `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`。装到模拟器后测试普通问答、直接调节音量、确认创建规则、连接 WiFi 触发规则，以及后端 `device_callbacks` 表是否收到执行回调。若真机无法连接服务，先用手机浏览器打开 `http://电脑局域网IP:8000/health` 排查网络。

后端回调目前只记录 Android 上报结果，尚未验证该回调与已发出的动作计划是否匹配。蓝牙开关受 Android 系统版本限制，执行失败时会上报失败状态。
