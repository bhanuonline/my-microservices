import jakarta.activation.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles({"core", "core-one", "core-two"})
public class MultiDataSourceIntegrationTest {

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    @Qualifier("secondaryDataSource")
    private DataSource secondaryDataSource;

    @Autowired
    @Qualifier("tertiaryDataSource")
    private DataSource tertiaryDataSource;

    @Test
    void testAllDataSources() {
        assertNotNull(primaryDataSource);
        assertNotNull(secondaryDataSource);
        assertNotNull(tertiaryDataSource);
    }
}
