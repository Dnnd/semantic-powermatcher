package ru.agentlab.semantic.powermatcher.battery;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.Literals;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import ru.agentlab.semantic.wot.actions.FloatSetter;
import ru.agentlab.semantic.wot.api.Action;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.api.ObservationFactory;
import ru.agentlab.semantic.wot.observations.DefaultMetadata;
import ru.agentlab.semantic.wot.observations.DefaultMetadataParser;
import ru.agentlab.semantic.wot.observations.FloatObservationParser;
import ru.agentlab.semantic.wot.repositories.ThingActionAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;

import javax.measure.Measure;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.EXAMPLE_IRI;
import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_SIMPLE_RESULT;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.ACTION_INVOCATION;

@Component(
        immediate = true,
        service = {Endpoint.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@Port(
        name = "manager",
        sends = AdvancedBatteryState.class,
        accepts = AdvancedBatteryControlParameters.class)
@Designate(
        ocd = BatteryDriver.Config.class,
        factory = true)
public class BatteryDriver extends AbstractResourceDriver<AdvancedBatteryState, AdvancedBatteryControlParameters> {
    private SailRepository repository;

    private final ObservationFactory<Float, DefaultMetadata> floatObservationsFactory;
    private Disposable onStateUpdateSubscription;
    private Sinks.Many<Double> setPowerSink;
    private Disposable onControlParametersReceived;
    private ConnectionContext context;
    private ThingPropertyAffordanceRepository propertyAffordances;
    private ThingActionAffordanceRepository actionAffordances;

    private final static Logger logger = LoggerFactory.getLogger(BatteryDriver.class);

    public BatteryDriver() {
        floatObservationsFactory = (obsIri) -> new FloatObservationParser<>(new DefaultMetadataParser(
                obsIri));
    }


    @ObjectClassDefinition(name = "Battery Semantic Driver")
    public @interface Config {
        String thingIRI();

        String stateContext();
    }

    @Reference
    public void bindSailRepositoryProvider(SailRepositoryProvider repositoryProvider) {
        this.repository = repositoryProvider.getRepository();
    }

    @Activate
    public void activate(Config config) {
        setPowerSink = Sinks.many().unicast().onBackpressureBuffer();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var repoConn = repository.getConnection();
        context = new ConnectionContext(executor, repoConn);
        var thingIRI = iri(config.thingIRI());
        var things = new ThingRepository(context);
        propertyAffordances = new ThingPropertyAffordanceRepository(context);
        actionAffordances = new ThingActionAffordanceRepository(context);

        var thingMono = things.getThing(thingIRI).cache();
        var stateContext = iri(config.stateContext());
        onControlParametersReceived = thingMono.flatMap(thing -> actionAffordances.byDescription(
                thing,
                iri(EXAMPLE_IRI, "GenericSetBatteryPower")
        )).flatMapMany(actionAffordance -> setPowerSink.asFlux().doOnNext(powerToSet -> {
            var setter = serializeFloatSetter(actionAffordance, powerToSet);
            repoConn.add(setter, stateContext);
            logger.info("invoked power setting action={}", setter);
        })).subscribe();

        onStateUpdateSubscription = thingMono.flatMapMany(this::subscribeOnBatteryStateUpdate)
                                             .subscribe(this::publishState);
    }

    @Deactivate
    public void deactivate() {
        onStateUpdateSubscription.dispose();
        onControlParametersReceived.dispose();
        try {
            setPowerSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        } catch (Exception e) {
            logger.error("unable to complete set power sink", e);
        }
        context.getExecutor().shutdown();
        context.getConnection().close();
    }

    @Override
    protected void handleControlParameters(AdvancedBatteryControlParameters batteryControlParameters) {
        var targetPowerInWatts = batteryControlParameters.getDesiredPower();
        try {
            setPowerSink.emitNext(targetPowerInWatts.doubleValue(SI.WATT), Sinks.EmitFailureHandler.FAIL_FAST);
        } catch (Exception e) {
            logger.error("unable to send control parameters", e);
        }
    }

    private Flux<AdvancedBatteryState> subscribeOnBatteryStateUpdate(Thing battery) {

        return propertyAffordances.getPropertyAffordancesWithType(battery, STATE_OF_CHARGE, POWER)
                                  .collectList()
                                  .flatMapMany(affordances -> {
                                      var power = affordances.stream()
                                                             .filter(affordance -> affordance.hasType(POWER))
                                                             .findFirst()
                                                             .orElseThrow();

                                      var stateOfCharge = affordances.stream()
                                                                     .filter(affordance -> affordance.hasType(
                                                                             STATE_OF_CHARGE))
                                                                     .findFirst()
                                                                     .orElseThrow();

                                      return subscribeOnBatteryStateUpdate(battery, power, stateOfCharge);
                                  });
    }

    private Flux<AdvancedBatteryState> subscribeOnBatteryStateUpdate(Thing battery,
                                                                     ThingPropertyAffordance stateOfCharge,
                                                                     ThingPropertyAffordance power) {
        var lastModifiedComparator = Comparator.comparing(
                (Observation<Float, DefaultMetadata> obs) -> obs.getMetadata().getLastModified()
        );

        var maxCapacity = battery.getProperty(TOTAL_CAPACITY)
                                 .map(v -> Literals.getDoubleValue(v, -1))
                                 .map(capacity -> Measure.valueOf(capacity, NonSI.KWH))
                                 .orElseThrow();

        var powerUpdates = propertyAffordances
                .latestObservation(power, floatObservationsFactory)
                .concatWith(propertyAffordances.subscribeOnLatestObservations(
                        power,
                        HAS_SIMPLE_RESULT,
                        floatObservationsFactory,
                        lastModifiedComparator
                ));

        var minLevel = battery.getProperty(CHARGE_LEVEL_MIN)
                              .map(v -> Literals.getDoubleValue(v, -1))
                              .orElseThrow();

        var maxLevel = battery.getProperty(CHARGE_LEVEL_MAX)
                              .map(v -> Literals.getDoubleValue(v, -1))
                              .orElseThrow();

        var chargeRateMax = battery.getProperty(CHARGING_RATE_MAX)
                                   .map(v -> Literals.getDoubleValue(v, -1))
                                   .orElseThrow();

        var numberOfModulationSteps = battery.getProperty(MODULATION_STEPS)
                                             .map(v -> Literals.getIntValue(v, -1))
                                             .orElseThrow();

        return propertyAffordances.subscribeOnLatestObservations(
                stateOfCharge,
                HAS_SIMPLE_RESULT,
                floatObservationsFactory,
                lastModifiedComparator
        ).withLatestFrom(
                powerUpdates,
                (stateOfChargeObservation, powerObservation) -> new SimpleBatteryState(
                        stateOfChargeObservation.getValue(),
                        Measure.valueOf(powerObservation.getValue(), SI.WATT),
                        maxCapacity,
                        minLevel,
                        maxLevel,
                        chargeRateMax,
                        chargeRateMax,
                        numberOfModulationSteps
                )
        );
    }

    private Model serializeFloatSetter(ThingActionAffordance affordance, double value) {
        IRI actionInvocation = iri(EXAMPLE_IRI, UUID.randomUUID().toString());
        Action<Float, Void, DefaultMetadata> setter = new FloatSetter<>((float) value);
        OffsetDateTime now = OffsetDateTime.now();
        setter.setMetadata(new DefaultMetadata(
                affordance.getIRI(),
                actionInvocation,
                affordance.getThingIRI(),
                now,
                ACTION_INVOCATION
        ));
        return setter.toModel();
    }
}
