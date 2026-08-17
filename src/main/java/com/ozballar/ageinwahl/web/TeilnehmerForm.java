package com.ozballar.ageinwahl.web;

import com.ozballar.ageinwahl.domain.Teilnehmer;

public class TeilnehmerForm {

    private Integer id;
    private String vorname;
    private String name;
    private String klasse;
    private Teilnehmer.GtTeilnahme gtTeilnahme = Teilnehmer.GtTeilnahme.JA;

    public static TeilnehmerForm from(Teilnehmer teilnehmer) {
        TeilnehmerForm form = new TeilnehmerForm();
        form.setId(teilnehmer.id());
        form.setVorname(teilnehmer.vorname());
        form.setName(teilnehmer.name());
        form.setKlasse(teilnehmer.klasse());
        form.setGtTeilnahme(teilnehmer.gtTeilnahme());
        return form;
    }

    public Teilnehmer toTeilnehmer() {
        return new Teilnehmer(id, vorname, name, klasse, gtTeilnahme);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getVorname() {
        return vorname;
    }

    public void setVorname(String vorname) {
        this.vorname = vorname;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKlasse() {
        return klasse;
    }

    public void setKlasse(String klasse) {
        this.klasse = klasse;
    }

    public Teilnehmer.GtTeilnahme getGtTeilnahme() {
        return gtTeilnahme;
    }

    public void setGtTeilnahme(Teilnehmer.GtTeilnahme gtTeilnahme) {
        this.gtTeilnahme = gtTeilnahme;
    }
}
