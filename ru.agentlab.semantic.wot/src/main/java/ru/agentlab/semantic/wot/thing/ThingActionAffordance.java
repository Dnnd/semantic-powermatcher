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
import ru.agentlab.semantic.wot.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

public class ThingActionAffordance {
    private final IRI actionAffordanceIRI;
    private final List<IRI> types;
    private final IRI inputSchema;
    private final IRI outputSchema;
    private final Model model;
    private final IRI thingIRI;


    public ThingActionAffordance(IRI thingIRI, IRI propertyAffordance, List<IRI> types, IRI inputSchema, IRI outputSchema, Model model) {
        this.actionAffordanceIRI = propertyAffordance;
        this.types = types;
        this.model = model;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.thingIRI = thingIRI;
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

    public IRI getIRI() {
        return actionAffordanceIRI;
    }

    public IRI getThingIRI() {
        return thingIRI;
    }

    public Model getModel() {
        return model;
    }

    @Override
    public String toString() {
        return "ThingActionAffordance{" +
                "actionAffordanceIRI=" + actionAffordanceIRI +
                ", types=" + types +
                ", inputSchema=" + inputSchema +
                ", outputSchema=" + outputSchema +
                ", model=" + model +
                ", thingIRI=" + thingIRI +
                '}';
    }
}
