package com.modules.mainapp.reservation.repository;

import com.modules.mainapp.reservation.entity.ReservationJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationJpa, Long> {
    List<ReservationJpa> findByIdAgencyOrderByReservationDateDescReservationTimeDesc(long idAgency);
    List<ReservationJpa> findByIdAgencyAndReservationDateBetweenOrderByReservationDateAscReservationTimeAsc(long idAgency, LocalDate from, LocalDate to);
    List<ReservationJpa> findByIdAgencyAndStatusOrderByReservationDateAscReservationTimeAsc(long idAgency, String status);
    List<ReservationJpa> findByStatusAndReservationDateAndReminder24hSentFalse(String status, LocalDate date);
    List<ReservationJpa> findByStatusAndReservationDateAndReminder1hSentFalse(String status, LocalDate date);
}
