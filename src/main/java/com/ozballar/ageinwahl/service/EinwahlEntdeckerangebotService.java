package com.ozballar.ageinwahl.service;

import java.util.Objects;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.stream.StreamSupport;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.AgRepository;
import com.ozballar.ageinwahl.repository.EinwahlEntdeckerangebotRepository;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

@Service
public class EinwahlEntdeckerangebotService {

    private final EinwahlEntdeckerangebotRepository einwahlEntdeckerangebotRepository;
    private final TeilnehmerRepository teilnehmerRepository;
    private final AgRepository agRepository;

    public EinwahlEntdeckerangebotService(
            EinwahlEntdeckerangebotRepository einwahlEntdeckerangebotRepository,
            TeilnehmerRepository teilnehmerRepository,
            AgRepository agRepository
    ) {
        this.einwahlEntdeckerangebotRepository = einwahlEntdeckerangebotRepository;
        this.teilnehmerRepository = teilnehmerRepository;
        this.agRepository = agRepository;
    }

    public void erstelleEintraegeFuerTeilnehmer(Teilnehmer teilnehmer) {
        agRepository.findAll().forEach(ag -> erstelleEintragWennErlaubtUndNochNichtVorhanden(teilnehmer, ag));
    }

    public void erstelleEintraegeFuerAg(Ag ag) {
        teilnehmerRepository.findAll().forEach(teilnehmer -> erstelleEintragWennErlaubtUndNochNichtVorhanden(teilnehmer, ag));
    }

    public Iterable<EinwahlEntdeckerangebot> findeAlle() {
        return einwahlEntdeckerangebotRepository.findAll();
    }

    public void loescheFuerTeilnehmer(Integer teilnehmerId) {
        Map<String, Ag> agNachTitel = StreamSupport.stream(agRepository.findAll().spliterator(), false)
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        StreamSupport.stream(einwahlEntdeckerangebotRepository.findAll().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmerId))
                .filter(einwahl -> agNachTitel.containsKey(einwahl.agTitel()))
                .filter(einwahl -> agNachTitel.get(einwahl.agTitel()).zeit() == Ag.Zeit.NACHMITTAG)
                .forEach(einwahl -> einwahlEntdeckerangebotRepository.deleteById(einwahl.id()));
    }

    public Optional<EinwahlEntdeckerangebot> findeNachId(Integer id) {
        return einwahlEntdeckerangebotRepository.findById(id);
    }

    public EinwahlEntdeckerangebot speichereAuswahl(Integer id, EinwahlEntdeckerangebot.Auswahl auswahl) {
        EinwahlEntdeckerangebot einwahl = findeNachId(id)
                .orElseThrow(() -> new IllegalArgumentException("Einwahl wurde nicht gefunden."));

        return einwahlEntdeckerangebotRepository.save(new EinwahlEntdeckerangebot(
                einwahl.id(),
                einwahl.teilnehmerId(),
                einwahl.agTitel(),
                auswahl,
                einwahl.zugewiesen()
        ));
    }

    public EinwahlEntdeckerangebot speichereZuweisung(Integer id, boolean zugewiesen) {
        EinwahlEntdeckerangebot einwahl = findeNachId(id)
                .orElseThrow(() -> new IllegalArgumentException("Einwahl wurde nicht gefunden."));

        return einwahlEntdeckerangebotRepository.save(new EinwahlEntdeckerangebot(
                einwahl.id(),
                einwahl.teilnehmerId(),
                einwahl.agTitel(),
                einwahl.auswahl(),
                zugewiesen
        ));
    }

    public void speichereZuweisung(Integer teilnehmerId, Ag.Wochentag wochentag, Integer zugewieseneEinwahlId) {
        Map<String, Ag> agNachTitel = StreamSupport.stream(agRepository.findAll().spliterator(), false)
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        List<EinwahlEntdeckerangebot> einwahlenAmTag = StreamSupport.stream(einwahlEntdeckerangebotRepository.findAll().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmerId))
                .filter(einwahl -> agNachTitel.containsKey(einwahl.agTitel()))
                .filter(einwahl -> agNachTitel.get(einwahl.agTitel()).wochentag() == wochentag)
                .toList();

        if (zugewieseneEinwahlId == null) {
            throw new IllegalArgumentException("Bei den Entdeckerangeboten muss fuer " + wochentag.name() + " ein Angebot zugewiesen sein.");
        }

        boolean einwahlIstInGruppe = einwahlenAmTag.stream()
                .anyMatch(einwahl -> Objects.equals(einwahl.id(), zugewieseneEinwahlId));
        if (!einwahlIstInGruppe) {
            throw new IllegalArgumentException("Das zugewiesene Entdeckerangebot gehoert nicht zum ausgewaehlten Wochentag.");
        }

        einwahlenAmTag.forEach(einwahl -> einwahlEntdeckerangebotRepository.save(new EinwahlEntdeckerangebot(
                einwahl.id(),
                einwahl.teilnehmerId(),
                einwahl.agTitel(),
                einwahl.auswahl(),
                Objects.equals(einwahl.id(), zugewieseneEinwahlId)
        )));
    }

    private void erstelleEintragWennErlaubtUndNochNichtVorhanden(Teilnehmer teilnehmer, Ag ag) {
        if (!EinwahlEntdeckerangebot.istErlaubteAuswahl(teilnehmer, ag) || istBereitsVorhanden(teilnehmer, ag)) {
            return;
        }

        einwahlEntdeckerangebotRepository.save(new EinwahlEntdeckerangebot(null, teilnehmer, ag, null));
    }

    private boolean istBereitsVorhanden(Teilnehmer teilnehmer, Ag ag) {
        return StreamSupport.stream(einwahlEntdeckerangebotRepository.findAll().spliterator(), false)
                .anyMatch(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()) && Objects.equals(einwahl.agTitel(), ag.titel()));
    }
}
