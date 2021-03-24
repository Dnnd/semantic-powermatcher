package ru.agentlab.semantic.wot.observations;

import org.eclipse.rdf4j.model.Statement;
import ru.agentlab.semantic.wot.observation.api.Action;
import ru.agentlab.semantic.wot.observation.api.ActionBuilder;
import ru.agentlab.semantic.wot.observation.api.MetadataBuilder;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_INPUT;

public class FloatSetterBuilder<M> implements ActionBuilder<Float, Void, M> {

    private final MetadataBuilder<M> metadataBuilder;
    private Float value;

    public FloatSetterBuilder(MetadataBuilder<M> builder) {
        metadataBuilder = builder;
    }

    @Override
    public ActionBuilder<Float, Void, M> process(Statement st) {
        metadataBuilder.process(st);
        if (st.getPredicate().equals(HAS_INPUT)) {
            value = Float.parseFloat(st.getObject().stringValue());
        }
        return this;
    }

    @Override
    public Action<Float, Void, M>  build() {
        Action<Float, Void, M> action = new FloatSetter<>(value);
        action.setMetadata(metadataBuilder.build());
        return action;
    }
}
