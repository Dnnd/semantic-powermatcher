package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;

import java.util.List;
import java.util.Optional;

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
