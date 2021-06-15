package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.Statement;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.MetadataParser;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.api.ObservationParser;

import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_SIMPLE_RESULT;

public class LongObservationParser<M extends Metadata<M>> implements ObservationParser<Long, M> {

    private final MetadataParser<M> metadataParser;
    private Long value;

    public LongObservationParser(MetadataParser<M> builder) {
        metadataParser = builder;
    }

    @Override
    public LongObservationParser<M> process(Statement st) {
        metadataParser.process(st);
        if (st.getPredicate().equals(HAS_SIMPLE_RESULT)) {
            value = Long.parseLong(st.getObject().stringValue());
        }
        return this;
    }

    @Override
    public Observation<Long, M> build() {
        LongObservation<M> obs = new LongObservation<>(value);
        obs.setMetadata(metadataParser.build());
        return obs;
    }
}
