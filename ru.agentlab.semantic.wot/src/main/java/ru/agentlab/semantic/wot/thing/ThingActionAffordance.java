package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import reactor.core.publisher.Mono;
import ru.agentlab.semantic.wot.observation.api.Action;
import ru.agentlab.semantic.wot.observation.api.ActionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_VALUE;

public class ThingActionAffordance {
    private final IRI actionAffordanceIRI;
    private final List<IRI> types;
    private final IRI inputSchema;
    private final IRI outputSchema;
    private final Model model;
    private final ConnectionContext context;

    public static ThingActionAffordance of(IRI affordanceIRI, ConnectionContext ctx) {
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
        return new ThingActionAffordance(affordanceIRI, types, inputSchema, outputSchema, model, ctx);
    }

    public ThingActionAffordance(IRI propertyAffordance, List<IRI> types, IRI inputSchema, IRI outputSchema, Model model, ConnectionContext context) {
        this.actionAffordanceIRI = propertyAffordance;
        this.types = types;
        this.model = model;
        this.context = context;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
    }

    public <I, O, M> Mono<Action<I, O, M>> latestInvocation(ActionBuilder<I, O, M> builder) {
        var conn = this.context.getConnection();
        CompletableFuture<Optional<Action<I, O, M>>> future = CompletableFuture.supplyAsync(() -> {
            Variable mostRecent = var("mostRecent");
            Variable lastModified = var("lastModified");
            Variable obs = var("obs");
            Variable output = var("output");
            Variable input = var("input");

            var query = Queries.CONSTRUCT(
                    tp(obs, DESCRIBED_BY_AFFORDANCE, actionAffordanceIRI),
                    tp(obs, MODIFIED, mostRecent),
                    tp(obs, HAS_INPUT, input),
                    tp(obs, HAS_OUTPUT, output)
            ).where(select(Expressions.max(lastModified).as(mostRecent))
                            .where(tp(obs, DESCRIBED_BY_AFFORDANCE, actionAffordanceIRI),
                                   tp(obs, MODIFIED, lastModified)
                            ),
                    optional(tp(obs, HAS_INPUT, input)),
                    optional(tp(obs, HAS_OUTPUT, output))
            );
            Model model = new LinkedHashModel();
            conn.prepareGraphQuery(query.getQueryString()).evaluate().forEach(model::add);
            if (model.size() == 0) {
                return Optional.empty();
            }
            return Optional.ofNullable(builder.build());
        }, this.context.getExecutor());
        return Mono.fromFuture(future)
                .flatMap(maybeLatestInvocation -> maybeLatestInvocation.map(Mono::just).orElseGet(Mono::empty));
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

    public IRI getActionAffordanceIRI() {
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
