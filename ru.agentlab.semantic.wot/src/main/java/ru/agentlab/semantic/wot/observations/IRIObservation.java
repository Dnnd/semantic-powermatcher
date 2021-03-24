package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.observation.api.Observation;

public class IRIObservation<M> implements Observation<IRI, M> {
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
    public String toString() {
        return "IRIObservation{" +
                "metadata=" + metadata +
                ", value=" + value +
                '}';
    }
}
