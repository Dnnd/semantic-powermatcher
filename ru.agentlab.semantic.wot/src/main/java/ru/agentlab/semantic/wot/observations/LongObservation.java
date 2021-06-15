package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.Observation;

import java.util.Objects;
import java.util.StringJoiner;

import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_SIMPLE_RESULT;

public class LongObservation<M extends Metadata<M>> implements Observation<Long, M> {
    private final Long observedValue;
    private M metadata;

    public LongObservation(Long observedValue) {
        this.observedValue = observedValue;
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
    public Long getValue() {
        return observedValue;
    }

    @Override
    public IRI getResultType() {
        return HAS_SIMPLE_RESULT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LongObservation<?> that = (LongObservation<?>) o;
        return Objects.equals(observedValue, that.observedValue) && Objects.equals(
                metadata,
                that.metadata
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(observedValue, metadata);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", LongObservation.class.getSimpleName() + "[", "]")
                .add("observedValue=" + observedValue)
                .add("metadata=" + metadata)
                .toString();
    }
}
