package ru.agentlab.semantic.powermatcher.examples.heater;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
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

import javax.measure.unit.SI;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.*;
import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_SIMPLE_RESULT;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.ACTION_INVOCATION;

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

    private final ObservationFactory<Float, DefaultMetadata> floatObservationsFactory;
    private Disposable onStateUpdateSubscription;
    private Sinks.Many<Double> setPowerSink;
    private Disposable onControlParametersReceived;
    private ConnectionContext context;
    private ThingPropertyAffordanceRepository propertyAffordances;
    private ThingActionAffordanceRepository actionAffordances;

    public HeaterDriver() {
        floatObservationsFactory = (obsIri) -> new FloatObservationParser<>(new DefaultMetadataParser(
                obsIri));
    }


    @ObjectClassDefinition(name = "Heater Semantic Driver")
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
                iri(EXAMPLE_IRI, "GenericSetHeatingPower")
        )).flatMapMany(actionAffordance -> setPowerSink.asFlux().doOnNext(powerToSet -> {
            var setter = serializeFloatSetter(actionAffordance, powerToSet);
            repoConn.add(setter, stateContext);
            logger.info("invoked power setting action={}", setter);
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
                                                                         .filter(affordance -> affordance.hasType(
                                                                                 TEMPERATURE))
                                                                         .findFirst()
                                                                         .orElseThrow();

                                      return subscribeOnHeaterStateUpdates(indoorTemperature, heatingPower);
                                  });
    }

    private Flux<HeaterResourceState> subscribeOnHeaterStateUpdates(ThingPropertyAffordance indoorTemperature,
                                                                    ThingPropertyAffordance heatingPower) {
        var lastModifiedComparator = Comparator.comparing(
                (Observation<Float, DefaultMetadata> obs) -> obs.getMetadata().getLastModified()
        );

        var heatingPowerUpdates = propertyAffordances
                .latestObservation(heatingPower, floatObservationsFactory)
                .concatWith(propertyAffordances.subscribeOnLatestObservations(
                        heatingPower,
                        HAS_SIMPLE_RESULT,
                        floatObservationsFactory,
                        lastModifiedComparator
                ));

        return propertyAffordances.subscribeOnLatestObservations(
                indoorTemperature,
                HAS_SIMPLE_RESULT,
                floatObservationsFactory,
                lastModifiedComparator
        ).withLatestFrom(
                heatingPowerUpdates,
                (temperatureObservation, heatingPowerObservation) -> new HeaterResourceState(
                        temperatureObservation.getValue(),
                        heatingPowerObservation.getValue()
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
