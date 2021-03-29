package ru.agentlab.semantic.wot.observation.api;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;

public interface ObservationFactory<T, M> {

    ObservationBuilder<T, M> createObservationBuilder(IRI observationIRI);

    default Observation<T, M> createObservation(IRI observationIRI, Model observationModel) {
        return createObservationBuilder(observationIRI).processAll(observationModel).build();
    }

}
