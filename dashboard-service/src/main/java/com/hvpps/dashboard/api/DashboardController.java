package com.hvpps.dashboard.api;

import com.hvpps.dashboard.core.DashboardDataService;
import com.hvpps.dashboard.core.model.DashboardOverview;
import com.hvpps.dashboard.core.model.FailedTxn;
import com.hvpps.dashboard.core.model.KafkaPartitionStats;
import com.hvpps.dashboard.core.model.LiveMetric;
import com.hvpps.dashboard.core.model.TelemetrySeries;
import com.hvpps.dashboard.core.model.TransactionDetail;
import com.hvpps.dashboard.core.model.TransactionItem;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardDataService dashboardDataService;

    public DashboardController(DashboardDataService dashboardDataService) {
        this.dashboardDataService = dashboardDataService;
    }

    @GetMapping("/overview")
    public DashboardOverview overview() {
        return dashboardDataService.overview();
    }

    @GetMapping("/transactions")
    public List<TransactionItem> transactions() {
        return dashboardDataService.transactions();
    }

    @GetMapping("/transactions/catalog")
    public List<TransactionItem> catalogedTransactions() {
        return dashboardDataService.catalogedTransactions();
    }

    @GetMapping("/transactions/{transactionId}")
    public TransactionDetail transactionDetail(@PathVariable String transactionId) {
        return dashboardDataService.transactionDetail(transactionId);
    }

    @GetMapping("/kafka/partitions")
    public List<KafkaPartitionStats> kafkaPartitions() {
        return dashboardDataService.partitions();
    }

    @GetMapping("/failed")
    public List<FailedTxn> failedTransactions() {
        return dashboardDataService.failedTransactions();
    }

    @GetMapping("/telemetry")
    public TelemetrySeries telemetry(@RequestParam(name = "range", defaultValue = "hour") String range) {
        return dashboardDataService.telemetry(range);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                LiveMetric metric = dashboardDataService.liveMetric();
                emitter.send(SseEmitter.event().name("metric").data(metric));
            } catch (IOException ex) {
                emitter.complete();
                scheduler.shutdown();
            }
        }, 0, 2, TimeUnit.SECONDS);
        emitter.onCompletion(scheduler::shutdown);
        emitter.onTimeout(() -> {
            emitter.complete();
            scheduler.shutdown();
        });
        return emitter;
    }
}
