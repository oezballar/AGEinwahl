package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Table("einwahl_entdeckerangebot")
public record EinwahlEntdeckerangebot(
        @Id
        Integer id,
        @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "teilnehmer_")
        Teilnehmer teilnehmer,
        @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "ag_")
        Ag ag,
        Auswahl auswahl
) {

    public EinwahlEntdeckerangebot {
        if (teilnehmer == null) {
            throw new IllegalArgumentException("Teilnehmer darf nicht null sein.");
        }

        if (teilnehmer.klasse() == null) {
            throw new IllegalArgumentException("Teilnehmerklasse darf nicht null sein.");
        }

        if (!istErlaubteAuswahl(teilnehmer, ag)) {
            throw new IllegalArgumentException("EinwahlEntdeckerangebot darf nur fuer Entdeckerangebote erstellt werden, die fuer den Jahrgang des Teilnehmers erlaubt sind.");
        }
    }

    public static boolean istErlaubteAuswahl(Teilnehmer teilnehmer, Ag ag) {
        if (teilnehmer == null || teilnehmer.klasse() == null || ag == null) {
            return false;
        }

        int jahrgang = Character.getNumericValue(teilnehmer.klasse().charAt(0));

        return ag.kategorie() == Ag.Kategorie.ENTDECKERANGEBOT
                && ag.erlaubteJahrgaenge() != null
                && ag.erlaubteJahrgaenge().contains(jahrgang);
    }

    public enum Auswahl {
        JA,
        NEIN
    }
}
