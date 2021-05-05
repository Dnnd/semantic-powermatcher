package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.model.IRI;

import static org.eclipse.rdf4j.model.util.Values.iri;

public interface WotServicesVocabulary {
    String WOT_SERVICES_IRI = "https://wot.services.agentlab.ru/#";
    IRI THING_SERVICE_CONFIGURATOR = iri(WOT_SERVICES_IRI, "ThingServiceConfigurator");

    IRI CONFIGURATOR_CONF_PID = iri(WOT_SERVICES_IRI, "configuratorPid");
    IRI CONFIGURATOR_BUNDLE_ID = iri(WOT_SERVICES_IRI, "configuratorBundleID");
    IRI CONFIGURATOR_SERVICE_TYPE = iri(WOT_SERVICES_IRI, "configuratorServiceType");

    IRI HAS_CONFIGURATION_PROPERTY = iri(WOT_SERVICES_IRI, "hasConfigurationProperty");
    IRI CONFIGURATION_PROPERTY_NAME = iri(WOT_SERVICES_IRI, "configurationPropertyName");
    IRI CONFIGURATION_PROPERTY_VALUE = iri(WOT_SERVICES_IRI, "configurationPropertyValue");

    IRI MODEL_IRI = iri(WOT_SERVICES_IRI, "modelIri");
    IRI SINGLETON = iri(WOT_SERVICES_IRI, "Singleton");
    IRI FACTORY = iri(WOT_SERVICES_IRI, "Factory");
}
