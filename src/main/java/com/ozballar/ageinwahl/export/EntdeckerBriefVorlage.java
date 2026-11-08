package com.ozballar.ageinwahl.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.export.EntdeckerRueckmeldezettelExport.ExportFehler;

/** Befüllt ausschließlich die Textfelder; Zeichnungen, Formatierung und Beziehungen bleiben erhalten. */
@Service
public class EntdeckerBriefVorlage {
    static final String VORLAGE = "templates/exports/Rueckmeldezettel_Entdeckerangebot.docx";
    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final DateTimeFormatter KURZDATUM = DateTimeFormatter.ofPattern("dd.MM.uu");

    public byte[] erstellen(List<Teilnehmer> kinder, Map<Integer, Map<Ag.Wochentag, Ag>> zuweisungen,
            LocalDate datum, LocalDate beginn, LocalDate ende) {
        try (var input = new ClassPathResource(VORLAGE).getInputStream();
             var gesamt = new XWPFDocument(input);
             var output = new ByteArrayOutputStream()) {
            // Das Originalpaket behält insbesondere das Logo und dessen rId-Beziehung.
            while (!gesamt.getBodyElements().isEmpty()) {
                gesamt.removeBodyElement(0);
            }
            for (int index = 0; index < kinder.size(); index++) {
                Teilnehmer kind = kinder.get(index);
                try (var vorlagenInput = new ClassPathResource(VORLAGE).getInputStream();
                     var brief = new XWPFDocument(vorlagenInput)) {
                    befuellen(brief, kind, zuweisungen.getOrDefault(kind.id(), Map.of()), datum, beginn, ende);
                    if (index > 0) {
                        brief.getParagraphs().get(0).setPageBreak(true);
                    }
                    for (XWPFParagraph paragraph : brief.getParagraphs()) {
                        CTP kopie = (CTP) paragraph.getCTP().copy();
                        // Zeichnungs-IDs sind innerhalb eines Word-Dokuments eindeutig.
                        zeichnungsId(kopie.getDomNode(), index + 1);
                        gesamt.getDocument().getBody().addNewP().set(kopie);
                    }
                }
            }
            gesamt.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ExportFehler("Die DOCX-Vorlage für die Entdecker-Rückmeldezettel konnte nicht gelesen werden.", ex);
        }
    }

    private void zeichnungsId(Node node, int id) {
        if (node instanceof Element element && "docPr".equals(element.getLocalName())) {
            element.setAttribute("id", Integer.toString(id));
        }
        for (Node kind = node.getFirstChild(); kind != null; kind = kind.getNextSibling()) {
            zeichnungsId(kind, id);
        }
    }

    private void befuellen(XWPFDocument brief, Teilnehmer kind, Map<Ag.Wochentag, Ag> tage,
            LocalDate datum, LocalDate beginn, LocalDate ende) {
        Map<String, String> werte = Map.of(
                "{{Klasse}}", kind.klasse(), "{{Vorname}}", kind.vorname(), "{{Nachname}}", kind.name(),
                "{{Briefdatum}}", DATUM.format(datum), "{{Beginn}}", DATUM.format(beginn),
                "{{BeginnKurz}}", KURZDATUM.format(beginn), "{{EndeKurz}}", KURZDATUM.format(ende));
        int tagIndex = 0;
        List<XWPFParagraph> entfernen = new ArrayList<>();
        for (XWPFParagraph paragraph : brief.getParagraphs()) {
            for (var wert : werte.entrySet()) {
                ersetzen(paragraph, wert.getKey(), wert.getValue());
            }
            Ag ag = tage.get(tagIndex == 0 ? Ag.Wochentag.MONTAG : Ag.Wochentag.FREITAG);
            ersetzen(paragraph, "{{AG Name}}", ag == null ? "Noch nicht zugewiesen" : ag.titel());
            if (paragraph.getText().contains("{{Material}}")) {
                if (ag == null || ag.material() == null || ag.material().isBlank()) {
                    entfernen.add(paragraph);
                } else {
                    ersetzen(paragraph, "{{Material}}", ag.material());
                }
                tagIndex++;
            }
        }
        entfernen.forEach(paragraph -> brief.removeBodyElement(brief.getPosOfParagraph(paragraph)));
        // Mehrzeilige Materialangaben benötigen Word-Zeilenumbrüche, keine Roh-Newlines in w:t.
        for (XWPFParagraph paragraph : brief.getParagraphs()) {
            NodeList texte = ((Element) paragraph.getCTP().getDomNode()).getElementsByTagNameNS(WORD_NS, "t");
            List<Node> knoten = new ArrayList<>();
            for (int i = 0; i < texte.getLength(); i++) {
                knoten.add(texte.item(i));
            }
            for (Node node : knoten) {
                String[] zeilen = text(node).split("\\R", -1);
                if (zeilen.length < 2) {
                    continue;
                }
                node.getFirstChild().setNodeValue(zeilen[0]);
                Node letztes = node;
                for (int i = 1; i < zeilen.length; i++) {
                    Node umbruch = node.getOwnerDocument().createElementNS(WORD_NS, "w:br");
                    Node zeile = node.cloneNode(true);
                    zeile.getFirstChild().setNodeValue(zeilen[i]);
                    node.getParentNode().insertBefore(umbruch, letztes.getNextSibling());
                    node.getParentNode().insertBefore(zeile, umbruch.getNextSibling());
                    letztes = zeile;
                }
            }
        }
    }

    /** Platzhalter können über mehrere Word-Runs mit verschiedenen Eigenschaften verteilt sein. */
    private void ersetzen(XWPFParagraph paragraph, String platzhalter, String wert) {
        NodeList texte = ((Element) paragraph.getCTP().getDomNode()).getElementsByTagNameNS(WORD_NS, "t");
        StringBuilder inhalt = new StringBuilder();
        for (int i = 0; i < texte.getLength(); i++) {
            inhalt.append(text(texte.item(i)));
        }
        // Von hinten ersetzen, damit sich die Offsets noch ausstehender Treffer nicht verschieben.
        for (int start = inhalt.lastIndexOf(platzhalter); start >= 0;
                start = inhalt.lastIndexOf(platzhalter, start - 1)) {
            int ende = start + platzhalter.length();
            int offset = 0;
            boolean erster = true;
            for (int i = 0; i < texte.getLength(); i++) {
                Node node = texte.item(i);
                String text = text(node);
                int von = offset;
                offset += text.length();
                if (offset <= start || von >= ende) {
                    continue;
                }
                String ersetzt = text.substring(0, Math.max(0, start - von))
                        + (erster ? wert : "") + text.substring(Math.min(text.length(), ende - von));
                // XMLBeans unterstützt setTextContent nicht auf allen DOM-Knoten.
                while (node.hasChildNodes()) {
                    node.removeChild(node.getFirstChild());
                }
                node.appendChild(node.getOwnerDocument().createTextNode(ersetzt));
                ((Element) node).setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve");
                erster = false;
            }
        }
    }

    private String text(Node node) {
        StringBuilder text = new StringBuilder();
        for (Node kind = node.getFirstChild(); kind != null; kind = kind.getNextSibling()) {
            text.append(kind.getNodeValue());
        }
        return text.toString();
    }

    public byte[] zuPdf(byte[] docx, int seitenAnzahl) {
        Path verzeichnis = null;
        Process prozess = null;
        try {
            verzeichnis = Files.createTempDirectory("ageinwahl-entdecker-");
            Path datei = verzeichnis.resolve("entdecker.docx");
            Files.write(datei, docx);
            Path profil = verzeichnis.resolve("profil");
            prozess = starteLibreOffice(datei, verzeichnis, profil);
            if (!prozess.waitFor(30, TimeUnit.SECONDS)) {
                throw new ExportFehler("Die PDF-Konvertierung wurde wegen einer Zeitüberschreitung abgebrochen.");
            }
            Path pdfDatei = verzeichnis.resolve("entdecker.pdf");
            if (prozess.exitValue() != 0 || !Files.isRegularFile(pdfDatei)) {
                throw new ExportFehler("LibreOffice konnte die Entdecker-Rückmeldezettel nicht in PDF umwandeln.");
            }
            byte[] pdf = Files.readAllBytes(pdfDatei);
            pruefePdf(pdf, seitenAnzahl);
            return pdf;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExportFehler("Die PDF-Konvertierung wurde unterbrochen.", ex);
        } catch (IOException ex) {
            throw new ExportFehler("Die PDF-Konvertierung ist fehlgeschlagen. Bitte LibreOffice installieren oder den Pfad mit -Dageinwahl.libreoffice angeben.", ex);
        } finally {
            if (prozess != null && prozess.isAlive()) {
                prozess.destroyForcibly();
            }
            if (verzeichnis != null) {
                try (var dateien = Files.walk(verzeichnis)) {
                    for (Path datei : dateien.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(datei);
                    }
                } catch (IOException ex) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Temporäre Exportdateien konnten nicht gelöscht werden.", ex);
                }
            }
        }
    }

    private Process starteLibreOffice(Path docx, Path verzeichnis, Path profil) throws IOException {
        String konfiguriert = System.getProperty("ageinwahl.libreoffice");
        List<String> kandidaten = new ArrayList<>();
        if (konfiguriert != null && !konfiguriert.isBlank()) {
            kandidaten.add(konfiguriert);
        } else if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            for (String variable : List.of("ProgramFiles", "ProgramFiles(x86)")) {
                String basis = System.getenv(variable);
                if (basis != null) {
                    kandidaten.add(Path.of(basis, "LibreOffice", "program", "soffice.exe").toString());
                }
            }
            kandidaten.add("soffice.exe");
        } else {
            kandidaten.addAll(List.of("libreoffice", "soffice"));
        }
        IOException fehler = null;
        for (String kandidat : kandidaten) {
            try {
                return new ProcessBuilder(kandidat, "-env:UserInstallation=" + profil.toUri(),
                        "--headless", "--convert-to", "pdf:writer_pdf_Export", "--outdir", verzeichnis.toString(), docx.toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException ex) {
                fehler = ex;
            }
        }
        throw fehler;
    }

    void pruefePdf(byte[] pdf, int seitenAnzahl) throws IOException {
        try (var dokument = Loader.loadPDF(pdf)) {
            for (var seite : dokument.getPages()) {
                for (var name : seite.getResources().getFontNames()) {
                    String schrift = seite.getResources().getFont(name).getName();
                    if (!schrift.contains("ComicSansMS")) {
                        throw new ExportFehler("Die Vorlage benötigt Comic Sans MS (normal und fett). Bitte diese Schrift installieren; LibreOffice hat eine Ersatzschrift verwendet.");
                    }
                }
            }
            if (dokument.getNumberOfPages() != seitenAnzahl) {
                throw new ExportFehler("Der Text passt mit der Formatierung der Vorlage nicht auf eine Seite je Kind. Bitte die Länge der Namen, AG-Titel und Materialangaben prüfen.");
            }
        }
    }
}
