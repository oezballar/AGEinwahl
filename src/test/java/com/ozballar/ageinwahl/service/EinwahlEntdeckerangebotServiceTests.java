package com.ozballar.ageinwahl.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.ErlaubterJahrgang;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.AgRepository;
import com.ozballar.ageinwahl.repository.EinwahlEntdeckerangebotRepository;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

class EinwahlEntdeckerangebotServiceTests {

    @Test
    void erstelltFuerTeilnehmerEintraegeFuerPassendeEntdeckerangeboteOhneAuswahl() {
        Teilnehmer teilnehmer = teilnehmer(1, "2a");
        Ag passendesEntdeckerangebot = ag("Entdecken", Ag.Kategorie.ENTDECKERANGEBOT, List.of(2));
        Ag falscheKategorie = ag("Sport", Ag.Kategorie.AG, List.of(2));
        Ag falscherJahrgang = ag("Forschen", Ag.Kategorie.ENTDECKERANGEBOT, List.of(1));
        List<EinwahlEntdeckerangebot> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlEntdeckerangebotService service = new EinwahlEntdeckerangebotService(
                einwahlRepository(List.of(), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(teilnehmer)),
                agRepository(List.of(passendesEntdeckerangebot, falscheKategorie, falscherJahrgang))
        );

        service.erstelleEintraegeFuerTeilnehmer(teilnehmer);

        assertEquals(1, gespeicherteEinwahlen.size());
        assertEquals(teilnehmer.nr(), gespeicherteEinwahlen.get(0).teilnehmerNr());
        assertEquals(passendesEntdeckerangebot.titel(), gespeicherteEinwahlen.get(0).agTitel());
        assertEquals(null, gespeicherteEinwahlen.get(0).auswahl());
    }

    @Test
    void erstelltFuerAgEintraegeFuerPassendeTeilnehmer() {
        Teilnehmer passenderTeilnehmer = teilnehmer(1, "3b");
        Teilnehmer falscherJahrgang = teilnehmer(2, "2a");
        Ag entdeckerangebot = ag("Entdecken", Ag.Kategorie.ENTDECKERANGEBOT, List.of(3));
        List<EinwahlEntdeckerangebot> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlEntdeckerangebotService service = new EinwahlEntdeckerangebotService(
                einwahlRepository(List.of(), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(passenderTeilnehmer, falscherJahrgang)),
                agRepository(List.of(entdeckerangebot))
        );

        service.erstelleEintraegeFuerAg(entdeckerangebot);

        assertEquals(1, gespeicherteEinwahlen.size());
        assertEquals(passenderTeilnehmer.nr(), gespeicherteEinwahlen.get(0).teilnehmerNr());
        assertEquals(entdeckerangebot.titel(), gespeicherteEinwahlen.get(0).agTitel());
    }

    @Test
    void erstelltKeineDoppeltenEintraege() {
        Teilnehmer teilnehmer = teilnehmer(1, "2a");
        Ag entdeckerangebot = ag("Entdecken", Ag.Kategorie.ENTDECKERANGEBOT, List.of(2));
        EinwahlEntdeckerangebot vorhandeneEinwahl = new EinwahlEntdeckerangebot(1, teilnehmer, entdeckerangebot, null);
        List<EinwahlEntdeckerangebot> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlEntdeckerangebotService service = new EinwahlEntdeckerangebotService(
                einwahlRepository(List.of(vorhandeneEinwahl), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(teilnehmer)),
                agRepository(List.of(entdeckerangebot))
        );

        service.erstelleEintraegeFuerTeilnehmer(teilnehmer);

        assertEquals(0, gespeicherteEinwahlen.size());
    }

    private static AgRepository agRepository(List<Ag> ags) {
        return repositoryProxy(AgRepository.class, ags, new ArrayList<>());
    }

    private static TeilnehmerRepository teilnehmerRepository(List<Teilnehmer> teilnehmer) {
        return repositoryProxy(TeilnehmerRepository.class, teilnehmer, new ArrayList<>());
    }

    private static EinwahlEntdeckerangebotRepository einwahlRepository(
            List<EinwahlEntdeckerangebot> vorhandeneEinwahlen,
            List<EinwahlEntdeckerangebot> gespeicherteEinwahlen
    ) {
        return repositoryProxy(EinwahlEntdeckerangebotRepository.class, vorhandeneEinwahlen, gespeicherteEinwahlen);
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

    private static Teilnehmer teilnehmer(Integer nr, String klasse) {
        return new Teilnehmer(nr, "Max", "Muster" + nr, klasse);
    }

    private static Ag ag(String titel, Ag.Kategorie kategorie, List<Integer> erlaubteJahrgaenge) {
        return new Ag(
                null,
                Ag.Wochentag.MONTAG,
                Ag.Zeit.VORMITTAG,
                kategorie,
                titel,
                "Verantwortlicher",
                "Ort",
                10,
                erlaubteJahrgaenge.stream()
                        .map(ErlaubterJahrgang::new)
                        .toList()
        );
    }
}
