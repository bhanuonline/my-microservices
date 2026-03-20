import jakarta.activation.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("core-two") // Test Tertiary Data Source
public class TertiaryDataSourceTest {

    @Autowired
    @Qualifier("tertiaryDataSource")
    private DataSource dataSource;

    @Test
    void testTertiaryDataSource() {
        assertNotNull(dataSource);
        // Add assertions
    }
}