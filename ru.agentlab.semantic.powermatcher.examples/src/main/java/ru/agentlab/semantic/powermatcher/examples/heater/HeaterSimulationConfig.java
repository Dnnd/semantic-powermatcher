package ru.agentlab.semantic.powermatcher.examples.heater;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
public @interface HeaterSimulationConfig {
    @AttributeDefinition(description = "Heater Thing IRI")
    String thingIRI();

    @AttributeDefinition(description = "Graph context for observations")
    String stateContext();

    @AttributeDefinition(
            description = "Frequency with which updates will be sent out in milliseconds")
    int updateFrequency() default 1000;

    @AttributeDefinition(description = "Graph context for things")
    String thingContext();
}
