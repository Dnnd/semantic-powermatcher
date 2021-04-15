package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.api.ObservationParser;
import ru.agentlab.semantic.wot.api.MetadataParser;

import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_RESULT;

public class IRIObservationParser<M extends Metadata<M>> implements ObservationParser<IRI, M> {
    private final MetadataParser<M> metadataParser;
    private IRI value;

    public IRIObservationParser(MetadataParser<M> builder) {
        this.metadataParser = builder;

    }

    @Override
    public ObservationParser<IRI, M> process(Statement st) {
        metadataParser.process(st);
        if (st.getPredicate().equals(HAS_RESULT)) {
            value = (IRI) st.getObject();
        }
        return this;
    }

    @Override
    public Observation<IRI, M> build() {
        IRIObservation<M> observation = new IRIObservation<>(value);
        observation.setMetadata(metadataParser.build());
        return observation;
    }
}
