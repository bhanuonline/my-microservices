package com.example.paymentservice.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Looks up {@link PaymentProvider} beans by {@link PaymentProvider#name() name}.
 * All {@code @Component} providers Spring finds on the classpath get indexed
 * at construction. Stubs marked {@code @ConditionalOnProperty} only appear
 * when their enable flag is on, so the registry size reflects what's actually
 * configured.
 */
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> byName;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.byName = providers.stream().collect(Collectors.toMap(
                PaymentProvider::name,
                p -> p
        ));
    }

    public PaymentProvider require(String name) {
        PaymentProvider p = byName.get(name);
        if (p == null) {
            throw new IllegalArgumentException(
                    "Unknown payment provider: " + name
                    + ". Available: " + byName.keySet()
                    + ". If you expected a provider here, check its payment.<name>.enabled flag.");
        }
        return p;
    }

    public List<String> availableNames() {
        return List.copyOf(byName.keySet());
    }
}
