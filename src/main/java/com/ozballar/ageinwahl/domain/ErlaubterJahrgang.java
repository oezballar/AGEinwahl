package com.ozballar.ageinwahl.domain;

import org.springframework.data.relational.core.mapping.Table;

@Table("ERLAUBTER_JAHRGANG")
public record ErlaubterJahrgang(Integer jahrgang) {

    public ErlaubterJahrgang {
        if (jahrgang == null || jahrgang < 1 || jahrgang > 4) {
            throw new IllegalArgumentException("Jahrgang muss zwischen 1 und 4 liegen.");
        }
    }
}
