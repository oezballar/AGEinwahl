package com.ozballar.ageinwahl.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;
import java.util.stream.Collectors;

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
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.ErlaubterJahrgang;
import com.ozballar.ageinwahl.domain.Teilnehmer;

@Service
public class ExcelImportService {

    private static final String[] TEILNEHMER_KOPF = {"Vorname", "Name", "Klasse", "GT-Teilnahme"};
    private static final String[] AG_KOPF = {
            "Titel",
            "Beschreibung",
            "Material",
            "Kategorie",
            "Zeit",
            "Wochentag",
            "Verantwortlicher",
            "Ort",
            "Maximale Teilnehmerzahl",
            "Erlaubte Jahrgänge"
    };
    private static final String[] BESUCHTE_AG_KOPF = {"Vorname", "Name", "Klasse", "Bereits besuchte AGs"};

    private final TeilnehmerService teilnehmerService;
    private final AgService agService;
    private final EinwahlEntdeckerangebotService einwahlEntdeckerangebotService;
    private final BesuchteAgService besuchteAgService;
    private final DataFormatter formatter = new DataFormatter(Locale.GERMANY);

    public ExcelImportService(
            TeilnehmerService teilnehmerService,
            AgService agService,
            EinwahlEntdeckerangebotService einwahlEntdeckerangebotService,
            BesuchteAgService besuchteAgService
    ) {
        this.teilnehmerService = teilnehmerService;
        this.agService = agService;
        this.einwahlEntdeckerangebotService = einwahlEntdeckerangebotService;
        this.besuchteAgService = besuchteAgService;
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
                String material = wert(row, 2);
                Ag.Kategorie kategorie = enumWert(wert(row, 3), Ag.Kategorie.class, "Kategorie", zeile, fehler);
                Ag.Zeit zeit = enumWert(wert(row, 4), Ag.Zeit.class, "Zeit", zeile, fehler);
                Ag.Wochentag wochentag = enumWert(wert(row, 5), Ag.Wochentag.class, "Wochentag", zeile, fehler);
                String verantwortlicher = wert(row, 6);
                String ort = wert(row, 7);
                Integer maximaleTeilnehmerzahl = zahlOderNull(wert(row, 8), "Maximale Teilnehmerzahl", zeile, fehler);
                List<ErlaubterJahrgang> erlaubteJahrgaenge = jahrgaenge(wert(row, 9), zeile, fehler);

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
                    ags.add(new Ag(null, wochentag, zeit, kategorie, titel, beschreibung, material, verantwortlicher, ort, maximaleTeilnehmerzahl, erlaubteJahrgaenge));
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
                List.of("Forscher-AG", "Wir experimentieren und entdecken spannende Dinge.", "Stifte, Becher, Wasser", "AG", "NACHMITTAG", "MONTAG", "Frau Beispiel", "Raum 12", "12", "1,2,3"),
                List.of("Theater entdecken", "Wir spielen Rollen und erfinden kleine Szenen.", "Kostüme und Requisiten", "ENTDECKERANGEBOT", "VORMITTAG", "DIENSTAG", "Herr Muster", "Aula", "10", "2"),
                List.of("Jahres-Chor", "Wir singen das ganze Schuljahr gemeinsam und bereiten Auftritte vor.", "Notenständer und Liederbücher", "JAHRES_AG", "NACHMITTAG", "MITTWOCH", "Frau Musik", "Musikraum", "18", "3,4")
        ));
    }

    @Transactional
    public int importiereBesuchteAgs(MultipartFile datei) {
        pruefeDatei(datei);
        List<String> fehler = new ArrayList<>();
        Map<Integer, Set<String>> besuchteAgs = new java.util.HashMap<>();
        Set<String> namenInDatei = new HashSet<>();

        try (Workbook workbook = workbook(datei)) {
            Sheet sheet = ersteTabelle(workbook);
            pruefeKopfzeile(sheet, BESUCHTE_AG_KOPF, fehler);
            for (int zeilenIndex = 1; zeilenIndex <= sheet.getLastRowNum(); zeilenIndex++) {
                Row row = sheet.getRow(zeilenIndex);
                if (istLeer(row, BESUCHTE_AG_KOPF.length)) {
                    continue;
                }
                int zeile = zeilenIndex + 1;
                String vorname = pflicht(wert(row, 0), "Vorname", zeile, fehler);
                String name = pflicht(wert(row, 1), "Name", zeile, fehler);
                pflicht(wert(row, 2), "Klasse", zeile, fehler);
                String nameSchluessel = normalisiert(vorname) + "|" + normalisiert(name);
                if (!namenInDatei.add(nameSchluessel)) {
                    fehler.add("Zeile " + zeile + ": Vorname und Name kommen in der Datei mehrfach vor.");
                }

                Teilnehmer teilnehmer = vorname == null || name == null ? null
                        : teilnehmerService.findeNachVornameUndName(vorname, name).orElse(null);
                if (teilnehmer == null && vorname != null && name != null) {
                    fehler.add("Zeile " + zeile + ": Teilnehmer " + vorname + " " + name + " ist nicht vorhanden.");
                    continue;
                }

                Set<String> agTitelDerZeile = new HashSet<>();
                for (String agTitelKandidat : wert(row, 3).split(";")) {
                    String agTitel = agTitelKandidat.strip();
                    if (agTitel.isBlank()) {
                        continue;
                    }
                    if (!agTitelDerZeile.add(agTitel)) {
                        fehler.add("Zeile " + zeile + ": Die AG " + agTitel + " ist doppelt eingetragen.");
                    }
                    if (agService.findeNachTitel(agTitel).isEmpty()) {
                        fehler.add("Zeile " + zeile + ": Die AG " + agTitel + " ist nicht vorhanden.");
                    }
                }
                besuchteAgs.put(teilnehmer.id(), agTitelDerZeile);
            }
        } catch (IOException ex) {
            throw new ExcelImportException("Die Excel-Datei konnte nicht gelesen werden. Bitte eine .xlsx-Datei mit der Vorlage verwenden.");
        }

        pruefeImportierbar(fehler, besuchteAgs.size(), "Teilnehmer");
        besuchteAgService.ersetzeAlle(besuchteAgs);
        return besuchteAgs.size();
    }

    public byte[] besuchteAgsVorlage() {
        return vorlage("Besuchte AGs", BESUCHTE_AG_KOPF, List.of(
                List.of("Mila", "Muster", "2a", "Fußball; Chor"),
                List.of("Ben", "Beispiel", "3b", "")
        ));
    }

    public byte[] exportiereEntdeckerangebote() {
        List<Ag> entdeckerangebote = StreamSupport.stream(agService.findeAlle().spliterator(), false)
                .filter(ag -> ag.kategorie() == Ag.Kategorie.ENTDECKERANGEBOT)
                .sorted(java.util.Comparator.comparing(Ag::titel))
                .toList();
        List<EinwahlEntdeckerangebot> einwahlen = StreamSupport.stream(einwahlEntdeckerangebotService.findeAlle().spliterator(), false)
                .filter(einwahl -> einwahl.auswahl() == EinwahlEntdeckerangebot.Auswahl.JA)
                .toList();
        Map<Integer, Teilnehmer> teilnehmerNachId = StreamSupport.stream(teilnehmerService.findeAlle().spliterator(), false)
                .collect(Collectors.toMap(Teilnehmer::id, teilnehmer -> teilnehmer));
        List<List<Teilnehmer>> interessentenNachAngebot = entdeckerangebote.stream()
                .map(angebot -> einwahlen.stream()
                        .filter(einwahl -> einwahl.agTitel().equals(angebot.titel()))
                        .map(einwahl -> teilnehmerNachId.get(einwahl.teilnehmerId()))
                        .filter(java.util.Objects::nonNull)
                        .sorted(java.util.Comparator.comparing(Teilnehmer::name)
                                .thenComparing(Teilnehmer::vorname)
                                .thenComparing(Teilnehmer::id))
                        .toList())
                .toList();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Entdeckerangebote");
            Font fett = workbook.createFont();
            fett.setBold(true);
            CellStyle kopfStil = workbook.createCellStyle();
            kopfStil.setFont(fett);
            CellStyle listeStil = workbook.createCellStyle();
            listeStil.setWrapText(true);
            listeStil.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

            Row kopf = sheet.createRow(0);
            for (int index = 0; index < entdeckerangebote.size(); index++) {
                Cell angebotsCell = kopf.createCell(index);
                angebotsCell.setCellValue(entdeckerangebote.get(index).titel());
                angebotsCell.setCellStyle(kopfStil);
                sheet.setColumnWidth(index, 32 * 256);
            }

            int maximaleInteressenten = interessentenNachAngebot.stream()
                    .mapToInt(List::size)
                    .max()
                    .orElse(0);
            for (int zeilenIndex = 0; zeilenIndex < maximaleInteressenten; zeilenIndex++) {
                Row row = sheet.createRow(zeilenIndex + 1);
                for (int angebotIndex = 0; angebotIndex < interessentenNachAngebot.size(); angebotIndex++) {
                    List<Teilnehmer> interessenten = interessentenNachAngebot.get(angebotIndex);
                    if (zeilenIndex < interessenten.size()) {
                        Teilnehmer kind = interessenten.get(zeilenIndex);
                        Cell cell = row.createCell(angebotIndex);
                        cell.setCellValue(kind.vorname() + " " + kind.name() + " (" + kind.klasse() + ")");
                        cell.setCellStyle(listeStil);
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new ExcelImportException("Der Export der Entdeckerangebote konnte nicht erstellt werden.");
        }
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
