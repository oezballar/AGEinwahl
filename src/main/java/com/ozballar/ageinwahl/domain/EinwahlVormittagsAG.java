package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("EINWAHL_VORMITTAGS_AG")
public record EinwahlVormittagsAG(
        @Id
        Integer id,
        Integer teilnehmerId,
        String agTitel,
        Auswahl auswahl,
        Boolean zugewiesen
) {

    public EinwahlVormittagsAG {
        if (teilnehmerId == null) {
            throw new IllegalArgumentException("Teilnehmernummer darf nicht null sein.");
        }

        if (agTitel == null || agTitel.isBlank()) {
            throw new IllegalArgumentException("AG-Titel darf nicht leer sein.");
        }

    }

    public EinwahlVormittagsAG(Integer id, Teilnehmer teilnehmer, Ag ag, Auswahl auswahl) {
        this(id, teilnehmer, ag, auswahl, false);
    }

    public EinwahlVormittagsAG(Integer id, Teilnehmer teilnehmer, Ag ag, Auswahl auswahl, Boolean zugewiesen) {
        this(id, teilnehmerIdAus(teilnehmer), agTitelAus(ag), auswahl, zugewiesen);

        if (!istGueltigeAuswahl(teilnehmer, ag)) {
            throw new IllegalArgumentException("EinwahlVormittagsAG darf nur fuer AGs oder Jahres-AGs am Vormittag erstellt werden, die fuer den Jahrgang des Teilnehmers erlaubt sind.");
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
