# Changelog

## 0.0.1

### English

1. Fixed: Restore item models and crafting recipes for utility cards and the ME Quick Transfer Tool on Minecraft 26.1.2.
2. Improved: Port recipe transfer, pattern encoding, utility-card menus and network synchronization to Minecraft 26.1.2.
3. Fixed: Preserve utility-card contents when saving and reloading AE2 and supported addon pattern providers.
4. Removed: Remove the Recipe Finder item and its filtering interface while retaining JEI/EMI pattern encoding and batch uploads.
5. Fixed: Keep shared JEI recipe access available when EMI handles plugin callbacks.
6. Fixed: Preserve custom ingredient components when selecting fallback items for recipe transfer.
7. Fixed: Restore the JEI item list when ExtendedAE Plus is installed.
8. Fixed: Limit machine recipe transfer quantities to the available input slots and ingredient stack sizes.
9. Fixed: Clear upload queues and provider selection state when disconnecting from a server.
10. Fixed: Clear expired wireless terminal contexts and release cached contexts on logout and server shutdown.
11. Fixed: Count retained patterns toward batch limits and release quota when encoding fails or blank patterns are refunded.
12. Fixed: Restore Shift-click pattern uploads when EAEP's unrelated matrix integration cannot load.
13. Fixed: Handle empty JEI input slots when previewing recipe transfers in machines and ME terminals.

### 中文

1. 修复：恢复 Minecraft 26.1.2 中功能卡与 ME 快捷转移工具的物品模型和合成配方。
2. 优化：将配方拉料、样板编码、功能卡界面与网络同步适配至 Minecraft 26.1.2。
3. 修复：保存与重载 AE2 及受支持扩展供应器时正确保留功能卡内容。
4. 移除：删除配方查找器物品及筛选界面，保留 JEI/EMI 样板编码与批量上传。
5. 修复：EMI 接管插件回调时仍可访问共享的 JEI 配方数据。
6. 修复：配方拉料选择备用物品时保留自定义原料的数据组件。
7. 修复：安装 ExtendedAE Plus 时 JEI 物品列表不显示的问题。
8. 修复：按机器输入槽位和原料堆叠上限限制配方拉料数量。
9. 修复：断线时清理上传队列和供应器选择状态。
10. 修复：主动清理过期无线终端缓存，并在退出和停服时释放缓存。
11. 修复：批量编码按实际保留的样板计数，编码失败或退还空白样板时释放额度。
12. 修复：EAEP 矩阵兼容代码加载异常时 Shift 点击编码箭头无法上传样板的问题。
13. 修复：机器与 ME 终端的 JEI 配方拉料预览遇到空输入槽时出现异常的问题。
