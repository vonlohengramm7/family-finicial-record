package com.familyledger.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 刷新各域的可重建状态快照。
 *
 * API 只读取已保存的快照；首次就绪和后续定时任务在服务端采集，避免读取请求触发外部调用。
 */
@Service
public class FamilyStateRefreshService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FamilyStateRefreshService.class);
    private final List<StateCollector> collectors;
    private final StateSnapshotService snapshotService;

    public FamilyStateRefreshService(List<StateCollector> collectors, StateSnapshotService snapshotService) {
        this.collectors = collectors;
        this.snapshotService = snapshotService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${family-state.refresh.fixed-delay-ms:300000}",
            initialDelayString = "${family-state.refresh.initial-delay-ms:300000}")
    public void refresh() {
        for (StateCollector collector : collectors) {
            String domain = collector.domain();
            try {
                snapshotService.store(domain, collector.collect());
            } catch (Exception exception) {
                LOGGER.warn("Family state collector failed: domain={}", domain, exception);
                snapshotService.store(domain, unavailable(domain));
            }
        }
    }

    private StateDomainResponse unavailable(String domain) {
        return StateDomainResponse.builder()
                .status(StateStatus.UNAVAILABLE)
                .source("state-collector:" + domain)
                .freshness(StateDomainResponse.Freshness.builder().ttlSeconds(0).build())
                .data(Map.of())
                .errorCode("STATE_COLLECTOR_FAILED")
                .errorMessage("状态采集器执行失败")
                .build();
    }
}
