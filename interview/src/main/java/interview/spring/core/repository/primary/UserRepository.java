package interview.spring.core.repository.primary;

import interview.spring.core.model.primary.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}