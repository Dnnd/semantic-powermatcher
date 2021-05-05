package ru.agentlab.semantic.powermatcher.examples.uncontrolled;

import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.flexiblepower.messaging.Endpoint;
import org.flexiblepower.ral.ResourceControlParameters;
import org.flexiblepower.ral.drivers.uncontrolled.PowerState;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrollableDriver;
import org.flexiblepower.ral.ext.AbstractResourceDriver;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import reactor.core.Disposable;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.observations.DefaultMetadataParser;
import ru.agentlab.semantic.wot.observations.FloatObservationParser;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.thing.ConnectionContext;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.POWER;
import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_SIMPLE_RESULT;

@Component(
        name = UncontrolledSemanticResourceDriverConfigurator.DRIVER_CONFIG_NAME,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true,
        service = {Endpoint.class})
@Designate(ocd = UncontrolledSemanticResourceDriver.Config.class, factory = true)
public class UncontrolledSemanticResourceDriver extends AbstractResourceDriver<PowerState, ResourceControlParameters>
        implements UncontrollableDriver {

    private SailRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Disposable subscription;

    @ObjectClassDefinition(
            id = UncontrolledSemanticResourceDriverConfigurator.DRIVER_CONFIG_NAME,
            name = "Semantic Resource Driver Config"
    )
    @interface Config {
        @AttributeDefinition(name = UncontrolledSemanticResourceDriverConfigurator.THING_IRI_PROPERTY)
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
                                           .flatMapMany(thing -> propertyAffordanceRepository.getPropertyAffordancesWithType(
                                                   thing,
                                                   POWER
                                           ))
                                           .flatMap(powerProp -> propertyAffordanceRepository.subscribeOnLatestObservations(
                                                   powerProp,
                                                   HAS_SIMPLE_RESULT,
                                                   (obsIRI) -> new FloatObservationParser<>(new DefaultMetadataParser(
                                                           obsIRI)),
                                                   Comparator.comparing(observation -> observation.getMetadata()
                                                                                                  .getLastModified())
                                           ))
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
