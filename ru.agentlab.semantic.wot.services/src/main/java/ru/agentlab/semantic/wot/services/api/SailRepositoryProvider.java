package ru.agentlab.semantic.wot.services.api;

import org.eclipse.rdf4j.repository.sail.SailRepository;

public interface SailRepositoryProvider {
    SailRepository getRepository();
}
