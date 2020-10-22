package org.ja13.eau.node.six;

import org.ja13.eau.EAU;
import org.ja13.eau.cable.CableRenderDescriptor;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.NodeBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.ja13.eau.EAU;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.NodeBlockEntity;

import java.io.DataInputStream;
import java.io.IOException;

public class SixNodeEntity extends NodeBlockEntity {
    //boolean[] syncronizedSideEnable = new boolean[6];


    public SixNodeElementRender[] elementRenderList = new SixNodeElementRender[6];
    short[] elementRenderIdList = new short[6];

    public Block sixNodeCacheBlock = Blocks.air;
    public byte sixNodeCacheBlockMeta = 0;

    public SixNodeEntity() {
        for (int idx = 0; idx < 6; idx++) {
            elementRenderList[idx] = null;
            elementRenderIdList[idx] = 0;
        }
    }

	/* caca
    public boolean onBlockActivated(EntityPlayer entityPlayer, Direction direction) {
		
		//Utils.println("onBlockActivated " + direction);
		
		return getNode().onBlockActivated(entityPlayer, direction);
	}
	*/

    public static final int singleTargetId = 2;

    @Override
    public void serverPublishUnserialize(DataInputStream stream) {

        Block sixNodeCacheBlockOld = sixNodeCacheBlock;

        super.serverPublishUnserialize(stream);

        try {

            sixNodeCacheBlock = Block.getBlockById(stream.readInt());
            sixNodeCacheBlockMeta = stream.readByte();

            int idx;
            for (idx = 0; idx < 6; idx++) {
                short id = stream.readShort();
                if (id == 0) {
                    elementRenderIdList[idx] = (short) 0;
                    elementRenderList[idx] = null;
                } else {
                    if (id != elementRenderIdList[idx]) {
                        boolean failed = false;
                        elementRenderIdList[idx] = id;
                        SixNodeDescriptor descriptor = EAU.sixNodeItem.getDescriptor(id);
                        if(descriptor == null) {
                            Utils.println("ERROR: Server sent bad SixNodeDescriptor id " + id);
                            failed = true;
                        }
                        if(!failed && descriptor.RenderClass == null) {
                            Utils.println("ERROR: Id " + id + " gives descriptor " + descriptor + " with null RenderClass");
                            failed = true;
                        }
                        if(!failed) {
                            try {
                                elementRenderList[idx] = (SixNodeElementRender) descriptor.RenderClass.getConstructor(SixNodeEntity.class, Direction.class, SixNodeDescriptor.class).newInstance(this, Direction.fromInt(idx), descriptor);
                            } catch(Exception e) {
                                Utils.println("ERROR: Initialize SixNodeElementRender for id " + id + " descriptor " + descriptor + " RenderClass " + descriptor.RenderClass + " failed with exception " + e);
                                e.printStackTrace();
                                failed = true;
                            }
                        }
                        if(failed) {
                            Utils.println("ERROR: A previous failure has desynchronized the DataInputStream for this packet. No further information can be processed. If something isn't rendering right now, please post a bug report for this version of Electrical Age.");
                            Utils.println("... " + stream.available() + " bytes remained on the stream, consuming all of them");
                            stream.skip(stream.available());
                            break;
                        }
                    }
                    if(elementRenderList[idx] != null) elementRenderList[idx].publishUnserialize(stream);
                }
            }

        } catch (IOException e) {

            e.printStackTrace();
        } catch (SecurityException e) {

            e.printStackTrace();
        }

        //	worldObj.setLightValue(EnumSkyBlock.Sky, xCoord,yCoord,zCoord,15);
        if (sixNodeCacheBlock != sixNodeCacheBlockOld) {
            Chunk chunk = worldObj.getChunkFromBlockCoords(xCoord, zCoord);
            chunk.generateHeightMap();
            Utils.updateSkylight(chunk);
            chunk.generateSkylightMap();
            Utils.updateAllLightTypes(worldObj, xCoord, yCoord, zCoord);
        }

    }

    @Override
    public void serverPacketUnserialize(DataInputStream stream) {

        super.serverPacketUnserialize(stream);

        try {
            int side = stream.readByte();
            int id = stream.readShort();
            if (elementRenderIdList[side] == id) {
                elementRenderList[side].serverPacketUnserialize(stream);
            }
        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    public boolean getSyncronizedSideEnable(Direction direction) {
        return elementRenderList[direction.getInt()] != null;
    }

    public Container newContainer(Direction side, EntityPlayer player) {
        SixNode n = ((SixNode) getNode());
        if (n == null) return null;
        return n.newContainer(side, player);
    }

    public GuiScreen newGuiDraw(Direction side, EntityPlayer player) {
        return elementRenderList[side.getInt()].newGuiDraw(side, player);
    }

    public CableRenderDescriptor getCableRender(Direction side, LRDU lrdu) {
        side = side.applyLRDU(lrdu);
        if (elementRenderList[side.getInt()] == null)
            return null;

        return elementRenderList[side.getInt()].getCableRender(lrdu);
    }

    public int getCableDry(Direction side, LRDU lrdu) {
        side = side.applyLRDU(lrdu);
        if (elementRenderList[side.getInt()] == null)
            return 0;

        return elementRenderList[side.getInt()].getCableDry(lrdu);
    }

    @Override
    public boolean cameraDrawOptimisation() {
        for (SixNodeElementRender e : elementRenderList) {
            if (e != null && !e.cameraDrawOptimisation())
                return false;
        }
        return true;
    }

    @Override
    public void destructor() {
        for (SixNodeElementRender render : elementRenderList) {
            if (render != null)
                render.destructor();
        }
        super.destructor();
    }

    /*public float getBlockHardness(World world, int x, int y, int z) {

        return 0;
    }*/
    public int getDamageValue(World world, int x, int y, int z) {
        if (world.isRemote) {
            for (int idx = 0; idx < 6; idx++) {
                if (elementRenderList[idx] != null) {
                    return elementRenderIdList[idx];
                }
            }
        }
        return 0;
    }

    public boolean hasVolume(World world, int x, int y, int z) {

        if (worldObj.isRemote) {
            for (SixNodeElementRender e : elementRenderList) {
                if (e != null && e.sixNodeDescriptor.hasVolume())
                    return true;
            }
            return false;
        } else {
            SixNode node = ((SixNode) getNode());
            if (node == null)
                return false;
            return node.hasVolume();
        }
    }

    @Override
    public void tileEntityNeighborSpawn() {

        for (SixNodeElementRender e : elementRenderList) {
            if (e != null)
                e.notifyNeighborSpawn();
        }
    }

    @Override
    public String getNodeUuid() {
        return EAU.sixNodeBlock.getNodeUuid();
    }

    @Override
    public void clientRefresh(float deltaT) {
        for (SixNodeElementRender e : elementRenderList) {
            if (e != null) {
                e.refresh(deltaT);
            }
        }

    }

    @Override
    public int isProvidingWeakPower(Direction side) {
        if (worldObj.isRemote) {
            int max = 0;
            for (SixNodeElementRender r : elementRenderList) {
                if (r == null) continue;
                if (max < r.isProvidingWeakPower(side)) max = r.isProvidingWeakPower(side);
            }
            return max;
        } else {
            if (getNode() == null) return 0;
            return getNode().isProvidingWeakPower(side);
        }
    }
}
// && 
