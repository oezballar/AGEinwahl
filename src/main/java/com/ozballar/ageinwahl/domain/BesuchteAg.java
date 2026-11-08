package com.ozballar.ageinwahl.domain;

import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.annotation.Id;

@Table("BESUCHTE_AG")
public record BesuchteAg(
        @Id Integer id,
        Integer teilnehmerId,
        String agTitel
) {
}
