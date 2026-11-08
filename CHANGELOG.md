# Changelog

## 1.20.1.2

### 中文

1. 新增：补齐批量编码、顺序上传、背包样板上传和配方查找功能。
2. 新增：支持机器拉料、NBT 撕裂卡和红石发信卡的完整交互。
3. 优化：完善编码预览、材料替换和 JEI/EMI 操作体验。
4. 修复：预选后更换样板或取消上传可能导致物品消耗不一致或样板复制的问题。
5. 修复：未安装 JEI 时机器拉料可能导致服务端报错的问题。
6. 修复：与新版扩展模组共用时的样板上传、供应器选择和配方按钮兼容问题。
7. 修复：Applied Create 与 Jade 共用时因缺少配置文本而无法进入主菜单的问题。
8. 优化：配方界面关闭后及时释放编码按钮缓存，减少长时间浏览的内存占用。
9. 修复：过量材料候选的编码请求可能占用过多服务端内存的问题。

### English

1. Added: Completed batch encoding, sequential uploads, inventory pattern uploads, and recipe finding.
2. Added: Full interaction support for machine ingredient pulling, NBT Tear Cards, and Redstone Signal Cards.
3. Improved: Refined encoding previews, ingredient substitution, and JEI/EMI controls.
4. Fixed: Replacing a prepared pattern or cancelling an upload could consume the wrong item or duplicate patterns.
5. Fixed: Machine ingredient pulling could fail on servers without JEI installed.
6. Fixed: Pattern uploads, provider selection, and recipe buttons with newer addon versions.
7. Fixed: Missing configuration labels could prevent reaching the title screen with Applied Create and Jade.
8. Improved: Released encoding-button caches after recipe layouts close to reduce memory retention.
9. Fixed: Encoding requests with excessive ingredient alternatives could consume too much server memory.

## v1.20.1.1

### English

1. Added: Third-party mods can register recipe-finder material classification rules.
1. Added: JEI terminal recipe-input pulling, extracting materials from the ME network into the player inventory.
1. Added: NBT Tear Card and Redstone Signal Card items, configuration APIs, and Forge recipes.
1. Added: EMI single/batch pattern encoding, upload, and recipe-substitution buttons.
1. Added: Optional JEI encoding compatibility for Some Useless Things omnibus patterns.
1. Improved: JEI encode-button state is computed once per recipe per tick, reducing lag on multi-candidate/multi-recipe pages.
1. Added: JEI Crafting Tree integration as its AE2 encode/upload backend with editable pattern drafts; Alt+left-click the encode arrow to open the recipe tree.
1. Added: Alt+left-click the EAEP upload button on the pattern encoding terminal to batch-upload all patterns in the inventory (cancelling skips that machine group).
1. Added: ME Quick Transfer tool — after linking a wireless access point, move items, fluids, and chemicals from a container into the ME network.
1. Added: Recipe Finder that filters JEI recipes by sample, mod, machine, and material features, then batch-encodes them.
1. Added: Configurable lock of multi-candidate input slots to the actually encoded materials while hovering the JEI encode arrow.
1. Added: Configurable read-only pattern-encoding-terminal preview in the tooltip while hovering the JEI encode arrow.
1. Added: Pre-check for blank patterns before batch encoding; cancel immediately with a message if none are available.
1. Added: Single and batch encode/upload support for GTCEu/GTL and Neo ECO multiblock structure info pages.
1. Added: Shift-upload of crafting/smithing/stonecutting patterns prefers the Neo ECO AE Extension crafting subsystem and checks for duplicates.
1. Added: Configurable reuse of a previously chosen same-named provider when a batch-upload search key matches multiple providers.
1. Fixed: Batch Shift-upload could send the next pattern too early on high-latency servers and overwrite the pending pattern.
1. Added: EAEP provider selection screen shows the pattern and machine currently being processed.
1. Added: Ctrl+left-click encode bookmarks missing-pattern inputs in JEI; Ctrl+Shift+left-click also uploads.
1. Improved: Encode-button tooltip is shorter; hold Shift+N for the full control legend.
1. Added: JEI encoding automatically strips chance byproducts while keeping the primary output.
1. Added: `/ae2utility stopuploads [all]` command to abort batch uploads.
1. Added: Batch encoding skips patterns that already exist on the ME network.
1. Added: Batch encoding stops remaining recipes when blank patterns run out.
1. Added: Client config option to hide the "entire category, all pages" batch-encode button.
1. Added: Server config to require an open encoding UI, disable full-category batch encoding, and cap batch size.
1. Added: Configurable preference for JEI bookmarks when encoding multi-candidate inputs.
1. Fixed: Multi-candidate encoding could pick a recipe output as an input, creating a self-looping pattern.
1. Fixed: Recipes with too many ingredient alternatives could exceed the packet size limit and disconnect.
1. Fixed: Severe lag when viewing JEI recipes with huge ingredient counts while an AE terminal is open.
1. Fixed: Encode button did nothing for GregTech-style recipes with multiple output slots.
1. Improved: Encoding now shows a message when a recipe contains unsupported ingredients.
1. Improved: Encode buttons are hidden on tag pages, info pages, and other layouts with nothing to encode.

### 中文

1. 新增：第三方模组可注册配方查找器材料分类规则。
1. 新增：JEI 终端配方输入拉取，可从 ME 网络提取材料到玩家背包。
1. 新增：NBT 撕裂卡与红石发信卡基础物品、配置 API 和 Forge 配方资源。
1. 新增：EMI 单条/批量样板编码、上传及配方替换按钮。
1. 新增：可选 Some Useless Things 万象样板 JEI 编码兼容。
1. 优化：JEI 编码按钮状态按配方每 tick 只计算一次，减少多候选/多配方页面的卡顿
1. 新增：JEI Crafting Tree 集成，作为其 AE2 编码/上传后端并支持可编辑样板草稿，编码箭头 Alt+左键打开配方树
1. 新增：样板编码终端 Alt+左键 EAEP 上传按钮，批量上传背包中的全部样板（取消时跳过该机器分组）
1. 新增：ME快捷转移工具，绑定无线接入点后可把容器内物品、流体、化学品直接转入 ME 网络
1. 新增：配方查找器，可按样本、模组、机器和材料特征筛选 JEI 配方并批量编码
1. 新增：悬停 JEI 编码箭头时多候选输入槽固定显示实际编码材料（可配置）
1. 新增：悬停 JEI 编码箭头时 tooltip 显示只读的样板编码终端预览（可配置）
1. 新增：批量编码前预检空白样板，确定没有时直接提示并取消
1. 新增：单条与批量编码/上传支持 GTCEu/GTL、Neo ECO 的多方块结构信息页
1. 新增：Shift 上传合成/锻造/切石样板时优先写入 Neo ECO AE Extension 合成子系统，并检查重复
1. 新增：批量上传时同一关键字匹配多个供应器会复用本批次选过的同名供应器（可配置）
1. 修复：批量 Shift 上传在服务器延迟较高时可能提前发送下一条导致待定样板被覆盖
1. 新增：EAEP 供应器选择界面显示当前处理的样板与机器名
1. 新增：Ctrl+左键编码时把缺少样板的输入加入 JEI 书签，Ctrl+Shift+左键同时上传
1. 优化：编码按钮提示精简，按住 Shift+N 查看完整操作说明
1. 新增：JEI 编码时自动剔除概率副产物（主产物保留）
1. 新增：/ae2utility stopuploads [all] 命令，用于中止批量上传
1. 新增：批量编码自动跳过 ME 网络中已存在的相同样板
1. 新增：批量编码时空白样板用完会停止剩余配方
1. 新增：客户端配置可隐藏「整类全分页」批量编码按钮
1. 新增：服务端配置，可要求打开编码界面、禁止整类批量编码、限制单次批量条数
1. 新增：多候选输入编码时优先使用 JEI 书签（客户端可配置）
1. 修复：多候选编码可能把配方产物选作输入导致自循环样板
1. 修复：候选材料过多的配方编码时可能超出网络包上限导致断开连接
1. 修复：打开 AE 终端时查看材料数量极大的 JEI 配方严重卡顿
1. 修复：格雷等多输出槽机器配方点击编码无反应
1. 优化：配方含不支持的材料时编码给出提示
1. 优化：标签页、信息页等无可编码输入输出的页面不再显示编码按钮

## v1.20.1.0

### English

1. Added an in-game Forge configuration screen accessible from the Mods list.
2. Added Curios-slot detection for AE2WTLib and WCWT wireless terminals when encoding without an open terminal.
3. Integrated the four AE2 Utility recipe-option buttons into JEI's native expandable option tab.
4. Fixed EAEP provider discovery when a WCWT terminal is equipped in a Curios slot.
5. Fixed empty Advanced AE Reaction Chamber mappings during JEI pattern uploads.

### 中文

1. 新增可从模组列表直接进入的 Forge 游戏内配置界面。
2. 未打开终端进行编码时，新增对 Curios 饰品栏内 AE2WTLib 与 WCWT 无线终端的识别。
3. 将四个 AE2 Utility 配方选项按钮整合进 JEI 原生可扩展选项栏。
4. 修复 WCWT 终端放入 Curios 饰品栏时，EAEP 映射界面无法获取供应器列表的问题。
5. 修复 JEI 上传时 Advanced AE 反应仓映射为空的问题。

## v1.20.1

### English

1. Added JEI single-recipe pattern encoding and Shift-upload support for ExtendedAE Plus providers and assembly matrices.
2. Added JEI highlight previews for craftable outputs and reusable/craftable inputs.
3. Removed the duplicate AE2 Utility assembly matrix success message; ExtendedAE Plus already shows its own upload result.
4. Fixed processing-pattern upload highlights so the encode button state updates immediately after the provider upload succeeds.
5. Replaced the Forge MDK README with an AE2: Utility project README.
6. Added Ctrl+Shift right-click conversion from encoded patterns back to blank patterns.
7. Added JEI-local recipe option buttons for item and fluid substitutions, with state persisted locally for JEI encoding and uploads.
8. Added JEI recipe option buttons for batch encoding/uploading all recipes on the current page or every page in the current JEI category.
9. Cancelling an EAEP provider selection during JEI batch upload now cancels the remaining batch queue.

### 中文

1. 新增 JEI 单个配方样板编码功能，并支持 Shift 上传到 ExtendedAE Plus 供应器与装配矩阵；
2. 新增 JEI 可合成输出与可复用/可合成输入的高亮预览；
3. 移除 AE2 Utility 自己额外发送的装配矩阵上传成功提示，避免和 ExtendedAE Plus 原有提示重复；
4. 修复处理样板上传到供应器后编码按钮高亮状态不能立即更新的问题；
5. 将 Forge MDK 默认 README 替换为 AE2: Utility 模组说明；
6. 新增按住ctrl+shift手持已编码样板右键时批量转化为空白样板。
7. 在 JEI 配方选项区域新增 JEI 独立的物品替换与流体替换按钮，状态本地持久化并用于 JEI 编码/上传样板。
8. 在 JEI 配方选项区域新增一键编码/上传当前页与当前 JEI 分类全部分页配方的按钮。
9. 在 JEI 批量上传期间取消 EAEP 供应器选择时，会一并取消剩余批量上传队列。
