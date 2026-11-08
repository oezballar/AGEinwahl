package com.ozballar.ageinwahl.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.EinwahlEntdeckerangebotService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

class EntdeckerRueckmeldezettelExportTests {
    private final EntdeckerBriefVorlage vorlage = spy(new EntdeckerBriefVorlage());
    private final AgService ags = mock(AgService.class);
    private final TeilnehmerService kinder = mock(TeilnehmerService.class);
    private final EinwahlEntdeckerangebotService einwahlen = mock(EinwahlEntdeckerangebotService.class);
    private final Teilnehmer ada = new Teilnehmer(1, "Ada", "Müller", "2a");

    private EntdeckerRueckmeldezettelExport export(String datum, String beginn, String ende) {
        return new EntdeckerRueckmeldezettelExport(ags, kinder, einwahlen, datum, beginn, ende, vorlage);
    }

    private String pdf() {
        return text(export("2026-11-10", "2026-11-16", "2027-01-29").erstellen());
    }

    private String text(byte[] docx) {
        try (var doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            return doc.getParagraphs().stream().map(p -> p.getText().replaceAll("\\t+", " ")).collect(java.util.stream.Collectors.joining("\n"));
        } catch (IOException ex) { throw new AssertionError(ex); }
    }

    @BeforeEach
    void vorbereiten() {
        doAnswer(i -> i.getArgument(0)).when(vorlage).zuPdf(any(), anyInt());
        when(kinder.findeAlle()).thenReturn(List.of(ada));
        when(ags.findeAlle()).thenReturn(List.of(ag("Forschen", Ag.Wochentag.MONTAG, "")));
        when(einwahlen.findeAlle()).thenReturn(List.of(einwahl(1, "Forschen", null, true)));
    }

    @Test
    void sortiertNachKlasseNachnameVornameUndIdMitGenauEinerSeiteJeKind() {
        when(kinder.findeAlle()).thenReturn(List.of(
                new Teilnehmer(5, "Berta", "Zeller", "2a"),
                new Teilnehmer(4, "Zoe", "Adler", "2a"),
                new Teilnehmer(3, "Anna", "Adler", "2a"),
                new Teilnehmer(2, "Zoe", "Zeller", "1c"),
                new Teilnehmer(6, "Ohne", "Zuweisung", "1a"), ada));
        when(einwahlen.findeAlle()).thenReturn(java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(id -> new EinwahlEntdeckerangebot(id, id, "Forschen", null, true)).toList());
        String pdf = pdf();
        verify(vorlage).zuPdf(any(), eq(5));
        assertFalse(pdf.contains("Liebe/r Ohne Zuweisung"));
        assertTrue(pdf.indexOf("Liebe/r Zoe Zeller") < pdf.indexOf("Liebe/r Anna Adler"));
        assertTrue(pdf.indexOf("Liebe/r Anna Adler") < pdf.indexOf("Liebe/r Zoe Adler"));
        assertTrue(pdf.indexOf("Liebe/r Zoe Adler") < pdf.indexOf("Liebe/r Ada Müller"));
        assertTrue(pdf.indexOf("Liebe/r Ada Müller") < pdf.indexOf("Liebe/r Berta Zeller"));
        verify(kinder, times(1)).findeAlle();
        verify(ags, times(1)).findeAlle();
        verify(einwahlen, times(1)).findeAlle();
    }

    @Test
    void verwendetZuweisungUnabhaengigVomWunschUndOrdnetNachWochentagZu() {
        when(ags.findeAlle()).thenReturn(List.of(
                ag("Freitagskunst", Ag.Wochentag.FREITAG, "Schere & Kleber"),
                ag("Montagsforscher", Ag.Wochentag.MONTAG, "Schürze"),
                ag("Nur Wunsch", Ag.Wochentag.MONTAG, "")));
        when(einwahlen.findeAlle()).thenReturn(List.of(
                einwahl(1, "Freitagskunst", EinwahlEntdeckerangebot.Auswahl.NEIN, true),
                einwahl(2, "Montagsforscher", null, true),
                einwahl(3, "Nur Wunsch", EinwahlEntdeckerangebot.Auswahl.JA, false),
                einwahl(4, "Nur Wunsch", EinwahlEntdeckerangebot.Auswahl.JA, null)));
        String pdf = pdf();
        assertTrue(pdf.indexOf("Montag: Entdeckerangebot") < pdf.indexOf("Montagsforscher"));
        assertTrue(pdf.indexOf("Montagsforscher") < pdf.indexOf("Freitag: Entdeckerangebot"));
        assertTrue(pdf.indexOf("Freitag: Entdeckerangebot") < pdf.indexOf("Freitagskunst"));
        assertTrue(pdf.contains("Benötigtes Material: Schürze"));
        assertTrue(pdf.contains("Benötigtes Material: Schere & Kleber"));
        assertFalse(pdf.contains("Nur Wunsch"));
    }

    @Test
    void zeigtBeiTeilweiserZuweisungBeideTageUndLaesstLeeresMaterialWeg() {
        when(ags.findeAlle()).thenReturn(List.of(ag("Forschen", Ag.Wochentag.MONTAG, "  ")));
        when(einwahlen.findeAlle()).thenReturn(List.of(einwahl(1, "Forschen", null, true)));
        String pdf = pdf();
        assertTrue(pdf.contains("Montag: Entdeckerangebot"));
        assertTrue(pdf.contains("Freitag: Entdeckerangebot"));
        assertTrue(pdf.contains("Forschen"));
        assertEquals(1, Pattern.compile("Noch nicht zugewiesen").matcher(pdf).results().count());
        assertFalse(pdf.contains("Benötigtes Material"));
    }

    @Test
    void erzeugtOhneZuweisungenKeinePersonalisiertenBriefeAuchBeiJaWuenschen() {
        when(einwahlen.findeAlle()).thenReturn(List.of(
                einwahl(1, "Forschen", EinwahlEntdeckerangebot.Auswahl.JA, false),
                einwahl(2, "Forschen", EinwahlEntdeckerangebot.Auswahl.JA, null)));
        String pdf = new String(export("", "", "").erstellen(), AbstractPdfExport.PDF_CHARSET);
        assertTrue(pdf.contains("Keine Daten vorhanden."));
        assertFalse(pdf.contains("Ada"));
        assertFalse(pdf.contains("Liebe/r"));
        verify(vorlage, never()).erstellen(any(), any(), any(), any(), any());
        verify(vorlage, never()).zuPdf(any(), anyInt());
    }

    @Test
    void leereTeilnehmerlisteErzeugtInformationsseiteOhneBriefkonfiguration() {
        when(kinder.findeAlle()).thenReturn(List.of());
        String pdf = new String(export("", "", "").erstellen(), AbstractPdfExport.PDF_CHARSET);
        assertTrue(pdf.contains("/Count 1"));
        assertTrue(pdf.contains("Keine Daten vorhanden."));
        verifyNoInteractions(ags, einwahlen);
    }

    @Test
    void lehntMehrereAngeboteAmSelbenTagAbAuchBeiVerschiedenenZeiten() {
        Ag vormittag = new Ag(2, Ag.Wochentag.MONTAG, Ag.Zeit.VORMITTAG,
                Ag.Kategorie.ENTDECKERANGEBOT, "Zweites Angebot", "", "", "", "", 10, List.of());
        when(ags.findeAlle()).thenReturn(List.of(ag("Forschen", Ag.Wochentag.MONTAG, ""), vormittag));
        when(einwahlen.findeAlle()).thenReturn(List.of(einwahl(1, "Forschen", null, true),
                einwahl(2, "Zweites Angebot", null, true)));
        var ex = assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class, this::pdf);
        assertTrue(ex.getMessage().contains("Ada Müller (Klasse 2a)"));
        assertTrue(ex.getMessage().contains("Mehrere Entdeckerangebote am Montag"));
    }

    @Test
    void lehntAndereTageUndUngueltigeReferenzenAb() {
        when(ags.findeAlle()).thenReturn(List.of(ag("Forschen", Ag.Wochentag.DIENSTAG, "")));
        when(einwahlen.findeAlle()).thenReturn(List.of(einwahl(1, "Forschen", null, true)));
        assertTrue(assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class, this::pdf)
                .getMessage().contains("außerhalb von Montag und Freitag"));
        when(ags.findeAlle()).thenReturn(List.of());
        assertTrue(assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class, this::pdf)
                .getMessage().contains("Ungültige Entdecker-Zuweisung"));
        when(ags.findeAlle()).thenReturn(List.of(ag("Forschen", Ag.Wochentag.MONTAG, "")));
        when(einwahlen.findeAlle()).thenReturn(List.of(new EinwahlEntdeckerangebot(1, 999, "Forschen", null, true)));
        assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class, this::pdf);
    }

    @Test
    void verwendetKonfigurierteDatenUndFesteNamenUndPrueftDatumswerte() {
        String pdf = text(export("2027-02-01", "2027-02-08", "2027-04-30").erstellen());
        assertTrue(pdf.contains("01.02.2027"));
        assertTrue(pdf.contains("ab 08.02.2027"));
        assertTrue(pdf.contains("Zeitraum: 08.02.27 – 30.04.27"));
        assertTrue(pdf.contains("Deine Annika Wollstädter & deine Anna Stoll"));
        assertFalse(pdf.contains("16.11.2026"));
        assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class,
                () -> export("2026-02-30", "2026-11-16", "2027-01-29").erstellen());
        assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class,
                () -> export("", "2026-11-16", "2027-01-29").erstellen());
        assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class,
                () -> export("2026-11-10", "2027-02-01", "2027-01-29").erstellen());
    }

    private Ag ag(String titel, Ag.Wochentag tag, String material) {
        return new Ag(1, tag, Ag.Zeit.NACHMITTAG, Ag.Kategorie.ENTDECKERANGEBOT,
                titel, "", material, "", "", 10, List.of());
    }

    private EinwahlEntdeckerangebot einwahl(int id, String titel, EinwahlEntdeckerangebot.Auswahl auswahl, Boolean zugewiesen) {
        return new EinwahlEntdeckerangebot(id, ada.id(), titel, auswahl, zugewiesen);
    }
}
