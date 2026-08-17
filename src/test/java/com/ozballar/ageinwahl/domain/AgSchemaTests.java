package com.ozballar.ageinwahl.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AgSchemaTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void agTitelIstEindeutig() {
        String sql = """
                INSERT INTO ag (wochentag, zeit, kategorie, titel, verantwortlicher, ort, maximale_teilnehmerzahl)
                VALUES ('MONTAG', 'NACHMITTAG', 'AG', 'Sport', 'Max Muster', 'Turnhalle', 10)
                """;

        jdbcTemplate.update(sql);

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(sql));
    }

    @Test
    void teilnehmerIstDurchVornameUndNameEindeutig() {
        String sql = """
                INSERT INTO teilnehmer (id, vorname, name, klasse)
                VALUES (?, 'Max', 'Muster', '2a')
                """;

        jdbcTemplate.update(sql, 1);

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(sql, 2));
    }
}
