package com.ozballar.ageinwahl.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

@Service
public class TeilnehmerService {

    private final TeilnehmerRepository teilnehmerRepository;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlAGService einwahlAGService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;

    public TeilnehmerService(
            TeilnehmerRepository teilnehmerRepository,
            EinwahlEntdeckerangebotService einwahlEntdeckerangebotService,
            EinwahlAGService einwahlAGService,
            EinwahlVormittagsAGService einwahlVormittagsAGService
    ) {
        this.teilnehmerRepository = teilnehmerRepository;
        this.einwahlEntdeckerangebotService = einwahlEntdeckerangebotService;
        this.einwahlAGService = einwahlAGService;
        this.einwahlVormittagsAGService = einwahlVormittagsAGService;
    }

    public Teilnehmer speichern(Teilnehmer teilnehmer) {
        Teilnehmer gespeicherterTeilnehmer = teilnehmerRepository.save(teilnehmer);
        einwahlEntdeckerangebotService.erstelleEintraegeFuerTeilnehmer(gespeicherterTeilnehmer);
        einwahlAGService.erstelleEintraegeFuerTeilnehmer(gespeicherterTeilnehmer);
        einwahlVormittagsAGService.erstelleEintraegeFuerTeilnehmer(gespeicherterTeilnehmer);
        return gespeicherterTeilnehmer;
    }

    public Iterable<Teilnehmer> mehrereSpeichern(List<Teilnehmer> teilnehmer) {
        return teilnehmer.stream()
                .map(this::speichern)
                .toList();
    }

    public Optional<Teilnehmer> findeNachNr(Integer nr) {
        return teilnehmerRepository.findById(nr);
    }

    public Optional<Teilnehmer> findeNachVornameUndName(String vorname, String name) {
        return teilnehmerRepository.findByVornameAndName(vorname, name);
    }

    public Iterable<Teilnehmer> findeAlle() {
        return teilnehmerRepository.findAll();
    }

    public void loescheNachNr(Integer nr) {
        teilnehmerRepository.deleteById(nr);
    }
}
