package com.ozballar.ageinwahl.web;

import java.util.ArrayList;
import java.util.List;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.ErlaubterJahrgang;

public class AgForm {

    private Integer id;
    private Ag.Wochentag wochentag;
    private Ag.Zeit zeit;
    private Ag.Kategorie kategorie;
    private String titel;
    private String beschreibung;
    private String verantwortlicher;
    private String ort;
    private Integer maximaleTeilnehmerzahl;
    private List<Integer> erlaubteJahrgaenge = new ArrayList<>();

    public static AgForm from(Ag ag) {
        AgForm form = new AgForm();
        form.setId(ag.id());
        form.setWochentag(ag.wochentag());
        form.setZeit(ag.zeit());
        form.setKategorie(ag.kategorie());
        form.setTitel(ag.titel());
        form.setBeschreibung(ag.beschreibung());
        form.setVerantwortlicher(ag.verantwortlicher());
        form.setOrt(ag.ort());
        form.setMaximaleTeilnehmerzahl(ag.maximaleTeilnehmerzahl());
        form.setErlaubteJahrgaenge(ag.erlaubteJahrgaenge().stream()
                .map(ErlaubterJahrgang::jahrgang)
                .toList());
        return form;
    }

    public Ag toAg() {
        return new Ag(
                id,
                wochentag,
                zeit,
                kategorie,
                titel,
                beschreibung,
                verantwortlicher,
                ort,
                maximaleTeilnehmerzahl,
                erlaubteJahrgaenge.stream()
                        .map(ErlaubterJahrgang::new)
                        .toList()
        );
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Ag.Wochentag getWochentag() {
        return wochentag;
    }

    public void setWochentag(Ag.Wochentag wochentag) {
        this.wochentag = wochentag;
    }

    public Ag.Zeit getZeit() {
        return zeit;
    }

    public void setZeit(Ag.Zeit zeit) {
        this.zeit = zeit;
    }

    public Ag.Kategorie getKategorie() {
        return kategorie;
    }

    public void setKategorie(Ag.Kategorie kategorie) {
        this.kategorie = kategorie;
    }

    public String getTitel() {
        return titel;
    }

    public void setTitel(String titel) {
        this.titel = titel;
    }

    public String getBeschreibung() {
        return beschreibung;
    }

    public void setBeschreibung(String beschreibung) {
        this.beschreibung = beschreibung;
    }

    public String getVerantwortlicher() {
        return verantwortlicher;
    }

    public void setVerantwortlicher(String verantwortlicher) {
        this.verantwortlicher = verantwortlicher;
    }

    public String getOrt() {
        return ort;
    }

    public void setOrt(String ort) {
        this.ort = ort;
    }

    public Integer getMaximaleTeilnehmerzahl() {
        return maximaleTeilnehmerzahl;
    }

    public void setMaximaleTeilnehmerzahl(Integer maximaleTeilnehmerzahl) {
        this.maximaleTeilnehmerzahl = maximaleTeilnehmerzahl;
    }

    public List<Integer> getErlaubteJahrgaenge() {
        return erlaubteJahrgaenge;
    }

    public void setErlaubteJahrgaenge(List<Integer> erlaubteJahrgaenge) {
        this.erlaubteJahrgaenge = erlaubteJahrgaenge == null ? new ArrayList<>() : erlaubteJahrgaenge;
    }
}
