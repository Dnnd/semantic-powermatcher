package ru.agentlab.semantic.powermatcher.battery;

import org.flexiblepower.bidding.strategies.api.BiddingStrategy;
import org.flexiblepower.bidding.strategies.api.BiddingStrategyDefinition;
import org.flexiblepower.bidding.strategies.priority.BasicPriorityBiddingStrategy;
import org.flexiblepower.efi.BufferResourceManager;
import org.flexiblepower.efi.buffer.*;
import org.flexiblepower.efi.util.FillLevelFunction;
import org.flexiblepower.efi.util.RunningMode;
import org.flexiblepower.efi.util.Transition;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ext.AbstractResourceManager;
import org.flexiblepower.ral.messages.ControlSpaceRevoke;
import org.flexiblepower.ral.messages.ResourceMessage;
import org.flexiblepower.ral.values.Commodity;
import org.flexiblepower.ral.values.CommodityMeasurables;
import org.flexiblepower.ral.values.CommoditySet;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.Duration;
import javax.measure.quantity.Money;
import javax.measure.quantity.Power;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.tan;

@Component(
        service = Endpoint.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@Port(name = "driver", accepts = AdvancedBatteryState.class)
@Designate(ocd = RemoteBatteryManagerConfig.class, factory = true)
public class RemoteBatteryManager
        extends AbstractResourceManager<AdvancedBatteryState, AdvancedBatteryControlParameters>
        implements BufferResourceManager {
    private static final int BATTERY_CHARGER_ACTUATOR_ID = 0;
    private RemoteBatteryManagerConfig config;
    private Actuator batteryCharger;
    private BufferRegistration<Dimensionless> batteryBufferRegistration;
    private HashMap<Integer, RunningMode<FillLevelFunction<RunningModeBehaviour>>> runningModes;
    private final static Logger logger = LoggerFactory.getLogger(RemoteBatteryManager.class);
    private AdvancedBatteryState state;
    private final Lock lock = new ReentrantLock();

    @Activate
    public void activate(RemoteBatteryManagerConfig config) {
        lock.lock();
        try {
            this.config = config;
        } finally {
            lock.unlock();
        }
    }

    @Modified
    public void update(RemoteBatteryManagerConfig config) {
        lock.lock();
        try {
            this.config = config;
        } finally {
            lock.unlock();
        }
    }

    @Deactivate
    public void deactivate() {
        logger.debug("Remote Battery Manager deactivated");
    }

    protected AdvancedBatteryControlParameters handleBufferAllocation(BufferAllocation message) {
        lock.lock();
        try {
            var fillLevel = getCappedFillLevel();
            for (ActuatorAllocation allocation : message.getActuatorAllocations()) {
                if (allocation.getActuatorId() == batteryCharger.getActuatorId()) {

                    if (runningModes.containsKey(allocation.getRunningModeId())) {
                        Measurable<Power> desiredChargePower = runningModes.get(allocation.getRunningModeId())
                                                                           .getValue()
                                                                           .getValueForFillLevel(fillLevel)
                                                                           .getCommodityConsumption()
                                                                           .get(Commodity.ELECTRICITY);
                        double desiredChargePowerValue = desiredChargePower.doubleValue(SI.WATT);
                        Measurable<Power> actualChargePower = Measure.valueOf(desiredChargePowerValue, SI.WATT);
                        logger.info("Actual power: " + actualChargePower);
                        return () -> actualChargePower;
                    } else {
                        logger.warn(
                                "Received allocation for non-existing runningmode: " + allocation.getRunningModeId());
                        return null;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return null;
    }

    private BufferStateUpdate<Dimensionless> createBufferStateUpdate(Date timestamp) {
        // create running mode
        int currentRunningMode = findRunningModeWithPower(state.getElectricPower().doubleValue(SI.WATT));
        Set<ActuatorUpdate> actuatorUpdates =
                Collections.singleton(new ActuatorUpdate(
                        BATTERY_CHARGER_ACTUATOR_ID,
                        currentRunningMode,
                        null
                ));
        double currentFillLevel = getCappedFillLevel();
        // create buffer state update message
        return new BufferStateUpdate<>(
                batteryBufferRegistration,
                timestamp,
                timestamp,
                Measure.valueOf(
                        currentFillLevel,
                        NonSI.PERCENT
                ),
                actuatorUpdates
        );
    }

    private int findRunningModeWithPower(double powerWatt) {
        double fillLevel = getCappedFillLevel();
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestRmId = 0;
        for (Entry<Integer, RunningMode<FillLevelFunction<RunningModeBehaviour>>> e : runningModes.entrySet()) {
            double rmPower = e.getValue().getValue().getValueForFillLevel(fillLevel).getCommodityConsumption()
                              .get(Commodity.ELECTRICITY).doubleValue(SI.WATT);
            double distance = Math.abs(powerWatt - rmPower);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestRmId = e.getKey();
            }
        }
        return bestRmId;
    }

    private ActuatorBehaviour createBatteryActuatorBehaviour(int actuatorId) {
        int nrOfRunningModes = 3 + 2 * state.getNumberOfModulationSteps();

        // Create a set of all the Transitions. Since every transition is
        // allowed from every RunningMode to every other RunningMode, we can
        // give every RunningMode the same set of all transitions.
        Set<Transition> transitions = new HashSet<Transition>();
        for (int runnningModeId = 0; runnningModeId < nrOfRunningModes; runnningModeId++) {
            transitions.add(makeTransition(runnningModeId));
        }

        // Create RunningModes
        int runningModeId = 0;
        runningModes = new HashMap<>();
        // Charging RunningModes
        double increment = state.maximumChargingRateWatts() / (state.getNumberOfModulationSteps() + 1);
        for (int i = 0; i < state.getNumberOfModulationSteps() + 1; i++) {
            double power = state.maximumChargingRateWatts() - (increment * i);
            runningModes.put(
                    runningModeId,
                    createRunningMode(runningModeId, transitions, Measure.valueOf(power, SI.WATT))
            );
            runningModeId++;
        }

        // Idle RunningMode
        runningModes.put(runningModeId, createRunningMode(runningModeId, transitions, Measure.zero(SI.WATT)));
        runningModeId++;

        // Discharging RunningModes
        increment = state.maximumDischargingRateWatts() / (state.getNumberOfModulationSteps() + 1);
        for (int i = state.getNumberOfModulationSteps(); i >= 0; i--) {
            double power = -state.maximumDischargingRateWatts() + (increment * i);
            runningModes.put(
                    runningModeId,
                    createRunningMode(runningModeId, transitions, Measure.valueOf(power, SI.WATT))
            );
            runningModeId++;
        }

        // return the actuator behavior with the three running modes for the
        // specified actuator id
        return new ActuatorBehaviour(actuatorId, runningModes.values());
    }

    private RunningMode<FillLevelFunction<RunningModeBehaviour>> createRunningMode(int runningModeId,
                                                                                   Set<Transition> transitions,
                                                                                   Measurable<Power> power) {
        String name;
        if (power.doubleValue(SI.WATT) > 0) {
            name = "charging, " + power;
        } else if (power.doubleValue(SI.WATT) < 0) {
            name = "discharging, " + power;
        } else {
            name = "idle";
        }
        return new RunningMode<>(runningModeId, name,
                                 createFillLevelFunction(power), transitions
        );
    }

    private FillLevelFunction<RunningModeBehaviour> createFillLevelFunction(Measurable<Power> power) {
        double lowerBoundPercent = state.minimumFillLevelPercent();
        double upperBoundPercent = state.maximumFillLevelPercent();

        return FillLevelFunction.<RunningModeBehaviour>create(lowerBoundPercent)
                                .add(
                                        upperBoundPercent,
                                        new RunningModeBehaviour(
                                                power.doubleValue(SI.WATT) / state.getTotalCapacity()
                                                                                  .doubleValue(SI.JOULE) * 100d,
                                                CommodityMeasurables.electricity(power),
                                                Measure.zero(NonSI.EUR_PER_HOUR)
                                        )
                                )
                                .build();
    }

    private Transition makeTransition(int toRunningMode) {
        // no timers
        Measurable<Duration> transitionTime = Measure.zero(SI.SECOND);
        // no cost
        Measurable<Money> transitionCosts = Measure.zero(NonSI.EUROCENT);

        // return transition
        return new Transition(toRunningMode, null, null, transitionCosts, transitionTime);
    }

    @Override
    protected ControlSpaceRevoke createRevokeMessage() {
        return null;
    }

    @Override
    protected AdvancedBatteryControlParameters receivedAllocation(ResourceMessage message) {
        if (message instanceof BufferAllocation) {
            return handleBufferAllocation((BufferAllocation) message);
        }
        logger.warn("invalid allocation type: " + message.getClass().getSimpleName());
        return null;
    }

    @Override
    protected List<? extends ResourceMessage> startRegistration(AdvancedBatteryState state) {
        lock.lock();
        try {
            this.state = state;
            var now = Date.from(OffsetDateTime.now().toInstant());
            // safe current state of the battery

            // ---- Buffer registration ----
            // create a battery actuator for electricity
            batteryCharger = new Actuator(BATTERY_CHARGER_ACTUATOR_ID, "Battery Charger 1",
                                          CommoditySet.onlyElectricity
            );
            // create a buffer registration message
            batteryBufferRegistration = new BufferRegistration<>(
                    config.resourceId(),
                    now,
                    Measure.zero(SI.SECOND),
                    "Battery state of charge in percent",
                    NonSI.PERCENT,
                    Collections.singleton(batteryCharger)
            );

            ActuatorBehaviour batteryActuatorBehaviour = createBatteryActuatorBehaviour(batteryCharger.getActuatorId());
            FillLevelFunction<LeakageRate> bufferLeakageFunction = FillLevelFunction.<LeakageRate>create(0d)
                                                                                    .add(100d, new LeakageRate(0))
                                                                                    .build();
            Set<ActuatorBehaviour> actuatorsBehaviours = new HashSet<ActuatorBehaviour>();
            actuatorsBehaviours.add(batteryActuatorBehaviour);
            BufferSystemDescription sysDescr = new BufferSystemDescription(batteryBufferRegistration, now, now,
                                                                           actuatorsBehaviours, bufferLeakageFunction
            );
            BiddingStrategyDefinition biddingStrategyDef = createBiddingStrategyDefinition(
                    batteryBufferRegistration,
                    now
            );
            BufferStateUpdate<Dimensionless> update = createBufferStateUpdate(now);

            logger.debug("Battery manager start registration completed.");
            return List.of(batteryBufferRegistration, sysDescr, biddingStrategyDef, update);
        } finally {
            lock.unlock();
        }
    }

    private double getCappedFillLevel() {
        return Math.max(
                Math.min(state.getStateOfCharge(), state.maximumFillLevelPercent()),
                state.minimumFillLevelPercent()
        );
    }

    private BiddingStrategyDefinition createBiddingStrategyDefinition(BufferRegistration<?> bufferRegistration,
                                                                      Date now) {
        BiddingStrategy strategy = BasicPriorityBiddingStrategy.builder()
                                                               .priorityCalculationFunction(
                                                                       (soc) -> tan(1 - 2 * soc) / tan(1.0)
                                                               )
                                                               .build();
        return new BiddingStrategyDefinition(bufferRegistration, now, now, strategy);
    }

    @Override
    protected List<? extends ResourceMessage> updatedState(AdvancedBatteryState state) {
        lock.lock();
        try {
            this.state = state;
            BufferStateUpdate<Dimensionless> update =
                    createBufferStateUpdate(Date.from(OffsetDateTime.now().toInstant()));
            return List.of(update);
        } finally {
            lock.unlock();
        }
    }
}
