package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.RDF;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_THING_MODEL;

public class Thing {
    private final IRI thingIRI;
    private final IRI thingModelIRI;
    private final Model model;
    private final List<IRI> types;

    public Thing(IRI thingIRI, IRI thingModelIRI, List<IRI> types, Model model) {
        this.thingIRI = thingIRI;
        this.types = types;
        this.model = model;
        this.thingModelIRI = thingModelIRI;
    }

    public Thing(IRI thingIRI, Model model) {
        this.thingIRI = thingIRI;
        this.model = model;
        this.types = model.filter(thingIRI, RDF.TYPE, null)
                .stream()
                .map(st -> (IRI) st.getSubject())
                .collect(Collectors.toList());
        this.thingModelIRI = model.filter(thingIRI, HAS_THING_MODEL, null)
                .stream()
                .findFirst()
                .map(st -> (IRI) st.getObject()).orElseThrow();
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

    public IRI getThingModelIRI() {
        return thingModelIRI;
    }
}
