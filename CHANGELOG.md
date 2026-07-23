# Changelog

## 20.1.0

### English

1. Added an in-game Forge configuration screen accessible from the Mods list.
2. Added Curios-slot detection for AE2WTLib and WCWT wireless terminals when encoding without an open terminal.
3. Integrated the four AE2 Utility recipe-option buttons into JEI's native expandable option tab.
4. Moved the AE2 Utility JEI recipe-option buttons above JEI's craftable-first and bookmark-first buttons.
5. Added Advanced AE Reaction Chamber provider mapping for JEI pattern uploads.
6. Redesigned the configuration screen with left-aligned option descriptions and compact right-side toggle buttons.
7. Completed the Forge 1.20.1 release metadata, project license, contribution guide, repository links, and CI artifact packaging.

### 中文

1. 新增可从模组列表直接进入的 Forge 游戏内配置界面。
2. 未打开终端进行编码时，新增对 Curios 饰品栏内 AE2WTLib 与 WCWT 无线终端的识别。
3. 将四个 AE2 Utility 配方选项按钮整合进 JEI 原生可扩展选项栏。
4. 将 AE2 Utility 的 JEI 配方选项按钮移动到“优先显示可合成配方”和“优先显示书签配方”按钮上方。
5. 新增 Advanced AE 反应仓在 JEI 上传样板时的供应器机器映射。
6. 重做配置界面布局，改为左侧选项说明、右侧紧凑开关按钮。
7. 补齐 Forge 1.20.1 发布所需的项目许可证、贡献指南、仓库链接与 CI 构建产物配置。

## 0.0.1-forge-1.20.1

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
