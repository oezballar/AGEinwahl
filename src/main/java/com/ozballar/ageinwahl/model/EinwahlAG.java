package com.ozballar.ageinwahl.model;

import java.util.HashMap;

public record EinwahlAG(
        Teilnehmer teilnehmer,
        HashMap<Integer, Ag> auswahl
) {

    public EinwahlAG {
        if (teilnehmer == null) {
            throw new IllegalArgumentException("Teilnehmer darf nicht null sein.");
        }

        if (teilnehmer.klasse() == null) {
            throw new IllegalArgumentException("Teilnehmerklasse darf nicht null sein.");
        }

        if (auswahl != null && auswahl.entrySet().stream().anyMatch(eintrag -> !istGueltigeAuswahl(eintrag.getKey(), eintrag.getValue(), teilnehmer))) {
            throw new IllegalArgumentException("Auswahl darf nur AGs oder Jahres-AGs am Nachmittag enthalten, die fuer den Jahrgang des Teilnehmers erlaubt sind.");
        }

        auswahl = auswahl == null ? new HashMap<>() : new HashMap<>(auswahl);
    }

    private static boolean istGueltigeAuswahl(Integer schluessel, Ag ag, Teilnehmer teilnehmer) {
        int jahrgang = Character.getNumericValue(teilnehmer.klasse().charAt(0));

        return schluessel != null
                && schluessel >= 1
                && ag != null
                && (ag.kategorie() == Ag.Kategorie.AG || ag.kategorie() == Ag.Kategorie.JAHRES_AG)
                && ag.zeit() == Ag.Zeit.NACHMITTAG
                && ag.erlaubteJahrgaenge() != null
                && ag.erlaubteJahrgaenge().contains(jahrgang);
    }
}
