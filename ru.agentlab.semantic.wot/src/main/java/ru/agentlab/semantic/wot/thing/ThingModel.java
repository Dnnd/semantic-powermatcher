package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

public class ThingModel {
    private ConnectionContext context;

    public ConnectionContext getContext() {
        return this.context;
    }

}
