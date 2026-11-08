package com.ozballar.ageinwahl.export;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlEntdeckerangebotService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

/** Rückmeldebrief gemäß Rueckmeldezettel_Entdeckerangebot.docx. */
@Service
public class EntdeckerRueckmeldezettelExport extends AbstractPdfExport {

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;
    private final EinwahlEntdeckerangebotService einwahlService;
    private final String briefdatum;
    private final String beginn;
    private final String ende;
    private final EntdeckerBriefVorlage vorlage;

    public EntdeckerRueckmeldezettelExport(
            AgService agService, TeilnehmerService teilnehmerService,
            EinwahlEntdeckerangebotService einwahlService,
            @Value("${ageinwahl.entdecker-rueckmeldezettel.briefdatum:}") String briefdatum,
            @Value("${ageinwahl.entdecker-rueckmeldezettel.beginn:}") String beginn,
            @Value("${ageinwahl.entdecker-rueckmeldezettel.ende:}") String ende,
            EntdeckerBriefVorlage vorlage) {
        this.agService = agService;
        this.teilnehmerService = teilnehmerService;
        this.einwahlService = einwahlService;
        this.briefdatum = briefdatum;
        this.beginn = beginn;
        this.ende = ende;
        this.vorlage = vorlage;
    }

    public byte[] erstellen() {
        List<Teilnehmer> kinder = listeAus(teilnehmerService.findeAlle()).stream()
                .sorted(Comparator.comparing(Teilnehmer::klasse).thenComparing(Teilnehmer::name)
                        .thenComparing(Teilnehmer::vorname).thenComparing(Teilnehmer::id))
                .toList();
        if (kinder.isEmpty()) {
            return new PdfDocument(List.of(), PAGE_WIDTH, PAGE_HEIGHT).render();
        }
        Map<String, Ag> ags = listeAus(agService.findeAlle()).stream()
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        Map<Integer, Teilnehmer> kinderNachId = kinder.stream()
                .collect(Collectors.toMap(Teilnehmer::id, kind -> kind));
        Map<Integer, Map<Ag.Wochentag, Ag>> zuweisungen = new HashMap<>();
        for (var einwahl : einwahlService.findeAlle()) {
            if (!Boolean.TRUE.equals(einwahl.zugewiesen())) {
                continue;
            }
            Teilnehmer kind = kinderNachId.get(einwahl.teilnehmerId());
            Ag ag = ags.get(einwahl.agTitel());
            if (kind == null || ag == null || ag.kategorie() != Ag.Kategorie.ENTDECKERANGEBOT) {
                throw new ExportFehler("Ungültige Entdecker-Zuweisung: Teilnehmer "
                        + einwahl.teilnehmerId() + ", Angebot „" + einwahl.agTitel() + "“. Bitte die Zuweisung prüfen.");
            }
            if (ag.wochentag() != Ag.Wochentag.MONTAG && ag.wochentag() != Ag.Wochentag.FREITAG) {
                throw new ExportFehler(bezeichnung(kind) + ": „" + ag.titel()
                        + "“ liegt außerhalb von Montag und Freitag. Bitte die Zuweisung prüfen.");
            }
            Map<Ag.Wochentag, Ag> tage = zuweisungen.computeIfAbsent(kind.id(),
                    id -> new EnumMap<>(Ag.Wochentag.class));
            if (tage.putIfAbsent(ag.wochentag(), ag) != null) {
                throw new ExportFehler(bezeichnung(kind) + ": Mehrere Entdeckerangebote am "
                        + wochentagText(ag.wochentag()) + ". Bitte die Zuweisungen prüfen.");
            }
        }
        List<Teilnehmer> kinderMitZuweisung = kinder.stream()
                .filter(kind -> zuweisungen.containsKey(kind.id()))
                .toList();
        if (kinderMitZuweisung.isEmpty()) {
            return new PdfDocument(List.of(), PAGE_WIDTH, PAGE_HEIGHT).render();
        }
        LocalDate datum = datum(briefdatum, "briefdatum");
        LocalDate start = datum(beginn, "beginn");
        LocalDate schluss = datum(ende, "ende");
        if (schluss.isBefore(start)) {
            throw new ExportFehler("Das Ende des Angebotszeitraums liegt vor dem Beginn.");
        }
        byte[] dokument = vorlage.erstellen(kinderMitZuweisung, zuweisungen, datum, start, schluss);
        return vorlage.zuPdf(dokument, kinderMitZuweisung.size());
    }

    private LocalDate datum(String wert, String schluessel) {
        try {
            return LocalDate.parse(wert);
        } catch (DateTimeParseException ex) {
            throw new ExportFehler("Bitte ageinwahl.entdecker-rueckmeldezettel." + schluessel
                    + " im Format JJJJ-MM-TT konfigurieren.");
        }
    }

    private String bezeichnung(Teilnehmer kind) {
        return kind.vorname() + " " + kind.name() + " (Klasse " + kind.klasse() + ")";
    }

    public static class ExportFehler extends RuntimeException {
        public ExportFehler(String nachricht) {
            super(nachricht);
        }

        public ExportFehler(String nachricht, Throwable ursache) {
            super(nachricht, ursache);
        }
    }
}
