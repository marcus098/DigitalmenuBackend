package com.modules.printmodule.dto;

/** DTO del protocollo bridge ESC/POS. */
public final class BridgeDtos {

    private BridgeDtos() {}

    /** Risposta di GET /api/printers/bridge/{deviceToken}/next. */
    public record BridgeJob(String jobId, String kind, String comandId, String escposBase64) {}

    /** Body di POST /api/printers/bridge/{deviceToken}/jobs/{jobId}/ack. */
    public record BridgeAck(boolean ok, String error) {}
}
