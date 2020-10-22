package org.ja13.eau.transparentnode.turret;

import org.ja13.eau.EAU;
import org.ja13.eau.generic.GenericItemUsingDamageDescriptor;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.item.ConfigCopyToolDescriptor;
import org.ja13.eau.item.EntitySensorFilterDescriptor;
import org.ja13.eau.item.IConfigurable;
import org.ja13.eau.misc.Coordonate;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.AutoAcceptInventoryProxy;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.transparent.TransparentNode;
import org.ja13.eau.node.transparent.TransparentNodeDescriptor;
import org.ja13.eau.node.transparent.TransparentNodeElement;
import org.ja13.eau.node.transparent.TransparentNodeElementInventory;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.nbt.NbtElectricalLoad;
import org.ja13.eau.sim.nbt.NbtResistor;
import org.ja13.eau.sixnode.lampsocket.LightBlockEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.ja13.eau.EAU;
import org.ja13.eau.generic.GenericItemUsingDamageDescriptor;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.item.ConfigCopyToolDescriptor;
import org.ja13.eau.item.EntitySensorFilterDescriptor;
import org.ja13.eau.item.IConfigurable;
import org.ja13.eau.misc.Coordonate;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.AutoAcceptInventoryProxy;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.transparent.TransparentNode;
import org.ja13.eau.node.transparent.TransparentNodeDescriptor;
import org.ja13.eau.node.transparent.TransparentNodeElement;
import org.ja13.eau.node.transparent.TransparentNodeElementInventory;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.nbt.NbtElectricalLoad;
import org.ja13.eau.sim.nbt.NbtResistor;
import org.ja13.eau.sixnode.lampsocket.LightBlockEntity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TurretElement extends TransparentNodeElement implements IConfigurable {

    public static final byte ToggleFilterMeaning = 1;
    public static final byte UnserializeChargePower = 2;

    private final TurretDescriptor descriptor;

    private final TurretMechanicsSimulation simulation;

    public double chargePower;
    public boolean filterIsSpare = false;
    public double energyBuffer = 0;

    final NbtElectricalLoad load = new NbtElectricalLoad("load");
    final NbtResistor powerResistor = new NbtResistor("powerResistor", load, null);

    final AutoAcceptInventoryProxy acceptingInventory =
        (new AutoAcceptInventoryProxy(new TransparentNodeElementInventory(1, 64, this)))
            .acceptAlways(0, 1, new AutoAcceptInventoryProxy.SimpleItemDropper(node), EntitySensorFilterDescriptor.class);

    public TurretElement(TransparentNode transparentNode, TransparentNodeDescriptor descriptor) {
        super(transparentNode, descriptor);
        this.descriptor = (TurretDescriptor) descriptor;
        chargePower = ((TurretDescriptor) descriptor).getProperties().chargePower;
        slowProcessList.add(new TurretSlowProcess(this));
        simulation = new TurretMechanicsSimulation((TurretDescriptor) descriptor);
        slowProcessList.add(simulation);

        EAU.smallInsulationMediumCurrentCopperCable.applyTo(load);
        electricalLoadList.add(load);
        electricalComponentList.add(powerResistor);

    }

    public TurretDescriptor getDescriptor() {
        return descriptor;
    }

    public double getTurretAngle() {
        return simulation.getTurretAngle();
    }

    public void setTurretAngle(float angle) {
        if (simulation.setTurretAngle(angle)) needPublish();
    }

    public double getGunPosition() {
        return simulation.getGunPosition();
    }

    public void setGunPosition(float position) {
        if (simulation.setGunPosition(position)) needPublish();
    }

    public void setGunElevation(float elevation) {
        if (simulation.setGunElevation(elevation)) needPublish();
    }

    public void setSeekMode(boolean seekModeEnabled) {
        if (seekModeEnabled != simulation.inSeekMode()) needPublish();
        simulation.setSeekMode(seekModeEnabled);
    }

    public void shoot() {
        Coordonate lightSourceCoordinate = new Coordonate();
        lightSourceCoordinate.copyFrom(coordonate());
        lightSourceCoordinate.move(front);
        LightBlockEntity.addLight(lightSourceCoordinate, 25, 2);
        if (simulation.shoot()) needPublish();
    }

    public boolean isTargetReached() {
        return simulation.isTargetReached();
    }

    public void setEnabled(boolean armed) {
        if (simulation.setEnabled(armed)) needPublish();
    }

    public boolean isEnabled() {
        return simulation.isEnabled();
    }

    @Override
    public ElectricalLoad getElectricalLoad(Direction side, LRDU lrdu) {
        if (side == front.back() && lrdu == LRDU.Down) return load;
        return null;
    }

    @Override
    public ThermalLoad getThermalLoad(Direction side, LRDU lrdu) {
        return null;
    }

    @Override
    public int getConnectionMask(Direction side, LRDU lrdu) {
        if (side == front.back() && lrdu == LRDU.Down) return NodeBase.MASK_ELECTRIC;
        return 0;
    }

    @Override
    public String multiMeterString(Direction side) {
        return Utils.plotUIP(load.getU(), load.getI());
    }

    @Override
    public String thermoMeterString(Direction side) {
        return null;
    }

    @Override
    public void initialize() {
        connect();
    }

    @Override
    public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side,
                                    float vx, float vy, float vz) {
        return acceptingInventory.take(entityPlayer.getCurrentEquippedItem());
    }

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        try {
            stream.writeDouble(simulation.getTurretTargetAngle());
            stream.writeDouble(simulation.getGunTargetPosition());
            stream.writeDouble(simulation.getGunTargetElevation());
            stream.writeBoolean(simulation.inSeekMode());
            stream.writeBoolean(simulation.isShooting());
            stream.writeBoolean(simulation.isEnabled());
            Utils.serialiseItemStack(stream, acceptingInventory.getInventory().getStackInSlot(TurretContainer.filterId));
            stream.writeBoolean(filterIsSpare);
            stream.writeDouble(chargePower);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setDouble("chargePower", chargePower);
        nbt.setBoolean("filterIsSpare", filterIsSpare);
        nbt.setDouble("energyBuffer", energyBuffer);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        chargePower = nbt.getDouble("chargePower");
        filterIsSpare = nbt.getBoolean("filterIsSpare");
        energyBuffer = nbt.getDouble("energyBuffer");
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Override
    public IInventory getInventory() {
        return acceptingInventory.getInventory();
    }

    @Override
    public Container newContainer(Direction side, EntityPlayer player) {
        return new TurretContainer(player, acceptingInventory.getInventory());
    }

    @Override
    public void inventoryChange(IInventory inventory) {
        super.inventoryChange(inventory);
        needPublish();
    }

    @Override
    public byte networkUnserialize(DataInputStream stream) {
        byte packetType = super.networkUnserialize(stream);
        try {
            switch (packetType) {
                case ToggleFilterMeaning:
                    filterIsSpare = !filterIsSpare;
                    needPublish();
                    break;

                case UnserializeChargePower:
                    chargePower = stream.readFloat();
                    needPublish();
                    break;
            }
        } catch (IOException e) {


            e.printStackTrace();
        }
        return unserializeNulldId;
    }

    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<>();
        info.put(I18N.tr("Charge power"), Utils.plotPower(chargePower, ""));

        ItemStack filterStack = acceptingInventory.getInventory().getStackInSlot(TurretContainer.filterId);
        if (filterStack != null) {
            GenericItemUsingDamageDescriptor gen = EntitySensorFilterDescriptor.getDescriptor(filterStack);
            if (gen instanceof EntitySensorFilterDescriptor) {
                EntitySensorFilterDescriptor filter = (EntitySensorFilterDescriptor) gen;
                String target = I18N.tr("Shoot ");
                if (filterIsSpare) {
                    target += "not ";
                }
                if (filter.entityClass == EntityPlayer.class) {
                    target += I18N.tr("players");
                } else if (filter.entityClass == IMob.class) {
                    target += I18N.tr("monsters");
                } else if (filter.entityClass == EntityAnimal.class) {
                    target += I18N.tr("animals");
                } else {
                    target += I18N.tr("??");
                }
                info.put(I18N.tr("Target"), target);
            }
        } else {
            if (filterIsSpare) {
                info.put(I18N.tr("Target"), I18N.tr("Shoot everything"));
            } else {
                info.put(I18N.tr("Target"), I18N.tr("Shoot nothing"));
            }
        }

        if (EAU.wailaEasyMode) {
            info.put(I18N.tr("Charge level"),
                Utils.plotPercent(energyBuffer / descriptor.getProperties().impulseEnergy, ""));
        }
        return info;
    }

    @Override
    public void readConfigTool(NBTTagCompound compound, EntityPlayer invoker) {
        if(compound.hasKey("chargePower")) {
            chargePower = compound.getDouble("chargePower");
            needPublish();
        }
        if(compound.hasKey("filterInvert")) {
            filterIsSpare = compound.getBoolean("filterInvert");
        }
        if(ConfigCopyToolDescriptor.readGenDescriptor(compound, "filter", getInventory(), 0, invoker))
            needPublish();
    }

    @Override
    public void writeConfigTool(NBTTagCompound compound, EntityPlayer invoker) {
        compound.setDouble("chargePower", chargePower);
        compound.setBoolean("filterInvert", filterIsSpare);
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "filter", getInventory().getStackInSlot(0));
    }
}
