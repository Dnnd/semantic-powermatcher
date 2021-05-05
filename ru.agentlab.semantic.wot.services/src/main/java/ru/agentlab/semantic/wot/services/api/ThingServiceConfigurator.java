package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;

import java.util.Dictionary;

public interface ThingServiceConfigurator {
    String MODEL_IRI_PROPERTY = "modelIRI";
    String CONFIGURATOR_IRI_PROPERTY = "configuratorIRI";

    Dictionary<String, ?> getConfiguration(Thing thing, ConnectionContext context);

    IRI getModelIRI();

    String getConfigurationPID();

    String getBundleID();

    ServiceType getServiceType();
}
