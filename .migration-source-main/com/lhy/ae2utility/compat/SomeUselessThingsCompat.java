package com.lhy.ae2utility.compat;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.EncodePatternPacket;

/**
 * Isolated reflective bridge for Some Useless Things omniversal patterns.
 * Direct class references would fail to load when the optional mod is absent.
 */
public final class SomeUselessThingsCompat {
    private static final String ENTRY_CLASS = "com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog$Entry";
    private static final String IDENTITY_CLASS = "com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity";
    private static final String CATALOG_CLASS = "com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog";
    private static final String RECIPE_CLASS = "com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe";
    private static final String ENCODING_CLASS =
            "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding";
    private static final String HANDLER_CLASS = "com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler";

    private static IRecipeTransferHandlerHelper transferHelper;

    public record OmniversalIdentity(ResourceLocation recipeId, String fingerprint, String sourceId) {
        public boolean isComplete() {
            return recipeId != null && fingerprint != null && !fingerprint.isBlank();
        }
    }

    private SomeUselessThingsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("useless_mod");
    }

    public static void prepare(IRecipeTransferHandlerHelper helper) {
        if (!isLoaded()) {
            return;
        }
        transferHelper = helper;
    }

    public static boolean isOmniversalRecipe(Object recipe) {
        if (!isLoaded() || recipe == null) {
            return false;
        }
        try {
            return Class.forName(ENTRY_CLASS, false, recipe.getClass().getClassLoader()).isInstance(recipe);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static @Nullable OmniversalIdentity identityOf(Object recipe) {
        if (!isOmniversalRecipe(recipe)) {
            return null;
        }
        try {
            Object identity = recipe.getClass().getMethod("identity").invoke(recipe);
            ResourceLocation recipeId = (ResourceLocation) identity.getClass().getMethod("recipeId").invoke(identity);
            String fingerprint = (String) identity.getClass().getMethod("fingerprint").invoke(identity);
            String sourceId = (String) recipe.getClass().getMethod("sourceId").invoke(recipe);
            OmniversalIdentity result = new OmniversalIdentity(recipeId, fingerprint, sourceId == null ? "" : sourceId);
            return result.isComplete() ? result : null;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return null;
        }
    }

    public static EncodePatternPacket attachIdentity(EncodePatternPacket packet, Object recipe) {
        OmniversalIdentity identity = identityOf(recipe);
        if (packet == null || identity == null) {
            return packet;
        }
        return packet.withOmniversalIdentity(identity.recipeId(), identity.fingerprint(), identity.sourceId());
    }

    public static ItemStack encodePattern(ItemStack processingPattern, EncodePatternPacket payload, Level level) {
        if (!isLoaded() || level == null || payload == null) {
            return processingPattern;
        }
        try {
            Object entry = resolveEntry(level, payload);
            if (entry == null) {
                entry = findEntryFromPattern(processingPattern, level);
            }
            if (entry == null) {
                return processingPattern;
            }
            ClassLoader loader = entry.getClass().getClassLoader();
            Class<?> entryClass = Class.forName(ENTRY_CLASS, false, loader);
            Class<?> recipeClass = Class.forName(RECIPE_CLASS, false, loader);
            Class<?> encodingClass = Class.forName(ENCODING_CLASS, false, loader);
            Object recipe = entryClass.getMethod("recipe").invoke(entry);
            ItemStack officialProcessing = (ItemStack) encodingClass
                    .getMethod("createProcessingPattern", recipeClass)
                    .invoke(null, recipe);
            ItemStack source = officialProcessing != null && !officialProcessing.isEmpty()
                    ? officialProcessing
                    : processingPattern;
            if (source == null || source.isEmpty()) {
                return processingPattern;
            }
            ItemStack encoded = (ItemStack) encodingClass
                    .getMethod("encode", ItemStack.class, entryClass, Level.class)
                    .invoke(null, source, entry, level);
            return encoded == null || encoded.isEmpty() ? processingPattern : encoded;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException t) {
            Ae2UtilityMod.LOGGER.debug("Omniversal pattern encoding failed", t);
            return processingPattern;
        }
    }

    public static boolean transfer(IRecipeLayoutDrawable<?> layout, boolean doTransfer) {
        if (transferHelper == null || !isOmniversalRecipe(layout.getRecipe())) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof PatternEncodingTermMenu menu)) {
            return false;
        }
        IRecipeSlotsView slots = layout.getRecipeSlotsView();
        try {
            ClassLoader loader = layout.getRecipe().getClass().getClassLoader();
            Class<?> entryClass = Class.forName(ENTRY_CLASS, false, loader);
            Class<?> handlerClass = Class.forName(HANDLER_CLASS, false, loader);
            handlerClass.getMethod("transferOmniversalRecipe", PatternEncodingTermMenu.class,
                    entryClass, IRecipeSlotsView.class, Player.class, boolean.class, boolean.class,
                    IRecipeTransferHandlerHelper.class)
                    .invoke(null, menu, layout.getRecipe(), slots, player, doTransfer, false, transferHelper);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static @Nullable Object resolveEntry(Level level, EncodePatternPacket payload) throws ReflectiveOperationException {
        if (!payload.hasOmniversalIdentity()) {
            return null;
        }
        ClassLoader loader = level.getClass().getClassLoader();
        Class<?> identityClass = Class.forName(IDENTITY_CLASS, false, loader);
        Class<?> catalogClass = Class.forName(CATALOG_CLASS, false, loader);
        Object identity = identityClass.getConstructor(ResourceLocation.class, String.class)
                .newInstance(payload.omniversalRecipeId(), payload.omniversalFingerprint());
        Optional<?> resolved;
        if (!payload.omniversalSourceId().isBlank()) {
            resolved = (Optional<?>) catalogClass
                    .getMethod("resolve", Level.class, String.class, identityClass)
                    .invoke(null, level, payload.omniversalSourceId(), identity);
        } else {
            resolved = (Optional<?>) catalogClass
                    .getMethod("resolve", Level.class, identityClass)
                    .invoke(null, level, identity);
        }
        return resolved == null ? null : resolved.orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Object findEntryFromPattern(ItemStack processingPattern, Level level)
            throws ReflectiveOperationException {
        if (processingPattern == null || processingPattern.isEmpty()) {
            return null;
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(processingPattern, level);
        if (details == null) {
            return null;
        }
        Class<?> catalogClass = Class.forName(CATALOG_CLASS, false, level.getClass().getClassLoader());
        List<?> candidates = (List<?>) catalogClass
                .getMethod("findPatternCandidates", Level.class, IPatternDetails.class)
                .invoke(null, level, details);
        if (candidates != null && candidates.size() == 1) {
            return candidates.getFirst();
        }
        Optional<?> unique = (Optional<?>) catalogClass
                .getMethod("findUniqueMoldPatternCandidate", Level.class, IPatternDetails.class)
                .invoke(null, level, details);
        return unique == null ? null : unique.orElse(null);
    }
}
