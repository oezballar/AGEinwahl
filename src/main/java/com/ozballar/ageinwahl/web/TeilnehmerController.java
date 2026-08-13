package com.ozballar.ageinwahl.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
@RequestMapping("/teilnehmer")
public class TeilnehmerController {

    private final TeilnehmerService teilnehmerService;
    private final AgService agService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;
    private final EinwahlAGService einwahlAGService;

    public TeilnehmerController(
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
    public String liste(Model model) {
        List<EinwahlEntdeckerangebot> entdeckerEinwahlen = listeAus(einwahlEntdeckerangebotService.findeAlle());
        List<EinwahlVormittagsAG> vormittagsEinwahlen = listeAus(einwahlVormittagsAGService.findeAlle());
        List<EinwahlAG> nachmittagsEinwahlen = listeAus(einwahlAGService.findeAlle());
        Map<String, Ag> agNachTitel = listeAus(agService.findeAlle()).stream()
                .collect(Collectors.toMap(Ag::titel, Function.identity()));

        List<TeilnehmerKlassenTabelle> tabellen = listeAus(teilnehmerService.findeAlle())
                .stream()
                .map(teilnehmer -> teilnehmerEintrag(teilnehmer, entdeckerEinwahlen, vormittagsEinwahlen, nachmittagsEinwahlen, agNachTitel))
                .collect(Collectors.groupingBy(
                        eintrag -> eintrag.teilnehmer().klasse(),
                        java.util.TreeMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> new TeilnehmerKlassenTabelle(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator
                                        .comparing((TeilnehmerEintrag eintrag) -> eintrag.teilnehmer().name())
                                        .thenComparing(eintrag -> eintrag.teilnehmer().vorname())
                                        .thenComparing(eintrag -> eintrag.teilnehmer().nr()))
                                .toList()
                ))
                .toList();

        model.addAttribute("tabellen", tabellen);
        return "teilnehmer/list";
    }

    @GetMapping("/neu")
    public String neu(Model model) {
        TeilnehmerForm form = new TeilnehmerForm();
        form.setNr(teilnehmerService.naechsteNr());
        model.addAttribute("teilnehmerForm", form);
        model.addAttribute("titel", "Teilnehmer anlegen");
        model.addAttribute("neu", true);
        return "teilnehmer/form";
    }

    @GetMapping("/{nr}/bearbeiten")
    public String bearbeiten(@PathVariable Integer nr, Model model) {
        TeilnehmerForm form = teilnehmerService.findeNachNr(nr)
                .map(TeilnehmerForm::from)
                .orElseThrow(() -> new IllegalArgumentException("Teilnehmer wurde nicht gefunden."));
        model.addAttribute("teilnehmerForm", form);
        model.addAttribute("titel", "Teilnehmer bearbeiten");
        model.addAttribute("neu", false);
        return "teilnehmer/form";
    }

    @PostMapping
    public String speichern(@ModelAttribute TeilnehmerForm teilnehmerForm, Model model) {
        try {
            teilnehmerService.speichern(teilnehmerForm.toTeilnehmer());
            return "redirect:/teilnehmer";
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            boolean neu = teilnehmerForm.getNr() == null;
            if (neu) {
                teilnehmerForm.setNr(teilnehmerService.naechsteNr());
            }
            model.addAttribute("teilnehmerForm", teilnehmerForm);
            model.addAttribute("titel", neu ? "Teilnehmer anlegen" : "Teilnehmer bearbeiten");
            model.addAttribute("neu", neu);
            model.addAttribute("fehler", ex.getMessage());
            return "teilnehmer/form";
        }
    }

    @PostMapping("/{nr}/loeschen")
    public String loeschen(@PathVariable Integer nr) {
        teilnehmerService.loescheNachNr(nr);
        return "redirect:/teilnehmer";
    }

    private TeilnehmerEintrag teilnehmerEintrag(
            Teilnehmer teilnehmer,
            List<EinwahlEntdeckerangebot> entdeckerEinwahlen,
            List<EinwahlVormittagsAG> vormittagsEinwahlen,
            List<EinwahlAG> nachmittagsEinwahlen,
            Map<String, Ag> agNachTitel
    ) {
        int offeneFelder = (int) entdeckerEinwahlen.stream()
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmer.nr()))
                .filter(einwahl -> einwahl.auswahl() == null)
                .count()
                + (int) vormittagsEinwahlen.stream()
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmer.nr()))
                .filter(einwahl -> einwahl.auswahl() == null)
                .count()
                + (int) nachmittagsEinwahlen.stream()
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmer.nr()))
                .filter(einwahl -> einwahl.auswahl() == null)
                .count();

        int offeneNachmittage = offeneNachmittagsZuweisungen(teilnehmer, nachmittagsEinwahlen, agNachTitel);
        return new TeilnehmerEintrag(teilnehmer, offeneFelder + offeneNachmittage);
    }

    private int offeneNachmittagsZuweisungen(
            Teilnehmer teilnehmer,
            List<EinwahlAG> nachmittagsEinwahlen,
            Map<String, Ag> agNachTitel
    ) {
        return (int) nachmittagsEinwahlen.stream()
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmer.nr()))
                .filter(einwahl -> agNachTitel.containsKey(einwahl.agTitel()))
                .collect(Collectors.groupingBy(einwahl -> agNachTitel.get(einwahl.agTitel()).wochentag()))
                .values()
                .stream()
                .filter(einwahlenAmTag -> einwahlenAmTag.stream().noneMatch(einwahl -> Integer.valueOf(1).equals(einwahl.auswahl())))
                .count();
    }

    private <T> List<T> listeAus(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    public record TeilnehmerKlassenTabelle(String klasse, List<TeilnehmerEintrag> teilnehmer) {
    }

    public record TeilnehmerEintrag(Teilnehmer teilnehmer, int offeneEinwahlen) {
        public boolean einwahlVollstaendig() {
            return offeneEinwahlen == 0;
        }
    }
}
