package com.hvpps.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit development entry point. Test dependencies never enter production artifacts. */
public final class LocalDemoApplication {
    private LocalDemoApplication() { }

    public static void main(String[] args) throws Exception {
        var broker = new EmbeddedKafkaKraftBroker(1, 12, "payments.v1", "payments.v1.dlq");
        Runtime.getRuntime().addShutdownHook(new Thread(broker::destroy));
        broker.afterPropertiesSet();
        Path state = Path.of("target", "local-demo");
        Files.createDirectories(state);
        Files.writeString(state.resolve("broker.txt"), broker.getBrokersAsString());
        SpringApplication.run(PaymentIngestionApplication.class,
                "--server.address=127.0.0.1", "--server.port=8081",
                "--spring.kafka.bootstrap-servers=" + broker.getBrokersAsString(),
                "--hvpps.idempotency.store=sql", "--spring.sql.init.mode=always",
                "--spring.datasource.url=jdbc:h2:mem:local-demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--management.health.redis.enabled=false");
    }
}
