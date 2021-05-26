package ru.agentlab.semantic.powermatcher.battery;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.ral.drivers.battery.BatteryMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import java.time.Duration;
import java.time.OffsetDateTime;

public class SimpleBatteryModel {
    private final Measurable<Dimensionless> minLevel;
    private final Measurable<Dimensionless> maxLevel;
    private final Logger logger = LoggerFactory.getLogger(SimpleBatteryModel.class);
    private Measurable<Power> dischargeSpeedInWatt;
    private Measurable<Power> chargeSpeedInWatt;
    private final Measurable<Power> selfDischargeSpeedInWatt;
    private final Measurable<Energy> totalCapacityInKWh;
    private BatteryMode mode = BatteryMode.IDLE;
    private OffsetDateTime lastUpdatedTime;
    private double stateOfCharge;

    public SimpleBatteryModel(Measurable<Power> dischargeSpeedInWatt,
                              Measurable<Power> chargeSpeedInWatt,
                              Measurable<Power> selfDischargeSpeedInWatt,
                              Measurable<Energy> totalCapacityInKWh,
                              OffsetDateTime lastUpdatedTime,
                              double stateOfCharge,
                              Measurable<Dimensionless> maxLevel,
                              Measurable<Dimensionless> minLevel) {
        this.dischargeSpeedInWatt = dischargeSpeedInWatt;
        this.chargeSpeedInWatt = chargeSpeedInWatt;
        this.selfDischargeSpeedInWatt = selfDischargeSpeedInWatt;
        this.totalCapacityInKWh = totalCapacityInKWh;
        this.lastUpdatedTime = lastUpdatedTime;
        this.stateOfCharge = stateOfCharge;
        this.maxLevel = maxLevel;
        this.minLevel = minLevel;
    }

    public void calculate(Duration interval) {
        double durationSinceLastUpdate = interval.getSeconds();
        lastUpdatedTime = lastUpdatedTime.plus(interval);

        logger.debug("Battery simulation step. Mode={} Timestep={}s", mode, durationSinceLastUpdate);
        if (durationSinceLastUpdate > 0) {
            double amountOfChargeInWatt = switch (mode) {
                case IDLE -> 0;
                case CHARGE -> chargeSpeedInWatt.doubleValue(SI.WATT);
                case DISCHARGE -> -dischargeSpeedInWatt.doubleValue(SI.WATT);
            };
            // always also self discharge
            double changeInW = amountOfChargeInWatt - selfDischargeSpeedInWatt.doubleValue(SI.WATT);
            double changeInWS = changeInW * durationSinceLastUpdate;
            double changeInKWH = changeInWS / (1000.0 * 3600.0);

            double newStateOfCharge = stateOfCharge + (changeInKWH / totalCapacityInKWh.doubleValue(NonSI.KWH));

            // check if the stateOfCharge is not outside the limits of the battery
            var minPercents = this.minLevel.doubleValue(NonSI.PERCENT) / 100d;
            if (newStateOfCharge < minPercents) {
                newStateOfCharge = minPercents;
                // indicate that battery has stopped discharging
                mode = BatteryMode.IDLE;
            } else {
                var maxPercents = this.maxLevel.doubleValue(NonSI.PERCENT) / 100d;
                if (newStateOfCharge > maxPercents) {
                    newStateOfCharge = maxPercents;
                    // indicate that battery has stopped charging
                    mode = BatteryMode.IDLE;
                }
            }
            stateOfCharge = newStateOfCharge;
        }
    }

    public void setRunningMode(Measurable<Power> power) {
        double powerInWatt = power.doubleValue(SI.WATT);
        if (powerInWatt > 0) {
            this.mode = BatteryMode.CHARGE;
            this.chargeSpeedInWatt = power;
        } else if (powerInWatt < 0) {
            this.mode = BatteryMode.DISCHARGE;
            this.dischargeSpeedInWatt = Measure.valueOf(-powerInWatt, SI.WATT);
        } else {
            this.mode = BatteryMode.IDLE;
            this.dischargeSpeedInWatt = Measure.zero(SI.WATT);
            this.chargeSpeedInWatt = Measure.zero(SI.WATT);
        }
    }

    public Measurable<Power> getElectricPower() {
        return switch (mode) {
            case CHARGE -> chargeSpeedInWatt;
            case DISCHARGE -> Measure.valueOf(-this.dischargeSpeedInWatt.doubleValue(SI.WATT), SI.WATT);
            default -> Measure.zero(SI.WATT);
        };
    }

    public double getCurrentFillLevel() {
        return this.stateOfCharge;
    }

    @Override
    public String toString() {
        return "SimpleBatteryModel{" +
                "minLevel=" + minLevel +
                ", maxLevel=" + maxLevel +
                ", dischargeSpeedInWatt=" + dischargeSpeedInWatt +
                ", chargeSpeedInWatt=" + chargeSpeedInWatt +
                ", selfDischargeSpeedInWatt=" + selfDischargeSpeedInWatt +
                ", totalCapacityInKWh=" + totalCapacityInKWh +
                ", mode=" + mode +
                ", lastUpdatedTime=" + lastUpdatedTime +
                ", stateOfCharge=" + stateOfCharge +
                '}';
    }
}
