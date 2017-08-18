package net.shadowmage.ancientwarfare.automation.tile.worksite;

import cofh.api.energy.IEnergyHandler;
import cpw.mods.fml.common.Optional;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.shadowmage.ancientwarfare.automation.config.AWAutomationStatics;
import net.shadowmage.ancientwarfare.automation.item.ItemWorksiteUpgrade;
import net.shadowmage.ancientwarfare.core.AncientWarfareCore;
import net.shadowmage.ancientwarfare.core.block.BlockRotationHandler.IRotatableTile;
import net.shadowmage.ancientwarfare.core.config.AWCoreStatics;
import net.shadowmage.ancientwarfare.core.interfaces.IInteractableTile;
import net.shadowmage.ancientwarfare.core.interfaces.IOwnable;
import net.shadowmage.ancientwarfare.core.interfaces.ITorque.TorqueCell;
import net.shadowmage.ancientwarfare.core.interfaces.IWorkSite;
import net.shadowmage.ancientwarfare.core.interfaces.IWorker;
import net.shadowmage.ancientwarfare.core.upgrade.WorksiteUpgrade;
import net.shadowmage.ancientwarfare.core.util.InventoryTools;

import java.util.EnumSet;
import java.util.UUID;

@Optional.Interface(iface = "cofh.api.energy.IEnergyHandler", modid = "CoFHCore", striprefs = true)
public abstract class TileWorksiteBase extends TileEntity implements IWorkSite, IInteractableTile, IOwnable, IRotatableTile, IEnergyHandler {

    private String owningPlayer = "";
    private UUID ownerId;
    private EntityPlayer owner;

    private double efficiencyBonusFactor = 0.f;

    private EnumSet<WorksiteUpgrade> upgrades = EnumSet.noneOf(WorksiteUpgrade.class);

    private EnumFacing orientation = EnumFacing.NORTH;

    private final TorqueCell torqueCell;

    private int workRetryDelay = 20;

    public TileWorksiteBase() {
        torqueCell = new TorqueCell(32, 0, AWCoreStatics.energyPerWorkUnit * 3, 1);
    }

    //*************************************** COFH RF METHODS ***************************************//
    @Optional.Method(modid = "CoFHCore")
    @Override
    public final int getEnergyStored(EnumFacing from) {
        return (int) (getTorqueStored(from) * AWAutomationStatics.torqueToRf);
    }

    @Optional.Method(modid = "CoFHCore")
    @Override
    public final int getMaxEnergyStored(EnumFacing from) {
        return (int) (getMaxTorque(from) * AWAutomationStatics.torqueToRf);
    }

    @Optional.Method(modid = "CoFHCore")
    @Override
    public final boolean canConnectEnergy(EnumFacing from) {
        return canOutputTorque(from) || canInputTorque(from);
    }

    @Optional.Method(modid = "CoFHCore")
    @Override
    public final int extractEnergy(EnumFacing from, int maxExtract, boolean simulate) {
        return 0;
    }

    @Optional.Method(modid = "CoFHCore")
    @Override
    public final int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate) {
        if (!canInputTorque(from)) {
            return 0;
        }
        if (simulate) {
            return Math.min(maxReceive, (int) (AWAutomationStatics.torqueToRf * getMaxTorqueInput(from)));
        }
        return (int) (AWAutomationStatics.torqueToRf * addTorque(from, (double) maxReceive * AWAutomationStatics.rfToTorque));
    }
//*************************************** UPGRADE HANDLING METHODS ***************************************//

    @Override
    public final EnumSet<WorksiteUpgrade> getUpgrades() {
        return upgrades;
    }

    @Override
    public EnumSet<WorksiteUpgrade> getValidUpgrades() {
        return EnumSet.of(
                WorksiteUpgrade.ENCHANTED_TOOLS_1,
                WorksiteUpgrade.ENCHANTED_TOOLS_2,
                WorksiteUpgrade.TOOL_QUALITY_1,
                WorksiteUpgrade.TOOL_QUALITY_2,
                WorksiteUpgrade.TOOL_QUALITY_3
        );
    }

    @Override
    public void onBlockBroken() {
        for (WorksiteUpgrade ug : this.upgrades) {
            InventoryTools.dropItemInWorld(worldObj, ItemWorksiteUpgrade.getStack(ug), xCoord, yCoord, zCoord);
        }
        efficiencyBonusFactor = 0;
        upgrades.clear();
    }

    @Override
    public void addUpgrade(WorksiteUpgrade upgrade) {
        upgrades.add(upgrade);
        updateEfficiency();
        worldObj.notifyBlockUpdate(xCoord, yCoord, zCoord);
        markDirty();
    }

    @Override
    public void removeUpgrade(WorksiteUpgrade upgrade) {
        upgrades.remove(upgrade);
        updateEfficiency();
        worldObj.notifyBlockUpdate(xCoord, yCoord, zCoord);
        markDirty();
    }

    public int getFortune() {
        return getUpgrades().contains(WorksiteUpgrade.ENCHANTED_TOOLS_2) ? 2 : getUpgrades().contains(WorksiteUpgrade.ENCHANTED_TOOLS_1) ? 1 : 0;
    }

//*************************************** TILE UPDATE METHODS ***************************************//

    protected abstract boolean processWork();

    protected abstract boolean hasWorksiteWork();

    protected abstract void updateWorksite();

    @Override
    public final boolean canUpdate() {
        return true;
    }

    @Override
    public final void updateEntity() {
        if (!hasWorldObj() || worldObj.isRemote) {
            return;
        }
        if (workRetryDelay > 0) {
            workRetryDelay--;
        } else {
            worldObj.theProfiler.startSection("Check For Work");
            double ePerUse = IWorkSite.WorksiteImplementation.getEnergyPerActivation(efficiencyBonusFactor);
            boolean hasWork = getTorqueStored(EnumFacing.UNKNOWN) >= ePerUse && hasWorksiteWork();
            if (hasWork) {
                worldObj.theProfiler.endStartSection("Process Work");
                if (processWork()) {
                    torqueCell.setEnergy(torqueCell.getEnergy() - ePerUse);
                    markDirty();
                } else {
                    workRetryDelay = 20;
                }
            }
            worldObj.theProfiler.endSection();
        }
        worldObj.theProfiler.startSection("WorksiteBaseUpdate");
        updateWorksite();
        worldObj.theProfiler.endSection();
    }

    protected final void updateEfficiency() {
        efficiencyBonusFactor = IWorkSite.WorksiteImplementation.getEfficiencyFactor(upgrades);
    }

//*************************************** TILE INTERACTION METHODS ***************************************//

    @Override
    public final Team getTeam() {
        if (owningPlayer != null) {
            return worldObj.getScoreboard().getPlayersTeam(owningPlayer);
        }
        return null;
    }

    @Override
    public final String getOwnerName() {
        return owningPlayer;
    }
    
    @Override
    public final UUID getOwnerUuid() {
        return ownerId;
    }

    public final EntityPlayer getOwnerAsPlayer() {
        if(!isOwnerReal()) {
            owner = AncientWarfareCore.proxy.getFakePlayer(getWorldObj(), owningPlayer, ownerId);
        }
        return owner;
    }

    private boolean isOwnerReal(){
        return owner!=null && owner.isEntityAlive() && !owner.isEntityInvulnerable();
    }

    @Override
    public final boolean isOwner(EntityPlayer player){
        if(player == null || player.getGameProfile() == null)
            return false;
        if(isOwnerReal())
            return player.getGameProfile().equals(owner.getGameProfile());
        if(ownerId!=null)
            return player.getUniqueID().equals(ownerId);
        return player.getCommandSenderName().equals(owningPlayer);
    }

    @Override
    public final void setOwner(EntityPlayer player) {
        if (player == null) {
            this.owningPlayer = "";
            this.owner = null;
            this.ownerId = null;
        }else{
            this.owner = player;
            this.owningPlayer = player.getCommandSenderName();
            this.ownerId = player.getUniqueID();
        }
    }
    
    @Override
    public final void setOwner(String ownerName, UUID ownerUuid) {
        owningPlayer = ownerName;
        ownerId = ownerUuid;
        owner = AncientWarfareCore.proxy.getFakePlayer(getWorldObj(), ownerName, ownerUuid);
    }

//*************************************** TORQUE INTERACTION METHODS ***************************************//

    @Override
    public final float getClientOutputRotation(EnumFacing from, float delta) {
        return 0;
    }

    @Override
    public final boolean useOutputRotation(EnumFacing from) {
        return false;
    }

    @Override
    public final double getMaxTorqueOutput(EnumFacing from) {
        return 0;
    }

    @Override
    public final boolean canOutputTorque(EnumFacing towards) {
        return false;
    }

    @Override
    public final double drainTorque(EnumFacing from, double energy) {
        return 0;
    }

    @Override
    public final void addEnergyFromWorker(IWorker worker) {
        addTorque(EnumFacing.UNKNOWN, AWCoreStatics.energyPerWorkUnit * worker.getWorkEffectiveness(getWorkType()) * AWAutomationStatics.hand_cranked_generator_output);
    }

    @Override
    public final void addEnergyFromPlayer(EntityPlayer player) {
        addTorque(EnumFacing.UNKNOWN, AWCoreStatics.energyPerWorkUnit * AWAutomationStatics.hand_cranked_generator_output);
    }

    @Override
    public final double addTorque(EnumFacing from, double energy) {
        return torqueCell.addEnergy(energy);
    }

    @Override
    public final double getMaxTorque(EnumFacing from) {
        return torqueCell.getMaxEnergy();
    }

    @Override
    public final double getTorqueStored(EnumFacing from) {
        return torqueCell.getEnergy();
    }

    @Override
    public final double getMaxTorqueInput(EnumFacing from) {
        return torqueCell.getMaxTickInput();
    }

    @Override
    public final boolean canInputTorque(EnumFacing from) {
        return true;
    }

//*************************************** MISC METHODS ***************************************//
    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }

    @Override
    public String toString() {
        return "Worksite Base[" + torqueCell.getEnergy() + "]";
    }

    @Override
    public boolean hasWork() {
        return torqueCell.getEnergy() < torqueCell.getMaxEnergy() && worldObj.getBlockPowerInput(xCoord, yCoord, zCoord) == 0;
    }

    @Override
    public final EnumFacing getPrimaryFacing() {
        return orientation;
    }

    @Override
    public final void setPrimaryFacing(EnumFacing face) {
        orientation = face;
        this.worldObj.notifyBlockUpdate(xCoord, yCoord, zCoord);
        markDirty();//notify neighbors of tile change
    }

//*************************************** NBT AND PACKET DATA METHODS ***************************************//

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setDouble("storedEnergy", torqueCell.getEnergy());
        if (owningPlayer != null) {
            tag.setString("owner", owningPlayer);
            if(ownerId == null && hasWorldObj()){
                getOwnerAsPlayer();
                if(isOwnerReal()){
                    ownerId = owner.getUniqueID();
                }
            }
        }
        if(ownerId!=null){
            tag.setString("ownerId", ownerId.toString());
        }
        if (!getUpgrades().isEmpty()) {
            int[] ug = new int[getUpgrades().size()];
            int i = 0;
            for (WorksiteUpgrade u : getUpgrades()) {
                ug[i] = u.ordinal();
                i++;
            }
            tag.setIntArray("upgrades", ug);
        }
        tag.setInteger("orientation", orientation.ordinal());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        torqueCell.setEnergy(tag.getDouble("storedEnergy"));
        if (tag.hasKey("owner")) {
            owningPlayer = tag.getString("owner");
        }
        if(tag.hasKey("ownerId")){
            ownerId = UUID.fromString(tag.getString("ownerId"));
        }
        if (tag.hasKey("upgrades")) {
            NBTBase upgradeTag = tag.getTag("upgrades");
            if (upgradeTag instanceof NBTTagIntArray) {
                int[] ug = tag.getIntArray("upgrades");
                for (int anUg : ug) {
                    upgrades.add(WorksiteUpgrade.values()[anUg]);
                }
            } else if (upgradeTag instanceof NBTTagList)//template parser reads int-arrays as a tag list for some reason
            {
                NBTTagList list = (NBTTagList) upgradeTag;
                for (int i = 0; i < list.tagCount(); i++) {
                    String st = list.getStringTagAt(i);
                    try {
                        int ug = Integer.parseInt(st);
                        upgrades.add(WorksiteUpgrade.values()[ug]);
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }

        if (tag.hasKey("orientation")) {
            orientation = EnumFacing.values()[tag.getInteger("orientation")];
        }
        updateEfficiency();
    }

    protected NBTTagCompound getDescriptionPacketTag(NBTTagCompound tag) {
        int[] ugs = new int[upgrades.size()];
        int i = 0;
        for (WorksiteUpgrade ug : upgrades) {
            ugs[i] = ug.ordinal();
            i++;
        }
        tag.setIntArray("upgrades", ugs);
        tag.setInteger("orientation", orientation.ordinal());
        return tag;
    }

    @Override
    public final Packet getDescriptionPacket() {
        NBTTagCompound tag = getDescriptionPacketTag(new NBTTagCompound());
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        upgrades.clear();
        if (pkt.func_148857_g().hasKey("upgrades")) {
            int[] ugs = pkt.func_148857_g().getIntArray("upgrades");
            for (int ug : ugs) {
                upgrades.add(WorksiteUpgrade.values()[ug]);
            }
        }
        updateEfficiency();
        orientation = EnumFacing.values()[pkt.func_148857_g().getInteger("orientation")];
        markDirty();
    }

}
