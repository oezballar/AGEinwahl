package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("EINWAHL_VORMITTAGS_AG")
public record EinwahlVormittagsAG(
        @Id
        Integer id,
        Integer teilnehmerNr,
        String agTitel,
        Auswahl auswahl
) {

    public EinwahlVormittagsAG {
        if (teilnehmerNr == null) {
            throw new IllegalArgumentException("Teilnehmernummer darf nicht null sein.");
        }

        if (agTitel == null || agTitel.isBlank()) {
            throw new IllegalArgumentException("AG-Titel darf nicht leer sein.");
        }

    }

    public EinwahlVormittagsAG(Integer id, Teilnehmer teilnehmer, Ag ag, Auswahl auswahl) {
        this(id, teilnehmerNrAus(teilnehmer), agTitelAus(ag), auswahl);

        if (!istGueltigeAuswahl(teilnehmer, ag)) {
            throw new IllegalArgumentException("EinwahlVormittagsAG darf nur fuer AGs oder Jahres-AGs am Vormittag erstellt werden, die fuer den Jahrgang des Teilnehmers erlaubt sind.");
        }
    }

    private static Integer teilnehmerNrAus(Teilnehmer teilnehmer) {
        if (teilnehmer == null) {
            throw new IllegalArgumentException("Teilnehmer darf nicht null sein.");
        }

        if (teilnehmer.klasse() == null) {
            throw new IllegalArgumentException("Teilnehmerklasse darf nicht null sein.");
        }

        return teilnehmer.nr();
    }

    private static String agTitelAus(Ag ag) {
        return ag == null ? null : ag.titel();
    }

    public static boolean istGueltigeAuswahl(Teilnehmer teilnehmer, Ag ag) {
        if (teilnehmer == null || teilnehmer.klasse() == null || ag == null) {
            return false;
        }

        int jahrgang = Character.getNumericValue(teilnehmer.klasse().charAt(0));

        return (ag.kategorie() == Ag.Kategorie.AG || ag.kategorie() == Ag.Kategorie.JAHRES_AG)
                && ag.zeit() == Ag.Zeit.VORMITTAG
                && ag.istFuerJahrgangErlaubt(jahrgang);
    }

    public enum Auswahl {
        JA,
        NEIN
    }
}
