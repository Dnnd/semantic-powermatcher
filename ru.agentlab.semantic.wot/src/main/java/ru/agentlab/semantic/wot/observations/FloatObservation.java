package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.Observation;

import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_SIMPLE_RESULT;

public class FloatObservation<M extends Metadata<M>> implements Observation<Float, M> {
    private final Float value;
    private M metadata;

    public FloatObservation(Float value) {
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
    public Float getValue() {
        return value;
    }

    @Override
    public IRI getResultType() {
        return HAS_SIMPLE_RESULT;
    }

    @Override
    public String toString() {
        return "FloatObservation{" +
                "value=" + value +
                ", metadata=" + metadata +
                '}';
    }
}
