import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("core") // Test Primary Data Source
public class PrimaryDataSourceTest {

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource dataSource;

    @Test
    void testPrimaryDataSource() {
        assertNotNull(dataSource);
        // Add assertions to verify connection, e.g., execute a query
    }
}
