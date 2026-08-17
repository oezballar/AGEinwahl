package com.ozballar.ageinwahl.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.EinwahlVormittagsAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlAGService;
import com.ozballar.ageinwahl.service.EinwahlEntdeckerangebotService;
import com.ozballar.ageinwahl.service.EinwahlVormittagsAGService;
import com.ozballar.ageinwahl.service.ExcelImportException;
import com.ozballar.ageinwahl.service.ExcelImportService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Controller
@RequestMapping("/teilnehmer")
public class TeilnehmerController {

    private final TeilnehmerService teilnehmerService;
    private final AgService agService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;
    private final EinwahlAGService einwahlAGService;
    private final ExcelImportService excelImportService;

    public TeilnehmerController(
            TeilnehmerService teilnehmerService,
            AgService agService,
            EinwahlEntdeckerangebotService einwahlEntdeckerangebotService,
            EinwahlVormittagsAGService einwahlVormittagsAGService,
            EinwahlAGService einwahlAGService,
            ExcelImportService excelImportService
    ) {
        this.teilnehmerService = teilnehmerService;
        this.agService = agService;
        this.einwahlEntdeckerangebotService = einwahlEntdeckerangebotService;
        this.einwahlVormittagsAGService = einwahlVormittagsAGService;
        this.einwahlAGService = einwahlAGService;
        this.excelImportService = excelImportService;
    }

    @GetMapping
    public String liste(@RequestParam(required = false) String agTitel, Model model) {
        List<EinwahlEntdeckerangebot> entdeckerEinwahlen = listeAus(einwahlEntdeckerangebotService.findeAlle());
        List<EinwahlVormittagsAG> vormittagsEinwahlen = listeAus(einwahlVormittagsAGService.findeAlle());
        List<EinwahlAG> nachmittagsEinwahlen = listeAus(einwahlAGService.findeAlle());
        Map<String, Ag> agNachTitel = listeAus(agService.findeAlle()).stream()
                .collect(Collectors.toMap(Ag::titel, Function.identity()));
        String aktiverAgFilter = leerZuNull(agTitel);

        List<TeilnehmerKlassenTabelle> tabellen = listeAus(teilnehmerService.findeAlle())
                .stream()
                .filter(teilnehmer -> aktiverAgFilter == null
                        || istTeilnehmerFuerAgBelegt(
                        teilnehmer,
                        aktiverAgFilter,
                        entdeckerEinwahlen,
                        vormittagsEinwahlen,
                        nachmittagsEinwahlen
                ))
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
                                        .thenComparing(eintrag -> eintrag.teilnehmer().id()))
                                .toList()
                ))
                .toList();

        model.addAttribute("tabellen", tabellen);
        model.addAttribute("agFilter", aktiverAgFilter);
        return "teilnehmer/list";
    }

    @GetMapping("/neu")
    public String neu(Model model) {
        TeilnehmerForm form = new TeilnehmerForm();
        form.setId(teilnehmerService.naechsteId());
        model.addAttribute("teilnehmerForm", form);
        model.addAttribute("titel", "Teilnehmer anlegen");
        model.addAttribute("neu", true);
        return "teilnehmer/form";
    }

    @GetMapping("/{id}/bearbeiten")
    public String bearbeiten(@PathVariable Integer id, Model model) {
        TeilnehmerForm form = teilnehmerService.findeNachId(id)
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
            boolean neu = teilnehmerForm.getId() == null;
            if (neu) {
                teilnehmerForm.setId(teilnehmerService.naechsteId());
            }
            model.addAttribute("teilnehmerForm", teilnehmerForm);
            model.addAttribute("titel", neu ? "Teilnehmer anlegen" : "Teilnehmer bearbeiten");
            model.addAttribute("neu", neu);
            model.addAttribute("fehler", ex.getMessage());
            return "teilnehmer/form";
        }
    }

    @PostMapping("/{id}/loeschen")
    public String loeschen(@PathVariable Integer id) {
        teilnehmerService.loescheNachId(id);
        return "redirect:/teilnehmer";
    }

    @GetMapping("/importieren")
    public String importieren() {
        return "teilnehmer/import";
    }

    @PostMapping("/importieren")
    public String importieren(@RequestParam("datei") MultipartFile datei, Model model, RedirectAttributes redirectAttributes) {
        try {
            int anzahl = excelImportService.importiereTeilnehmer(datei);
            redirectAttributes.addFlashAttribute("erfolg", anzahl + " Teilnehmer wurden importiert.");
            return "redirect:/teilnehmer";
        } catch (ExcelImportException | DataIntegrityViolationException ex) {
            model.addAttribute("fehler", ex.getMessage());
            return "teilnehmer/import";
        }
    }

    @GetMapping("/import-vorlage.xlsx")
    public ResponseEntity<byte[]> importVorlage() {
        return excelDownload("teilnehmer-import-vorlage.xlsx", excelImportService.teilnehmerVorlage());
    }

    private TeilnehmerEintrag teilnehmerEintrag(
            Teilnehmer teilnehmer,
            List<EinwahlEntdeckerangebot> entdeckerEinwahlen,
            List<EinwahlVormittagsAG> vormittagsEinwahlen,
            List<EinwahlAG> nachmittagsEinwahlen,
            Map<String, Ag> agNachTitel
    ) {
        int offeneFelder = (int) entdeckerEinwahlen.stream()
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()))
                .filter(einwahl -> einwahl.auswahl() == null)
                .count()
                + (int) vormittagsEinwahlen.stream()
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()))
                .filter(einwahl -> einwahl.auswahl() == null)
                .count()
                + (int) nachmittagsEinwahlen.stream()
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()))
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
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()))
                .filter(einwahl -> agNachTitel.containsKey(einwahl.agTitel()))
                .collect(Collectors.groupingBy(einwahl -> agNachTitel.get(einwahl.agTitel()).wochentag()))
                .values()
                .stream()
                .filter(einwahlenAmTag -> einwahlenAmTag.stream().noneMatch(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen())))
                .count();
    }

    private boolean istTeilnehmerFuerAgBelegt(
            Teilnehmer teilnehmer,
            String agTitel,
            List<EinwahlEntdeckerangebot> entdeckerEinwahlen,
            List<EinwahlVormittagsAG> vormittagsEinwahlen,
            List<EinwahlAG> nachmittagsEinwahlen
    ) {
        return entdeckerEinwahlen.stream()
                .anyMatch(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id())
                        && Objects.equals(einwahl.agTitel(), agTitel)
                        && Boolean.TRUE.equals(einwahl.zugewiesen()))
                || vormittagsEinwahlen.stream()
                .anyMatch(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id())
                        && Objects.equals(einwahl.agTitel(), agTitel)
                        && Boolean.TRUE.equals(einwahl.zugewiesen()))
                || nachmittagsEinwahlen.stream()
                .anyMatch(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id())
                        && Objects.equals(einwahl.agTitel(), agTitel)
                        && Boolean.TRUE.equals(einwahl.zugewiesen()));
    }

    private String leerZuNull(String wert) {
        if (wert == null || wert.isBlank()) {
            return null;
        }

        return wert.strip();
    }

    private <T> List<T> listeAus(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    private ResponseEntity<byte[]> excelDownload(String dateiname, byte[] inhalt) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(dateiname).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(inhalt);
    }

    public record TeilnehmerKlassenTabelle(String klasse, List<TeilnehmerEintrag> teilnehmer) {
    }

    public record TeilnehmerEintrag(Teilnehmer teilnehmer, int offeneEinwahlen) {
        public boolean einwahlVollstaendig() {
            return offeneEinwahlen == 0;
        }
    }
}
