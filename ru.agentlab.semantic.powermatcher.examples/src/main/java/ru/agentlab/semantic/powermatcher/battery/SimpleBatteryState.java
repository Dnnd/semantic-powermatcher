package ru.agentlab.semantic.powermatcher.battery;

import javax.measure.Measurable;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;

public class SimpleBatteryState implements AdvancedBatteryState {

    public SimpleBatteryState(float stateOfCharge, Measurable<Power> electricPower,
                              Measurable<Energy> totalCapacity, double fillLevelPercentMin,
                              double fillLevelPercentMax, double chargingRateWattsMax, double dischargingRateWattsMax,
                              int numberOfModulationSteps) {
        this.stateOfCharge = stateOfCharge;
        this.electricPower = electricPower;
        this.totalCapacity = totalCapacity;
        this.fillLevelPercentMin = fillLevelPercentMin;
        this.fillLevelPercentMax = fillLevelPercentMax;
        this.chargingRateWattsMax = chargingRateWattsMax;
        this.dischargingRateWattsMax = dischargingRateWattsMax;
        this.numberOfModulationSteps = numberOfModulationSteps;
    }

    private final float stateOfCharge;
    private final Measurable<Power> electricPower;
    private final Measurable<Energy> totalCapacity;
    private final double fillLevelPercentMin;
    private final double fillLevelPercentMax;
    private final double chargingRateWattsMax;
    private final double dischargingRateWattsMax;
    private final int numberOfModulationSteps;

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public float getStateOfCharge() {
        return this.stateOfCharge;
    }

    @Override
    public Measurable<Power> getElectricPower() {
        return this.electricPower;
    }

    @Override
    public Measurable<Energy> getTotalCapacity() {
        return this.totalCapacity;
    }

    @Override
    public double minimumFillLevelPercent() {
        return fillLevelPercentMin;
    }

    @Override
    public double maximumFillLevelPercent() {
        return fillLevelPercentMax;
    }

    @Override
    public double maximumChargingRateWatts() {
        return chargingRateWattsMax;
    }

    @Override
    public double maximumDischargingRateWatts() {
        return dischargingRateWattsMax;
    }

    @Override
    public int getNumberOfModulationSteps() {
        return numberOfModulationSteps;
    }

}