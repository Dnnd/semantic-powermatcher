package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.Statement;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observation.api.ObservationBuilder;
import ru.agentlab.semantic.wot.observation.api.MetadataBuilder;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_VALUE;

public class FloatObservationBuilder<M> implements ObservationBuilder<Float, M> {
    private final MetadataBuilder<M> metadataBuilder;
    private Float value;

    public FloatObservationBuilder(MetadataBuilder<M> builder) {
        metadataBuilder = builder;
    }

    @Override
    public FloatObservationBuilder<M> process(Statement st) {
        metadataBuilder.process(st);
        if (st.getPredicate().equals(HAS_VALUE)) {
            value = Float.parseFloat(st.getObject().stringValue());
        }
        return this;
    }

    @Override
    public Observation<Float, M> build() {
        FloatObservation<M> obs = new FloatObservation<>(value);
        obs.setMetadata(metadataBuilder.build());
        return obs;
    }
}
