package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.Observation;

import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_RESULT;

public class IRIObservation<M extends Metadata<M>> implements Observation<IRI, M> {
    private M metadata;
    private final IRI value;

    public IRIObservation(IRI value) {
        this.value = value;
    }

    @Override
    public void setMetadata(M metadata) {
        this.metadata = metadata;
    }

    @Override
    public M getMetadata() {
        return metadata;
    }

    @Override
    public IRI getValue() {
        return value;
    }

    @Override
    public IRI getResultType() {
        return HAS_RESULT;
    }

    @Override
    public String toString() {
        return "IRIObservation{" +
                "metadata=" + metadata +
                ", value=" + value +
                '}';
    }
}
