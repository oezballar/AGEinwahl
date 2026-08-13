package com.ozballar.ageinwahl.domain;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("ag")
public record Ag(
        @Id
        Integer id,
        Wochentag wochentag,
        Zeit zeit,
        Kategorie kategorie,
        String titel,
        String verantwortlicher,
        String ort,
        Integer maximaleTeilnehmerzahl,
        @MappedCollection(idColumn = "ag_id")
        List<Integer> erlaubteJahrgaenge
) {

    public Ag {
        if (erlaubteJahrgaenge != null && erlaubteJahrgaenge.stream().anyMatch(jahrgang -> jahrgang == null || jahrgang < 1 || jahrgang > 4)) {
            throw new IllegalArgumentException("Erlaubte Jahrgaenge muessen zwischen 1 und 4 liegen.");
        }

        erlaubteJahrgaenge = erlaubteJahrgaenge == null ? null : List.copyOf(erlaubteJahrgaenge);
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
