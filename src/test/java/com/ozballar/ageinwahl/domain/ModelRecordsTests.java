package com.ozballar.ageinwahl.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class ModelRecordsTests {

    @Test
    void teilnehmerErlaubtKlassenMitOptionalemKleinbuchstaben() {
        assertDoesNotThrow(() -> new Teilnehmer(1, "Max", "Muster", "1"));
        assertDoesNotThrow(() -> new Teilnehmer(2, "Erika", "Muster", "2b"));
        assertDoesNotThrow(() -> new Teilnehmer(3, "Lisa", "Muster", "4c"));
    }

    @Test
    void teilnehmerLehntUngueltigeKlassenAb() {
        assertThrows(IllegalArgumentException.class, () -> new Teilnehmer(1, "Max", "Muster", "0a"));
        assertThrows(IllegalArgumentException.class, () -> new Teilnehmer(1, "Max", "Muster", "5"));
        assertThrows(IllegalArgumentException.class, () -> new Teilnehmer(1, "Max", "Muster", "1d"));
        assertThrows(IllegalArgumentException.class, () -> new Teilnehmer(1, "Max", "Muster", "11"));
        assertThrows(IllegalArgumentException.class, () -> new Teilnehmer(1, "Max", "Muster", null));
    }

    @Test
    void agErlaubtNurJahrgaengeVonEinsBisVier() {
        assertDoesNotThrow(() -> ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(1, 2, 3, 4)));
        assertDoesNotThrow(() -> ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, null));

        assertThrows(IllegalArgumentException.class, () -> ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(5)));
        assertThrows(IllegalArgumentException.class, () -> ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, listWithNull()));
    }

    @Test
    void einwahlEntdeckerangebotErlaubtNurPassendeEntdeckerangebote() {
        Teilnehmer teilnehmer = teilnehmer("2b");
        Ag erlaubtesEntdeckerangebot = ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.VORMITTAG, List.of(2));

        assertDoesNotThrow(() -> new EinwahlEntdeckerangebot(
                null,
                teilnehmer,
                erlaubtesEntdeckerangebot,
                null
        ));
    }

    @Test
    void einwahlEntdeckerangebotLehntFalscheKategorieOderFalschenJahrgangAb() {
        Teilnehmer teilnehmer = teilnehmer("2b");
        Ag falscheKategorie = ag(Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(2));
        Ag falscherJahrgang = ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.VORMITTAG, List.of(1));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebot(
                null,
                teilnehmer,
                falscheKategorie,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebot(
                null,
                teilnehmer,
                falscherJahrgang,
                null
        ));
    }

    @Test
    void einwahlEntdeckerangebotBenoetigtTeilnehmerMitKlasse() {
        Ag entdeckerangebot = ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.VORMITTAG, List.of(2));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebot(
                null,
                null,
                entdeckerangebot,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebot(
                null,
                teilnehmer(null),
                entdeckerangebot,
                null
        ));
    }

    @Test
    void einwahlAgErlaubtAgUndJahresAgAmNachmittagMitPassendemJahrgangUndSchluesselGroesserGleichEins() {
        Teilnehmer teilnehmer = teilnehmer("3a");
        Ag ag = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(3));
        Ag jahresAg = ag(Ag.Kategorie.JAHRES_AG, Ag.Zeit.NACHMITTAG, List.of(3));

        assertDoesNotThrow(() -> new EinwahlAG(null, teilnehmer, ag, null));
        assertDoesNotThrow(() -> new EinwahlAG(null, teilnehmer, jahresAg, 1));
    }

    @Test
    void einwahlAgLehntUngueltigeSchluesselKategorieZeitOderJahrgangAb() {
        Teilnehmer teilnehmer = teilnehmer("3a");
        Ag entdeckerangebot = ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.NACHMITTAG, List.of(3));
        Ag vormittagsAg = ag(Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(3));
        Ag falscherJahrgang = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(null, teilnehmer, entdeckerangebot, null));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(null, teilnehmer, vormittagsAg, null));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(null, teilnehmer, falscherJahrgang, null));
    }

    @Test
    void einwahlAgBenoetigtTeilnehmerMitKlasse() {
        Ag ag = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(3));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(null, null, ag, null));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(null, teilnehmer(null), ag, null));
    }

    @Test
    void einwahlAgLehntAuswahlKleinerEinsAb() {
        Teilnehmer teilnehmer = teilnehmer("2a");
        Ag ersteAg = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(
                null,
                teilnehmer,
                ersteAg,
                0
        ));
    }

    @Test
    void einwahlVormittagsAgErlaubtAgUndJahresAgAmVormittagMitPassendemJahrgang() {
        Teilnehmer teilnehmer = teilnehmer("3a");
        Ag ag = ag(Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(3));
        Ag jahresAg = ag(Ag.Kategorie.JAHRES_AG, Ag.Zeit.VORMITTAG, List.of(3));

        assertDoesNotThrow(() -> new EinwahlVormittagsAG(null, teilnehmer, ag, null));
        assertDoesNotThrow(() -> new EinwahlVormittagsAG(null, teilnehmer, jahresAg, EinwahlVormittagsAG.Auswahl.JA));
    }

    @Test
    void einwahlVormittagsAgLehntUngueltigeKategorieZeitOderJahrgangAb() {
        Teilnehmer teilnehmer = teilnehmer("3a");
        Ag entdeckerangebot = ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.VORMITTAG, List.of(3));
        Ag nachmittagsAg = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(3));
        Ag falscherJahrgang = ag(Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(2));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlVormittagsAG(null, teilnehmer, entdeckerangebot, null));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlVormittagsAG(null, teilnehmer, nachmittagsAg, null));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlVormittagsAG(null, teilnehmer, falscherJahrgang, null));
    }

    @Test
    void einwahlVormittagsAgErlaubtJaUndNeinAlsAuswahl() {
        Teilnehmer teilnehmer = teilnehmer("2a");
        Ag ag = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(2));

        assertDoesNotThrow(() -> new EinwahlVormittagsAG(null, teilnehmer, ag, EinwahlVormittagsAG.Auswahl.JA));
        assertDoesNotThrow(() -> new EinwahlVormittagsAG(null, teilnehmer, ag, EinwahlVormittagsAG.Auswahl.NEIN));
    }

    private static Teilnehmer teilnehmer(String klasse) {
        return new Teilnehmer(1, "Max", "Muster", klasse);
    }

    private static Ag ag(Ag.Kategorie kategorie, Ag.Zeit zeit, List<Integer> erlaubteJahrgaenge) {
        return ag("Titel", kategorie, zeit, erlaubteJahrgaenge);
    }

    private static Ag ag(String titel, Ag.Kategorie kategorie, Ag.Zeit zeit, List<Integer> erlaubteJahrgaenge) {
        return new Ag(
                null,
                Ag.Wochentag.MONTAG,
                zeit,
                kategorie,
                titel,
                "Verantwortlicher",
                "Ort",
                10,
                erlaubteJahrgaenge == null ? null : erlaubteJahrgaenge.stream()
                        .map(ErlaubterJahrgang::new)
                        .toList()
        );
    }

    private static List<Integer> listWithNull() {
        return java.util.Arrays.asList(1, null);
    }
}
