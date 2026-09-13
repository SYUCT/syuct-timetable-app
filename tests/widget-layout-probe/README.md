# 原生校徽布局回归

仅在临时项目副本和测试模拟器执行，不覆盖正式 App。此目录不进入 APK。

1. 将 WidgetLayoutProbe.java 复制到副本 app/src/debug/java/top/syuct/timetable/，将 AndroidManifest.xml 复制到 app/src/debug/。
2. 使用已配置的 JDK/SDK 运行 Gradle：`-I tests/widget-layout-probe/probe.init.gradle assembleDebug --offline`。
3. 在测试模拟器安装生成的 debug APK。其包名为 `top.syuct.timetable.widgetprobe`，与正式 App 分离。
4. 启动 `top.syuct.timetable.widgetprobe/top.syuct.timetable.WidgetLayoutProbe`。
5. 日志标签 WidgetLayoutProbe 应输出 `PASS 12 native layouts; original clipping reproduced in 4 cases`（复现数量随系统字体可能不同，但必须大于零）。屏幕显示实际 widget_today.xml，人工检查校徽完整。

每组检查校徽不超出父容器顶部/内容底部，且保留 44dp 尺寸。用代码恢复旧边距作为失败对照。测试宽度 220/280/340/400dp、字体比例 1/1.3/1.6。其他 Android/厂商系统需另行复核。
