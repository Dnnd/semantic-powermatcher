package ru.agentlab.semantic.wot.actions;

import org.eclipse.rdf4j.model.Statement;
import ru.agentlab.semantic.wot.api.Action;
import ru.agentlab.semantic.wot.api.ActionParser;
import ru.agentlab.semantic.wot.api.Metadata;
import ru.agentlab.semantic.wot.api.MetadataParser;

import static ru.agentlab.semantic.wot.vocabularies.Vocabularies.HAS_INPUT;

public class FloatSetterParser<M extends Metadata<M>> implements ActionParser<Float, Void, M> {

    private final MetadataParser<M> metadataParser;
    private Float value;

    public FloatSetterParser(MetadataParser<M> builder) {
        metadataParser = builder;
    }

    @Override
    public ActionParser<Float, Void, M> process(Statement st) {
        metadataParser.process(st);
        if (st.getPredicate().equals(HAS_INPUT)) {
            value = Float.parseFloat(st.getObject().stringValue());
        }
        return this;
    }

    @Override
    public Action<Float, Void, M> build() {
        Action<Float, Void, M> action = new FloatSetter<>(value);
        action.setMetadata(metadataParser.build());
        return action;
    }
}
