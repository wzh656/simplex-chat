# 目标 1：不改 Haskell 的自定义服务器首次配置

日期：2026-08-20

## 1. 目标

Windows 和 Android 客户端首次使用时，用户必须先提供并验证：

- 1 条 SMP 消息服务器地址；
- 1 条 XFTP 文件服务器地址。

两条地址都通过格式校验和真实在线测试后，才允许用户填写名称并完成资料创建。资料创建完成后只启用用户提供的 SMP/XFTP，后续聊天队列和文件传输必须使用这两条地址。

同时从 Windows/Android 产品层移除：

- 预置服务器运营商选择与条款页面；
- Chat Relay 设置和公共频道入口；
- NTF 推送功能入口；
- 音视频通话、WebRTC 和 ICE 设置；
- Android 中 SimpleX/Flux 域名的 App Link；
- 所有会主动引导用户使用上游服务的 UI。

保留私聊、普通群组、文字消息、图片和文件收发。

## 2. 不改核心的边界

本目标禁止修改 Haskell、JNI C 和现有原生库。因此必须接受以下技术边界：

- `libsimplex.so` / `libsimplex.dll` 内仍包含上游预置地址常量；
- 核心执行 `CreateActiveUser` 时仍会先在本地数据库生成预置服务器记录；
- 服务器管理和测试 API 要求核心 chat 已启动，Kotlin 层必须在新用户尚无连接、队列、消息和文件时启动核心，再立即禁用预置记录并写入用户服务器；
- 产品保证正式资料最终只启用用户选择的服务器，不限制用户填写的服务器域名或运营方；
- 若要求从原生库物理删除地址，必须修改并重编 Haskell，这与本计划约束冲突。

验收口径定为“正式资料不默认使用预置服务器”，不是“原生二进制字符串完全不存在或上游地址不可连接”。

## 3. SMP 与 Chat Relay

SMP 是底层消息传输服务器。资料在 SMP 上创建接收队列，私聊、普通群组和控制消息都通过 SMP 转发。一条可用 SMP 地址足以支持保留的聊天功能。

Chat Relay 是运行在 SMP 网络上的特殊应用账号/中继程序，地址形如 `https://.../r#...`。它服务于公共频道，不是一台普通 SMP，也不会因为用户部署了 SMP 就自动存在。

因此本产品采用以下范围：

- 私聊和普通群组只需要用户的 SMP；
- 文件和媒体只需要用户的 XFTP；
- 不提供公共频道创建与 Chat Relay 管理；
- 保留底层频道数据模型和事件解码，减少与上游合并冲突，但不暴露产品入口。

## 4. 首次配置流程

新增 `Step2_ConfigureServers`，流程为：

1. 欢迎页；
2. 配置并验证 SMP/XFTP；
3. 填写资料名称；
4. 创建核心资料并立即应用用户服务器；
5. Windows 数据库口令设置；
6. Android 后台运行模式设置；
7. 完成。

服务器配置页包含：

- SMP 单行输入；
- XFTP 单行输入；
- “未验证 / 测试中 / 通过 / 失败”状态；
- 一个“验证服务器”按钮；
- 一个仅在两项通过后可用的“继续”按钮；
- 失败时展示现有 `ProtocolTestFailure.localizedDescription`；
- 修改任一已验证地址时立即清除验证状态；
- 不提供跳过或忽略失败选项。

候选地址只保存在 Compose 内存状态中，不写普通 SharedPreferences，避免明文持久化地址中的服务器密码。

## 5. 创建资料前的真实测试

现有 `testProtoServer()` 需要核心用户 ID。纯 Kotlin 方案复用已有临时数据库能力：

1. 使用 `chatParseServer` 校验地址格式和协议，SMP 输入必须解析为 SMP，XFTP 输入必须解析为 XFTP。
2. 创建随机路径的一次性临时 chat/agent 数据库。
3. 在临时控制器中创建随机名称的 `Temp` 用户，设置临时文件路径和网络配置。
4. 使用 `mainApp = false` 启动临时核心，不启动 Kotlin 消息接收器和主应用后台 worker。
5. 新增 Kotlin API 重载，直接向临时 `ChatCtrl` 发送 `CC.APITestProtoServer(tempUserId, address)`，不依赖全局 `ChatModel.currentUser`。
6. 对 SMP 执行连接、TLS 指纹、创建和删除测试队列。
7. 对 XFTP 执行连接、TLS 指纹、上传鉴权、创建和删除测试文件。
8. 两项结果都成功才进入资料名称页。
9. 在不可取消的 `finally` 中停止临时核心、调用 `chatCloseStore(ctrl)`，删除临时 `_chat.db`、`_agent.db` 和测试文件。

测试命令明确使用用户输入的候选地址，不从临时用户的默认服务器列表选择测试目标。

主要位置：

- `platform/Core.kt`
- `model/SimpleXAPI.kt`
- `views/migration/MigrateFromDevice.kt` 中可复用的临时数据库清理模式
- 新增 `views/onboarding/ConfigureServers.kt`

## 6. 正式资料配置

资料创建必须作为一个受保护的 provisioning 流程执行：

1. 创建前设置 `profileProvisioning = true` 的本地状态标记。
2. 调用现有 `apiCreateActiveUser()` 创建没有连接、队列、消息和文件的新用户。
3. 启动核心以满足服务器管理 API 的 `chatStarted` 前置条件。
4. 调用 `getUserServers()` 取得完整列表，把所有预置 SMP/XFTP 设为 `enabled = false`，并清空或禁用 Chat Relay。
5. 在完整列表中追加 `operator = null` 的用户组，其中 SMP/XFTP 各为验证过的一条地址，`preset = false`、`enabled = true`、`tested = true`。
6. 调用 `validateServers()`，要求无 SMP/XFTP 错误，再调用 `setUserServers()` 保存整个列表；不能省略预置组，否则核心未必删除这些记录。
7. 调用 `getServerOperators()`，将全部预置 operator 设为 `enabled = false` 并保存。
8. 删除核心自动创建的 `contactCard` 本地联系卡，并移除设置、帮助中的 SimpleX 团队连接入口。
9. 再次调用 `getUserServers()`，确认只有用户 SMP/XFTP 处于启用状态，且没有启用的 operator 或 Chat Relay。
10. 停止 provisioning 使用的核心，成功后更新 onboarding 阶段并最后清除 `profileProvisioning`。
11. 任一步失败时停止核心、关闭控制器、删除这次创建的全新数据库并回到服务器配置页。

应用启动时若发现 `profileProvisioning = true`，说明上次在资料提交完成前中断。直接清理这套尚未投入使用的数据库并返回服务器配置页，不尝试保留部分配置。

主要位置：

- `views/WelcomeView.kt`
- `views/onboarding/OnboardingView.kt`
- `App.kt`
- `platform/Core.kt`
- `views/database/DatabaseView.kt` 中现有数据库清理函数

## 7. 移除产品层内置服务

### 7.1 Operator、Chat Relay 和公共频道

- 删除 onboarding 的服务器运营商选择和条款阶段。
- 网络设置页不展示预置 operator，只展示“你的 SMP”和“你的 XFTP”。
- 删除 Chat Relay 添加、测试和角色设置入口。
- 删除“新建频道”和频道 Relay 管理入口。
- 对频道/Relay 专用链接显示“不支持的链接类型”。
- 不删除共享核心的数据类、序列化类型或数据库字段。
- 删除设置和帮助中的 SimpleX 团队、创始人及其他上游联系入口，确保核心生成的联系卡不会在 UI 中出现。

### 7.2 NTF

- Android 只保留后台服务、定时拉取和关闭三种方式，通过用户 SMP 获取消息。
- 不注册 NTF token，不展示 Push server 设置。
- Windows 不启用 NTF。
- 核心内置 NTF 常量仍在原生库中，但不会被客户端调用。

### 7.3 音视频和 ICE

- 删除设置中的音视频与 ICE 页面入口。
- 删除聊天、联系人和群成员页面的语音/视频按钮。
- 删除 Android 相机、录音、通话前台服务权限和通话 Activity/Service 声明。
- 删除 WebRTC WebView/controller、`call.js`、`call.html` 和 STUN/TURN 凭据资源。
- 收到通话邀请事件时调用现有 `apiRejectCall()`，不显示来电 UI 或系统通知。
- 保留最小 call 数据模型和响应解码，避免收到上游事件时 JSON 解码失败。

### 7.4 链接和 Android 域名

- Android Manifest 移除 `simplex.chat`、`*.simplex.im`、`*.simplexonflux.com` App Link host。
- 只保留定制 scheme/domain 和包含完整服务器地址的连接链接。
- 在 Kotlin 连接入口拦截仅依赖上游 host 补全的短链接，显示“不支持的短链接”，不交给核心解析或连接。

## 8. 测试与验收

### 8.1 流程测试

- SMP 字段拒绝 XFTP，XFTP 字段拒绝 SMP。
- 格式错误、指纹错误、连接失败和鉴权失败都不能继续。
- 任一地址修改后必须重新测试。
- 临时测试成功或失败后数据库文件都被删除。
- 正式资料配置期间可启动核心以调用服务器 API，但不得在配置完成前创建联系人或消息队列。
- provisioning 中断后不会启动不完整资料。
- 完成后 `getUserServers()` 只有用户 SMP/XFTP 启用，operator 和 Chat Relay 均未启用。

### 8.2 功能测试

- 使用受控 SMP/XFTP 完成资料创建。
- 两台设备互加联系人并双向发送消息。
- 创建普通群组并收发消息。
- 收发图片和普通文件。
- Android 后台服务和定时拉取都只访问用户 SMP。
- UI 中不存在通话、ICE、Chat Relay、公共频道和预置 operator 入口。

### 8.3 网络审计

在全新 Android 安装和全新 Windows 用户目录中抓取 DNS/TCP 流量，覆盖服务器测试、资料创建、首次启动聊天和文件收发。确认服务器测试访问用户输入的目标，正式资料建立的 SMP 队列和 XFTP 文件操作使用最终保存的服务器。

资源字符串扫描应覆盖 Kotlin、Manifest 和 Web 资源；原生 `libsimplex` 因禁止修改 Haskell，作为明确例外单独记录。

## 9. 构建与提交拆分

1. 新增首次服务器页和纯格式校验。
2. 增加 Kotlin 临时控制器测试 API及严格清理。
3. 实现资料 provisioning、operator 禁用和自定义服务器写入。
4. 简化服务器设置，移除 operator、Chat Relay 和公共频道入口。
5. 移除音视频 UI、权限、WebRTC/ICE 资源和来电处理。
6. 清理 Android App Link 与上游服务 UI 文本。
7. 执行 `.\.local-build\build.ps1 all`，完成 Android/Windows 全新安装、功能测试和网络审计。

本目标不得修改 `src/**/*.hs`、JNI C 文件或替换现有原生库。提交前用 `git diff --name-only` 明确检查这一约束。各提交按产品边界拆分，避免把大范围 UI 删除和 onboarding 状态机改动混在一起，降低后续合并上游修复的冲突。
