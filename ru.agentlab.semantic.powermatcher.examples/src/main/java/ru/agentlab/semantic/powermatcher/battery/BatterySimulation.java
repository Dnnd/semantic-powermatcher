package ru.agentlab.semantic.powermatcher.battery;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.util.Literals;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.agentlab.semantic.wot.actions.FloatSetterParser;
import ru.agentlab.semantic.wot.api.ObservationFactory;
import ru.agentlab.semantic.wot.observations.DefaultMetadata;
import ru.agentlab.semantic.wot.observations.DefaultMetadataParser;
import ru.agentlab.semantic.wot.observations.FloatObservation;
import ru.agentlab.semantic.wot.observations.FloatObservationParser;
import ru.agentlab.semantic.wot.repositories.ThingActionAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;
import ru.agentlab.semantic.wot.utils.Utils;

import javax.measure.Measure;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.examples.Utils.openResourceStream;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.wot.vocabularies.SSN.OBSERVATION;

@Component(
        service = {BatterySimulation.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true
)
@Designate(ocd = BatterySimulation.BatterySimulationConfig.class)
public class BatterySimulation {

    private final Logger logger = LoggerFactory.getLogger(BatterySimulation.class);

    private SailRepository repository;
    private Disposable subscription;
    private ConnectionContext context;

    @Reference
    public void bindSailRepository(SailRepositoryProvider repositoryProvider) {
        this.repository = repositoryProvider.getRepository();
    }

    @ObjectClassDefinition(name = "Battery Simulation")
    public @interface BatterySimulationConfig {
        @AttributeDefinition(description = "Battery Thing IRI")
        String thingIRI();

        @AttributeDefinition(description = "Graph context for observations")
        String stateContext();

        @AttributeDefinition(description = "Frequency with which updates will be sent out in milliseconds")
        int updateFrequency() default 1000;

        @AttributeDefinition(description = "Graph context for things")
        String thingContext();
    }

    @Activate
    public void activate(BatterySimulationConfig config) {
        logger.info(
                "Activating with stateContext={}, thingsContext={}, updateFrequency={}, thingIRI={}",
                config.stateContext(),
                config.thingContext(),
                config.updateFrequency(),
                config.thingIRI()
        );
        ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        var interval = Duration.ofMillis(config.updateFrequency());
        var repoConn = repository.getConnection();
        context = new ConnectionContext(executor, repoConn);
        ThingRepository things = new ThingRepository(context);
        var propertyAffordances = new ThingPropertyAffordanceRepository(context);
        var actionAffordances = new ThingActionAffordanceRepository(context);
        var observationsContext = iri(config.stateContext());
        var modelContext = iri(config.thingContext());

        subscription = populateThingModel(context, observationsContext, modelContext)
                .then(things.getThing(iri(config.thingIRI())))
                .flatMap(thing -> fetchInitialState(thing, propertyAffordances, actionAffordances))
                .flatMapMany(state -> scheduleSimulation(
                        context,
                        actionAffordances,
                        state,
                        interval,
                        observationsContext
                ))
                .doFinally(signal -> context.close())
                .subscribe();
    }


    private Flux<Void> scheduleSimulation(ConnectionContext context,
                                          ThingActionAffordanceRepository actionAffordances,
                                          SimpleBatteryTwin state,
                                          Duration interval,
                                          Resource... stateContext) {
        logger.info("Launching simulation...");
        return Flux.interval(interval, Schedulers.fromExecutor(context.getExecutor()))
                   .flatMap(tick -> {
                       logger.info("battery simulation tick {}", tick);
                       ThingActionAffordance setPowerAffordance = state.getSetPowerAffordance();
                       var metadataBuilder = new DefaultMetadataParser(setPowerAffordance.getIRI());
                       var actionBuilder = new FloatSetterParser<>(metadataBuilder);

                       var latestInvocation = actionAffordances.latestInvocation(
                               setPowerAffordance.getIRI(),
                               actionBuilder
                       );
                       return latestInvocation.doOnNext(setPowerCommand -> {
                           logger.info("updating battery model state, power={}", setPowerCommand.getInput());
                           var model = state.getModel();
                           model.setRunningMode(Measure.valueOf(setPowerCommand.getInput(), SI.WATT));
                           model.calculate(interval);
                       }).then(publishNewState(context, state, stateContext));
                   });
    }

    private Model makeFloatObservation(ThingPropertyAffordance affordance, double value, Resource... obsContext) {
        IRI observationIRI = iri(EXAMPLE_IRI, UUID.randomUUID().toString());
        var obs = new FloatObservation<DefaultMetadata>((float) value);
        obs.setMetadata(new DefaultMetadata(
                                affordance.getIRI(),
                                observationIRI,
                                affordance.getThingIRI(),
                                OffsetDateTime.now(),
                                OBSERVATION
                        )
        );
        return obs.toModel(obsContext);
    }

    private Mono<Void> publishNewState(ConnectionContext context,
                                       SimpleBatteryTwin state,
                                       Resource... stateContext) {
        return Utils.supplyAsyncWithCancel(
                () -> publishNewStateSync(context, state, stateContext),
                context.getExecutor()
        );
    }

    private Mono<Void> populateThingModel(ConnectionContext context, Resource obsContext, Resource modelContext) {
        return Utils.supplyAsyncWithCancel(
                () -> populateThingModelSync(context, obsContext, modelContext),
                context.getExecutor()
        );
    }

    private void publishNewStateSync(ConnectionContext context, SimpleBatteryTwin state, Resource... stateContext) {
        var conn = context.getConnection();
        logger.info("publishing new state={}", state);
        Model stateOfChargeObservation = makeFloatObservation(
                state.getStateOfCharge(),
                state.getModel().getCurrentFillLevel(),
                stateContext
        );
        Model outputPowerObservation = makeFloatObservation(
                state.getPower(),
                state.getModel().getElectricPower().doubleValue(SI.WATT),
                stateContext
        );
        conn.begin();
        conn.add(stateOfChargeObservation);
        conn.add(outputPowerObservation);
        conn.commit();
    }

    private void populateThingModelSync(ConnectionContext context, Resource obsContext, Resource modelContext) {
        try (var conn = context.createConnection()) {
            conn.begin();
            try (var initialObservations = openResourceStream("battery_initial_observations.ttl");
                 var battery = openResourceStream("battery_model.ttl")) {
                var obsModel = Rio.parse(initialObservations, RDFFormat.TURTLE);
                var batteryModel = Rio.parse(battery, RDFFormat.TURTLE);
                conn.add(obsModel, obsContext);
                conn.add(batteryModel, modelContext);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                logger.error("unable to populate thing model", e);
                return;
            }
            logger.info("Thing Model successfully populated...");
        }
    }

    private Mono<SimpleBatteryTwin> fetchInitialState(Thing thing,
                                                      ThingPropertyAffordanceRepository properties,
                                                      ThingActionAffordanceRepository actions) {
        IRI powerAffordanceIRI = iri(EXAMPLE_IRI, "Battery_1_Power");
        IRI stateOfChargeAffordanceIRI = iri(EXAMPLE_IRI, "Battery_1_StateOfCharge");
        IRI setPowerActionIRI = iri(EXAMPLE_IRI, "Battery_1_SetBatteryPowerAction");

        var powerAffordanceMono = properties.getThingPropertyAffordance(
                thing,
                powerAffordanceIRI
        );
        var stateOfChargeMono = properties.getThingPropertyAffordance(
                thing,
                stateOfChargeAffordanceIRI
        );
        var actionAffordanceMono = actions.getActionAffordance(
                thing,
                setPowerActionIRI
        );

        ObservationFactory<Float, DefaultMetadata> obsFactory = (obsIRI) -> {
            var metadataBuilder = new DefaultMetadataParser(obsIRI);
            return new FloatObservationParser<>(metadataBuilder);
        };

        return Mono.zip(powerAffordanceMono, stateOfChargeMono, actionAffordanceMono)
                   .flatMap(affordances -> {
                       var powerAffordance = affordances.getT1();
                       var stateOfChargeAffordance = affordances.getT2();
                       var setPowerAffordance = affordances.getT3();

                       return Mono.zip(
                               (observations) -> {
                                   var initialPower =
                                           ((FloatObservation<DefaultMetadata>) observations[0]).getValue();
                                   var initialStateOfCharge =
                                           ((FloatObservation<DefaultMetadata>) observations[1]).getValue();

                                   var simulationModel = makeSimpleBatteryModel(thing, initialStateOfCharge);
                                   simulationModel.setRunningMode(Measure.valueOf(initialPower, SI.WATT));

                                   return new SimpleBatteryTwin(
                                           simulationModel,
                                           powerAffordance,
                                           stateOfChargeAffordance,
                                           setPowerAffordance
                                   );
                               },
                               properties.latestObservation(powerAffordance, obsFactory),
                               properties.latestObservation(stateOfChargeAffordance, obsFactory)
                       );
                   });
    }


    private SimpleBatteryModel makeSimpleBatteryModel(Thing battery, double stateOfCharge) {

        double totalCapacity = battery.getProperty(TOTAL_CAPACITY)
                                      .map(v -> Literals.getDoubleValue(v, -1))
                                      .orElseThrow();

        return new SimpleBatteryModel(
                Measure.zero(SI.WATT),
                Measure.zero(SI.WATT),
                Measure.zero(SI.WATT),
                Measure.valueOf(totalCapacity, NonSI.KWH),
                OffsetDateTime.now(),
                stateOfCharge,
                Measure.valueOf(100, NonSI.PERCENT),
                Measure.valueOf(20, NonSI.PERCENT)
        );
    }

    @Deactivate
    public void deactivate() {
        repository = null;
        subscription.dispose();
    }
}
