package com.ozballar.ageinwahl.service;

import java.util.Objects;
import java.util.Optional;
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

    public Optional<EinwahlAG> findeNachId(Integer id) {
        return einwahlAGRepository.findById(id);
    }

    public EinwahlAG speichereAuswahl(Integer id, Integer auswahl) {
        EinwahlAG einwahl = findeNachId(id)
                .orElseThrow(() -> new IllegalArgumentException("Einwahl wurde nicht gefunden."));

        return einwahlAGRepository.save(new EinwahlAG(
                einwahl.id(),
                einwahl.teilnehmerNr(),
                einwahl.agTitel(),
                auswahl
        ));
    }

    private void erstelleEintragWennGueltigUndNochNichtVorhanden(Teilnehmer teilnehmer, Ag ag) {
        if (!EinwahlAG.istGueltigeAuswahl(teilnehmer, ag) || istBereitsVorhanden(teilnehmer, ag)) {
            return;
        }

        einwahlAGRepository.save(new EinwahlAG(null, teilnehmer, ag, null));
    }

    private boolean istBereitsVorhanden(Teilnehmer teilnehmer, Ag ag) {
        return StreamSupport.stream(einwahlAGRepository.findAll().spliterator(), false)
                .anyMatch(einwahl -> Objects.equals(einwahl.teilnehmerNr(), teilnehmer.nr()) && Objects.equals(einwahl.agTitel(), ag.titel()));
    }
}
