package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;

public interface ThingServiceConfigurator {
    String MODEL_IRI_PROPERTY = "modelIRI";
    String CONFIGURATOR_IRI_PROPERTY = "configuratorIRI";
    String THING_IRI_PROPERTY = "thingIRI";

    DeclarativeServiceLaunchConfiguration getServiceLaunchConfiguration(Thing thing, ConnectionContext context);

    String getConfiguratorPID();

    IRI getModelIRI();
}
