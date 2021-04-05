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
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import ru.agentlab.semantic.powermatcher.examples.uncontrolled.SailRepositoryProvider;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observation.api.ObservationFactory;
import ru.agentlab.semantic.wot.observations.DefaultMetadataBuilder;
import ru.agentlab.semantic.wot.observations.DefaultObservationMetadata;
import ru.agentlab.semantic.wot.observations.FloatObservationBuilder;
import ru.agentlab.semantic.wot.repositories.ThingActionAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;

import javax.measure.unit.SI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

@Component(
        immediate = true,
        service = {Endpoint.class},
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
    private Disposable onControlParametersReceived;
    private ConnectionContext context;
    private ThingPropertyAffordanceRepository propertyAffordances;
    private ThingActionAffordanceRepository actionAffordances;

    public HeaterDriver() {
        floatObservationsFactory = (obsIri) -> new FloatObservationBuilder<>(new DefaultMetadataBuilder(obsIri));
    }


    @ObjectClassDefinition(name = "Heater Semantic Driver")
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
        var things = new ThingRepository(context);
        propertyAffordances = new ThingPropertyAffordanceRepository(context);
        actionAffordances = new ThingActionAffordanceRepository(context);

        var thingMono = things.getThing(thingIRI).cache();

        onControlParametersReceived = thingMono.flatMap(thing -> actionAffordances.byDescription(
                thing,
                iri("https://example.agentlab.ru/#GenericSetHeatingPower")
        )).flatMapMany(actionAffordance -> setPowerSink.asFlux().doOnNext(powerToSet -> {
            var setter = serializeFloatSetter(actionAffordance, powerToSet);
            repoConn.add(setter);
            logger.info("invoked power setting action...");
        })).subscribe();

        onStateUpdateSubscription = thingMono.flatMapMany(this::subscribeOnHeaterStateUpdates)
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
    protected void handleControlParameters(HeaterControlParameters heaterControlParameters) {
        var targetPowerInWatts = heaterControlParameters.getCurrentConsumption().doubleValue(SI.WATT);
        try {
            setPowerSink.emitNext(targetPowerInWatts, Sinks.EmitFailureHandler.FAIL_FAST);
        } catch (Exception e) {
            logger.error("unable to send control parameters", e);
        }
    }

    private Flux<HeaterResourceState> subscribeOnHeaterStateUpdates(Thing heater) {

        return propertyAffordances.getPropertyAffordancesWithType(heater, TEMPERATURE, POWER)
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

        var heatingPowerUpdates = propertyAffordances.latestObservation(heatingPower, floatObservationsFactory)
                .concatWith(propertyAffordances.subscribeOnLatestObservations(
                        heatingPower,
                        floatObservationsFactory,
                        lastModifiedComparator
                ));

        return propertyAffordances.subscribeOnLatestObservations(
                indoorTemperature,
                floatObservationsFactory,
                lastModifiedComparator
        ).withLatestFrom(heatingPowerUpdates,
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
        IRI actionInvocation = iri("https://example.agentlab.ru#" + UUID.randomUUID().toString());
        model.add(actionInvocation, RDF.TYPE, ACTION_INVOCATION);
        model.add(actionInvocation, DESCRIBED_BY_AFFORDANCE, affordance.getIRI());
        model.add(actionInvocation,
                  HAS_INPUT,
                  Values.literal(value)
        );
        model.add(actionInvocation, MODIFIED, Values.literal(time, XSD.DATETIME));
        return model;
    }
}
