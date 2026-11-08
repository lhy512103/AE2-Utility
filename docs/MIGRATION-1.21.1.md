# 1.21.1 NeoForge → 1.20.1 Forge 迁移

源项目：`ae2utility-1.21.1-neoforge`，包含其工作目录中截至上游 1.8.0 的功能。
目标发布版本：`1.20.1.2`。保留 Java 17，当前验证基线为 Forge 47.4.28、AE2 15.4.12；依赖更新与回退原因见 [依赖审计](DEPENDENCY-AUDIT.md)。

## 已迁移内容

- JEI/EMI 单条、整页、整分类编码，顺序上传队列、供应器记忆、取消和回执。
- 背包样板上传矩阵/供应器、预先选择供应器、缓存失效与编码规则同步。
- 编码材料固定、终端预览、书签优先、多方块结构页和万象样板兼容入口。
- 样板查询器的索引、筛选、选择和批量编码。
- 机器/背包拉料、机器配方状态，以及公共机器注册接口。
- NBT 撕裂卡的模拟、输入提取、投料和返还匹配；红石发信卡状态机。
- ME 快捷转移工具及 Sophisticated 容器优化，保留 Forge 的四类 Mekanism 化学品处理。
- API v1、中文/英文文本、界面资源、合成配方、原项目的 37 项回归测试。

## 平台适配

- NeoForge payload 改为 Forge `SimpleChannel`，协议版本升为 `4`，客户端和服务器需同时更新。
  自有 codec 封装使用 `FriendlyByteBuf`，额外验证大候选列表往返、复合包往返和列表长度上限。
- Data Components 改用 1.20.1 NBT；沿用已有卡片 NBT 键，避免破坏当前 Forge 存档中的卡片配置。
- RecipeHolder/CraftingInput 改用 1.20.1 Recipe/TransientCraftingContainer；AE2 列表接口按数组适配。
- JEI 更新为 **15.74.0.234**，使用已回移的按钮 API 并适配新版控制器；模组元数据阻止加载旧版 JEI。
- EAEP 1.6.4 使用 `ProviderUploadUtil`、`MatrixUploadUtil`、`RecipeTypeNameConfig` 和自己的 Forge 网络通道，上传按 `UploadOutcome` 区分成功、重复与失败。
- NeoEcoAE 20.5.5 的 CPU 钩子适配 `crafting.execution.ECOCraftingCPULogic.tickCraftingLogic`，不再注入仅作兼容锚点的旧 `api.me` 类。
- AE2CS 保留最新兼容版 1.1.4.514，1.2.0 的路由投料重写需要另行适配，不能跳过 NBT 撕裂卡的守恒关键注入。
- 背包样板预选与原槽位/完整物品 NBT 绑定，原生 EAEP 上传和取消通道同样校验；已有真实 pending 不允许被覆盖清除。
- 机器元数据与 JEI 类型关联分离，服务端未安装 JEI 时不会因公共查询初始化客户端依赖。
- ExtendedAE 的 Forge 模组 ID/切割器配方命名空间为 `expatternprovider`。
- 机器拉料优先按 AE2 槽位语义定位，兼容网络工具引入的附加槽位。
- 数据包配方使用 `data/ae2utility/recipes` 和 `result.item`；修复查询器指南针纹理引用。

## 外部模组边界

功能以安装的 **1.20.1 对应扩展**为准，不把其他模组的 1.21.1 方块移植到本模组。
例如 ExtendedAE 1.20-1.4.12 没有 Crystal Assembler，其机器注册入口会跳过；切割器与扩展压印器使用 Forge 版类型。
EAEP 旧版未公开的新撤回记录接口采用可选兼容调用；不提供的外部能力不能通过迁移本模组凭空新增。
JEI Crafting Tree、无用之物等未包含在本地运行依赖中的集成保留兼容入口，尚未实机验证。

## 验证与复现

2026-10-22 历史迁移验证：40 项测试通过，27 个延迟目标及 9 个机器配置完成主菜单检查。

2026-11-08 本轮复验：Java 17 `clean build` 成功，60 项测试全部实际执行且无失败/跳过；完整依赖客户端进入主菜单，31 个延迟目标通过检查，9 个机器配置成功注册。
发布 JAR 的 321 个类均不高于 Java 17 字节码（major 61），无重复 ZIP 条目，Mixin refmap/清单/类条目齐全，JSON 资源及 Forge 配方格式检查通过。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat build
.\gradlew.bat runClient -PmigrationSmokeTest
```

- 完整构建包含重混淆发布 JAR、sources JAR、API Javadoc JAR 和 60 项回归测试。
- 冒烟模式进入主菜单后强制加载延迟初始化的合成 CPU、样板、供应器界面及 JEI 类，检查 Mixin 目标，并自动退出；不打开存档。
- 本轮补齐 Applied Create 缺失的两项 Jade 中英文翻译，冒烟模式不再排除该模组，与正常客户端使用相同依赖集。
- 专用服务端 `runServer` 在 Java 17 下再次到达 EULA 提示并正常退出，未修改 `eula=false`，这不等于完成模组全生命周期或世界加载验证。
- 无 JEI 的机器查询路径通过隔离类加载回归测试；未完成 AE2-only 服务端世界加载、世界内逐项交互或多人端到端验证。
- 启动仍有第三方模组资源、版本检查和旧 JEI 本地配置迁移警告；本轮没有删除用户配置来掩盖警告。

本地验证日志、迁移前源码目录和独立 ZIP 位于忽略版本控制的 `migration-backup/`。
迁移前已有的未提交修改保存在该快照中；没有重置 Git 或修改源 1.21.1 项目。
