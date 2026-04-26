# AE2: Utility

适用于 **Minecraft 1.21.1** + **NeoForge** 的 AE2 辅助模组，增强 **JEI** 与 **Applied Energistics 2** 的协同：从 ME 网络拉取配方物品、在 JEI 中编码 / 上传样板、批量配方树编码、NBT 撕裂卡等。

AE2: Utility is a helper mod for **Minecraft 1.21.1** + **NeoForge** that improves the workflow between **JEI** and **Applied Energistics 2**, including recipe transfer from the ME network, pattern encoding/upload, recipe tree batch encoding, and NBT Tear Card support.

## 功能介绍 / Features

### 中文

AE2: Utility 模组主要功能介绍：

1. 在 ME / 无线 / 合成 / 无线合成等 AE2 终端打开 JEI 配方时，可用 JEI `+` 从网络取物填配方。
2. 支持从 ME 网络向熔炉 / 烟熏炉 / 高炉 / 切石机 / 锻造台 / AE2 压印器等界面按 JEI 配方请求物品，部分 Advanced AE / ExtendedAE 机器在类存在时自动启用同类拉料（以当前模组内置列表为准），JEI 转移带槽位预检高亮。
3. 在样板编码终端打开时，或背包 / Curios 中带样板编码 / 通用无线终端时显示编码按钮。
   - 左键：按当前 JEI 配方编码样板至玩家物品栏（JEI 页面左上角显示物品 / 流体替换开关）。
   - Shift + 左键（需安装 ExtendedAE-Plus）：走映射 / 上传样板到供应器等 EAEP 相关流程。
   - Ctrl + Shift + 左键：按 Shift 上传路径上传样板，并把无自动合成样板的输入（当前槽位物品 JEI 默认闪烁物品静态列表中的第一项）加入 JEI 收藏。
   - 悬停箭头时输入槽高亮：蓝 = ME 网络中存在对应合成样板；红 = 无对应合成样板。
   - 按钮背景蓝 / 橙表示输出或输入侧存在可合成判断。
   - 多选原材料的优先级规则：书签优先 > ME 网络已有配方 > JEI 默认闪烁物品静态列表中的第一项。
4. 新增“配方树”功能，入口设定为 JEI 界面编码按钮 `Alt + 左键` 进入。
   - 编码时将一次性编码所有已指定配方，并消耗等量样板。
   - 当安装有 ExtendedAE-Plus 模组时，启用上传功能，可一次性编码并上传所有样板。
   - 上传样板时未映射机器将一个个走映射界面，取消则将样板发送至玩家背包并收藏指定配方到 JEI。
   - 上传完成后发送已成功样板与未上传样板提示。
   - 概率产物默认不展示。
   - 网络中已含有样板时，自动禁用二次选择。
   - 在选择第一次后的配方，第二次自动匹配。
   - 若存在某种材料有多种选择，点击下拉按钮可选择其他物品，默认选择静态列表第一个。
   - 配方树界面右上角可查看配方树总览，用于查看当前配方编码情况。
   - 配方树总览界面支持选取配方，操作逻辑与配方树类似，机器图标可选择配方。
   - 界面左上角增加显示当前配方编码样板时所需空白样板数量。
   - 界面右上角增加开关，禁用编码 ME 网络中已存在该样板功能。
   - 增加总材料清单，实时汇总当前配方所需材料。
   - 点击材料清单物品图标，可跳转到对应分支位置，鼠标悬停时可查看 ME 网络里是否含有该配方。
   - 修复存在 NBT 的物品在配方树总览界面显示错误的 bug。
   - 存在分支的配方可进行折叠取消编码。
5. ExtendedAE-Plus 模组供应器选择界面搜索栏上面增加提示“正在处理样板 XXX（机器 XXX）”，机器名称显示为中文。
6. 样板编码终端界面，`Alt` 点击上传样板时可将背包里的样板全部尝试上传，取消时跳过该类机器样板上传，旧样板不可用。
7. JEI 界面编码时自动剔除概率产物，目前仅测试过 Mekanism 模组。

### English

Main features of AE2: Utility:

1. When viewing JEI recipes in AE2 ME / wireless / crafting / wireless crafting terminals, you can use JEI `+` to pull required ingredients from the ME network into the recipe.
2. Supports JEI-driven ingredient transfer from the ME network into furnaces, smokers, blast furnaces, stonecutters, smithing tables, AE2 inscribers, and more. Some Advanced AE / ExtendedAE machines are enabled automatically when their classes are present. Slot pre-check highlighting is included before transfer.
3. Shows an encode button when the pattern encoding terminal is open, or when a pattern terminal / universal wireless terminal is available from inventory or Curios.
   - Left click: encode the current JEI recipe into a pattern and place it into the player inventory.
   - Shift + Left click (requires ExtendedAE-Plus): use the EAEP provider mapping / upload workflow.
   - Ctrl + Shift + Left click: use the Shift upload path and bookmark JEI inputs that still do not have an autocrafting pattern.
   - Hovering the encode arrow highlights inputs: blue = craftable in the ME network, red = not craftable.
   - Blue / orange button backgrounds indicate craftable state on the output or input side.
   - Ingredient priority for multi-choice inputs: bookmarks > existing ME patterns > the first static JEI cycling entry.
4. Adds a Recipe Tree feature, opened by `Alt + Left click` on the JEI encode button.
   - Encodes all selected sub-recipes in one pass and consumes matching blank patterns.
   - When ExtendedAE-Plus is installed, all selected patterns can be encoded and uploaded in batch.
   - Unmapped machine patterns are handled one by one through the provider selection screen; cancelling sends the pattern back to the player and bookmarks the related recipe in JEI.
   - Shows success / failure feedback after batch upload.
   - Probabilistic outputs are hidden by default.
   - Existing patterns in the ME network disable duplicate selection automatically.
   - Once a recipe choice is made for a branch, matching branches can auto-select it next time.
   - Alternative materials can be selected through a dropdown button; the default is the first static JEI entry.
   - A Recipe Tree overview button is available in the upper-right corner.
   - The overview supports recipe selection with logic similar to the main tree; machine icons can also be used to select recipes.
   - The upper-left corner shows how many blank patterns are required for the current tree.
   - The upper-right corner includes a toggle to skip encoding patterns that already exist in the ME network.
   - A live total material list summarizes all required materials.
   - Clicking material icons jumps to the related branch, and hovering shows whether the ME network already contains the pattern.
   - Fixes incorrect display for ingredients containing NBT data in the overview screen.
   - Branches can be collapsed to cancel encoding for that path.
5. ExtendedAE-Plus provider selection now shows “Currently processing pattern XXX (machine XXX)” above the search box, with machine names localized to Chinese.
6. `Alt` clicking upload in the pattern encoding terminal attempts to upload all encoded patterns from the player inventory; cancelling skips that machine group, while old patterns without stored mapping data are not supported.
7. Probabilistic outputs are automatically filtered during JEI encoding. Currently tested mainly with Mekanism recipes.

## 依赖 / Dependencies

| 模组 / Mod | 说明 / Notes |
|------|------|
| NeoForge | 加载器 / Loader |
| Applied Energistics 2 | ME 网络与终端 / ME network and terminals |
| Just Enough Items (JEI) | 配方界面（客户端） / Recipe UI (client) |
| ExtendedAE Plus | 可选：Shift 上传 / 供应器相关流程 / Optional: Shift upload and provider workflows |

## 从源码构建 / Build From Source

```bash
./gradlew build
```

产物位于 `build/libs/`。  
Artifacts are generated in `build/libs/`.

## 变更记录 / Changelog

见 [CHANGELOG.md](CHANGELOG.md)。

## 许可证 / License

本项目以 [MIT License](LICENSE) 发布。  
This project is licensed under the [MIT License](LICENSE).

