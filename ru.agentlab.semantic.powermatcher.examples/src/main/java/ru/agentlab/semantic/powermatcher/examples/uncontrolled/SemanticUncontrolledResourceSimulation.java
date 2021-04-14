package ru.agentlab.semantic.powermatcher.examples.uncontrolled;

import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.utils.Utils;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.examples.Utils.openResourceStream;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.EXAMPLE_IRI;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.POWER;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

@Component(
        name = "ru.agentlab.semantic.powermatcher.SemanticUncontrolledResourceSimulation",
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true
)
@Designate(ocd = SemanticUncontrolledResourceSimulation.Config.class)
public class SemanticUncontrolledResourceSimulation {
    private static final Logger logger = LoggerFactory.getLogger(SemanticUncontrolledResourceSimulation.class);
    private UniformRealDistribution random;
    private final Scheduler scheduler = Schedulers.newSingle(SemanticUncontrolledResourceSimulation.class.getName());
    private Disposable subscription;
    private SailRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @ObjectClassDefinition(id = "ru.agentlab.semantic.powermatcher.SemanticUncontrolledResourceSimulation")
    public @interface Config {
        String thingIRI();

        float from() default 0;

        float to() default 10000;

        int intervalMsec() default 1000;

        String thingContext() default "";

        String stateContext() default "";
    }

    @Reference
    public void bindSailRepository(SailRepositoryProvider repositoryProvider) {
        this.repository = repositoryProvider.getRepository();
    }

    @Activate
    public void activate(Config config) {
        logger.info("Starting semantic uncontrolled resource simulation...");
        random = new UniformRealDistribution(config.from(), config.to());
        SailRepositoryConnection connection = repository.getConnection();
        ChangeTrackerConnection sailConn = (ChangeTrackerConnection) connection.getSailConnection();
        ConnectionContext ctx = new ConnectionContext(executor, connection);
        var thingRepository = new ThingRepository(ctx);
        var propertyAffordanceRepository = new ThingPropertyAffordanceRepository(ctx);
        var thingCtx = iri(config.thingContext());
        var obsCtx = iri(config.stateContext());

        subscription = Utils.supplyAsyncWithCancel(() -> populateThingModel(ctx, thingCtx, obsCtx), ctx.getExecutor())
                .then(thingRepository.getThing(iri(config.thingIRI())))
                .flatMapMany(thing -> propertyAffordanceRepository.getPropertyAffordancesWithType(thing, POWER))
                .flatMap(affordance -> Flux.interval(Duration.ofMillis(config.intervalMsec()), scheduler)
                        .map(sec -> affordance))
                .doFinally(ev -> {
                    sailConn.close();
                    connection.close();
                })
                .subscribe(powerAffordance -> publishNewState(random.sample(),
                                                              powerAffordance.getIRI(),
                                                              connection,
                                                              obsCtx
                ));
        logger.info("Starting semantic uncontrolled resource simulation...Done");
    }

    @Deactivate
    public void deactivate() {
        this.subscription.dispose();
    }

    private void populateThingModel(ConnectionContext ctx, Resource thingCtx, Resource obsCtx) {
        try (var conn = ctx.createConnection()) {
            conn.begin();
            try (var observations = openResourceStream("uncontrolled_initial_observations.ttl");
                 var things = openResourceStream("uncontrolled_model.ttl")) {

                var obsModel = Rio.parse(observations, RDFFormat.TURTLE);
                var thingsModel = Rio.parse(things, RDFFormat.TURTLE);

                conn.add(thingsModel, thingCtx);
                conn.add(obsModel, obsCtx);

                conn.commit();
            } catch (Exception exception) {
                conn.rollback();
                logger.error("unable to populate thing model", exception);
            }
        }
    }

    private void publishNewState(double currentValue,
                                 IRI powerAffordanceIRI,
                                 RepositoryConnection conn,
                                 Resource stateContext) {
        logger.info("publishing new state: currentValue={}, powerAffordanceIRI={}", currentValue, powerAffordanceIRI);
        IRI obsId = iri(EXAMPLE_IRI, UUID.randomUUID().toString());
        OffsetDateTime now = OffsetDateTime.now();
        String time = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        conn.begin();
        conn.add(obsId, RDF.TYPE, PROPERTY_STATE, stateContext);
        conn.add(obsId, DESCRIBED_BY_AFFORDANCE, powerAffordanceIRI, stateContext);
        conn.add(obsId, HAS_VALUE, Values.literal(currentValue), stateContext);
        conn.add(obsId, MODIFIED, Values.literal(time, XSD.DATETIME), stateContext);
        conn.commit();
    }
}
