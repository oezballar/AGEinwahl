package com.ozballar.ageinwahl.export;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.Teilnehmer;

class EntdeckerBriefVorlageTests {
    private final EntdeckerBriefVorlage vorlage = new EntdeckerBriefVorlage();

    @Test
    void erhaeltLogoSeitenformatAbsatzformatierungUndRunEigenschaftenDerOriginalvorlage() throws Exception {
        var kind = new Teilnehmer(1, "Marie-Luise", "Müller", "2a");
        var ag = new Ag(1, Ag.Wochentag.MONTAG, Ag.Zeit.NACHMITTAG, Ag.Kategorie.ENTDECKERANGEBOT,
                "Forschen", "", "Schürze", "", "", 10, List.of());
        byte[] docx = vorlage.erstellen(List.of(kind), Map.of(1, Map.of(Ag.Wochentag.MONTAG, ag, Ag.Wochentag.FREITAG, ag)),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 16), LocalDate.of(2027, 1, 29));
        try (var original = new XWPFDocument(Files.newInputStream(Path.of("docs/vorlagen/Rueckmeldezettel_Entdeckerangebot.docx")));
             var brief = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertArrayEquals(original.getAllPictures().get(0).getData(), brief.getAllPictures().get(0).getData());
            assertEquals(original.getDocument().getBody().getSectPr().xmlText(), brief.getDocument().getBody().getSectPr().xmlText());
            assertEquals(original.getParagraphs().size(), brief.getParagraphs().size());
            for (int i = 0; i < original.getParagraphs().size(); i++) {
                var a = original.getParagraphs().get(i);
                var b = brief.getParagraphs().get(i);
                assertEquals(a.getCTP().isSetPPr(), b.getCTP().isSetPPr());
                if (a.getCTP().isSetPPr()) {
                    assertEquals(a.getCTP().getPPr().xmlText(), b.getCTP().getPPr().xmlText());
                }
                assertEquals(a.getRuns().size(), b.getRuns().size());
                for (int r = 0; r < a.getRuns().size(); r++) {
                    assertEquals(a.getRuns().get(r).getCTR().getRPr().xmlText(), b.getRuns().get(r).getCTR().getRPr().xmlText());
                }
            }
            assertTrue(brief.getParagraphs().get(0).getCTP().xmlText().contains("drawing"));
            assertTrue(brief.getParagraphs().get(12).getRuns().get(0).isBold());
            assertEquals("Comic Sans MS", brief.getParagraphs().get(12).getRuns().get(0).getFontFamily());
            assertFalse(brief.getDocument().xmlText().contains("{{"));
        }
    }

    @Test
    void wiederholtLogoUndErzwingtSeitenwechselAmBriefanfang() throws Exception {
        byte[] docx = vorlage.erstellen(List.of(new Teilnehmer(1, "Ada", "Müller", "2a"),
                new Teilnehmer(2, "Grace", "Hopper", "3b")), Map.of(),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 16), LocalDate.of(2027, 1, 29));
        try (var brief = new XWPFDocument(new ByteArrayInputStream(docx))) {
            var koepfe = brief.getParagraphs().stream().filter(p -> p.getText().contains("Klasse:")).toList();
            assertEquals(2, koepfe.size());
            assertFalse(koepfe.get(0).isPageBreak());
            assertTrue(koepfe.get(1).isPageBreak());
            for (var kopf : koepfe) {
                assertTrue(kopf.getCTP().xmlText().contains("drawing"));
                assertTrue(kopf.getCTP().xmlText().contains("rId4"));
            }
            assertEquals(1, brief.getAllPictures().size());
        }
    }

    @Test
    void vorlageIstAlsLaufzeitressourceMitOriginalLogoVerfuegbar() throws Exception {
        try (var input = new ClassPathResource(EntdeckerBriefVorlage.VORLAGE).getInputStream();
             var doc = new XWPFDocument(input)) {
            assertEquals(1, doc.getAllPictures().size());
            assertTrue(doc.getParagraphs().get(0).getText().contains("{{Briefdatum}}"));
        }
    }

    @Test
    void ersatzschriftFuehrtZuVerstaendlichemHinweis() throws Exception {
        try (var pdf = new PDDocument(); var output = new java.io.ByteArrayOutputStream()) {
            PDPage seite = new PDPage();
            pdf.addPage(seite);
            try (var stream = new PDPageContentStream(pdf, seite)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.showText("Ersatzschrift");
                stream.endText();
            }
            pdf.save(output);
            var fehler = assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class,
                    () -> vorlage.pruefePdf(output.toByteArray(), 1));
            assertTrue(fehler.getMessage().contains("Comic Sans MS"));
        }
    }
}
