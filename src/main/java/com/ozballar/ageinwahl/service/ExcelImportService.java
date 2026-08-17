package com.ozballar.ageinwahl.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.ErlaubterJahrgang;
import com.ozballar.ageinwahl.domain.Teilnehmer;

@Service
public class ExcelImportService {

    private static final String[] TEILNEHMER_KOPF = {"Vorname", "Name", "Klasse", "GT-Teilnahme"};
    private static final String[] AG_KOPF = {
            "Titel",
            "Beschreibung",
            "Kategorie",
            "Zeit",
            "Wochentag",
            "Verantwortlicher",
            "Ort",
            "Maximale Teilnehmerzahl",
            "Erlaubte Jahrgänge"
    };

    private final TeilnehmerService teilnehmerService;
    private final AgService agService;
    private final DataFormatter formatter = new DataFormatter(Locale.GERMANY);

    public ExcelImportService(TeilnehmerService teilnehmerService, AgService agService) {
        this.teilnehmerService = teilnehmerService;
        this.agService = agService;
    }

    @Transactional
    public int importiereTeilnehmer(MultipartFile datei) {
        pruefeDatei(datei);
        List<String> fehler = new ArrayList<>();
        List<Teilnehmer> teilnehmer = new ArrayList<>();
        Set<String> namenInDatei = new HashSet<>();

        try (Workbook workbook = workbook(datei)) {
            Sheet sheet = ersteTabelle(workbook);
            pruefeKopfzeile(sheet, TEILNEHMER_KOPF, fehler);

            for (int zeilenIndex = 1; zeilenIndex <= sheet.getLastRowNum(); zeilenIndex++) {
                Row row = sheet.getRow(zeilenIndex);
                if (istLeer(row, TEILNEHMER_KOPF.length)) {
                    continue;
                }

                int zeile = zeilenIndex + 1;
                String vorname = pflicht(wert(row, 0), "Vorname", zeile, fehler);
                String name = pflicht(wert(row, 1), "Name", zeile, fehler);
                String klasse = pflicht(wert(row, 2), "Klasse", zeile, fehler);
                Teilnehmer.GtTeilnahme gtTeilnahme = enumWert(wert(row, 3), Teilnehmer.GtTeilnahme.class, "GT-Teilnahme", zeile, fehler);

                String nameSchluessel = normalisiert(vorname) + "|" + normalisiert(name);
                if (!namenInDatei.add(nameSchluessel)) {
                    fehler.add("Zeile " + zeile + ": Vorname und Name kommen in der Datei mehrfach vor.");
                }
                teilnehmerService.findeNachVornameUndName(vorname, name)
                        .ifPresent(vorhanden -> fehler.add("Zeile " + zeile + ": " + vorname + " " + name + " ist bereits vorhanden."));

                try {
                    teilnehmer.add(new Teilnehmer(null, vorname, name, klasse, gtTeilnahme));
                } catch (IllegalArgumentException ex) {
                    fehler.add("Zeile " + zeile + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new ExcelImportException("Die Excel-Datei konnte nicht gelesen werden. Bitte eine .xlsx-Datei mit der Vorlage verwenden.");
        }

        pruefeImportierbar(fehler, teilnehmer.size(), "Teilnehmer");
        teilnehmer.forEach(teilnehmerService::speichern);
        return teilnehmer.size();
    }

    @Transactional
    public int importiereAgs(MultipartFile datei) {
        pruefeDatei(datei);
        List<String> fehler = new ArrayList<>();
        List<Ag> ags = new ArrayList<>();
        Set<String> titelInDatei = new HashSet<>();

        try (Workbook workbook = workbook(datei)) {
            Sheet sheet = ersteTabelle(workbook);
            pruefeKopfzeile(sheet, AG_KOPF, fehler);

            for (int zeilenIndex = 1; zeilenIndex <= sheet.getLastRowNum(); zeilenIndex++) {
                Row row = sheet.getRow(zeilenIndex);
                if (istLeer(row, AG_KOPF.length)) {
                    continue;
                }

                int zeile = zeilenIndex + 1;
                String titel = pflicht(wert(row, 0), "Titel", zeile, fehler);
                String beschreibung = wert(row, 1);
                Ag.Kategorie kategorie = enumWert(wert(row, 2), Ag.Kategorie.class, "Kategorie", zeile, fehler);
                Ag.Zeit zeit = enumWert(wert(row, 3), Ag.Zeit.class, "Zeit", zeile, fehler);
                Ag.Wochentag wochentag = enumWert(wert(row, 4), Ag.Wochentag.class, "Wochentag", zeile, fehler);
                String verantwortlicher = wert(row, 5);
                String ort = wert(row, 6);
                Integer maximaleTeilnehmerzahl = zahlOderNull(wert(row, 7), "Maximale Teilnehmerzahl", zeile, fehler);
                List<ErlaubterJahrgang> erlaubteJahrgaenge = jahrgaenge(wert(row, 8), zeile, fehler);

                if (titel != null && !titelInDatei.add(normalisiert(titel))) {
                    fehler.add("Zeile " + zeile + ": Der AG-Titel kommt in der Datei mehrfach vor.");
                }
                if (titel != null) {
                    agService.findeNachTitel(titel)
                            .ifPresent(vorhanden -> fehler.add("Zeile " + zeile + ": Eine AG mit dem Titel \"" + titel + "\" ist bereits vorhanden."));
                }
                if (maximaleTeilnehmerzahl != null && maximaleTeilnehmerzahl < 1) {
                    fehler.add("Zeile " + zeile + ": Maximale Teilnehmerzahl muss mindestens 1 sein.");
                }

                try {
                    ags.add(new Ag(null, wochentag, zeit, kategorie, titel, beschreibung, verantwortlicher, ort, maximaleTeilnehmerzahl, erlaubteJahrgaenge));
                } catch (IllegalArgumentException ex) {
                    fehler.add("Zeile " + zeile + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new ExcelImportException("Die Excel-Datei konnte nicht gelesen werden. Bitte eine .xlsx-Datei mit der Vorlage verwenden.");
        }

        pruefeImportierbar(fehler, ags.size(), "AGs");
        ags.forEach(agService::speichern);
        return ags.size();
    }

    public byte[] teilnehmerVorlage() {
        return vorlage("Teilnehmer", TEILNEHMER_KOPF, List.of(
                List.of("Mila", "Muster", "2a", "Ja"),
                List.of("Ben", "Beispiel", "3b", "Nein")
        ));
    }

    public byte[] agVorlage() {
        return vorlage("AGs", AG_KOPF, List.of(
                List.of("Forscher-AG", "Wir experimentieren und entdecken spannende Dinge.", "AG", "NACHMITTAG", "MONTAG", "Frau Beispiel", "Raum 12", "12", "1,2,3"),
                List.of("Theater entdecken", "Wir spielen Rollen und erfinden kleine Szenen.", "ENTDECKERANGEBOT", "VORMITTAG", "DIENSTAG", "Herr Muster", "Aula", "10", "2"),
                List.of("Jahres-Chor", "Wir singen das ganze Schuljahr gemeinsam und bereiten Auftritte vor.", "JAHRES_AG", "NACHMITTAG", "MITTWOCH", "Frau Musik", "Musikraum", "18", "3,4")
        ));
    }

    private void pruefeDatei(MultipartFile datei) {
        if (datei == null || datei.isEmpty()) {
            throw new ExcelImportException("Bitte wählen Sie eine Excel-Datei im Format .xlsx aus.");
        }
        String name = datei.getOriginalFilename();
        if (name != null && !name.isBlank() && !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ExcelImportException("Die Datei muss eine .xlsx-Datei sein. Bitte die Vorlage herunterladen und ausfüllen.");
        }
    }

    private Workbook workbook(MultipartFile datei) throws IOException {
        try (InputStream inputStream = datei.getInputStream()) {
            return new XSSFWorkbook(inputStream);
        } catch (RuntimeException ex) {
            throw new ExcelImportException("Die Excel-Datei konnte nicht gelesen werden. Bitte eine .xlsx-Datei mit der Vorlage verwenden.");
        }
    }

    private Sheet ersteTabelle(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new ExcelImportException("Die Excel-Datei enthält kein Tabellenblatt.");
        }
        return workbook.getSheetAt(0);
    }

    private void pruefeKopfzeile(Sheet sheet, String[] erwarteteSpalten, List<String> fehler) {
        Row kopf = sheet.getRow(0);
        for (int i = 0; i < erwarteteSpalten.length; i++) {
            String wert = wert(kopf, i);
            if (!normalisiert(wert).equals(normalisiert(erwarteteSpalten[i]))) {
                fehler.add("Kopfzeile: Spalte " + (i + 1) + " muss \"" + erwarteteSpalten[i] + "\" heißen.");
            }
        }
    }

    private boolean istLeer(Row row, int spalten) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < spalten; i++) {
            if (!wert(row, i).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String wert(Row row, int index) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).strip();
    }

    private String pflicht(String wert, String feld, int zeile, List<String> fehler) {
        if (wert == null || wert.isBlank()) {
            fehler.add("Zeile " + zeile + ": " + feld + " darf nicht leer sein.");
            return null;
        }
        return wert.strip();
    }

    private Integer zahlOderNull(String wert, String feld, int zeile, List<String> fehler) {
        if (wert == null || wert.isBlank()) {
            return null;
        }
        String normalisiert = wert.strip().replace(".", "").replace(',', '.');
        try {
            double zahl = Double.parseDouble(normalisiert);
            if (zahl != Math.rint(zahl)) {
                fehler.add("Zeile " + zeile + ": " + feld + " muss eine ganze Zahl sein.");
                return null;
            }
            return (int) zahl;
        } catch (NumberFormatException ex) {
            fehler.add("Zeile " + zeile + ": " + feld + " muss eine Zahl sein.");
            return null;
        }
    }

    private <E extends Enum<E>> E enumWert(String wert, Class<E> enumTyp, String feld, int zeile, List<String> fehler) {
        if (wert == null || wert.isBlank()) {
            fehler.add("Zeile " + zeile + ": " + feld + " darf nicht leer sein.");
            return null;
        }
        String kandidat = wert.strip().toUpperCase(Locale.ROOT)
                .replace('Ä', 'A')
                .replace('Ö', 'O')
                .replace('Ü', 'U')
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return Enum.valueOf(enumTyp, kandidat);
        } catch (IllegalArgumentException ex) {
            String erlaubteWerte = String.join(", ", StreamSupport.stream(List.of(enumTyp.getEnumConstants()).spliterator(), false)
                    .map(Enum::name)
                    .toList());
            fehler.add("Zeile " + zeile + ": " + feld + " hat den ungültigen Wert \"" + wert + "\". Erlaubt sind: " + erlaubteWerte + ".");
            return null;
        }
    }

    private List<ErlaubterJahrgang> jahrgaenge(String wert, int zeile, List<String> fehler) {
        if (wert == null || wert.isBlank()) {
            return List.of();
        }

        List<ErlaubterJahrgang> jahrgaenge = new ArrayList<>();
        Set<Integer> eindeutig = new HashSet<>();
        for (String teil : wert.split("[,; ]+")) {
            if (teil.isBlank()) {
                continue;
            }
            Integer jahrgang = zahlOderNull(teil, "Erlaubte Jahrgänge", zeile, fehler);
            if (jahrgang == null) {
                continue;
            }
            if (jahrgang < 1 || jahrgang > 4) {
                fehler.add("Zeile " + zeile + ": Erlaubte Jahrgänge dürfen nur Werte von 1 bis 4 enthalten.");
                continue;
            }
            if (eindeutig.add(jahrgang)) {
                jahrgaenge.add(new ErlaubterJahrgang(jahrgang));
            }
        }
        return jahrgaenge;
    }

    private void pruefeImportierbar(List<String> fehler, int anzahl, String bezeichnung) {
        if (anzahl == 0) {
            fehler.add("Es wurden keine " + bezeichnung + " gefunden. Bitte mindestens eine Datenzeile ausfüllen.");
        }
        if (!fehler.isEmpty()) {
            throw new ExcelImportException("Import nicht möglich: " + String.join(" ", fehler));
        }
    }

    private byte[] vorlage(String tabellenName, String[] kopf, List<List<String>> beispiele) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(tabellenName);
            Font fett = workbook.createFont();
            fett.setBold(true);
            CellStyle kopfStil = workbook.createCellStyle();
            kopfStil.setFont(fett);

            Row kopfZeile = sheet.createRow(0);
            for (int i = 0; i < kopf.length; i++) {
                Cell cell = kopfZeile.createCell(i);
                cell.setCellValue(kopf[i]);
                cell.setCellStyle(kopfStil);
            }

            for (int i = 0; i < beispiele.size(); i++) {
                Row row = sheet.createRow(i + 1);
                List<String> beispiel = beispiele.get(i);
                for (int j = 0; j < beispiel.size(); j++) {
                    row.createCell(j).setCellValue(beispiel.get(j));
                }
            }

            for (int i = 0; i < kopf.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new ExcelImportException("Die Excel-Vorlage konnte nicht erstellt werden.");
        }
    }

    private String normalisiert(String wert) {
        return wert == null ? "" : wert.strip().toLowerCase(Locale.ROOT);
    }
}
