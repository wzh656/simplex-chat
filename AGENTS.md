## 当前目标

基于 [simplex-chat](https://github.com/simplex-chat/simplex-chat) 开源项目开发一个定制化的加密聊天软件

新名称：“灰色异托邦” (Gray Heterotopia)

只考虑 Windows 和 Android 客户端

## 要求

- 不要改动 Haskell 核心！
- 当前是要开发一个新的聊天软件，根本没有用户，所以不要给我扯什么旧用户、旧数据、迁移什么的！！！
- 只有用户明确说“修复”“开始执行”时才能进行编辑，否则默认为只读模式，应该给出计划、分析
- 禁止破坏系统/全局环境，如有确有需要则应该给出具体步骤并由用户手动操作
- 当前仓库 fork 自官方仓库，推荐考虑更好的修改方式以避免未来官方修复了严重的安全漏洞时不方便合并之类问题

## 计划

- [x] 去除内置服务器并且要求进入应用在创建资料前就需要先填写并验证服务器地址
- [x] 修改simplex名称、包名、软件名称、数据目录名称、图标等标识为新名称和新图标
- [x] 去除音视频通话功能、限制只允许上传最大50MB文件，防止服务器流量/存储过大
- [x] 重构UI风格（使用Material Design 3，参考Telegram等设计风格）
- [x] 增加表情包功能
- [x] 打包 Release 版本
- [x] 支持 Debug/Release 双模式本地构建

## 本机打包

- 持久化工具链位于仓库根目录 `.local-build/`，已在 `.git/info/exclude` 中排除；不要删除或提交。内含 JDK 17、Android NDK 23.1、WiX 3.14.1 和 NanoHTTPD 本地缓存，其余 Android SDK 组件复用本机 SDK。
- 默认构建 Release：`.\.local-build\build.ps1 -Target all -Configuration release`。
- Android Debug：`.\.local-build\build.ps1 -Target android -Configuration debug`。
- Android Release：`.\.local-build\build.ps1 -Target android -Configuration release`。
- Windows Debug：`.\.local-build\build.ps1 -Target windows -Configuration debug`，只生成未签名便携目录，不生成 MSI。
- Windows Release：`.\.local-build\build.ps1 -Target windows -Configuration release`，生成 MSI、便携目录并执行签名验签。
- 也支持位置参数简写：`\.local-build\build.ps1 android debug`、`\.local-build\build.ps1 all release`。
- Android Debug APK 输出到 `apps/multiplatform/android/build/outputs/apk/foss/debug/`；Android 签名 Release APK 输出到 `apps/multiplatform/release/android/`。
- Windows 便携目录输出到 `apps/multiplatform/release/main/app/Gray Heterotopia/`；Windows Release MSI 输出到 `apps/multiplatform/release/main/msi/`。
- Android Release 使用 `.local-build/signing/grayheterotopia-release.jks`；Windows Release 使用 `.local-build/signing/grayheterotopia-windows-local-signing.pfx`。缺少签名材料时 Release 构建直接失败，不会退回 unsigned 包。
- 当前 Windows PFX 是本地自签名证书，仅用于开发测试；公开发布前替换为 CA 代码签名证书，并在凭据文件中配置 `timestampUrl`。
- Windows 使用 `.local-build/windows-native/` 中已修复 XFTP 关闭卡死的原生库；仅 Haskell 核心、simplexmq 版本或 JNI C 源码变化时才需用 GitHub Actions 重编。
