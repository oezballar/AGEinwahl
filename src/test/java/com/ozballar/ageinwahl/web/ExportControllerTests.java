package com.ozballar.ageinwahl.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.ErlaubterJahrgang;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@SpringBootTest
class ExportControllerTests {

    @Autowired
    private ExportController exportController;

    @Autowired
    private AgService agService;

    @Autowired
    private TeilnehmerService teilnehmerService;

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
    void wunschzettelEnthaeltZweiAbschnitteProA4SeiteUndJaNeinFelder() {
        agService.speichern(new Ag(
                null,
                Ag.Wochentag.DIENSTAG,
                Ag.Zeit.VORMITTAG,
                Ag.Kategorie.AG,
                "Chor",
                "",
                "Frau Beispiel",
                "Musikraum",
                20,
                List.of(new ErlaubterJahrgang(3))
        ));
        agService.speichern(new Ag(
                null,
                Ag.Wochentag.DIENSTAG,
                Ag.Zeit.NACHMITTAG,
                Ag.Kategorie.AG,
                "Naturfreunde",
                "",
                "Herr Beispiel",
                "Garten",
                12,
                List.of(new ErlaubterJahrgang(3))
        ));
        agService.speichern(new Ag(
                null,
                Ag.Wochentag.DIENSTAG,
                Ag.Zeit.NACHMITTAG,
                Ag.Kategorie.AG,
                "Theater",
                "",
                "Frau Beispiel",
                "Aula",
                12,
                List.of(new ErlaubterJahrgang(3))
        ));
        agService.speichern(new Ag(
                null,
                Ag.Wochentag.DIENSTAG,
                Ag.Zeit.NACHMITTAG,
                Ag.Kategorie.AG,
                "Töpfern",
                "",
                "Frau Beispiel",
                "Werkraum",
                12,
                List.of(new ErlaubterJahrgang(3))
        ));
        agService.speichern(new Ag(
                null,
                Ag.Wochentag.DIENSTAG,
                Ag.Zeit.NACHMITTAG,
                Ag.Kategorie.AG,
                "Fußball",
                "",
                "Herr Beispiel",
                "Sportplatz",
                12,
                List.of(new ErlaubterJahrgang(3))
        ));
        agService.speichern(new Ag(
                null,
                Ag.Wochentag.DIENSTAG,
                Ag.Zeit.NACHMITTAG,
                Ag.Kategorie.AG,
                "Programmieren",
                "",
                "Frau Beispiel",
                "Computerraum",
                12,
                List.of(new ErlaubterJahrgang(3))
        ));
        teilnehmerService.speichern(new Teilnehmer(null, "Ada", "Lovelace", "3a"));
        teilnehmerService.speichern(new Teilnehmer(null, "Grace", "Hopper", "3a"));

        byte[] pdf = exportController.wunschzettel().getBody();

        assertNotNull(pdf);
        String inhalt = new String(pdf, Charset.forName("windows-1252"));
        assertTrue(inhalt.contains("/MediaBox [0 0 595 842]"));
        assertTrue(inhalt.contains("24 421 m 571 421 l S"));
        assertTrue(inhalt.contains("(Name: Ada Lovelace) Tj"));
        assertTrue(inhalt.contains("(Name: Grace Hopper) Tj"));
        assertTrue(inhalt.contains("(Dienstag - 7:30 - 8:15 Uhr) Tj"));
        assertTrue(inhalt.contains("(Dienstag - 13:30 - 14:30 Uhr) Tj"));
        assertTrue(inhalt.contains("(Naturfreunde) Tj"));
        assertTrue(inhalt.contains("(Theater) Tj"));
        assertTrue(inhalt.contains("(Töpfern) Tj"));
        assertTrue(inhalt.contains("(Fußball) Tj"));
        assertTrue(inhalt.contains("(Programmieren) Tj"));
        assertFalse(inhalt.contains("Vormittag"));
        assertFalse(inhalt.contains("Nachmittag"));
        assertTrue(inhalt.contains("(Ja) Tj"));
        assertTrue(inhalt.contains("(Nein) Tj"));
        assertTrue(inhalt.contains("(1/2/3/4/5) Tj"));
        assertFalse(inhalt.contains("(1/2/3/4) Tj"));
        assertFalse(inhalt.contains("(Wunsch) Tj"));
        assertEquals("attachment; filename=\"wunschzettel.pdf\"",
                exportController.wunschzettel().getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void stundenplaeneEnthaltenBelegteAgsUndZweiKinderProSeite() {
        Ag ag = agService.speichern(new Ag(
                null,
                Ag.Wochentag.MONTAG,
                Ag.Zeit.NACHMITTAG,
                Ag.Kategorie.AG,
                "Naturfreunde",
                "",
                "Frau Beispiel",
                "Garten",
                12,
                List.of(new ErlaubterJahrgang(3))
        ));
        Ag unvergebeneAg = agService.speichern(new Ag(
                null,
                Ag.Wochentag.DIENSTAG,
                Ag.Zeit.VORMITTAG,
                Ag.Kategorie.AG,
                "Nicht belegt",
                "",
                "Herr Beispiel",
                "Raum 1",
                12,
                List.of(new ErlaubterJahrgang(3))
        ));
        Teilnehmer ada = teilnehmerService.speichern(new Teilnehmer(null, "Ada", "Lovelace", "3a"));
        Teilnehmer grace = teilnehmerService.speichern(new Teilnehmer(null, "Grace", "Hopper", "3a"));
        jdbcTemplate.update("UPDATE einwahl_ag SET zugewiesen = TRUE WHERE teilnehmer_id = ? AND ag_titel = ?", ada.id(), ag.titel());
        jdbcTemplate.update("UPDATE einwahl_vormittags_ag SET zugewiesen = TRUE WHERE teilnehmer_id = ? AND ag_titel = ?", grace.id(), unvergebeneAg.titel());

        byte[] pdf = exportController.teilnehmerStundenplaene().getBody();

        assertNotNull(pdf);
        String inhalt = new String(pdf, Charset.forName("windows-1252"));
        assertTrue(inhalt.contains("/MediaBox [0 0 595 842]"));
        assertTrue(inhalt.contains("24 421 m 571 421 l S"));
        assertTrue(inhalt.contains("(Name: Ada Lovelace) Tj"));
        assertTrue(inhalt.contains("(Name: Grace Hopper) Tj"));
        assertTrue(inhalt.contains("(Montag) Tj"));
        assertTrue(inhalt.contains("(7:30–8:15 Uhr) Tj"));
        assertTrue(inhalt.contains("(13:30–14:30 Uhr) Tj"));
        assertTrue(inhalt.contains("(Naturfreunde) Tj"));
        assertTrue(inhalt.contains("(Nicht belegt) Tj"));
        assertEquals("attachment; filename=\"teilnehmer-stundenplaene.pdf\"",
                exportController.teilnehmerStundenplaene().getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void zipExporteEnthaltenGesamtPdfUndNachKlasseGruppiertePdf() throws IOException {
        agService.speichern(new Ag(
                null,
                Ag.Wochentag.MONTAG,
                Ag.Zeit.VORMITTAG,
                Ag.Kategorie.AG,
                "Chor & Musik",
                "",
                "Frau Beispiel",
                "Musikraum",
                20,
                List.of(new ErlaubterJahrgang(3))
        ));
        teilnehmerService.speichern(new Teilnehmer(null, "Ada", "Lovelace", "3a"));

        byte[] teilnehmerlistenZip = exportController.agTeilnehmerlistenZip().getBody();
        byte[] klassenuebersichtZip = exportController.klassenuebersichtZip().getBody();

        assertZipDateien(teilnehmerlistenZip, "ag-teilnehmerlisten.pdf", "ag-teilnehmerlisten-Chor___Musik.pdf");
        assertZipDateien(klassenuebersichtZip, "klassenuebersicht.pdf", "klassenuebersicht-3a.pdf");
        assertEquals("attachment; filename=\"ag-teilnehmerlisten.zip\"",
                exportController.agTeilnehmerlistenZip().getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("application/zip",
                exportController.klassenuebersichtZip().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
    }

    private void assertZipDateien(byte[] zip, String... erwarteteDateien) throws IOException {
        assertNotNull(zip);
        Set<String> dateien = new HashSet<>();
        try (ZipInputStream eingang = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry eintrag;
            while ((eintrag = eingang.getNextEntry()) != null) {
                dateien.add(eintrag.getName());
            }
        }
        assertEquals(Set.of(erwarteteDateien), dateien);
    }

    private void leereDatenbank() {
        jdbcTemplate.update("DELETE FROM einwahl_entdeckerangebot");
        jdbcTemplate.update("DELETE FROM einwahl_ag");
        jdbcTemplate.update("DELETE FROM einwahl_vormittags_ag");
        jdbcTemplate.update("DELETE FROM erlaubter_jahrgang");
        jdbcTemplate.update("DELETE FROM ag");
        jdbcTemplate.update("DELETE FROM teilnehmer");
    }
}
