package org.ja13.eau.transparentnode.solarpanel;


import org.ja13.eau.gui.GuiContainerEln;
import org.ja13.eau.gui.GuiHelperContainer;
import org.ja13.eau.gui.GuiVerticalTrackBar;
import org.ja13.eau.gui.IGuiObject;
import org.ja13.eau.node.transparent.TransparentNodeElementInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import org.ja13.eau.gui.GuiContainerEln;
import org.ja13.eau.gui.GuiHelperContainer;
import org.ja13.eau.gui.GuiVerticalTrackBar;
import org.ja13.eau.gui.IGuiObject;
import org.ja13.eau.i18n.I18N;
import org.ja13.eau.node.transparent.TransparentNodeElementInventory;

import static org.ja13.eau.i18n.I18N.tr;


public class SolarPannelGuiDraw extends GuiContainerEln {


    private final TransparentNodeElementInventory inventory;
    SolarPanelRender render;

    GuiVerticalTrackBar vuMeterTemperature;

    public SolarPannelGuiDraw(EntityPlayer player, IInventory inventory, SolarPanelRender render) {
        super(new SolarPanelContainer(null, player, inventory));
        this.inventory = (TransparentNodeElementInventory) inventory;
        this.render = render;


    }

    public void initGui() {
        super.initGui();


        vuMeterTemperature = newGuiVerticalTrackBar(176 / 2 + 12, 8, 20, 69);
        vuMeterTemperature.setStepIdMax(181);
        vuMeterTemperature.setEnable(true);
        vuMeterTemperature.setRange((float) render.descriptor.alphaMin, (float) render.descriptor.alphaMax);
        syncVumeter();
    }

    public void syncVumeter() {
        vuMeterTemperature.setValue(render.pannelAlphaSyncValue);
        render.pannelAlphaSyncNew = false;
    }


    @Override
    public void guiObjectEvent(IGuiObject object) {

        super.guiObjectEvent(object);
        if (vuMeterTemperature == object) {
            render.clientSetPannelAlpha(vuMeterTemperature.getValue());
        }
    }

    @Override
    protected void preDraw(float f, int x, int y) {

        super.preDraw(f, x, y);
        if (render.pannelAlphaSyncNew) syncVumeter();
        //vuMeterTemperature.temperatureHit = (float) (SolarPannelSlowProcess.getSolarAlpha(render.tileEntity.worldObj));
        vuMeterTemperature.setEnable(!render.hasTracker);
        int sunAlpha = ((int) (180 / Math.PI * SolarPannelSlowProcess.getSolarAlpha(render.tileEntity.getWorldObj())) - 90);

        vuMeterTemperature.setComment(0, I18N.tr("Solar panel angle: %1$°", ((int) (180 / Math.PI * vuMeterTemperature.getValue()) - 90)));
        if (Math.abs(sunAlpha) > 90)
            vuMeterTemperature.setComment(1, I18N.tr("It is night"));
        else
            vuMeterTemperature.setComment(1, I18N.tr("Sun angle: %1$°", sunAlpha));
    }

    @Override
    protected void postDraw(float f, int x, int y) {

        super.postDraw(f, x, y);
        //drawString(8, 6,"Alpha " + render.pannelAlphaSyncNew);
    }

    @Override
    protected GuiHelperContainer newHelper() {

        return new GuiHelperContainer(this, 176, 166, 8, 84);
    }


}
