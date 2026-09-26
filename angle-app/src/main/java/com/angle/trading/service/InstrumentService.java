package com.angle.trading.service;

import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.persistence.BiasInstrumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Source of truth for tracked instruments.
 *
 * On first boot, seeds itself from {@code bias.instruments[]} in
 * application.properties. After that, the DB is authoritative — the
 * properties list is ignored (edited via {@code /admin/instruments} UI instead).
 *
 * Callers should use {@link #listEnabled()} for anything user-facing
 * (warmer, dashboard, change detector) and {@link #listAll()} for admin views.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final BiasInstrumentRepository repository;
    private final BiasProperties biasProperties;

    /**
     * On startup: seed from properties if the DB is empty, then bind ourselves
     * as the LIVE source of {@link BiasProperties#getInstruments()} so every
     * downstream consumer (warmer, dashboard, change detector, ticker fetcher)
     * transparently reads from the DB from now on.
     */
    @PostConstruct
    @Transactional
    public void bootstrap() {
        long existing = repository.count();
        if (existing == 0) {
            List<BiasProperties.Instrument> fromProps = biasProperties.getInstrumentsFromProperties();
            if (fromProps != null && !fromProps.isEmpty()) {
                int prio = 10;
                for (BiasProperties.Instrument p : fromProps) {
                    BiasInstrumentEntity e = new BiasInstrumentEntity(
                            p.getSymbol(),
                            p.getBroker() == null ? "ANGEL" : p.getBroker(),
                            p.getExchange() == null ? "NSE" : p.getExchange().name(),
                            p.getSymbolToken(),
                            p.getIntradayInterval() == null ? "FIVE_MINUTE" : p.getIntradayInterval().name(),
                            true,
                            prio
                    );
                    repository.save(e);
                    prio += 10;
                }
                log.info("InstrumentService: seeded {} instruments from application.properties", fromProps.size());
            } else {
                log.info("InstrumentService: DB empty and no properties seed — starting fresh");
            }
        } else {
            log.info("InstrumentService: DB has {} instruments — properties list ignored", existing);
        }

        // Wire ourselves as the live source. From now on getInstruments() → DB.
        biasProperties.bindDbSource(this::listAsPropertyBeans);
        log.info("InstrumentService: bound as BiasProperties.getInstruments() supplier");
    }

    // ---------- read ----------

    @Transactional(readOnly = true)
    public List<BiasInstrumentEntity> listAll() {
        return repository.findAllByOrderByPriorityAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<BiasInstrumentEntity> listEnabled() {
        return repository.findByEnabledTrueOrderByPriorityAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Optional<BiasInstrumentEntity> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * Convert DB rows into the immutable value type BiasProperties exposes.
     * Called by {@link BiasProperties#getInstruments()} so downstream code
     * (warmer, dashboard) doesn't need to know DB exists.
     */
    @Transactional(readOnly = true)
    public List<BiasProperties.Instrument> listAsPropertyBeans() {
        return listEnabled().stream().map(InstrumentService::toBean).toList();
    }

    // ---------- write ----------

    @Transactional
    public BiasInstrumentEntity create(BiasInstrumentEntity in) {
        validate(in);
        if (repository.existsBySymbolToken(in.getSymbolToken())) {
            throw new IllegalArgumentException("Instrument with token " + in.getSymbolToken() + " already exists");
        }
        in.setId(null);
        in.setCreatedAt(Instant.now());
        in.setUpdatedAt(Instant.now());
        BiasInstrumentEntity saved = repository.save(in);
        log.info("Created instrument {} ({}:{})", saved.getSymbol(), saved.getExchange(), saved.getSymbolToken());
        return saved;
    }

    @Transactional
    public BiasInstrumentEntity update(Long id, BiasInstrumentEntity patch) {
        BiasInstrumentEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument " + id + " not found"));
        if (patch.getSymbol()       != null) existing.setSymbol(patch.getSymbol());
        if (patch.getBroker()       != null) existing.setBroker(patch.getBroker());
        if (patch.getExchange()     != null) existing.setExchange(patch.getExchange());
        if (patch.getSymbolToken()  != null) existing.setSymbolToken(patch.getSymbolToken());
        if (patch.getIntervalType() != null) existing.setIntervalType(patch.getIntervalType());
        if (patch.getNotes()        != null) existing.setNotes(patch.getNotes());
        existing.setEnabled(patch.isEnabled());
        existing.setPriority(patch.getPriority());
        existing.setUpdatedAt(Instant.now());
        validate(existing);
        log.info("Updated instrument {} (id={})", existing.getSymbol(), id);
        return existing;   // JPA dirty-check will flush on tx commit
    }

    @Transactional
    public void toggleEnabled(Long id, boolean enabled) {
        BiasInstrumentEntity e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument " + id + " not found"));
        e.setEnabled(enabled);
        e.setUpdatedAt(Instant.now());
        log.info("Toggled {} → enabled={}", e.getSymbol(), enabled);
    }

    @Transactional
    public void delete(Long id) {
        BiasInstrumentEntity e = repository.findById(id).orElse(null);
        if (e == null) return;
        repository.deleteById(id);
        log.info("Deleted instrument {} (id={})", e.getSymbol(), id);
    }

    @Transactional
    public void setPriority(Long id, int priority) {
        BiasInstrumentEntity e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instrument " + id + " not found"));
        e.setPriority(priority);
        e.setUpdatedAt(Instant.now());
    }

    // ---------- helpers ----------

    private static void validate(BiasInstrumentEntity e) {
        if (e.getSymbol() == null || e.getSymbol().isBlank())
            throw new IllegalArgumentException("symbol is required");
        if (e.getSymbolToken() == null || e.getSymbolToken().isBlank())
            throw new IllegalArgumentException("symbol-token is required");
        if (e.getBroker() == null || e.getBroker().isBlank()) e.setBroker("ANGEL");
        if (e.getExchange() == null || e.getExchange().isBlank()) e.setExchange("NSE");
        if (e.getIntervalType() == null || e.getIntervalType().isBlank()) e.setIntervalType("FIVE_MINUTE");
    }

    private static BiasProperties.Instrument toBean(BiasInstrumentEntity e) {
        BiasProperties.Instrument p = new BiasProperties.Instrument();
        p.setSymbol(e.getSymbol());
        p.setBroker(e.getBroker());
        try { p.setExchange(Exchange.valueOf(e.getExchange())); }
        catch (Exception ex) { p.setExchange(Exchange.NSE); }
        p.setSymbolToken(e.getSymbolToken());
        try { p.setIntradayInterval(Interval.valueOf(e.getIntervalType())); }
        catch (Exception ex) { p.setIntradayInterval(Interval.FIVE_MINUTE); }
        return p;
    }
}
