package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import ru.agentlab.semantic.wot.observation.api.Observation;
import ru.agentlab.semantic.wot.observation.api.ObservationBuilder;
import ru.agentlab.semantic.wot.observation.api.MetadataBuilder;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_VALUE;

public class IRIObservationBuilder<M> implements ObservationBuilder<IRI, M> {
    private final MetadataBuilder<M> metadataBuilder;
    private IRI value;

    public IRIObservationBuilder(MetadataBuilder<M> builder) {
        this.metadataBuilder = builder;

    }

    @Override
    public ObservationBuilder<IRI, M> process(Statement st) {
        metadataBuilder.process(st);
        if (st.getPredicate().equals(HAS_VALUE)) {
            value = (IRI) st.getObject();
        }
        return this;
    }

    @Override
    public Observation<IRI, M> build() {
        IRIObservation<M> observation = new IRIObservation<>(value);
        observation.setMetadata(metadataBuilder.build());
        return observation;
    }
}
