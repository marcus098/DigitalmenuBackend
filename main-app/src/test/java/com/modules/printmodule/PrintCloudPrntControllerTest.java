package com.modules.printmodule;

import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.printmodule.controller.CloudPrntController;
import com.modules.printmodule.controller.PrintBridgeController;
import com.modules.printmodule.model.*;
import com.modules.printmodule.repository.PrintJobRepository;
import com.modules.printmodule.repository.PrinterRepository;
import com.modules.printmodule.service.PrintJobService;
import com.modules.printmodule.service.PrinterService;
import com.modules.printmodule.service.TicketFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Flusso CloudPRNT (poll → fetch → confirm) e bridge ESC/POS, senza Mongo: repository mockati. */
class PrintCloudPrntControllerTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String MAC = "00:11:62:AA:BB:CC";

    private PrinterRepository printerRepo;
    private PrintJobRepository jobRepo;
    private MockMvc mvc;
    private PrinterDoc printer;
    private PrintJobDoc job;

    @BeforeEach
    void setUp() {
        printerRepo = mock(PrinterRepository.class);
        jobRepo = mock(PrintJobRepository.class);
        PrinterService printerService = new PrinterService(printerRepo, jobRepo, "https://api.example.com/");
        PrintJobService jobService = new PrintJobService(printerRepo, jobRepo, mock(MongoComandReadRepository.class), mock(TicketFactory.class));
        mvc = MockMvcBuilders.standaloneSetup(
                new CloudPrntController(printerService, jobService, printerRepo),
                new PrintBridgeController(printerService, jobService, printerRepo)).build();

        printer = new PrinterDoc();
        printer.setId("p1");
        printer.setIdAgency(7L);
        printer.setType(PrinterType.STAR_CLOUDPRNT);
        printer.setMacAddress("001162aabbcc");
        printer.setDeviceTokenHash(PrinterService.sha256(TOKEN));
        printer.setPaperWidth(80);
        when(printerRepo.findByDeviceTokenHash(PrinterService.sha256(TOKEN))).thenReturn(Optional.of(printer));

        job = new PrintJobDoc();
        job.setId("job1");
        job.setPrinterId("p1");
        job.setKind(PrintJobKind.NEW_ORDER);
        job.setStatus(PrintJobStatus.PENDING);
        job.setCreatedAt(Instant.now());
        job.setContent(List.of("C|Pizzeria", "H|TAVOLO 5", "B|2x Margherita"));
    }

    @Test
    void fullCloudPrntFlow() throws Exception {
        when(jobRepo.peekNextPending("p1")).thenReturn(Optional.of(job));

        mvc.perform(post("/api/printers/cloudprnt/" + TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"23 6 0 0 0 0 0 0 0 \",\"printerMAC\":\"" + MAC + "\",\"statusCode\":\"200%20OK\",\"printingInProgress\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobReady").value(true))
                .andExpect(jsonPath("$.jobToken").value("job1"))
                .andExpect(jsonPath("$.mediaTypes[0]").value("application/vnd.star.starprnt"))
                .andExpect(jsonPath("$.mediaTypes[1]").value("text/plain"));
        verify(printerRepo).touchIfStale(eq(printer), isNull(), eq("200%20OK"));

        PrintJobDoc sent = copyWithStatus(PrintJobStatus.SENT);
        when(jobRepo.claimById("p1", "job1")).thenReturn(Optional.of(sent));
        mvc.perform(get("/api/printers/cloudprnt/" + TOKEN).param("mac", MAC).param("type", "text/plain").param("token", "job1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("TAVOLO 5")))
                .andExpect(content().string(containsString("2x Margherita")));

        mvc.perform(get("/api/printers/cloudprnt/" + TOKEN).param("mac", MAC).param("type", "application/vnd.star.starprnt").param("token", "job1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.star.starprnt"));

        when(jobRepo.findByIdAndPrinterId("job1", "p1")).thenReturn(Optional.of(sent));
        mvc.perform(delete("/api/printers/cloudprnt/" + TOKEN).param("mac", MAC).param("code", "200 OK").param("token", "job1"))
                .andExpect(status().isOk());
        verify(jobRepo).markPrinted("job1");
        verify(jobRepo, never()).markFailedAttempt(any(), any(), anyInt());
    }

    @Test
    void errorCodeRequeuesJob() throws Exception {
        PrintJobDoc sent = copyWithStatus(PrintJobStatus.SENT);
        when(jobRepo.findByIdAndPrinterId("job1", "p1")).thenReturn(Optional.of(sent));
        mvc.perform(delete("/api/printers/cloudprnt/" + TOKEN).param("mac", MAC).param("code", "520 Paper Empty").param("token", "job1"))
                .andExpect(status().isOk());
        verify(jobRepo).markFailedAttempt(eq(sent), contains("520"), eq(PrintJobService.MAX_ATTEMPTS));
        verify(jobRepo, never()).markPrinted(any());
    }

    @Test
    void noJobAndAuthChecks() throws Exception {
        when(jobRepo.peekNextPending("p1")).thenReturn(Optional.empty());
        mvc.perform(post("/api/printers/cloudprnt/" + TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"printerMAC\":\"" + MAC + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobReady").value(false));

        // MAC diverso → 403
        mvc.perform(post("/api/printers/cloudprnt/" + TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"printerMAC\":\"00:11:62:00:00:01\"}"))
                .andExpect(status().isForbidden());

        // token sconosciuto → 404
        mvc.perform(post("/api/printers/cloudprnt/zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        // nessun job da scaricare → 404
        when(jobRepo.claimNext("p1")).thenReturn(Optional.empty());
        mvc.perform(get("/api/printers/cloudprnt/" + TOKEN).param("mac", MAC).param("type", "text/plain"))
                .andExpect(status().isNotFound());

        // token di una stampante Star non vale sul bridge
        mvc.perform(get("/api/printers/bridge/" + TOKEN + "/next")).andExpect(status().isNotFound());
    }

    @Test
    void learnsMacOnFirstContact() throws Exception {
        printer.setMacAddress("");
        when(jobRepo.peekNextPending("p1")).thenReturn(Optional.empty());
        mvc.perform(post("/api/printers/cloudprnt/" + TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"printerMAC\":\"" + MAC + "\"}"))
                .andExpect(status().isOk());
        verify(printerRepo).touchIfStale(eq(printer), eq("001162aabbcc"), isNull());
    }

    @Test
    void bridgeNextAndAck() throws Exception {
        printer.setType(PrinterType.ESCPOS_BRIDGE);
        PrintJobDoc sent = copyWithStatus(PrintJobStatus.SENT);
        when(jobRepo.claimNext("p1")).thenReturn(Optional.of(sent));
        mvc.perform(get("/api/printers/bridge/" + TOKEN + "/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job1"))
                .andExpect(jsonPath("$.escposBase64").isString());

        when(jobRepo.findByIdAndPrinterId("job1", "p1")).thenReturn(Optional.of(sent));
        mvc.perform(post("/api/printers/bridge/" + TOKEN + "/jobs/job1/ack").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ok\":false,\"error\":\"ECONNREFUSED\"}"))
                .andExpect(status().isOk());
        verify(jobRepo).markFailedAttempt(sent, "ECONNREFUSED", PrintJobService.MAX_ATTEMPTS);

        when(jobRepo.claimNext("p1")).thenReturn(Optional.empty());
        mvc.perform(get("/api/printers/bridge/" + TOKEN + "/next")).andExpect(status().isNoContent());
    }

    private PrintJobDoc copyWithStatus(PrintJobStatus status) {
        PrintJobDoc j = new PrintJobDoc();
        j.setId(job.getId());
        j.setPrinterId(job.getPrinterId());
        j.setKind(job.getKind());
        j.setContent(job.getContent());
        j.setCreatedAt(job.getCreatedAt());
        j.setStatus(status);
        j.setAttempts(1);
        j.setSentAt(Instant.now());
        return j;
    }
}
