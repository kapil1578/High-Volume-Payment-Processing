package com.hvpps.ingestion;

import com.hvpps.ingestion.idempotency.SqlIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class SqlIdempotencyConcurrencyTest {
    @Test
    void concurrentRequestsHaveExactlyOneReservationWinner() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE payment_idempotency (request_id VARCHAR(128) PRIMARY KEY)");
        var store = new SqlIdempotencyStore(jdbc);
        var workers = Executors.newFixedThreadPool(16);
        var gate = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        try {
            for (int i = 0; i < 32; i++) results.add(workers.submit(() -> {
                gate.await();
                return store.registerIfAbsent("same-request");
            }));
            gate.countDown();
            int winners = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) winners++;
            assertThat(winners).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_idempotency", Integer.class)).isEqualTo(1);
            assertThat(store.registerIfAbsent("different-request")).isTrue();
        } finally {
            workers.shutdownNow();
            jdbc.execute("DROP TABLE payment_idempotency");
        }
    }
}
