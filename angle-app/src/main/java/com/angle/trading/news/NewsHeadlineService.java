package com.angle.trading.news;

import com.angle.trading.config.NewsFeedProperties;
import com.angle.trading.persistence.NewsHeadlineEntity;
import com.angle.trading.persistence.NewsHeadlineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Owns the news-headline lifecycle:
 *   1. Fetch each configured RSS feed
 *   2. Dedupe by URL, save new rows
 *   3. Trigger AI tagger to enrich saved rows
 *   4. Purge rows older than retentionDays
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsHeadlineService {

    private final NewsFeedProperties props;
    private final RssFeedFetcher fetcher;
    private final NewsHeadlineRepository repo;
    private final NewsSentimentTagger tagger;

    // ---------- fetch loop ----------

    /** Manual trigger — used by refresh endpoint and initial startup. */
    @Transactional
    public int refreshAll() {
        if (!props.isEnabled()) return 0;
        int inserted = 0;
        for (var source : props.getSources()) {
            inserted += fetchOne(source);
        }
        log.info("News feed: inserted {} new headlines across {} sources",
                inserted, props.getSources().size());
        return inserted;
    }

    private int fetchOne(NewsFeedProperties.Source source) {
        List<RssFeedFetcher.RssItem> items = fetcher.fetch(source.getUrl());
        if (items.isEmpty()) return 0;
        int inserted = 0;
        Instant now = Instant.now();
        for (RssFeedFetcher.RssItem it : items) {
            if (it.link() == null || it.link().isBlank()) continue;
            if (repo.findByUrl(it.link()).isPresent()) continue;   // dedupe
            NewsHeadlineEntity h = new NewsHeadlineEntity();
            h.setTitle(clip(it.title(), 490));
            h.setUrl(clip(it.link(), 490));
            h.setSourceName(source.getName());
            h.setSourceCategory(source.getCategory());
            h.setPublishedAt(it.publishedAt() != null ? it.publishedAt() : now);
            h.setFetchedAt(now);
            h.setAiTagged(false);
            repo.save(h);
            inserted++;
        }
        return inserted;
    }

    @Scheduled(fixedRateString = "#{@newsFeedProperties.refreshMinutes * 60 * 1000}")
    public void scheduledRefresh() {
        if (!props.isEnabled()) return;
        refreshAll();
        // Trigger AI tagger for whatever's still un-tagged
        if (props.isAiTaggingEnabled()) {
            tagger.tagBatch();
        }
    }

    /** Standalone AI-tagging pass — hits Claude on un-tagged rows only. */
    public int tagUntagged() {
        return tagger.tagBatch();
    }

    // ---------- retention ----------

    /** Daily cleanup at 00:10 IST. */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void purgeOld() {
        if (!props.isEnabled()) return;
        Instant cutoff = Instant.now().minusSeconds(props.getRetentionDays() * 86400L);
        int deleted = repo.deleteOlderThan(cutoff);
        if (deleted > 0) log.info("News feed: purged {} headlines older than {} days",
                deleted, props.getRetentionDays());
    }

    // ---------- queries used by controller ----------

    public List<NewsHeadlineEntity> findRecent(int hours, String source, String sentiment,
                                                String sector, int limit) {
        return repo.findFiltered(
                Instant.now().minusSeconds(hours * 3600L),
                nullIfBlank(source),
                nullIfBlank(sentiment),
                nullIfBlank(sector),
                PageRequest.of(0, Math.max(1, limit)));
    }

    public long countLast24h() {
        return repo.countByFetchedAtAfter(Instant.now().minusSeconds(86400));
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
