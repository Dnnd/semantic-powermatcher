package ru.agentlab.semantic.wot.repositories;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import reactor.core.publisher.Mono;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class ThingRepository implements WotRepository {

    private final ConnectionContext context;

    public ThingRepository(ConnectionContext context) {
        this.context = context;
    }

    public Mono<Thing> getThing(IRI thingIRI) {
        return Utils.supplyAsyncWithCancel(() -> {
            List<IRI> thingTypes = new ArrayList<>();
            Model thingModel = new LinkedHashModel();
            var statements = context.getConnection().getStatements(thingIRI, null, null);
            Utils.filterStatements(statements, thingIRI, thingModel, thingTypes);
            return new Thing(thingIRI, thingTypes, thingModel);
        }, context.getExecutor());
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return context;
    }
}
