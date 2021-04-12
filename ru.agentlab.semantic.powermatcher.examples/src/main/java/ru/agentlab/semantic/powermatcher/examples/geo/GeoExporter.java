package ru.agentlab.semantic.powermatcher.examples.geo;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import ru.agentlab.semantic.powermatcher.examples.uncontrolled.SailRepositoryProvider;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.api.ObservationFactory;
import ru.agentlab.semantic.wot.observations.DefaultMetadata;
import ru.agentlab.semantic.wot.observations.DefaultMetadataParser;
import ru.agentlab.semantic.wot.observations.IRIObservationParser;
import ru.agentlab.semantic.wot.repositories.ThingPropertyAffordanceRepository;
import ru.agentlab.semantic.wot.thing.ConnectionContext;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.PLACE;

@Component
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
        var repoConn = repository.getConnection();
        var ctx = new ConnectionContext(executor, repoConn);
        var propsRepo = new ThingPropertyAffordanceRepository(ctx);
        var geoRepo = new GeoRepository(ctx);
        subscription = discoverPlaceObservations(propsRepo)
                .flatMap(obs -> {
                    var placeMono = geoRepo.get(obs.getValue());
                    return placeMono;
                })
                .doFinally(signal -> ctx.close())
                .subscribe(place -> {
                    logger.info("observed place: {}", place);
                });
    }

    @Deactivate
    public void deactivate() {
        subscription.dispose();
    }

    private Flux<Observation<IRI, DefaultMetadata>> discoverPlaceObservations(ThingPropertyAffordanceRepository props) {
        ObservationFactory<IRI, DefaultMetadata> iriObsBuilderFactory = (obsIRI) ->
                new IRIObservationParser<>(new DefaultMetadataParser(obsIRI));

        return props.discoverPropertyAffordancesWithType(PLACE)
                .flatMap(locationAffordance -> {
                    var latestObs = props.latestObservation(
                            locationAffordance.getIRI(),
                            iriObsBuilderFactory
                    );
                    Comparator<Observation<IRI, DefaultMetadata>> byLastModified = Comparator.comparing(
                            obs -> obs.getMetadata().getLastModified()
                    );
                    return latestObs.concatWith(props.subscribeOnLatestObservations(
                            locationAffordance.getIRI(),
                            iriObsBuilderFactory,
                            byLastModified
                    ));
                });
    }

}
