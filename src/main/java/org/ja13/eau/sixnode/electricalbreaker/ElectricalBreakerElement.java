package org.ja13.eau.sixnode.electricalbreaker;

import org.ja13.eau.EAU;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.misc.VoltageTier;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.six.SixNode;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElement;
import org.ja13.eau.node.six.SixNodeElementInventory;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.mna.component.Resistor;
import org.ja13.eau.sim.nbt.NbtElectricalLoad;
import org.ja13.eau.sixnode.genericcable.GenericCableDescriptor;
import org.ja13.eau.sound.SoundCommand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.ja13.eau.EAU;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.misc.VoltageTier;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.six.SixNode;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElement;
import org.ja13.eau.node.six.SixNodeElementInventory;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.mna.component.Resistor;
import org.ja13.eau.sim.nbt.NbtElectricalLoad;
import org.ja13.eau.sixnode.genericcable.GenericCableDescriptor;
import org.ja13.eau.sound.SoundCommand;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ElectricalBreakerElement extends SixNodeElement {

    public ElectricalBreakerDescriptor descriptor;
    public NbtElectricalLoad aLoad = new NbtElectricalLoad("aLoad");
    public NbtElectricalLoad bLoad = new NbtElectricalLoad("bLoad");
    public Resistor switchResistor = new Resistor(aLoad, bLoad);
    public ElectricalBreakerCutProcess cutProcess = new ElectricalBreakerCutProcess(this);

    SixNodeElementInventory inventory = new SixNodeElementInventory(1, 64, this);

    public float voltageMax = (float) VoltageTier.TTL.getVoltage(), voltageMin = 0;

    boolean switchState = false;
    double currantMax = 0;
    boolean nbtBoot = false;

    public GenericCableDescriptor cableDescriptor = null;

    public static final byte setVoltageMaxId = 1;
    public static final byte setVoltageMinId = 2;
    public static final byte toogleSwitchId = 3;

    public ElectricalBreakerElement(SixNode sixNode, Direction side, SixNodeDescriptor descriptor) {
        super(sixNode, side, descriptor);

        electricalLoadList.add(aLoad);
        electricalLoadList.add(bLoad);
        electricalComponentList.add(switchResistor);
        electricalComponentList.add(new Resistor(bLoad, null).pullDown());
        electricalComponentList.add(new Resistor(aLoad, null).pullDown());

        electricalProcessList.add(cutProcess);

        this.descriptor = (ElectricalBreakerDescriptor) descriptor;
    }

    public SixNodeElementInventory getInventory() {
        return inventory;
    }

    public static boolean canBePlacedOnSide(Direction side, int type) {
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        byte value = nbt.getByte("front");
        front = LRDU.fromInt((value >> 0) & 0x3);
        switchState = nbt.getBoolean("switchState");
        voltageMax = nbt.getFloat("voltageMax");
        voltageMin = nbt.getFloat("voltageMin");
        nbtBoot = true;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("front", (byte) (front.toInt() << 0));
        nbt.setBoolean("switchState", switchState);
        nbt.setFloat("voltageMax", voltageMax);
        nbt.setFloat("voltageMin", voltageMin);
    }

    @Override
    public ElectricalLoad getElectricalLoad(LRDU lrdu, int mask) {
        if (front == lrdu) return aLoad;
        if (front.inverse() == lrdu) return bLoad;
        return null;
    }

    @Override
    public ThermalLoad getThermalLoad(LRDU lrdu, int mask) {
        return null;
    }

    @Override
    public int getConnectionMask(LRDU lrdu) {
        if (inventory.getStackInSlot(ElectricalBreakerContainer.cableSlotId) == null) return 0;
        if (front == lrdu) return NodeBase.maskElectricalAll;
        if (front.inverse() == lrdu) return NodeBase.maskElectricalAll;

        return 0;
    }

    @Override
    public String multiMeterString() {
        return Utils.plotVolt(aLoad.getU(), "Ua:") + Utils.plotVolt(bLoad.getU(), "Ub:") + Utils.plotAmpere(aLoad.getCurrent(), "");
    }

    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(I18N.tr("Contact"), switchState ? I18N.tr("Closed") : I18N.tr("Open"));
        info.put(I18N.tr("Current"), Utils.plotAmpere(aLoad.getCurrent(), ""));
        if (EAU.wailaEasyMode) {
            info.put(I18N.tr("Voltages"), Utils.plotVolt(aLoad.getU(), "") + Utils.plotVolt(bLoad.getU(), ""));
        }
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
            stream.writeBoolean(switchState);
            stream.writeFloat(voltageMax);
            stream.writeFloat(voltageMin);

            Utils.serialiseItemStack(stream, inventory.getStackInSlot(ElectricalBreakerContainer.cableSlotId));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSwitchState(boolean state) {
        if (state == switchState) return;
        play(new SoundCommand("random.click").mulVolume(0.3F, 0.6f).smallRange());
        switchState = state;
        refreshSwitchResistor();
        needPublish();
    }

    public void refreshSwitchResistor() {
        ItemStack cable = inventory.getStackInSlot(ElectricalBreakerContainer.cableSlotId);
        GenericCableDescriptor cableDescriptor = (GenericCableDescriptor) EAU.sixNodeItem.getDescriptor(cable);
        if (cableDescriptor == null || !switchState) {
            switchResistor.ultraImpedance();
        } else {
            cableDescriptor.applyTo(switchResistor);
        }
    }

    public boolean getSwitchState() {
        return switchState;
    }

    @Override
    public void initialize() {
        computeElectricalLoad();
        setSwitchState(switchState);
    }

    @Override
    protected void inventoryChanged() {
        computeElectricalLoad();
        reconnect();
    }

    public void computeElectricalLoad() {
        ItemStack cable = inventory.getStackInSlot(ElectricalBreakerContainer.cableSlotId);

        if (!nbtBoot) setSwitchState(false);
        nbtBoot = false;

        cableDescriptor = (GenericCableDescriptor) EAU.sixNodeItem.getDescriptor(cable);
        if (cableDescriptor == null) {
            aLoad.highImpedance();
            bLoad.highImpedance();
        } else {
            cableDescriptor.applyTo(aLoad);
            cableDescriptor.applyTo(bLoad);
            currantMax = cableDescriptor.electricalMaximalCurrent;
        }
        refreshSwitchResistor();
    }

    @Override
    public void networkUnserialize(DataInputStream stream) {
        super.networkUnserialize(stream);
        try {
            switch (stream.readByte()) {
                case setVoltageMaxId:
                    voltageMax = stream.readFloat();
                    needPublish();
                    break;
                case setVoltageMinId:
                    voltageMin = stream.readFloat();
                    needPublish();
                    break;
                case toogleSwitchId:
                    setSwitchState(!getSwitchState());
                    needPublish();
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Override
    public Container newContainer(Direction side, EntityPlayer player) {
        return new ElectricalBreakerContainer(player, inventory);
    }
}
