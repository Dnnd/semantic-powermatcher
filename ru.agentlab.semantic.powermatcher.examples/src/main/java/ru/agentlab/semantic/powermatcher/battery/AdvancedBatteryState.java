package ru.agentlab.semantic.powermatcher.battery;

import org.flexiblepower.ral.ResourceState;

import javax.measure.Measurable;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;

public interface AdvancedBatteryState extends ResourceState{

	float getStateOfCharge();

	Measurable<Power> getElectricPower();

	Measurable<Energy> getTotalCapacity();


	double minimumFillLevelPercent();

	double maximumFillLevelPercent();


	double maximumChargingRateWatts();

	double maximumDischargingRateWatts();

	int getNumberOfModulationSteps();
}
