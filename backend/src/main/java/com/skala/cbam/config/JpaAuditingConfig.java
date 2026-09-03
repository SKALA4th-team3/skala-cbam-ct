package com.skala.cbam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity 의 @CreatedDate · @LastModifiedDate 를 동작시킨다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
