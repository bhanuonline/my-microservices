package com.example.auth.crypto;

import com.example.auth.config.FeatureFlags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

import java.net.URI;

/**
 * Wires the {@code VaultTemplate} that {@link VaultSigningKeyStore} uses.
 *
 * <p>Only created when {@code features.kms-keys.backend=vault} — on JPA backend
 * this bean doesn't exist, so there's no Vault dependency at runtime.
 *
 * <p>Wired via {@code @ConditionalOnProperty}: Spring reads the property BEFORE
 * bean instantiation, and skips this bean entirely if the value isn't 'vault'.
 * That's why {@link SigningKeyStore} gets an {@code ObjectProvider<VaultTemplate>}
 * — it can gracefully see 'no bean available' on JPA runs.
 */
@Configuration
@Profile("jdbc")
public class VaultConfig {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "features.kms-keys.backend", havingValue = "vault")
    public VaultTemplate vaultTemplate(FeatureFlags flags) {
        FeatureFlags.KmsKeys.Vault v = flags.getKmsKeys().getVault();
        VaultEndpoint endpoint = VaultEndpoint.from(URI.create(v.getUri()));
        return new VaultTemplate(endpoint, new TokenAuthentication(v.getToken()));
    }
}
