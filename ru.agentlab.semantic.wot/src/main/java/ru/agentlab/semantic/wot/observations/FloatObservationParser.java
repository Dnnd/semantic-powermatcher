package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.Statement;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.api.ObservationParser;
import ru.agentlab.semantic.wot.api.MetadataParser;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_VALUE;

public class FloatObservationParser<M extends Metadata<M>> implements ObservationParser<Float, M> {
    private final MetadataParser<M> metadataParser;
    private Float value;

    public FloatObservationParser(MetadataParser<M> builder) {
        metadataParser = builder;
    }

    @Override
    public FloatObservationParser<M> process(Statement st) {
        metadataParser.process(st);
        if (st.getPredicate().equals(HAS_VALUE)) {
            value = Float.parseFloat(st.getObject().stringValue());
        }
        return this;
    }

    @Override
    public Observation<Float, M> build() {
        FloatObservation<M> obs = new FloatObservation<>(value);
        obs.setMetadata(metadataParser.build());
        return obs;
    }
}
