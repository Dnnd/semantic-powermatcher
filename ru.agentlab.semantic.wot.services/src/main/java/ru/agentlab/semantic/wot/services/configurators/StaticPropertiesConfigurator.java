package ru.agentlab.semantic.wot.services.configurators;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.agentlab.semantic.wot.services.api.DeclarativeServiceLaunchConfiguration;
import ru.agentlab.semantic.wot.services.api.ThingServiceConfigurator;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.optional;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.semantic.wot.services.api.WotServicesVocabulary.*;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE,
           immediate = true,
           service = ThingServiceConfigurator.class)
@Designate(ocd = StaticPropertiesConfigurator.Config.class, factory = true)
public class StaticPropertiesConfigurator implements ThingServiceConfigurator {
    private final Logger logger = LoggerFactory.getLogger(StaticPropertiesConfigurator.class);
    private Config config;

    @Activate
    public void activate(Config config) {
        logger.info("Activating StaticPropertiesConfigurator {}...", config.configuratorIRI());
        this.config = config;
        logger.info("Activating StaticPropertiesConfigurator {}... Done", config.configuratorIRI());
    }

    @Override
    public DeclarativeServiceLaunchConfiguration getServiceLaunchConfiguration(Thing thing, ConnectionContext context) {
        var propName = var("propName");
        var propValue = var("propValue");
        var serviceID = var("serviceID");
        var bundleID = var("bundleID");
        var propIRI = var("propIRI");

        var configuratorIRI = iri(config.configuratorIRI());

        var serviceConfigBuilder = new DeclarativeServiceLaunchConfiguration.Builder();

        var query = Queries.CONSTRUCT(
                tp(configuratorIRI, CONFIGURES_SERVICE, serviceID),
                tp(configuratorIRI, CONFIGURES_SERVICE_IN_BUNDLE, bundleID),
                tp(configuratorIRI, HAS_CONFIGURATION_PROPERTY, propIRI),
                tp(propName, CONFIGURATION_PROPERTY_VALUE, propValue)
        ).where(
                tp(configuratorIRI, CONFIGURES_SERVICE, serviceID),
                tp(configuratorIRI, CONFIGURES_SERVICE_IN_BUNDLE, bundleID),
                optional(
                        tp(configuratorIRI, HAS_CONFIGURATION_PROPERTY, propIRI),
                        tp(propIRI, CONFIGURATION_PROPERTY_NAME, propName),
                        tp(propIRI, CONFIGURATION_PROPERTY_VALUE, propValue)
                )
        );
        var conn = context.getConnection();
        try (var res = conn.prepareGraphQuery(query.getQueryString()).evaluate()) {
            return serviceConfigBuilder.setFromStatements(res)
                                       .addProperty(THING_IRI_PROPERTY, thing.getIRI())
                                       .build();
        }
    }

    @Override
    public String getConfiguratorPID() {
        return config.servicePID();
    }

    @Override
    public IRI getModelIRI() {
        return iri(config.modelIri());
    }

    @ObjectClassDefinition(name = "Static Properties Configurator")
    public @interface Config {
        @AttributeDefinition(name = MODEL_IRI_PROPERTY)
        String modelIri();

        @AttributeDefinition(name = CONFIGURATOR_IRI_PROPERTY)
        String configuratorIRI();

        @AttributeDefinition
        String servicePID();
    }


}
