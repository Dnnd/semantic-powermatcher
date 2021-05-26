package ru.agentlab.semantic.powermatcher.battery;

import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;

public class SimpleBatteryTwin {
    private final SimpleBatteryModel model;
    private final ThingPropertyAffordance power;
    private final ThingPropertyAffordance stateOfCharge;

    public SimpleBatteryModel getModel() {
        return model;
    }

    public ThingPropertyAffordance getPower() {
        return power;
    }

    public ThingPropertyAffordance getStateOfCharge() {
        return stateOfCharge;
    }

    public ThingActionAffordance getSetPowerAffordance() {
        return setPowerAffordance;
    }

    private final ThingActionAffordance setPowerAffordance;

    public SimpleBatteryTwin(SimpleBatteryModel model,
                             ThingPropertyAffordance power,
                             ThingPropertyAffordance stateOfCharge,
                             ThingActionAffordance setPowerAffordance) {
        this.model = model;
        this.power = power;
        this.stateOfCharge = stateOfCharge;
        this.setPowerAffordance = setPowerAffordance;
    }

    @Override
    public String toString() {
        return "SimpleBatteryTwin{" +
                "model=" + model +
                ", power=" + power +
                ", stateOfCharge=" + stateOfCharge +
                ", setPowerAffordance=" + setPowerAffordance +
                '}';
    }
}
