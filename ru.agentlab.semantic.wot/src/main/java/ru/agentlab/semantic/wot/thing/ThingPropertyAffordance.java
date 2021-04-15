package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Value;

import java.util.List;
import java.util.Optional;

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

    public IRI getThingIRI() {
        return thingIRI;
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
