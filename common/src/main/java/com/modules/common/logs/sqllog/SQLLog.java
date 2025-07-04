package com.modules.common.logs.sqllog;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SQLLog {
    public static final Logger logger = LoggerFactory.getLogger(SQLLog.class);
}
