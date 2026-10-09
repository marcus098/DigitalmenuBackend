package com.modules.ordermodule.event;

/**
 * Published (Spring ApplicationEvent) after a comand has been persisted, from every creation path
 * (waiter, client at table, table-session submit, public takeaway).
 */
public record ComandCreatedEvent(String comandId, String idAgency) {
}
