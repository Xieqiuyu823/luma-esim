# Luma eSIM

基于 [OpenEUICC](https://gitea.angry.im/PeterCxy/OpenEUICC) 源码的 Android 原生界面改版。参考 [NekokoLPA 2](https://github.com/iebb/NekokoLPA2) 的设备选择、EID 信息区、紧凑配置卡片与开关交互，使用独立的应用名称、包名及原创图标。

不是启动另一个 App 的壳：界面直接调用 OpenEUICC 的 EuiccChannelManagerService 和 lpac-jni 完成 eSIM 操作。

[浅色界面](docs/images/preview-light.png) · [深色界面](docs/images/preview-dark.png) · [验证结果](docs/VALIDATION.md)

## 已实现

- 蓝色浅色主题、深色主题与跟随系统；中文和英文界面。
- 设备 / 卡槽 / USB 多卡切换，显示真实 EID、配置数量与启用状态。
- 紧凑配置卡片、行内开关、已启用配置高亮和「全部 / 已启用」筛选。
- 配置卡片左侧显示归属网络国家 / 地区的国旗；通过配置的 MCC/MNC 元数据识别，国旗离线内置，国际网络或信息缺失时显示地球图标。
- 配置详情底部面板，ICCID / EID 隐藏、查看与复制。
- 启用前确认；成功后刷新真实卡片状态，不提前显示已切换。
- 沿用上游的二维码扫描、相册导入、剪贴板、手动激活码及下载进度流程。
- 沿用上游的重命名、删除确认、通知处理、MEP、USB CCID 与保护机制。
- System 版可选在配置切换成功后，只为对应逻辑卡槽 / MEP 端口的活动 SIM 开启数据漫游。
- 没有卡片时显示连接指引与兼容性检查；只读界面预览明确标注为示例数据，不调用 LPA。

这不是 NekokoLPA 的完整功能移植。本版没有新增蓝牙 / 远程读卡器、标签、到期提醒、流量统计、运营商资料库或自定义图标。UI 改版不扩展 OpenEUICC 原有硬件支持。

## 两个版本

| 版本 | 包名 | 用途与条件 |
| --- | --- | --- |
| Luma eSIM | `io.luma.euicc.easy` | Android 9+；可插拔 eSIM 的 OMAPI 访问需卡片 ARA-M 授权本构建的签名；也支持兼容的 USB CCID 读卡器。 |
| Luma eSIM System | `io.luma.euicc` | Android 12+；管理内置 eSIM 需要作为系统特权应用安装并授予白名单权限。单纯点击安装 APK 不会获得这些权限。 |

两版 APK 均使用公开的 [9eSIM Community Key](https://github.com/9esim/9eSIMCommunityKey)，证书 SHA-1 为 `D1C0F48B370E74D4EA4770ED4C3CD70A3198D31F`。若 eUICC 的 ARA-M 已允许这条指纹，普通版可直接通过 OMAPI 访问，无需为本构建另写规则；如果卡片只允许其他指纹（例如 Nekoko Community 的 `C473…`），仍需另外授权或使用其他访问路径。各手机和卡片的 ARA-M 行为可能不同。

这是共享的社区签名身份，不代表 9eSIM 或 NekokoLPA 官方出品、授权或背书；持有该公开 keystore 的任何人都能生成相同签名身份的 APK。请仅从可信来源安装。自行重新构建时若未配置该 keystore，签名会不同，ARA-M 也会相应变化。密钥文件不包含在本源码包中。

## 安装与体验

当前安装包及源码包可从 [GitHub Releases](https://github.com/Xieqiuyu823/luma-esim/releases/latest) 获取。日常在已 Root 设备使用请下载 [System 版 Magisk ZIP](https://github.com/Xieqiuyu823/luma-esim/releases/download/v0.1.3/Luma-eSIM-System-0.1.3-magisk.zip)；如果下载器无法处理 ZIP，可用 [同内容 BIN 副本](https://github.com/Xieqiuyu823/luma-esim/releases/download/v0.1.3/Luma-eSIM-System-0.1.3-magisk.bin)，下载后改回 `.zip` 再在 Magisk 中安装。

1. 普通使用安装 `Luma-eSIM-0.1.3.apk`。
2. 无可访问卡片时，点击「查看界面预览」；有卡片时，也可以在菜单或设置中打开预览。
3. 连接兼容卡片或 USB CCID 读卡器；授予读卡器访问权限后刷新。
4. 在真实设备页点击「添加 eSIM」，或通过配置开关切换、点击卡片查看详情。
5. 「设置 → 外观」选择浅色 / 深色 / 跟随系统。

System 版面向已经了解系统特权安装的设备。日常使用安装 `Luma-eSIM-System-0.1.3-magisk.zip`：在 Magisk 的「模块」里从本地安装 ZIP，再重启。也提供 `.bin` 同内容下载副本；不要把普通版 APK 当作系统版。

System 版设置中的「切换配置后自动开启数据漫游」默认关闭。启用后，每次配置成功切换并重新连接，只会为该逻辑卡槽对应的活动 SIM 开启数据漫游；不会开启移动数据，漫游使用可能产生运营商费用。普通版不显示此系统特权功能。

国旗表示配置的归属网络地区，不等同于旅行套餐的使用目的地或覆盖范围。详情面板中也会显示该地区。读取不到元数据时不根据名称或 ICCID 猜测国家。识别方式及素材来源见 [国旗说明](docs/FLAGS.md)。0.1.3 与此前社区签名的 0.1.2 使用相同签名，可作为更新安装。

## 构建

所需环境：JDK 21、Android SDK Platform 37（SDK Manager 中可能显示 `platforms;android-37.0`）、Build Tools 36.0.0、NDK 26.1.10909125。Gradle Wrapper 为 9.5.0，AGP 为 9.3.0。首次构建需要网络下载依赖。

```bash
# Git 克隆方式需要初始化子模块；随附源码 ZIP 已包含这部分代码。
git submodule update --init --recursive

# 在 local.properties 中设置 sdk.dir，或导出 ANDROID_HOME。
./gradlew :app-unpriv:assembleRelease :app:assembleRelease --max-workers=2
```

输出：

- `app-unpriv/build/outputs/apk/release/app-unpriv-release.apk`
- `app/build/outputs/apk/release/app-release.apk`

分发的 APK 使用上述社区签名。自行构建默认使用本机 Android debug keystore；如需生成相同 ARA-M 指纹，可按 9eSIM Community Key 仓库说明自行获取 keystore，并通过未提交的 `keystore.properties` 配置。不要把 keystore 或密码加入源码包。

Release 签名沿用上游配置，详见 [上游构建说明](README.upstream.md)。不要将私钥、真实激活码或包含标识符的日志提交进源码。

系统模块构建：

```bash
./gradlew :app:assembleReleaseMagiskModule
```

## 测试

```bash
./gradlew :app-common:testDebugUnitTest :app:testDebugUnitTest \
  :app-unpriv:testDebugUnitTest :libs:lpac-jni:testDebugUnitTest --max-workers=2
```

新增测试覆盖：PLMN 解码与国旗映射、未知地区回退和列表复用时更新国旗，以及筛选和状态恢复、EID 隐藏 / 查看 / 复制、只读预览不启动真实下载、真实管理页切换确认、按逻辑卡槽 / MEP 端口唯一映射订阅并在歧义时跳过。Robolectric 输出浅色与深色界面图。

截图输出在 `app-common/build/reports/luma/`。测试使用模拟 LPA；真实下载、APDU 通信、USB 拔插和基带刷新仍需在目标手机 / 卡片上测试。构建通过不能替代 eUICC 实机验证。

## 开源与来源

- 上游版本与子模块固定提交见 [UPSTREAM.md](docs/UPSTREAM.md)。
- OpenEUICC 及本分支主体：**GPL-3.0-only**，见 [LICENSE](LICENSE)。
- `libs/lpac-jni` 保留其 LGPL-2.1 许可；第三方代码保留各自许可证。
- 随安装包提供完整源码 ZIP（包括 native 子模块），便于审查、修改与重新构建。
- 原上游说明保存在 [README.upstream.md](README.upstream.md)。
