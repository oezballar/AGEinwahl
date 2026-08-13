package com.ozballar.ageinwahl.web;

import java.util.Comparator;
import java.util.List;
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
import com.ozballar.ageinwahl.service.AgService;

@Controller
@RequestMapping("/ags")
public class AgController {

    private final AgService agService;

    public AgController(AgService agService) {
        this.agService = agService;
    }

    @GetMapping
    public String liste(Model model) {
        List<Ag> ags = StreamSupport.stream(agService.findeAlle().spliterator(), false).toList();
        List<AgTagesTabelle> tabellen = java.util.Arrays.stream(Ag.Wochentag.values())
                .map(wochentag -> new AgTagesTabelle(
                        wochentag.name(),
                        ags.stream()
                                .filter(ag -> ag.wochentag() == wochentag)
                                .sorted(Comparator.comparing(Ag::zeit).thenComparing(Ag::titel))
                                .toList()
                ))
                .filter(tabelle -> !tabelle.ags().isEmpty())
                .toList();

        model.addAttribute("tabellen", tabellen);
        return "ag/list";
    }

    @GetMapping("/neu")
    public String neu(Model model) {
        model.addAttribute("agForm", new AgForm());
        formularAttribute(model, "AG anlegen");
        return "ag/form";
    }

    @GetMapping("/{id}/bearbeiten")
    public String bearbeiten(@PathVariable Integer id, Model model) {
        AgForm form = agService.findeNachId(id)
                .map(AgForm::from)
                .orElseThrow(() -> new IllegalArgumentException("AG wurde nicht gefunden."));
        model.addAttribute("agForm", form);
        formularAttribute(model, "AG bearbeiten");
        return "ag/form";
    }

    @PostMapping
    public String speichern(@ModelAttribute AgForm agForm, Model model) {
        try {
            agService.speichern(agForm.toAg());
            return "redirect:/ags";
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            model.addAttribute("agForm", agForm);
            model.addAttribute("fehler", ex.getMessage());
            formularAttribute(model, "AG bearbeiten");
            return "ag/form";
        }
    }

    @PostMapping("/{id}/loeschen")
    public String loeschen(@PathVariable Integer id) {
        agService.loescheNachId(id);
        return "redirect:/ags";
    }

    private void formularAttribute(Model model, String titel) {
        model.addAttribute("titel", titel);
        model.addAttribute("wochentage", Ag.Wochentag.values());
        model.addAttribute("zeiten", Ag.Zeit.values());
        model.addAttribute("kategorien", Ag.Kategorie.values());
        model.addAttribute("jahrgaenge", List.of(1, 2, 3, 4));
    }

    public record AgTagesTabelle(String wochentag, List<Ag> ags) {
    }
}
