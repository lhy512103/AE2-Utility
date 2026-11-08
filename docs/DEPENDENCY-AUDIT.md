# 依赖审计与兼容性记录

审计日期：2026-11-08。目标：Minecraft 1.20.1 / Forge / Java 17，不跨平台升级到 NeoForge。
本文件面向开发者，不将依赖坐标调整写入用户 changelog；测试结果在完成验证后补充。

## 选型依据

- Modrinth API 按 `game_versions=["1.20.1"]`、`loaders=["forge"]` 筛选，检查实际归档字节码、Mixin 配置和必要前置。
- `python tools/audit_dependencies.py` 可重新查询目录中的 Modrinth 依赖，完整响应写入忽略版本控制的 `build/reports/dependency-audit.json`，不会自动改版本。
- Forge 采用 [官方最新/推荐版本](https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json) 47.4.28。
- Configuration 使用 [CurseForge 的 Forge 1.20.1 文件](https://www.curseforge.com/minecraft/mc-mods/configuration/files/9425510) 3.1.1。
- 版本字符串不是可靠的加载器标识：EAEP `1.6.4` 同时存在 Forge 1.20.1 和 NeoForge 1.21.1 文件；直接用版本字符串构建时实测取到 major 65 文件，导致 Java 17 编译失败。
- EAEP 改用精确文件 ID `auasuDQR`，NeoEcoAE 同样锁定 `eLBVfyxA`；JustEnoughCharacters 也锁定 `pdLyf9hN`，裸 `4.6.12` 实际启动时取到引用旧版 `ITextComponent` 的错误游戏文件；GeckoLib、Searchables 和 Kotlin for Forge 保留已经正确的文件 ID。
- 最新 JEI 15.74.0.234 标注 beta；它是满足最新 EAEP 要求、经过本轮验证的候选，而不是宣称所有依赖都是稳定通道。

## 本轮更新（相对接手时工作目录）

| 依赖 | 原版本 | 本轮目标 | 上游证据 / 备注 |
| --- | --- | --- | --- |
| Forge | 47.4.10 | 47.4.28 | 官方 latest/recommended |
| AE2 | 15.4.10 | 15.4.12 | [Forge 文件](https://modrinth.com/mod/ae2/version/QRpVsqDI) |
| WCWT | 1.20.1.5 | 1.20.1.11 | [文件](https://modrinth.com/mod/ae2-wcwt/version/CaR5NsCp) |
| EAEP | 1.5.4.1 | 1.6.4 / auasuDQR | [Java 17 Forge 文件](https://modrinth.com/mod/extendedae-plus/version/auasuDQR) |
| NeoEcoAE | 20.2.0 | 20.5.5 / eLBVfyxA | [文件](https://modrinth.com/mod/neoecoae/version/eLBVfyxA)，新版不再强制依赖 EAEP |
| Thunderbolt Core | 2.0.0-beta.4 | 2.0.5 Forge 1.20.1 | [文件](https://modrinth.com/mod/thunderbolt-core/version/v6bJHg61) |
| AE2 Lightning Tech | 2.1.0-beta.4 | 2.1.4 Forge 1.20.1 | [文件](https://modrinth.com/mod/ae2-lightning-tech/version/RoG4v66B) |
| AdvancedAE | 1.3.5 | 1.3.6-1.20.1 | [文件](https://modrinth.com/mod/advancedae/version/d83Wdhdn) |
| ExtendedAE | 1.20-1.4.12-forge | 1.20-1.4.18-forge | [文件](https://modrinth.com/mod/extended-ae/version/uq3lO4ER) |
| JustEnoughCharacters | 4.6.11 | 4.6.12 | [文件](https://modrinth.com/mod/justenoughcharacters/version/pdLyf9hN) |
| Sophisticated Core | 1.20.1-1.5.3.2357 | 1.20.1-1.5.29.2459 | [文件](https://modrinth.com/mod/sophisticated-core/version/dICsXsDi) |
| Sophisticated Storage | 1.20.1-1.5.0.2137 | 1.20.1-1.5.15.2220 | [文件](https://modrinth.com/mod/sophisticated-storage/version/S0AtW2W5) |
| Sophisticated Backpacks | 1.20.1-3.26.6.2176 | 1.20.1-3.26.19.2239 | [文件](https://modrinth.com/mod/sophisticated-backpacks/version/VjliRs3R) |
| Cloth Config | 11.1.106 | 11.1.136 | [Forge 文件](https://modrinth.com/mod/cloth-config/version/t8TXrZvZ) |
| Architectury | 9.1.12 | 9.2.14 | [Forge 文件](https://modrinth.com/mod/architectury-api/version/1MKTLiiG) |
| Configuration | 2.2.1 / 4710266 | 3.1.1 / 9425510 | 同一 CurseMaven GAV，满足 EAEP JarJar `[5840405,)` |
| JEI | 15.62.0.217 | 15.74.0.234 | [Forge 文件](https://modrinth.com/mod/jei/version/6E4ps2ER)，满足 EAEP `>=15.74.0.232` |

## 明确回退：AE2 Crystal Science

尝试最新 [1.2.0](https://modrinth.com/mod/ae2-crystal-science/version/VGhHOKsP) 后，完整客户端启动实测触发本模组 NBT 撕裂卡的 `removeFromRemaining` redirect 失效；实际字节码确认新版删除该方法并重写 `RoutedPatternData.sparseInputs` 投料路由。

最终保留 [1.1.4.514-1.20.1-forge](https://modrinth.com/mod/ae2-crystal-science/version/ReXfuIoE)，它是发布列表中紧邻 1.2.0 的最新兼容版本，没有可尝试的中间版。模组元数据阻止加载已知不兼容的 1.2 系列，不用 `require=0` 跳过守恒关键钩点。
后续需独立实现实际 AEKey 路由转换，验证 SIMULATE/MODULATE、部分插入、失败回滚与 NBT 变体守恒，不能只根据主菜单通过宣称安全。

## 保持当前最新适用版本

以下是按 Forge 1.20.1 过滤后当前已使用的最新候选；保持版本不是漏查，也不代表跨 Minecraft 版本的全平台最新版。

| 依赖 | 保持版本 |
| --- | --- |
| AE2WTLib | 15.3.3-forge |
| GuideME | 20.1.15 |
| LDLib | mc1.20.1-1.0.52.a-forge |
| GeckoLib | 4.8.4，文件 ID aC5KMoNg |
| Glodium | 1.20-1.5-forge |
| AppFlux | 1.20-1.3.7-forge |
| Mekanism / Generators | 10.4.16.80 |
| Applied Mekanistics | 1.4.3 |
| Create | mc1.20.1-6.0.8 |
| Mystical Agriculture / Cucumber / Agradditions | 7.0.24 / 7.0.16 / 7.0.12 |
| Extreme Sound Muffler / Caramel Chat | 3.51-1.20.1 / 1.2.2 |
| Mouse Tweaks / Controlling / IMBlocker | 1.20.1-2.25.1-forge / 12.0.2 / 5.6.2.1 |
| Searchables | 1.0.3，文件 ID PM9yAW1G |
| CodeChicken Lib / Brandon's Core / Draconic Evolution | 4.4.0.528 / 3.2.1.302 / 3.1.2.621 |
| Modonomicon / Occultism | 1.20.1-1.79.3 / 1.20.1-1.158.0 |
| Productive Bees / Jade | 1.20.1-12.6.0 / 11.13.3+forge |
| MEGA Cells / ME Requester | forge-2.4.6 / 1.20.1-1.2.1+forge |
| Applied Create | 1.1.6 |
| SmartBrainLib / Kotlin for Forge | 1.15 / 4.12.0，KFF 文件 ID Zsh14XeQ |
| Curios / EMI | 5.14.1+1.20.1 / 1.1.24+1.20.1+forge |

Modern UI 3.12.0.1 在目录中但未用于依赖声明，本轮没有凭空加入运行环境。
ForgeGradle/MixinGradle、Gradle Wrapper、JUnit 属于构建工具链，不以模组更新为由跨主要版本升级；现有 Gradle 9 弃用警告作为后续工具链任务记录。

## 本模组兼容修复与无法靠回退解决的问题

1. EAEP 1.6.4 的 `uploadPendingCtrlQPattern` 返回 `UploadOutcome`，不再返回 boolean；直接调用、反射判断和 Mixin 回调都需要适配，只有 `SUCCESS` 是成功交付。
2. AE2LT 新版将 `OverloadedPatternProviderLogic` 移到 `logic.provider`，Mixin 和延迟加载检查需同步实际目标。
3. JEI 新版控制器字段/重载变化和 EAEP 供应器选择界面变化按实际目标签名适配，不能用编译成功代替 Mixin 启动验证。
4. Applied Create 1.0.0 至 1.1.6 全部缺少相同的两项 Jade 配置翻译，回退旧版无法消除该启动断言；保留最新版，补中英文兼容资源，不再从冒烟依赖集中偷偷排除它。
5. 检查并修复背包样板上传的槽位/NBT 授权和原生 EAEP 上传/取消通道绕过，以及机器拉料服务端对可选 JEI 的静态依赖。
6. JEI 控制器弱键缓存原本通过强 value 反向持有 key，改用弱键/弱值注册表，过期预览响应安全忽略。

## 升级注意事项

- 更新整合包前先备份存档，特别是 NeoEcoAE 相关网络/存储；上游升级可能包含数据迁移。
- 本模组仍保持 Java 17；不要通过提高 Java 版本掩盖加载到错误平台文件的问题。
- 依赖开发环境更新并不把可选模组自动打包进发布 JAR，玩家按所使用功能安装相应模组。
- 新版 EAEP 与 JEI 有共同最低要求；加载了 EAEP 1.6.4 时不能再配旧版 JEI。

## 本轮验证

- Java 17 `gradlew.bat clean build` 成功，19 项任务全部执行；60 项测试通过，失败/错误/跳过均为 0。
- 完整依赖集 `runClient -PmigrationSmokeTest` 成功到达主菜单并自动退出，31 个延迟目标和 9 个机器配置验证通过，未排除 Applied Create。
- 发布 JAR：321 个类的 major 均不高于 61；无重复 ZIP 条目；Mixin refmap、清单及配置引用类存在；全部 JSON 可解析，Forge 配方和元数据占位符检查通过。
- `runServer` 在 EULA 提示处正常结束，保留 `eula=false`；没有代替用户接受条款，不声称完成服务端世界加载。
- 无 JEI 机器元数据/服务路径已做隔离类加载与字节码引用回归；真实 AE2-only 服务端世界与多人上传/取消/断线交互仍待验证。
- 本地客户端成功日志保存在忽略版本控制的 `logs/maintenance-2026-11-08-client.log`；服务端日志和 JAR 校验数据在 `build/reports/`。
- 本地 `build/` 历史产物带只读属性，曾导致 `clean` 删除失败；仅清理确认后的生成目录只读标志，随后干净构建成功，未改源码目录权限或删除用户文件。
- 存在上游 AE2CS `minVersion`、部分第三方资源/更新检查、旧 JEI 配置迁移和 Gradle 9 弃用提示；这些不是本轮启动失败，不通过禁用校验或删除用户配置掩盖。
