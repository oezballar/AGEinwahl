package com.ozballar.ageinwahl.export;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Service
public class WunschzettelExport extends AbstractPdfExport {

    private static final int WUNSCHZETTEL_HEIGHT = PAGE_HEIGHT / 2;

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;

    public WunschzettelExport(AgService agService, TeilnehmerService teilnehmerService) {
        this.agService = agService;
        this.teilnehmerService = teilnehmerService;
    }

    public byte[] erstellen() {
        List<Ag> ags = listeAus(agService.findeAlle()).stream()
                .sorted(Comparator
                        .comparing(Ag::wochentag)
                        .thenComparing(Ag::zeit)
                        .thenComparing(Ag::titel))
                .toList();
        List<Teilnehmer> teilnehmer = listeAus(teilnehmerService.findeAlle()).stream()
                .sorted(Comparator
                        .comparing(Teilnehmer::klasse)
                        .thenComparing(Teilnehmer::name)
                        .thenComparing(Teilnehmer::vorname)
                        .thenComparing(Teilnehmer::id))
                .toList();

        List<PdfPage> pages = new ArrayList<>();
        for (int index = 0; index < teilnehmer.size(); index += 2) {
            List<PdfElement> elemente = new ArrayList<>();
            addWunschzettel(elemente, teilnehmer.get(index), erlaubteAgsFuer(teilnehmer.get(index), ags), WUNSCHZETTEL_HEIGHT);
            addLine(elemente, 24, WUNSCHZETTEL_HEIGHT, PAGE_WIDTH - 24, WUNSCHZETTEL_HEIGHT);
            if (index + 1 < teilnehmer.size()) {
                addWunschzettel(elemente, teilnehmer.get(index + 1), erlaubteAgsFuer(teilnehmer.get(index + 1), ags), 0);
            }
            pages.add(new PdfPage(elemente));
        }

        return new PdfDocument(pages, PAGE_WIDTH, PAGE_HEIGHT).render();
    }

    private List<Ag> erlaubteAgsFuer(Teilnehmer teilnehmer, List<Ag> ags) {
        int jahrgang = jahrgang(teilnehmer);
        return ags.stream()
                .filter(ag -> ag.istFuerJahrgangErlaubt(jahrgang))
                .toList();
    }

    private int jahrgang(Teilnehmer teilnehmer) {
        if (teilnehmer.klasse() == null || teilnehmer.klasse().isBlank()) {
            return 0;
        }
        return Character.digit(teilnehmer.klasse().charAt(0), 10);
    }

    private void addWunschzettel(List<PdfElement> elemente, Teilnehmer teilnehmer, List<Ag> erlaubteAgs, int yOffset) {
        int links = 30;
        int rechts = PAGE_WIDTH - 30;
        int oben = yOffset + WUNSCHZETTEL_HEIGHT - 36;

        addRect(elemente, links, yOffset + 16, rechts - links, WUNSCHZETTEL_HEIGHT - 32);
        addText(elemente, links + 16, oben, 12, "Mein AG-Wunschzettel");
        addText(elemente, links + 16, oben - 22, 8, "Name: " + teilnehmer.vorname() + " " + teilnehmer.name());
        addText(elemente, links + 340, oben - 22, 8, "Klasse: " + teilnehmer.klasse());
        addLine(elemente, links + 16, oben - 32, rechts - 16, oben - 32);

        List<FormularZeile> zeilen = formularZeilen(erlaubteAgs);
        int nichtGedruckt = addFormularZeilen(elemente, zeilen, links + 16, oben - 50, yOffset + 40);
        if (nichtGedruckt > 0) {
            addText(elemente, links + 288, yOffset + 28, 7, nichtGedruckt + " weitere Angebote bitte händisch ergänzen.");
        }
    }

    private int addFormularZeilen(List<PdfElement> elemente, List<FormularZeile> zeilen, int x, int yStart, int yUnten) {
        int spaltenBreite = 246;
        int spaltenAbstand = 26;
        int spalte = 0;
        int y = yStart;

        for (int index = 0; index < zeilen.size(); index++) {
            FormularZeile zeile = zeilen.get(index);
            int hoehe = formularZeilenHoehe(zeile, spaltenBreite);
            int abstandVorher = zeile.ueberschrift() && y < yStart ? 8 : 0;

            if (zeile.ueberschrift()) {
                int blockHoehe = formularBlockHoehe(zeilen, index, spaltenBreite);
                if (y - abstandVorher - blockHoehe < yUnten) {
                    if (spalte == 0) {
                        spalte = 1;
                        y = yStart;
                        abstandVorher = 0;
                    } else {
                        return zeilen.size() - index;
                    }
                }
            }

            if (abstandVorher > 0) {
                y -= abstandVorher;
            }
            if (y - hoehe < yUnten && spalte == 0) {
                spalte = 1;
                y = yStart;
                abstandVorher = 0;
            }
            if (y - hoehe < yUnten) {
                return zeilen.size() - index;
            }

            int spaltenX = x + spalte * (spaltenBreite + spaltenAbstand);
            addFormularZeile(elemente, zeile, spaltenX, y, spaltenBreite);
            y -= hoehe;
        }

        return 0;
    }

    private int formularBlockHoehe(List<FormularZeile> zeilen, int startIndex, int spaltenBreite) {
        int hoehe = 0;
        for (int index = startIndex; index < zeilen.size(); index++) {
            FormularZeile zeile = zeilen.get(index);
            if (index > startIndex && zeile.ueberschrift()) {
                break;
            }
            hoehe += formularZeilenHoehe(zeile, spaltenBreite);
        }
        return hoehe;
    }

    private int formularZeilenHoehe(FormularZeile formularZeile, int spaltenBreite) {
        if (formularZeile.ueberschrift()) {
            return 18;
        }
        int textBreite = spaltenBreite - (formularZeile.jaNein() ? 74 : auswahlVorlaufBreite(formularZeile.auswahlBeschriftung()));
        int zeilen = Math.min(2, wrapLines(formularZeile.text(), Math.max(18, textBreite / 4)).size());
        return zeilen == 1 ? 17 : 25;
    }

    private void addFormularZeile(List<PdfElement> elemente, FormularZeile formularZeile, int x, int y, int spaltenBreite) {
        if (formularZeile.ueberschrift()) {
            addBoldText(elemente, x, y - 3, 7, formularZeile.text());
            addLine(elemente, x, y - 11, x + spaltenBreite, y - 11);
            return;
        }

        int textX;
        int textBreite;
        if (formularZeile.jaNein()) {
            addBox(elemente, x, y - 9, 9);
            addText(elemente, x + 13, y - 7, 6, "Ja");
            addBox(elemente, x + 35, y - 9, 9);
            addText(elemente, x + 49, y - 7, 6, "Nein");
            textX = x + 74;
            textBreite = spaltenBreite - 74;
        } else {
            String auswahlBeschriftung = formularZeile.auswahlBeschriftung();
            addRect(elemente, x, y - 10, 20, 11);
            addText(elemente, x + 25, y - 7, 6, auswahlBeschriftung);
            textX = x + auswahlVorlaufBreite(auswahlBeschriftung);
            textBreite = spaltenBreite - auswahlVorlaufBreite(auswahlBeschriftung);
        }

        List<String> titelZeilen = wrapLines(formularZeile.text(), Math.max(18, textBreite / 4));
        for (int index = 0; index < Math.min(2, titelZeilen.size()); index++) {
            addText(elemente, textX, y - 6 - index * 8, 6, titelZeilen.get(index));
        }
    }

    private int auswahlVorlaufBreite(String auswahlBeschriftung) {
        return 32 + auswahlBeschriftung.length() * 4;
    }

    private List<FormularZeile> formularZeilen(List<Ag> erlaubteAgs) {
        List<FormularZeile> zeilen = new ArrayList<>();
        for (Ag.Wochentag wochentag : Ag.Wochentag.values()) {
            for (Ag.Zeit zeit : Ag.Zeit.values()) {
                List<Ag> gruppe = erlaubteAgs.stream()
                        .filter(ag -> ag.wochentag() == wochentag && ag.zeit() == zeit)
                        .toList();
                if (gruppe.isEmpty()) {
                    continue;
                }

                long auswahlFelder = gruppe.stream()
                        .filter(ag -> ag.zeit() != Ag.Zeit.VORMITTAG && ag.kategorie() != Ag.Kategorie.ENTDECKERANGEBOT)
                        .count();
                String auswahlBeschriftung = auswahlBeschriftung(auswahlFelder);
                zeilen.add(new FormularZeile(wochentagText(wochentag) + " - " + wunschzettelZeitText(zeit), false, true, ""));
                for (Ag ag : gruppe) {
                    boolean jaNein = ag.zeit() == Ag.Zeit.VORMITTAG || ag.kategorie() == Ag.Kategorie.ENTDECKERANGEBOT;
                    zeilen.add(new FormularZeile(ag.titel(), jaNein, false, jaNein ? "" : auswahlBeschriftung));
                }
            }
        }
        return zeilen;
    }

    private String auswahlBeschriftung(long anzahl) {
        List<String> werte = new ArrayList<>();
        for (int index = 1; index <= anzahl; index++) {
            werte.add(String.valueOf(index));
        }
        return String.join("/", werte);
    }

    private String wunschzettelZeitText(Ag.Zeit zeit) {
        return switch (zeit) {
            case VORMITTAG -> "7:30 - 8:15 Uhr";
            case NACHMITTAG -> "13:30 - 14:30 Uhr";
        };
    }

    private record FormularZeile(String text, boolean jaNein, boolean ueberschrift, String auswahlBeschriftung) {
    }
}
