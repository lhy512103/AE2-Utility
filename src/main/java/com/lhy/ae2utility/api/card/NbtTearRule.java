package com.lhy.ae2utility.api.card;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
public record NbtTearRule(Set<ResourceLocation> itemIds) {
    public static final NbtTearRule ALL_ITEMS = new NbtTearRule(Set.of());
    public NbtTearRule { if (itemIds == null) throw new IllegalArgumentException("itemIds are required"); itemIds = Set.copyOf(itemIds); }
}
