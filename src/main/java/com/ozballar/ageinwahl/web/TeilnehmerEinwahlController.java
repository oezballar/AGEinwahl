package com.ozballar.ageinwahl.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
@RequestMapping("/teilnehmer/{id}/einwahl")
public class TeilnehmerEinwahlController {

    private static final String ENTDECKER_PREFIX = "entdecker_";
    private static final String VORMITTAG_PREFIX = "vormittag_";
    private static final String NACHMITTAG_PREFIX = "nachmittag_";
    private static final String ZUWEISUNG_PREFIX = "zugewiesen_";
    private static final String ENTDECKER_ZUWEISUNG_PREFIX = "zugewiesen_entdecker_";
    private static final String VORMITTAG_ZUWEISUNG_PREFIX = "zugewiesen_vormittag_";

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
    public String anzeigen(@PathVariable Integer id, Model model) {
        modelFuellen(id, model, null);
        return "teilnehmer/einwahl";
    }

    @PostMapping
    public String speichern(@PathVariable Integer id, @RequestParam Map<String, String> parameter, Model model) {
        try {
            Teilnehmer teilnehmer = teilnehmerService.findeNachId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Teilnehmer wurde nicht gefunden."));
            pruefeMittagsveranstaltungen(teilnehmer, parameter);
            pruefeNachmittagsZuweisungen(id, parameter);
            parameter.forEach(this::speichereAuswahl);
            speichereJaNeinZuweisungen(parameter);
            speichereNachmittagsZuweisungen(id, parameter);
            return "redirect:/teilnehmer";
        } catch (IllegalArgumentException ex) {
            modelFuellen(id, model, ex.getMessage());
            return "teilnehmer/einwahl";
        }
    }

    private void pruefeNachmittagsZuweisungen(Integer teilnehmerId, Map<String, String> parameter) {
        Teilnehmer teilnehmer = teilnehmerService.findeNachId(teilnehmerId)
                .orElseThrow(() -> new IllegalArgumentException("Teilnehmer wurde nicht gefunden."));
        if (!teilnehmer.nimmtAnMittagsveranstaltungenTeil()) {
            return;
        }
        Map<String, Ag> agNachTitel = agNachTitel();
        Map<Ag.Wochentag, List<EinwahlAG>> nachmittagsEinwahlenNachTag = StreamSupport.stream(einwahlAGService.findeAlle().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmerId))
                .collect(Collectors.groupingBy(einwahl -> agNachTitel.get(einwahl.agTitel()).wochentag()));

        for (Map.Entry<Ag.Wochentag, List<EinwahlAG>> entry : nachmittagsEinwahlenNachTag.entrySet()) {
            String wert = parameter.get(zuweisungsFeldname(entry.getKey(), Ag.Zeit.NACHMITTAG));
            Integer zugewieseneEinwahlId = idOderNullAus(wert);
            boolean einwahlIstInGruppe = entry.getValue().stream()
                    .anyMatch(einwahl -> Objects.equals(einwahl.id(), zugewieseneEinwahlId));

            if (!einwahlIstInGruppe) {
                throw new IllegalArgumentException("Bei den Nachmittags-AGs muss fuer " + entry.getKey().name() + " genau eine AG zugewiesen sein.");
            }
        }
    }

    private void pruefeMittagsveranstaltungen(Teilnehmer teilnehmer, Map<String, String> parameter) {
        if (teilnehmer.nimmtAnMittagsveranstaltungenTeil()) {
            return;
        }

        boolean enthaeltMittagsfeld = parameter.keySet().stream()
                .anyMatch(name -> name.startsWith(NACHMITTAG_PREFIX)
                        || name.startsWith(ZUWEISUNG_PREFIX) && !name.startsWith(ENTDECKER_ZUWEISUNG_PREFIX)
                        && !name.startsWith(VORMITTAG_ZUWEISUNG_PREFIX));
        if (enthaeltMittagsfeld) {
            throw new IllegalArgumentException("Teilnehmer ohne GT-Teilnahme dürfen nicht an Mittagsveranstaltungen teilnehmen.");
        }
    }

    private void speichereNachmittagsZuweisungen(Integer teilnehmerId, Map<String, String> parameter) {
        parameter.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(ZUWEISUNG_PREFIX))
                .filter(entry -> !entry.getKey().startsWith(ENTDECKER_ZUWEISUNG_PREFIX))
                .filter(entry -> !entry.getKey().startsWith(VORMITTAG_ZUWEISUNG_PREFIX))
                .forEach(entry -> einwahlAGService.speichereZuweisung(
                        teilnehmerId,
                        wochentagAusZuweisungsFeld(entry.getKey()),
                        idOderNullAus(entry.getValue())
                ));
    }

    private void speichereJaNeinZuweisungen(Map<String, String> parameter) {
        parameter.keySet()
                .stream()
                .filter(name -> name.startsWith(ENTDECKER_PREFIX))
                .map(name -> idAus(name, ENTDECKER_PREFIX))
                .distinct()
                .forEach(id -> einwahlEntdeckerangebotService.speichereZuweisung(
                        id,
                        false
                ));
        parameter.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(ENTDECKER_ZUWEISUNG_PREFIX))
                .map(entry -> idOderNullAus(entry.getValue()))
                .filter(Objects::nonNull)
                .forEach(id -> einwahlEntdeckerangebotService.speichereZuweisung(id, true));

        parameter.keySet()
                .stream()
                .filter(name -> name.startsWith(VORMITTAG_PREFIX))
                .map(name -> idAus(name, VORMITTAG_PREFIX))
                .distinct()
                .forEach(id -> einwahlVormittagsAGService.speichereZuweisung(
                        id,
                        false
                ));
        parameter.entrySet()
                .stream()
                .filter(entry -> entry.getKey().startsWith(VORMITTAG_ZUWEISUNG_PREFIX))
                .map(entry -> idOderNullAus(entry.getValue()))
                .filter(Objects::nonNull)
                .forEach(id -> einwahlVormittagsAGService.speichereZuweisung(id, true));
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

    private Integer idOderNullAus(String wert) {
        if (wert == null || wert.isBlank()) {
            return null;
        }

        return Integer.valueOf(wert);
    }

    private Integer idAus(String name, String prefix) {
        return Integer.valueOf(name.substring(prefix.length()));
    }

    private Ag.Wochentag wochentagAusZuweisungsFeld(String feldname) {
        String ohnePrefix = feldname.substring(ZUWEISUNG_PREFIX.length());
        String wochentag = ohnePrefix.substring(0, ohnePrefix.indexOf('_'));
        return Ag.Wochentag.valueOf(wochentag);
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

    private void modelFuellen(Integer id, Model model, String fehler) {
        Teilnehmer teilnehmer = teilnehmerService.findeNachId(id)
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
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()))
                .map(einwahl -> optionAus(einwahl, agNachTitel))
                .toList());
        optionen.addAll(StreamSupport.stream(einwahlVormittagsAGService.findeAlle().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()))
                .map(einwahl -> optionAus(einwahl, agNachTitel))
                .toList());
        optionen.addAll(StreamSupport.stream(einwahlAGService.findeAlle().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()))
                .map(einwahl -> optionAus(einwahl, agNachTitel))
                .toList());

        if (!teilnehmer.nimmtAnMittagsveranstaltungenTeil()) {
            optionen = optionen.stream()
                    .filter(option -> option.ag() == null || option.ag().zeit() != Ag.Zeit.NACHMITTAG)
                    .toList();
        }

        return tageAus(optionen);
    }

    private EinwahlOption optionAus(EinwahlEntdeckerangebot einwahl, Map<String, Ag> agNachTitel) {
        Ag ag = agNachTitel.get(einwahl.agTitel());
        return new EinwahlOption(
                ENTDECKER_PREFIX,
                einwahl.id(),
                ag,
                einwahl.auswahl() == null ? "" : einwahl.auswahl().name(),
                Boolean.TRUE.equals(einwahl.zugewiesen())
        );
    }

    private EinwahlOption optionAus(EinwahlVormittagsAG einwahl, Map<String, Ag> agNachTitel) {
        Ag ag = agNachTitel.get(einwahl.agTitel());
        return new EinwahlOption(
                VORMITTAG_PREFIX,
                einwahl.id(),
                ag,
                einwahl.auswahl() == null ? "" : einwahl.auswahl().name(),
                Boolean.TRUE.equals(einwahl.zugewiesen())
        );
    }

    private EinwahlOption optionAus(EinwahlAG einwahl, Map<String, Ag> agNachTitel) {
        Ag ag = agNachTitel.get(einwahl.agTitel());
        return new EinwahlOption(
                NACHMITTAG_PREFIX,
                einwahl.id(),
                ag,
                einwahl.auswahl() == null ? "" : einwahl.auswahl().toString(),
                Boolean.TRUE.equals(einwahl.zugewiesen())
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
                .map(entry -> {
                    List<EinwahlOption> sortierteOptionen = entry.getValue().stream()
                            .sorted(Comparator.comparing(option -> option.ag().titel()))
                            .toList();
                    return new EinwahlZeitgruppe(
                            entry.getKey().name(),
                            sortierteOptionen,
                            istZuweisungOffen(sortierteOptionen),
                            zuweisungsFeldname(sortierteOptionen),
                            zugewieseneEinwahlId(sortierteOptionen)
                    );
                })
                .toList();
    }

    private boolean istZuweisungOffen(List<EinwahlOption> optionen) {
        if (optionen.stream().anyMatch(EinwahlOption::vormittagsAuswahl)) {
            return false;
        }

        return optionen.stream().anyMatch(EinwahlOption::zuweisbareAuswahl)
                && optionen.stream().noneMatch(EinwahlOption::zugewiesen);
    }

    private String zuweisungsFeldname(List<EinwahlOption> optionen) {
        if (optionen.isEmpty() || optionen.stream().noneMatch(EinwahlOption::zuweisbareAuswahl)) {
            return "";
        }

        Ag ag = optionen.get(0).ag();
        EinwahlOption ersteOption = optionen.get(0);

        if (ersteOption.entdeckerAuswahl()) {
            return ENTDECKER_ZUWEISUNG_PREFIX + ag.wochentag().name() + "_" + ag.zeit().name();
        }

        if (ersteOption.vormittagsAuswahl()) {
            return VORMITTAG_ZUWEISUNG_PREFIX + ag.wochentag().name() + "_" + ag.zeit().name();
        }

        return zuweisungsFeldname(ag.wochentag(), ag.zeit());
    }

    private String zuweisungsFeldname(Ag.Wochentag wochentag, Ag.Zeit zeit) {
        return ZUWEISUNG_PREFIX + wochentag.name() + "_" + zeit.name();
    }

    private Integer zugewieseneEinwahlId(List<EinwahlOption> optionen) {
        return optionen.stream()
                .filter(EinwahlOption::zugewiesen)
                .map(EinwahlOption::id)
                .findFirst()
                .orElse(null);
    }

    public record EinwahlTag(String wochentag, List<EinwahlZeitgruppe> zeitgruppen) {
    }

    public record EinwahlZeitgruppe(
            String zeit,
            List<EinwahlOption> optionen,
            boolean offen,
            String zuweisungsFeldname,
            Integer zugewieseneEinwahlId
    ) {
        public boolean zuweisungsAuswahl() {
            return optionen.stream().anyMatch(EinwahlOption::zuweisbareAuswahl);
        }

        public boolean jaNeinAuswahl() {
            return optionen.stream().anyMatch(EinwahlOption::jaNeinAuswahl);
        }

        public boolean vormittagsAuswahl() {
            return optionen.stream().anyMatch(EinwahlOption::vormittagsAuswahl);
        }

        public List<Integer> prioritaeten() {
            return IntStream.rangeClosed(1, optionen.size()).boxed().toList();
        }
    }

    public record EinwahlOption(
            String prefix,
            Integer id,
            Ag ag,
            String auswahl,
            boolean zugewiesen
    ) {
        public String feldname() {
            return prefix + id;
        }

        public boolean jaNeinAuswahl() {
            return ENTDECKER_PREFIX.equals(prefix) || VORMITTAG_PREFIX.equals(prefix);
        }

        public boolean entdeckerAuswahl() {
            return ENTDECKER_PREFIX.equals(prefix);
        }

        public boolean vormittagsAuswahl() {
            return VORMITTAG_PREFIX.equals(prefix);
        }

        public boolean prioritaetsAuswahl() {
            return NACHMITTAG_PREFIX.equals(prefix);
        }

        public boolean zuweisbareAuswahl() {
            return jaNeinAuswahl() || prioritaetsAuswahl();
        }

        public String zeitText() {
            return ag.zeit().name();
        }
    }
}
