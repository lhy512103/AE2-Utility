package com.lhy.ae2utility.client.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.ITerminalHost;
import appeng.core.definitions.AEItems;
import appeng.integration.modules.curios.CuriosIntegration;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;

import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.service.WirelessTerminalContextResolver;

/**
 * 与服务端 {@link com.lhy.ae2utility.service.EncodePatternService#consumeOneBlankPattern} 检查顺序对齐的客户端快照：
 * 样板终端空白槽 / WCWT 空白槽占位 → 玩家背包 → 当前解析到的 ME 存储模拟提取空白样板。
 * <p>若无法解析到 ME {@link MEStorage}（未发现无线或未链接等），不因「未见网络」贸然拦截，交由服务端校验。</p>
 */
public final class BlankPatternClientPrecheck {
    private BlankPatternClientPrecheck() {
    }

    /**
     * @return {@code true}：按客户端当前快照可以确定没有任何可用空白样板，应在发起顺序批量前先提示并中止。
     */
    public static boolean lacksAnyDetectableBlankPattern(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        if (hasBlankPatternInEncodingTerminalSlots(player)) {
            return false;
        }
        if (playerInventoryHasBlankPattern(player)) {
            return false;
        }
        MEStorage me = EncodePatternPreviewStorage.resolveMeStorage(Minecraft.getInstance());
        if (me == null) {
            return false;
        }
        AEItemKey blankKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        IActionSource src = resolveSimulateSource(player);
        return me.extract(blankKey, 1, Actionable.SIMULATE, src) < 1;
    }

    private static boolean playerInventoryHasBlankPattern(LocalPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && AEItems.BLANK_PATTERN.is(stack)) {
                return true;
            }
        }
        var curios = player.getCapability(CuriosIntegration.ITEM_HANDLER);
        if (curios != null) {
            for (int s = 0; s < curios.getSlots(); s++) {
                var stack = curios.getStackInSlot(s);
                if (!stack.isEmpty() && AEItems.BLANK_PATTERN.is(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 与服务端 consume：先 AE 原版样板编码菜单，再 WCWT 等综合终端容器中出现的空白样板槽物品。 */
    private static boolean hasBlankPatternInEncodingTerminalSlots(LocalPlayer player) {
        var menu = player.containerMenu;
        if (menu instanceof PatternEncodingTermMenu patternMenu) {
            for (Slot slot : patternMenu.getSlots(SlotSemantics.BLANK_PATTERN)) {
                if (blankStackPresent(slot.getItem())) {
                    return true;
                }
            }
        }
        // Some AE2/third-party pattern terminals expose an MEStorageMenu host
        // instead of PatternEncodingTermMenu. In that case the blank-pattern
        // slots are still synchronized into the menu's regular slot list.
        if (WcwtCompat.isPatternEncodingLikeMenu(menu)) {
            for (Slot slot : menu.slots) {
                if (blankStackPresent(slot.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean blankStackPresent(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && AEItems.BLANK_PATTERN.is(stack);
    }

    /** 与服务端上下文一致尽可能带上 IActionHost（无线终端主机或当前打开的 ME 终端）。 */
    private static IActionSource resolveSimulateSource(LocalPlayer player) {
        IActionHost hinted = wiredActionHostForOpenMeMenu(player);
        if (hinted == null) {
            WirelessTerminalContextResolver.Resolution wireless = WirelessTerminalContextResolver.resolve(player);
            if (wireless.isReady() && wireless.host() instanceof IActionHost ah) {
                hinted = ah;
            }
        }
        return hinted != null ? IActionSource.ofPlayer(player, hinted) : IActionSource.ofPlayer(player);
    }

    private static IActionHost wiredActionHostForOpenMeMenu(LocalPlayer player) {
        if (!(player.containerMenu instanceof MEStorageMenu storageMenu)) {
            return null;
        }
        ITerminalHost th = WcwtCompat.extractTerminalHost(storageMenu);
        return th instanceof IActionHost ah ? ah : null;
    }
}
