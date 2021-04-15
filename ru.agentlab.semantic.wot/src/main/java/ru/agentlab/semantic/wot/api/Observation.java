package ru.agentlab.semantic.wot.api;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.util.Values;

import static ru.agentlab.semantic.wot.vocabularies.SSN.OBSERVED_PROPERTY;

public interface Observation<T, M extends Metadata<M>> {
    void setMetadata(M metadata);

    M getMetadata();

    T getValue();

    IRI getResultType();

    default Model toModel(Resource... context) {
        var metadata = getMetadata();
        var metadataModel = metadata.toModel(context);
        metadataModel.add(metadata.getIRI(), getResultType(), Values.literal(getValue()), context);
        metadataModel.add(metadata.getIRI(), OBSERVED_PROPERTY, metadata.getAffordanceIRI(), context);
        return metadataModel;
    }
}

