package com.modules.pushmodule.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Token Expo di un dispositivo dello staff (app "Sala"). Un utente può averne più di uno (telefono, tablet...).
 * {@code idAgency} è copiato dall'utente alla registrazione: le notifiche di un locale vanno a tutti i suoi token.
 */
@Getter
@Setter
@Entity
@Table(name = "push_tokens", indexes = {
        @Index(name = "idx_push_tokens_agency", columnList = "id_agency"),
        @Index(name = "idx_push_tokens_user", columnList = "user_id")
})
public class PushTokenJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "id_agency", nullable = false)
    private long idAgency;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    /** ios | android (facoltativo) */
    @Column(length = 20)
    private String platform;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
