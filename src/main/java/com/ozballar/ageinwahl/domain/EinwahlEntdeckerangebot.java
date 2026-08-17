package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("EINWAHL_ENTDECKERANGEBOT")
public record EinwahlEntdeckerangebot(
        @Id
        Integer id,
        Integer teilnehmerId,
        String agTitel,
        Auswahl auswahl,
        Boolean zugewiesen
) {

    public EinwahlEntdeckerangebot {
        if (teilnehmerId == null) {
            throw new IllegalArgumentException("Teilnehmernummer darf nicht null sein.");
        }

        if (agTitel == null || agTitel.isBlank()) {
            throw new IllegalArgumentException("AG-Titel darf nicht leer sein.");
        }
    }

    public EinwahlEntdeckerangebot(Integer id, Teilnehmer teilnehmer, Ag ag, Auswahl auswahl) {
        this(id, teilnehmer, ag, auswahl, false);
    }

    public EinwahlEntdeckerangebot(Integer id, Teilnehmer teilnehmer, Ag ag, Auswahl auswahl, Boolean zugewiesen) {
        this(id, teilnehmerIdAus(teilnehmer), agTitelAus(ag), auswahl, zugewiesen);

        if (!istErlaubteAuswahl(teilnehmer, ag)) {
            throw new IllegalArgumentException("EinwahlEntdeckerangebot darf nur fuer Entdeckerangebote erstellt werden, die fuer den Jahrgang des Teilnehmers erlaubt sind.");
        }
    }

    private static Integer teilnehmerIdAus(Teilnehmer teilnehmer) {
        if (teilnehmer == null) {
            throw new IllegalArgumentException("Teilnehmer darf nicht null sein.");
        }

        if (teilnehmer.klasse() == null) {
            throw new IllegalArgumentException("Teilnehmerklasse darf nicht null sein.");
        }

        return teilnehmer.id();
    }

    private static String agTitelAus(Ag ag) {
        return ag == null ? null : ag.titel();
    }

    public static boolean istErlaubteAuswahl(Teilnehmer teilnehmer, Ag ag) {
        if (teilnehmer == null || teilnehmer.klasse() == null || ag == null) {
            return false;
        }

        int jahrgang = Character.getNumericValue(teilnehmer.klasse().charAt(0));

        return ag.kategorie() == Ag.Kategorie.ENTDECKERANGEBOT
                && (ag.zeit() != Ag.Zeit.NACHMITTAG || teilnehmer.nimmtAnMittagsveranstaltungenTeil())
                && ag.istFuerJahrgangErlaubt(jahrgang);
    }

    public enum Auswahl {
        JA,
        NEIN
    }
}
