package net.shadowmage.ancientwarfare.structure.tile;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.shadowmage.ancientwarfare.core.interfaces.ISinger;
import net.shadowmage.ancientwarfare.core.util.BlockTools;
import net.shadowmage.ancientwarfare.core.util.SongPlayData;

import java.util.List;

public class TileSoundBlock extends TileEntity implements ISinger{

    private boolean playing = false;//if currently playing a tune.
    private boolean redstoneInteraction = false;
    private int currentDelay;//the current cooldown delay.  if not playing, this delay will be incremented before attempting to start next song
    private int tuneIndex = -1;//the index of the song being played / to play, incremented/updated on songStart()
    private int playerCheckDelay;//used to not check for players -every- tick. checks every 10 ticks
    private int playerRange = 20;
    private int playTime;//tracking current play time.  when this exceeds length, cooldown delay is triggered
    private SongPlayData tuneData;
    private Block blockCache;

    public TileSoundBlock() {
        tuneData = new SongPlayData();
    }

    @Override
    public void updateEntity() {
        if (world.isRemote) {
            return;
        }
        if (playing) {
            if (playTime-- <= 0) {
                endSong();
            }
        } else {
            if (currentDelay-- <= 0) {
                if (tuneData.getPlayOnPlayerEntry()) {
                    if (playerCheckDelay-- <= 0) {
                        playerCheckDelay = 20;
                        AxisAlignedBB aabb = new AxisAlignedBB(pos, x + 1, y + 1, z + 1).expand(playerRange, playerRange, playerRange);

                        List<EntityPlayer> list = world.getEntitiesWithinAABB(EntityPlayer.class, aabb);
                        if (list != null && !list.isEmpty()) {
                            startSong();
                        }
                    }
                } else if (isRedstoneInteraction()) {
                    if (world.getStrongPower(pos) > 0) {
                        startSong();
                    }
                } else {
                    startSong();
                }
            }
        }
    }

    private void startSong() {
        if (tuneData.getIsRandom()) {
            tuneIndex = 0;
            if (tuneData.size() > 0) {
                tuneIndex = world.rand.nextInt(tuneData.size());
            }
        } else {
            tuneIndex++;
        }
        if (tuneIndex >= tuneData.size()) {
            tuneIndex = 0;
        }
        if (tuneData.size() <= 0) {
            return;
        }
        playing = true;
        playTime = tuneData.get(tuneIndex).play(world, pos);
    }

    private void endSong() {
        playing = false;
        playTime = 0;
        currentDelay = tuneData.getMinDelay();
        int diff = tuneData.getMaxDelay() - currentDelay;
        if (diff > 0) {
            currentDelay += world.rand.nextInt(diff);
        }
    }

    @Override
    public Packet getDescriptionPacket(){
        return new S35PacketUpdateTileEntity(pos, 0, cacheToNBT(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt){
        if(world.isRemote && pkt.func_148853_f() == 0){
            cacheFromNBT(pkt.func_148857_g());
        }
    }

    private void cacheFromNBT(NBTTagCompound tag){
        String id = tag.getString("block");
        if(!id.isEmpty()){
            blockCache = Block.getBlockFromName(id);
        }
    }

    private NBTTagCompound cacheToNBT(NBTTagCompound tag){
        if(blockCache!=null){
            tag.setString("block", Block.blockRegistry.getNameForObject(blockCache));
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tuneData.readFromNBT(tag.getCompoundTag("tuneData"));
        redstoneInteraction = tag.getBoolean("redstone");
        tuneIndex = tag.getInteger("tuneIndex");
        playerRange = tag.getInteger("range");
        cacheFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("tuneData", tuneData.writeToNBT(new NBTTagCompound()));
        tag.setBoolean("redstone", redstoneInteraction);
        tag.setInteger("tuneIndex", tuneIndex);
        tag.setInteger("range", playerRange);
        cacheToNBT(tag);
    }

    public SongPlayData getSongs() {
        return tuneData;
    }

    public boolean isRedstoneInteraction() {
        return redstoneInteraction;
    }

    public void setRedstoneInteraction(boolean redstoneInteraction) {
        this.redstoneInteraction = redstoneInteraction;
    }

    public void setPlayerRange(int value){
        playerRange = value;
    }

    public int getPlayerRange(){
        return playerRange;
    }

    public Block getBlockCache(){
        return blockCache;
    }

    public void setBlockCache(ItemStack itemStack){
        blockCache = Block.getBlockFromItem(itemStack.getItem());
        BlockTools.notifyBlockUpdate(this);
        world.notifyBlockChange(pos, this.blockType);
        markDirty();
    }
}
