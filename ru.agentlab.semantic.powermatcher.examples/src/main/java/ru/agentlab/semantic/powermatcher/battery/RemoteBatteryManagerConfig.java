package ru.agentlab.semantic.powermatcher.battery;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(description = "Remote Battery Manager")
public @interface RemoteBatteryManagerConfig {
    @AttributeDefinition
    String resourceId() default "battery";

    @AttributeDefinition
    double minProductionPriceFraction() default 0.7;

    @AttributeDefinition
    double maxConsumptionPriceFraction() default 0.3;
}
