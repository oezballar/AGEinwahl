package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Table("ag_aggregate")
public record AgAggregat(
        @Id
        Integer id,
        @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "ag_")
        Ag ag
) {

    public AgAggregat {
        if (ag == null) {
            throw new IllegalArgumentException("AG darf nicht null sein.");
        }
    }
}
