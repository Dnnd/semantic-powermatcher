package ru.agentlab.semantic.powermatcher.examples.uncontrolled;

import com.opencsv.CSVReader;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
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
import ru.agentlab.changetracking.sail.ChangeTracker;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.observations.DefaultMetadata;
import ru.agentlab.semantic.wot.observations.FloatObservation;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.ThingPropertyAffordance;
import ru.agentlab.semantic.wot.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static ru.agentlab.semantic.powermatcher.examples.Utils.openResourceStream;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.EXAMPLE_IRI;
import static ru.agentlab.semantic.powermatcher.vocabularies.Example.POWER;
import static ru.agentlab.semantic.wot.vocabularies.SSN.OBSERVATION;

@Component(
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true
)
@Designate(ocd = SemanticUncontrolledResourceSimulation.Config.class)
public class SemanticUncontrolledResourceSimulation {
    private static final Logger logger = LoggerFactory.getLogger(SemanticUncontrolledResourceSimulation.class);
    private final Scheduler scheduler = Schedulers.newSingle(SemanticUncontrolledResourceSimulation.class.getName());
    private Disposable subscription;
    private SailRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @ObjectClassDefinition(name = "Uncontrolled Resource Simulation")
    public @interface Config {
        String thingIRI();

        String dataSource();

        int intervalMsec() default 1000;

        String thingContext() default "";

        String stateContext() default "";
    }

    @Reference
    public void bindSailRepository(SailRepositoryProvider repositoryProvider) {
        this.repository = repositoryProvider.getRepository();
    }

    public List<Float> readDataSource(File from) throws IOException {
        var reader = new CSVReader(Files.newBufferedReader(from.toPath(), StandardCharsets.UTF_8));
        return reader.readAll().stream()
                     .map(values -> Float.parseFloat(values[0]))
                     .collect(Collectors.toList());
    }

    @Activate
    public void activate(Config config) throws IOException {

        logger.info("Starting semantic uncontrolled resource simulation...");
        var generator = new WindGeneratorModel(readDataSource(new File(config.dataSource())));
        ConnectionContext ctx = new ConnectionContext(executor, repository, ChangeTracker.class);
        var thingRepository = new ThingRepository(ctx);
        var propertyAffordanceRepository = new ThingPropertyAffordanceRepository(ctx);
        var thingCtx = iri(config.thingContext());
        var obsCtx = iri(config.stateContext());

        subscription = Utils.supplyAsyncWithCancel(() -> populateThingModel(ctx, thingCtx, obsCtx), ctx.getExecutor())
                            .then(thingRepository.getThing(iri(config.thingIRI())))
                            .flatMapMany(thing -> propertyAffordanceRepository.getPropertyAffordancesWithType(
                                    thing,
                                    POWER
                            ))
                            .flatMap(affordance -> Flux.interval(Duration.ofMillis(config.intervalMsec()), scheduler)
                                                       .map(sec -> affordance))
                            .doFinally(ev -> {
                                ctx.getSailConnection().close();
                                ctx.getConnection().close();
                            })
                            .subscribe(powerAffordance -> publishNewState(
                                    generator.next(),
                                    powerAffordance,
                                    ctx.getConnection(),
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
                                 ThingPropertyAffordance powerAffordance,
                                 RepositoryConnection conn,
                                 Resource stateContext) {
        logger.info("publishing new state: currentValue={}, powerAffordanceIRI={}", currentValue, powerAffordance);
        IRI obsId = iri(EXAMPLE_IRI, UUID.randomUUID().toString());
        var obs = new FloatObservation<DefaultMetadata>((float) currentValue);
        obs.setMetadata(
                new DefaultMetadata(
                        powerAffordance.getIRI(),
                        obsId,
                        powerAffordance.getThingIRI(),
                        OffsetDateTime.now(),
                        OBSERVATION
                )
        );
        conn.begin();
        conn.add(obs.toModel(stateContext));
        conn.commit();
    }
}
