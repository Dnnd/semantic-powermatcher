package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
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

    public Mono<ThingActionAffordance> getActionAffordance(IRI propertyAffordance) {
        var future = CompletableFuture.supplyAsync(
                () -> ThingActionAffordance.of(thingIRI, propertyAffordance, context),
                context.getExecutor()
        );
        return Mono.fromFuture(future).doOnCancel(() -> future.cancel(true));
    }

    public Mono<ThingPropertyAffordance> getPropertyAffordance(IRI propertyAffordance) {
        var future = CompletableFuture.supplyAsync(
                () -> ThingPropertyAffordance.of(thingIRI, propertyAffordance, context),
                context.getExecutor()
        );
        return Mono.fromFuture(future).doOnCancel(() -> future.cancel(true));
    }

    public Flux<ThingPropertyAffordance> getPropertyAffordancesWithType(IRI... desiredAffordanceType) {
        var propertyAffordances = CompletableFuture.supplyAsync(() -> {
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
            return prepared.evaluate().stream().map(binding -> {
                var foundIRI = (IRI) binding.getBinding("affordanceIRI").getValue();
                return ThingPropertyAffordance.of(thingIRI, foundIRI, context);
            });
        }, context.getExecutor());

        return Mono.fromFuture(propertyAffordances)
                .doFinally(signal -> {
                    if (signal.equals(SignalType.CANCEL)) {
                        propertyAffordances.cancel(true);
                    }
                })
                .flatMapMany(Flux::fromStream);
    }

    public ConnectionContext getContext() {
        return context;
    }

    public IRI getIRI() {
        return thingIRI;
    }
}
