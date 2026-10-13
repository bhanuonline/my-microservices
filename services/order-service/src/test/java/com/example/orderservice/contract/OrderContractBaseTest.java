package com.example.orderservice.contract;

import com.example.orderservice.controller.OrderController;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.service.ProductService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;

// Base class for ALL generated contract tests.
// The Maven plugin generates a test class per contract that extends this.
// Here we set up MockMvc with just the controller — no full Spring context needed.
//
// @WebMvcTest loads only the web layer: controller + MVC config.
// @MockBean replaces real services with Mockito mocks — no DB, no Feign calls.
@WebMvcTest(OrderController.class)
public abstract class OrderContractBaseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderRepository orderRepository;

    @BeforeEach
    void setup() {
        // Stub the repository call the controller uses for GET /api/v1/orders/{id}
        when(orderRepository.findById("test-order-abc"))
                .thenReturn(Optional.of(buildTestOrderResponse()));

        // Hand MockMvc to RestAssured — the generated contract tests use RestAssured internally
        RestAssuredMockMvc.mockMvc(mockMvc);
    }

    private com.example.orderservice.model.Order buildTestOrderResponse() {
        var order = new com.example.orderservice.model.Order(
                "test-order-abc", 1L, 2, java.math.BigDecimal.valueOf(99.99));
        return order;
    }
}
