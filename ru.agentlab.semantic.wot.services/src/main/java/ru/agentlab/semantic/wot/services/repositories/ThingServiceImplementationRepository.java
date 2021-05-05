package ru.agentlab.semantic.wot.services.repositories;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.filter.Match;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.services.api.ThingServiceConfiguratorConfig;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.changetracking.filter.ChangetrackingFilter.Filtering.ADDED;
import static ru.agentlab.changetracking.filter.ChangetrackingFilter.Filtering.REMOVED;
import static ru.agentlab.semantic.wot.services.api.WotServicesVocabulary.*;

public class ThingServiceImplementationRepository {
    private final ConnectionContext context;

    public ThingServiceImplementationRepository(ConnectionContext context) {
        this.context = context;
    }

    public Flux<Match<ThingServiceConfiguratorConfig>> discoverThingServiceImplementations() {
        return Utils.supplyAsyncWithCancel(this::fetchThingServiceImplementationsSync, context.getExecutor())
                    .map(implementations -> implementations.map(implementation -> new Match<>(ADDED, implementation)))
                    .flatMapMany(Flux::fromStream)
                    .concatWith(subscribeOnNewThingServices());
    }

    public Flux<Match<ThingServiceConfiguratorConfig>> subscribeOnNewThingServices() {
        var conn = (ChangeTrackerConnection) context.getSailConnection();
        var filter = ChangetrackingFilter.builder()
                                         .addPattern(
                                                 null,
                                                 RDF.TYPE,
                                                 THING_SERVICE_CONFIGURATOR,
                                                 ChangetrackingFilter.Filtering.ALL
                                         )
                                         .build();
        return conn.events(context.getScheduler())
                   .flatMap(changes -> {
                       var added = Mono.justOrEmpty(
                               filter.matchModel(changes.getAddedStatements())
                                     .map(implModel -> new ThingServiceConfiguratorConfig.Builder()
                                             .setModel(implModel)
                                             .build()
                                     ).map(implementation -> new Match<>(REMOVED, implementation))
                       );
                       var removed = Mono.justOrEmpty(
                               filter.matchModel(changes.getRemovedStatements())
                                     .map(implModel -> new ThingServiceConfiguratorConfig.Builder()
                                             .setModel(implModel)
                                             .build()
                                     ).map(implementation -> new Match<>(ADDED, implementation))
                       );
                       return added.concatWith(removed);
                   });
    }

    public Stream<ThingServiceConfiguratorConfig> fetchThingServiceImplementationsSync() {
        SailRepositoryConnection connection = context.getConnection();
        Variable implIRI = var("implIRI");
        Variable pred = var("pred");
        Variable obj = var("obj");

        ConstructQuery query = Queries.CONSTRUCT()
                                      .where(
                                              tp(implIRI, RDF.TYPE, THING_SERVICE_CONFIGURATOR),
                                              tp(implIRI, pred, obj)
                                      );

        Map<IRI, ThingServiceConfiguratorConfig.Builder> implementations = new HashMap<>();
        try (var result = connection.prepareGraphQuery(query.getQueryString()).evaluate()) {
            for (Statement statement : result) {
                implementations.compute((IRI) statement.getSubject(), (implementationIRI, impl) -> {
                    if (impl == null) {
                        impl = new ThingServiceConfiguratorConfig.Builder();
                    }
                    return impl.processStatement(statement);
                });
            }

        }
        return implementations.values()
                              .stream()
                              .map(ThingServiceConfiguratorConfig.Builder::build);

    }
}
