# Changelog

## 1.7.5

### 中文

#### 修改

- 修复多候选 JEI 配方可能导致断开连接的问题，超限请求现在会安全停止并显示提示。
- 修复未启用 NBT 撕裂卡时，处理样板输入可能被错误拦截的问题。
- 修复通过 JEI 或 EMI 编码的样板缺少 ExtendedAE Plus 编码者信息的问题。
- 修复通过 AE2 Utility 上传的处理样板无法使用 ExtendedAE Plus“撤回上次上传样板”功能的问题。

### English

#### Changed

- Fixed JEI recipes with many ingredient alternatives potentially causing disconnects; oversized requests now stop safely and display a message.
- Fixed processing-pattern inputs potentially being intercepted without an active NBT Tear Card.
- Fixed patterns encoded through JEI or EMI missing ExtendedAE Plus encoder attribution.
- Fixed processing patterns uploaded through AE2 Utility not working with ExtendedAE Plus's “return last uploaded pattern” feature.

## 1.7.4

### 中文

#### 修复

- 优化大量样板供应器场景下的功能卡检测性能，降低服务端延迟。
- 优化红石发信卡状态机，减少无效对象创建和重复读取。
- 修复 AE2U 功能卡槽错误接收其它模组升级卡的问题。

### English

#### Fixed

- Improved feature-card detection performance with large numbers of pattern providers to reduce server latency.
- Optimized the redstone signal-card state machine to avoid unnecessary allocations and repeated reads.
- Fixed the AE2U feature-card slot incorrectly accepting upgrade cards from other mods.

## 1.7.3

### 中文

#### 新增

- 新增面向第三方模组的 `Ae2UtilityApi` v1，统一提供样板编码与上传、终端与机器拉料、机器传输注册、功能卡状态机和配方查找分类扩展能力。
- 新增新版 JEI Crafting Tree 的可编辑样板草稿后端，支持草稿验证、数量与候选编辑，以及合成、锻造、切石和处理样板的编码与上传。
- 新增公共 API 开发文档和 API Javadoc 发布产物。

#### 修改

- 修改 JEI/EMI 多候选输入的编码逻辑，优先采用收藏栏命中的候选；该行为现在由统一的客户端配置控制并默认开启，关闭后恢复原始候选顺序。
- 修改机器传输配置与配方查找分类器，使其支持第三方注册扩展，并加强 API 请求与候选数据的不可变性和边界校验。
- 修改模组显示名称为 `AE2 Utility`，并按 1.7.3 当前功能重写中英文 README。

#### 修复

- 修复 JEI Crafting Tree 草稿编辑数量可能被原始原料数量覆盖、部分版本候选结构差异导致输入丢失，以及锻造和切石样板模式识别不准确的问题。
- 修复 Applied Mekanistics 化学品子类型可能无法转换为 AE2 存储键的问题。

#### 移除

- 移除无条件输出的启动调试探针和过期的内部改进建议文档。

### English

#### Added

- Added the `Ae2UtilityApi` v1 integration API for pattern encoding and upload, terminal and machine transfers, machine transfer registration, card state machines, and recipe-finder classification extensions.
- Added an editable pattern-draft backend for the latest JEI Crafting Tree, including draft validation, amount and alternative editing, and encode/upload support for crafting, smithing, stonecutting, and processing patterns.
- Added public API documentation and an API Javadoc artifact.

#### Changed

- Changed JEI/EMI encoding of multi-alternative inputs to prefer matching favorites. A shared client option now controls this behavior, defaults to enabled, and restores the original alternative order when disabled.
- Changed machine transfer profiles and recipe-finder classifiers to support third-party registration, with stronger immutability and boundary validation for API requests and candidate data.
- Changed the displayed mod name to `AE2 Utility` and rewrote the English and Chinese README for the current 1.7.3 feature set.

#### Fixed

- Fixed JEI Crafting Tree draft amounts being overwritten by original ingredient amounts, inputs being lost with differing candidate structures, and smithing or stonecutting drafts using an incorrect pattern mode.
- Fixed Applied Mekanistics chemical subtypes sometimes failing to convert into AE2 storage keys.

#### Removed

- Removed the unconditional startup debug probe and the obsolete internal improvement-suggestions document.

## 1.7.2

### 中文

1. 优化 JEI/EMI 配方界面 AE2 Utility 按钮布局。
2. 修复空白样板预检无法识别部分样板编码终端菜单槽位的问题。
3. 修复红石发信卡功能。

### English

1. Improved the AE2 Utility button layout in the JEI and EMI recipe interfaces.
2. Fixed blank-pattern prechecks missing blank patterns in some pattern-encoding terminal menu slots.
3. Fixed redstone signal card functionality.

## 1.7.0

### 中文

AE2:Utility 1.6.0-1.7.0 模组更新日志：

1. 移除配方树功能。
2. EMI 界面新增单个/批量样板编码/上传功能。
3. 上传样板兼容自动上传合成、锻造台、切石机样板至 ECO 合成子系统。
4. 优化上传样板逻辑：
   - EAEP 开关关：任何自动行为（唯一匹配 / 复用）全部停止，回到全手动。
   - EAEP 开关开 + AE2U 复用开：唯一时自动，多个时复用上次同名选择。
   - EAEP 开关开 + AE2U 复用关：只有唯一匹配才自动，多个时每次手动选。
   - 当开启批次复用、本批次已记住同名选择、但该同名供应器不在当前列表（如原供应器已满）时，再次弹出供应器选择界面进行选择后才再次上传，避免上传至不匹配的供应器。
5. 更改编码/上传样板优先级：书签 > 网络已有样板（可合成） > 未损坏 > 库存最多 > 特异性变体 > 首项。
6. 修复 JEI 编码合成配方时可能被编码成处理样板的 bug。
7. 修复批量上传时处理样板被硬塞矩阵，提示“装配矩阵已满、已中止上传”的 bug。
8. 优化多标签/多候选配方界面卡顿。

### English

AE2:Utility 1.6.0-1.7.0 changelog:

1. Removed the built-in recipe tree feature.
2. Added single and batch pattern encode/upload support to the EMI interface.
3. Added ECO crafting subsystem upload compatibility for crafting, smithing table, and stonecutting patterns.
4. Improved pattern upload behavior:
   - EAEP toggle off: all automatic behavior (unique-match upload / provider reuse) stops, returning to fully manual selection.
   - EAEP toggle on + AE2U reuse on: unique matches upload automatically; multiple matches reuse the previous same-name provider choice.
   - EAEP toggle on + AE2U reuse off: only unique matches upload automatically; multiple matches require manual selection every time.
   - When batch reuse is enabled and the batch remembers a same-name provider, but that provider is no longer in the current list (for example, it is full), the provider selection screen opens again and upload resumes only after a new choice, avoiding uploads to mismatched providers.
5. Changed encode/upload input priority to: bookmarks > existing network patterns (craftable) > undamaged > highest stock > specific variant > first entry.
6. Fixed a bug where JEI crafting recipes could be encoded as processing patterns.
7. Fixed a bug where batch upload forced processing patterns into the matrix and showed “assembly matrix full, upload aborted”.
8. Improved performance for multi-tag and multi-alternative recipe screens.

## 1.4.0

1. 新增“配方树”功能，入口设定为 JEI 界面编码按钮 `Alt + 左键` 进入。
   - 编码时将一次性编码所有已指定配方，并消耗等量样板；
   - 当安装有 ExtendedAE-Plus 模组时，启用上传功能，可一次性编码并上传所有样板；
   - 上传样板时未映射机器将一个个走映射界面，取消则将样板发送至玩家背包并收藏指定配方到 JEI；
   - 上传完成后发送已成功样板与未上传样板提示；
   - 概率产物默认不展示；
   - 网络中已含有样板时，自动禁用二次选择；
   - 在选择第一次后的配方，第二次自动匹配；
   - 若存在某种材料有多种选择，点击下拉按钮可选择其他物品，默认选择静态列表第一个；
   - 配方树界面右上角可查看配方树总览，用于查看当前配方编码情况；
   - 配方树总览界面支持选取配方，操作逻辑与配方树类似，机器图标可选择配方；
   - 界面左上角增加显示当前配方编码样板时所需空白样板数量；
   - 界面右上角增加开关禁用编码 ME 网络中已存在该样板功能；
   - 增加总材料清单，实时汇总当前配方所需材料；
   - 点击材料清单物品图标，可跳转到对应分支位置，鼠标悬停时可查看 ME 网络里是否含有该配方；
   - 存在分支的配方可进行折叠取消编码。
2. 新增 “NBT 撕裂卡” 功能卡，样板供应器放入该卡可以忽略产物与合成材料的 NBT；右键撕裂卡可打开标记界面，不标记时默认全部忽略。
3. 修复了 JEI 界面编码文字提示过长遮挡视线的 bug，按住 `SHIFT + N` 时可显示描述。
4. 修复了 JEI 界面编码时无法编码大于 9 宫格配方的 bug，且修改为编码时不合并材料、不修改顺序。
5. 修复了 JEI 拉取非合成物品时无法拉取神秘学带 NBT 物品。
6. 修复了拉取非合成物品时与原终端物品互相冲突的 bug，拉取前自动移出终端内物品至 ME 网络。
7. 修复了在安装有 Applied Mekanistics 模组时，JEI 界面编码仍无法编码带化学品样板的 bug。
8. 修改缺少样板时的提示，精简化并给出具体缺失样板名称。
9. JEI 界面编码时自动剔除概率产物，目前仅测试过 Mekanism 模组。
10. 修复 `Ctrl + Shift + 右键` 清除样板后空白样板不自动堆叠的 bug。
11. 修复了在同时安装有 Applied Flux 和 ExtendedAE-Plus 模组时样板供应器槽位显示错位、重叠、数量不符等的 bug。
   - 不添加第 3 个槽，NBT 撕裂卡作为普通升级卡可进入它们的槽，严格限制为只能放入一个；
   - 仅在纯 AE2 环境下，才添加专用撕裂卡槽。
12. 修复了在放入 NBT 撕裂卡后无法合成 Draconic Evolution 模组工具类物品的问题。
13. ExtendedAE-Plus 模组供应器选择界面搜索栏上面增加提示“正在处理样板 XXX（机器 XXX）”，机器名称显示为中文。
14. 样板编码终端界面，`Alt` 点击上传样板时可将背包里的样板全部尝试上传，取消时跳过该类机器样板上传，旧样板不可用。
15. 更换 NBT 撕裂卡材质。
