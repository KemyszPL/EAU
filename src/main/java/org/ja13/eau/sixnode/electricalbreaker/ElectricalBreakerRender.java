package org.ja13.eau.sixnode.electricalbreaker;

import org.ja13.eau.cable.CableRenderDescriptor;
import org.ja13.eau.misc.*;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElementInventory;
import org.ja13.eau.node.six.SixNodeElementRender;
import org.ja13.eau.node.six.SixNodeEntity;
import org.ja13.eau.sixnode.genericcable.GenericCableDescriptor;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.ja13.eau.cable.CableRenderDescriptor;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.RcInterpolator;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.misc.UtilsClient;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElementInventory;
import org.ja13.eau.node.six.SixNodeElementRender;
import org.ja13.eau.node.six.SixNodeEntity;
import org.ja13.eau.sixnode.genericcable.GenericCableDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ElectricalBreakerRender extends SixNodeElementRender {

    SixNodeElementInventory inventory = new SixNodeElementInventory(1, 64, this);
    ElectricalBreakerDescriptor descriptor;
    long time;

    RcInterpolator interpol;

    float uMin, uMax;

    boolean boot = true;
    float switchAlpha = 0;
    public boolean switchState;

    CableRenderDescriptor cableRender;

    public ElectricalBreakerRender(SixNodeEntity tileEntity, Direction side, SixNodeDescriptor descriptor) {
        super(tileEntity, side, descriptor);
        this.descriptor = (ElectricalBreakerDescriptor) descriptor;
        time = System.currentTimeMillis();
        interpol = new RcInterpolator(this.descriptor.speed);
    }

    @Override
    public void draw() {
        super.draw();

        front.glRotateOnX();
        descriptor.draw((float)interpol.get(), UtilsClient.distanceFromClientPlayer(tileEntity));
    }

    @Override
    public void refresh(float deltaT) {
        interpol.setTarget(switchState ? 1f : 0f);
        interpol.step(deltaT);
    }

    @Override
    public CableRenderDescriptor getCableRender(LRDU lrdu) {
        return cableRender;
    }

    @Override
    public void publishUnserialize(DataInputStream stream) {
        super.publishUnserialize(stream);
        Utils.println("Front : " + front);
        try {
            switchState = stream.readBoolean();
            uMax = stream.readFloat();
            uMin = stream.readFloat();

            ItemStack itemStack = Utils.unserializeItemStack(stream);
            if (itemStack != null) {
                GenericCableDescriptor desc = (GenericCableDescriptor) GenericCableDescriptor.getDescriptor(itemStack, GenericCableDescriptor.class);
                //ElectricalCableDescriptor desc = (ElectricalCableDescriptor) ElectricalCableDescriptor.getDescriptor(itemStack, ElectricalCableDescriptor.class);
                if (desc == null)
                    cableRender = null;
                else
                    cableRender = desc.render;
            } else {
                cableRender = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (boot) {
            interpol.setValue(switchState ? 1f : 0f);
        }
        boot = false;
    }

    public void clientSetVoltageMin(float value) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(bos);

            preparePacketForServer(stream);

            stream.writeByte(ElectricalBreakerElement.setVoltageMinId);
            stream.writeFloat(value);

            sendPacketToServer(bos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clientSetVoltageMax(float value) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(bos);

            preparePacketForServer(stream);

            stream.writeByte(ElectricalBreakerElement.setVoltageMaxId);
            stream.writeFloat(value);

            sendPacketToServer(bos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clientToogleSwitch() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(bos);

            preparePacketForServer(stream);

            stream.writeByte(ElectricalBreakerElement.toogleSwitchId);

            sendPacketToServer(bos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public GuiScreen newGuiDraw(Direction side, EntityPlayer player) {
        return new ElectricalBreakerGui(player, inventory, this);
    }
}
