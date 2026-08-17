package com.ozballar.ageinwahl.datei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.ErlaubterJahrgang;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.EinwahlAGRepository;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@SpringBootTest
class AgeinwahlDateiServiceTests {

    @Autowired
    private AgeinwahlDateiService ageinwahlDateiService;

    @Autowired
    private AgService agService;

    @Autowired
    private TeilnehmerService teilnehmerService;

    @Autowired
    private EinwahlAGRepository einwahlAGRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        leereDatenbank();
    }

    @AfterEach
    void tearDown() {
        leereDatenbank();
    }

    @Test
    void exportiertUndImportiertAktuellenDatenbestand() {
        Ag ag = agService.speichern(new Ag(
                null,
                Ag.Wochentag.MONTAG,
                Ag.Zeit.NACHMITTAG,
                Ag.Kategorie.AG,
                "Chor",
                "Singen",
                "Frau Beispiel",
                "Musikraum",
                20,
                List.of(new ErlaubterJahrgang(3))
        ));
        Teilnehmer teilnehmer = teilnehmerService.speichern(new Teilnehmer(null, "Ada", "Lovelace", "3a"));
        EinwahlAG einwahl = listeAus(einwahlAGRepository.findAll()).get(0);
        einwahlAGRepository.save(new EinwahlAG(einwahl.id(), teilnehmer.id(), ag.titel(), 1, true));

        byte[] json = ageinwahlDateiService.exportiereJson();
        leereDatenbank();

        ageinwahlDateiService.importiereJson(new ByteArrayInputStream(json));

        assertTrue(new String(json, StandardCharsets.UTF_8).contains("\"format\" : \"AGEinwahl\""));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ag", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teilnehmer", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM einwahl_ag WHERE auswahl = 1 AND zugewiesen = TRUE", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM erlaubter_jahrgang WHERE jahrgang = 3", Integer.class));
    }

    private void leereDatenbank() {
        jdbcTemplate.update("DELETE FROM einwahl_entdeckerangebot");
        jdbcTemplate.update("DELETE FROM einwahl_ag");
        jdbcTemplate.update("DELETE FROM einwahl_vormittags_ag");
        jdbcTemplate.update("DELETE FROM erlaubter_jahrgang");
        jdbcTemplate.update("DELETE FROM ag");
        jdbcTemplate.update("DELETE FROM teilnehmer");
    }

    private <T> List<T> listeAus(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
