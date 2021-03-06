package org.ja13.eau.node;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.ja13.eau.misc.Direction;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import java.io.DataInputStream;

public interface INodeEntity {
    String getNodeUuid();

    void serverPublishUnserialize(DataInputStream stream);

    void serverPacketUnserialize(DataInputStream stream);

    @SideOnly(Side.CLIENT)
    GuiScreen newGuiDraw(Direction side, EntityPlayer player);

    Container newContainer(Direction side, EntityPlayer player);
}
