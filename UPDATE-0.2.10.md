# 0.2.10-alpha1：可选准时提醒与效果预览

状态：本地已实现、已签名，尚未推送或发布。versionCode 12。

## 实现

- 新增“准时提醒”独立开关，默认关闭；只在用户点击后解释并打开本应用的系统授权页。
- 声明 `SCHEDULE_EXACT_ALARM`，不声明 `USE_EXACT_ALARM`，不使用 `setAlarmClock`、前台服务、保活轮询或电池白名单。
- 用户选择且授权后，用 `setExactAndAllowWhileIdle` 预约下一次课前15分钟通知；未授权和精确调用被拒绝时回退普通预约。
- 保存预约的精确/普通模式，避免时间相同导致错误保留旧预约。授权广播、返回前台、重启和升级重新核对实际权限。
- 提醒仍遵循通知权限、单双周、开始日期、节次时间、已提醒记录与不在上课后补发的规则；精确权限不自动开启通知总开关。
- 系统撤销精确权限会终止应用并删除精确预约；重开App恢复普通预约，不宣称后台无条件自动恢复。
- 原生提醒设置采用校徽、蓝白卡片、48dp按钮、滚动内容与固定完成按钮。通知和精确定时权限分别说明。
- 预览优先借用下一门课的名称、地点；无后续课用明确标注的样例。3分钟演示时间明确区别真实时间，不写课表、不修改发送记录。

## 验证

- Release / Debug / 离线编译通过；最终 Release Lint：0 errors、12 warnings（既有兼容性等提示）。
- 核心33项、浏览器60项、原生时段20项、提醒计划40项通过。
- API35隔离包：未授权18项、已授权17项、撤销后恢复4项通过；含权限变化、同时间不同模式重新预约、权限广播、关闭取消、调用拒绝回退和预览不写数据。
- 系统 dumpsys 确认授权后的正式课程预约为 `window=0 exactAllowReason=permission`。
- 深度休眠触发：约晚37ms；随后结束隔离测试进程（非强行停止），再次深度休眠，由新的PID重新投递，约晚230ms。测试仅预约20秒后的合成广播，复用生产调度函数，未伪称完整15分钟真课实测。
- 已查看提醒设置上下部截图；已实际点击授权按钮，打开的是化大课表0.2.10的“Alarms & reminders”页面。
- 模拟器启动阶段曾出现 SystemUI 无响应提示，发生在测试包安装前；恢复后完成上述验证，未见应用崩溃。
- APK签名与现有版一致；包名 `top.syuct.timetable`，versionCode12，新增且仅新增精确定时权限。
- APK SHA-256：`eadc36eeb8d892207786e986b9714a55624af28ec065dae7eb3ff12a6a564239`。

## 未验证

未在用户手机验证厂商省电策略、升级后的完整课前15分钟送达、重启后长时间待机送达、Android26–30实机及新预览在厂商岛上的布局。精确定时不保证关机、强行停止、通知禁用、密集调度或厂商限制时送达。系统短时间内连续唤醒仍有节流限制。

## 测试复现

`tests/exact-probe/probe.init.gradle` 只构建 `.exactprobe` 隔离包，测试代码不会进入Release。用该init脚本运行 `assembleDebug` 并安装后，授予隔离包通知权限。通过系统页面或仅用于QA的appops切换 `SCHEDULE_EXACT_ALARM`，启动 `NativeExactProbe`：

- `--es phase denied`：未授权路径。
- `--es phase granted`：已授权路径。
- 撤销精确权限后 `--es phase resume`：保留状态验证重开回退。
- `--es phase visual`：未授权状态的界面检查。
- `--es phase delivery`：已授权时预约20秒后合成广播，自动返回桌面；日志 `NativeExactProbe` 记录实际晚到毫秒数。

仅测试模拟器可以用 `dumpsys deviceidle force-idle`；结束后必须 `unforce`，不要更改真实用户手机或正式包权限。

## 发布与回退

用户确认发布后提交App代码、创建签名APK Release，再同一网站提交更新APK及 `app-version.json`（版本12和匹配哈希），下载线上文件核验。当前尚未执行。

如需回退使用方式，关闭“准时提醒”即可重新预约普通提醒。不要卸载回滚以免丢课表；代码修复应发布更高versionCode并覆盖安装。

参考：[Android精确定时与授权](https://developer.android.com/develop/background-work/services/alarms)、[Android14默认授权变化](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)。
