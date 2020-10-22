package org.ja13.eau.sixnode.electricalbreaker;

import org.ja13.eau.misc.INBTTReady;
import org.ja13.eau.sim.IProcess;
import org.ja13.eau.sixnode.genericcable.GenericCableDescriptor;
import net.minecraft.nbt.NBTTagCompound;
import org.ja13.eau.misc.INBTTReady;
import org.ja13.eau.sim.IProcess;
import org.ja13.eau.sixnode.genericcable.GenericCableDescriptor;

public class ElectricalBreakerCutProcess implements IProcess, INBTTReady {

    ElectricalBreakerElement breaker;

    double T = 0;

    public ElectricalBreakerCutProcess(ElectricalBreakerElement breaker) {
        this.breaker = breaker;
    }

    @Override
    public void process(double time) {
        double U = breaker.aLoad.getU();
        double I = breaker.aLoad.getCurrent();
        double Tmax = 0;
        GenericCableDescriptor cable = breaker.cableDescriptor;
        if (cable == null) {
            T = 0;
        } else {
            Math.min(I, cable.electricalNominalPower / cable.electricalMaximalVoltage * 10);
            double P = I * I * cable.electricalRs * 2 - T / cable.thermalRp * 0.9;
            /*if (P > 200) {
				int i = 0;
				i++;
				Utils.println(P);
			}*/
            //double pMax = Eln.electricalCableDeltaTMax * cable.thermalC;
            if (I > 1) {
                int idx = 0;
                idx++;
            }
            T += P / cable.thermalC * time;
            Tmax = cable.thermalWarmLimit * 0.8;
        }
        //Utils.println(T);

        if (U >= breaker.voltageMax || U < breaker.voltageMin || T > Tmax) {
            breaker.setSwitchState(false);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt, String str) {
        T = nbt.getFloat(str + "T");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt, String str) {
        nbt.setFloat(str + "T", (float) T);
    }
}
