package com.ozballar.ageinwahl.web;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
@RequestMapping("/zuweisungen")
public class ZuweisungController {

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;
    private final EinwahlAGService einwahlAGService;

    public ZuweisungController(
            AgService agService,
            TeilnehmerService teilnehmerService,
            EinwahlEntdeckerangebotService einwahlEntdeckerangebotService,
            EinwahlVormittagsAGService einwahlVormittagsAGService,
            EinwahlAGService einwahlAGService
    ) {
        this.agService = agService;
        this.teilnehmerService = teilnehmerService;
        this.einwahlEntdeckerangebotService = einwahlEntdeckerangebotService;
        this.einwahlVormittagsAGService = einwahlVormittagsAGService;
        this.einwahlAGService = einwahlAGService;
    }

    @GetMapping
    public String index(Model model) {
        Map<String, Ag> agNachTitel = listeAus(agService.findeAlle()).stream()
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        List<EinwahlEntdeckerangebot> entdeckerangebote = listeAus(einwahlEntdeckerangebotService.findeAlle());
        List<EinwahlVormittagsAG> vormittagsAgs = listeAus(einwahlVormittagsAGService.findeAlle());
        List<EinwahlAG> nachmittagsAgs = listeAus(einwahlAGService.findeAlle());
        Map<Integer, String> teilnehmerNamen = listeAus(teilnehmerService.findeAlle()).stream()
                .collect(Collectors.toMap(
                        teilnehmer -> teilnehmer.id(),
                        teilnehmer -> teilnehmer.vorname() + " " + teilnehmer.name()
                ));

        List<AgUebersicht> ueberbelegteAgs = ueberbelegteAgs(agNachTitel, entdeckerangebote, vormittagsAgs, nachmittagsAgs);
        model.addAttribute("anzahlTeilnehmer", anzahl(teilnehmerService.findeAlle()));
        model.addAttribute("anzahlZuweisungen", anzahlZuweisungen(entdeckerangebote, vormittagsAgs, nachmittagsAgs));
        model.addAttribute("offeneZuweisungen", offeneZuweisungen(agNachTitel, entdeckerangebote, vormittagsAgs, nachmittagsAgs));
        model.addAttribute("ueberbelegteAgs", ueberbelegteAgs);
        model.addAttribute("teilnehmerNamen", teilnehmerNamen);
        model.addAttribute("matrix", matrix(nachmittagsAgs, agNachTitel));
        return "zuweisungen/index";
    }

    @PostMapping("/zuweisen")
    public String zuweisen(
            @RequestParam String wochentag,
            @RequestParam Map<String, String> parameter
    ) {
        parameter.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("teilnehmer_"))
                .forEach(entry -> einwahlAGService.speichereZuweisung(
                        Integer.valueOf(entry.getKey().substring("teilnehmer_".length())),
                        Ag.Wochentag.valueOf(wochentag),
                        Integer.valueOf(entry.getValue())
                ));
        return "redirect:/zuweisungen";
    }

    private Matrix matrix(List<EinwahlAG> einwahlen, Map<String, Ag> agNachTitel) {
        Map<Ag.Wochentag, List<EinwahlAG>> einwahlenNachWochentag = einwahlen.stream()
                .filter(einwahl -> agNachTitel.containsKey(einwahl.agTitel()))
                .collect(Collectors.groupingBy(einwahl -> agNachTitel.get(einwahl.agTitel()).wochentag()));
        List<TagesMatrix> tage = einwahlenNachWochentag.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> tagesMatrix(entry.getKey(), entry.getValue(), agNachTitel))
                .toList();
        return new Matrix(tage);
    }

    private TagesMatrix tagesMatrix(
            Ag.Wochentag wochentag,
            List<EinwahlAG> einwahlen,
            Map<String, Ag> agNachTitel
    ) {
        List<MatrixZeile> zeilen = einwahlen.stream()
                .collect(Collectors.groupingBy(EinwahlAG::agTitel))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Ag ag = agNachTitel.get(entry.getKey());
                    int belegtePlaetze = (int) entry.getValue().stream()
                            .filter(this::istZugewiesen)
                            .count();
                    int maximaleTeilnehmerzahl = ag == null || ag.maximaleTeilnehmerzahl() == null
                            ? 0 : ag.maximaleTeilnehmerzahl();
                    Map<Integer, Integer> prioritaeten = entry.getValue().stream()
                            .filter(einwahl -> einwahl.auswahl() != null)
                            .collect(Collectors.toMap(EinwahlAG::teilnehmerId, EinwahlAG::auswahl));
                    Map<Integer, Integer> einwahlIds = entry.getValue().stream()
                            .collect(Collectors.toMap(EinwahlAG::teilnehmerId, EinwahlAG::id));
                    Map<Integer, Integer> zugewieseneEinwahlIds = entry.getValue().stream()
                            .filter(this::istZugewiesen)
                            .collect(Collectors.toMap(EinwahlAG::teilnehmerId, EinwahlAG::id));
                    return new MatrixZeile(entry.getKey(), belegtePlaetze, maximaleTeilnehmerzahl,
                            prioritaeten, einwahlIds, zugewieseneEinwahlIds);
                })
                .toList();
        List<Integer> teilnehmerIds = zeilen.stream()
                .flatMap(zeile -> einwahlen.stream().map(EinwahlAG::teilnehmerId))
                .distinct()
                .sorted()
                .toList();
        return new TagesMatrix(wochentagText(wochentag), teilnehmerIds, zeilen);
    }

    private List<AgUebersicht> ueberbelegteAgs(
            Map<String, Ag> agNachTitel,
            List<EinwahlEntdeckerangebot> entdeckerangebote,
            List<EinwahlVormittagsAG> vormittagsAgs,
            List<EinwahlAG> nachmittagsAgs
    ) {
        Map<String, Integer> belegungen = new HashMap<>();
        entdeckerangebote.stream().filter(this::istZugewiesen).forEach(einwahl -> zaehle(belegungen, einwahl.agTitel()));
        vormittagsAgs.stream().filter(this::istZugewiesen).forEach(einwahl -> zaehle(belegungen, einwahl.agTitel()));
        nachmittagsAgs.stream().filter(this::istZugewiesen).forEach(einwahl -> zaehle(belegungen, einwahl.agTitel()));

        return belegungen.entrySet().stream()
                .map(entry -> {
                    Ag ag = agNachTitel.get(entry.getKey());
                    int maximaleTeilnehmerzahl = ag == null || ag.maximaleTeilnehmerzahl() == null ? 0 : ag.maximaleTeilnehmerzahl();
                    return new AgUebersicht(
                            entry.getKey(),
                            ag == null ? "" : wochentagText(ag.wochentag()),
                            ag == null ? "" : ag.zeit().name(),
                            entry.getValue(),
                            maximaleTeilnehmerzahl,
                            entry.getValue() - maximaleTeilnehmerzahl
                    );
                })
                .filter(ag -> {
                    Ag definition = agNachTitel.get(ag.titel());
                    return definition != null
                            && definition.zeit() == Ag.Zeit.NACHMITTAG
                            && definition.kategorie() == Ag.Kategorie.AG;
                })
                .filter(ag -> ag.ueberbelegung() > 0)
                .sorted(Comparator.comparing(AgUebersicht::ueberbelegung).reversed().thenComparing(AgUebersicht::titel))
                .toList();
    }

    private int offeneZuweisungen(
            Map<String, Ag> agNachTitel,
            List<EinwahlEntdeckerangebot> entdeckerangebote,
            List<EinwahlVormittagsAG> vormittagsAgs,
            List<EinwahlAG> nachmittagsAgs
    ) {
        int offeneJaNein = gruppenOhneZuweisung(entdeckerangebote.stream()
                .filter(einwahl -> einwahl.auswahl() == EinwahlEntdeckerangebot.Auswahl.JA)
                .map(einwahl -> new GruppenSchluessel(einwahl.teilnehmerId(), einwahl.agTitel()))
                .toList(), entdeckerangebote.stream().filter(this::istZugewiesen)
                .map(einwahl -> new GruppenSchluessel(einwahl.teilnehmerId(), einwahl.agTitel())).toList());
        int offeneVormittage = gruppenOhneZuweisung(vormittagsAgs.stream()
                .filter(einwahl -> einwahl.auswahl() == EinwahlVormittagsAG.Auswahl.JA)
                .map(einwahl -> new GruppenSchluessel(einwahl.teilnehmerId(), einwahl.agTitel()))
                .toList(), vormittagsAgs.stream().filter(this::istZugewiesen)
                .map(einwahl -> new GruppenSchluessel(einwahl.teilnehmerId(), einwahl.agTitel())).toList());

        Map<Integer, Map<Ag.Wochentag, List<EinwahlAG>>> nachmittagsNachTag = nachmittagsAgs.stream()
                .filter(einwahl -> agNachTitel.containsKey(einwahl.agTitel()))
                .collect(Collectors.groupingBy(EinwahlAG::teilnehmerId,
                        Collectors.groupingBy(einwahl -> agNachTitel.get(einwahl.agTitel()).wochentag())));
        int offeneNachmittage = (int) nachmittagsNachTag.values().stream()
                .flatMap(tage -> tage.values().stream())
                .filter(einwahlen -> einwahlen.stream().noneMatch(this::istZugewiesen))
                .count();
        return offeneJaNein + offeneVormittage + offeneNachmittage;
    }

    private int gruppenOhneZuweisung(List<GruppenSchluessel> optionen, List<GruppenSchluessel> zuweisungen) {
        return (int) optionen.stream().distinct().filter(option -> !zuweisungen.contains(option)).count();
    }

    private int anzahlZuweisungen(
            List<EinwahlEntdeckerangebot> entdeckerangebote,
            List<EinwahlVormittagsAG> vormittagsAgs,
            List<EinwahlAG> nachmittagsAgs
    ) {
        return (int) StreamSupport.stream(entdeckerangebote.spliterator(), false).filter(this::istZugewiesen).count()
                + (int) StreamSupport.stream(vormittagsAgs.spliterator(), false).filter(this::istZugewiesen).count()
                + (int) StreamSupport.stream(nachmittagsAgs.spliterator(), false).filter(this::istZugewiesen).count();
    }

    private boolean istZugewiesen(EinwahlEntdeckerangebot einwahl) {
        return Boolean.TRUE.equals(einwahl.zugewiesen());
    }

    private boolean istZugewiesen(EinwahlVormittagsAG einwahl) {
        return Boolean.TRUE.equals(einwahl.zugewiesen());
    }

    private boolean istZugewiesen(EinwahlAG einwahl) {
        return Boolean.TRUE.equals(einwahl.zugewiesen());
    }

    private void zaehle(Map<String, Integer> belegungen, String titel) {
        belegungen.merge(titel, 1, Integer::sum);
    }

    private String wochentagText(Ag.Wochentag wochentag) {
        return switch (wochentag) {
            case MONTAG -> "Montag";
            case DIENSTAG -> "Dienstag";
            case MITTWOCH -> "Mittwoch";
            case DONNERSTAG -> "Donnerstag";
            case FREITAG -> "Freitag";
        };
    }

    private long anzahl(Iterable<?> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).count();
    }

    private <T> List<T> listeAus(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    public record AgUebersicht(String titel, String wochentag, String zeit, int belegt, int maximaleTeilnehmerzahl, int ueberbelegung) {
    }

    public record Matrix(List<TagesMatrix> tage) {
    }

    public record TagesMatrix(String wochentag, List<Integer> teilnehmerIds, List<MatrixZeile> zeilen) {
    }

    public record MatrixZeile(
            String agTitel,
            int belegtePlaetze,
            int maximaleTeilnehmerzahl,
            Map<Integer, Integer> prioritaeten,
            Map<Integer, Integer> einwahlIds,
            Map<Integer, Integer> zugewieseneEinwahlIds
    ) {
        public boolean ueberbelegt() {
            return belegtePlaetze > maximaleTeilnehmerzahl;
        }
    }

    private record GruppenSchluessel(Integer teilnehmerId, String agTitel) {
    }
}
