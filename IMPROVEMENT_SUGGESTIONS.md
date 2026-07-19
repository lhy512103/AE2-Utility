# AE2: Utility 改进建议

## 0. 本轮四项优先级的落地状态

本轮已完成以下四项：

1. **测试与输入边界**：加入 JUnit 5 测试，覆盖远端编码规则、红石信号卡模式 Codec、批处理策略和网络列表大小边界；新增统一 `NetworkValidation`，对请求材料、候选物品、批量样板、配方输入/输出槽、可合成缓存和背包槽位数量做上限校验。
2. **大型类拆分**：抽出 `EncodeBatchPolicy`、`RecipeFinderTextFormatter`，分别承载批量编码策略和配方搜索文本格式化，降低服务类与界面类的职责密度；`EncodePatternService` 与 `RecipeFinderScreen` 仍建议按 P0 计划继续拆分。
3. **运行时环境分层**：新增 `coreLocalRuntime`、`ae2AddonLocalRuntime`、`compatibilityLocalRuntime`，并提供 `runClientMinimal` 与 `runClientCompatibility` 两套开发运行入口。
4. **统一可选模组检测**：新增 `ModCapabilities`，集中管理 EMI、JEICT、ExtendedAE、Advanced AE、AE2 Crystal Science、AE2 Lightning Tech 等 Mod ID 与能力探测。

## 1. 本次依赖集成说明

已将 `D:\Code\java\JDTE\dependencies.gradle` 中的 `localRuntime` 运行时依赖并入本项目的 `dependencies.gradle`，覆盖：

- GuideME、Just Dire Things、FTB Ultimine；
- AppleSkin、Patchouli、Titanium、Industrial Foregoing、Ex Deorum；
- Just Dyna Things、Mystical Agriculture、Mystical Agradditions、Cucumber；
- Productive Bees、AllTheCompressed、Sophisticated Storage/Core、Logistics Network；
- Bookshelf、Placebo、Prickle、Apothic 系列、Apotheosis、Enchantment Descriptions、Hostile Neural Networks。

依赖被放在 `localRuntime`，含义是：

- 用于开发运行和兼容性回归测试；
- 不会自动打包进最终发布 jar；
- 不会改变模组对玩家整合包的强制依赖关系；
- 其中客户端专用依赖仍应放在 `clientLocalRuntime`，避免服务端启动加载客户端类。

## 2. 当前代码状态观察

### 优点

1. 功能边界清晰：围绕 AE2、JEI/EMI、样板编码、网络拉料和配方树展开。
2. 已有较完整的可选模组兼容层，例如 ExtendedAE-Plus、JEICT、ECO，并通过 `ModList`/反射降低硬依赖。
3. 已区分服务端逻辑、客户端 GUI、网络包、服务层、JEI/EMI 集成和 Mixin，后续扩展有明确落点。
4. 已有服务端/客户端配置，且对批量编码上限、NBT Tear Card 黑名单等高风险行为提供了控制项。
5. 当前 Gradle 依赖解析和完整构建均已通过。

### 需要优先关注的问题

1. **测试覆盖仍不完整**：目前已有 `src/test`，但仍缺少 NBT 比较、配方树展开、材料汇总、重复样板检测和队列取消等纯 Java 单元测试。
2. **核心类过大**：`EncodePatternService.java` 超过 1000 行，`RecipeFinderScreen.java` 接近 900 行，`TerminalPullService.java`、`MachinePullService.java` 也较大。业务策略、网络编排、库存操作、提示文本和兼容判断混在一起，会提高回归风险。
3. **依赖集合偏重**：现在的 `localRuntime` 同时承担核心开发依赖、AE2 生态兼容依赖、JDTE 兼容依赖和大量内容模组。启动时间、下载时间、模组冲突概率和定位成本都会上升。
4. **版本与文件号分散**：部分依赖使用属性变量，部分直接写死 CurseForge 文件号，后续升级容易漏改或误配。
5. **JEI/EMI 运行模式仍需更明确**：当前默认加载 JEI，EMI 仅编译支持；建议把两套测试场景做成明确的 Gradle run 配置，而不是手动注释依赖。
6. **Mixin 数量较多**：Mixin 的目标类来自 AE2 及其扩展，升级 AE2/NeoForge 时容易出现注入点失效。应补充启动自检、版本兼容矩阵和失败时的可读日志。
7. **运行时调试日志需要分级**：已有多个 debug logger，但应统一由配置控制，并确保生产环境默认不输出高频 wire/队列日志。
8. **发布元数据仍较固定**：`neoforge.mods.toml` 中 AE2/JEI 版本范围是硬编码，建议将关键版本下限放入 Gradle 属性，避免源码和构建配置不一致。

## 3. 分阶段优化计划

### P0：稳定性与可维护性

1. 为以下纯逻辑建立 JUnit 5 测试：
   - NBT Tear Card 的过滤、默认值、序列化/反序列化；
   - 配方树拓扑排序、循环检测、概率产物处理；
   - 输入物品选择优先级；
   - 样板重复检测；
   - 批量编码数量限制和取消行为；
   - 网络 payload 的长度、数量和非法 ResourceLocation 校验。
2. 继续把 `EncodePatternService` 拆为：
   - `PatternEncodePlanner`：只负责规划；
   - `PatternEncodeExecutor`：只负责执行；
   - `PatternUploadCoordinator`：只负责供应器上传；
   - `PatternEncodeFeedback`：只负责玩家反馈。
3. 继续把 `RecipeFinderScreen` 拆为状态模型、布局渲染、输入处理、网络动作四层，尽量让 GUI 类只处理渲染和事件转发。
4. 对所有网络包统一做服务端权限检查、玩家上下文检查、最大列表长度检查和异常兜底；不要信任客户端传来的槽位、配方 ID 或数量。
5. 为队列引入统一状态机：`PENDING/RUNNING/WAITING_USER/SUCCESS/SKIPPED/FAILED/CANCELLED`，避免多个队列各自维护相似的布尔状态。

### P1：构建与兼容性

1. 将运行时依赖拆成三个集合：
   - `coreLocalRuntime`：AE2、JEI/EMI、必需 API；
   - `ae2AddonLocalRuntime`：AE2 扩展；
   - `compatibilityLocalRuntime`：JDTE 和大型内容模组。
2. 为 JEI、EMI、最小环境、完整兼容环境分别定义 NeoForge run：
   - `clientJei`；
   - `clientEmi`；
   - `clientMinimal`；
   - `clientCompatibility`。
3. 将所有 CurseForge 文件号、Modrinth 版本和 Maven 版本统一集中到 `gradle.properties` 或版本目录，并在构建时打印依赖清单。
4. 增加启动 smoke test：启动 client/server 后检查关键注册、Mixin 应用、网络 payload 注册和可选模组探测结果。
5. 建立 AE2、NeoForge、JEI、EMI、ExtendedAE-Plus 的兼容矩阵，升级时先跑最小环境，再跑完整环境。
6. 对可选模组采用“能力探测”而不是大量散落的 modid 字符串；集中维护 `ModCapabilities`，例如 `HAS_EMI`、`HAS_EAEP`、`HAS_JEICT`。

### P2：性能与用户体验

1. 配方树索引和 JEI 扫描尽量在后台线程执行，但所有世界、菜单、网络和玩家状态访问回到主线程。
2. 对配方树、材料汇总和 ME 网络查询增加缓存，缓存键包含世界/网络/配方版本，网络变化时增量失效。
3. 对批量上传增加并发上限、每 tick 配额和背压，避免一次性向服务器发送大量 payload。
4. 优化提示信息：显示成功、跳过、缺少样板、缺少材料、等待用户选择、失败原因的数量汇总，并提供点击复制日志/配方 ID 的方式。
5. 为危险操作增加预览：预计消耗空白样板、预计上传数量、预计缺少材料和预计占用 CPU 后再确认。
6. 为键位和按钮增加可配置快捷键，避免 Alt/Shift/Ctrl 组合与 JEI、EMI、ExtendedAE-Plus 冲突。

## 4. 值得添加的功能

### 高价值功能

1. **批量编码预检报告**：一次列出空白样板不足、材料缺失、未识别机器、循环依赖、重复样板和需要手动映射的节点。
2. **编码方案保存/加载**：把配方树选择、材料替换、机器映射和上传策略保存为本地方案，可重复执行。
3. **失败任务重试**：仅重试失败节点，不重复执行已成功的编码和上传。
4. **供应器模板管理**：按机器、频道、优先级和网络保存供应器映射，并支持导入/导出。
5. **多网络选择**：玩家同时接入多个可访问 ME 网络时，显示网络名称、维度、距离和可用 CPU，让用户明确选择目标网络。
6. **材料清单导出**：导出为聊天文本、JSON、CSV 或 JEI 收藏，便于整合包准备材料。

### AE2 深度集成

1. 按网络库存、合成库存、已有样板、CPU 状态和频道状态提供过滤器。
2. 支持“只编码可自动合成”“只上传缺失样板”“只处理某一机器类型”等策略。
3. 增加样板版本管理：显示样板来源、最近更新时间、输入/输出变化，并支持安全替换旧样板。
4. 为红石信号卡增加定时器、脉冲、按库存阈值触发和按网络 CPU 状态触发模式。
5. 提供网络诊断页面：频道占用、供应器状态、样板重复、无法解析的机器和最近失败原因。

### 生态兼容

1. 对更多配方查看器提供统一抽象，避免 JEI 和 EMI 各自复制编码逻辑。
2. 将机器 transfer profile 外置为数据驱动配置，允许整合包通过 JSON 添加槽位映射，而不是每次都改 Java。
3. 对 Create、Mekanism、PneumaticCraft、Productive Bees、Mystical Agriculture 等增加专门的概率产物、流体容器和 NBT 匹配规则。
4. 提供服务器策略配置：允许管理员禁用批量编码、限制每次数量、限制跨维度网络访问、限制自动上传。

## 5. 建议的验收顺序

1. `gradlew dependencies --configuration localRuntime`：确认 JDTE 依赖都能解析。
2. `gradlew build`：确认编译、资源处理、jar 和 sources jar 正常。
3. 启动 `clientMinimal`：验证基础 AE2 + JEI/EMI 功能。
4. 启动 `clientCompatibility`：验证 JDTE 依赖集合下的启动、配方索引和批量编码。
5. 在专用服务器上验证：无客户端类加载、网络包权限、批量操作限流和服务端配置。
6. 对 JEI、EMI、ExtendedAE-Plus 各跑一遍编码、上传、取消、重试和异常场景。
