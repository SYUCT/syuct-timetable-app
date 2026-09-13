# 0.2.5-alpha1：小组件校徽裁切修复

校徽原图完整。旧版横向 LinearLayout 使用 center_vertical，图片底部单侧 10dp 外边距使其上移，在常规字体下顶端超出父容器约 5dp，被裁掉。

将图片和文字各自的底部留白统一移到父容器 paddingBottom，并关闭横向基线对齐。保留原图、44dp 尺寸与 fitCenter，不改课程、通知或权限。

## 验证

- Android API 35 模拟器实际加载 widget_today.xml：4 种宽度 × 3 种字体共 12 组全部通过。对同一布局恢复旧边距，在 4 组重现负的顶部坐标。
- 修复截图人工检查：圆形校徽上缘完整，标题与日期正常。
- 核心测试 33 项，浏览器流程 57 项通过。
- assembleRelease / lintRelease 成功，0 错误、8 项原有警告。
- 版本 0.2.5-alpha1，versionCode 7，沿用原签名，权限仍为网络、开机广播、通知。
- SHA-256：`717cddefe94d5945ca7ec99e3e30ca2bf116e8431c29d0fb27e1e5836542b3b8`。

用户手机的厂商桌面需覆盖安装后复核，不需要卸载原 App。安装包通过 GitHub Release 与官网发布，线上状态以发布结果为准。

## 后续发布

推送代码并创建 Release；同步官网 APK 与 app-version.json（versionCode 7、版本名和上述校验值须一致），核验线上两文件后才宣布发布。若需回滚，应以更高 versionCode 构建回退代码，不通过卸载降级丢失课表。
