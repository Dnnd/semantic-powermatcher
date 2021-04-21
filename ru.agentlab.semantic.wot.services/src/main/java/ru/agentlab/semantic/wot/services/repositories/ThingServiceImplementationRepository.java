package ru.agentlab.semantic.wot.services.repositories;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.services.api.ThingServiceImplementation;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.changetracking.filter.ChangetrackingFilter.Filtering.ADDED;
import static ru.agentlab.semantic.wot.services.repositories.WotServicesVocabulary.*;

public class ThingServiceImplementationRepository {
    private final ConnectionContext context;

    public ThingServiceImplementationRepository(ConnectionContext context) {
        this.context = context;
    }

    public Flux<ThingServiceImplementation> discoverThingServiceImplementations() {
        return Utils.supplyAsyncWithCancel(this::fetchThingServiceImplementationsSync, context.getExecutor())
                    .flatMapMany(Flux::fromStream)
                    .concatWith(subscribeOnNewThingServices());
    }

    public Flux<ThingServiceImplementation> subscribeOnNewThingServices() {
        var conn = (ChangeTrackerConnection) context.getSailConnection();
        var filter = ChangetrackingFilter.builder()
                                         .addPattern(null, RDF.TYPE, THING_SERVICE_IMPLEMENTATION, ADDED)
                                         .build();
        return conn.events(context.getScheduler())
                   .flatMap(changes -> Mono.justOrEmpty(
                           filter.matchModel(changes.getAddedStatements())
                                 .map(implModel -> new ThingServiceImplementation.Builder()
                                         .setModel(implModel)
                                         .build()
                                 )
                   ));
    }

    public Stream<ThingServiceImplementation> fetchThingServiceImplementationsSync() {
        SailRepositoryConnection connection = context.getConnection();
        Variable implIRI = var("implIRI");
        Variable confID = var("confID");
        Variable bundleID = var("bundleID");
        Variable modelIRI = var("modelIRI");

        ConstructQuery query = Queries.CONSTRUCT()
                                      .where(
                                              tp(implIRI, RDF.TYPE, iri("ThingServiceImplementation")),
                                              tp(implIRI, CONFIGURATION_ID, confID),
                                              tp(implIRI, BUNDLE_ID, bundleID),
                                              tp(implIRI, MODEL_IRI, modelIRI)
                                      );
        Map<IRI, ThingServiceImplementation.Builder> implementations = new HashMap<>();
        try (var result = connection.prepareGraphQuery(query.getQueryString()).evaluate()) {
            for (Statement statement : result) {
                implementations.compute((IRI) statement.getObject(), (implementationIRI, impl) -> {
                    if (impl == null) {
                        impl = new ThingServiceImplementation.Builder();
                    }
                    return impl.processStatement(statement);
                });
            }

        }
        return implementations.values()
                              .stream()
                              .map(ThingServiceImplementation.Builder::build);

    }
}
