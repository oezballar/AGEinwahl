package com.ozballar.ageinwahl.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlAGService;
import com.ozballar.ageinwahl.service.EinwahlEntdeckerangebotService;
import com.ozballar.ageinwahl.service.EinwahlVormittagsAGService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Service
public class AgTeilnehmerlistenExport extends AbstractPdfExport {

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;
    private final EinwahlAGService einwahlAGService;

    public AgTeilnehmerlistenExport(
            AgService agService,
            TeilnehmerService teilnehmerService,
            EinwahlEntdeckerangebotService einwahlEntdeckerangebotService,
            EinwahlVormittagsAGService einwahlVormittagsAGService,
            EinwahlAGService einwahlAGService
    ) {
        this.agService = agService;
        this.teilnehmerService = teilnehmerService;
        this.einwahlEntdeckerangebotService = einwahlEntdeckerangebotService;
        this.einwahlVormittagsAGService = einwahlVormittagsAGService;
        this.einwahlAGService = einwahlAGService;
    }

    public byte[] erstellen() {
        return dokument().render();
    }

    public byte[] erstellenZip() {
        List<Ag> ags = sortierteAgs();
        return zipMitEinzelseiten(dokument(), "ag-teilnehmerlisten", ags.stream().map(Ag::titel).toList());
    }

    private PdfDocument dokument() {
        Map<Integer, Teilnehmer> teilnehmerNachId = teilnehmerNachId();
        Map<String, List<Teilnehmer>> teilnehmerNachAgTitel = teilnehmerNachAgTitel(teilnehmerNachId);

        List<PdfPage> pages = sortierteAgs().stream()
                .map(ag -> teilnehmerlisteSeite(ag, teilnehmerNachAgTitel.getOrDefault(ag.titel(), List.of())))
                .toList();

        return new PdfDocument(pages, PAGE_WIDTH, PAGE_HEIGHT);
    }

    private List<Ag> sortierteAgs() {
        return listeAus(agService.findeAlle()).stream()
                .sorted(Comparator
                        .comparing(Ag::wochentag)
                        .thenComparing(Ag::zeit)
                        .thenComparing(Ag::titel))
                .toList();
    }

    private Map<Integer, Teilnehmer> teilnehmerNachId() {
        Map<Integer, Teilnehmer> teilnehmerNachId = new HashMap<>();
        listeAus(teilnehmerService.findeAlle())
                .forEach(teilnehmer -> teilnehmerNachId.put(teilnehmer.id(), teilnehmer));
        return teilnehmerNachId;
    }

    private Map<String, List<Teilnehmer>> teilnehmerNachAgTitel(Map<Integer, Teilnehmer> teilnehmerNachId) {
        Map<String, List<Teilnehmer>> teilnehmerNachAgTitel = new HashMap<>();

        listeAus(einwahlEntdeckerangebotService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeTeilnehmerHinzu(teilnehmerNachAgTitel, teilnehmerNachId, einwahl.agTitel(), einwahl.teilnehmerId()));
        listeAus(einwahlVormittagsAGService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeTeilnehmerHinzu(teilnehmerNachAgTitel, teilnehmerNachId, einwahl.agTitel(), einwahl.teilnehmerId()));
        listeAus(einwahlAGService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeTeilnehmerHinzu(teilnehmerNachAgTitel, teilnehmerNachId, einwahl.agTitel(), einwahl.teilnehmerId()));

        teilnehmerNachAgTitel.replaceAll((titel, teilnehmer) -> teilnehmer.stream()
                .distinct()
                .sorted(Comparator
                        .comparing(Teilnehmer::klasse)
                        .thenComparing(Teilnehmer::name)
                        .thenComparing(Teilnehmer::vorname)
                        .thenComparing(Teilnehmer::id))
                .toList());
        return teilnehmerNachAgTitel;
    }

    private void fuegeTeilnehmerHinzu(
            Map<String, List<Teilnehmer>> teilnehmerNachAgTitel,
            Map<Integer, Teilnehmer> teilnehmerNachId,
            String agTitel,
            Integer teilnehmerId
    ) {
        Teilnehmer teilnehmer = teilnehmerNachId.get(teilnehmerId);
        if (teilnehmer == null) {
            return;
        }

        teilnehmerNachAgTitel.computeIfAbsent(agTitel, titel -> new ArrayList<>()).add(teilnehmer);
    }

    private PdfPage teilnehmerlisteSeite(Ag ag, List<Teilnehmer> teilnehmer) {
        List<PdfElement> texte = new ArrayList<>();
        int y = 790;

        texte.add(new PdfText(48, y, 18, "AG-Teilnehmerliste"));
        y -= 34;
        texte.add(new PdfText(48, y, 12, "Titel: " + wert(ag.titel())));
        y -= 20;
        texte.add(new PdfText(48, y, 12, "Verantwortlich: " + wert(ag.verantwortlicher())));
        y -= 20;
        texte.add(new PdfText(48, y, 12, "Zeit: " + ag.wochentag().name() + " / " + ag.zeit().name()));
        y -= 20;
        texte.add(new PdfText(48, y, 12, "Raum: " + wert(ag.ort())));
        y -= 30;

        texte.add(new PdfText(48, y, 13, "Teilnehmer (" + teilnehmer.size() + ")"));
        y -= 22;
        texte.add(new PdfText(48, y, 10, "Pos."));
        texte.add(new PdfText(92, y, 10, "Klasse"));
        texte.add(new PdfText(154, y, 10, "Name"));
        y -= 12;
        texte.add(new PdfText(48, y, 10, "____________________________________________________________"));
        y -= 20;

        if (teilnehmer.isEmpty()) {
            texte.add(new PdfText(48, y, 11, "Keine Teilnehmer zugewiesen."));
            return new PdfPage(texte);
        }

        int position = 1;
        for (Teilnehmer eintrag : teilnehmer) {
            if (y < 64) {
                texte.add(new PdfText(48, y, 10, "Weitere Teilnehmer bitte auf Folgeseite ergänzen."));
                break;
            }
            texte.add(new PdfText(48, y, 10, position + "."));
            texte.add(new PdfText(92, y, 10, eintrag.klasse()));
            texte.add(new PdfText(154, y, 10, eintrag.name() + ", " + eintrag.vorname()));
            y -= 18;
            position++;
        }

        return new PdfPage(texte);
    }

    private String wert(String wert) {
        return wert == null || wert.isBlank() ? "-" : wert;
    }
}
