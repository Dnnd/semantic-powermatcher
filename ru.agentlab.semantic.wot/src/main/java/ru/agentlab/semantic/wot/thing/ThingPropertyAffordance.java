package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.agentlab.changetracking.filter.ChangetrackingFilter;
import ru.agentlab.changetracking.sail.ChangeTrackerConnection;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observation.api.ObservationBuilder;
import ru.agentlab.semantic.wot.observation.api.ObservationFactory;
import ru.agentlab.semantic.wot.utils.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.select;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_VALUE;

public class ThingPropertyAffordance {
    private final IRI thingIRI;
    private final IRI propertyAffordanceIRI;
    private List<IRI> types;
    private final Model model;

    public ThingPropertyAffordance(IRI thingIRI, IRI propertyAffordance, List<IRI> types, Model model) {
        this.thingIRI = thingIRI;
        this.propertyAffordanceIRI = propertyAffordance;
        this.types = types;
        this.model = model;
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
                "thingIRI=" + thingIRI +
                ", propertyAffordanceIRI=" + propertyAffordanceIRI +
                ", types=" + types +
                ", model=" + model +
                '}';
    }
}
