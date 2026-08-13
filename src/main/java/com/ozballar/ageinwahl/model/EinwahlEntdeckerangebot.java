package com.ozballar.ageinwahl.model;

import java.util.HashMap;

public record EinwahlEntdeckerangebot(
        Teilnehmer teilnehmer,
        HashMap<Ag, Auswahl> auswahl
) {

    public EinwahlEntdeckerangebot {
        if (teilnehmer == null) {
            throw new IllegalArgumentException("Teilnehmer darf nicht null sein.");
        }

        if (teilnehmer.klasse() == null) {
            throw new IllegalArgumentException("Teilnehmerklasse darf nicht null sein.");
        }

        if (auswahl != null && auswahl.keySet().stream().anyMatch(ag -> !istErlaubtesEntdeckerangebot(ag, teilnehmer))) {
            throw new IllegalArgumentException("Auswahl darf nur Entdeckerangebote enthalten, die fuer den Jahrgang des Teilnehmers erlaubt sind.");
        }

        auswahl = auswahl == null ? new HashMap<>() : new HashMap<>(auswahl);
    }

    private static boolean istErlaubtesEntdeckerangebot(Ag ag, Teilnehmer teilnehmer) {
        int jahrgang = Character.getNumericValue(teilnehmer.klasse().charAt(0));

        return ag != null
                && ag.kategorie() == Ag.Kategorie.ENTDECKERANGEBOT
                && ag.erlaubteJahrgaenge() != null
                && ag.erlaubteJahrgaenge().contains(jahrgang);
    }

    public enum Auswahl {
        JA,
        NEIN
    }
}
