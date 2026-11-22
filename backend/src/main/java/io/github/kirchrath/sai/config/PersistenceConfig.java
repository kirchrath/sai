package io.github.kirchrath.sai.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

/**
 * Aktiviert {@code @CreatedDate}/{@code @LastModifiedDate} fuer Spring Data JDBC.
 */
@Configuration(proxyBeanMethods = false)
@EnableJdbcAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class PersistenceConfig {

    /**
     * PostgreSQL speichert Zeitstempel mit Mikrosekunden. Ohne Kuerzung wuerde sich die gespeicherte Entity
     * vom wieder gelesenen Stand unterscheiden.
     */
    @Bean
    DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
