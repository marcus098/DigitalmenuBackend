package com.modules.mainapp.reservation.job;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.mainapp.reservation.repository.ReservationRepository;
import com.modules.mainapp.service.TwilioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class ReservationReminderJob {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TwilioService twilioService;

    /** Runs every hour. Sends 24h and 1h reminders for CONFIRMED reservations. */
    @Scheduled(fixedDelay = 3_600_000)
    public void sendReminders() {
        try {
            LocalDate today    = LocalDate.now();
            LocalDate tomorrow = today.plusDays(1);
            LocalTime now      = LocalTime.now();

            // 24h reminder — reservations tomorrow that haven't been notified yet
            List<ReservationJpa> tomorrow24h = reservationRepository
                    .findByStatusAndReservationDateAndReminder24hSentFalse("CONFIRMED", tomorrow);
            for (ReservationJpa r : tomorrow24h) {
                String msg = String.format(
                        "Ciao %s! Ti ricordiamo la prenotazione di domani alle %s per %d %s. A presto!",
                        r.getCustomerName(),
                        r.getReservationTime().toString().substring(0, 5),
                        r.getPartySize(),
                        r.getPartySize() == 1 ? "persona" : "persone"
                );
                twilioService.sendSms(r.getCustomerPhone(), msg);
                r.setReminder24hSent(true);
                reservationRepository.save(r);
            }

            // 1h reminder — reservations today, 55-65 minutes from now
            List<ReservationJpa> today1h = reservationRepository
                    .findByStatusAndReservationDateAndReminder1hSentFalse("CONFIRMED", today);
            for (ReservationJpa r : today1h) {
                if (r.getReservationTime() == null) continue;
                long minsUntil = ChronoUnit.MINUTES.between(now, r.getReservationTime());
                if (minsUntil >= 55 && minsUntil <= 65) {
                    String msg = String.format(
                            "Ciao %s! Tra circa 1 ora è prevista la tua prenotazione alle %s per %d %s. Ti aspettiamo!",
                            r.getCustomerName(),
                            r.getReservationTime().toString().substring(0, 5),
                            r.getPartySize(),
                            r.getPartySize() == 1 ? "persona" : "persone"
                    );
                    twilioService.sendSms(r.getCustomerPhone(), msg);
                    r.setReminder1hSent(true);
                    reservationRepository.save(r);
                }
            }
        } catch (Exception e) {
            ErrorLog.logger.error("Errore ReservationReminderJob", e);
        }
    }
}
