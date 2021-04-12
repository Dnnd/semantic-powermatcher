package ru.agentlab.semantic.powermatcher.examples.uncontrolled;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrollableDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.filter.Transformations;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.changetracking.sail.TransactionChanges;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.observations.DefaultMetadata;
import ru.agentlab.semantic.wot.observations.DefaultMetadataParser;
import ru.agentlab.semantic.wot.observations.FloatObservationParser;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.POWER;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

@Component(
        name = "ru.agentlab.semantic.powermatcher.UncontrolledSemanticResourceDriver",
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true,
        service = {Endpoint.class})
@Designate(ocd = UncontrolledSemanticResourceDriver.Config.class, factory = true)
public class UncontrolledSemanticResourceDriver extends AbstractResourceDriver<PowerState, ResourceControlParameters>
        implements UncontrollableDriver {

    private SailRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Scheduler scheduler = Schedulers.fromExecutorService(executor);
    private Disposable subscription;

    @ObjectClassDefinition(
            id = "ru.agentlab.semantic.powermatcher.UncontrolledSemanticResourceDriver",
            name = "Semantic Resource Driver Config"
    )
    @interface Config {
        String thingIRI();
    }

    public static class State implements PowerState {
        private final float currentUsageWatts;

        public State(float currentUsage) {
            this.currentUsageWatts = currentUsage;
        }


        @Override
        public Measurable<Power> getCurrentUsage() {
            return Measure.valueOf(currentUsageWatts, SI.WATT);
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public String toString() {
            return "State{" +
                    "currentUsageWatts=" + currentUsageWatts +
                    '}';
        }
    }

    @Reference
    public void bindRepository(SailRepositoryProvider repositoryProvider) {
        this.repository = repositoryProvider.getRepository();
    }


    @Activate
    public void activate(Config config) {
        logger.info("Uncontrolled semantic resource driver...");
        SailRepositoryConnection conn = repository.getConnection();
        ChangeTrackerConnection sailConn = (ChangeTrackerConnection) conn.getSailConnection();
        var ctx = new ConnectionContext(executor, conn);
        var thingRepository = new ThingRepository(ctx);
        var propertyAffordanceRepository = new ThingPropertyAffordanceRepository(ctx);

        this.subscription = thingRepository.getThing(iri(config.thingIRI()))
                .flatMapMany(thing -> propertyAffordanceRepository.getPropertyAffordancesWithType(thing, POWER))
                .flatMap(powerProp -> {
                    logger.info("observing {}", powerProp.getIRI());
                    var powerPropObservationFilter = createObservationFilterForProperty(powerProp.getIRI());
                    return sailConn.events(scheduler)
                            .flatMap(changes -> extractLatestObservation(changes, powerPropObservationFilter));
                })
                .doFinally(signal -> {
                    thingRepository.cancel();
                    propertyAffordanceRepository.cancel();
                    sailConn.close();
                    conn.close();
                })
                .subscribe(powerOutputObservation -> {
                    logger.info(powerOutputObservation.toString());
                    publishState(new State(powerOutputObservation.getValue()));
                });
        logger.info("Uncontrolled semantic resource driver...Done");
    }

    private static Mono<Observation<Float, DefaultMetadata>> extractLatestObservation(TransactionChanges changes, ChangetrackingFilter modelFilter) {
        Map<IRI, Model> modelsBySubject = Transformations.groupBySubject(changes.getAddedStatements());
        return modelsBySubject.entrySet()
                .stream()
                .flatMap(entity -> {
                    IRI observationIRI = entity.getKey();
                    Model observationModel = entity.getValue();
                    return modelFilter.matchModel(observationModel)
                            .map(model -> createObservation(observationIRI, observationModel))
                            .stream();
                })
                .max(Comparator.comparing(observation -> observation.getMetadata().getLastModified()))
                .map(Mono::just)
                .orElseGet(Mono::empty);
    }

    private static Observation<Float, DefaultMetadata> createObservation(IRI observationIRI, Model observationModel) {
        var metadataBuilder = new DefaultMetadataParser(observationIRI);
        var observationBuilder = new FloatObservationParser<>(metadataBuilder);
        return observationBuilder.processAll(observationModel).build();
    }

    private static ChangetrackingFilter createObservationFilterForProperty(IRI property) {
        return ChangetrackingFilter.builder()
                .addPattern(null, DESCRIBED_BY_AFFORDANCE, property, ChangetrackingFilter.Filtering.ADDED)
                .addPattern(null, HAS_VALUE, null, ChangetrackingFilter.Filtering.ADDED)
                .addPattern(null, MODIFIED, null, ChangetrackingFilter.Filtering.ADDED)
                .build();
    }

    @Modified
    public void modified(Config config) {
        deactivate();
        activate(config);
    }

    @Deactivate
    public void deactivate() {
        subscription.dispose();
        executor.shutdown();
    }

    @Override
    protected void handleControlParameters(ResourceControlParameters resourceControlParameters) {
        logger.error("unexpected control parameters: {}", resourceControlParameters);
    }

}
