package com.ozballar.ageinwahl.domain;

import java.util.Locale;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("TEILNEHMER")
public record Teilnehmer(
        @Id
        Integer id,
        String vorname,
        String name,
        String klasse,
        GtTeilnahme gtTeilnahme
) {

    public Teilnehmer(Integer id, String vorname, String name, String klasse) {
        this(id, vorname, name, klasse, GtTeilnahme.JA);
    }

    public Teilnehmer {
        klasse = normalisiereKlasse(klasse);
        gtTeilnahme = gtTeilnahme == null ? GtTeilnahme.JA : gtTeilnahme;
    }

    public static String normalisiereKlasse(String klasse) {
        if (klasse == null) {
            throw new IllegalArgumentException("Klasse muss im Format [1-4][a-c]? angegeben werden.");
        }

        String normalisiert = klasse.strip().toLowerCase(Locale.ROOT);
        if (normalisiert.matches("0[1-4][a-c]?")) {
            normalisiert = normalisiert.substring(1);
        }
        if (!normalisiert.matches("[1-4][a-c]?")) {
            throw new IllegalArgumentException("Klasse muss im Format [1-4][a-c]? angegeben werden.");
        }
        return normalisiert;
    }

    public boolean nimmtAnMittagsveranstaltungenTeil() {
        return gtTeilnahme == GtTeilnahme.JA;
    }

    public enum GtTeilnahme {
        JA,
        NEIN
    }
}
