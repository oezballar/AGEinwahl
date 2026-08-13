package com.ozballar.ageinwahl.web;

import com.ozballar.ageinwahl.domain.Teilnehmer;

public class TeilnehmerForm {

    private Integer nr;
    private String vorname;
    private String name;
    private String klasse;

    public static TeilnehmerForm from(Teilnehmer teilnehmer) {
        TeilnehmerForm form = new TeilnehmerForm();
        form.setNr(teilnehmer.nr());
        form.setVorname(teilnehmer.vorname());
        form.setName(teilnehmer.name());
        form.setKlasse(teilnehmer.klasse());
        return form;
    }

    public Teilnehmer toTeilnehmer() {
        return new Teilnehmer(nr, vorname, name, klasse);
    }

    public Integer getNr() {
        return nr;
    }

    public void setNr(Integer nr) {
        this.nr = nr;
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
}
