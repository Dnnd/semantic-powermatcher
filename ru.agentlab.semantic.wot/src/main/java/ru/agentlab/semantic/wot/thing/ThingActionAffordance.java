package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import reactor.core.publisher.Mono;
import ru.agentlab.semantic.wot.observation.api.Action;
import ru.agentlab.semantic.wot.observation.api.ActionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

public class ThingActionAffordance {
    private final IRI actionAffordanceIRI;
    private final List<IRI> types;
    private final IRI inputSchema;
    private final IRI outputSchema;
    private final Model model;
    private final ConnectionContext context;
    private final IRI thingIRI;

    public static ThingActionAffordance of(IRI thingIRI, IRI affordanceIRI, ConnectionContext ctx) {
        Model model = new LinkedHashModel();
        List<IRI> types = new ArrayList<>();
        IRI inputSchema = null;
        IRI outputSchema = null;

        for (var st : ctx.getConnection().getStatements(affordanceIRI, null, null)) {
            model.add(st);
            if (st.getPredicate().equals(RDF.TYPE)) {
                types.add((IRI) st.getObject());
            } else if (st.getPredicate().equals(HAS_INPUT_SCHEMA)) {
                inputSchema = (IRI) st.getObject();
            } else if (st.getPredicate().equals(HAS_OUTPUT_SCHEMA)) {
                outputSchema = (IRI) st.getObject();
            }
        }
        return new ThingActionAffordance(thingIRI, affordanceIRI, types, inputSchema, outputSchema, model, ctx);
    }

    public static ThingActionAffordance byDescription(IRI thingIRI,
                                                      IRI modelAffordanceIRI,
                                                      ConnectionContext context) {
        SailRepositoryConnection repoConn = context.getConnection();
        Variable actionAffordanceIRI = SparqlBuilder.var("actionAffordanceIRI");
        Variable inputSchema = SparqlBuilder.var("inputSchema");
        Variable outputSchema = SparqlBuilder.var("outputSchema");
        Variable type = SparqlBuilder.var("type");

        var query = Queries.CONSTRUCT(
                tp(actionAffordanceIRI, DESCRIBED_BY, modelAffordanceIRI),
                tp(actionAffordanceIRI, RDF.TYPE, type),
                tp(actionAffordanceIRI, HAS_OUTPUT_SCHEMA, outputSchema),
                tp(actionAffordanceIRI, HAS_INPUT_SCHEMA, inputSchema)
        ).where(
                tp(actionAffordanceIRI, DESCRIBED_BY, modelAffordanceIRI),
                tp(actionAffordanceIRI, RDF.TYPE, type),
                tp(thingIRI, HAS_ACTION_AFFORDANCE, actionAffordanceIRI),
                optional(tp(actionAffordanceIRI, HAS_OUTPUT_SCHEMA, outputSchema)),
                optional(tp(actionAffordanceIRI, HAS_INPUT_SCHEMA, inputSchema))
        );
        try (var results = repoConn.prepareGraphQuery(query.getQueryString()).evaluate()) {
            Model model = new LinkedHashModel();
            List<IRI> types = new ArrayList<>();
            IRI inputSchemaIRI = null;
            IRI outputSchemaIRI = null;
            IRI affordanceIRI = null;
            for (var st : results) {
                model.add(st);
                if (st.getPredicate().equals(RDF.TYPE)) {
                    types.add((IRI) st.getObject());
                } else if (st.getPredicate().equals(HAS_INPUT_SCHEMA)) {
                    inputSchemaIRI = (IRI) st.getObject();
                } else if (st.getPredicate().equals(HAS_OUTPUT_SCHEMA)) {
                    outputSchemaIRI = (IRI) st.getObject();
                } else if (affordanceIRI == null) {
                    affordanceIRI = (IRI) st.getSubject();
                }
            }
            return new ThingActionAffordance(thingIRI,
                                             affordanceIRI,
                                             types,
                                             inputSchemaIRI,
                                             outputSchemaIRI,
                                             model,
                                             context
            );
        }
    }

    public ThingActionAffordance(IRI thingIRI, IRI propertyAffordance, List<IRI> types, IRI inputSchema, IRI outputSchema, Model model, ConnectionContext context) {
        this.actionAffordanceIRI = propertyAffordance;
        this.types = types;
        this.model = model;
        this.context = context;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.thingIRI = thingIRI;
    }

    public <I, O, M> Mono<Action<I, O, M>> latestInvocation(ActionBuilder<I, O, M> builder) {
        var conn = this.context.getConnection();
        CompletableFuture<Optional<Action<I, O, M>>> future = CompletableFuture.supplyAsync(() -> {
            Variable mostRecent = var("mostRecent");
            Variable lastModified = var("lastModified");
            Variable invocation = var("invocation");
            Variable output = var("output");
            Variable input = var("input");
            var query = Queries.CONSTRUCT(
                    tp(invocation, DESCRIBED_BY_AFFORDANCE, actionAffordanceIRI),
                    tp(invocation, MODIFIED, mostRecent),
                    tp(invocation, HAS_INPUT, input),
                    tp(invocation, HAS_OUTPUT, output)
            ).where(
                    select(Expressions.max(lastModified).as(mostRecent)).where(
                            tp(invocation, DESCRIBED_BY_AFFORDANCE, actionAffordanceIRI),
                            tp(invocation, MODIFIED, lastModified)
                    ),
                    tp(invocation, DESCRIBED_BY_AFFORDANCE, actionAffordanceIRI),
                    tp(invocation, MODIFIED, mostRecent),
                    optional(tp(invocation, HAS_INPUT, input)),
                    optional(tp(invocation, HAS_OUTPUT, output))
            );
            Model model = new LinkedHashModel();
            try (var results = conn.prepareGraphQuery(query.getQueryString()).evaluate()) {
                results.forEach(model::add);
            }
            if (model.size() == 0) {
                return Optional.empty();
            }
            builder.processAll(model);
            return Optional.ofNullable(builder.build());
        }, this.context.getExecutor());
        return Mono.fromFuture(future)
                .flatMap(Mono::justOrEmpty);
    }

    public IRI getInputSchema() {
        return inputSchema;
    }

    public IRI getOutputSchema() {
        return outputSchema;
    }

    public List<IRI> getTypes() {
        return types;
    }

    public ConnectionContext getContext() {
        return context;
    }

    public IRI getIRI() {
        return actionAffordanceIRI;
    }

    @Override
    public String toString() {
        return "ThingActionAffordance{" +
                "actionAffordanceIRI=" + actionAffordanceIRI +
                ", types=" + types +
                ", inputSchema=" + inputSchema +
                ", outputSchema=" + outputSchema +
                ", model=" + model +
                ", context=" + context +
                '}';
    }
}
