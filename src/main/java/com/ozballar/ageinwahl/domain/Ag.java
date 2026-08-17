package com.ozballar.ageinwahl.domain;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("AG")
public record Ag(
        @Id
        Integer id,
        Wochentag wochentag,
        Zeit zeit,
        Kategorie kategorie,
        String titel,
        String beschreibung,
        String verantwortlicher,
        String ort,
        Integer maximaleTeilnehmerzahl,
        @MappedCollection(idColumn = "AG_ID", keyColumn = "POSITION")
        List<ErlaubterJahrgang> erlaubteJahrgaenge
) {

    public Ag {
        if (erlaubteJahrgaenge != null && erlaubteJahrgaenge.stream().anyMatch(jahrgang -> jahrgang == null || jahrgang.jahrgang() == null)) {
            throw new IllegalArgumentException("Erlaubte Jahrgaenge duerfen keine leeren Eintraege enthalten.");
        }

        erlaubteJahrgaenge = erlaubteJahrgaenge == null ? List.of() : List.copyOf(erlaubteJahrgaenge);
    }

    public boolean istFuerJahrgangErlaubt(int jahrgang) {
        return erlaubteJahrgaenge.stream()
                .anyMatch(erlaubterJahrgang -> erlaubterJahrgang.jahrgang() == jahrgang);
    }

    public List<Integer> erlaubteJahrgangszahlen() {
        return erlaubteJahrgaenge.stream()
                .map(ErlaubterJahrgang::jahrgang)
                .toList();
    }

    public enum Wochentag {
        MONTAG,
        DIENSTAG,
        MITTWOCH,
        DONNERSTAG,
        FREITAG
    }

    public enum Zeit {
        VORMITTAG,
        NACHMITTAG
    }

    public enum Kategorie {
        ENTDECKERANGEBOT,
        AG,
        JAHRES_AG
    }
}
