package com.ozballar.ageinwahl.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.ErlaubterJahrgang;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.AgRepository;
import com.ozballar.ageinwahl.repository.EinwahlAGRepository;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

class EinwahlAGServiceTests {

    @Test
    void erstelltFuerTeilnehmerEintraegeFuerPassendeAgsOhneAuswahl() {
        Teilnehmer teilnehmer = teilnehmer(1, "2a");
        Ag passendeAg = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));
        Ag jahresAg = ag("Musik", Ag.Kategorie.JAHRES_AG, Ag.Zeit.NACHMITTAG, List.of(2));
        Ag entdeckerangebot = ag("Entdecken", Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.NACHMITTAG, List.of(2));
        Ag vormittagsAg = ag("Forschen", Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(2));
        List<EinwahlAG> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlAGService service = new EinwahlAGService(
                einwahlRepository(List.of(), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(teilnehmer)),
                agRepository(List.of(passendeAg, jahresAg, entdeckerangebot, vormittagsAg))
        );

        service.erstelleEintraegeFuerTeilnehmer(teilnehmer);

        assertEquals(2, gespeicherteEinwahlen.size());
        assertEquals(passendeAg.titel(), gespeicherteEinwahlen.get(0).agTitel());
        assertEquals(null, gespeicherteEinwahlen.get(0).auswahl());
        assertEquals(jahresAg.titel(), gespeicherteEinwahlen.get(1).agTitel());
        assertEquals(null, gespeicherteEinwahlen.get(1).auswahl());
    }

    @Test
    void erstelltFuerAgEintraegeFuerPassendeTeilnehmer() {
        Teilnehmer passenderTeilnehmer = teilnehmer(1, "3b");
        Teilnehmer falscherJahrgang = teilnehmer(2, "2a");
        Ag ag = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(3));
        List<EinwahlAG> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlAGService service = new EinwahlAGService(
                einwahlRepository(List.of(), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(passenderTeilnehmer, falscherJahrgang)),
                agRepository(List.of(ag))
        );

        service.erstelleEintraegeFuerAg(ag);

        assertEquals(1, gespeicherteEinwahlen.size());
        assertEquals(passenderTeilnehmer.id(), gespeicherteEinwahlen.get(0).teilnehmerId());
        assertEquals(ag.titel(), gespeicherteEinwahlen.get(0).agTitel());
    }

    @Test
    void erstelltKeineDoppeltenEintraege() {
        Teilnehmer teilnehmer = teilnehmer(1, "2a");
        Ag ag = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));
        EinwahlAG vorhandeneEinwahl = new EinwahlAG(1, teilnehmer, ag, null);
        List<EinwahlAG> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlAGService service = new EinwahlAGService(
                einwahlRepository(List.of(vorhandeneEinwahl), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(teilnehmer)),
                agRepository(List.of(ag))
        );

        service.erstelleEintraegeFuerTeilnehmer(teilnehmer);

        assertEquals(0, gespeicherteEinwahlen.size());
    }

    @Test
    void speichertGenauEineZuweisungFuerNachmittagsgruppe() {
        Teilnehmer teilnehmer = teilnehmer(1, "2a");
        Ag sport = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));
        Ag musik = ag("Musik", Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));
        EinwahlAG sportEinwahl = new EinwahlAG(1, teilnehmer, sport, 1, true);
        EinwahlAG musikEinwahl = new EinwahlAG(2, teilnehmer, musik, 2, false);
        List<EinwahlAG> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlAGService service = new EinwahlAGService(
                einwahlRepository(List.of(sportEinwahl, musikEinwahl), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(teilnehmer)),
                agRepository(List.of(sport, musik))
        );

        service.speichereZuweisung(teilnehmer.id(), Ag.Wochentag.MONTAG, musikEinwahl.id());

        assertEquals(2, gespeicherteEinwahlen.size());
        assertFalse(gespeicherteEinwahlen.get(0).zugewiesen());
        assertTrue(gespeicherteEinwahlen.get(1).zugewiesen());
        assertEquals(1, gespeicherteEinwahlen.get(0).auswahl());
        assertEquals(2, gespeicherteEinwahlen.get(1).auswahl());
    }

    private static AgRepository agRepository(List<Ag> ags) {
        return repositoryProxy(AgRepository.class, ags, new ArrayList<>());
    }

    private static TeilnehmerRepository teilnehmerRepository(List<Teilnehmer> teilnehmer) {
        return repositoryProxy(TeilnehmerRepository.class, teilnehmer, new ArrayList<>());
    }

    private static EinwahlAGRepository einwahlRepository(
            List<EinwahlAG> vorhandeneEinwahlen,
            List<EinwahlAG> gespeicherteEinwahlen
    ) {
        return repositoryProxy(EinwahlAGRepository.class, vorhandeneEinwahlen, gespeicherteEinwahlen);
    }

    @SuppressWarnings("unchecked")
    private static <T> T repositoryProxy(Class<T> repositoryTyp, List<?> vorhandeneEintraege, List<?> gespeicherteEintraege) {
        return (T) Proxy.newProxyInstance(
                repositoryTyp.getClassLoader(),
                new Class<?>[]{repositoryTyp},
                (proxy, method, args) -> {
                    if (method.getName().equals("findAll")) {
                        return vorhandeneEintraege;
                    }

                    if (method.getName().equals("save")) {
                        ((List<Object>) gespeicherteEintraege).add(args[0]);
                        return args[0];
                    }

                    if (Iterable.class.isAssignableFrom(method.getReturnType())) {
                        return List.of();
                    }

                    return null;
                }
        );
    }

    private static Teilnehmer teilnehmer(Integer id, String klasse) {
        return new Teilnehmer(id, "Max", "Muster" + id, klasse);
    }

    private static Ag ag(String titel, Ag.Kategorie kategorie, Ag.Zeit zeit, List<Integer> erlaubteJahrgaenge) {
        return new Ag(
                null,
                Ag.Wochentag.MONTAG,
                zeit,
                kategorie,
                titel,
                "Beschreibung",
                "Verantwortlicher",
                "Ort",
                10,
                erlaubteJahrgaenge.stream()
                        .map(ErlaubterJahrgang::new)
                        .toList()
        );
    }
}
