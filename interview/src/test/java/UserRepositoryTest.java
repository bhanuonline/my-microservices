import interview.spring.core.model.primary.User;
import interview.spring.core.repository.primary.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("core") // ✅ Activate the profile for primary data source
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveAndFind() {
        User user = new User();
        user.setName("John Doe");
        userRepository.save(user);

        assertNotNull(userRepository.findById(user.getId()));
    }
}
