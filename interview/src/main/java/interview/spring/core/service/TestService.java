package interview.spring.core.service;

import interview.spring.core.model.primary.User;
import interview.spring.core.model.secondary.Product;
import interview.spring.core.repository.primary.UserRepository;
import interview.spring.core.repository.secondary.ProductRepository;
import org.springframework.stereotype.Service;

@Service
public class TestService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public TestService(UserRepository userRepository,
                       ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public void testBoth() {

        userRepository.save(new User(null, "Bhanu"));
        productRepository.save(new Product(null, "Laptop"));

        System.out.println("Saved to both databases!");
    }
}