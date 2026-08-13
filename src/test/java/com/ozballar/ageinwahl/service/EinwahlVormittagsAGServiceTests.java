package com.ozballar.ageinwahl.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlVormittagsAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.AgRepository;
import com.ozballar.ageinwahl.repository.EinwahlVormittagsAGRepository;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

class EinwahlVormittagsAGServiceTests {

    @Test
    void erstelltFuerTeilnehmerEintraegeFuerPassendeVormittagsAgsOhneAuswahl() {
        Teilnehmer teilnehmer = teilnehmer(1, "2a");
        Ag passendeAg = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(2));
        Ag jahresAg = ag("Musik", Ag.Kategorie.JAHRES_AG, Ag.Zeit.VORMITTAG, List.of(2));
        Ag entdeckerangebot = ag("Entdecken", Ag.Kategorie.ENTDECKERANGEBOT, Ag.Zeit.VORMITTAG, List.of(2));
        Ag nachmittagsAg = ag("Forschen", Ag.Kategorie.AG, Ag.Zeit.NACHMITTAG, List.of(2));
        List<EinwahlVormittagsAG> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlVormittagsAGService service = new EinwahlVormittagsAGService(
                einwahlRepository(List.of(), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(teilnehmer)),
                agRepository(List.of(passendeAg, jahresAg, entdeckerangebot, nachmittagsAg))
        );

        service.erstelleEintraegeFuerTeilnehmer(teilnehmer);

        assertEquals(2, gespeicherteEinwahlen.size());
        assertEquals(passendeAg, gespeicherteEinwahlen.get(0).ag());
        assertEquals(null, gespeicherteEinwahlen.get(0).auswahl());
        assertEquals(jahresAg, gespeicherteEinwahlen.get(1).ag());
        assertEquals(null, gespeicherteEinwahlen.get(1).auswahl());
    }

    @Test
    void erstelltFuerAgEintraegeFuerPassendeTeilnehmer() {
        Teilnehmer passenderTeilnehmer = teilnehmer(1, "3b");
        Teilnehmer falscherJahrgang = teilnehmer(2, "2a");
        Ag ag = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(3));
        List<EinwahlVormittagsAG> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlVormittagsAGService service = new EinwahlVormittagsAGService(
                einwahlRepository(List.of(), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(passenderTeilnehmer, falscherJahrgang)),
                agRepository(List.of(ag))
        );

        service.erstelleEintraegeFuerAg(ag);

        assertEquals(1, gespeicherteEinwahlen.size());
        assertEquals(passenderTeilnehmer, gespeicherteEinwahlen.get(0).teilnehmer());
        assertEquals(ag, gespeicherteEinwahlen.get(0).ag());
    }

    @Test
    void erstelltKeineDoppeltenEintraege() {
        Teilnehmer teilnehmer = teilnehmer(1, "2a");
        Ag ag = ag("Sport", Ag.Kategorie.AG, Ag.Zeit.VORMITTAG, List.of(2));
        EinwahlVormittagsAG vorhandeneEinwahl = new EinwahlVormittagsAG(1, teilnehmer, ag, null);
        List<EinwahlVormittagsAG> gespeicherteEinwahlen = new ArrayList<>();
        EinwahlVormittagsAGService service = new EinwahlVormittagsAGService(
                einwahlRepository(List.of(vorhandeneEinwahl), gespeicherteEinwahlen),
                teilnehmerRepository(List.of(teilnehmer)),
                agRepository(List.of(ag))
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

    private static EinwahlVormittagsAGRepository einwahlRepository(
            List<EinwahlVormittagsAG> vorhandeneEinwahlen,
            List<EinwahlVormittagsAG> gespeicherteEinwahlen
    ) {
        return repositoryProxy(EinwahlVormittagsAGRepository.class, vorhandeneEinwahlen, gespeicherteEinwahlen);
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
                erlaubteJahrgaenge
        );
    }
}
