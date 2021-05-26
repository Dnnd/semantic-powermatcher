package ru.agentlab.semantic.powermatcher.battery;

import org.flexiblepower.ral.ResourceControlParameters;

import javax.measure.Measurable;
import javax.measure.quantity.Power;

public interface AdvancedBatteryControlParameters extends ResourceControlParameters {
	Measurable<Power> getDesiredPower();
}
