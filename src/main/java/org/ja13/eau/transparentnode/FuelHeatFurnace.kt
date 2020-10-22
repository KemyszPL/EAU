package org.ja13.eau.transparentnode

import net.minecraft.client.gui.GuiButton
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.client.IItemRenderer
import org.ja13.eau.fluid.FuelRegistry
import org.ja13.eau.fluid.PreciseElementFluidHandler
import org.ja13.eau.gui.ISlotSkin.SlotSkin
import org.ja13.eau.item.FuelBurnerDescriptor
import org.ja13.eau.item.regulator.IRegulatorDescriptor.RegulatorType
import org.ja13.eau.misc.Direction
import org.ja13.eau.misc.LRDU
import org.ja13.eau.misc.Obj3D
import org.ja13.eau.misc.Utils
import org.ja13.eau.misc.UtilsClient
import org.ja13.eau.misc.VoltageTier
import org.ja13.eau.node.Synchronizable
import org.ja13.eau.node.published
import org.ja13.eau.sim.ThermalLoadInitializerByPowerDrop
import org.ja13.eau.sound.LoopedSound
import org.lwjgl.opengl.GL11
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.*

class FuelHeatFurnaceDescriptor(name: String, model: Obj3D, val thermal: ThermalLoadInitializerByPowerDrop) :
    org.ja13.eau.node.transparent.TransparentNodeDescriptor(name, FuelHeatFurnaceElement::class.java, FuelHeatFurnaceRender::class.java,
        org.ja13.eau.node.transparent.EntityMetaTag.Fluid) {
    private val main = model.getPart("Main")
    private val burners = arrayOf(model.getPart("BurnerA"), model.getPart("BurnerB"), model.getPart("BurnerC"))
    private val powerLED = model.getPart("PowerLED")
    private val heatLED = model.getPart("HeatLED")

    init {
        thermal.setMaximalPower(2000.0)
        voltageTier = VoltageTier.NEUTRAL
    }

    fun draw(installedBurner: Int? = null, on: Boolean = false, heating: Boolean = false) {
        main?.draw()
        if (installedBurner != null) {
            burners[installedBurner]?.draw()
        }

        if (on) {
            GL11.glColor3f(0f, 1f, 0f)
            UtilsClient.drawLight(powerLED)
        } else {
            GL11.glColor3f(0f, 0.5f, 0f)
            powerLED?.draw()
        }

        if (heating) {
            GL11.glColor3f(1f, 0f, 0f)
            UtilsClient.drawLight(heatLED)
        } else {
            GL11.glColor3f(0.5f, 0f, 0f)
            heatLED?.draw()
        }
        GL11.glColor3f(1f, 1f, 1f)
    }

    override fun handleRenderType(item: ItemStack?, type: IItemRenderer.ItemRenderType?) = true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType?, item: ItemStack?,
                                       helper: IItemRenderer.ItemRendererHelper?) =
        type != IItemRenderer.ItemRenderType.INVENTORY
    override fun renderItem(type: IItemRenderer.ItemRenderType?, item: ItemStack?, vararg data: Any?) =
        if (type == IItemRenderer.ItemRenderType.INVENTORY) super.renderItem(type, item, *data) else draw()

    override fun addInfo(itemStack: ItemStack, entityPlayer: EntityPlayer, list: MutableList<String>) {
        super.addInfo(itemStack, entityPlayer, list)
        list.add(org.ja13.eau.i18n.I18N.tr("Generates heat when supplied with fuel."))
        list.add(Utils.plotCelsius(thermal.warmLimit, org.ja13.eau.i18n.I18N.tr("Max. temperature:")))
    }
}

class FuelHeatFurnaceElement(transparentNode: org.ja13.eau.node.transparent.TransparentNode, descriptor: org.ja13.eau.node.transparent.TransparentNodeDescriptor) :
    org.ja13.eau.node.transparent.TransparentNodeElement(transparentNode, descriptor) {
    companion object {
        val ExternalControlledToggleEvent: Byte = 0
        val MainSwitchToggleEvent: Byte = 1
        val SetManualControlValueEvent: Byte = 2
        val SetTemperatureEvent: Byte = 3
    }

    private val thermalLoad = org.ja13.eau.sim.nbt.NbtThermalLoad("thermalLoad")
    private val controlLoad = org.ja13.eau.sim.nbt.NbtElectricalGateInput("commandLoad")

    private val tank = PreciseElementFluidHandler(25)

    private val inventory_ = org.ja13.eau.node.transparent.TransparentNodeElementInventory(2, 1, this)

    private var externalControlled by published(false)
    private var mainSwitch by published(false)

    private var manualControl by published(0.0)
    private var setTemperature by published(0.0)

    private var heaterControlValue = 0.0
    private var actualHeatPower by published(0.0)

    private val controlProcess = object : org.ja13.eau.sim.RegulatorProcess("controller") {
        override fun process(time: Double) {
            val nominalPower = if (mainSwitch)
                FuelBurnerDescriptor.getDescriptor(inventory_.getStackInSlot(FuelHeatFurnaceContainer.FuelBurnerSlot))?.producedHeatPower ?: 0.0
            else
                0.0

            when {
                externalControlled -> {
                    setCmd(controlLoad.u / VoltageTier.TTL.voltage)
                }
                else -> {
                    setCmd(manualControl)
                }
            }
            super.process(time)

            val availableEnergy = tank.drainEnergy(heaterControlValue * nominalPower * time)
            actualHeatPower = availableEnergy / time
            thermalLoad.PcTemp += actualHeatPower
        }

        override fun getHit() = thermalLoad.Tc
        override fun setCmd(cmd: Double) {
            heaterControlValue = Math.max(0.0, cmd)
        }
    }

    private val thermalWatchdog = org.ja13.eau.sim.process.destruct.ThermalLoadWatchDog()

    init {
        thermalLoadList.add(thermalLoad)
        thermalFastProcessList.add(controlProcess)
        electricalLoadList.add(controlLoad)
        slowProcessList.add(org.ja13.eau.node.NodePeriodicPublishProcess(transparentNode, 2.0, 1.0))
        slowProcessList.add(thermalWatchdog)

        tank.setFilter(FuelRegistry.fluidListToFluids(FuelRegistry.gasolineList + FuelRegistry.dieselList))

        thermalWatchdog.set(thermalLoad).setLimit((descriptor as FuelHeatFurnaceDescriptor).thermal)
            .set(org.ja13.eau.sim.process.destruct.WorldExplosion(this).machineExplosion())
    }

    override fun getElectricalLoad(side: Direction?, lrdu: LRDU?) = when {
        side != front.inverse && lrdu == LRDU.Down -> controlLoad
        else -> null
    }

    override fun getThermalLoad(side: Direction?, lrdu: LRDU?) = when {
        side == front.inverse && lrdu == LRDU.Down -> thermalLoad
        else -> null
    }

    override fun getConnectionMask(side: Direction?, lrdu: LRDU?) = when (lrdu) {
        LRDU.Down -> when (side) {
            front.inverse -> org.ja13.eau.node.NodeBase.MASK_THERMAL
            else -> org.ja13.eau.node.NodeBase.MASK_ELECTRIC
        }
        else -> 0
    }

    override fun getFluidHandler() = tank

    override fun multiMeterString(side: Direction?) = Utils.plotPower(thermalLoad.power)

    override fun thermoMeterString(side: Direction) = Utils.plotCelsius(thermalLoad.Tc)

    override fun initialize() {
        (descriptor as FuelHeatFurnaceDescriptor).thermal.applyTo(thermalLoad)
        inventoryChange(inventory_)
        connect()
    }

    override fun onBlockActivated(entityPlayer: EntityPlayer?, side: Direction?, vx: Float, vy: Float, vz: Float) = false

    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        stream.writeBoolean(externalControlled)
        stream.writeBoolean(mainSwitch)
        stream.writeFloat(heaterControlValue.toFloat())
        stream.writeFloat(setTemperature.toFloat())
        stream.writeFloat(actualHeatPower.toFloat())
        stream.writeFloat(thermalLoad.Tc.toFloat())
        stream.writeInt(FuelBurnerDescriptor.getDescriptor(inventory_.getStackInSlot(FuelHeatFurnaceContainer.FuelBurnerSlot))?.type ?: -1)
    }

    override fun networkUnserialize(stream: DataInputStream): Byte {
        when (super.networkUnserialize(stream)) {
            ExternalControlledToggleEvent -> {
                externalControlled = !externalControlled
                inventoryChange(inventory_)
            }
            MainSwitchToggleEvent -> mainSwitch = !mainSwitch
            SetManualControlValueEvent -> {
                manualControl = stream.readFloat().toDouble()
            }
            SetTemperatureEvent -> {
                setTemperature = stream.readFloat().toDouble()
                controlProcess.target = setTemperature
            }
        }

        return org.ja13.eau.node.transparent.TransparentNodeElement.unserializeNulldId
    }

    override fun writeToNBT(nbt: NBTTagCompound) {
        super.writeToNBT(nbt)
        tank.writeToNBT(nbt, "tank")
        nbt.setBoolean("externalControlled", externalControlled)
        nbt.setBoolean("mainSwitch", mainSwitch)
        nbt.setDouble("heaterControlValue", heaterControlValue)
        nbt.setDouble("manualControl", manualControl)
        nbt.setDouble("setTemperature", setTemperature)
        nbt.setDouble("actualHeatPower", actualHeatPower)
    }

    override fun readFromNBT(nbt: NBTTagCompound) {
        super.readFromNBT(nbt)
        tank.readFromNBT(nbt, "tank")
        externalControlled = nbt.getBoolean("externalControlled")
        mainSwitch = nbt.getBoolean("mainSwitch")
        heaterControlValue = nbt.getDouble("heaterControlValue")
        manualControl = nbt.getDouble("manualControl")
        setTemperature = nbt.getDouble("setTemperature")
        actualHeatPower = nbt.getDouble("actualHeatPower")
    }

    override fun hasGui() = true

    override fun getWaila(): MutableMap<String, String> {
        val info = HashMap<String, String>()
        info[org.ja13.eau.i18n.I18N.tr("Temperature")] = Utils.plotCelsius(thermalLoad.Tc)
        info[org.ja13.eau.i18n.I18N.tr("Power")] = Utils.plotPower(actualHeatPower)
        return info
    }

    override fun getInventory() = inventory_

    override fun inventoryChange(inventory: IInventory?) {
        mainSwitch = mainSwitch && inventory_.getStackInSlot(FuelHeatFurnaceContainer.FuelBurnerSlot) != null

        val regulatorStack = inventory_.getStackInSlot(FuelHeatFurnaceContainer.RegulatorSlot)
        if (regulatorStack != null && !externalControlled) {
            val regulator = Utils.getItemObject(regulatorStack) as org.ja13.eau.item.regulator.IRegulatorDescriptor
            regulator.applyTo(controlProcess, 500.0, 20.0, 0.2, 0.1)
        } else {
            controlProcess.setManual()
        }
    }

    override fun newContainer(side: Direction?, player: EntityPlayer) = FuelHeatFurnaceContainer(node, player, inventory_)
}

class FuelHeatFurnaceRender(tileEntity: org.ja13.eau.node.transparent.TransparentNodeEntity, descriptor: org.ja13.eau.node.transparent.TransparentNodeDescriptor) :
    org.ja13.eau.node.transparent.TransparentNodeElementRender(tileEntity, descriptor) {
    private val inventory = org.ja13.eau.node.transparent.TransparentNodeElementInventory(2, 1, this)

    var type: Int? = null
    var externalControlled = false
    var mainSwitch = false

    var manualControl = Synchronizable(0f)
    var setTemperature = Synchronizable(0f)

    var heatPower = 0f
    var actualTemperature = 0f

    val sound = object : LoopedSound("eln:fuelheatfurnace", coordonate()) {
        override fun getPitch() = FuelBurnerDescriptor.pitchForType(type)
        override fun getVolume() = if (heatPower > 0) 0.01f + 0.00001f * heatPower else 0f
    }

    init {
        addLoopedSound(sound)
    }

    override fun draw() {
        front.glRotateXnRef()
        (transparentNodedescriptor as FuelHeatFurnaceDescriptor).draw(type, mainSwitch, heatPower != 0f)
    }

    override fun newGuiDraw(side: Direction?, player: EntityPlayer) = FuelHeatFurnaceGui(player, inventory, this)

    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        externalControlled = stream.readBoolean()
        mainSwitch = stream.readBoolean()
        manualControl.value = stream.readFloat()
        setTemperature.value = stream.readFloat()
        heatPower = stream.readFloat()
        actualTemperature = stream.readFloat()
        type = stream.readInt().let { type ->
            when (type) {
                -1 -> null
                else -> type
            }
        }
    }

    override fun getInventory() = inventory
}

class FuelHeatFurnaceContainer(val base: org.ja13.eau.node.NodeBase?, player: EntityPlayer, inventory: IInventory) :
    org.ja13.eau.misc.BasicContainer(player, inventory,
        arrayOf(org.ja13.eau.generic.GenericItemUsingDamageSlot(inventory, FuelBurnerSlot, 26, 58, 1, FuelBurnerDescriptor::class.java,
                SlotSkin.medium, arrayOf(org.ja13.eau.i18n.I18N.tr("Fuel burner slot"))),
                org.ja13.eau.item.regulator.RegulatorSlot(inventory, RegulatorSlot, 8, 58, 1, arrayOf(RegulatorType.Analog),
                        SlotSkin.medium, org.ja13.eau.i18n.I18N.tr("Analog regulator slot")))), org.ja13.eau.node.INodeContainer {
    companion object {
        val FuelBurnerSlot = 0
        val RegulatorSlot = 1
    }

    override fun getNode() = base

    override fun getRefreshRateDivider() = 1
}

class FuelHeatFurnaceGui(player: EntityPlayer, val inventory: IInventory, val render: FuelHeatFurnaceRender) :
    org.ja13.eau.gui.GuiContainerEln(FuelHeatFurnaceContainer(null, player, inventory)) {
    lateinit var externalControlled: GuiButton
    lateinit var mainSwitch: GuiButton

    lateinit var manualControl: org.ja13.eau.gui.GuiVerticalTrackBar
    lateinit var setTemperature: org.ja13.eau.gui.GuiVerticalTrackBarHeat

    override fun initGui() {
        super.initGui()

        externalControlled = newGuiButton(6, 6, 100, "")
        mainSwitch = newGuiButton(6, 30, 100, "")

        manualControl = newGuiVerticalTrackBar(144, 8, 20, 69)
        manualControl.setStepIdMax((0.9f / 0.01f).toInt())
        manualControl.setRange(0f, 1f)
        manualControl.value = render.manualControl.value

        setTemperature = newGuiVerticalTrackBarHeat(116, 8, 20, 69)
        setTemperature.setStepIdMax(98)
        setTemperature.setRange(0f, 900f)
        setTemperature.value = render.setTemperature.value
    }

    override fun preDraw(f: Float, x: Int, y: Int) {
        super.preDraw(f, x, y)

        if (!render.externalControlled)
            externalControlled.displayString = org.ja13.eau.i18n.I18N.tr("Internal control")
        else
            externalControlled.displayString = org.ja13.eau.i18n.I18N.tr("External control")

        if (render.mainSwitch)
            mainSwitch.displayString = org.ja13.eau.i18n.I18N.tr("Furnace is on")
        else
            mainSwitch.displayString = org.ja13.eau.i18n.I18N.tr("Furnace is off")
        mainSwitch.enabled = inventory.getStackInSlot(FuelHeatFurnaceContainer.FuelBurnerSlot) != null

        if (render.manualControl.pending) {
            manualControl.value = render.manualControl.value
        }
        manualControl.setEnable(inventory.getStackInSlot(FuelHeatFurnaceContainer.RegulatorSlot) == null &&
            !render.externalControlled)
        manualControl.setComment(0, org.ja13.eau.i18n.I18N.tr("Control value at %1$", Utils.plotPercent(manualControl.value.toDouble())))
        manualControl.setComment(1, org.ja13.eau.i18n.I18N.tr("Heat Power: %1$", Utils.plotPower(render.heatPower.toDouble())))

        if (render.setTemperature.pending) {
            setTemperature.value = render.setTemperature.value
        }
        setTemperature.setEnable(inventory.getStackInSlot(FuelHeatFurnaceContainer.RegulatorSlot) != null &&
            !render.externalControlled)
        setTemperature.temperatureHit = Math.max(0f, render.actualTemperature)
        setTemperature.setComment(0, org.ja13.eau.i18n.I18N.tr("Temperature"))
        setTemperature.setComment(1, org.ja13.eau.i18n.I18N.tr("Actual: %1$", Utils.plotCelsius(render.actualTemperature.toDouble())))
        if (!render.externalControlled)
            setTemperature.setComment(2, org.ja13.eau.i18n.I18N.tr("Set point: %1$", Utils.plotCelsius(setTemperature.value.toDouble())))
    }

    override fun guiObjectEvent(sender: org.ja13.eau.gui.IGuiObject?) {
        super.guiObjectEvent(sender)

        when (sender) {
            externalControlled -> render.clientSendId(FuelHeatFurnaceElement.ExternalControlledToggleEvent)
            mainSwitch -> render.clientSendId(FuelHeatFurnaceElement.MainSwitchToggleEvent)
            manualControl -> render.clientSendFloat(FuelHeatFurnaceElement.SetManualControlValueEvent, manualControl.value)
            setTemperature -> render.clientSendFloat(FuelHeatFurnaceElement.SetTemperatureEvent, setTemperature.value)
        }
    }

    override fun newHelper() = org.ja13.eau.gui.HelperStdContainer(this)
}
