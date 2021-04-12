package ru.agentlab.semantic.wot.api;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.util.Values;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_VALUE;

public interface Observation<T, M extends Metadata<M>> {
    void setMetadata(M metadata);

    M getMetadata();

    T getValue();

    default Model toModel(Resource... context) {
        var metadata = getMetadata();
        var metadataModel = metadata.toModel(context);
        metadataModel.add(metadata.getIRI(), HAS_VALUE, Values.literal(getValue()), context);
        return metadataModel;
    }
}

