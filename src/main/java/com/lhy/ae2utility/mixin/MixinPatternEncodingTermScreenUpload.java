package com.lhy.ae2utility.mixin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.client.InventoryPatternUploadQueue;
import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.network.UploadInventoryPatternsToMatrixPacket;

import appeng.api.crafting.IPatternDetails;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.WidgetStyle;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

@Mixin(PatternEncodingTermScreen.class)
public class MixinPatternEncodingTermScreenUpload {
    private static final String REQUEST_PACKET_CLASS = "com.extendedae_plus.network.RequestProvidersListC2SPacket";

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2utility$batchUploadInventoryPatterns(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        boolean altDown = Screen.hasAltDown();
        boolean ctrlDown = Screen.hasControlDown();
        boolean shiftDown = Screen.hasShiftDown();
        if (button == 0 && altDown) {
            InventoryPatternUploadDebug.info("mouse_clicked",
                    "screen={} mouseX={} mouseY={} alt={} ctrl={} shift={}",
                    this.getClass().getName(), mouseX, mouseY, altDown, ctrlDown, shiftDown);
        }
        if (button != 0 || !altDown || ctrlDown || shiftDown) {
            return;
        }
        if (!ModList.get().isLoaded("extendedae_plus")) {
            InventoryPatternUploadDebug.warn("mouse_clicked", "extendedae_plus not loaded");
            return;
        }

        PatternEncodingTermScreen<?> screen = (PatternEncodingTermScreen<?>) (Object) this;
        if (!ae2utility$isOverUploadButton(screen, mouseX, mouseY)) {
            InventoryPatternUploadDebug.info("mouse_clicked", "not over upload button");
            return;
        }
        InventoryPatternUploadDebug.info("mouse_clicked", "upload button hit");

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            InventoryPatternUploadDebug.warn("mouse_clicked", "player is null after hit");
            cir.setReturnValue(true);
            return;
        }

        var patternSlots = InventoryPatternUploadQueue.collectEncodedPatternSlots(player);
        if (patternSlots.isEmpty()) {
            InventoryPatternUploadDebug.warn("mouse_clicked", "no encoded patterns found in inventory");
            player.displayClientMessage(Component.literal("背包里没有可上传的已编码样板").withStyle(ChatFormatting.RED), true);
            cir.setReturnValue(true);
            return;
        }

        PacketDistributor.sendToServer(new UploadInventoryPatternsToMatrixPacket(patternSlots));
        InventoryPatternUploadDebug.info("mouse_clicked", "requested upload for all pattern slots={}", patternSlots);

        cir.setReturnValue(true);
    }

    private static boolean ae2utility$isOverUploadButton(PatternEncodingTermScreen<?> screen, double mouseX, double mouseY) {
        try {
            WidgetStyle widget = ((AEBaseScreenStyleAccessor) screen).ae2utility$getStyle().getWidget("encodePattern");
            Rect2i bounds = ((AEBaseScreenInvoker) screen).ae2utility$getBounds(true);
            var pos = widget.resolve(bounds);
            int baseWidth = widget.getWidth() > 0 ? widget.getWidth() : 12;
            int baseHeight = widget.getHeight() > 0 ? widget.getHeight() : 12;
            int width = Math.max(10, Math.round(baseWidth * 0.75f));
            int height = Math.max(10, Math.round(baseHeight * 0.75f));
            int x = pos.getX() - baseWidth - 2;
            int y = pos.getY();
            boolean hit = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
            InventoryPatternUploadDebug.info("hit_test",
                    "boundsX={} boundsY={} width={} height={} mouseX={} mouseY={} hit={}",
                    x, y, width, height, mouseX, mouseY, hit);
            return hit;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("hit_test", "failed error={}", t.toString());
            return false;
        }
    }
}
