package com.ozballar.ageinwahl.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.EinwahlVormittagsAG;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlAGService;
import com.ozballar.ageinwahl.service.EinwahlEntdeckerangebotService;
import com.ozballar.ageinwahl.service.EinwahlVormittagsAGService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Controller
public class HomeController {

    private final TeilnehmerService teilnehmerService;
    private final AgService agService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlAGService einwahlAGService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;

    public HomeController(
            TeilnehmerService teilnehmerService,
            AgService agService,
            EinwahlEntdeckerangebotService einwahlEntdeckerangebotService,
            EinwahlAGService einwahlAGService,
            EinwahlVormittagsAGService einwahlVormittagsAGService
    ) {
        this.teilnehmerService = teilnehmerService;
        this.agService = agService;
        this.einwahlEntdeckerangebotService = einwahlEntdeckerangebotService;
        this.einwahlAGService = einwahlAGService;
        this.einwahlVormittagsAGService = einwahlVormittagsAGService;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<Ag> ags = listeAus(agService.findeAlle());
        List<EinwahlEntdeckerangebot> entdeckerEinwahlen = listeAus(einwahlEntdeckerangebotService.findeAlle());
        List<EinwahlAG> nachmittagsEinwahlen = listeAus(einwahlAGService.findeAlle());
        List<EinwahlVormittagsAG> vormittagsEinwahlen = listeAus(einwahlVormittagsAGService.findeAlle());
        Map<String, Ag> agNachTitel = ags.stream()
                .collect(Collectors.toMap(Ag::titel, ag -> ag));

        int offeneFelder = (int) entdeckerEinwahlen.stream().filter(einwahl -> einwahl.auswahl() == null).count()
                + (int) nachmittagsEinwahlen.stream().filter(einwahl -> einwahl.auswahl() == null).count()
                + (int) vormittagsEinwahlen.stream().filter(einwahl -> einwahl.auswahl() == null).count();

        Map<String, Long> belegungNachAgTitel = belegungNachAgTitel(entdeckerEinwahlen, nachmittagsEinwahlen, vormittagsEinwahlen);
        List<AgBelegung> belegungen = ags.stream()
                .map(ag -> agBelegung(ag, belegungNachAgTitel.getOrDefault(ag.titel(), 0L).intValue()))
                .sorted(Comparator
                        .comparing(AgBelegung::ueberbelegt).reversed()
                        .thenComparing(AgBelegung::wochentag)
                        .thenComparing(AgBelegung::titel))
                .toList();

        int abgegebeneAuswahlen = (int) entdeckerEinwahlen.stream().filter(einwahl -> einwahl.auswahl() != null).count()
                + (int) nachmittagsEinwahlen.stream().filter(einwahl -> einwahl.auswahl() != null).count()
                + (int) vormittagsEinwahlen.stream().filter(einwahl -> einwahl.auswahl() != null).count();
        int ueberbelegteAgs = (int) belegungen.stream()
                .filter(AgBelegung::ueberbelegt)
                .count();
        int freiePlaetze = belegungen.stream()
                .mapToInt(belegung -> Math.max(0, belegung.maximaleTeilnehmerzahl() - belegung.belegt()))
                .sum();

        model.addAttribute("anzahlTeilnehmer", listeAus(teilnehmerService.findeAlle()).size());
        model.addAttribute("anzahlAgs", ags.size());
        model.addAttribute("offeneFelder", offeneFelder);
        model.addAttribute("abgegebeneAuswahlen", abgegebeneAuswahlen);
        model.addAttribute("ueberbelegteAgs", ueberbelegteAgs);
        model.addAttribute("freiePlaetze", freiePlaetze);
        model.addAttribute("offeneNachmittagsZuweisungen", offeneNachmittagsZuweisungen(nachmittagsEinwahlen, agNachTitel));
        model.addAttribute("belegungen", belegungen);
        return "dashboard/index";
    }

    private Map<String, Long> belegungNachAgTitel(
            List<EinwahlEntdeckerangebot> entdeckerEinwahlen,
            List<EinwahlAG> nachmittagsEinwahlen,
            List<EinwahlVormittagsAG> vormittagsEinwahlen
    ) {
        Map<String, Long> belegung = entdeckerEinwahlen.stream()
                .filter(einwahl -> einwahl.auswahl() == EinwahlEntdeckerangebot.Auswahl.JA)
                .collect(Collectors.groupingBy(EinwahlEntdeckerangebot::agTitel, Collectors.counting()));

        nachmittagsEinwahlen.stream()
                .filter(einwahl -> Integer.valueOf(1).equals(einwahl.auswahl()))
                .collect(Collectors.groupingBy(EinwahlAG::agTitel, Collectors.counting()))
                .forEach((titel, anzahl) -> belegung.merge(titel, anzahl, Long::sum));

        vormittagsEinwahlen.stream()
                .filter(einwahl -> einwahl.auswahl() == EinwahlVormittagsAG.Auswahl.JA)
                .collect(Collectors.groupingBy(EinwahlVormittagsAG::agTitel, Collectors.counting()))
                .forEach((titel, anzahl) -> belegung.merge(titel, anzahl, Long::sum));

        return belegung;
    }

    private int offeneNachmittagsZuweisungen(List<EinwahlAG> nachmittagsEinwahlen, Map<String, Ag> agNachTitel) {
        return (int) nachmittagsEinwahlen.stream()
                .collect(Collectors.groupingBy(einwahl -> new NachmittagsZuweisungSchluessel(
                        einwahl.teilnehmerNr(),
                        wochentagVon(einwahl, agNachTitel)
                )))
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().wochentag() != null)
                .filter(entry -> entry.getValue().stream().noneMatch(einwahl -> Integer.valueOf(1).equals(einwahl.auswahl())))
                .count();
    }

    private Ag.Wochentag wochentagVon(EinwahlAG einwahl, Map<String, Ag> agNachTitel) {
        Ag ag = agNachTitel.get(einwahl.agTitel());
        return ag == null ? null : ag.wochentag();
    }

    private AgBelegung agBelegung(Ag ag, int belegt) {
        int maximaleTeilnehmerzahl = ag.maximaleTeilnehmerzahl() == null ? 0 : ag.maximaleTeilnehmerzahl();
        int prozent = maximaleTeilnehmerzahl == 0 ? 0 : (int) Math.round((belegt * 100.0) / maximaleTeilnehmerzahl);
        int balkenBreite = Math.max(0, Math.min(100, prozent));
        return new AgBelegung(
                ag.titel(),
                ag.wochentag().name(),
                ag.zeit().name(),
                ag.kategorie().name(),
                maximaleTeilnehmerzahl,
                belegt,
                prozent,
                balkenBreite,
                belegt > maximaleTeilnehmerzahl
        );
    }

    private <T> List<T> listeAus(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    public record AgBelegung(
            String titel,
            String wochentag,
            String zeit,
            String kategorie,
            int maximaleTeilnehmerzahl,
            int belegt,
            int prozent,
            int balkenBreite,
            boolean ueberbelegt
    ) {
    }

    private record NachmittagsZuweisungSchluessel(
            Integer teilnehmerNr,
            Ag.Wochentag wochentag
    ) {
        private NachmittagsZuweisungSchluessel {
            Objects.requireNonNull(teilnehmerNr);
        }
    }
}
