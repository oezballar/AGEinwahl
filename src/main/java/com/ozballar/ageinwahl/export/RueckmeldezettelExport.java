package com.ozballar.ageinwahl.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.springframework.core.io.ClassPathResource;
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
public class RueckmeldezettelExport extends AbstractPdfExport {

    private static final String VORLAGE = "templates/exports/Rueckmeldezettel.docx";
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*(Klasse|Vorname|Nachname|AG Name|Material)\\s*\\}\\}");
    private static final Duration KONVERTIER_TIMEOUT = Duration.ofSeconds(30);

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final EinwahlVormittagsAGService einwahlVormittagsAGService;
    private final EinwahlAGService einwahlAGService;

    public RueckmeldezettelExport(
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
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        Map<Integer, List<BelegteAg>> belegteAgs = belegteAgsNachTeilnehmer(agNachTitel);
        List<Teilnehmer> teilnehmer = listeAus(teilnehmerService.findeAlle()).stream()
                .sorted(Comparator.comparing(Teilnehmer::klasse)
                        .thenComparing(Teilnehmer::name)
                        .thenComparing(Teilnehmer::vorname)
                        .thenComparing(Teilnehmer::id))
                .toList();

        if (teilnehmer.isEmpty()) {
            return new PdfDocument(List.of(new PdfPage(List.of(new PdfText(48, 790, 14, "Keine Teilnehmer vorhanden.")))),
                    PAGE_WIDTH, PAGE_HEIGHT).render();
        }

        return erstelleDokument(teilnehmer, belegteAgs);
    }

    private byte[] erstelleDokument(List<Teilnehmer> teilnehmer, Map<Integer, List<BelegteAg>> belegteAgs) {
        Path arbeitsverzeichnis = null;
        Path docx = null;
        Path pdf = null;
        try {
            arbeitsverzeichnis = Files.createTempDirectory("ageinwahl-rueckmeldezettel-");
            docx = arbeitsverzeichnis.resolve("Rueckmeldezettel.docx");
            pdf = arbeitsverzeichnis.resolve("Rueckmeldezettel.pdf");
            try (InputStream ersteVorlage = new ClassPathResource(VORLAGE).getInputStream();
                 XWPFDocument gesamt = new XWPFDocument(ersteVorlage)) {
                while (!gesamt.getBodyElements().isEmpty()) {
                    gesamt.removeBodyElement(0);
                }
                for (int index = 0; index < teilnehmer.size(); index++) {
                    Teilnehmer kind = teilnehmer.get(index);
                    try (InputStream vorlage = new ClassPathResource(VORLAGE).getInputStream();
                         XWPFDocument dokument = new XWPFDocument(vorlage)) {
                        entferneUnpassendeSeite(dokument, kind);
                        ersetzeVariablen(dokument, kind, belegteAgs.getOrDefault(kind.id(), List.of()));
                        if (index > 0) {
                            gesamt.createParagraph().createRun().addBreak(BreakType.PAGE);
                        }
                        uebernehmeKoerper(gesamt, dokument);
                    }
                }
                try (var output = Files.newOutputStream(docx)) {
                    gesamt.write(output);
                }
            }
            konvertiereZuPdf(docx, arbeitsverzeichnis);
            return Files.readAllBytes(pdf);
        } catch (IOException ex) {
            throw new IllegalStateException("Die Rückmeldezettel konnten nicht erstellt werden.", ex);
        } finally {
            try {
                if (pdf != null) {
                    Files.deleteIfExists(pdf);
                }
                if (docx != null) {
                    Files.deleteIfExists(docx);
                }
                if (arbeitsverzeichnis != null) {
                    try (var dateien = Files.walk(arbeitsverzeichnis)) {
                        dateien.sorted(Comparator.reverseOrder()).forEach(this::loescheTemporareDatei);
                    }
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Die temporären Rückmeldezettel konnten nicht gelöscht werden.", ex);
            }
        }
    }

    private void loescheTemporareDatei(Path datei) {
        try {
            Files.deleteIfExists(datei);
        } catch (IOException ex) {
            throw new IllegalStateException("Die temporären Rückmeldezettel konnten nicht gelöscht werden.", ex);
        }
    }

    private void uebernehmeKoerper(XWPFDocument ziel, XWPFDocument quelle) {
        for (var element : quelle.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                ziel.getDocument().getBody().addNewP().set((CTP) paragraph.getCTP().copy());
            } else if (element instanceof XWPFTable table) {
                ziel.getDocument().getBody().addNewTbl().set((CTTbl) table.getCTTbl().copy());
            }
        }
    }

    private void entferneUnpassendeSeite(XWPFDocument dokument, Teilnehmer kind) {
        List<org.apache.poi.xwpf.usermodel.IBodyElement> elemente = dokument.getBodyElements();
        int seitenumbruch = -1;
        for (int index = 0; index < elemente.size(); index++) {
            if (elemente.get(index) instanceof XWPFParagraph paragraph
                    && paragraph.getCTP().xmlText().contains("w:type=\"page\"")) {
                seitenumbruch = index;
                paragraph.getRuns().forEach(run -> run.getCTR().getBrList().clear());
                break;
            }
        }
        if (seitenumbruch < 0) {
            return;
        }

        boolean ersteSeite = jahrgang(kind) <= 2;
        if (ersteSeite) {
            for (int index = elemente.size() - 1; index > seitenumbruch; index--) {
                dokument.removeBodyElement(index);
            }
        } else {
            for (int index = seitenumbruch; index >= 0; index--) {
                dokument.removeBodyElement(index);
            }
        }
    }

    private void ersetzeVariablen(XWPFDocument dokument, Teilnehmer kind, List<BelegteAg> belegteAgs) {
        Map<Ag.Wochentag, BelegteAg> agNachTag = belegteAgs.stream()
                .collect(Collectors.toMap(BelegteAg::wochentag, ag -> ag, (erste, zweite) ->
                        new BelegteAg(erste.wochentag(), erste.titel() + " / " + zweite.titel(),
                                erste.material() + " / " + zweite.material())));
        List<Ag.Wochentag> tage = jahrgang(kind) <= 2
                ? List.of(Ag.Wochentag.MONTAG, Ag.Wochentag.DIENSTAG, Ag.Wochentag.MITTWOCH, Ag.Wochentag.DONNERSTAG, Ag.Wochentag.FREITAG)
                : List.of(Ag.Wochentag.DIENSTAG, Ag.Wochentag.MITTWOCH);
        Map<String, String> einfacheVariablen = Map.of(
                "Klasse", kind.klasse(),
                "Vorname", kind.vorname(),
                "Nachname", kind.name()
        );
        int[] tagIndex = {0};
        for (XWPFParagraph paragraph : dokument.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                String text = run.getText(0);
                if (text == null) {
                    continue;
                }
                text = text.replace("-lich", "‑lich");
                Matcher matcher = VARIABLE.matcher(text);
                StringBuffer ersetzt = new StringBuffer();
                while (matcher.find()) {
                    String variable = matcher.group(1);
                    String wert = einfacheVariablen.get(variable);
                    if ("AG Name".equals(variable) || "Material".equals(variable)) {
                        BelegteAg ag = agNachTag.get(tage.get(Math.min(tagIndex[0], tage.size() - 1)));
                        wert = ag == null ? "-" : "AG Name".equals(variable) ? ag.titel() : ag.material();
                        if ("Material".equals(variable)) {
                            tagIndex[0]++;
                        }
                    }
                    matcher.appendReplacement(ersetzt, Matcher.quoteReplacement(wert == null ? "-" : wert));
                }
                matcher.appendTail(ersetzt);
                run.setText(ersetzt.toString(), 0);
            }
        }
    }

    private void konvertiereZuPdf(Path docx, Path arbeitsverzeichnis) throws IOException {
        String kommando = System.getProperty("ageinwahl.libreoffice", "libreoffice");
        Path profil = arbeitsverzeichnis.resolve("libreoffice-profile");
        Files.createDirectories(profil);
        Process prozess = new ProcessBuilder(kommando, "-env:UserInstallation=" + profil.toUri(), "--headless", "--convert-to", "pdf:writer_pdf_Export",
                "--outdir", arbeitsverzeichnis.toString(), docx.toString())
                .redirectErrorStream(true)
                .start();
        try {
            if (!prozess.waitFor(KONVERTIER_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                prozess.destroyForcibly();
                throw new IOException("Die PDF-Konvertierung wurde wegen einer Zeitüberschreitung abgebrochen.");
            }
            if (prozess.exitValue() != 0) {
                throw new IOException("LibreOffice konnte den Rückmeldezettel nicht in PDF umwandeln.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Die PDF-Konvertierung wurde unterbrochen.", ex);
        }
    }

    private Map<Integer, List<BelegteAg>> belegteAgsNachTeilnehmer(Map<String, Ag> agNachTitel) {
        Map<Integer, List<BelegteAg>> ergebnis = new HashMap<>();
        listeAus(einwahlEntdeckerangebotService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeHinzu(ergebnis, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));
        listeAus(einwahlVormittagsAGService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeHinzu(ergebnis, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));
        listeAus(einwahlAGService.findeAlle()).stream()
                .filter(einwahl -> Boolean.TRUE.equals(einwahl.zugewiesen()))
                .forEach(einwahl -> fuegeHinzu(ergebnis, agNachTitel, einwahl.teilnehmerId(), einwahl.agTitel()));
        ergebnis.values().forEach(ags -> ags.sort(Comparator.comparing(BelegteAg::wochentag).thenComparing(BelegteAg::titel)));
        return ergebnis;
    }

    private void fuegeHinzu(Map<Integer, List<BelegteAg>> ergebnis, Map<String, Ag> agNachTitel, Integer teilnehmerId, String titel) {
        Ag ag = agNachTitel.get(titel);
        if (ag != null) {
            ergebnis.computeIfAbsent(teilnehmerId, id -> new java.util.ArrayList<>())
                    .add(new BelegteAg(ag.wochentag(), ag.titel(), ag.material() == null || ag.material().isBlank() ? "-" : ag.material()));
        }
    }

    private int jahrgang(Teilnehmer kind) {
        return Character.getNumericValue(kind.klasse().charAt(0));
    }

    private record BelegteAg(Ag.Wochentag wochentag, String titel, String material) {
    }
}
