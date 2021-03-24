package ru.agentlab.semantic.powermatcher.examples.heater;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;
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
import ru.agentlab.semantic.wot.observation.api.Action;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observations.*;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;

import javax.measure.unit.SI;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_INPUT;

public class HeaterProvider {

    private final Logger logger = LoggerFactory.getLogger(HeaterProvider.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SailRepository repository;
    private final List<Disposable> subscriptions = new CopyOnWriteArrayList<>();

    public static class HeaterState {
        public HeaterState(HeaterSimulationModel model,
                           ThingPropertyAffordance power,
                           ThingPropertyAffordance indoor,
                           ThingPropertyAffordance outdoor,
                           ThingActionAffordance setPowerAffordance) {
            this.model = model;
            this.power = power;
            this.indoor = indoor;
            this.outdoor = outdoor;
            this.setPowerAffordance = setPowerAffordance;
        }

        public HeaterSimulationModel getModel() {
            return model;
        }

        public ThingPropertyAffordance getPower() {
            return power;
        }

        public ThingPropertyAffordance getIndoor() {
            return indoor;
        }

        public ThingPropertyAffordance getOutdoor() {
            return outdoor;
        }

        public ThingActionAffordance getSetPowerAffordance() {
            return setPowerAffordance;
        }

        private final HeaterSimulationModel model;
        private final ThingPropertyAffordance power;
        private final ThingPropertyAffordance indoor;
        private final ThingPropertyAffordance outdoor;
        private final ThingActionAffordance setPowerAffordance;

    }

    @Reference
    public void bindSailRepository(SailRepository repository) {
        this.repository = repository;
    }

    @Activate
    public void activate(HeaterSimulationConfig config) {
        var interval = Duration.ofMillis(config.updateFrequency());
        var repoConn = repository.getConnection();
        var sailConn = (ChangeTrackerConnection) repoConn.getSailConnection();
        var context = new ConnectionContext(executor, repoConn);

        Disposable outer = populateThingModel(iri(config.thingIRI()), context)
                .doFinally(signal -> {
                    sailConn.close();
                    repoConn.close();
                })
                .subscribe(thing -> fetchInitialState(thing).subscribe(state -> {
                    Disposable simulation = scheduleSimulation(state.getIndoor(),
                                                               state.getModel(),
                                                               interval
                    ).subscribe();
                    var setPowerActionIRI = state.getSetPowerAffordance().getPropertyAffordanceIRI();
                    var setPowerInvocationFilter = createObservationFilterForAction(setPowerActionIRI);
                    Disposable stateUpdate = sailConn.events(Schedulers.fromExecutorService(executor))
                            .flatMap(changes -> extractLatestInvocation(changes, setPowerInvocationFilter))
                            .subscribe(actionInvocation -> {
                                state.getModel().setHeatingPower(actionInvocation.getInput());
                            });
                    subscriptions.addAll(List.of(simulation, stateUpdate));
                }));
        subscriptions.add(outer);
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

    private Flux<Void> scheduleSimulation(ThingPropertyAffordance temperatureAffordance, HeaterSimulationModel state, Duration interval) {
        return Flux.interval(interval, Schedulers.fromExecutor(temperatureAffordance.getContext().getExecutor()))
                .doOnNext(tick -> {
                    logger.info("running for {} seconds", tick * interval.toSeconds());
                    state.calculate(interval);

                }).flatMap(tick -> publishNewState(temperatureAffordance, state));
    }

    private Mono<Void> publishNewState(ThingPropertyAffordance affordance, HeaterSimulationModel state) {
        var context = affordance.getContext();
        var future = CompletableFuture.runAsync(() -> {
            var conn = context.getConnection();
            logger.info("publishing new state={}", state);
            IRI obsId = iri("https://example.agentlab.ru#" + UUID.randomUUID().toString());
            OffsetDateTime now = OffsetDateTime.now();
            String time = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            conn.begin();
            conn.add(obsId, RDF.TYPE, PROPERTY_STATE);
            conn.add(obsId, DESCRIBED_BY_AFFORDANCE, affordance.getIRI());
            conn.add(obsId, HAS_VALUE, Values.literal(state.getCurrentIndoorTemperature().doubleValue(SI.CELSIUS)));
            conn.add(obsId, MODIFIED, Values.literal(time, XSD.DATETIME));
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

    private Mono<HeaterState> fetchInitialState(Thing thing) {
        IRI powerAffordanceIRI = iri("https://example.agentlab.ru/#Heater_1_PowerDemand");
        IRI outsideTemperatureIRI = iri("https://example.agentlab.ru/#Heater_1_OutdoorTemperature");
        IRI insideTemperatureIRI = iri("https://example.agentlab.ru/#Heater_1_IndoorTemperature");
        IRI setPowerActionIRI = iri("https://example.agentlab.ru/#Heater_1_SetHeatingPowerAction");
        ThingPropertyAffordance powerAffordance = thing.getPropertyAffordance(powerAffordanceIRI);
        ThingPropertyAffordance outsideTemperature = thing.getPropertyAffordance(outsideTemperatureIRI);
        ThingPropertyAffordance insideTemperature = thing.getPropertyAffordance(insideTemperatureIRI);
        ThingActionAffordance actionAffordance = thing.getActionAffordance(setPowerActionIRI);

        Building building = fetchLocationBuilding(thing);
        return Mono.zip((affordances) -> {
            FloatObservation<DefaultObservationMetadata>[] typedAffordances = (FloatObservation<DefaultObservationMetadata>[]) affordances;
            var initialIndoorTemperature = typedAffordances[2].getValue();
            var initialOutdoorTemperature = typedAffordances[1].getValue();
            var initialHeatingPower = typedAffordances[0].getValue();
            var simulationModel = new HeaterSimulationModel(building,
                                                            initialHeatingPower,
                                                            initialOutdoorTemperature,
                                                            initialIndoorTemperature
            );

            return new HeaterState(simulationModel,
                                   powerAffordance,
                                   insideTemperature,
                                   outsideTemperature,
                                   actionAffordance
            );
        }, fromAffordance(powerAffordance), fromAffordance(outsideTemperature), fromAffordance(insideTemperature));
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
        subscriptions.forEach(Disposable::dispose);
        executor.shutdown();
    }

}
