package com.ozballar.ageinwahl.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlAGService;
import com.ozballar.ageinwahl.service.EinwahlEntdeckerangebotService;
import com.ozballar.ageinwahl.service.EinwahlVormittagsAGService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Service
public class KlassenuebersichtExport extends AbstractPdfExport {

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;
    private final EinwahlAGService einwahlAGService;

    public KlassenuebersichtExport(
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
        List<KlassenSeiten> klassen = klassenSeiten();
        PdfDocument gesamt = new PdfDocument(
                klassen.stream().flatMap(eintrag -> eintrag.seiten().stream()).toList(),
                A4_LANDSCAPE_WIDTH,
                A4_LANDSCAPE_HEIGHT
        );
        List<PdfDocument> einzelDokumente = klassen.stream()
                .map(eintrag -> new PdfDocument(eintrag.seiten(), A4_LANDSCAPE_WIDTH, A4_LANDSCAPE_HEIGHT))
                .toList();
        List<String> klassenNamen = klassen.stream().map(KlassenSeiten::klasse).toList();
        return zipMitDokumenten(gesamt, "klassenuebersicht", klassenNamen, einzelDokumente);
    }

    private PdfDocument dokument() {
        List<KlassenSeiten> klassen = klassenSeiten();
        List<PdfPage> pages = klassen.stream()
                .flatMap(eintrag -> eintrag.seiten().stream())
                .toList();
        return new PdfDocument(pages, A4_LANDSCAPE_WIDTH, A4_LANDSCAPE_HEIGHT);
    }

    private List<KlassenSeiten> klassenSeiten() {
        Map<String, Ag> agNachTitel = listeAus(agService.findeAlle()).stream()
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        Map<Integer, List<Ag>> agsNachTeilnehmer = zugewieseneAgsNachTeilnehmer(agNachTitel);

        Map<String, List<Teilnehmer>> teilnehmerNachKlasse = listeAus(teilnehmerService.findeAlle()).stream()
                .sorted(Comparator
                        .comparing(Teilnehmer::klasse)
                        .thenComparing(Teilnehmer::name)
                        .thenComparing(Teilnehmer::vorname)
                        .thenComparing(Teilnehmer::id))
                .collect(Collectors.groupingBy(
                        Teilnehmer::klasse,
                        java.util.TreeMap::new,
                        Collectors.toList()
                ));

        return teilnehmerNachKlasse.entrySet().stream()
                .map(entry -> new KlassenSeiten(
                        entry.getKey(),
                        klassenuebersichtSeiten(entry.getKey(), entry.getValue(), agsNachTeilnehmer)
                ))
                .toList();
    }

    private Map<Integer, List<Ag>> zugewieseneAgsNachTeilnehmer(Map<String, Ag> agNachTitel) {
        Map<Integer, List<Ag>> agsNachTeilnehmer = new HashMap<>();

        listeAus(einwahlEntdeckerangebotService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeAgHinzu(agsNachTeilnehmer, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));
        listeAus(einwahlVormittagsAGService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeAgHinzu(agsNachTeilnehmer, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));
        listeAus(einwahlAGService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeAgHinzu(agsNachTeilnehmer, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));

        agsNachTeilnehmer.replaceAll((teilnehmerId, ags) -> ags.stream()
                .distinct()
                .sorted(Comparator
                        .comparing(Ag::wochentag)
                        .thenComparing(Ag::zeit)
                        .thenComparing(Ag::titel))
                .toList());
        return agsNachTeilnehmer;
    }

    private void fuegeAgHinzu(Map<Integer, List<Ag>> agsNachTeilnehmer, Map<String, Ag> agNachTitel, Integer teilnehmerId, String agTitel) {
        Ag ag = agNachTitel.get(agTitel);
        if (ag == null) {
            return;
        }
        agsNachTeilnehmer.computeIfAbsent(teilnehmerId, id -> new ArrayList<>()).add(ag);
    }

    private List<PdfPage> klassenuebersichtSeiten(
            String klasse,
            List<Teilnehmer> teilnehmer,
            Map<Integer, List<Ag>> agsNachTeilnehmer
    ) {
        List<PdfPage> pages = new ArrayList<>();
        List<PdfElement> texte = new ArrayList<>();
        int y = klassenuebersichtKopf(texte, klasse, false);

        int links = 36;
        int nameX = links;
        int ersteAgX = 182;
        int rechts = 806;
        int agSpalten = Ag.Wochentag.values().length;
        int agSpaltenBreite = Math.max(64, (rechts - ersteAgX) / agSpalten);

        for (Teilnehmer eintrag : teilnehmer) {
            List<Ag> zugewieseneAgs = agsNachTeilnehmer.getOrDefault(eintrag.id(), List.of());
            Map<Ag.Wochentag, List<Ag>> agsNachWochentag = zugewieseneAgs.stream()
                    .collect(Collectors.groupingBy(
                            Ag::wochentag,
                            () -> new EnumMap<>(Ag.Wochentag.class),
                            Collectors.toList()
                    ));
            List<List<String>> agZeilen = new ArrayList<>();
            for (Ag.Wochentag wochentag : Ag.Wochentag.values()) {
                List<Ag> agsAnTag = agsNachWochentag.getOrDefault(wochentag, List.of());
                if (agsAnTag.isEmpty()) {
                    if (wochentag == Ag.Wochentag.MONTAG && zugewieseneAgs.isEmpty()) {
                        agZeilen.add(List.of("Keine AG zugewiesen"));
                    } else {
                        agZeilen.add(List.of(""));
                    }
                } else {
                    List<String> zeilen = new ArrayList<>();
                    for (Ag ag : agsAnTag) {
                        zeilen.addAll(wrapLines(agKurzText(ag), Math.max(12, agSpaltenBreite / 5)));
                    }
                    agZeilen.add(zeilen);
                }
            }
            int maxZeilen = agZeilen.stream().mapToInt(List::size).max().orElse(1);
            int benoetigteHoehe = Math.max(34, maxZeilen * 13 + 24);
            if (y - benoetigteHoehe < 62) {
                pages.add(new PdfPage(texte));
                texte = new ArrayList<>();
                y = klassenuebersichtKopf(texte, klasse, true);
            }

            addText(texte, nameX, y, 9, eintrag.name() + ", " + eintrag.vorname());
            for (int spalte = 0; spalte < agSpalten; spalte++) {
                int x = ersteAgX + spalte * agSpaltenBreite;
                int agY = y;
                for (String zeile : agZeilen.get(spalte)) {
                    if (!zeile.isBlank()) {
                        addText(texte, x, agY, 7, zeile);
                    }
                    agY -= 11;
                }
            }
            int linieY = y - Math.max(16, maxZeilen * 13) - 6;
            addLine(texte, links, linieY, rechts, linieY);
            y = linieY - 18;
        }

        pages.add(new PdfPage(texte));
        return pages;
    }

    private int klassenuebersichtKopf(List<PdfElement> texte, String klasse, boolean fortsetzung) {
        int y = 545;
        int links = 36;
        int ersteAgX = 182;
        int rechts = 806;
        int agSpalten = Ag.Wochentag.values().length;
        int agSpaltenBreite = Math.max(64, (rechts - ersteAgX) / agSpalten);

        addText(texte, links, y, 18, "Klassenübersicht" + (fortsetzung ? " - Fortsetzung" : ""));
        y -= 30;
        addText(texte, links, y, 13, "Klasse " + klasse);
        y -= 28;
        addText(texte, links, y, 10, "Name");
        Ag.Wochentag[] wochentage = Ag.Wochentag.values();
        for (int spalte = 0; spalte < agSpalten; spalte++) {
            addText(texte, ersteAgX + spalte * agSpaltenBreite, y, 10, wochentagText(wochentage[spalte]));
        }
        y -= 12;
        addLine(texte, links, y, rechts, y);
        return y - 20;
    }

    private String agKurzText(Ag ag) {
        return ag.titel();
    }

    private record KlassenSeiten(String klasse, List<PdfPage> seiten) {
    }
}
