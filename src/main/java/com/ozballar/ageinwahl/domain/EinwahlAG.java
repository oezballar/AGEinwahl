package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("EINWAHL_AG")
public record EinwahlAG(
        @Id
        Integer id,
        Integer teilnehmerId,
        String agTitel,
        Integer auswahl,
        Boolean zugewiesen
) {

    public EinwahlAG {
        if (teilnehmerId == null) {
            throw new IllegalArgumentException("Teilnehmernummer darf nicht null sein.");
        }

        if (agTitel == null || agTitel.isBlank()) {
            throw new IllegalArgumentException("AG-Titel darf nicht leer sein.");
        }

        if (auswahl != null && auswahl < 1) {
            throw new IllegalArgumentException("Auswahl muss groesser gleich 1 sein.");
        }
    }

    public EinwahlAG(Integer id, Teilnehmer teilnehmer, Ag ag, Integer auswahl) {
        this(id, teilnehmer, ag, auswahl, false);
    }

    public EinwahlAG(Integer id, Teilnehmer teilnehmer, Ag ag, Integer auswahl, Boolean zugewiesen) {
        this(id, teilnehmerIdAus(teilnehmer), agTitelAus(ag), auswahl, zugewiesen);

        if (!istGueltigeAuswahl(teilnehmer, ag)) {
            throw new IllegalArgumentException("EinwahlAG darf nur fuer AGs oder Jahres-AGs am Nachmittag erstellt werden, die fuer den Jahrgang des Teilnehmers erlaubt sind.");
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
                && ag.zeit() == Ag.Zeit.NACHMITTAG
                && teilnehmer.nimmtAnMittagsveranstaltungenTeil()
                && ag.istFuerJahrgangErlaubt(jahrgang);
    }
}
