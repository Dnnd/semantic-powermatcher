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

    public Thing(IRI thingIRI, List<IRI> types, Model model) {
        this.thingIRI = thingIRI;
        this.types = types;
        this.model = model;
    }

    public Optional<Value> getProperty(IRI predicate) {
        for (var st : model.getStatements(thingIRI, predicate, null)) {
            return Optional.of(st.getObject());
        }
        return Optional.empty();
    }

    public IRI getIRI() {
        return thingIRI;
    }

    public List<IRI> getTypes() {
        return types;
    }
}
