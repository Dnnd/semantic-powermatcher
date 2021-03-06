package ru.agentlab.semantic.powermatcher.examples.geo;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import ru.agentlab.changetracking.sail.ChangeTracker;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.api.ObservationFactory;
import ru.agentlab.semantic.wot.observations.IRIObservationParser;
import ru.agentlab.semantic.wot.observations.SensorMetadata;
import ru.agentlab.semantic.wot.observations.SensorMetadataParser;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.thing.ConnectionContext;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_RESULT;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.PLACE;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
public class GeoExporter {

    private final Logger logger = LoggerFactory.getLogger(GeoExporter.class);
    private Disposable subscription;
    private SailRepository repository;

    @Reference
    public void bindSailRepository(SailRepositoryProvider provider) {
        this.repository = provider.getRepository();
    }

    @Activate
    public void activate() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var ctx = new ConnectionContext(executor, repository, ChangeTracker.class);
        var propsRepo = new ThingPropertyAffordanceRepository(ctx);
        var geoRepo = new GeoRepository(ctx);
        subscription = discoverPlaceObservations(propsRepo)
                .flatMap(obs -> geoRepo.get(obs.getValue()))
                .doFinally(signal -> ctx.close())
                .subscribe(place -> logger.info("observed place: {}", place));
    }

    @Deactivate
    public void deactivate() {
        subscription.dispose();
    }

    private Flux<Observation<IRI, SensorMetadata>> discoverPlaceObservations(ThingPropertyAffordanceRepository props) {
        ObservationFactory<IRI, SensorMetadata> iriObsBuilderFactory = (obsIRI) ->
                new IRIObservationParser<>(new SensorMetadataParser(obsIRI));

        return props.discoverPropertyAffordancesWithType(PLACE)
                    .flatMap(locationAffordance -> {
                        var latestObs = props.latestObservation(
                                locationAffordance.getIRI(),
                                iriObsBuilderFactory
                        );
                        Comparator<Observation<IRI, SensorMetadata>> byLastModified = Comparator.comparing(
                                obs -> obs.getMetadata().getLastModified()
                        );
                        return latestObs.concatWith(props.subscribeOnLatestObservations(
                                locationAffordance.getIRI(),
                                HAS_RESULT,
                                iriObsBuilderFactory,
                                byLastModified
                        ));
                    });
    }

}
