package com.ozballar.ageinwahl.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.EinwahlVormittagsAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlAGService;
import com.ozballar.ageinwahl.service.EinwahlEntdeckerangebotService;
import com.ozballar.ageinwahl.service.EinwahlVormittagsAGService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Controller
@RequestMapping("/teilnehmer/{nr}/einwahl")
public class TeilnehmerEinwahlController {

    private static final String ENTDECKER_PREFIX = "entdecker_";
    private static final String VORMITTAG_PREFIX = "vormittag_";
    private static final String NACHMITTAG_PREFIX = "nachmittag_";

    private final TeilnehmerService teilnehmerService;
    private final AgService agService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;
    private final EinwahlAGService einwahlAGService;

    public TeilnehmerEinwahlController(
            TeilnehmerService teilnehmerService,
            AgService agService,
            EinwahlEntdeckerangebotService einwahlEntdeckerangebotService,
            EinwahlVormittagsAGService einwahlVormittagsAGService,
            EinwahlAGService einwahlAGService
    ) {
        this.teilnehmerService = teilnehmerService;
        this.agService = agService;
        this.einwahlEntdeckerangebotService = einwahlEntdeckerangebotService;
        this.einwahlVormittagsAGService = einwahlVormittagsAGService;
        this.einwahlAGService = einwahlAGService;
    }

    @GetMapping
    public String anzeigen(@PathVariable Integer nr, Model model) {
        modelFuellen(nr, model, null);
        return "teilnehmer/einwahl";
    }

    @PostMapping
    public String speichern(@PathVariable Integer nr, @RequestParam Map<String, String> parameter, Model model) {
        try {
            pruefeNachmittagsZuweisungen(nr, parameter);
            parameter.forEach(this::speichereAuswahl);
            return "redirect:/teilnehmer";
        } catch (IllegalArgumentException ex) {
            modelFuellen(nr, model, ex.getMessage());
            return "teilnehmer/einwahl";
        }
    }

    private void pruefeNachmittagsZuweisungen(Integer teilnehmerNr, Map<String, String> parameter) {
        Map<String, Ag> agNachTitel = agNachTitel();
        Map<Ag.Wochentag, List<EinwahlAG>> nachmittagsEinwahlenNachTag = StreamSupport.stream(einwahlAGService.findeAlle().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmerNr))
                .collect(Collectors.groupingBy(einwahl -> agNachTitel.get(einwahl.agTitel()).wochentag()));

        for (Map.Entry<Ag.Wochentag, List<EinwahlAG>> entry : nachmittagsEinwahlenNachTag.entrySet()) {
            long zuweisungen = entry.getValue()
                    .stream()
                    .filter(einwahl -> Integer.valueOf(1).equals(nachmittagsAuswahlAus(parameter, einwahl)))
                    .count();

            if (zuweisungen != 1) {
                throw new IllegalArgumentException("Bei den Nachmittags-AGs muss fuer " + entry.getKey().name() + " genau eine AG mit Auswahl 1 eingetragen sein.");
            }
        }
    }

    private Integer nachmittagsAuswahlAus(Map<String, String> parameter, EinwahlAG einwahl) {
        String wert = parameter.get(NACHMITTAG_PREFIX + einwahl.id());
        return zahlAus(wert);
    }

    private void speichereAuswahl(String name, String wert) {
        if (name.startsWith(ENTDECKER_PREFIX)) {
            einwahlEntdeckerangebotService.speichereAuswahl(
                    idAus(name, ENTDECKER_PREFIX),
                    entdeckerAuswahlAus(wert)
            );
        } else if (name.startsWith(VORMITTAG_PREFIX)) {
            einwahlVormittagsAGService.speichereAuswahl(idAus(name, VORMITTAG_PREFIX), vormittagsAuswahlAus(wert));
        } else if (name.startsWith(NACHMITTAG_PREFIX)) {
            einwahlAGService.speichereAuswahl(idAus(name, NACHMITTAG_PREFIX), zahlAus(wert));
        }
    }

    private Integer idAus(String name, String prefix) {
        return Integer.valueOf(name.substring(prefix.length()));
    }

    private EinwahlEntdeckerangebot.Auswahl entdeckerAuswahlAus(String wert) {
        if (wert == null || wert.isBlank()) {
            return null;
        }

        return EinwahlEntdeckerangebot.Auswahl.valueOf(wert);
    }

    private EinwahlVormittagsAG.Auswahl vormittagsAuswahlAus(String wert) {
        if (wert == null || wert.isBlank()) {
            return null;
        }

        return EinwahlVormittagsAG.Auswahl.valueOf(wert);
    }

    private Integer zahlAus(String wert) {
        if (wert == null || wert.isBlank()) {
            return null;
        }

        return Integer.valueOf(wert);
    }

    private void modelFuellen(Integer nr, Model model, String fehler) {
        Teilnehmer teilnehmer = teilnehmerService.findeNachNr(nr)
                .orElseThrow(() -> new IllegalArgumentException("Teilnehmer wurde nicht gefunden."));
        Map<String, Ag> agNachTitel = agNachTitel();

        model.addAttribute("teilnehmer", teilnehmer);
        model.addAttribute("auswahlWerte", EinwahlEntdeckerangebot.Auswahl.values());
        model.addAttribute("einwahlTage", einwahlTage(teilnehmer, agNachTitel));
        model.addAttribute("fehler", fehler);
    }

    private Map<String, Ag> agNachTitel() {
        return StreamSupport.stream(agService.findeAlle().spliterator(), false)
                .collect(Collectors.toMap(Ag::titel, Function.identity()));
    }

    private List<EinwahlTag> einwahlTage(Teilnehmer teilnehmer, Map<String, Ag> agNachTitel) {
        List<EinwahlOption> optionen = new ArrayList<>();
        optionen.addAll(StreamSupport.stream(einwahlEntdeckerangebotService.findeAlle().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmer.nr()))
                .map(einwahl -> optionAus(einwahl, agNachTitel))
                .toList());
        optionen.addAll(StreamSupport.stream(einwahlVormittagsAGService.findeAlle().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmer.nr()))
                .map(einwahl -> optionAus(einwahl, agNachTitel))
                .toList());
        optionen.addAll(StreamSupport.stream(einwahlAGService.findeAlle().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmer.nr()))
                .map(einwahl -> optionAus(einwahl, agNachTitel))
                .toList());

        return tageAus(optionen);
    }

    private EinwahlOption optionAus(EinwahlEntdeckerangebot einwahl, Map<String, Ag> agNachTitel) {
        Ag ag = agNachTitel.get(einwahl.agTitel());
        return new EinwahlOption(
                ENTDECKER_PREFIX,
                einwahl.id(),
                ag,
                einwahl.auswahl() == null ? "" : einwahl.auswahl().name()
        );
    }

    private EinwahlOption optionAus(EinwahlVormittagsAG einwahl, Map<String, Ag> agNachTitel) {
        Ag ag = agNachTitel.get(einwahl.agTitel());
        return new EinwahlOption(
                VORMITTAG_PREFIX,
                einwahl.id(),
                ag,
                einwahl.auswahl() == null ? "" : einwahl.auswahl().name()
        );
    }

    private EinwahlOption optionAus(EinwahlAG einwahl, Map<String, Ag> agNachTitel) {
        Ag ag = agNachTitel.get(einwahl.agTitel());
        return new EinwahlOption(
                NACHMITTAG_PREFIX,
                einwahl.id(),
                ag,
                einwahl.auswahl() == null ? "" : einwahl.auswahl().toString()
        );
    }

    private List<EinwahlTag> tageAus(List<EinwahlOption> optionen) {
        return optionen.stream()
                .filter(option -> option.ag() != null)
                .collect(Collectors.groupingBy(
                        option -> option.ag().wochentag(),
                        () -> new java.util.TreeMap<>(Comparator.comparing(Enum::ordinal)),
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> new EinwahlTag(
                        entry.getKey().name(),
                        zeitgruppenAus(entry.getValue())
                ))
                .toList();
    }

    private List<EinwahlZeitgruppe> zeitgruppenAus(List<EinwahlOption> optionen) {
        return optionen.stream()
                .collect(Collectors.groupingBy(
                        option -> option.ag().zeit(),
                        () -> new java.util.TreeMap<>(Comparator.comparing(Enum::ordinal)),
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> new EinwahlZeitgruppe(
                        entry.getKey().name(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(option -> option.ag().titel()))
                                .toList()
                ))
                .toList();
    }

    public record EinwahlTag(String wochentag, List<EinwahlZeitgruppe> zeitgruppen) {
    }

    public record EinwahlZeitgruppe(String zeit, List<EinwahlOption> optionen) {
    }

    public record EinwahlOption(
            String prefix,
            Integer id,
            Ag ag,
            String auswahl
    ) {
        public String feldname() {
            return prefix + id;
        }

        public boolean jaNeinAuswahl() {
            return ENTDECKER_PREFIX.equals(prefix) || VORMITTAG_PREFIX.equals(prefix);
        }

        public boolean prioritaetsAuswahl() {
            return NACHMITTAG_PREFIX.equals(prefix);
        }

        public String zeitText() {
            return ag.zeit().name();
        }
    }
}
