// Spring Cloud Contract DSL — defines the HTTP interaction between
// order-service (producer) and any consumer (e.g. graphql-bff, shop-ui).
//
// mvn verify generates a test from this contract that runs against
// the real controller. If the test passes, a stub jar is published
// so consumers can test against it without running the real service.

package contracts.orders

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "GET /api/v1/orders/{id} returns the order when it exists"

    request {
        method GET()
        url '/api/v1/orders/test-order-abc'
        headers {
            header('Accept', 'application/json')
        }
    }

    response {
        status OK()                          // 200
        headers {
            contentType(applicationJson())
        }
        body([
                orderId   : 'test-order-abc',
                productId : 1L,
                quantity  : 2,
                status    : 'CREATED',
                createdAt : $(anyIso8601DateTime())   // regex: matches any ISO-8601 timestamp
        ])
        bodyMatchers {
            jsonPath('$.orderId', byEquality())
            jsonPath('$.status', byRegex(/(CREATED|PAID|CANCELLED)/))
            jsonPath('$.createdAt', byRegex(isoDateTime()))
        }
    }
}
