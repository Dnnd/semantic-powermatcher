package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_ACTION_AFFORDANCE;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_PROPERTY_AFFORDANCE;

public class Thing {
    private final IRI thingIRI;
    private final Model model;
    private final List<IRI> types;
    private final ConnectionContext context;

    public static Thing of(IRI thingIRI, ConnectionContext context) {
        Model model = new LinkedHashModel();
        List<IRI> types = new ArrayList<>();

        for (var st : context.getConnection().getStatements(thingIRI, null, null)) {
            model.add(st);
            if (st.getPredicate().equals(RDF.TYPE)) {
                types.add((IRI) st.getObject());
            }
        }
        return new Thing(thingIRI, types, model, context);

    }

    public Thing(IRI thingIRI, List<IRI> types, Model model, ConnectionContext context) {
        this.thingIRI = thingIRI;
        this.types = types;
        this.context = context;
        this.model = model;
    }

    public Optional<Value> getProperty(IRI predicate) {
        for (var st : model.getStatements(thingIRI, predicate, null)) {
            return Optional.of(st.getObject());
        }
        return Optional.empty();
    }

    public ThingActionAffordance getActionAffordance(IRI propertyAffordance) {
        var properties = model.getStatements(
                thingIRI,
                HAS_ACTION_AFFORDANCE,
                propertyAffordance
        );
        for (Statement ignored : properties) {
            return ThingActionAffordance.of(thingIRI, propertyAffordance, context);
        }
        throw new RuntimeException("not found");
    }

    public ThingPropertyAffordance getPropertyAffordance(IRI propertyAffordance) {
        var properties = model.getStatements(
                thingIRI,
                HAS_PROPERTY_AFFORDANCE,
                propertyAffordance
        );
        for (Statement ignored : properties) {
            return ThingPropertyAffordance.of(propertyAffordance, context);
        }
        throw new RuntimeException("not found");
    }

    public Flux<ThingPropertyAffordance> getPropertyAffordancesWithType(IRI... desiredAffordanceType) {
        return Flux.create(sink -> CompletableFuture.supplyAsync(() -> {
            Variable affordanceIRI = var("affordanceIRI");

            GraphPattern pattern;
            if (desiredAffordanceType == null || desiredAffordanceType.length == 0) {
                pattern = tp(thingIRI, HAS_PROPERTY_AFFORDANCE, affordanceIRI);
            } else {
                pattern = tp(thingIRI, HAS_PROPERTY_AFFORDANCE, affordanceIRI)
                        .and(tp(affordanceIRI, RDF.TYPE, desiredAffordanceType[0]));
                for (int i = 1; i < desiredAffordanceType.length; ++i) {
                    pattern = pattern.union(
                            tp(thingIRI, HAS_PROPERTY_AFFORDANCE, affordanceIRI)
                                    .and(tp(affordanceIRI, RDF.TYPE, desiredAffordanceType[1]))
                    );
                }
            }
            SelectQuery query = Queries.SELECT(affordanceIRI).where(pattern).distinct();

            var prepared = context.getConnection().prepareTupleQuery(query.getQueryString());
            for (var binding : prepared.evaluate()) {
                var foundIRI = (IRI) binding.getBinding("affordanceIRI").getValue();
                sink.next(ThingPropertyAffordance.of(foundIRI, context));
            }

            throw new RuntimeException("not found");
        }, context.getExecutor()));
    }

    public ConnectionContext getContext() {
        return context;
    }

    public IRI getIRI() {
        return thingIRI;
    }
}
