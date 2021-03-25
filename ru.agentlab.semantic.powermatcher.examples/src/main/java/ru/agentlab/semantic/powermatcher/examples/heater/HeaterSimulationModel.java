package ru.agentlab.semantic.powermatcher.examples.heater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.quantity.Temperature;
import javax.measure.unit.SI;
import java.time.Duration;

public class HeaterSimulationModel {

    private final double wallThermalTransferResistance = 5;
    private final double airHeatCapacity = 1005;
    private final double airExchangeRate = 7;
    private final int floors = 1;
    private final double floorHeight = 2.5;
    private final double airVolumeReductionFactor = 0.85;

    private double indoorTemperature;
    private double outdoorTemperature;
    private double heatingPower;

    private final double buildingLength;
    private final double buildingWidth;
    private final double buildingHeight;

    private final Logger logger = LoggerFactory.getLogger(HeaterSimulationModel.class);

    public HeaterSimulationModel(Building building,
                                 double indoorTemperature,
                                 double outdoorTemperature,
                                 double heatingPower) {
        this.indoorTemperature = indoorTemperature;
        this.heatingPower = heatingPower;
        this.outdoorTemperature = outdoorTemperature;
        this.buildingLength = building.getLength();
        this.buildingWidth = building.getWidth();
        this.buildingHeight = building.getHeight();
    }

    public double getIndoorTemperature() {
        return indoorTemperature;
    }

    public void setOutdoorTemperature(double temperatureValue) {
        outdoorTemperature = temperatureValue;
    }

    public double getOutdoorTemperature() {
        return outdoorTemperature;
    }

    public void setHeatingPower(double powerValue) {
        if (heatingPower != powerValue) {
            heatingPower = powerValue;
        }
    }

    public void calculate(Duration timeDelta) {
        double timeDeltaInSeconds = timeDelta.toSeconds();

        logger.debug("Time diff: {}", timeDeltaInSeconds);

        double ventilationHeatTransferFactor = 0.28 * airHeatCapacity * airVolumeReductionFactor * airVolume()
                * airDensity() * airExchangeRate / outdoorWallsArea() / 3600;
        double outdoorHeatTransferFactor = 0.9 * Math.pow(floors, 0.25) * (outdoorWallsArea() / heatingArea())
                / wallThermalTransferResistance;
        double heatLossFactor = (outdoorHeatTransferFactor + ventilationHeatTransferFactor) * outdoorWallsArea();

        indoorTemperature = indoorTemperature + timeDeltaInSeconds
                * (heatingPower - heatLossFactor * (indoorTemperature - outdoorTemperature)) / thermalMass();
    }

    private double airDensity() {
        return 353 / (273 + 0.5 * (indoorTemperature + outdoorTemperature));
    }

    private double airVolume() {
        return buildingLength * buildingWidth * buildingHeight;
    }

    private double outdoorWallsArea() {
        return 2 * (buildingLength * buildingWidth + buildingHeight * buildingWidth
                + buildingHeight * buildingLength);
    }

    private double heatingArea() {
        return airVolume() / floorHeight;
    }

    private double thermalMass() {
        return airHeatCapacity * airDensity() * airVolume();
    }

    public double getHeatingPower() {
        return heatingPower;
    }

    @Override
    public String toString() {
        return "HeaterSimulationModel{" +
                "wallThermalTransferResistance=" + wallThermalTransferResistance +
                ", airHeatCapacity=" + airHeatCapacity +
                ", airExchangeRate=" + airExchangeRate +
                ", floors=" + floors +
                ", floorHeight=" + floorHeight +
                ", airVolumeReductionFactor=" + airVolumeReductionFactor +
                ", indoorTemperature=" + indoorTemperature +
                ", outdoorTemperature=" + outdoorTemperature +
                ", heatingPower=" + heatingPower +
                ", buildingLength=" + buildingLength +
                ", buildingWidth=" + buildingWidth +
                ", buildingHeight=" + buildingHeight +
                '}';
    }
}
