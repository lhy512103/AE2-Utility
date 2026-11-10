package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.Rect2i;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.SlotPosition;

/**
 * 供客户端按 {@link appeng.client.gui.style.ScreenStyle} 解析槽位像素（与 AE2 自带 {@code positionSlots} 一致），
 * 避免写死坐标；GUI 缩放由 MC 对整屏统一缩放，此处数值均为「缩放后 GUI 像素」。
 */
@Mixin(AEBaseScreen.class)
public interface AEBaseScreenInvoker {
    @Invoker("getBounds")
    Rect2i ae2utility$getBounds(boolean fullSize);

    @Invoker("getSlotPosition")
    Point ae2utility$getSlotPosition(SlotPosition slotPosition, int index);
}
