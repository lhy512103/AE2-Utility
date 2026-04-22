# AE2: Utility

适用于 **Minecraft 1.21.1** + **NeoForge** 的小型模组，增强 **JEI** 与 **Applied Energistics 2** 的协同：从 ME 网络拉取配方物品、在 JEI 中编码 / 上传样板等。

## 功能介绍

### 1. JEI 样板编码按钮

在打开**样板编码终端**时，或背包 / **Curios** 中佩戴有**（无线）样板编码** / **通用无线终端**时显示编码按钮。

- **左键**：按当前 JEI 配方编码样板至玩家物品栏（JEI 页面左上角可开关「物品 / 流体 替换」）。
- **Shift + 左键**（需安装 **ExtendedAE Plus** 模组）：走映射 / 上传样板到供应器等 **EAEP** 相关流程。
- **Ctrl + Shift + 左键**：按 **Shift 上传路径**上传样板，并把无自动合成样板的输入（当前槽位物品 **JEI 默认闪烁物品列表中的第一项**）加入 JEI 收藏。
- **悬停箭头**时输入槽高亮：**蓝** = ME 网络中存在对应合成样板；**红** = 无对应合成样板。
- 按钮背景 **蓝 / 橙** 表示输出或输入侧存在可合成判断。
- **多选原材料的优先级规则**：书签优先 > ME 网络已有配方 > JEI 默认闪烁物品列表中的第一项。

### 2. JEI ↔ ME 终端拉料

支持 JEI 拉取**非合成配方**物品到 ME 终端；物品栏中含有足量的物品将不会拉取；优先放至终端网格，空间不足时再放至物品栏，均无法放置则跳过并提示。

### 3. JEI ↔ 机器界面拉料

**锻造台**、**熔炉**、**切石机**，以及 AE 部分机器，如（扩展）**压印器**、**电路切片机**、**反应仓**、**水晶装配器**等，支持 JEI 调取 ME 网络中的物品到机器中；**目前仅支持物品**。

## 依赖

| 模组 | 说明 |
|------|------|
| NeoForge | 加载器 |
| Applied Energistics 2 | ME 网络与终端 |
| Just Enough Items (JEI) | 配方界面（客户端） |
| ExtendedAE Plus | 可选：Shift 上传 / 供应器相关流程 |

## 从源码构建

```bash
./gradlew build
```

产物位于 `build/libs/`。

## 许可证

本项目以 [MIT License](LICENSE) 发布。

## 作者

维护者：**lhy**（见 `gradle.properties` 中 `mod_authors`）。

- **源码 / Issue：** [github.com/lhy512103/AE2-Utility](https://github.com/lhy512103/AE2-Utility)
- **克隆：** `git clone https://github.com/lhy512103/AE2-Utility.git`
- CurseForge / Modrinth 等发布页可在取得地址后自行补充于此。

## 首次开源到 GitHub 时可对照的检查项

- 使用本仓库已提供的 **`.gitignore`**，避免把 `build/`、`.gradle/`、`run/` 等目录推上去。
- 根目录 **`LICENSE`**（MIT）与 **`gradle.properties`** 中的 `mod_license=MIT` 保持一致。
- 在 GitHub 仓库 **Settings → General** 中填写简介，并添加 **Topics**（例如：`minecraft`、`neoforge`、`minecraft-mod`、`jei`、`applied-energistics-2`）。
- 发布版本时，用 `./gradlew build` 生成的 **`build/libs/<mod_id>-<version>.jar`**（不含 `-sources`）作为发行附件。
- 若不需要 GitHub Actions，可删除 **`.github/workflows/`** 目录。
