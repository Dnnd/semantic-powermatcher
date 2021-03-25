package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import reactor.core.publisher.Mono;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observation.api.ObservationBuilder;
import ru.agentlab.semantic.wot.observations.DefaultObservationMetadata;
import ru.agentlab.semantic.wot.observations.FloatObservation;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.select;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_VALUE;

public class ThingPropertyAffordance {
    private final IRI propertyAffordanceIRI;
    private List<IRI> types;
    private final Model model;
    private final ConnectionContext context;

    public static ThingPropertyAffordance of(IRI affordanceIRI, ConnectionContext ctx) {
        Model model = new LinkedHashModel();
        List<IRI> types = new ArrayList<>();

        for (var st : ctx.getConnection().getStatements(affordanceIRI, null, null)) {
            model.add(st);
            if (st.getPredicate().equals(RDF.TYPE)) {
                types.add((IRI) st.getObject());
            }
        }
        return new ThingPropertyAffordance(affordanceIRI, types, model, ctx);
    }

    public ThingPropertyAffordance(IRI propertyAffordance, List<IRI> types, Model model, ConnectionContext context) {
        this.propertyAffordanceIRI = propertyAffordance;
        this.types = types;
        this.model = model;
        this.context = context;
    }

    public List<IRI> getTypes() {
        return types;
    }

    public void setTypes(List<IRI> types) {
        this.types = types;
    }

    public IRI getIRI() {
        return propertyAffordanceIRI;
    }

    public <T, M> Mono<Observation<T, M>> latestObservation(ObservationBuilder<T, M> builder) {
        var conn = this.context.getConnection();
        var future = CompletableFuture.supplyAsync(() -> {
            Variable mostRecent = var("mostRecent");
            Variable lastModified = var("lastModified");
            Variable obs = var("obs");
            Variable value = var("value");
            var query = Queries.CONSTRUCT(
                    tp(obs, DESCRIBED_BY_AFFORDANCE, propertyAffordanceIRI),
                    tp(obs, MODIFIED, mostRecent),
                    tp(obs, HAS_VALUE, value)
            ).where(select(obs, Expressions.max(lastModified).as(mostRecent))
                            .where(tp(obs, DESCRIBED_BY_AFFORDANCE, propertyAffordanceIRI),
                                   tp(obs, MODIFIED, lastModified)
                            )
                            .groupBy(obs),
                    tp(obs, HAS_VALUE, value)
            );
            conn.prepareGraphQuery(query.getQueryString()).evaluate().forEach(builder::process);
            return builder.build();
        }, this.context.getExecutor());
        return Mono.fromFuture(future);
    }

    public boolean hasType(IRI desiredType) {
        return types.stream().anyMatch(type -> type.equals(desiredType));
    }

    public Optional<Value> getProperty(IRI predicate) {
        for (var st : model.getStatements(propertyAffordanceIRI, predicate, null)) {
            return Optional.of(st.getObject());
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "ThingPropertyAffordance{" +
                "propertyAffordanceIRI=" + propertyAffordanceIRI +
                ", types=" + types +
                ", context=" + context +
                '}';
    }

    public ConnectionContext getContext() {
        return context;
    }
}
