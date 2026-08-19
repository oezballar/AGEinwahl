package com.ozballar.ageinwahl.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.EinwahlVormittagsAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlAGService;
import com.ozballar.ageinwahl.service.EinwahlEntdeckerangebotService;
import com.ozballar.ageinwahl.service.EinwahlVormittagsAGService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Service
public class TeilnehmerStundenplanExport extends AbstractPdfExport {

    private static final int ABSCHNITT_HOEHE = PAGE_HEIGHT / 2;
    private static final int RAND = 30;
    private static final int TABELLEN_BREITE = PAGE_WIDTH - 2 * RAND;
    private static final int ZEITSPALTE_BREITE = 82;
    private static final int TAG_BREITE = (TABELLEN_BREITE - ZEITSPALTE_BREITE) / 5;

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;
    private final EinwahlAGService einwahlAGService;

    public TeilnehmerStundenplanExport(
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
        Map<String, Ag> agNachTitel = listeAus(agService.findeAlle()).stream()
                .collect(java.util.stream.Collectors.toMap(Ag::titel, ag -> ag));
        Map<Integer, List<BelegteAg>> belegteAgs = belegteAgsNachTeilnehmer(agNachTitel);
        List<Teilnehmer> teilnehmer = listeAus(teilnehmerService.findeAlle()).stream()
                .sorted(Comparator.comparing(Teilnehmer::klasse)
                        .thenComparing(Teilnehmer::name)
                        .thenComparing(Teilnehmer::vorname)
                        .thenComparing(Teilnehmer::id))
                .toList();

        List<PdfPage> pages = new ArrayList<>();
        for (int index = 0; index < teilnehmer.size(); index += 2) {
            List<PdfElement> elemente = new ArrayList<>();
            addStundenplan(elemente, teilnehmer.get(index), belegteAgs.getOrDefault(teilnehmer.get(index).id(), List.of()), ABSCHNITT_HOEHE);
            addLine(elemente, 24, ABSCHNITT_HOEHE, PAGE_WIDTH - 24, ABSCHNITT_HOEHE);
            if (index + 1 < teilnehmer.size()) {
                Teilnehmer zweiterTeilnehmer = teilnehmer.get(index + 1);
                addStundenplan(elemente, zweiterTeilnehmer, belegteAgs.getOrDefault(zweiterTeilnehmer.id(), List.of()), 0);
            }
            pages.add(new PdfPage(elemente));
        }

        return new PdfDocument(pages, PAGE_WIDTH, PAGE_HEIGHT).render();
    }

    private Map<Integer, List<BelegteAg>> belegteAgsNachTeilnehmer(Map<String, Ag> agNachTitel) {
        Map<Integer, Set<BelegteAg>> belegteAgs = new HashMap<>();
        listeAus(einwahlEntdeckerangebotService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeHinzu(belegteAgs, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));
        listeAus(einwahlVormittagsAGService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeHinzu(belegteAgs, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));
        listeAus(einwahlAGService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeHinzu(belegteAgs, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));

        Map<Integer, List<BelegteAg>> ergebnis = new HashMap<>();
        belegteAgs.forEach((teilnehmerId, ags) -> ergebnis.put(teilnehmerId, ags.stream()
                .sorted(Comparator.comparing(BelegteAg::wochentag)
                        .thenComparing(BelegteAg::zeit)
                        .thenComparing(BelegteAg::titel))
                .toList()));
        return ergebnis;
    }

    private void fuegeHinzu(
            Map<Integer, Set<BelegteAg>> belegteAgs,
            Map<String, Ag> agNachTitel,
            Integer teilnehmerId,
            String agTitel
    ) {
        Ag ag = agNachTitel.get(agTitel);
        if (ag != null) {
            belegteAgs.computeIfAbsent(teilnehmerId, id -> new LinkedHashSet<>())
                    .add(new BelegteAg(ag.wochentag(), ag.zeit(), ag.titel()));
        }
    }

    private void addStundenplan(List<PdfElement> elemente, Teilnehmer teilnehmer, List<BelegteAg> belegteAgs, int yOffset) {
        int oben = yOffset + ABSCHNITT_HOEHE - 28;
        int tabellenOben = yOffset + ABSCHNITT_HOEHE - 75;
        int kopfHoehe = 22;
        int zeilenHoehe = 125;
        int tabellenUnten = tabellenOben - kopfHoehe - 2 * zeilenHoehe;

        addBoldText(elemente, RAND, oben, 12, "Stundenplan");
        addText(elemente, RAND + 115, oben, 9, "Name: " + teilnehmer.vorname() + " " + teilnehmer.name());
        addText(elemente, RAND + 390, oben, 9, "Klasse: " + teilnehmer.klasse());

        addRect(elemente, RAND, tabellenUnten, TABELLEN_BREITE, tabellenOben - tabellenUnten);
        int zeitspaltenLinie = RAND + ZEITSPALTE_BREITE;
        addLine(elemente, zeitspaltenLinie, tabellenUnten, zeitspaltenLinie, tabellenOben);
        for (int tagIndex = 1; tagIndex <= 5; tagIndex++) {
            int x = zeitspaltenLinie + tagIndex * TAG_BREITE;
            addLine(elemente, x, tabellenUnten, x, tabellenOben);
        }
        int kopfLinie = tabellenOben - kopfHoehe;
        int mittellinie = kopfLinie - zeilenHoehe;
        addLine(elemente, RAND, kopfLinie, PAGE_WIDTH - RAND, kopfLinie);
        addLine(elemente, RAND, mittellinie, PAGE_WIDTH - RAND, mittellinie);

        addBoldText(elemente, RAND + 10, tabellenOben - 15, 7, "Zeit");
        for (Ag.Wochentag wochentag : Ag.Wochentag.values()) {
            int x = zeitspaltenLinie + wochentag.ordinal() * TAG_BREITE + 8;
            addBoldText(elemente, x, tabellenOben - 15, 7, wochentagText(wochentag));
        }
        addText(elemente, RAND + 10, kopfLinie - 35, 7, "7:30–8:15 Uhr");
        addText(elemente, RAND + 10, mittellinie - 35, 7, "13:30–14:30 Uhr");

        for (Ag.Wochentag wochentag : Ag.Wochentag.values()) {
            for (Ag.Zeit zeit : Ag.Zeit.values()) {
                int x = zeitspaltenLinie + wochentag.ordinal() * TAG_BREITE + 5;
                int zellenOben = zeit == Ag.Zeit.VORMITTAG ? kopfLinie : mittellinie;
                List<String> titel = belegteAgs.stream()
                        .filter(ag -> ag.wochentag() == wochentag && ag.zeit() == zeit)
                        .map(BelegteAg::titel)
                        .toList();
                addAgTitel(elemente, x, zellenOben - 18, titel);
            }
        }
    }

    private void addAgTitel(List<PdfElement> elemente, int x, int y, List<String> titel) {
        if (titel.isEmpty()) {
            addText(elemente, x, y, 6, "-");
            return;
        }

        int zeile = 0;
        for (String agTitel : titel) {
            for (String text : wrapLines(agTitel, 15)) {
                if (zeile >= 11) {
                    return;
                }
                addText(elemente, x, y - zeile * 9, 6, text);
                zeile++;
            }
        }
    }

    private record BelegteAg(Ag.Wochentag wochentag, Ag.Zeit zeit, String titel) {
    }
}
