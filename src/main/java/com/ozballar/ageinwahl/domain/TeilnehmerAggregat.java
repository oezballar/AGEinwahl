package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Table("teilnehmer_aggregate")
public record TeilnehmerAggregat(
        @Id
        Integer id,
        @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "teilnehmer_")
        Teilnehmer teilnehmer
) {

    public TeilnehmerAggregat {
        if (teilnehmer == null) {
            throw new IllegalArgumentException("Teilnehmer darf nicht null sein.");
        }
    }
}
