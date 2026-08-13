package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("teilnehmer")
public record Teilnehmer(
        @Id
        Integer nr,
        String vorname,
        String name,
        String klasse
) {

    public Teilnehmer {
        if (klasse == null || !klasse.matches("[1-4][a-c]?")) {
            throw new IllegalArgumentException("Klasse muss aus einer Zahl von 1 bis 4 und optional einem Kleinbuchstaben von a bis c bestehen.");
        }
    }
}
