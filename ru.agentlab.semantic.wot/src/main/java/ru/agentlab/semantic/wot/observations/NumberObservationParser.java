package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.MetadataParser;
import ru.agentlab.semantic.wot.api.Observation;
import ru.agentlab.semantic.wot.api.ObservationParser;

import static ru.agentlab.semantic.wot.vocabularies.SSN.HAS_SIMPLE_RESULT;

public class NumberObservationParser<M extends Metadata<M>> implements ObservationParser<Number, M> {
    private final MetadataParser<M> metadataParser;
    private Number value;

    public NumberObservationParser(MetadataParser<M> metadataParser) {
        this.metadataParser = metadataParser;
    }

    @Override
    public ObservationParser<Number, M> process(Statement st) {
        metadataParser.process(st);
        if (st.getPredicate().equals(HAS_SIMPLE_RESULT)) {
            if (st.getObject() instanceof Literal) {
                Literal l = (Literal) st.getObject();
                var datatype = l.getDatatype();
                if (datatype.equals(XSD.LONG)) {
                    value = l.longValue();
                } else if (datatype.equals(XSD.FLOAT)) {
                    value = l.floatValue();
                } else if (datatype.equals(XSD.DOUBLE)) {
                    value = l.doubleValue();
                } else if (datatype.equals(XSD.INT)) {
                    value = l.intValue();
                } else if (datatype.equals(XSD.INTEGER)) {
                    value = l.integerValue();
                } else if (datatype.equals(XSD.SHORT)) {
                    value = l.shortValue();
                }
            }
        }
        return this;
    }

    @Override
    public Observation<Number, M> build() {
        var obs = new NumberObservation<M>(value);
        obs.setMetadata(metadataParser.build());
        return obs;
    }
}
