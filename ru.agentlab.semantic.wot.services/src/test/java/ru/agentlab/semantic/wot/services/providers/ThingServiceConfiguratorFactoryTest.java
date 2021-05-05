package ru.agentlab.semantic.wot.services.providers;


import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.config.SailRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import ru.agentlab.changetracking.sail.ChangeTrackingFactory;
import ru.agentlab.changetracking.utils.EmbeddedChangetrackingRepo;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ThingServiceConfiguratorFactoryTest {
    @Mock
    ConfigurationAdmin admin;

    @Mock
    Configuration config;

    @Captor
    ArgumentCaptor<String> arg1captor;

    @Captor
    ArgumentCaptor<String> arg2captor;

    @Test
    void test() throws IOException, InterruptedException {
        Dictionary<String, Object> props = new Hashtable<>();
        when(config.getProperties()).thenReturn(props);

        when(admin.createFactoryConfiguration(any(), any()))
                .thenReturn(config);

        var configuratorFactory = new ThingServiceConfiguratorFactory();

        SailRegistry.getInstance().add(new ChangeTrackingFactory());
        configuratorFactory.bindConfigurationAdmin(admin);

        try (EmbeddedChangetrackingRepo repo = EmbeddedChangetrackingRepo.makeTempRepository("test")) {
            try (var conn = repo.getConnection();
                 var configuratorsTTL = ThingServiceConfiguratorFactoryTest.class.getClassLoader()
                                                                                 .getResourceAsStream(
                                                                                         "configurators.ttl")
            ) {

                Model configurators = Rio.parse(configuratorsTTL, RDFFormat.TURTLE);
                SailRepository repository = (SailRepository) conn.getRepository();
                conn.add(configurators);

                configuratorFactory.setSailRepository(() -> repository);
                configuratorFactory.activate(null);

                Thread.sleep(1000);
                System.out.println(props.size());
                verify(admin, times(2)).createFactoryConfiguration(any(), any());
                configuratorFactory.deactivate();
            }
        }
    }
}
