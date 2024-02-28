import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.engine.DefaultProducerTemplate;
import org.apache.camel.test.junit5.params.Test;

@QuarkusTest
public class ElasticsearchIndexTest {

    @Test
    void testIndex() {



    }

}
