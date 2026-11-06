package com.modules.servletconfiguration.jpa;

import com.modules.common.utilities.BusinessTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

/**
 * Il driver Mongo non ha un codec per OffsetDateTime (usato da TableSessionJpa): senza questi
 * convertitori ogni salvataggio della sessione al tavolo fallisce con CodecConfigurationException.
 * Si salva come Date (istante UTC) e si rilegge nel fuso del business.
 */
@Configuration
public class MongoConf {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new OffsetDateTimeToDate(), new DateToOffsetDateTime()));
    }

    @WritingConverter
    static class OffsetDateTimeToDate implements Converter<OffsetDateTime, Date> {
        @Override
        public Date convert(OffsetDateTime source) {
            return Date.from(source.toInstant());
        }
    }

    @ReadingConverter
    static class DateToOffsetDateTime implements Converter<Date, OffsetDateTime> {
        @Override
        public OffsetDateTime convert(Date source) {
            return source.toInstant().atZone(BusinessTime.ZONE).toOffsetDateTime();
        }
    }
}
