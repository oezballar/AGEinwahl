package com.ozballar.ageinwahl.export;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.Teilnehmer;

/** Benötigt LibreOffice und Comic Sans MS: -Dageinwahl.pdf-integration=true. */
@EnabledIfSystemProperty(named = "ageinwahl.pdf-integration", matches = "true")
class EntdeckerBriefPdfTests {
    private final EntdeckerBriefVorlage vorlage = new EntdeckerBriefVorlage();

    private byte[] docx(List<Teilnehmer> kinder, String titel, String material) {
        var ag = new Ag(1, Ag.Wochentag.MONTAG, Ag.Zeit.NACHMITTAG, Ag.Kategorie.ENTDECKERANGEBOT,
                titel, "", material, "", "", 10, List.of());
        return vorlage.erstellen(kinder, Map.of(1, Map.of(Ag.Wochentag.MONTAG, ag)),
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 16), LocalDate.of(2027, 1, 29));
    }

    @Test
    void pdfHatJeKindEineSeiteMitLogoUndEingebetteterComicSansInZwoelfPunkt() throws Exception {
        byte[] docx = docx(List.of(new Teilnehmer(1, "Marie-Luise", "Wollstädter-Müller", "2a"),
                new Teilnehmer(2, "Grace", "Hopper", "3b")), "Forschen und Entdecken", "Schürze\nSchere & Kleber");
        try (var pdf = Loader.loadPDF(vorlage.zuPdf(docx, 2))) {
            assertEquals(2, pdf.getNumberOfPages());
            for (var seite : pdf.getPages()) {
                assertEquals(595, seite.getMediaBox().getWidth(), 1);
                assertEquals(842, seite.getMediaBox().getHeight(), 1);
                int bilder = 0;
                boolean fett = false;
                for (var name : seite.getResources().getXObjectNames()) {
                    if (seite.getResources().getXObject(name) instanceof PDImageXObject) { bilder++; }
                }
                assertEquals(1, bilder, "Das Schullogo muss auf jeder Seite vorhanden sein.");
                for (var name : seite.getResources().getFontNames()) {
                    var font = seite.getResources().getFont(name);
                    assertTrue(font.getName().contains("ComicSansMS"));
                    assertTrue(font.isEmbedded());
                    fett |= font.getName().contains("Bold");
                }
                assertTrue(fett);
            }
            var stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition position) {
                    assertEquals(12, position.getFontSizeInPt(), 0.1);
                    super.processTextPosition(position);
                }
            };
            String text = stripper.getText(pdf);
            assertTrue(text.contains("Wollstädter-Müller"));
            assertTrue(text.contains("Schürze"));
            assertTrue(text.contains("Schere & Kleber"));
            assertTrue(text.indexOf("Marie-Luise") < text.indexOf("Grace"));
            assertFalse(text.contains("{{"));
        }
    }

    @Test
    void langeNamenUndTitelWerdenInOriginalschriftUmgebrochen() throws Exception {
        byte[] docx = docx(List.of(new Teilnehmer(1, "Marie-Luise Charlotte", "Wollstädter-Müller", "2a")),
                "Forschen und Entdecken mit Naturmaterialien in unserer Umgebung und im Schulgarten",
                "Schürze, Schere, Kleber und ein kleines Glas mit Deckel");
        try (var pdf = Loader.loadPDF(vorlage.zuPdf(docx, 1))) {
            assertEquals(1, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Schulgarten"));
            assertTrue(text.contains("Deine Annika Wollstädter & deine Anna Stoll"));
        }
    }

    @Test
    void zuLangeInhalteWerdenMitHinweisAbgewiesenStattSchriftOderLayoutZuAendern() {
        byte[] docx = docx(List.of(new Teilnehmer(1, "Ada", "Müller", "2a")), "Forschen", "Material ".repeat(1500));
        var fehler = assertThrows(EntdeckerRueckmeldezettelExport.ExportFehler.class, () -> vorlage.zuPdf(docx, 1));
        assertTrue(fehler.getMessage().contains("nicht auf eine Seite je Kind"));
    }
}
