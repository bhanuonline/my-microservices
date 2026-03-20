package interview.spring.core.service;

import interview.spring.core.config.routingdb.ReadOnlyConnection;
import interview.spring.core.model.primary.User;
import interview.spring.core.repository.primary.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // WRITE → MASTER
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    // READ → REPLICA
    @ReadOnlyConnection
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}