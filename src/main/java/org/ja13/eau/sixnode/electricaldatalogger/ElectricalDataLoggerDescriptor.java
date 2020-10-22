package org.ja13.eau.sixnode.electricaldatalogger;

import org.ja13.eau.EAU;
import org.ja13.eau.misc.*;
import org.ja13.eau.misc.Obj3D.Obj3DPart;
import org.ja13.eau.node.six.SixNodeDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.ja13.eau.EAU;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.misc.Direction;
import org.ja13.eau.misc.LRDU;
import org.ja13.eau.misc.Obj3D;
import org.ja13.eau.misc.Utils;
import org.ja13.eau.misc.UtilsClient;
import org.ja13.eau.misc.VoltageTier;
import org.ja13.eau.node.six.SixNodeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.List;

import static org.ja13.eau.i18n.I18N.tr;

public class ElectricalDataLoggerDescriptor extends SixNodeDescriptor {

    Obj3D obj;
    Obj3D.Obj3DPart main, led, reflection;
    float sx, sy, sz;
    float tx, ty, tz;
    float rx, ry, rz, ra;
    float mx, my;

    float cr, cg, cb;

    float reflc;

    public boolean onFloor;
    public String textColor;

    public ElectricalDataLoggerDescriptor(String name, boolean onFloor, String objName, float cr, float cg, float cb, String textColor) {
        super(name, ElectricalDataLoggerElement.class, ElectricalDataLoggerRender.class);
        this.cb = cb;
        this.cr = cr;
        this.cg = cg;
        this.onFloor = onFloor;
        this.textColor = textColor;
        obj = EAU.obj.getObj(objName);
        if (obj != null) {
            main = obj.getPart("main");
            reflection = obj.getPart("reflection");
            if (main != null) {
                sx = main.getFloat("sx");
                sy = main.getFloat("sy");
                sz = main.getFloat("sz");
                tx = main.getFloat("tx");
                ty = main.getFloat("ty");
                tz = main.getFloat("tz");
                rx = main.getFloat("rx");
                ry = main.getFloat("ry");
                rz = main.getFloat("rz");
                ra = main.getFloat("ra");
                mx = main.getFloat("mx");
                my = main.getFloat("my");
                reflc = main.getFloat("reflc");
                led = obj.getPart("led");
            }
        }

        if (onFloor) {
            setPlaceDirection(Direction.YN);
        }

        voltageTier = VoltageTier.TTL;
    }

    void draw(DataLogs log, Direction side, LRDU front, int objPosMX, int objPosMZ, byte color) {
        if (onFloor || side.isY()) front.glRotateOnX();
        if (!onFloor && side.isNotY()) GL11.glRotatef(90, 1, 0, 0);
        //GL11.glDisable(GL11.GL_TEXTURE_2D);
        if (main != null) {
            Utils.setGlColorFromDye(color);
            main.draw();
            GL11.glColor3f(1f, 1f, 1f);
        }
        //GL11.glEnable(GL11.GL_TEXTURE_2D);

        //Glass (reflections)
        UtilsClient.enableBlend();
        obj.bindTexture("Reflection.png");
        float rotYaw = Minecraft.getMinecraft().thePlayer.rotationYaw / 360.f;
        float rotPitch = Minecraft.getMinecraft().thePlayer.rotationPitch / 180.f;
        float pos = (((float) Minecraft.getMinecraft().thePlayer.posX) - ((float) (objPosMX * 2)) + ((float) Minecraft.getMinecraft().thePlayer.posZ) - ((float) (objPosMZ * 2))) / 24.f;
        GL11.glColor4f(1, 1, 1, reflc);
        reflection.draw(rotYaw + pos, rotPitch * 0.857f);
        UtilsClient.disableBlend();

        //Plot
        if (log != null) {
            UtilsClient.disableLight();
            // GL11.glPushMatrix();
            UtilsClient.ledOnOffColor(true);
            if (led != null) led.draw();

            UtilsClient.glDefaultColor();

            GL11.glTranslatef(tx, ty, tz);
            GL11.glRotatef(ra, rx, ry, rz);
            GL11.glScalef(sx, sy, sz);
            GL11.glColor4f(cr, cg, cb, 1);
            log.draw(mx, my, textColor);

            UtilsClient.glDefaultColor();

            UtilsClient.enableLight();
        }
    }

    @Override
    public boolean hasVolume() {
        return onFloor;
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type != ItemRenderType.INVENTORY;
    }

    @Override
    public boolean shouldUseRenderHelperEln(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return type != ItemRenderType.INVENTORY;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (type == ItemRenderType.INVENTORY) {
            super.renderItem(type, item, data);
        } else {
            if (main != null) main.draw();
        }
    }

    @Override
    public void addInfo(@NotNull ItemStack itemStack, @NotNull EntityPlayer entityPlayer, @NotNull List list) {
        super.addInfo(itemStack, entityPlayer, list);
        Collections.addAll(list, I18N.tr("Measures the voltage of an\nelectrical signal and plots\nthe data in real time.").split("\n"));
        list.add(I18N.tr("It can store up to 256 points."));
    }

    @Override
    public LRDU getFrontFromPlace(Direction side, EntityPlayer player) {
        LRDU front = super.getFrontFromPlace(side, player);
        if (onFloor) {
            return front.inverse();
        } else {
            return front;
        }
    }
}
