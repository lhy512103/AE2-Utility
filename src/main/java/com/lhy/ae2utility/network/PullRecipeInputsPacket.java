package com.lhy.ae2utility.network;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public final class PullRecipeInputsPacket {
    private final boolean maxTransfer;
    private final boolean craftMissing;
    private final List<RequestedIngredient> requestedIngredients;
    public PullRecipeInputsPacket(boolean maxTransfer, boolean craftMissing, List<RequestedIngredient> requestedIngredients) {
        this.maxTransfer = maxTransfer;
        this.craftMissing = craftMissing;
        this.requestedIngredients = requestedIngredients == null ? List.of() : requestedIngredients.stream().map(RequestedIngredient::copy).toList();
    }
    public boolean maxTransfer(){ return maxTransfer; }
    public boolean craftMissing(){ return craftMissing; }
    public List<RequestedIngredient> requestedIngredients(){ return requestedIngredients; }
    public static PullRecipeInputsPacket decode(FriendlyByteBuf buffer){ return new PullRecipeInputsPacket(buffer.readBoolean(),buffer.readBoolean(),RecipeTransferPacketHelper.readRequestedIngredients(buffer)); }
    public static void encode(PullRecipeInputsPacket packet,FriendlyByteBuf buffer){ buffer.writeBoolean(packet.maxTransfer); buffer.writeBoolean(packet.craftMissing); RecipeTransferPacketHelper.writeRequestedIngredients(buffer,packet.requestedIngredients); }
    public static final class RequestedIngredient {
        private final List<ItemStack> alternatives;
        private final int count;
        public RequestedIngredient(List<ItemStack> alternatives,int count){ this.alternatives=alternatives==null?List.of():alternatives.stream().map(ItemStack::copy).toList(); this.count=count; }
        public List<ItemStack> alternatives(){return alternatives;}
        public int count(){return count;}
        public RequestedIngredient copy(){return new RequestedIngredient(alternatives,count);}
    }
}
