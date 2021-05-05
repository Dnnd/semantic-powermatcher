package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;

import java.util.List;

public class ThingActionAffordance {
    private final IRI actionAffordanceIRI;
    private final List<IRI> types;
    private final IRI inputSchema;
    private final IRI outputSchema;
    private final Model model;
    private final IRI thingIRI;


    public ThingActionAffordance(IRI thingIRI,
                                 IRI propertyAffordance,
                                 List<IRI> types,
                                 IRI inputSchema,
                                 IRI outputSchema,
                                 Model model) {
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
