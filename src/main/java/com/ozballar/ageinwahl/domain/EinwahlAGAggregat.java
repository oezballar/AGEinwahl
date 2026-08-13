package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Table("einwahl_ag_aggregate")
public record EinwahlAGAggregat(
        @Id
        Integer id,
        @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "einwahl_ag_")
        EinwahlAG einwahlAG
) {

    public EinwahlAGAggregat {
        if (einwahlAG == null) {
            throw new IllegalArgumentException("EinwahlAG darf nicht null sein.");
        }
    }
}
