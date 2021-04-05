package ru.agentlab.semantic.wot.repositories;

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
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.thing.ThingActionAffordance;
import ru.agentlab.semantic.wot.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

public class ThingActionAffordanceRepository implements WotRepository {
    private final ConnectionContext context;

    public ThingActionAffordanceRepository(ConnectionContext context) {
        this.context = context;
    }

    public Mono<ThingActionAffordance> getActionAffordance(Thing thing, IRI affordanceIRI) {
        return Utils.supplyAsyncWithCancel(
                () -> getActionAffordanceSync(thing, affordanceIRI),
                context.getExecutor()
        );
    }

    public ThingActionAffordance getActionAffordanceSync(Thing thing, IRI affordanceIRI) {
        Model model = new LinkedHashModel();
        List<IRI> types = new ArrayList<>();
        IRI inputSchema = null;
        IRI outputSchema = null;

        for (var st : getConnectionContext().getConnection().getStatements(affordanceIRI, null, null)) {
            model.add(st);
            if (st.getPredicate().equals(RDF.TYPE)) {
                types.add((IRI) st.getObject());
            } else if (st.getPredicate().equals(HAS_INPUT_SCHEMA)) {
                inputSchema = (IRI) st.getObject();
            } else if (st.getPredicate().equals(HAS_OUTPUT_SCHEMA)) {
                outputSchema = (IRI) st.getObject();
            }
        }
        return new ThingActionAffordance(thing.getIRI(), affordanceIRI, types, inputSchema, outputSchema, model);
    }

    public Mono<ThingActionAffordance> byDescription(Thing thing, IRI modelAffordanceIRI) {
        return Utils.supplyAsyncWithCancel(
                () -> byDescriptionSync(thing, modelAffordanceIRI),
                context.getExecutor()
        );
    }

    public ThingActionAffordance byDescriptionSync(Thing thing, IRI modelAffordanceIRI) {
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
                tp(thing.getIRI(), HAS_ACTION_AFFORDANCE, actionAffordanceIRI),
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
            return new ThingActionAffordance(thing.getIRI(),
                                             affordanceIRI,
                                             types,
                                             inputSchemaIRI,
                                             outputSchemaIRI,
                                             model
            );
        }
    }

    public <I, O, M> Mono<Action<I, O, M>> latestInvocation(IRI actionAffordanceIRI, ActionBuilder<I, O, M> builder) {
        return Utils.supplyAsyncWithCancel(
                () -> latestInvocationSync(actionAffordanceIRI, builder),
                context.getExecutor()
        ).flatMap(Mono::justOrEmpty);
    }

    public <I, O, M> Optional<Action<I, O, M>> latestInvocationSync(IRI actionAffordanceIRI, ActionBuilder<I, O, M> builder) {
        var conn = this.context.getConnection();
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
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return context;
    }
}
