## 当前目标

基于 [simplex-chat](https://github.com/simplex-chat/simplex-chat) 开源项目开发一个定制化的加密聊天软件

新名称：“灰色异托邦” (Gray Heterotopia)

只考虑 Windows 和 Android 客户端

## 要求

- 不要改动 Haskell 核心！
- 当前是要开发一个新的聊天软件，根本没有用户，所以不要给我扯什么旧用户、旧数据、迁移什么的！！！
- 只有用户明确说“修复”“开始执行”时才能进行编辑，否则默认为只读模式，应该给出问题分析、修复计划
- 禁止破坏系统/全局环境，如有确有需要则应该给出具体步骤并由用户手动操作
- 当前仓库 fork 自官方仓库，推荐考虑更好的修改方式以避免未来官方修复了严重的安全漏洞时不方便合并之类问题

## 计划

1. 去除内置服务器并且要求进入应用在创建资料前就需要先填写并验证服务器地址
2. 修改simplex名称、包名、软件名称、数据目录名称、图标等标识为新名称和新图标
3. 去除音视频通话功能、限制只允许上传最大50MB文件，防止服务器流量/存储过大
4. 重构UI风格

## 本机打包

- 持久化工具链位于仓库根目录 `.local-build/`，已在 `.git/info/exclude` 中排除；不要删除或提交。内含 JDK 17、Android NDK 23.1、WiX 3.14.1 和 NanoHTTPD 本地缓存，其余 Android SDK 组件复用本机 SDK。
- 构建 Android：`.\.local-build\build.ps1 android`。
- 构建 Windows：`.\.local-build\build.ps1 windows`。
- 同时构建：`.\.local-build\build.ps1 all`。
- APK 输出到 `apps/multiplatform/android/build/outputs/apk/foss/debug/`；MSI 输出到 `apps/multiplatform/release/main/msi/`。
- 当前 APK 使用 debug 签名，MSI 未签名，只用于开发测试。
- 当前复用上游同版本原生库。若上游 tag 到 HEAD 的 Haskell 核心或 JNI C 源码发生变化，必须重新编译原生库，禁止继续混用旧库。
