package com.ozballar.ageinwahl.export;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlAGService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Service
public class AgWartelistenExport extends AbstractPdfExport {

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;
    private final EinwahlAGService einwahlAGService;

    public AgWartelistenExport(
            AgService agService,
            TeilnehmerService teilnehmerService,
            EinwahlAGService einwahlAGService
    ) {
        this.agService = agService;
        this.teilnehmerService = teilnehmerService;
        this.einwahlAGService = einwahlAGService;
    }

    public byte[] erstellen() {
        Map<Integer, Teilnehmer> teilnehmerNachId = listeAus(teilnehmerService.findeAlle()).stream()
                .collect(Collectors.toMap(Teilnehmer::id, teilnehmer -> teilnehmer));
        Map<String, Ag> agNachTitel = listeAus(agService.findeAlle()).stream()
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        Map<String, List<Teilnehmer>> wartendeTeilnehmer = listeAus(einwahlAGService.findeAlle()).stream()
                .filter(einwahl -> einwahl.auswahl() != null && einwahl.auswahl() == 1)
                .filter(einwahl -> !Boolean.TRUE.equals(einwahl.zugewiesen()))
                .filter(einwahl -> agNachTitel.containsKey(einwahl.agTitel()))
                .filter(einwahl -> agNachTitel.get(einwahl.agTitel()).zeit() == Ag.Zeit.NACHMITTAG)
                .map(einwahl -> Map.entry(einwahl.agTitel(), teilnehmerNachId.get(einwahl.teilnehmerId())))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        List<PdfPage> pages = wartendeTeilnehmer.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, List<Teilnehmer>> entry) -> agNachTitel.get(entry.getKey()).wochentag())
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> wartelisteSeite(agNachTitel.get(entry.getKey()), entry.getValue()))
                .toList();
        return new PdfDocument(pages, PAGE_WIDTH, PAGE_HEIGHT).render();
    }

    private PdfPage wartelisteSeite(Ag ag, List<Teilnehmer> teilnehmer) {
        List<PdfElement> texte = new java.util.ArrayList<>();
        int y = 790;
        List<Teilnehmer> sortiert = teilnehmer.stream()
                .distinct()
                .sorted(Comparator.comparing(Teilnehmer::klasse)
                        .thenComparing(Teilnehmer::name)
                        .thenComparing(Teilnehmer::vorname)
                        .thenComparing(Teilnehmer::id))
                .toList();

        addBoldText(texte, 48, y, 18, "AG-Warteliste");
        y -= 34;
        addText(texte, 48, y, 12, "AG: " + ag.titel());
        y -= 20;
        addText(texte, 48, y, 12, "Zeit: " + wochentagText(ag.wochentag()) + " / " + ag.zeit().name());
        y -= 30;
        addBoldText(texte, 48, y, 13, "Wartende Kinder (" + sortiert.size() + ")");
        y -= 22;
        addText(texte, 48, y, 10, "Pos.");
        addText(texte, 92, y, 10, "Klasse");
        addText(texte, 154, y, 10, "Name");
        y -= 12;
        addText(texte, 48, y, 10, "____________________________________________________________");
        y -= 20;

        for (int index = 0; index < sortiert.size(); index++) {
            if (y < 64) {
                addText(texte, 48, y, 10, "Weitere Kinder bitte auf Folgeseite ergänzen.");
                break;
            }
            Teilnehmer kind = sortiert.get(index);
            addText(texte, 48, y, 10, (index + 1) + ".");
            addText(texte, 92, y, 10, kind.klasse());
            addText(texte, 154, y, 10, kind.name() + ", " + kind.vorname());
            y -= 18;
        }
        return new PdfPage(texte);
    }
}
