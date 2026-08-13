package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Table("einwahl_vormittags_ag")
public record EinwahlVormittagsAG(
        @Id
        Integer id,
        @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "teilnehmer_")
        Teilnehmer teilnehmer,
        @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "ag_")
        Ag ag,
        Integer auswahl
) {

    public EinwahlVormittagsAG {
        if (teilnehmer == null) {
            throw new IllegalArgumentException("Teilnehmer darf nicht null sein.");
        }

        if (teilnehmer.klasse() == null) {
            throw new IllegalArgumentException("Teilnehmerklasse darf nicht null sein.");
        }

        if (!istGueltigeAuswahl(teilnehmer, ag)) {
            throw new IllegalArgumentException("EinwahlVormittagsAG darf nur fuer AGs oder Jahres-AGs am Vormittag erstellt werden, die fuer den Jahrgang des Teilnehmers erlaubt sind.");
        }

        if (auswahl != null && auswahl < 1) {
            throw new IllegalArgumentException("Auswahl muss groesser gleich 1 sein.");
        }
    }

    public static boolean istGueltigeAuswahl(Teilnehmer teilnehmer, Ag ag) {
        if (teilnehmer == null || teilnehmer.klasse() == null || ag == null) {
            return false;
        }

        int jahrgang = Character.getNumericValue(teilnehmer.klasse().charAt(0));

        return (ag.kategorie() == Ag.Kategorie.AG || ag.kategorie() == Ag.Kategorie.JAHRES_AG)
                && ag.zeit() == Ag.Zeit.VORMITTAG
                && ag.erlaubteJahrgaenge() != null
                && ag.erlaubteJahrgaenge().contains(jahrgang);
    }
}
