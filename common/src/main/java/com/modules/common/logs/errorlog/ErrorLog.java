package com.modules.common.logs.errorlog;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ErrorLog {
    public static final Logger logger = LoggerFactory.getLogger(ErrorLog.class);
}
