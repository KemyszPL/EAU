package org.ja13.eau.sixnode.electricalredstoneinput;

import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.six.SixNode;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElement;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.nbt.NbtElectricalGateOutput;
import org.ja13.eau.sim.nbt.NbtElectricalGateOutputProcess;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.six.SixNode;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElement;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.nbt.NbtElectricalGateOutput;
import org.ja13.eau.sim.nbt.NbtElectricalGateOutputProcess;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ElectricalRedstoneInputElement extends SixNodeElement {

    ElectricalRedstoneInputDescriptor descriptor;

    public NbtElectricalGateOutput outputGate = new NbtElectricalGateOutput("outputGate");
    public NbtElectricalGateOutputProcess outputGateProcess = new NbtElectricalGateOutputProcess("outputGateProcess", outputGate);
    public ElectricalRedstoneInputSlowProcess slowProcess = new ElectricalRedstoneInputSlowProcess(this);

    boolean warm = false;

    public ElectricalRedstoneInputElement(SixNode sixNode, Direction side, SixNodeDescriptor descriptor) {
        super(sixNode, side, descriptor);
        electricalLoadList.add(outputGate);
        electricalComponentList.add(outputGateProcess);
        slowProcessList.add(slowProcess);
        this.descriptor = (ElectricalRedstoneInputDescriptor) descriptor;
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    public static boolean canBePlacedOnSide(Direction side, int type) {
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        byte value = nbt.getByte("front");
        front = LRDU.fromInt((value >> 0) & 0x3);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("front", (byte) (front.toInt() << 0));
    }

    @Override
    public ElectricalLoad getElectricalLoad(LRDU lrdu, int mask) {
        if (front == lrdu.left()) return outputGate;
        return null;
    }

    @Override
    public ThermalLoad getThermalLoad(LRDU lrdu, int mask) {
        return null;
    }

    @Override
    public int getConnectionMask(LRDU lrdu) {
        if (front == lrdu.left()) return NodeBase.MASK_ELECTRIC;
        return 0;
    }

    @Override
    public String multiMeterString() {
        return Utils.plotVolt(outputGate.getU(), "") + Utils.plotAmpere(outputGate.getCurrent(), "");
    }

    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(I18N.tr("Redstone value"), Utils.plotValue(slowProcess.oldSignal));
        info.put(I18N.tr("Output voltage"), Utils.plotVolt(outputGate.getU(), ""));
        return info;
    }

    @Override
    public String thermoMeterString() {
        return "";
    }

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        try {
            stream.writeByte(slowProcess.oldSignal);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setWarm(boolean value) {
        if (warm != value) {
            needPublish();
        }
        warm = value;
    }

    @Override
    public void initialize() {
    }

    @Override
    public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side, float vx, float vy, float vz) {
        if (onBlockActivatedRotate(entityPlayer)) return true;
        ItemStack currentItemStack = entityPlayer.getCurrentEquippedItem();
        if (currentItemStack != null) {
            Item item = currentItemStack.getItem();
            /*if (item== Eln.toolsSetItem) {
				colorCare = colorCare ^ 1;
				entityPlayer.addChatMessage("Wire color care " + colorCare);
				sixNode.reconnect();
			}
			if (item == Eln.brushItem) {
				if (currentItemStack.getItemDamage() < BrushItem.maximalUse) {
					color = currentItemStack.getItemDamage() & 0xF;
					
					currentItemStack.setItemDamage(currentItemStack.getItemDamage() + 16);
					
					sixNode.reconnect();
				} else {
					entityPlayer.addChatMessage("Brush is empty");
				}
			}*/
        }
        return false;
    }
}
