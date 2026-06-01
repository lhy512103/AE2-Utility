package com.lhy.ae2utility.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.network.UploadInventoryPatternsToMatrixPacket;
import com.lhy.ae2utility.compat.WcwtCompat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class InventoryPatternMatrixUploadService {
    private InventoryPatternMatrixUploadService() {
    }

    public static void handle(ServerPlayer player, UploadInventoryPatternsToMatrixPacket payload) {
        if (player == null || payload == null || payload.slotIndices().isEmpty()) {
            InventoryPatternUploadDebug.warn("matrix_upload", "playerOrPayload invalid player={} payloadEmpty={}",
                    player != null, payload == null || payload.slotIndices().isEmpty());
            return;
        }

        IGrid grid = resolveGrid(player);
        if (grid == null) {
            InventoryPatternUploadDebug.warn("matrix_upload", "no grid resolved slots={}", payload.slotIndices());
            return;
        }
        InventoryPatternUploadDebug.info("matrix_upload", "resolved grid={} nodeClasses={}",
                grid.getClass().getName(), summarizeMachineClasses(grid));

        List<Integer> remainingSlots = new ArrayList<>();

        for (Integer slotIndex : payload.slotIndices()) {
            if (slotIndex == null || slotIndex.intValue() < 0 || slotIndex.intValue() >= player.getInventory().getContainerSize()) {
                continue;
            }
            ItemStack stack = player.getInventory().getItem(slotIndex.intValue());
            if (stack.isEmpty()) {
                continue;
            }

            InventoryPatternUploadDebug.info("matrix_upload", "slot={} count={} item={}",
                    slotIndex, stack.getCount(), stack.getItem());

            boolean fullyUploaded = true;

            // Only try matrix for matrix-capable patterns
            if (isMatrixCapablePattern(player, stack)) {
                while (!stack.isEmpty()) {
                    if (!uploadSinglePatternToMatrix(player, stack.copyWithCount(1), grid)) {
                        InventoryPatternUploadDebug.info("matrix_upload", "slot={} stopped by upload failure", slotIndex);
                        fullyUploaded = false;
                        break;
                    }
                    stack.shrink(1);
                    InventoryPatternUploadDebug.info("matrix_upload", "slot={} uploaded one remaining={}", slotIndex, stack.getCount());
                }
            } else {
                fullyUploaded = false;
            }

            if (!fullyUploaded && !stack.isEmpty()) {
                remainingSlots.add(slotIndex);
            }

            player.getInventory().setItem(slotIndex.intValue(), stack.isEmpty() ? ItemStack.EMPTY : stack);
        }

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        if (!remainingSlots.isEmpty()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.lhy.ae2utility.network.FallbackToProviderSelectionPacket(remainingSlots));
            InventoryPatternUploadDebug.info("matrix_upload", "sent fallback packet with slots={}", remainingSlots);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("全部样板已自动上传到装配矩阵").withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
    }

    private static boolean isMatrixCapablePattern(ServerPlayer player, ItemStack stack) {
        try {
            IPatternDetails details = appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, player.level());
            return details instanceof AECraftingPattern
                    || details instanceof AESmithingTablePattern
                    || details instanceof AEStonecuttingPattern;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("matrix_upload", "decode failed stack={} error={}", stack, t.toString());
            return false;
        }
    }

    /**
     * JEI / {@link EncodePatternService} 在 EAEP {@code uploadPatternToMatrix} 返回 false 时的二次路径：
     * 与 Alt+背包批量上传相同，直接向装配矩阵 {@link InternalInventory} 塞样板（含处理类等）。
     */
    public static boolean tryDirectMatrixInsert(ServerPlayer player, ItemStack pattern, IGrid grid) {
        if (player == null || pattern == null || pattern.isEmpty() || grid == null) {
            return false;
        }
        return uploadSinglePatternToMatrix(player, pattern, grid);
    }

    private static boolean uploadSinglePatternToMatrix(ServerPlayer player, ItemStack pattern, IGrid grid) {
        List<InternalInventory> inventories = findDirectMatrixInventories(grid);
        InventoryPatternUploadDebug.info("matrix_upload", "direct inventories found={} pattern={}", inventories.size(), pattern);
        for (int i = 0; i < inventories.size(); i++) {
            InternalInventory inv = inventories.get(i);
            if (inv == null) {
                continue;
            }
            ItemStack remain = inv.addItems(pattern.copy());
            boolean uploaded = remain.getCount() < pattern.getCount();
            InventoryPatternUploadDebug.info("matrix_upload", "try inventoryIndex={} uploaded={} remain={}",
                    i, uploaded, remain.getCount());
            if (uploaded) {
                return true;
            }
        }
        return false;
    }

    private static IGrid resolveGrid(ServerPlayer player) {
        if (player.containerMenu instanceof PatternEncodingTermMenu menu) {
            try {
                if (menu instanceof AEBaseMenu abm) {
                    Object target = abm.getTarget();
                    if (target instanceof IActionHost host && host.getActionableNode() != null) {
                        IGrid grid = host.getActionableNode().getGrid();
                        if (grid != null) {
                            InventoryPatternUploadDebug.info("matrix_upload", "resolved from open menu target={}",
                                    target.getClass().getName());
                            return grid;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        } else if (WcwtCompat.isWcwtMenu(player.containerMenu)) {
            try {
                var host = WcwtCompat.extractTerminalHost(player.containerMenu);
                if (host instanceof IActionHost actionHost && actionHost.getActionableNode() != null) {
                    IGrid grid = actionHost.getActionableNode().getGrid();
                    if (grid != null) {
                        InventoryPatternUploadDebug.info("matrix_upload", "resolved from wcwt host");
                        return grid;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        IGrid grid = com.lhy.ae2utility.integration.eaep.EaepReflection.findPlayerGrid(player);
        if (grid != null) {
            InventoryPatternUploadDebug.info("matrix_upload", "resolved from pending util");
        }
        return grid;
    }

    private static List<InternalInventory> findDirectMatrixInventories(IGrid grid) {
        if (grid == null) {
            return List.of();
        }

        List<MatrixInventoryEntry> result = new ArrayList<>();
        Set<Object> seenHosts = Collections.newSetFromMap(new IdentityHashMap<>());

        try {
            for (Class<?> machineClass : grid.getMachineClasses()) {
                if (!isDirectMatrixMachine(machineClass)) {
                    continue;
                }

                @SuppressWarnings({ "rawtypes", "unchecked" })
                Set<?> machines = grid.getMachines((Class) machineClass);
                for (Object machine : machines) {
                    if (machine == null || !seenHosts.add(machine) || !isUsableMatrixMachine(machine)) {
                        continue;
                    }

                    InternalInventory exposed = getExposedInventory(machine);
                    BlockPos approximatePos = approximateMachineWorldPos(machine);
                    if (exposed != null) {
                        result.add(new MatrixInventoryEntry(approximatePos, exposed));
                        InventoryPatternUploadDebug.info("matrix_upload", "usable matrix machine={} pos={} inventoryAdded=true",
                                machine.getClass().getName(), approximatePos.toShortString());
                    } else {
                        InventoryPatternUploadDebug.info("matrix_upload", "usable matrix machine={} pos={} inventoryAdded=false",
                                machine.getClass().getName(), approximatePos.toShortString());
                    }
                }
            }

            /*
             * 多台装配矩阵/核心共存时迭代顺序未定，会向「第一台」过量塞样板导致屏幕上出现零散空槽。
             * 按方块坐标稳定排序，使插入顺序与多数合并界面从上到下、从前到后的观感更接近。
             */
            result.sort(Comparator.comparingInt(MatrixInventoryEntry::x)
                    .thenComparingInt(MatrixInventoryEntry::y)
                    .thenComparingInt(MatrixInventoryEntry::z));
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("matrix_upload", "find inventories failed error={}", t.toString());
        }

        ArrayList<InternalInventory> out = new ArrayList<>(result.size());
        for (MatrixInventoryEntry entry : result) {
            out.add(entry.inventory());
        }
        return out;
    }

    /**
     * 配方树等与 JEI 顺序 Shift 批量区分：<code>preserveInputOrder == true</code> 可走供应器备选。
     */
    private static BlockPos approximateMachineWorldPos(Object machine) {
        if (machine == null) {
            return BlockPos.ZERO;
        }
        try {
            Method getPos = machine.getClass().getMethod("getBlockPos");
            Object pos = getPos.invoke(machine);
            if (pos instanceof BlockPos blockPos) {
                return blockPos;
            }
        } catch (Throwable ignored) {
        }
        return BlockPos.ZERO;
    }

    private record MatrixInventoryEntry(BlockPos position, InternalInventory inventory) {
        int x() {
            return position.getX();
        }

        int y() {
            return position.getY();
        }

        int z() {
            return position.getZ();
        }
    }

    private static List<String> summarizeMachineClasses(IGrid grid) {
        try {
            List<String> names = new ArrayList<>();
            for (Class<?> machineClass : grid.getMachineClasses()) {
                String name = machineClass == null ? "null" : machineClass.getName();
                if (name.contains("matrix") || name.contains("Pattern") || name.contains("Assembler")) {
                    names.add(name);
                }
            }
            return names;
        } catch (Throwable t) {
            return List.of("error:" + t);
        }
    }

    private static boolean isDirectMatrixMachine(Class<?> machineClass) {
        if (machineClass == null) {
            return false;
        }
        String className = machineClass.getName();
        return "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern".equals(className)
                || "com.extendedae_plus.content.matrix.PatternCorePlusBlockEntity".equals(className);
    }

    private static boolean isUsableMatrixMachine(Object machine)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method isFormed = machine.getClass().getMethod("isFormed");
        Object formed = isFormed.invoke(machine);
        if (!(formed instanceof Boolean) || !((Boolean) formed).booleanValue()) {
            return false;
        }

        Method getMainNode = machine.getClass().getMethod("getMainNode");
        Object node = getMainNode.invoke(machine);
        if (node == null) {
            return false;
        }

        Method isActive = node.getClass().getMethod("isActive");
        Object active = isActive.invoke(node);
        return active instanceof Boolean && ((Boolean) active).booleanValue();
    }

    private static InternalInventory getExposedInventory(Object machine) {
        try {
            Method method = machine.getClass().getMethod("getExposedInventory");
            Object inventory = method.invoke(machine);
            return inventory instanceof InternalInventory internalInventory ? internalInventory : null;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("matrix_upload", "getExposedInventory failed machine={} error={}",
                    machine.getClass().getName(), t.toString());
            return null;
        }
    }
}
