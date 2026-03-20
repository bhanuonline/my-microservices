import jakarta.activation.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("core-one") // Test Secondary Data Source
public class SecondaryDataSourceTest {

    @Autowired
    @Qualifier("secondaryDataSource")
    private DataSource dataSource;

    @Test
    void testSecondaryDataSource() {
        assertNotNull(dataSource);
        // Add assertions
    }
}


