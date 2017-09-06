package net.shadowmage.ancientwarfare.npc.orders;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.INBTSerializable;
import net.shadowmage.ancientwarfare.npc.block.BlockTownHall;
import net.shadowmage.ancientwarfare.npc.config.AWNPCStatics;
import net.shadowmage.ancientwarfare.npc.item.ItemUpkeepOrder;

public class UpkeepOrder implements INBTSerializable<NBTTagCompound> {

    BlockPos upkeepPosition;
    int upkeepDimension;
    int blockSide;
    int upkeepAmount = 6000;

    public UpkeepOrder() {

    }

    public void changeBlockSide() {
        blockSide++;
        if (blockSide > 5) {
            blockSide = 0;
        }
    }

    public void removeUpkeepPoint() {
        upkeepPosition = null;
        blockSide = 0;
        upkeepDimension = 0;
        upkeepAmount = 6000;
    }

    public void setUpkeepAmount(int amt) {
        this.upkeepAmount = amt;
    }

    public void setBlockSide(int side) {
        this.blockSide = side;
    }

    public int getUpkeepBlockSide() {
        return blockSide;
    }

    public int getUpkeepDimension() {
        return upkeepDimension;
    }

    public BlockPos getUpkeepPosition() {
        return upkeepPosition;
    }

    public Block getBlock() {
        return upkeepPosition == null ?  null : upkeepPosition.get(upkeepDimension);
    }

    public final int getUpkeepAmount() {
        return upkeepAmount;
    }

    public boolean addUpkeepPosition(World world, BlockPos pos) {
        if(pos != null && world.getTileEntity(pos) instanceof IInventory) {
            if (!AWNPCStatics.npcAllowUpkeepAnyInventory && (!(world.getBlock(pos.x, pos.y, pos.z) instanceof BlockTownHall)))
                return false;
            upkeepPosition = pos;
            upkeepDimension = world.provider.getDimension();
            blockSide = 0;
            upkeepAmount = 6000;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Upkeep Orders[" + upkeepPosition + "]";
    }

    public static UpkeepOrder getUpkeepOrder(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemUpkeepOrder) {
            UpkeepOrder order = new UpkeepOrder();
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("orders")) {
                order.readFromNBT(stack.getTagCompound().getCompoundTag("orders"));
            }
            return order;
        }
        return null;
    }

    public void write(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemUpkeepOrder) {
            stack.setTagInfo("orders", writeToNBT(new NBTTagCompound()));
        }
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (upkeepPosition != null) {
            tag.setTag("upkeepPosition", upkeepPosition.writeToNBT(new NBTTagCompound()));
            tag.setInteger("dim", upkeepDimension);
            tag.setInteger("side", blockSide);
            tag.setInteger("upkeepAmount", upkeepAmount);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(NBTTagCompound tag) {
        if (tag.hasKey("upkeepPosition")) {
            upkeepPosition = BlockPos.fromLong(tag.getLong("upkeepPosition"));
            upkeepDimension = tag.getInteger("dim");
            blockSide = tag.getInteger("side");
            upkeepAmount = tag.getInteger("upkeepAmount");
        }
    }
}
