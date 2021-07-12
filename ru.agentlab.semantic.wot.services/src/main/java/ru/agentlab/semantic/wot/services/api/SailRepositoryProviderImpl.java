package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.repository.manager.LocalRepositoryManager;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.io.IOException;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE, service = SailRepositoryProvider.class)
@Designate(ocd = SailRepositoryProviderImpl.Config.class)
public class SailRepositoryProviderImpl implements SailRepositoryProvider {
    private LocalRepositoryManager repositoryManager;
    private SailRepository repo;

    @ObjectClassDefinition
    @interface Config {
        String repoID();
    }

    @Reference
    public void bindLocalRepositoryManager(LocalRepositoryManager manager) {
        this.repositoryManager = manager;
    }

    @Activate
    public void activate(Config config) throws IOException {
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
