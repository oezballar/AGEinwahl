package com.ozballar.ageinwahl.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.AgRepository;
import com.ozballar.ageinwahl.repository.EinwahlAGRepository;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

@Service
public class EinwahlAGService {

    private final EinwahlAGRepository einwahlAGRepository;
    private final TeilnehmerRepository teilnehmerRepository;
    private final AgRepository agRepository;

    public EinwahlAGService(
            EinwahlAGRepository einwahlAGRepository,
            TeilnehmerRepository teilnehmerRepository,
            AgRepository agRepository
    ) {
        this.einwahlAGRepository = einwahlAGRepository;
        this.teilnehmerRepository = teilnehmerRepository;
        this.agRepository = agRepository;
    }

    public void erstelleEintraegeFuerTeilnehmer(Teilnehmer teilnehmer) {
        agRepository.findAll().forEach(ag -> erstelleEintragWennGueltigUndNochNichtVorhanden(teilnehmer, ag));
    }

    public void erstelleEintraegeFuerAg(Ag ag) {
        teilnehmerRepository.findAll().forEach(teilnehmer -> erstelleEintragWennGueltigUndNochNichtVorhanden(teilnehmer, ag));
    }

    public Iterable<EinwahlAG> findeAlle() {
        return einwahlAGRepository.findAll();
    }

    public void loescheFuerTeilnehmer(Integer teilnehmerId) {
        einwahlAGRepository.deleteByTeilnehmerId(teilnehmerId);
    }

    public Optional<EinwahlAG> findeNachId(Integer id) {
        return einwahlAGRepository.findById(id);
    }

    public EinwahlAG speichereAuswahl(Integer id, Integer auswahl) {
        EinwahlAG einwahl = findeNachId(id)
                .orElseThrow(() -> new IllegalArgumentException("Einwahl wurde nicht gefunden."));

        return einwahlAGRepository.save(new EinwahlAG(
                einwahl.id(),
                einwahl.teilnehmerId(),
                einwahl.agTitel(),
                auswahl,
                einwahl.zugewiesen()
        ));
    }

    public void speichereZuweisung(Integer teilnehmerId, Ag.Wochentag wochentag, Integer zugewieseneEinwahlId) {
        Map<String, Ag> agNachTitel = StreamSupport.stream(agRepository.findAll().spliterator(), false)
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        List<EinwahlAG> einwahlenAmTag = StreamSupport.stream(einwahlAGRepository.findAll().spliterator(), false)
                .filter(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmerId))
                .filter(einwahl -> agNachTitel.containsKey(einwahl.agTitel()))
                .filter(einwahl -> agNachTitel.get(einwahl.agTitel()).wochentag() == wochentag)
                .toList();

        if (zugewieseneEinwahlId == null) {
            throw new IllegalArgumentException("Bei den Nachmittags-AGs muss fuer " + wochentag.name() + " eine AG zugewiesen sein.");
        }

        boolean einwahlIstInGruppe = einwahlenAmTag.stream()
                .anyMatch(einwahl -> Objects.equals(einwahl.id(), zugewieseneEinwahlId));
        if (!einwahlIstInGruppe) {
            throw new IllegalArgumentException("Die zugewiesene AG gehoert nicht zur ausgewaehlten Nachmittagsgruppe.");
        }

        einwahlenAmTag.forEach(einwahl -> einwahlAGRepository.save(new EinwahlAG(
                einwahl.id(),
                einwahl.teilnehmerId(),
                einwahl.agTitel(),
                einwahl.auswahl(),
                Objects.equals(einwahl.id(), zugewieseneEinwahlId)
        )));
    }

    private void erstelleEintragWennGueltigUndNochNichtVorhanden(Teilnehmer teilnehmer, Ag ag) {
        if (!EinwahlAG.istGueltigeAuswahl(teilnehmer, ag) || istBereitsVorhanden(teilnehmer, ag)) {
            return;
        }

        einwahlAGRepository.save(new EinwahlAG(null, teilnehmer, ag, null));
    }

    private boolean istBereitsVorhanden(Teilnehmer teilnehmer, Ag ag) {
        return StreamSupport.stream(einwahlAGRepository.findAll().spliterator(), false)
                .anyMatch(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()) && Objects.equals(einwahl.agTitel(), ag.titel()));
    }
}
