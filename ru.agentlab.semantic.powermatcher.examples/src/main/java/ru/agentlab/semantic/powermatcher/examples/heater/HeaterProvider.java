package ru.agentlab.semantic.powermatcher.examples.heater;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.agentlab.semantic.powermatcher.examples.uncontrolled.SailRepositoryProvider;
import ru.agentlab.semantic.wot.actions.FloatSetterParser;
import ru.agentlab.semantic.wot.api.ObservationFactory;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.observations.DefaultMetadata;
import ru.agentlab.semantic.wot.observations.DefaultMetadataParser;
import ru.agentlab.semantic.wot.observations.FloatObservation;
import ru.agentlab.semantic.wot.observations.FloatObservationParser;
import ru.agentlab.semantic.wot.repositories.ThingActionAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;
import ru.agentlab.semantic.wot.utils.Utils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.examples.Utils.openResourceStream;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.wot.vocabularies.SSN.*;

@Component(
        service = {HeaterProvider.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true
)
@Designate(ocd = HeaterSimulationConfig.class)
public class HeaterProvider {

    private final Logger logger = LoggerFactory.getLogger(HeaterProvider.class);

    private SailRepository repository;
    private Disposable subscription;
    private ConnectionContext context;

    @Reference
    public void bindSailRepository(SailRepositoryProvider repositoryProvider) {
        this.repository = repositoryProvider.getRepository();
    }

    @Activate
    public void activate(HeaterSimulationConfig config) {
        logger.info("Activating with stateContext={}, thingsContext={}, updateFrequency={}, thingIRI={}",
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
                .flatMapMany(state -> scheduleSimulation(context,
                                                         actionAffordances,
                                                         state,
                                                         interval,
                                                         observationsContext
                ))
                .doFinally(signal -> context.close())
                .subscribe();
    }


    private Building fetchLocationBuilding(Thing building) {
        return building.getProperty(LENGTH)
                .map(Value::stringValue)
                .map(Float::parseFloat)
                .flatMap(len -> building.getProperty(HEIGHT)
                        .map(Value::stringValue)
                        .map(Float::parseFloat)
                        .flatMap(height -> building.getProperty(WIDTH)
                                .map(Value::stringValue)
                                .map(Float::parseFloat)
                                .map(width -> new Building(len, width, height))
                        )
                ).orElseThrow();
    }

    private Flux<Void> scheduleSimulation(ConnectionContext context,
                                          ThingActionAffordanceRepository actionAffordances,
                                          HeaterSimulationTwin state,
                                          Duration interval,
                                          Resource... stateContext) {
        logger.info("Launching simulation...");
        return Flux.interval(interval, Schedulers.fromExecutor(context.getExecutor()))
                .flatMap(tick -> {
                    logger.info("heater simulation tick {}", tick);
                    ThingActionAffordance setPowerAffordance = state.getSetPowerAffordance();
                    var metadataBuilder = new DefaultMetadataParser(setPowerAffordance.getIRI());
                    var actionBuilder = new FloatSetterParser<>(metadataBuilder);

                    var latestInvocation = actionAffordances.latestInvocation(
                            setPowerAffordance.getIRI(),
                            actionBuilder
                    );
                    return latestInvocation.doOnNext(setPowerCommand -> {
                        logger.info("updating heater model state, power={}", setPowerCommand.getInput());
                        var model = state.getModel();
                        model.setHeatingPower(setPowerCommand.getInput());
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

    private Mono<Void> publishNewState(ConnectionContext context, HeaterSimulationTwin state, Resource... stateContext) {
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

    private void publishNewStateSync(ConnectionContext context, HeaterSimulationTwin state, Resource... stateContext) {
        var conn = context.getConnection();
        logger.info("publishing new state={}", state);
        Model indoorTemperatureObservation = makeFloatObservation(
                state.getIndoor(),
                state.getModel().getIndoorTemperature(),
                stateContext
        );
        Model outdoorTemperatureObservation = makeFloatObservation(
                state.getOutdoor(),
                state.getModel().getOutdoorTemperature(),
                stateContext
        );
        Model heatingPowerObservation = makeFloatObservation(
                state.getPower(),
                state.getModel().getHeatingPower(),
                stateContext
        );
        conn.begin();
        conn.add(indoorTemperatureObservation);
        conn.add(outdoorTemperatureObservation);
        conn.add(heatingPowerObservation);
        conn.commit();
    }

    private void populateThingModelSync(ConnectionContext context, Resource obsContext, Resource modelContext) {
        // use separate connection for multistage transactions
        try (var conn = context.createConnection()) {
            conn.begin();
            try (var initialObservations = openResourceStream("heater_initial_observations.ttl");
                 var heaterThing = openResourceStream("heater_model.ttl")) {
                var obsModel = Rio.parse(initialObservations, RDFFormat.TURTLE);
                var heaterModel = Rio.parse(heaterThing, RDFFormat.TURTLE);
                conn.add(obsModel, obsContext);
                conn.add(heaterModel, modelContext);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                logger.error("unable to populate thing model", e);
                return;
            }
            logger.info("Thing Model successfully populated...");
        }
    }

    private Mono<HeaterSimulationTwin> fetchInitialState(Thing thing,
                                                         ThingPropertyAffordanceRepository properties,
                                                         ThingActionAffordanceRepository actions) {
        IRI powerAffordanceIRI = iri(EXAMPLE_IRI, "Heater_1_PowerDemand");
        IRI outsideTemperatureIRI = iri(EXAMPLE_IRI, "Heater_1_OutdoorTemperature");
        IRI insideTemperatureIRI = iri(EXAMPLE_IRI, "Heater_1_IndoorTemperature");
        IRI setPowerActionIRI = iri(EXAMPLE_IRI, "Heater_1_SetHeatingPowerAction");

        var powerAffordanceMono = properties.getThingPropertyAffordance(
                thing,
                powerAffordanceIRI
        );
        var outsideTemperatureMono = properties.getThingPropertyAffordance(
                thing,
                outsideTemperatureIRI
        );
        var insideTemperatureMono = properties.getThingPropertyAffordance(
                thing,
                insideTemperatureIRI
        );
        var actionAffordanceMono = actions.getActionAffordance(
                thing,
                setPowerActionIRI
        );

        ObservationFactory<Float, DefaultMetadata> obsFactory = (obsIRI) -> {
            var metadataBuilder = new DefaultMetadataParser(obsIRI);
            return new FloatObservationParser<>(metadataBuilder);
        };

        Building building = fetchLocationBuilding(thing);
        return Mono.zip(powerAffordanceMono, outsideTemperatureMono, insideTemperatureMono, actionAffordanceMono)
                .flatMap(affordances -> {
                    var powerAffordance = affordances.getT1();
                    var outsideTemperatureAffordance = affordances.getT2();
                    var insideTemperatureAffordance = affordances.getT3();
                    var powerSetter = affordances.getT4();

                    return Mono.zip((observations) -> {
                                        var initialHeatingPower = ((FloatObservation<DefaultMetadata>) observations[0]).getValue();
                                        var initialOutdoorTemperature = ((FloatObservation<DefaultMetadata>) observations[1]).getValue();
                                        var initialIndoorTemperature = ((FloatObservation<DefaultMetadata>) observations[2]).getValue();
                                        var simulationModel = new HeaterSimulationModel(building,
                                                                                        initialHeatingPower,
                                                                                        initialOutdoorTemperature,
                                                                                        initialIndoorTemperature
                                        );

                                        return new HeaterSimulationTwin(simulationModel,
                                                                        powerAffordance,
                                                                        insideTemperatureAffordance,
                                                                        outsideTemperatureAffordance,
                                                                        powerSetter
                                        );
                                    },
                                    properties.latestObservation(powerAffordance, obsFactory),
                                    properties.latestObservation(outsideTemperatureAffordance, obsFactory),
                                    properties.latestObservation(insideTemperatureAffordance, obsFactory)
                    );
                });
    }

    @Deactivate
    public void deactivate() {
        repository = null;
        subscription.dispose();
    }

}
