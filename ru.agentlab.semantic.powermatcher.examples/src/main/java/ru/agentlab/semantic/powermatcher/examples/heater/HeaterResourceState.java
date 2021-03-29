package ru.agentlab.semantic.powermatcher.examples.heater;

import org.flexiblepower.manager.heater.api.HeaterState;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.quantity.Temperature;
import javax.measure.unit.SI;

public class HeaterResourceState implements HeaterState {

    private final double temperature;
    private final double heatingPower;

    public HeaterResourceState(double temperature, double heatingPower) {
        this.temperature = temperature;
        this.heatingPower = heatingPower;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Measurable<Temperature> getCurrentTemperature() {
        return Measure.valueOf(temperature, SI.CELSIUS);
    }

    @Override
    public Measurable<Power> getCurrentUsage() {
        return Measure.valueOf(heatingPower, SI.WATT);
    }
}
