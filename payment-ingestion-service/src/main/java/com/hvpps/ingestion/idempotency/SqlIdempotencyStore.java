package com.hvpps.ingestion.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hvpps.idempotency.store", havingValue = "sql")
public class SqlIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbcTemplate;

    public SqlIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean registerIfAbsent(String requestId) {
        try {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO payment_idempotency (request_id) VALUES (?)",
                    requestId
            );
            return inserted > 0;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }
}
