# 化大课表 App 项目

独立 Android 课表工具，内置教务 WebView，读取后核对并保存本机。与 SYUCT-web 网站分开维护。

当前版本 **0.2.2-alpha1**。支持硕士同格多门课、本科首页七天课表读取。保留普通通知，不新增闹钟或悬浮窗权限。变更与验收记录见 [UPDATE-0.2.2.md](UPDATE-0.2.2.md)。

## 使用

1. 从本仓库 Releases 下载已发布的 APK，在安卓手机安装。0.2.2 安装包名为 `SYUCT-Timetable-0.2.2-alpha1.apk`。
2. 选择「导入 → 本科教务」或「硕士教务」，在学校页面自行登录。
3. 本科可读取首页七天课表（教师信息留空），完整信息请进入「信息查询 → 学生个人课表」；硕士进入「我的课程表」，选择目标学期。
4. 点击底部「读取课表」，核对课程、单双周和教室，确认后保存。
5. 点击首页「设置第一周」选择周一，即可按当前周显示；名称和日期可暂时留空，总周数默认 **20**。

点击「全览」保持竖屏，星期一至日七列同时显示；上下滑动查看后续节次，点击课程查看完整名称、教室和教师。卡片显示课程名称与简化地点（保留教学楼和教室，括号内旧楼名在详情查看）。顶部仅保留一行工具栏，返回单日页。首页学期与切周区域压缩为两行。设置页可添加桌面「今日课表」小组件，点击小组件课程或标题打开本周全览。当前课按北京时间和每节课起止时间判断，课间不标记；作息可在设置中修改，第11–12节默认留空。

小组件显示当前课并自动更新。计划每次上课前15分钟发送普通通知，首次使用可开启，设置页可关闭。只申请通知权限，使用默认提示音，尊重静音和勿扰；省电时可能延迟。无闹钟特殊权限和常驻后台服务。详见 [REMINDERS.md](REMINDERS.md)。

## 在 Mac 上预览

本机已安装独立 Android Emulator（API 35 / ARM64，预览设备 `SYUCT_Preview_API35`），无需 Android Studio。工具位于用户目录 `.local/share/syuct-android-tools`；模拟设备数据位于 `.android/avd`，均不上传仓库。

双击项目中的 **`预览化大课表.command`**：自动启动已配置模拟器、覆盖安装 `artifacts/SYUCT-Timetable-0.2.2-alpha1.apk` 并打开 App。也可在终端运行 `zsh ./预览化大课表.command`。该入口适用于这台已配置的 Mac；其他电脑需先安装 SDK 并创建同名设备。

用鼠标点击、拖动模拟手机；关闭模拟器窗口即可停止运行，不会开机自启。预览设备中的 `DEMO` 是合成测试课表，不包含在 APK 中，导入自己的课表可替换。

也可粘贴已有 `SYUCT-TT2` 课表码，或本科完整个人课表文字。已保存课表离线可用；可在设置复制课表码备份、恢复上一次保存。

本科首页需有完整七天的课表网格，仅读取页面已有课程，不猜测教师或未加载课程。解析成功不能证明课表完整，请逐门核对。未排课、调停补课提示不自动加入课表。

## 首版范围与限制

- Android 8.0（API 26）以上，需要支持现代 JavaScript 的 Android System WebView，建议先更新系统 WebView。
- 本科个人课表复用原解析器，首页按星期行及课程时间解析；硕士按完整星期表头、课程结束标记及节次、周次、地点字段拆分多课。
- 保留离散节次和周次，不将 `1,3` 扩展为 `1–3`。多份不同课表、错位表头、不可读框架或无法解析记录会停止导入。
- **未用真实学生账号测试本科/硕士登录、验证码、统一认证及真实当前页面 DOM。** 如学校使用新域名、跨域框架或其他布局，需要按实际页面适配；不会绕过登录、证书验证或学校访问限制。
- 无推送、云同步、PDF/OCR 导入、文件下载。学校文件仍可在原网站转换后以课表码导入。
- 教务页面文字与布局可能变化。浏览器模拟测试不等于安卓真机验收。
- 卸载或清除数据会删除本机课表与备份，操作前请导出课表码。

## 隐私与安全

- 请求联网及开机完成广播权限，后者用于恢复小组件和上课提醒。提醒仅申请通知权限。不申请闹钟、相机、通讯录、位置、外部存储权限，无常驻后台服务。
- 教务页单独使用一个 WebView，**不暴露原生 JavaScript 桥**。仅在用户点击读取时获取课表表格，不读取密码输入框、Cookie 值或账户资料。
- 只允许 `https://jws.syuct.edu.cn` / `https://geims.syuct.edu.cn` 对应教务域名导航，不允许任意域名、HTTP 或忽略证书错误。
- 本地 UI 使用打包资源与严格内容安全策略，禁止外部页面、脚本、框架和网络请求；原生桥只供该本地 UI 使用。
- 课表保存到应用私有存储；保存前保留上一版。关闭 Android 云备份和设备迁移。登录 Cookie 留在系统 WebView 私有目录，可在设置中清除。
- 不设自有服务器或统计 SDK。教务登录和访问仍会与学校服务器通信。
- 签名密钥、密码、个人课表与登录状态不进入 Git 仓库。Release APK 关闭调试，使用本项目独立私钥签名；测试版标签不等于正式发布验收。

## 源码结构

```text
app/src/main/java/top/syuct/timetable/
  MainActivity.java       本地界面、私有存储、备份、复制课表码
  SchoolActivity.java     教务 WebView、原生读取按钮、导航限制
  Policy.java             精确域名校验
  TodayWidget*.java       原生桌面小组件、列表服务、刷新和点击入口
  WidgetState.java        从私有课表读取今日安排
  LessonClock.java        北京时间、单双周与当前节次判断
app/src/main/assets/
  collector.js            只读 DOM 采集（同源框架、表头与网格）
  app-core.js             硕士解析、字段适配、验证、教学周计算
  app.js / app.css        本地课表、导入核对与设置界面
  overview.css            横向全览、日期弹窗和当前课样式
  section-times.json      默认节次时间（原生与网页共用）
  timetable-*.js          从 SYUCT-web 复用的解析器与 TT2 编码器
tests/                    匿名样本、解析、浏览器流程和域名规则测试
tools/sign-release.mjs    本地签名工具（密钥目录必须在仓库外）
```

## 本地构建与测试

使用 JDK 17、Android SDK 35 / Build Tools 35.0.0。Gradle Wrapper 固定 8.11.1，Android Gradle Plugin 固定 8.9.2。

```sh
# 配置 JAVA_HOME、ANDROID_HOME 指向本机已安装目录
./gradlew --no-daemon assembleDebug lintDebug
npm ci
npm test
npx playwright install chromium
npm run test:browser
mkdir -p test-results
javac -d test-results tests/PolicyTest.java app/src/main/java/top/syuct/timetable/Policy.java
java -cp test-results PolicyTest
javac -d test-results tests/LessonClockTest.java app/src/main/java/top/syuct/timetable/LessonClock.java
java -cp test-results LessonClockTest
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。CI 产物为开发调试包，不与 Releases 的独立签名安装包混用。

需要复用已有 Chrome 时，可设置 `CHROME_PATH`。正式测试包构建：

```sh
./gradlew --no-daemon assembleRelease lintRelease
node tools/sign-release.mjs \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  artifacts/SYUCT-Timetable-0.2.0-alpha1.apk \
  /path/outside/repository/signing
```

首次签名生成独立密钥和随机密码；后续务必沿用同一个私有签名目录。请离线备份密钥和密码，丢失后无法覆盖升级已安装应用。不要上传私钥到 GitHub。

## 验收和回滚

详见 [TESTING.md](TESTING.md)。请先验证本科、硕士各一份真实课表，确认登录、读取数量、字段与完整性，再扩大使用范围。

每次升级前复制课表码备份。课表误改可通过「恢复上一次保存」撤回。Android 不保证允许版本降级；若必须卸载新版本再安装旧 APK，应先备份课表码再导入。不要通过卸载回滚而丢失本机课表。

## 来源

复用的三个 `timetable-*.js` 文件来自管理员提供的 SYUCT-web 工作区，对应网站基线 `81556e2a42105c294de1ebdb3b65577afda9cc39`；文件保持原样，详见 [SOURCE-PROVENANCE.md](SOURCE-PROVENANCE.md)。Gradle Wrapper 为官方生成，许可见 `third-party/`。

这是学生共建的非官方项目，与学校官方应用无关。公开仓库不自动授予开源许可；项目整体许可由维护者决定。
