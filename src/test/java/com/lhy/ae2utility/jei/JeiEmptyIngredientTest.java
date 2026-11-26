package com.lhy.ae2utility.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;

class JeiEmptyIngredientTest {
    @Test
    void machinePreviewSkipsEmptyIngredients() throws Exception {
        var handler = new MachineRecipeTransferHandler<>(null, null);
        var method = MachineRecipeTransferHandler.class.getDeclaredMethod("toIngredient", IRecipeSlotView.class);
        method.setAccessible(true);

        assertNull(method.invoke(handler, emptySlot()));
    }

    @Test
    void terminalPreviewSkipsEmptyIngredients() throws Exception {
        var method = TerminalJeRecipeTransferPreview.class.getDeclaredMethod("toIngredient", IRecipeSlotView.class);
        method.setAccessible(true);

        assertNull(method.invoke(null, emptySlot()));
    }

    @Test
    void machineRequestPreservesEmptySlotPositions() throws Exception {
        var handler = new MachineRecipeTransferHandler<>(null, null);
        var method = MachineRecipeTransferHandler.class.getDeclaredMethod("collectRequestedIngredients", List.class);
        method.setAccessible(true);

        var ingredients = (List<?>) method.invoke(handler, List.of(emptySlot(), emptySlot()));

        assertEquals(2, ingredients.size());
        for (Object value : ingredients) {
            var ingredient = (RequestedIngredient) value;
            assertTrue(ingredient.alternatives().isEmpty());
            assertEquals(0, ingredient.count());
        }
    }

    private static IRecipeSlotView emptySlot() {
        return (IRecipeSlotView) Proxy.newProxyInstance(IRecipeSlotView.class.getClassLoader(),
                new Class<?>[] {IRecipeSlotView.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getItemStacks" -> Stream.empty();
                    case "getDisplayedItemStack" -> Optional.empty();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
