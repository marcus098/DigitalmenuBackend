package com.modules.ordermodule.event;

import com.modules.common.model.enums.ComandStatus;

/**
 * Published (Spring ApplicationEvent) after a comand status change has been persisted.
 */
public record ComandStatusChangedEvent(String comandId, String idAgency, ComandStatus oldStatus, ComandStatus newStatus) {
}
