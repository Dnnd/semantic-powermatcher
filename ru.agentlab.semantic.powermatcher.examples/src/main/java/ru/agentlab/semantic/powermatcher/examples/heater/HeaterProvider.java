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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
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
import ru.agentlab.semantic.wot.observation.api.Action;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observations.*;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_INPUT;

public class HeaterProvider {

    private final Logger logger = LoggerFactory.getLogger(HeaterProvider.class);
    private final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private SailRepository repository;
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

        subscription = populateThingModel(iri(config.thingIRI()), context)
                .flatMap(this::fetchInitialState)
                .flatMapMany(state -> scheduleSimulation(state, interval))
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

    private Flux<Void> scheduleSimulation(HeaterSimulationTwin state, Duration interval) {
        return Flux.interval(interval, Schedulers.fromExecutor(state.getIndoor().getContext().getExecutor()))
                .flatMap(tick -> {
                    ThingActionAffordance setPowerAffordance = state.getSetPowerAffordance();
                    var metadataBuilder = new DefaultActionMetadataBuilder(setPowerAffordance.getIRI());
                    var actionBuilder = new FloatSetterBuilder<>(metadataBuilder);

                    var latestInvocation = setPowerAffordance.latestInvocation(actionBuilder);
                    return latestInvocation.doOnNext(setPowerCommand -> {
                        logger.info("updating heater model state, power={}", setPowerCommand.getInput());
                        var model = state.getModel();
                        model.setHeatingPower(setPowerCommand.getInput());
                    }).then(publishNewState(state));
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

    private Mono<Void> publishNewState(HeaterSimulationTwin state) {
        var context = state.getIndoor().getContext();
        var future = CompletableFuture.runAsync(() -> {
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
        }, context.getExecutor());
        return Mono.fromFuture(future);
    }

    private Mono<Thing> populateThingModel(IRI thingIRI, ConnectionContext context) {
        var future = CompletableFuture.supplyAsync(() -> {
            try (var uncontrolledTTL = getClass().getClassLoader().getResourceAsStream("heater.ttl")) {
                var model = Rio.parse(uncontrolledTTL, RDFFormat.TURTLE);
                context.getConnection().add(model);
            } catch (IOException exception) {
                logger.error("unable to populate thing model", exception);
            }
            return Thing.of(thingIRI, context);
        }, context.getExecutor());
        return Mono.fromFuture(future);
    }

    private Mono<HeaterSimulationTwin> fetchInitialState(Thing thing) {
        IRI powerAffordanceIRI = iri("https://example.agentlab.ru/#Heater_1_PowerDemand");
        IRI outsideTemperatureIRI = iri("https://example.agentlab.ru/#Heater_1_OutdoorTemperature");
        IRI insideTemperatureIRI = iri("https://example.agentlab.ru/#Heater_1_IndoorTemperature");
        IRI setPowerActionIRI = iri("https://example.agentlab.ru/#Heater_1_SetHeatingPowerAction");
        var powerAffordanceMono = thing.getPropertyAffordance(powerAffordanceIRI);
        var outsideTemperatureMono = thing.getPropertyAffordance(outsideTemperatureIRI);
        var insideTemperatureMono = thing.getPropertyAffordance(insideTemperatureIRI);
        var actionAffordanceMono = thing.getActionAffordance(setPowerActionIRI);

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
        return affordance.latestObservation(observationBuilder);
    }

    @Deactivate
    public void deactivate() {
        repository = null;
        subscription.dispose();
        executor.shutdown();
    }

}
