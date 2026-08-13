package com.ozballar.ageinwahl.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                teilnehmer,
                new HashMap<>(Map.of(erlaubtesEntdeckerangebot, EinwahlEntdeckerangebot.Auswahl.JA))
        ));
    }

    @Test
    void einwahlEntdeckerangebotLehntFalscheKategorieOderFalschenJahrgangAb() {
        Teilnehmer teilnehmer = teilnehmer("2b");
        Ag falscheKategorie = ag(Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(2));
        Ag falscherJahrgang = ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.VORMITTAG, List.of(1));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebot(
                teilnehmer,
                new HashMap<>(Map.of(falscheKategorie, EinwahlEntdeckerangebot.Auswahl.JA))
        ));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebot(
                teilnehmer,
                new HashMap<>(Map.of(falscherJahrgang, EinwahlEntdeckerangebot.Auswahl.JA))
        ));
    }

    @Test
    void einwahlEntdeckerangebotBenoetigtTeilnehmerMitKlasse() {
        Ag entdeckerangebot = ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.VORMITTAG, List.of(2));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebot(
                null,
                new HashMap<>(Map.of(entdeckerangebot, EinwahlEntdeckerangebot.Auswahl.JA))
        ));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebot(
                teilnehmer(null),
                new HashMap<>(Map.of(entdeckerangebot, EinwahlEntdeckerangebot.Auswahl.JA))
        ));
    }

    @Test
    void einwahlAgErlaubtAgUndJahresAgAmNachmittagMitPassendemJahrgangUndSchluesselGroesserGleichEins() {
        Teilnehmer teilnehmer = teilnehmer("3a");
        Ag ag = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(3));
        Ag jahresAg = ag(Ag.Kategorie.JAHRES_AG, Ag.Zeit.NACHMITTAG, List.of(3));

        assertDoesNotThrow(() -> new EinwahlAG(teilnehmer, new HashMap<>(Map.of(1, ag))));
        assertDoesNotThrow(() -> new EinwahlAG(teilnehmer, new HashMap<>(Map.of(2, jahresAg))));
    }

    @Test
    void einwahlAgLehntUngueltigeSchluesselKategorieZeitOderJahrgangAb() {
        Teilnehmer teilnehmer = teilnehmer("3a");
        Ag erlaubteAg = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(3));
        Ag entdeckerangebot = ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.NACHMITTAG, List.of(3));
        Ag vormittagsAg = ag(Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(3));
        Ag falscherJahrgang = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(teilnehmer, new HashMap<>(Map.of(0, erlaubteAg))));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(teilnehmer, new HashMap<>(Map.of(1, entdeckerangebot))));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(teilnehmer, new HashMap<>(Map.of(1, vormittagsAg))));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(teilnehmer, new HashMap<>(Map.of(1, falscherJahrgang))));
    }

    @Test
    void einwahlAgBenoetigtTeilnehmerMitKlasse() {
        Ag ag = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(3));

        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(null, new HashMap<>(Map.of(1, ag))));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlAG(teilnehmer(null), new HashMap<>(Map.of(1, ag))));
    }

    @Test
    void aggregateBenoetigenRootObjekt() {
        Teilnehmer teilnehmer = teilnehmer("2a");
        Ag ag = ag(Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));
        EinwahlAG einwahlAG = new EinwahlAG(teilnehmer, new HashMap<>(Map.of(1, ag)));
        EinwahlEntdeckerangebot einwahlEntdeckerangebot = new EinwahlEntdeckerangebot(
                teilnehmer,
                new HashMap<>(Map.of(
                        ag(Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.VORMITTAG, List.of(2)),
                        EinwahlEntdeckerangebot.Auswahl.JA
                ))
        );

        assertDoesNotThrow(() -> new TeilnehmerAggregat(1, teilnehmer));
        assertDoesNotThrow(() -> new AgAggregat(1, ag));
        assertDoesNotThrow(() -> new EinwahlAGAggregat(1, einwahlAG));
        assertDoesNotThrow(() -> new EinwahlEntdeckerangebotAggregat(1, einwahlEntdeckerangebot));

        assertThrows(IllegalArgumentException.class, () -> new TeilnehmerAggregat(1, null));
        assertThrows(IllegalArgumentException.class, () -> new AgAggregat(1, null));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlAGAggregat(1, null));
        assertThrows(IllegalArgumentException.class, () -> new EinwahlEntdeckerangebotAggregat(1, null));
    }

    private static Teilnehmer teilnehmer(String klasse) {
        return new Teilnehmer(1, "Max", "Muster", klasse);
    }

    private static Ag ag(Ag.Kategorie kategorie, Ag.Zeit zeit, List<Integer> erlaubteJahrgaenge) {
        return new Ag(
                Ag.Wochentag.MONTAG,
                zeit,
                kategorie,
                "Titel",
                "Verantwortlicher",
                "Ort",
                10,
                erlaubteJahrgaenge
        );
    }

    private static List<Integer> listWithNull() {
        return java.util.Arrays.asList(1, null);
    }
}
