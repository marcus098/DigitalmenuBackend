package com.modules.mainapp.esl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.modules.common.finders.TableUtils;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

@Service
public class EslService {

    @Value("${esl.ap.default-url:}")
    private String defaultApUrl;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Autowired
    private EslConfigRepository eslConfigRepository;

    @Autowired
    private AuthenticatedUserProvider authUserProvider;

    @Autowired
    private TableUtils tableUtils;

    @Autowired
    private EslApUrlValidator apUrlValidator;

    /**
     * Called when a table is marked OCCUPIED. Generates the QR code PNG and
     * pushes it to the configured OpenEPaperLink access point for this table.
     * Silently swallows all errors so the table-occupy flow is never blocked.
     */
    public void updateTableTag(Long tableId) {
        try {
            Optional<EslConfigJpa> configOpt = eslConfigRepository.findByTableId(tableId);
            if (configOpt.isEmpty()) return;
            EslConfigJpa config = configOpt.get();
            if (config.getEslTagMac() == null || config.getEslTagMac().isBlank()) return;

            // Localname comes from the authentication principal (= restaurant username)
            String localname = SecurityContextHolder.getContext().getAuthentication().getName();
            String qrUrl = frontendUrl + "/" + localname + "/Categories?table=" + tableId;

            byte[] qrPng = generateQrPng(qrUrl, 300);

            String apUrl = (config.getEslApUrl() != null && !config.getEslApUrl().isBlank())
                    ? config.getEslApUrl() : defaultApUrl;
            if (apUrl == null || apUrl.isBlank()) {
                ErrorLog.logger.warn("ESL: AP URL non configurato per tavolo {}", tableId);
                return;
            }

            apUrlValidator.validate(apUrl); // ri-validazione a ogni push (DNS rebinding)
            postToAp(apUrl, config.getEslTagMac(), qrPng);
            ErrorLog.logger.info("ESL: QR aggiornato per tavolo {} → tag {}", tableId, config.getEslTagMac());
        } catch (Exception e) {
            ErrorLog.logger.error("ESL: Errore aggiornamento tag per tavolo {}", tableId, e);
        }
    }

    public EslConfigJpa saveConfig(Long tableId, String eslTagMac, String eslApUrl) {
        long idAgency = authUserProvider.getAgencyId();
        requireOwnTable(tableId, idAgency);
        try {
            apUrlValidator.validate(eslApUrl);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        EslConfigJpa config = eslConfigRepository.findByTableId(tableId).orElse(new EslConfigJpa());
        config.setTableId(tableId);
        config.setIdAgency(idAgency);
        config.setEslTagMac(eslTagMac != null ? eslTagMac.trim() : null);
        config.setEslApUrl(eslApUrl != null ? eslApUrl.trim() : null);
        return eslConfigRepository.save(config);
    }

    public List<EslConfigJpa> getConfigsForAgency() {
        long idAgency = authUserProvider.getAgencyId();
        return eslConfigRepository.findByIdAgency(idAgency);
    }

    @Transactional
    public void deleteConfig(Long tableId) {
        requireOwnTable(tableId, authUserProvider.getAgencyId());
        eslConfigRepository.deleteByTableId(tableId);
    }

    /** Manually trigger a QR push from the dashboard (e.g. after configuring a new tag). */
    public boolean manualPush(Long tableId) {
        requireOwnTable(tableId, authUserProvider.getAgencyId());
        try {
            updateTableTag(tableId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** IDOR guard: il tavolo deve appartenere all'agency del chiamante, altrimenti 404. */
    private void requireOwnTable(Long tableId, long idAgency) {
        if (tableId == null || tableUtils.findByIdAndIdAgencyAndDeleted(tableId, idAgency).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tavolo non trovato");
        }
    }

    private byte[] generateQrPng(String content, int size) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
        return baos.toByteArray();
    }

    private void postToAp(String apUrl, String tagMac, byte[] pngBytes) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(6000);
        RestTemplate restTemplate = new RestTemplate(factory);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("mac", tagMac);
        body.add("image", new ByteArrayResource(pngBytes) {
            @Override public String getFilename() { return "qr.png"; }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        restTemplate.postForEntity(apUrl + "/imgupload", new HttpEntity<>(body, headers), String.class);
    }
}
