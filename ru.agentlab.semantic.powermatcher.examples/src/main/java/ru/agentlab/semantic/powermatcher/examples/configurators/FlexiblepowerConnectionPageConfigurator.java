package ru.agentlab.semantic.powermatcher.examples.configurators;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.util.Literals;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import ru.agentlab.semantic.wot.services.api.ServiceType;
import ru.agentlab.semantic.wot.services.api.ThingServiceConfigurator;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;

import java.util.Dictionary;
import java.util.Hashtable;

import static org.eclipse.rdf4j.model.util.Values.iri;
import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp;
import static ru.agentlab.semantic.wot.services.api.WotServicesVocabulary.*;

@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = FlexiblepowerConnectionPageConfigurator.Config.class, factory = true)
public class FlexiblepowerConnectionPageConfigurator implements ThingServiceConfigurator {

    @ObjectClassDefinition
    public @interface Config {
        @AttributeDefinition(name = ThingServiceConfigurator.MODEL_IRI_PROPERTY)
        String modelIRI();

        @AttributeDefinition(name = CONFIGURATOR_IRI_PROPERTY)
        String configuratorIRI();
    }

    private Config config;

    @Activate
    public void activate(Config config) {
        this.config = config;
    }

    @Override
    public Dictionary<String, ?> getConfiguration(Thing thing, ConnectionContext context) {
        Dictionary<String, Object> serviceConfiguration = new Hashtable<>();
        var propName = var("propName");
        var propValue = var("propValue");
        var propIRI = var("propIRI");
        var configuratorIRI = iri(config.configuratorIRI());
        var query = Queries.SELECT(propName, propValue).where(
                tp(configuratorIRI, HAS_CONFIGURATION_PROPERTY, propIRI),
                tp(propIRI, CONFIGURATION_PROPERTY_NAME, propName),
                tp(propIRI, CONFIGURATION_PROPERTY_VALUE, propValue)
        );
        var conn = context.getConnection();
        try (var res = conn.prepareTupleQuery(query.getQueryString()).evaluate()) {
            for (BindingSet bindings : res) {
                String property = bindings.getValue("propName").stringValue();
                var value = bindings.getValue("propValue");
                switch (property) {
                    case "felixPluginActive", "dashboardWidgetActive" -> serviceConfiguration.put(property, value.stringValue());
                }
            }

        }
        return serviceConfiguration;
    }

    @Override
    public IRI getModelIRI() {
        return iri(config.modelIRI());
    }

    @Override
    public String getConfigurationPID() {
        return "org.flexiblepower.runtime.ui.connectionspage.ConnectionsPage";
    }

    @Override
    public String getBundleID() {
        return "flexiblepower.ui.connectionspage";
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.SINGLETON;
    }
}
