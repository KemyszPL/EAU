package org.ja13.eau.transparentnode.eggincubator;

import org.ja13.eau.EAU;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.INBTTReady;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.transparent.TransparentNode;
import org.ja13.eau.node.transparent.TransparentNodeDescriptor;
import org.ja13.eau.node.transparent.TransparentNodeElement;
import org.ja13.eau.node.transparent.TransparentNodeElementInventory;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.IProcess;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.mna.component.Resistor;
import org.ja13.eau.sim.nbt.NbtElectricalLoad;
import org.ja13.eau.sim.process.destruct.VoltageStateWatchDog;
import org.ja13.eau.sim.process.destruct.WorldExplosion;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import org.ja13.eau.EAU;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.INBTTReady;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.transparent.TransparentNode;
import org.ja13.eau.node.transparent.TransparentNodeDescriptor;
import org.ja13.eau.node.transparent.TransparentNodeElement;
import org.ja13.eau.node.transparent.TransparentNodeElementInventory;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.IProcess;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.mna.component.Resistor;
import org.ja13.eau.sim.nbt.NbtElectricalLoad;
import org.ja13.eau.sim.process.destruct.VoltageStateWatchDog;
import org.ja13.eau.sim.process.destruct.WorldExplosion;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EggIncubatorElement extends TransparentNodeElement {

    public NbtElectricalLoad powerLoad = new NbtElectricalLoad("powerLoad");
    public Resistor powerResistor = new Resistor(powerLoad, null);
    TransparentNodeElementInventory inventory = new EggIncubatorInventory(1, 64, this);
    EggIncubatorProcess slowProcess = new EggIncubatorProcess();
    EggIncubatorDescriptor descriptor;

    VoltageStateWatchDog voltageWatchdog = new VoltageStateWatchDog();

    double lastVoltagePublish;

    public EggIncubatorElement(TransparentNode transparentNode, TransparentNodeDescriptor descriptor) {
        super(transparentNode, descriptor);
        electricalLoadList.add(powerLoad);
        electricalComponentList.add(powerResistor);
        slowProcessList.add(slowProcess);

        this.descriptor = (EggIncubatorDescriptor) descriptor;

        WorldExplosion exp = new WorldExplosion(this).machineExplosion();
        slowProcessList.add(voltageWatchdog.set(powerLoad).setUNominal(this.descriptor.nominalVoltage).set(exp));
    }

    class EggIncubatorProcess implements IProcess, INBTTReady {

        double energy = 5000;

        public EggIncubatorProcess() {
            resetEnergy();
        }

        void resetEnergy() {
            energy = 10000 + Math.random() * 10000;
        }

        @Override
        public void process(double time) {
            energy -= powerResistor.getP() * time;
            if (inventory.getStackInSlot(EggIncubatorContainer.EggSlotId) != null) {
                descriptor.setState(powerResistor, true);
                if (energy <= 0) {
                    inventory.decrStackSize(EggIncubatorContainer.EggSlotId, 1);
                    EntityChicken chicken = new EntityChicken(node.coordonate.world());
                    chicken.setGrowingAge(-24000);
                    EntityLiving entityliving = chicken;
                    entityliving.setLocationAndAngles(node.coordonate.x + 0.5, node.coordonate.y + 0.5, node.coordonate.z + 0.5, MathHelper.wrapAngleTo180_float(node.coordonate.world().rand.nextFloat() * 360.0F), 0.0F);
                    entityliving.rotationYawHead = entityliving.rotationYaw;
                    entityliving.renderYawOffset = entityliving.rotationYaw;
                    //entityliving.func_110161_a((EntityLivingData)null); 1.6.4
                    node.coordonate.world().spawnEntityInWorld(entityliving);
                    entityliving.playLivingSound();
                    //node.coordonate.world().spawnEntityInWorld());
                    resetEnergy();

                    needPublish();
                }
            } else {
                descriptor.setState(powerResistor, false);
                resetEnergy();
            }
            if (Math.abs(powerLoad.getU() - lastVoltagePublish) / descriptor.nominalVoltage > 0.1) needPublish();
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt, String str) {
            energy = nbt.getDouble(str + "energyCounter");
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt, String str) {
            nbt.setDouble(str + "energyCounter", energy);
        }
    }

    @Override
    public ElectricalLoad getElectricalLoad(Direction side, LRDU lrdu) {
        if (lrdu != LRDU.Down) return null;
        return powerLoad;
    }

    @Override
    public ThermalLoad getThermalLoad(Direction side, LRDU lrdu) {
        return null;
    }

    @Override
    public int getConnectionMask(Direction side, LRDU lrdu) {
        if (lrdu == LRDU.Down) {
            return NodeBase.MASK_ELECTRIC;
        }
        return 0;
    }

    @Override
    public String multiMeterString(Direction side) {
        return Utils.plotUIP(powerLoad.getU(), powerLoad.getCurrent());
    }

    @Override
    public String thermoMeterString(Direction side) {
        return null;
    }

    @Override
    public void initialize() {
        descriptor.applyTo(powerLoad);
        connect();
    }

    public void inventoryChange(IInventory inventory) {
        needPublish();
    }

    @Override
    public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side, float vx, float vy, float vz) {
        return false;
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Override
    public Container newContainer(Direction side, EntityPlayer player) {
        return new EggIncubatorContainer(player, inventory, node);
    }

    public float getLightOpacity() {
        return 1.0f;
    }

    @Override
    public IInventory getInventory() {
        return inventory;
    }

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        try {
            if (inventory.getStackInSlot(EggIncubatorContainer.EggSlotId) == null) stream.writeByte(0);
            else stream.writeByte(inventory.getStackInSlot(EggIncubatorContainer.EggSlotId).stackSize);

            node.lrduCubeMask.getTranslate(front.down()).serialize(stream);

            stream.writeFloat((float) powerLoad.getU());
        } catch (IOException e) {
            e.printStackTrace();
        }
        lastVoltagePublish = powerLoad.getU();
    }

    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(I18N.tr("Has egg"), inventory.getStackInSlot(EggIncubatorContainer.EggSlotId) != null ?
            I18N.tr("Yes") : I18N.tr("No"));
        if (EAU.wailaEasyMode) {
            info.put(I18N.tr("Power consumption"), Utils.plotPower(powerResistor.getP(), ""));
        }
        return info;
    }
}
