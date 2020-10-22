package org.ja13.eau.transparentnode.eggincubator;

import org.ja13.eau.gui.GuiContainerEln;
import org.ja13.eau.gui.GuiHelperContainer;
import org.ja13.eau.gui.GuiVerticalVoltageSupplyBar;
import org.ja13.eau.gui.IGuiObject;
import org.ja13.eau.node.transparent.TransparentNodeElementInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import org.ja13.eau.gui.GuiContainerEln;
import org.ja13.eau.gui.GuiHelperContainer;
import org.ja13.eau.gui.GuiVerticalVoltageSupplyBar;
import org.ja13.eau.gui.IGuiObject;
import org.ja13.eau.node.transparent.TransparentNodeElementInventory;

public class EggIncubatorGuiDraw extends GuiContainerEln {

    private final TransparentNodeElementInventory inventory;
    EggIncubatorRender render;

    GuiVerticalVoltageSupplyBar voltage;

    public EggIncubatorGuiDraw(EntityPlayer player, IInventory inventory, EggIncubatorRender render) {
        super(new EggIncubatorContainer(player, inventory, null));
        this.inventory = (TransparentNodeElementInventory) inventory;
        this.render = render;
    }

    public void initGui() {
        super.initGui();
        voltage = new GuiVerticalVoltageSupplyBar(176 - 2, 8, 20, 166 - 55 - 18, helper);
        voltage.setNominalU((float) render.descriptor.nominalVoltage);
        add(voltage);
    }

    @Override
    protected void preDraw(float f, int x, int y) {
        super.preDraw(f, x, y);
        voltage.setVoltage(render.voltage);
    }

    @Override
    public void guiObjectEvent(IGuiObject object) {
        super.guiObjectEvent(object);
    }

    @Override
    protected GuiHelperContainer newHelper() {
        return new GuiHelperContainer(this, 176 + 20 + 6, 166 - 55, 8, 84 - 55);
        //return new HelperStdContainer(this);
    }
}
