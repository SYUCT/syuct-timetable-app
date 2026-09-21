# 0.2.12-alpha1：小米通知回退路径修正

## 已确认问题与修正

- 0.2.11 的 Xiaomi 通用 shortCriticalText 只写 HH:mm，因此 OEM 模板不生效时课程名确实丢失。现在填入「课程前三字 HH:mm」。这是一个短文本槽，不是模拟出来的两个区域；系统可能裁切，不能保证两部分在所有岛尺寸下都完整。
- 0.2.11 只查协议版本就尝试原生模板，没有查焦点通知权限，并同时请求 Android 通用提升。现在只有协议 >=3、已开启实时通知、查询明确返回允许时才选原生模板；该路径不再同时请求通用提升。其余情况保留通用通知。
- 进入 App 时，后台单次查询小米官方 canShowFocus ContentProvider。结果区分允许、拒绝、未知，保留本地最长24小时缓存，系统升级或时间回拨后失效；每次回到 App 刷新。不上传设备/课表数据、不新增权限、不阻塞通知发送、不循环查询、不自动重发已有通知。
- 提示不再声称「已发送小米左右分区」就等于生效。拒绝时说明可能需要系统授权或厂商接入资格；查询失败不冒充拒绝。
- 小米通知的小图标不再用我们生成的亮度/透明度校徽蒙版，而直接传原始彩色校徽，并附带 MIUI 兼容提示 `miui.isGrayscaleIcon=false`。其他品牌保留原有标准单色小图标。大图与专用模板继续使用同一原图。
- 小米字段是兼容尝试，**不是标准 Android API 或当前 HyperOS 3 官方保证**；系统如果忽略该提示，仍可能染色甚至显示实心轮廓。未宣称已在用户手机修复。不得继续通过调整校徽亮度阈值来声称解决系统染色。
- 结束按钮、划掉后的去重记录、开课时的毫秒超时保留。未调整闹钟预约、导入解析、课表数据或 vivo 路径。

## 依据及外部限制

- 官方查询/模板合同：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
- 正式接入/白名单流程：https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2132
- 标准短文本槽及可裁切限制：https://developer.android.com/reference/android/app/Notification.Builder#setShortCriticalText(java.lang.String)
- MIUI 兼容字段的线索来自公开的旧 MiPush SDK 代码镜像，而非当前官方承诺：
  https://github.com/XEonAX/ANXMiuiApps/blob/cbce8c31f64f5fa3e1b1da00b9f806b096ea0a68/src/ANXGallery/sources/com/xiaomi/push/service/MIPushNotificationHelper.java
  旧代码为灰度图设置 true；本次原图设置 false 的显示效果是待验证假设。未引入该项目代码或 SDK。

用户已确认澎湃 OS 3，具体机型、完整构建号及本应用焦点权限尚未知。截图符合通用通知回退表现，但不能仅凭截图断言厂商拒绝授权。若系统明确拒绝原生模板且用户设置无法打开，则需要开发者接入/测试白名单，不应通过伪装媒体、修改系统或额外常驻服务绕过。

## 验证记录

- 课表核心33项、浏览器60项、原生时钟20项、提醒计划40项通过。
- Android 16（API 36）独立测试包74项通过：课程＋时间短文本、原图资源及兼容提示、权限返回类型/拒绝/未知、路由条件、原生路径不同时请求通用提升、按钮/关闭/超时、默认关闭、不篡改课程发送记录等。
- 首次 API 36 测试在立即发布后立即撤销的断言处失败。测试原先假定 notify 同步完成；现在先观察已发布，再撤销并等待系统处理（最多5秒），复测通过。没有删掉取消断言，也没有为通过测试改动正式提醒计划。
- Release/debug编译通过；Android lint 0错误、12条既有警告。
- 原签名验证通过，包名 top.syuct.timetable，versionCode 14。
- 本地 APK SHA256：`62d7151f23300f0a1f54a89abf7139dc433b528cfb80fddbc5a3298365760cc2`。
- **未验证**：澎湃 OS 3 实际焦点权限查询结果、灰度兼容提示是否生效、芯片的文字裁切、专用模板实际展示。标准模拟器不提供小米 SystemUI，74项通过不是这几个视觉问题已经解决的证据。

## 安装与回滚

经用户确认，发布本版本 APK（versionCode 14），同步 GitHub Release、官网 APK 与检测更新信息。使用原签名覆盖安装，不要卸载，避免丢失课表。发布不改变上述真机待验证限制。
如出现系统图标兼容问题，可先关闭实时倒计时保留普通提醒；需要代码回滚时回退这些通知改动并以更高 versionCode、原签名发布，不建议卸载降级。
