package org.ja13.eau.sixnode.lampsocket;

import org.ja13.eau.cable.CableRenderDescriptor;
import org.ja13.eau.item.LampDescriptor;
import org.ja13.eau.item.LampDescriptor.Type;
import org.ja13.eau.misc.*;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElementInventory;
import org.ja13.eau.node.six.SixNodeElementRender;
import org.ja13.eau.node.six.SixNodeEntity;
import org.ja13.eau.sixnode.genericcable.GenericCableDescriptor;
import org.ja13.eau.sound.SoundCommand;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.EnumSkyBlock;
import org.ja13.eau.cable.CableRenderDescriptor;
import org.ja13.eau.item.LampDescriptor;
import org.ja13.eau.misc.Coordonate;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.misc.UtilsClient;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.ja13.eau.node.six.SixNodeElementInventory;
import org.ja13.eau.node.six.SixNodeElementRender;
import org.ja13.eau.node.six.SixNodeEntity;
import org.ja13.eau.sound.SoundCommand;
import org.lwjgl.opengl.GL11;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LampSocketRender extends SixNodeElementRender {

    LampSocketDescriptor lampSocketDescriptor = null;
    LampSocketDescriptor descriptor;

    SixNodeElementInventory inventory = new SixNodeElementInventory(2, 64, this);
    boolean grounded = true;
    public boolean poweredByLampSupply;

    float pertuVy = 0, pertuPy = 0;
    float pertuVz = 0, pertuPz = 0;
    float weatherAlphaZ = 0, weatherAlphaY = 0;

    List entityList = new ArrayList();
    float entityTimout = 0;

    public String channel;
    LampDescriptor lampDescriptor = null;
    float alphaZ;
    byte light, oldLight = -1;
    int paintColor = 15;

    public boolean isConnectedToLampSupply;

    GenericCableDescriptor cable;

    public LampSocketRender(SixNodeEntity tileEntity, Direction side, SixNodeDescriptor descriptor) {
        super(tileEntity, side, descriptor);
        this.descriptor = (LampSocketDescriptor) descriptor;
        lampSocketDescriptor = (LampSocketDescriptor) descriptor;
    }

    @Override
    public GuiScreen newGuiDraw(Direction side, EntityPlayer player) {
        return new LampSocketGuiDraw(player, inventory, this);
    }

    @Override
    public IInventory getInventory() {
        return inventory;
    }

    @Override
    public void draw() {
        super.draw(); //Colored cable only

        GL11.glRotatef(descriptor.initialRotateDeg, 1.f, 0.f, 0.f);
        descriptor.render.draw(this, UtilsClient.distanceFromClientPlayer(this.tileEntity));
    }

    @Override
    public void refresh(float deltaT) {
        if (descriptor.render instanceof LampSocketSuspendedObjRender) {
            float dt = deltaT;

            entityTimout -= dt;
            if (entityTimout < 0) {
                entityList = tileEntity.getWorldObj().getEntitiesWithinAABB(Entity.class, new Coordonate(tileEntity.xCoord, tileEntity.yCoord - 2, tileEntity.zCoord, tileEntity.getWorldObj()).getAxisAlignedBB(2));
                entityTimout = 0.1f;
            }

            for (Object o : entityList) {
                Entity e = (Entity) o;
                float eFactor = 0;
                if (e instanceof EntityArrow)
                    eFactor = 1f;
                if (e instanceof EntityLivingBase)
                    eFactor = 4f;

                if (eFactor == 0)
                    continue;
                pertuVz += e.motionX * eFactor * dt;
                pertuVy += e.motionZ * eFactor * dt;
            }

            if (tileEntity.getWorldObj().getSavedLightValue(EnumSkyBlock.Sky, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord) > 3) {
                float weather = (float) UtilsClient.getWeather(tileEntity.getWorldObj()) * 0.9f + 0.1f;

                // TODO: Reduce swinging of lamps to some degree?
                weatherAlphaY += (0.4 - Math.random()) * dt * Math.PI / 0.2 * weather;
                weatherAlphaZ += (0.4 - Math.random()) * dt * Math.PI / 0.2 * weather;
                if (weatherAlphaY > 2 * Math.PI)
                    weatherAlphaY -= 2 * Math.PI;
                if (weatherAlphaZ > 2 * Math.PI)
                    weatherAlphaZ -= 2 * Math.PI;
                pertuVy += Math.random() * Math.sin(weatherAlphaY) * weather * weather * dt * 3;
                pertuVz += Math.random() * Math.cos(weatherAlphaY) * weather * weather * dt * 3;

                pertuVy += 0.4 * dt * weather * Math.signum(pertuVy) * Math.random();
                pertuVz += 0.4 * dt * weather * Math.signum(pertuVz) * Math.random();
            }

            pertuVy -= pertuPy / 10 * dt;
            pertuVy *= (1 - 0.2 * dt);
            pertuPy += pertuVy;

            pertuVz -= pertuPz / 10 * dt;
            pertuVz *= (1 - 0.2 * dt);
            pertuPz += pertuVz;
        }
    }

    void setLight(byte newLight) {
        light = newLight;
        if (lampDescriptor != null && lampDescriptor.type == LampDescriptor.Type.ECO && oldLight != -1 && oldLight < 9 && light >= 9) {
            float rand = (float) Math.random();
            if (rand > 0.1f)
                play(new SoundCommand("eln:neon_lamp").mulVolume(0.7f, 1.0f + (rand / 6.0f)).smallRange());
            else
                play(new SoundCommand("eln:NEON_LFNOISE").mulVolume(0.2f, 1f).verySmallRange());
        }
        oldLight = light;
    }

    @Override
    public void publishUnserialize(DataInputStream stream) {
        super.publishUnserialize(stream);
        try {
            Byte b;
            b = stream.readByte();
            grounded = (b & (1 << 6)) != 0;

            ItemStack lampStack = Utils.unserializeItemStack(stream);
            lampDescriptor = (LampDescriptor) Utils.getItemObject(lampStack);
            alphaZ = stream.readFloat();
            ItemStack itemStack = Utils.unserializeItemStack(stream);
            if (itemStack != null ) {
                cable = (GenericCableDescriptor) GenericCableDescriptor.getDescriptor(itemStack, GenericCableDescriptor.class);
            }

            poweredByLampSupply = stream.readBoolean();
            channel = stream.readUTF();

            isConnectedToLampSupply = stream.readBoolean();

            setLight(stream.readByte());
            paintColor = stream.readByte();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void serverPacketUnserialize(DataInputStream stream) throws IOException {
        super.serverPacketUnserialize(stream);
        setLight(stream.readByte());
    }

    public boolean getGrounded() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    @Override
    public CableRenderDescriptor getCableRender(LRDU lrdu) {
        if (cable == null
            || (lrdu == front && !descriptor.cableFront)
            || (lrdu == front.left() && !descriptor.cableLeft)
            || (lrdu == front.right() && !descriptor.cableRight)
            || (lrdu == front.inverse() && !descriptor.cableBack))
            return null;
        return cable.render;
    }

    public void clientSetGrounded(boolean value) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream stream = new DataOutputStream(bos);

            preparePacketForServer(stream);

            stream.writeByte(LampSocketElement.setGroundedId);
            stream.writeByte(value ? 1 : 0);

            sendPacketToServer(bos);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean cameraDrawOptimisation() {
        return descriptor.cameraOpt;
    }
}
