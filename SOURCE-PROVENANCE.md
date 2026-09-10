# 复用源码来源

以下三个文件原样复制自管理员提供的 SYUCT-web 工作区，网站基线提交为 `81556e2a42105c294de1ebdb3b65577afda9cc39`。

| 文件（位于 app/src/main/assets） | SHA-256 |
| --- | --- |
| timetable-campus-parser.js | `f5067b2b33e3551e93bdd62c3caf3a5ec7f5c441f8035debe68f0a40b6f4fede` |
| timetable-codec.js | `fa70192e4c23cf8423e0701ce02b08bc32550ea9905e9a606867eecd5f6d66b7` |
| timetable-mobile-text-parser.js | `c4c7b4722e1ec4bc2027dd0cfc4924296f7dadb8fd87e31b5acbe14fd73c9b83` |

来源：[SYUCT/SYUCT-web](https://github.com/SYUCT/SYUCT-web)。匿名本科样本与参考结果亦来自该仓库测试目录。不包含用户个人截图、原始课表 PDF、Cookie 或登录凭据。

Gradle Wrapper 由官方 Gradle 8.11.1 的 `wrapper` 任务生成。官方发行包校验值已写入 wrapper 配置并与下载文件一致。对应 LICENSE / NOTICE 保存在 `third-party/`。

Android SDK、JDK、Gradle 安装目录不随本仓库分发。测试依赖 Playwright 仅用于开发，不进入 APK。
