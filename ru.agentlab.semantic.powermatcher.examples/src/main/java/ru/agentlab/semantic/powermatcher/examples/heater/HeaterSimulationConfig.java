package ru.agentlab.semantic.powermatcher.examples.heater;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
public @interface HeaterSimulationConfig {
	@AttributeDefinition(
			description = "Resource identifier")
	String resourceId() default "heater";

	@AttributeDefinition(description = "Heater Thing IRI")
	String thingIRI();

	@AttributeDefinition(
			description = "Frequency with which updates will be sent out in seconds")
	int updateFrequency() default 5;

	@AttributeDefinition(
			description = "Initial indoor temperature in Celsius")
	double initialIndoorTemperature() default 10;
}
