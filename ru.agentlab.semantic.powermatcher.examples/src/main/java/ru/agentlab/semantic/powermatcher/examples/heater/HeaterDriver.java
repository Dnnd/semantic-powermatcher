package ru.agentlab.semantic.powermatcher.examples.heater;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.flexiblepower.manager.heater.api.HeaterControlParameters;
import org.flexiblepower.manager.heater.api.HeaterState;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.messaging.Port;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import ru.agentlab.semantic.powermatcher.examples.uncontrolled.SailRepositoryProvider;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observation.api.ObservationFactory;
import ru.agentlab.semantic.wot.observations.DefaultMetadataBuilder;
import ru.agentlab.semantic.wot.observations.DefaultObservationMetadata;
import ru.agentlab.semantic.wot.observations.FloatObservationBuilder;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;

import javax.measure.unit.SI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

@Component(
        immediate = true,
        service = {Endpoint.class},
        name = "ru.agentlab.semantic.powermatcher.examples.heater.HeaterDriver",
        configurationPolicy = ConfigurationPolicy.REQUIRE)
@Port(
        name = "manager",
        sends = HeaterState.class,
        accepts = HeaterControlParameters.class)
@Designate(
        ocd = HeaterDriver.Config.class,
        factory = true)
public class HeaterDriver extends AbstractResourceDriver<HeaterState, HeaterControlParameters> {

    private SailRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObservationFactory<Float, DefaultObservationMetadata> floatObservationsFactory;
    private Disposable onStateUpdateSubscription;
    private final Sinks.Many<Double> setPowerSink = Sinks.many().unicast().onBackpressureBuffer();
    private Disposable onControlParametersReceivied;
    private ConnectionContext context;

    public HeaterDriver() {
        floatObservationsFactory = (obsIri) -> new FloatObservationBuilder<>(new DefaultMetadataBuilder(obsIri));
    }


    public @interface Config {

        String thingIRI();
    }

    @Reference
    public void bindSailRepositoryProvider(SailRepositoryProvider repositoryProvider) {
        this.repository = repositoryProvider.getRepository();
    }

    @Activate
    public void activate(Config config) {
        var repoConn = repository.getConnection();
        context = new ConnectionContext(executor, repoConn);
        var thingIRI = iri(config.thingIRI());
        var thingMono = discoverThing(thingIRI, context).cache();

        onControlParametersReceivied = thingMono.flatMap(thing -> Mono.fromFuture(CompletableFuture.supplyAsync(
                () -> ThingActionAffordance.byDescription(
                        thing.getIRI(),
                        iri("https://example.agentlab.ru/#GenericSetHeatingPower"),
                        context
                ), context.getExecutor())
        )).flatMapMany(actionAffordance -> setPowerSink.asFlux().doOnNext(powerToSet -> {
            var setter = serializeFloatSetter(actionAffordance, powerToSet);
            repoConn.add(setter);
        })).subscribe();

        onStateUpdateSubscription = thingMono.flatMapMany(this::subscribeOnHeaterStateUpdates)
                .subscribe(this::publishState);
    }

    @Deactivate
    public void deactivate() {
        onStateUpdateSubscription.dispose();
        onControlParametersReceivied.dispose();
        try {
            setPowerSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        } catch (Exception e) {
            logger.error("unable to complete set power sink", e);
        }
        context.getExecutor().shutdown();
        context.getConnection().close();
    }

    @Override
    protected void handleControlParameters(HeaterControlParameters heaterControlParameters) {
        var targetPowerInWatts = heaterControlParameters.getCurrentConsumption().doubleValue(SI.WATT);
        try {
            setPowerSink.emitNext(targetPowerInWatts, Sinks.EmitFailureHandler.FAIL_FAST);
        } catch (Exception e) {
            logger.error("unable to send control parameters", e);
        }
    }

    private Mono<Thing> discoverThing(IRI thingIRI, ConnectionContext ctx) {
        var future = CompletableFuture.supplyAsync(() -> Thing.of(thingIRI, ctx), ctx.getExecutor());
        return Mono.fromFuture(future);
    }

    private Flux<HeaterResourceState> subscribeOnHeaterStateUpdates(Thing heater) {
        return heater.getPropertyAffordancesWithType(TEMPERATURE, POWER)
                .filter(affordance -> affordance.getProperty(LOCATION_TYPE)
                        .map(locationType -> locationType.equals(INSIDE))
                        .orElse(true))
                .collectList()
                .flatMapMany(affordances -> {
                    var heatingPower = affordances.stream()
                            .filter(affordance -> affordance.hasType(POWER))
                            .findFirst()
                            .orElseThrow();

                    var indoorTemperature = affordances.stream()
                            .filter(affordance -> affordance.hasType(TEMPERATURE))
                            .findFirst()
                            .orElseThrow();

                    return subscribeOnHeaterStateUpdates(indoorTemperature, heatingPower);
                });
    }

    private Flux<HeaterResourceState> subscribeOnHeaterStateUpdates(ThingPropertyAffordance indoorTemperature,
                                                                    ThingPropertyAffordance heatingPower) {
        var lastModifiedComparator = Comparator.comparing(
                (Observation<Float, DefaultObservationMetadata> obs) -> obs.getMetadata().getLastModified()
        );

        var heatingPowerUpdates = heatingPower.latestObservation(floatObservationsFactory)
                .concatWith(heatingPower.subscribeOnLatestObservations(
                        floatObservationsFactory,
                        lastModifiedComparator
                ));

        return indoorTemperature.subscribeOnLatestObservations(floatObservationsFactory, lastModifiedComparator)
                .withLatestFrom(heatingPowerUpdates,
                                (temperatureObservation, heatingPowerObservation) -> new HeaterResourceState(
                                        temperatureObservation.getValue(),
                                        heatingPowerObservation.getValue()
                                )
                );
    }

    private Model serializeFloatSetter(ThingActionAffordance affordance, double value) {
        Model model = new LinkedHashModel();
        OffsetDateTime now = OffsetDateTime.now();
        String time = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        IRI observationIRI = iri("https://example.agentlab.ru#" + UUID.randomUUID().toString());
        model.add(observationIRI, RDF.TYPE, PROPERTY_STATE);
        model.add(observationIRI, DESCRIBED_BY_AFFORDANCE, affordance.getIRI());
        model.add(observationIRI,
                  HAS_INPUT,
                  Values.literal(value)
        );
        model.add(observationIRI, MODIFIED, Values.literal(time, XSD.DATETIME));
        return model;
    }

    private Model serializeFloatObservation(ThingPropertyAffordance affordance, double value) {
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
}
