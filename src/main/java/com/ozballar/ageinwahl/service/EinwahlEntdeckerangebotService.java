package com.ozballar.ageinwahl.service;

import java.util.Objects;
import java.util.stream.StreamSupport;

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

    private void erstelleEintragWennErlaubtUndNochNichtVorhanden(Teilnehmer teilnehmer, Ag ag) {
        if (!EinwahlEntdeckerangebot.istErlaubteAuswahl(teilnehmer, ag) || istBereitsVorhanden(teilnehmer, ag)) {
            return;
        }

        einwahlEntdeckerangebotRepository.save(new EinwahlEntdeckerangebot(null, teilnehmer, ag, null));
    }

    private boolean istBereitsVorhanden(Teilnehmer teilnehmer, Ag ag) {
        return StreamSupport.stream(einwahlEntdeckerangebotRepository.findAll().spliterator(), false)
                .anyMatch(einwahl -> Objects.equals(einwahl.teilnehmer().nr(), teilnehmer.nr()) && Objects.equals(einwahl.ag().titel(), ag.titel()));
    }
}
