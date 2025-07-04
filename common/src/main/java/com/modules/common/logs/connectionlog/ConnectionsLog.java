package com.modules.common.logs.connectionlog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConnectionsLog {
    //@Autowired
    //private static HikariDataSource dataSource;
//
    //@Autowired
    //private void setDataSource(HikariDataSource dataSource) {
    //    ConnectionsLog.dataSource = dataSource;
    //}
//
    //public static Logger logger = LoggerFactory.getLogger(ConnectionsLog.class);
//
//
    //public static void writeLogSQL() {
    //    HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
    //    int available = poolMXBean.getActiveConnections();
    //    int idle = poolMXBean.getIdleConnections();
    //    int total = poolMXBean.getTotalConnections();
    //    logger.debug("Connessioni in uso: " + available + " | Connessioni idle: " + idle + " | Connessioni totali: " + total);
    //}
}
