package ru.agentlab.semantic.powermatcher.examples.heater;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
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
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.filter.Transformations;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.changetracking.sail.TransactionChanges;
import ru.agentlab.semantic.powermatcher.examples.uncontrolled.SailRepositoryProvider;
import ru.agentlab.semantic.wot.actions.FloatSetterBuilder;
import ru.agentlab.semantic.wot.observation.api.Action;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observations.*;
import ru.agentlab.semantic.wot.repositories.ThingActionAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;
import ru.agentlab.semantic.wot.utils.Utils;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_INPUT;

@Component(
        service = {HeaterProvider.class},
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true
)
@Designate(ocd = HeaterSimulationConfig.class)
public class HeaterProvider {

    private final Logger logger = LoggerFactory.getLogger(HeaterProvider.class);
    private final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private SailRepository repository;
    private ThingRepository things;
    private ThingPropertyAffordanceRepository propertyAffordances;
    private ThingActionAffordanceRepository actionAffordances;
    private Disposable subscription;

    @Reference
    public void bindSailRepository(SailRepositoryProvider repositoryProvider) {
        this.repository = repositoryProvider.getRepository();
    }

    @Activate
    public void activate(HeaterSimulationConfig config) {
        var interval = Duration.ofMillis(config.updateFrequency());
        var repoConn = repository.getConnection();
        var sailConn = (ChangeTrackerConnection) repoConn.getSailConnection();
        var context = new ConnectionContext(executor, repoConn);
        things = new ThingRepository(context);
        propertyAffordances = new ThingPropertyAffordanceRepository(context);
        actionAffordances = new ThingActionAffordanceRepository(context);

        subscription = populateThingModel(context)
                .then(things.getThing(iri(config.thingIRI())))
                .flatMap(this::fetchInitialState)
                .flatMapMany(state -> scheduleSimulation(context, state, interval))
                .doFinally(signal -> {
                    sailConn.close();
                    repoConn.close();
                })
                .subscribe();
    }

    private Mono<Action<Float, Void, DefaultActionMetadata>> extractLatestInvocation(TransactionChanges changes, ChangetrackingFilter setPowerInvocationFilter) {
        Map<IRI, Model> modelsBySubject = Transformations.groupBySubject(changes.getAddedStatements());
        return modelsBySubject.entrySet()
                .stream()
                .flatMap(entity -> {
                    IRI invocationIRI = entity.getKey();
                    Model invocationModel = entity.getValue();
                    return setPowerInvocationFilter.matchModel(invocationModel)
                            .map(model -> createInvocation(invocationIRI, invocationModel))
                            .stream();
                })
                .max(Comparator.comparing(observation -> observation.getMetadata().getLastModified()))
                .map(Mono::just)
                .orElseGet(Mono::empty);
    }

    private Action<Float, Void, DefaultActionMetadata> createInvocation(IRI invocationIRI, Model invocationModel) {
        DefaultActionMetadataBuilder metadataBuilder = new DefaultActionMetadataBuilder(invocationIRI);
        FloatSetterBuilder<DefaultActionMetadata> invocationBuilder = new FloatSetterBuilder<>(metadataBuilder);
        invocationBuilder.processAll(invocationModel);
        return invocationBuilder.build();
    }

    private Building fetchLocationBuilding(Thing building) {
        var len = building.getProperty(LENGTH).map(Value::stringValue).map(Float::parseFloat).orElseThrow();
        var height = building.getProperty(HEIGHT).map(Value::stringValue).map(Float::parseFloat).orElseThrow();
        var width = building.getProperty(WIDTH).map(Value::stringValue).map(Float::parseFloat).orElseThrow();
        return new Building(len, width, height);
    }

    private Flux<Void> scheduleSimulation(ConnectionContext context, HeaterSimulationTwin state, Duration interval) {
        logger.info("Launching simulation...");
        return Flux.interval(interval, Schedulers.fromExecutor(context.getExecutor()))
                .flatMap(tick -> {
                    logger.info("heater simulation tick {}", tick);
                    ThingActionAffordance setPowerAffordance = state.getSetPowerAffordance();
                    var metadataBuilder = new DefaultActionMetadataBuilder(setPowerAffordance.getIRI());
                    var actionBuilder = new FloatSetterBuilder<>(metadataBuilder);

                    var latestInvocation = actionAffordances.latestInvocation(
                            setPowerAffordance.getIRI(),
                            actionBuilder
                    );
                    return latestInvocation.doOnNext(setPowerCommand -> {
                        logger.info("updating heater model state, power={}", setPowerCommand.getInput());
                        var model = state.getModel();
                        model.setHeatingPower(setPowerCommand.getInput());
                        model.calculate(interval);
                    }).then(publishNewState(context, state));
                });
    }

    private Model makeFloatObservation(ThingPropertyAffordance affordance, double value) {
        Model model = new LinkedHashModel();
        OffsetDateTime now = OffsetDateTime.now();
        String time = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        IRI observationIRI = iri("https://example.agentlab.ru#" + UUID.randomUUID().toString());
        model.add(observationIRI, RDF.TYPE, PROPERTY_STATE);
        model.add(observationIRI, DESCRIBED_BY_AFFORDANCE, affordance.getIRI());
        model.add(observationIRI,
                  HAS_VALUE,
                  Values.literal(value)
        );
        model.add(observationIRI, MODIFIED, Values.literal(time, XSD.DATETIME));
        return model;
    }

    private Mono<Void> publishNewState(ConnectionContext context, HeaterSimulationTwin state) {
        return Utils.supplyAsyncWithCancel(
                () -> publishNewStateSync(context, state),
                context.getExecutor()
        );
    }

    private Mono<Void> populateThingModel(ConnectionContext context) {
        return Utils.supplyAsyncWithCancel(
                () -> populateThingModelSync(context),
                context.getExecutor()
        );
    }

    private void publishNewStateSync(ConnectionContext context, HeaterSimulationTwin state) {
        var conn = context.getConnection();
        logger.info("publishing new state={}", state);
        Model indoorTemperatureObservation = makeFloatObservation(
                state.getIndoor(),
                state.getModel().getIndoorTemperature()
        );
        Model outdoorTemperatureObservation = makeFloatObservation(
                state.getIndoor(),
                state.getModel().getOutdoorTemperature()
        );
        Model heatingPowerObservation = makeFloatObservation(
                state.getIndoor(),
                state.getModel().getHeatingPower()
        );
        conn.begin();
        conn.add(indoorTemperatureObservation);
        conn.add(outdoorTemperatureObservation);
        conn.add(heatingPowerObservation);
        conn.commit();
    }

    private void populateThingModelSync(ConnectionContext context) {
        try (var uncontrolledTTL = getClass().getClassLoader().getResourceAsStream("heater.ttl")) {
            var model = Rio.parse(uncontrolledTTL, RDFFormat.TURTLE);
            context.getConnection().add(model);
        } catch (IOException exception) {
            logger.error("unable to populate thing model", exception);
        }
        logger.info("Thing Model successfully populated...");
    }

    private Mono<HeaterSimulationTwin> fetchInitialState(Thing thing) {
        IRI powerAffordanceIRI = iri("https://example.agentlab.ru/#Heater_1_PowerDemand");
        IRI outsideTemperatureIRI = iri("https://example.agentlab.ru/#Heater_1_OutdoorTemperature");
        IRI insideTemperatureIRI = iri("https://example.agentlab.ru/#Heater_1_IndoorTemperature");
        IRI setPowerActionIRI = iri("https://example.agentlab.ru/#Heater_1_SetHeatingPowerAction");
        var powerAffordanceMono = propertyAffordances.getThingPropertyAffordance(
                thing,
                powerAffordanceIRI
        );
        var outsideTemperatureMono = propertyAffordances.getThingPropertyAffordance(
                thing,
                outsideTemperatureIRI
        );
        var insideTemperatureMono = propertyAffordances.getThingPropertyAffordance(
                thing,
                insideTemperatureIRI
        );
        var actionAffordanceMono = actionAffordances.getActionAffordance(
                thing,
                setPowerActionIRI
        );

        Building building = fetchLocationBuilding(thing);
        return Mono.zip(powerAffordanceMono, outsideTemperatureMono, insideTemperatureMono, actionAffordanceMono)
                .flatMap(affordances -> {
                    var powerAffordance = affordances.getT1();
                    var outsideTemperatureAffordance = affordances.getT2();
                    var insideTemperatureAffordance = affordances.getT3();
                    var powerSetter = affordances.getT4();


                    return Mono.zip((observations) -> {
                                        var initialIndoorTemperature = ((FloatObservation<DefaultObservationMetadata>) observations[2]).getValue();
                                        var initialOutdoorTemperature = ((FloatObservation<DefaultObservationMetadata>) observations[1]).getValue();
                                        var initialHeatingPower = ((FloatObservation<DefaultObservationMetadata>) observations[0]).getValue();
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
                                    fromAffordance(powerAffordance),
                                    fromAffordance(outsideTemperatureAffordance),
                                    fromAffordance(insideTemperatureAffordance)
                    );
                });
    }

    private static ChangetrackingFilter createObservationFilterForProperty(IRI property) {
        return ChangetrackingFilter.builder()
                .addPattern(null, DESCRIBED_BY_AFFORDANCE, property, ChangetrackingFilter.Filtering.ADDED)
                .addPattern(null, HAS_VALUE, null, ChangetrackingFilter.Filtering.ADDED)
                .addPattern(null, MODIFIED, null, ChangetrackingFilter.Filtering.ADDED)
                .build();
    }

    private static ChangetrackingFilter createObservationFilterForAction(IRI action) {
        return ChangetrackingFilter.builder()
                .addPattern(null, DESCRIBED_BY_AFFORDANCE, action, ChangetrackingFilter.Filtering.ADDED)
                .addPattern(null, HAS_INPUT, null, ChangetrackingFilter.Filtering.ADDED)
                .addPattern(null, HAS_OUTPUT, null, ChangetrackingFilter.Filtering.ADDED)
                .addPattern(null, MODIFIED, null, ChangetrackingFilter.Filtering.ADDED)
                .build();
    }

    private Mono<Observation<Float, DefaultObservationMetadata>> fromAffordance(ThingPropertyAffordance affordance) {
        DefaultMetadataBuilder metadataBuilder = new DefaultMetadataBuilder();
        var observationBuilder = new FloatObservationBuilder<>(metadataBuilder);
        return propertyAffordances.latestObservation(affordance.getIRI(), observationBuilder);
    }

    @Deactivate
    public void deactivate() {
        repository = null;
        subscription.dispose();
        executor.shutdown();
    }

}
