package org.ja13.eau.sixnode.lampsocket;

import org.ja13.eau.EAU;
import org.ja13.eau.generic.GenericItemUsingDamageDescriptor;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.item.*;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.AutoAcceptInventoryProxy;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.six.SixNode;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElement;
import org.ja13.eau.node.six.SixNodeElementInventory;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.MonsterPopFreeProcess;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.mna.component.Resistor;
import org.ja13.eau.sim.nbt.NbtElectricalLoad;
import org.ja13.eau.sixnode.genericcable.GenericCableDescriptor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.ja13.eau.EAU;
import org.ja13.eau.generic.GenericItemUsingDamageDescriptor;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.item.BrushDescriptor;
import org.ja13.eau.item.ConfigCopyToolDescriptor;
import org.ja13.eau.item.IConfigurable;
import org.ja13.eau.item.LampDescriptor;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.node.AutoAcceptInventoryProxy;
import org.ja13.eau.node.NodeBase;
import org.ja13.eau.node.six.SixNode;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElement;
import org.ja13.eau.node.six.SixNodeElementInventory;
import org.ja13.eau.sim.ElectricalLoad;
import org.ja13.eau.sim.MonsterPopFreeProcess;
import org.ja13.eau.sim.ThermalLoad;
import org.ja13.eau.sim.mna.component.Resistor;
import org.ja13.eau.sim.nbt.NbtElectricalLoad;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LampSocketElement extends SixNodeElement implements IConfigurable {

    LampSocketDescriptor socketDescriptor = null;

    public MonsterPopFreeProcess monsterPopFreeProcess = new MonsterPopFreeProcess(sixNode.coordonate, EAU.killMonstersAroundLampsRange);
    public NbtElectricalLoad positiveLoad = new NbtElectricalLoad("positiveLoad");

    public LampSocketProcess lampProcess = new LampSocketProcess(this);
    public Resistor lampResistor = new Resistor(positiveLoad, null);

    boolean poweredByLampSupply = true;
    boolean grounded = true;

    private final AutoAcceptInventoryProxy acceptingInventory =
        (new AutoAcceptInventoryProxy(new SixNodeElementInventory(2, 64, this)))
            .acceptIfEmpty(0, LampDescriptor.class)
            .acceptIfEmpty(1, GenericCableDescriptor.class);

    LampDescriptor lampDescriptor = null;
    public String channel = lastSocketName;

    public static String lastSocketName = "Default channel";

    static final int setGroundedId = 1;
    static final int setAlphaZId = 2;
    static final int tooglePowerSupplyType = 3, setChannel = 4;

    boolean isConnectedToLampSupply = false;

    public int paintColor = 15;

    public LampSocketElement(SixNode sixNode, Direction side, SixNodeDescriptor descriptor) {
        super(sixNode, side, descriptor);
        this.socketDescriptor = (LampSocketDescriptor) descriptor;

        lampProcess.alphaZ = this.socketDescriptor.alphaZBoot;
        slowProcessList.add(lampProcess);
        slowProcessList.add(monsterPopFreeProcess);
    }


    @Override
    public IInventory getInventory() {
        if (acceptingInventory != null)
            return acceptingInventory.getInventory();
        else
            return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        byte value = nbt.getByte("front");
        front = LRDU.fromInt((value >> 0) & 0x3);
        grounded = (value & 4) != 0;

        setPoweredByLampSupply(nbt.getBoolean("poweredByLampSupply"));
        channel = nbt.getString("channel");

        byte b = nbt.getByte("color");
        if (socketDescriptor.paintable)
            paintColor = b & 0xF;
        else {
            //For avoid existing lamps just set paintable to be drawn black (0) by default.
            //Of course, maps need to be loaded with this code before set an already existing lamp paintable.
            paintColor = 0x0F;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setByte("front", (byte) ((front.toInt() << 0) + (grounded ? 4 : 0)));
        nbt.setBoolean("poweredByLampSupply", poweredByLampSupply);
        nbt.setString("channel", channel);
        nbt.setByte("color", (byte) (paintColor));
    }

    public void networkUnserialize(DataInputStream stream) {
        try {
            switch (stream.readByte()) {
                case setGroundedId:
                    grounded = stream.readByte() != 0;
                    computeElectricalLoad();
                    reconnect();
                    break;
                case setAlphaZId:
                    lampProcess.alphaZ = stream.readFloat();
                    needPublish();
                    break;
                case tooglePowerSupplyType:
                    setPoweredByLampSupply(!poweredByLampSupply);
                    reconnect();
                    break;
                case setChannel:
                    channel = stream.readUTF();
                    lastSocketName = channel;
                    needPublish();
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setPoweredByLampSupply(boolean b) {
        poweredByLampSupply = b;
    }

    @Override
    public void disconnectJob() {
        super.disconnectJob();

        electricalLoadList.remove(positiveLoad);
        electricalComponentList.remove(lampResistor);
        positiveLoad.state = 0;
    }

    @Override
    public void connectJob() {
        if (!poweredByLampSupply) {
            electricalLoadList.add(positiveLoad);
            electricalComponentList.add(lampResistor);
        }
        super.connectJob();
    }

    @Override
    protected void inventoryChanged() {
        computeElectricalLoad();
        reconnect();
    }

    @Override
    public boolean hasGui() {
        return true;
    }

    @Override
    public Container newContainer(Direction side, EntityPlayer player) {
        return new LampSocketContainer(player, acceptingInventory.getInventory(), socketDescriptor);
    }

    public static boolean canBePlacedOnSide(Direction side, int type) {
        return true;
    }

    @Override
    public ElectricalLoad getElectricalLoad(LRDU lrdu, int mask) {
        if (acceptingInventory.getInventory().getStackInSlot(LampSocketContainer.cableSlotId) == null) return null;
        if (poweredByLampSupply) return null;

        if (grounded) return positiveLoad;
        return null;
    }

    @Override
    public ThermalLoad getThermalLoad(LRDU lrdu, int mask) {
        return null;
    }

    @Override
    public int getConnectionMask(LRDU lrdu) {
        if (acceptingInventory.getInventory().getStackInSlot(LampSocketContainer.cableSlotId) == null) return 0;
        if (poweredByLampSupply) return 0;
        if (grounded) return NodeBase.MASK_ELECTRIC;

        if (front == lrdu) return NodeBase.MASK_ELECTRIC;
        if (front == lrdu.inverse()) return NodeBase.MASK_ELECTRIC;

        return 0;
    }

    @Override
    public String multiMeterString() {
        return Utils.plotVolt(positiveLoad.getU(), "") + Utils.plotAmpere(lampResistor.getCurrent(), "");
    }

    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(I18N.tr("Power consumption"), Utils.plotPower(lampResistor.getI() * lampResistor.getU(), ""));
        if (lampDescriptor != null) {
            info.put(I18N.tr("Bulb"), lampDescriptor.name);
        } else {
            info.put(I18N.tr("Bulb"), I18N.tr("None"));
        }
        if (EAU.wailaEasyMode) {
            if (poweredByLampSupply) {
                info.put(I18N.tr("Channel"), channel);
            }
            info.put(I18N.tr("Voltage"), Utils.plotVolt(positiveLoad.getU(), ""));
            ItemStack lampStack = acceptingInventory.getInventory().getStackInSlot(0);
            if (lampStack != null && lampDescriptor != null) {
                info.put(I18N.tr("Life Left: "), Utils.plotValue(lampDescriptor.getLifeInTag(lampStack)) + " Hours");
            }

        }
        return info;
    }

    @Override
    public String thermoMeterString() {
        return null;
    }

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        try {
            stream.writeByte((grounded ? (1 << 6) : 0));
            Utils.serialiseItemStack(stream, acceptingInventory.getInventory().getStackInSlot(LampSocketContainer.lampSlotId));
            stream.writeFloat((float) lampProcess.alphaZ);
            Utils.serialiseItemStack(stream, acceptingInventory.getInventory().getStackInSlot(LampSocketContainer.cableSlotId));
            stream.writeBoolean(poweredByLampSupply);
            stream.writeUTF(channel);
            stream.writeBoolean(isConnectedToLampSupply);
            stream.writeByte(lampProcess.light);
            stream.writeByte(paintColor);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize() {
        computeElectricalLoad();
    }

    public void computeElectricalLoad() {
        ItemStack lamp = acceptingInventory.getInventory().getStackInSlot(LampSocketContainer.lampSlotId);
        ItemStack cable = acceptingInventory.getInventory().getStackInSlot(LampSocketContainer.cableSlotId);

        GenericCableDescriptor cableDescriptor = (GenericCableDescriptor) EAU.sixNodeItem.getDescriptor(cable);

        if (cableDescriptor == null) {
            positiveLoad.highImpedance();
            //negativeLoad.highImpedance();
        } else {
            cableDescriptor.applyTo(positiveLoad);
            //cableDescriptor.applyTo(negativeLoad, grounded,5);
        }

        lampDescriptor = (LampDescriptor) Utils.getItemObject(lamp);

        if (lampDescriptor == null) {
            lampResistor.setR(Double.POSITIVE_INFINITY);
        } else {
            lampDescriptor.applyTo(lampResistor);
        }
    }

    @Override
    public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side, float vx, float vy, float vz) {
        if (Utils.isPlayerUsingWrench(entityPlayer)) {
            front = front.getNextClockwise();
            if (socketDescriptor.rotateOnlyBy180Deg)
                front = front.getNextClockwise();
            reconnect();
            return true;
        }

        ItemStack currentItemStack = entityPlayer.getCurrentEquippedItem();
        if (currentItemStack != null) {
            GenericItemUsingDamageDescriptor itemDescriptor = GenericItemUsingDamageDescriptor.getDescriptor(currentItemStack);
            if (itemDescriptor != null) {
                if (itemDescriptor instanceof BrushDescriptor) {
                    BrushDescriptor brush = (BrushDescriptor) itemDescriptor;
                    int brushColor = brush.getColor(currentItemStack);
                    if (brushColor != paintColor && brush.use(currentItemStack, entityPlayer)) {
                        paintColor = brushColor;
                        needPublish(); //Sync
                    }
                    return true;
                }
            }
        }

        return acceptingInventory.take(entityPlayer.getCurrentEquippedItem(), this, true, false);
    }

    public int getLightValue() {
        return lampProcess.getBlockLight();
    }

    @Override
    public void destroy(EntityPlayerMP entityPlayer) {
        super.destroy(entityPlayer);
    }

    void setIsConnectedToLampSupply(boolean value) {
        if (isConnectedToLampSupply != value) {
            isConnectedToLampSupply = value;
            needPublish();
        }
    }

    @Override
    public void readConfigTool(NBTTagCompound compound, EntityPlayer invoker) {
        if(compound.hasKey("powerChannels")) {
            String newChannel = compound.getTagList("powerChannels", 8).getStringTagAt(0);
            if(newChannel != null && newChannel != "") {
                channel = newChannel;
                needPublish();
            }
        }
        if(ConfigCopyToolDescriptor.readGenDescriptor(compound, "lamp", getInventory(), 0, invoker))
            needPublish();
        if(ConfigCopyToolDescriptor.readCableType(compound, getInventory(), 1, invoker))
            needPublish();
    }

    @Override
    public void writeConfigTool(NBTTagCompound compound, EntityPlayer invoker) {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString(channel));
        compound.setTag("powerChannels", list);
        IInventory inv = getInventory();
        ItemStack lampStack = inv.getStackInSlot(0);
        ConfigCopyToolDescriptor.writeGenDescriptor(compound, "lamp", lampStack);
        ItemStack cableStack = inv.getStackInSlot(1);
        ConfigCopyToolDescriptor.writeCableType(compound, cableStack);
    }
}
