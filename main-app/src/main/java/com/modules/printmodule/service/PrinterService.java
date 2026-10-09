package com.modules.printmodule.service;

import com.modules.printmodule.dto.PrinterDto;
import com.modules.printmodule.dto.PrinterRequest;
import com.modules.printmodule.model.PrintOn;
import com.modules.printmodule.model.PrinterDoc;
import com.modules.printmodule.model.PrinterType;
import com.modules.printmodule.repository.PrintJobRepository;
import com.modules.printmodule.repository.PrinterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** CRUD stampanti (dashboard admin) + autenticazione dei dispositivi via deviceToken. */
@Service
public class PrinterService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);

    private final PrinterRepository printerRepository;
    private final PrintJobRepository jobRepository;
    private final String publicBaseUrl;

    public PrinterService(PrinterRepository printerRepository, PrintJobRepository jobRepository,
                          @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.printerRepository = printerRepository;
        this.jobRepository = jobRepository;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    public List<PrinterDto> list(long idAgency) {
        return printerRepository.findByIdAgency(idAgency).stream().map(p -> toDto(p, null)).toList();
    }

    public Optional<PrinterDoc> find(long idAgency, String id) {
        return printerRepository.findByIdAndIdAgency(id, idAgency);
    }

    /** @throws IllegalArgumentException su dati non validi (→ 400). */
    public PrinterDto create(long idAgency, PrinterRequest req) {
        if (req == null || req.type() == null) throw new IllegalArgumentException("type obbligatorio");
        PrinterDoc p = new PrinterDoc();
        p.setIdAgency(idAgency);
        p.setType(req.type());
        p.setCreatedAt(Instant.now());
        apply(p, req);
        String token = newToken();
        setToken(p, token);
        return toDto(printerRepository.save(p), token);
    }

    public Optional<PrinterDto> update(long idAgency, String id, PrinterRequest req) {
        if (req == null) throw new IllegalArgumentException("body mancante");
        return printerRepository.findByIdAndIdAgency(id, idAgency).map(p -> {
            if (req.type() != null) p.setType(req.type());
            apply(p, req);
            return toDto(printerRepository.save(p), null);
        });
    }

    public boolean delete(long idAgency, String id) {
        Optional<PrinterDoc> p = printerRepository.findByIdAndIdAgency(id, idAgency);
        if (p.isEmpty()) return false;
        jobRepository.deleteByPrinterId(id);
        printerRepository.delete(p.get());
        return true;
    }

    public Optional<PrinterDto> regenerateToken(long idAgency, String id) {
        return printerRepository.findByIdAndIdAgency(id, idAgency).map(p -> {
            String token = newToken();
            setToken(p, token);
            return toDto(printerRepository.save(p), token);
        });
    }

    /** Lookup del dispositivo (stampante Star o bridge) a partire dal token nel path. */
    public Optional<PrinterDoc> findByDeviceToken(String token, PrinterType expectedType) {
        if (token == null || token.length() < 20 || token.length() > 100) return Optional.empty();
        return printerRepository.findByDeviceTokenHash(sha256(token))
                .filter(p -> p.getType() == expectedType);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void apply(PrinterDoc p, PrinterRequest req) {
        String name = req.name() != null ? req.name().trim() : p.getName();
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name obbligatorio");
        p.setName(name.length() > 60 ? name.substring(0, 60) : name);
        if (req.macAddress() != null) {
            String mac = normalizeMac(req.macAddress());
            if (mac == null) throw new IllegalArgumentException("macAddress non valido");
            p.setMacAddress(mac);
        }
        if (req.paperWidth() != null) p.setPaperWidth(req.paperWidth() <= 58 ? 58 : 80);
        if (req.categoryFilter() != null) {
            p.setCategoryFilter(req.categoryFilter().stream().filter(Objects::nonNull).map(String::trim)
                    .filter(s -> !s.isEmpty()).distinct().toList());
        }
        if (req.printOn() != null) p.setPrintOn(req.printOn());
        if (p.getPrintOn() == null) p.setPrintOn(PrintOn.CREATED);
        if (req.copies() != null) p.setCopies(Math.max(1, Math.min(5, req.copies())));
        if (req.enabled() != null) p.setEnabled(req.enabled());
    }

    /** "00:11:62:AA:BB:CC" / "00-11-62-aa-bb-cc" → "001162aabbcc"; "" → ""; non valido → null. */
    public static String normalizeMac(String raw) {
        if (raw == null) return "";
        String hex = raw.replaceAll("[^0-9A-Fa-f]", "").toLowerCase();
        if (hex.isEmpty()) return "";
        return hex.length() == 12 ? hex : null;
    }

    private void setToken(PrinterDoc p, String token) {
        p.setDeviceTokenHash(sha256(token));
        p.setTokenHint(token.substring(token.length() - 4));
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String cloudPrntUrl(String token) {
        return publicBaseUrl + "/api/printers/cloudprnt/" + token;
    }

    public PrinterDto toDto(PrinterDoc p, String revealedToken) {
        if (p.getType() == PrinterType.TABLET_RAWBT) {
            // nessun dispositivo che si collega al server: niente token né URL di configurazione
            return new PrinterDto(p.getId(), p.getName(), p.getType(), p.getMacAddress(), p.getPaperWidth(), p.charsPerLine(),
                    p.getCategoryFilter(), p.getPrintOn(), p.getCopies(), p.isEnabled(), null, false,
                    null, null, null, null);
        }
        String setupUrl = p.getType() == PrinterType.ESCPOS_BRIDGE
                ? publicBaseUrl
                : (revealedToken != null ? cloudPrntUrl(revealedToken) : null);
        boolean online = p.getLastSeenAt() != null && p.getLastSeenAt().isAfter(Instant.now().minus(ONLINE_WINDOW));
        return new PrinterDto(p.getId(), p.getName(), p.getType(), p.getMacAddress(), p.getPaperWidth(), p.charsPerLine(),
                p.getCategoryFilter(), p.getPrintOn(), p.getCopies(), p.isEnabled(), p.getLastSeenAt(), online,
                p.getLastStatus(), p.getTokenHint(), revealedToken, setupUrl);
    }
}
