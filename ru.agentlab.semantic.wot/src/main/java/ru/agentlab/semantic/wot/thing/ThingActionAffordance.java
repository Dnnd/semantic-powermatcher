package ru.agentlab.semantic.wot.thing;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.RDF;

import java.util.ArrayList;
import java.util.List;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_INPUT_SCHEMA;
import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_OUTPUT_SCHEMA;

public class ThingActionAffordance {
    private final IRI propertyAffordanceIRI;
    private final List<IRI> types;
    private final IRI inputSchema;
    private final IRI outputSchema;
    private final Model model;
    private final ConnectionContext context;

    public static ThingActionAffordance of(IRI affordanceIRI, ConnectionContext ctx) {
        Model model = new LinkedHashModel();
        List<IRI> types = new ArrayList<>();
        IRI inputSchema = null;
        IRI outputSchema = null;

        for (var st : ctx.getConnection().getStatements(affordanceIRI, null, null)) {
            model.add(st);
            if (st.getPredicate().equals(RDF.TYPE)) {
                types.add((IRI) st.getObject());
            } else if (st.getPredicate().equals(HAS_INPUT_SCHEMA)) {
                inputSchema = (IRI) st.getObject();
            } else if (st.getPredicate().equals(HAS_OUTPUT_SCHEMA)) {
                outputSchema = (IRI) st.getObject();
            }
        }
        return new ThingActionAffordance(affordanceIRI, types, inputSchema, outputSchema, model, ctx);
    }

    public ThingActionAffordance(IRI propertyAffordance, List<IRI> types, IRI inputSchema, IRI outputSchema, Model model, ConnectionContext context) {
        this.propertyAffordanceIRI = propertyAffordance;
        this.types = types;
        this.model = model;
        this.context = context;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
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

    public ConnectionContext getContext() {
        return context;
    }

    public IRI getPropertyAffordanceIRI() {
        return propertyAffordanceIRI;
    }
}
