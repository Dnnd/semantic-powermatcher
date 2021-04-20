package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;

public class ThingModel {
    private final IRI thingModelIRI;

    public ThingModel(IRI thingModelIRI) {
        this.thingModelIRI = thingModelIRI;
    }

    public IRI getThingModelIRI() {
        return thingModelIRI;
    }

}
