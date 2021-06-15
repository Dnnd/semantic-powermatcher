package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.vocabularies.SSN;

public class NumberObservation<M extends Metadata<M>> implements Observation<Number, M> {
    private M metadata;
    private final Number value;

    public NumberObservation(Number observedValue) {
        this.value = observedValue;
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
    public Number getValue() {
        return value;
    }

    @Override
    public IRI getResultType() {
        return SSN.HAS_SIMPLE_RESULT;
    }
}
