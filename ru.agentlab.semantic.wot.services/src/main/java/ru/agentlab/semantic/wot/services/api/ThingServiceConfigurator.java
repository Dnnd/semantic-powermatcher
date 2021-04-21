package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.model.IRI;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;

import java.util.Dictionary;

public interface ThingServiceConfigurator {
    String SERVICE_CONFIGURATION_PID = "serviceConfigurationPID";
    String SERVICE_BUNDLE_ID = "serviceBundleID";
    String SERVICE_TYPE = "serviceType";
    String MODEL_IRI_PROPERTY = "modelIRI";

    Dictionary<String, ?> getConfiguration(Thing thing, ConnectionContext context);

    IRI getModelIRI();

    String getConfigurationPID();

    String getBundleID();

    ServiceType getServiceType();
}
