package com.ozballar.ageinwahl.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

@Table("einwahl_entdeckerangebot_aggregate")
public record EinwahlEntdeckerangebotAggregat(
        @Id
        Integer id,
        @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL, prefix = "einwahl_entdeckerangebot_")
        EinwahlEntdeckerangebot einwahlEntdeckerangebot
) {

    public EinwahlEntdeckerangebotAggregat {
        if (einwahlEntdeckerangebot == null) {
            throw new IllegalArgumentException("EinwahlEntdeckerangebot darf nicht null sein.");
        }
    }
}
