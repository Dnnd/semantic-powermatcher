package ru.agentlab.semantic.wot.api;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.*;

public interface Action<I, O, M extends Metadata<M>> {
    void setMetadata(M metadata);

    M getMetadata();

    I getInput();

    O getOutput();

    default Model toModel(Resource... context) {
        var model = getMetadata().toModel(context);
        IRI invocationIRI = getMetadata().getIRI();
        var input = getInput();
        if (input != null) {
            model.add(invocationIRI,
                      HAS_INPUT,
                      Values.literal(input),
                      context
            );
        }
        var output = getOutput();
        if (output != null) {
            model.add(invocationIRI,
                      HAS_OUTPUT,
                      Values.literal(output),
                      context
            );
        }
        return model;

    }
}
