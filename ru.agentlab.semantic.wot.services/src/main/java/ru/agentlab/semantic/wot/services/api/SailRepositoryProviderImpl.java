package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.repository.manager.LocalRepositoryManager;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.config.SailRepositoryConfig;
import org.eclipse.rdf4j.sail.nativerdf.config.NativeStoreConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import ru.agentlab.changetracking.sail.ChangeTrackerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = SailRepositoryProviderImpl.Config.class)
public class SailRepositoryProviderImpl implements SailRepositoryProvider {
    private LocalRepositoryManager repositoryManager;
    private SailRepository repo;

    @ObjectClassDefinition
    @interface Config {
        String baseDir();

        String repoID();
    }

    @Activate
    public void activate(Config config) throws IOException {
        File baseDir = new File(config.baseDir());
        Files.createDirectories(baseDir.toPath());

        repositoryManager = new LocalRepositoryManager(baseDir);
        repositoryManager.init();

        ChangeTrackerConfig trackerConfig = new ChangeTrackerConfig(new NativeStoreConfig());
        trackerConfig.setInteractiveNotifications(true);
        repositoryManager.addRepositoryConfig(new RepositoryConfig(
                config.repoID(),
                new SailRepositoryConfig(trackerConfig)
        ));
        repo = (SailRepository) repositoryManager.getRepository(config.repoID());
    }

    @Deactivate
    public void deactivate() {
        repositoryManager.shutDown();
    }

    @Override
    public SailRepository getRepository() {
        return repo;
    }
}
