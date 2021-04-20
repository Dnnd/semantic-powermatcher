package ru.agentlab.semantic.wot.repositories;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.filter.Transformations;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingModel;
import ru.agentlab.semantic.wot.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import static ru.agentlab.changetracking.filter.ChangetrackingFilter.Filtering.ADDED;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_THING_MODEL;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.THING;

public class ThingRepository implements WotRepository {

    private final ConnectionContext context;

    public ThingRepository(ConnectionContext context) {
        this.context = context;
    }

    public Flux<Thing> discoverDeploymentsOf(ThingModel model) {
        var filter = ChangetrackingFilter.builder()
                .addPattern(null, HAS_THING_MODEL, model.getThingModelIRI(), ADDED)
                .addPattern(null, RDF.TYPE, THING, ADDED)
                .build();
        var conn = (ChangeTrackerConnection) context.getSailConnection();
        return conn.events(context.getScheduler())
                .handle((changes, sink) -> {
                    var added = Transformations.groupBySubject(changes.getAddedStatements());
                    added.entrySet()
                            .stream()
                            .flatMap(entry -> {
                                var maybeThingIRI = entry.getKey();
                                var maybeThingDescription = entry.getValue();
                                return filter.matchModel(maybeThingDescription)
                                        .map(thingDescription -> new Thing(maybeThingIRI, thingDescription))
                                        .stream();
                            })
                            .forEach(sink::next);
                });
    }

    public Mono<Thing> getThing(IRI thingIRI) {
        return Utils.supplyAsyncWithCancel(() -> {
            Model thingModel = new LinkedHashModel();
            var statements = context.getConnection().getStatements(thingIRI, null, null);
            statements.forEach(thingModel::add);
            return new Thing(thingIRI, thingModel);
        }, context.getExecutor());
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return context;
    }
}
