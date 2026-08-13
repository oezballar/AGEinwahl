package com.ozballar.ageinwahl.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.repository.AgRepository;

@Service
public class AgService {

    private final AgRepository agRepository;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlAGService einwahlAGService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;

    public AgService(
            AgRepository agRepository,
            EinwahlEntdeckerangebotService einwahlEntdeckerangebotService,
            EinwahlAGService einwahlAGService,
            EinwahlVormittagsAGService einwahlVormittagsAGService
    ) {
        this.agRepository = agRepository;
        this.einwahlEntdeckerangebotService = einwahlEntdeckerangebotService;
        this.einwahlAGService = einwahlAGService;
        this.einwahlVormittagsAGService = einwahlVormittagsAGService;
    }

    public Ag speichern(Ag ag) {
        Ag gespeicherteAg = agRepository.save(ag);
        einwahlEntdeckerangebotService.erstelleEintraegeFuerAg(gespeicherteAg);
        einwahlAGService.erstelleEintraegeFuerAg(gespeicherteAg);
        einwahlVormittagsAGService.erstelleEintraegeFuerAg(gespeicherteAg);
        return gespeicherteAg;
    }

    public Iterable<Ag> mehrereSpeichern(List<Ag> ags) {
        return ags.stream()
                .map(this::speichern)
                .toList();
    }

    public Optional<Ag> findeNachId(Integer id) {
        return agRepository.findById(id);
    }

    public Optional<Ag> findeNachTitel(String titel) {
        return agRepository.findByTitel(titel);
    }

    public Iterable<Ag> findeAlle() {
        return agRepository.findAll();
    }

    public void loescheNachId(Integer id) {
        agRepository.deleteById(id);
    }
}
