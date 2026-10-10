package com.example.common.featureflag;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Opt-in via {@code feature-flags.enabled=true}.
 * Services wire JPA and web (already there via common-lib + their own starter).
 */
@AutoConfiguration
@ConditionalOnClass({org.springframework.data.jpa.repository.JpaRepository.class})
@ConditionalOnProperty(prefix = "feature-flags", name = "enabled", havingValue = "true")
@EntityScan(basePackages = "com.example.common.featureflag")
@EnableJpaRepositories(basePackages = "com.example.common.featureflag")
@ComponentScan(basePackages = "com.example.common.featureflag")
public class FeatureFlagAutoConfiguration {
}
