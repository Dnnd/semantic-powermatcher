package ru.agentlab.semantic.wot.services.providers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.eclipse.rdf4j.model.IRI;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import ru.agentlab.semantic.wot.repositories.ThingRepository;
import ru.agentlab.semantic.wot.services.api.SailRepositoryProvider;
import ru.agentlab.semantic.wot.services.api.ThingServiceConfigurator;
import ru.agentlab.semantic.wot.thing.ConnectionContext;
import ru.agentlab.semantic.wot.thing.Thing;
import ru.agentlab.semantic.wot.utils.Utils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

@Component(scope = ServiceScope.SINGLETON, immediate = true)
@Designate(ocd = ThingServiceFactory.Config.class)
public class ThingServiceFactory {
    private final List<ThingServiceConfigurator> preActivated = new CopyOnWriteArrayList<>();
    private final Map<IRI, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final Multimap<String, Configuration> configurations = ArrayListMultimap.create();
    private final Logger logger = LoggerFactory.getLogger(ThingServiceFactory.class);
    private volatile ConnectionContext context;
    private volatile ConfigurationAdmin configurationAdmin;

    @ObjectClassDefinition
    public @interface Config {
        // TODO: actually use this property
        boolean shutdownServicesOnDeactivate() default true;
    }

    @Reference
    public void bindSailRepositoryProvider(SailRepositoryProvider repositoryProvider) {
        var repo = repositoryProvider.getRepository();
        var executor = Executors.newSingleThreadExecutor();
        context = new ConnectionContext(executor, repo.getConnection());
    }

    @Reference
    public void bindConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Reference(
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE,
            unbind = "removeConfigurator")
    public void addConfigurator(ThingServiceConfigurator configurator) {
        logger.info("Adding ThingServiceConfigurator for {} ...", configurator.getModelIRI());
        if (context == null) {
            preActivated.add(configurator);
        } else {
            enableServiceDiscovery(configurator);
        }
        logger.info("Adding ThingServiceConfigurator for {} ... Done", configurator.getModelIRI());
    }

    public void removeConfigurator(ThingServiceConfigurator configurator) {
        logger.info("Removing ThingServiceConfigurator for {} ...", configurator.getModelIRI());
        for (Configuration configuration : configurations.get(configurator.getConfigurationPID())) {
            try {
                configuration.delete();
            } catch (IOException e) {
                logger.error("unable to delete configuration {}", configuration.getPid(), e);
            }
        }
        subscriptions.remove(configurator.getModelIRI()).dispose();
        logger.info("Removing ThingServiceConfigurator for {} ... Done", configurator.getModelIRI());
    }

    @Activate
    public void activate(Config ignore) {
        logger.info("Activating ThingService Factory...");
        preActivated.forEach(this::enableServiceDiscovery);
        logger.info("Activating ThingService Factory...Done");
    }

    @Deactivate
    public void deactivate() {
        subscriptions.values().forEach(Disposable::dispose);
        subscriptions.clear();
        configurations.values().forEach(configuration -> {
            try {
                configuration.delete();
            } catch (IOException e) {
                logger.error("unable to delete configuration {}", configuration.getPid());
            }
        });
    }

    private void enableServiceDiscovery(ThingServiceConfigurator configurator) {
        var repository = new ThingRepository(context);
        var subscription = repository.discoverDeploymentsOf(configurator.getModelIRI())
                                     .subscribe(thing -> Utils.supplyAsyncWithCancel(
                                             () -> registerThingServiceConfiguration(
                                                     configurator,
                                                     thing
                                             ), context.getExecutor())
                                     );
        subscriptions.putIfAbsent(configurator.getModelIRI(), subscription);
    }

    private void registerThingServiceConfiguration(ThingServiceConfigurator configurator, Thing thing) {
        try {
            var config = switch (configurator.getServiceType()) {
                case SINGLETON -> configurationAdmin.getConfiguration(
                        configurator.getConfigurationPID(),
                        configurator.getBundleID()
                );
                case FACTORY -> configurationAdmin.createFactoryConfiguration(
                        configurator.getConfigurationPID(),
                        configurator.getBundleID()
                );
            };
            var properties = configurator.getConfiguration(thing, context);
            configurations.put(config.getPid(), config);
            config.update(properties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
