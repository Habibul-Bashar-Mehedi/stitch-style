package com.stitchstyle.app.scheduler;

import com.stitchstyle.app.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ChatRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ChatRetentionJob.class);
    private static final long LOCK_KEY = 4_811_202_611L;
    private static final long RETENTION_DAYS = 30;

    private final ChatMessageRepository messageRepository;
    private final JdbcTemplate jdbcTemplate;

    public ChatRetentionJob(ChatMessageRepository messageRepository, JdbcTemplate jdbcTemplate) {
        this.messageRepository = messageRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge() {
        Boolean locked = jdbcTemplate.queryForObject("select pg_try_advisory_lock(?)", Boolean.class, LOCK_KEY);
        if (!Boolean.TRUE.equals(locked)) return;
        try {
            Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
            int deleted = messageRepository.deleteOlderThan(cutoff);
            if (deleted > 0) log.info("chat retention purged {} messages older than {}", deleted, cutoff);
        } finally {
            jdbcTemplate.queryForObject("select pg_advisory_unlock(?)", Boolean.class, LOCK_KEY);
        }
    }
}
