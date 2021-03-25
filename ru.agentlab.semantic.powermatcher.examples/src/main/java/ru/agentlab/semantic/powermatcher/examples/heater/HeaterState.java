package ru.agentlab.semantic.powermatcher.examples.heater;

import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;

public class HeaterState {
    private final HeaterSimulationModel model;
    private final ThingPropertyAffordance power;
    private final ThingPropertyAffordance indoor;
    private final ThingPropertyAffordance outdoor;
    private final ThingActionAffordance setPowerAffordance;

    public HeaterState(HeaterSimulationModel model,
                       ThingPropertyAffordance power,
                       ThingPropertyAffordance indoor,
                       ThingPropertyAffordance outdoor,
                       ThingActionAffordance setPowerAffordance) {
        this.model = model;
        this.power = power;
        this.indoor = indoor;
        this.outdoor = outdoor;
        this.setPowerAffordance = setPowerAffordance;
    }

    public HeaterSimulationModel getModel() {
        return model;
    }

    public ThingPropertyAffordance getPower() {
        return power;
    }

    public ThingPropertyAffordance getIndoor() {
        return indoor;
    }

    public ThingPropertyAffordance getOutdoor() {
        return outdoor;
    }

    public ThingActionAffordance getSetPowerAffordance() {
        return setPowerAffordance;
    }

    @Override
    public String toString() {
        return "HeaterState{" +
                "model=" + model +
                ", power=" + power +
                ", indoor=" + indoor +
                ", outdoor=" + outdoor +
                ", setPowerAffordance=" + setPowerAffordance +
                '}';
    }
}
