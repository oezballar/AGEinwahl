package com.ozballar.ageinwahl.service;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlVormittagsAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.AgRepository;
import com.ozballar.ageinwahl.repository.EinwahlVormittagsAGRepository;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

@Service
public class EinwahlVormittagsAGService {

    private final EinwahlVormittagsAGRepository einwahlVormittagsAGRepository;
    private final TeilnehmerRepository teilnehmerRepository;
    private final AgRepository agRepository;

    public EinwahlVormittagsAGService(
            EinwahlVormittagsAGRepository einwahlVormittagsAGRepository,
            TeilnehmerRepository teilnehmerRepository,
            AgRepository agRepository
    ) {
        this.einwahlVormittagsAGRepository = einwahlVormittagsAGRepository;
        this.teilnehmerRepository = teilnehmerRepository;
        this.agRepository = agRepository;
    }

    public void erstelleEintraegeFuerTeilnehmer(Teilnehmer teilnehmer) {
        agRepository.findAll().forEach(ag -> erstelleEintragWennGueltigUndNochNichtVorhanden(teilnehmer, ag));
    }

    public void erstelleEintraegeFuerAg(Ag ag) {
        teilnehmerRepository.findAll().forEach(teilnehmer -> erstelleEintragWennGueltigUndNochNichtVorhanden(teilnehmer, ag));
    }

    public Iterable<EinwahlVormittagsAG> findeAlle() {
        return einwahlVormittagsAGRepository.findAll();
    }

    public Optional<EinwahlVormittagsAG> findeNachId(Integer id) {
        return einwahlVormittagsAGRepository.findById(id);
    }

    public EinwahlVormittagsAG speichereAuswahl(Integer id, EinwahlVormittagsAG.Auswahl auswahl) {
        EinwahlVormittagsAG einwahl = findeNachId(id)
                .orElseThrow(() -> new IllegalArgumentException("Einwahl wurde nicht gefunden."));

        return einwahlVormittagsAGRepository.save(new EinwahlVormittagsAG(
                einwahl.id(),
                einwahl.teilnehmerId(),
                einwahl.agTitel(),
                auswahl,
                einwahl.zugewiesen()
        ));
    }

    public EinwahlVormittagsAG speichereZuweisung(Integer id, boolean zugewiesen) {
        EinwahlVormittagsAG einwahl = findeNachId(id)
                .orElseThrow(() -> new IllegalArgumentException("Einwahl wurde nicht gefunden."));

        return einwahlVormittagsAGRepository.save(new EinwahlVormittagsAG(
                einwahl.id(),
                einwahl.teilnehmerId(),
                einwahl.agTitel(),
                einwahl.auswahl(),
                zugewiesen
        ));
    }

    private void erstelleEintragWennGueltigUndNochNichtVorhanden(Teilnehmer teilnehmer, Ag ag) {
        if (!EinwahlVormittagsAG.istGueltigeAuswahl(teilnehmer, ag) || istBereitsVorhanden(teilnehmer, ag)) {
            return;
        }

        einwahlVormittagsAGRepository.save(new EinwahlVormittagsAG(null, teilnehmer, ag, null));
    }

    private boolean istBereitsVorhanden(Teilnehmer teilnehmer, Ag ag) {
        return StreamSupport.stream(einwahlVormittagsAGRepository.findAll().spliterator(), false)
                .anyMatch(einwahl -> Objects.equals(einwahl.teilnehmerId(), teilnehmer.id()) && Objects.equals(einwahl.agTitel(), ag.titel()));
    }
}
