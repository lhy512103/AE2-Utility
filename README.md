# AE2 Utility — NeoForge 26.1.2

[中文](#中文) | [English](#english)

## 中文

面向 **Minecraft 26.1.2 / NeoForge 26.1.2.126 / Java 25** 的 AE2 辅助模组。本分支使用目标版本 API，不能与 1.21.1 模组二进制混用。

### 功能

- 从 ME 网络向终端和机器拉取配方材料。
- 从 JEI/EMI 单次或批量编码样板，预览样板，并通过 ExtendedAE Plus 上传至供应器/装配矩阵。
- 配方查找器：按物品、来源、机器和材料特征筛选配方并批量编码。
- NBT 撕裂卡：按物品类型放宽匹配，保留黑名单和过滤规则。
- 红石发信卡：下单脉冲、返还脉冲及持续到完成三种模式。
- ME 快捷转移工具：将目标容器的物品、流体及受支持的化学品转入绑定网络。
- 服务端编码限制、取消上传命令及开发者 API。

### 依赖与可选集成

核心依赖：AE2 `26.1.13-beta`、客户端 JEI `29.53.0.115`，以及它们要求的 GuideME、MezzConfig。

可选集成在未安装对应模组时不启用，核心功能不依赖这些扩展。

- ExtendedAE `26.1-1.0.6-neoforge` / ExtendedAE Plus `1.6.3`。
- AdvancedAE `26.1.7`、AE2 Crystal Science `1.1.12`、Sophisticated Core `1.5.33.2466`。
- AE2 Lightning Tech `1.0.1alpha-26.1.2neoforge`。
- Neo ECO AE Extension `26.2.15-beta`。此版本要求同装 AE2LT 至少 `1.1.3`，不能与上述 AE2LT alpha 同装；测试配置将它们分开。
- EMI 使用 **PF-EMI 非官方 26.1.2 移植版** `RHajWT5D`；Mekanism 和 Applied Mekanistics 使用 **Version Locked 非官方移植版**。它们不是官方旧版 JAR，也不代表官方支持。
- Some Useless Things 等没有核实到目标版发行的可选模组，其反射集成保留，但不宣称已完成对应发行版的运行验证。

### 构建与验证

```sh
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew -PcompatibilityRuntime runClient
./gradlew -PcompatibilityRuntime -PecoRuntime runServer
```

`compatibilityRuntime` 启用额外模组测试组合；`ecoRuntime` 用 NeoEco 替换存在上游版本冲突的 AE2LT。`-PtestServer=127.0.0.1:25565` 可让测试客户端自动连接本地服务器。

输出：`build/libs/ae2utility-1.8.1.jar`。开发 API 见 [docs/API.md](docs/API.md)。更新日志仅记录用户可见变化，见 [CHANGELOG.md](CHANGELOG.md)。

## English

AE2 Utility for **Minecraft 26.1.2, NeoForge 26.1.2.126 and Java 25**. This branch uses target-version APIs; Minecraft 1.21.1 binaries are not compatible.

Features include ME ingredient transfer, JEI/EMI pattern encoding and preview, batch uploads, recipe filtering, NBT Tear and Redstone Signal cards, the ME Quick Transfer Tool, server policies and public APIs.

Core requirements are AE2 `26.1.13-beta`, JEI `29.53.0.115` on clients, and their GuideME/MezzConfig dependencies. Optional integrations remain isolated when absent.

The target EMI integration uses the **unofficial PF-EMI port**; chemical transfer uses **unofficial Mekanism / Applied Mekanistics Version Locked ports**. These must not be confused with official releases. NeoEco `26.2.15-beta` rejects AE2LT versions below `1.1.3`; the provided test profiles keep NeoEco separate from the available AE2LT `1.0.1alpha` release. Optional bridges without a verified target release are retained but are not claimed as runtime-tested.

Run `./gradlew build` for compilation and tests. The commands above provide core and optional-integration development runs. See [the API documentation](docs/API.md), [changelog](CHANGELOG.md) and [MIT license](LICENSE).
