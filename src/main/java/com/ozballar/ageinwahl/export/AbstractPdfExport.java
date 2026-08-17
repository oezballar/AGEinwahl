package com.ozballar.ageinwahl.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

abstract class AbstractPdfExport {

    static final Charset PDF_CHARSET = Charset.forName("windows-1252");
    static final int PAGE_WIDTH = 595;
    static final int PAGE_HEIGHT = 842;
    static final int A4_LANDSCAPE_WIDTH = 842;
    static final int A4_LANDSCAPE_HEIGHT = 595;

    void addText(List<PdfElement> elemente, int x, int y, int fontSize, String text) {
        elemente.add(new PdfText(x, y, fontSize, text, false));
    }

    void addBoldText(List<PdfElement> elemente, int x, int y, int fontSize, String text) {
        elemente.add(new PdfText(x, y, fontSize, text, true));
    }

    int addWrappedText(List<PdfElement> elemente, int x, int y, int fontSize, int maxWidth, String text) {
        return addWrappedText(elemente, x, y, fontSize, maxWidth, text, Integer.MAX_VALUE);
    }

    List<String> wrapLines(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : Objects.toString(text, "").split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            if (!line.isEmpty()) {
                line.append(" ");
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    int addWrappedText(List<PdfElement> elemente, int x, int y, int fontSize, int maxWidth, String text, int maxLines) {
        int maxChars = Math.max(18, maxWidth / Math.max(5, fontSize / 2));
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : Objects.toString(text, "").split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                if (lines >= maxLines) {
                    return y;
                }
                addText(elemente, x, y, fontSize, line.toString());
                y -= fontSize + 4;
                lines++;
                line = new StringBuilder();
            }
            if (!line.isEmpty()) {
                line.append(" ");
            }
            line.append(word);
        }
        if (!line.isEmpty() && lines < maxLines) {
            addText(elemente, x, y, fontSize, line.toString());
            y -= fontSize + 4;
        }
        return y;
    }

    void addLine(List<PdfElement> elemente, int x1, int y1, int x2, int y2) {
        elemente.add(new PdfLine(x1, y1, x2, y2));
    }

    void addRect(List<PdfElement> elemente, int x, int y, int width, int height) {
        elemente.add(new PdfRect(x, y, width, height));
    }

    void addBox(List<PdfElement> elemente, int x, int y, int size) {
        addRect(elemente, x, y, size, size);
    }

    <T> List<T> listeAus(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }

    byte[] zipMitEinzelseiten(PdfDocument dokument, String dateibasis) {
        return zipMitEinzelseiten(dokument, dateibasis, List.of());
    }

    byte[] zipMitEinzelseiten(PdfDocument dokument, String dateibasis, List<String> seitenTitel) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            schreibeZipEintrag(zip, dateibasis + ".pdf", dokument.render());
            for (int seite = 0; seite < dokument.seitenAnzahl(); seite++) {
                String einzelDateibasis = seite < seitenTitel.size()
                        ? dateibasis + "-" + dateinamensteil(seitenTitel.get(seite))
                        : String.format("%s-seite-%03d", dateibasis, seite + 1);
                String dateiname = einzelDateibasis + ".pdf";
                schreibeZipEintrag(zip, dateiname, dokument.einzelSeite(seite).render());
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("ZIP-Export konnte nicht erstellt werden.", exception);
        }
    }

    byte[] zipMitDokumenten(
            PdfDocument gesamtDokument,
            String dateibasis,
            List<String> dokumentTitel,
            List<PdfDocument> einzelDokumente
    ) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            schreibeZipEintrag(zip, dateibasis + ".pdf", gesamtDokument.render());
            for (int index = 0; index < einzelDokumente.size(); index++) {
                String dateiname = dateibasis + "-" + dateinamensteil(dokumentTitel.get(index)) + ".pdf";
                schreibeZipEintrag(zip, dateiname, einzelDokumente.get(index).render());
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("ZIP-Export konnte nicht erstellt werden.", exception);
        }
    }

    private void schreibeZipEintrag(ZipOutputStream zip, String dateiname, byte[] inhalt) throws IOException {
        zip.putNextEntry(new ZipEntry(dateiname));
        zip.write(inhalt);
        zip.closeEntry();
    }

    private String dateinamensteil(String text) {
        String bereinigt = Objects.toString(text, "").replaceAll("[^A-Za-z0-9]", "_");
        return bereinigt.isBlank() ? "ohne_titel" : bereinigt;
    }

    String wochentagText(com.ozballar.ageinwahl.domain.Ag.Wochentag wochentag) {
        return switch (wochentag) {
            case MONTAG -> "Montag";
            case DIENSTAG -> "Dienstag";
            case MITTWOCH -> "Mittwoch";
            case DONNERSTAG -> "Donnerstag";
            case FREITAG -> "Freitag";
        };
    }

    record PdfPage(List<PdfElement> elemente) {
    }

    interface PdfElement {
    }

    record PdfText(int x, int y, int fontSize, String text, boolean bold) implements PdfElement {

        PdfText(int x, int y, int fontSize, String text) {
            this(x, y, fontSize, text, false);
        }
    }

    record PdfLine(int x1, int y1, int x2, int y2) implements PdfElement {
    }

    record PdfRect(int x, int y, int width, int height) implements PdfElement {
    }

    static class PdfDocument {

        private final List<PdfPage> pages;
        private final int pageWidth;
        private final int pageHeight;

        PdfDocument(List<PdfPage> pages, int pageWidth, int pageHeight) {
            this.pages = pages.isEmpty() ? List.of(new PdfPage(List.of(new PdfText(48, pageHeight - 52, 14, "Keine Daten vorhanden.", false)))) : pages;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }

        int seitenAnzahl() {
            return pages.size();
        }

        PdfDocument einzelSeite(int index) {
            return new PdfDocument(List.of(pages.get(index)), pageWidth, pageHeight);
        }

        byte[] render() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            write(out, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");

            int pageCount = pages.size();
            int pagesObject = 2;
            int fontObject = 3;
            int boldFontObject = 4;
            int firstPageObject = 5;
            int firstContentObject = firstPageObject + pageCount;
            int objectCount = 4 + pageCount * 2;

            writeObject(out, offsets, 1, "<< /Type /Catalog /Pages " + pagesObject + " 0 R >>");

            String kids = java.util.stream.IntStream.range(0, pageCount)
                    .mapToObj(index -> (firstPageObject + index) + " 0 R")
                    .collect(Collectors.joining(" "));
            writeObject(out, offsets, pagesObject, "<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");
            writeObject(out, offsets, fontObject, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
            writeObject(out, offsets, boldFontObject, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");

            for (int i = 0; i < pageCount; i++) {
                int pageObject = firstPageObject + i;
                int contentObject = firstContentObject + i;
                writeObject(out, offsets, pageObject, "<< /Type /Page /Parent " + pagesObject + " 0 R /MediaBox [0 0 "
                        + pageWidth + " " + pageHeight + "] /Resources << /Font << /F1 " + fontObject
                        + " 0 R /F2 " + boldFontObject + " 0 R >> >> /Contents " + contentObject + " 0 R >>");
            }

            for (int i = 0; i < pageCount; i++) {
                byte[] stream = contentStream(pages.get(i));
                offsets.add(out.size());
                write(out, (firstContentObject + i) + " 0 obj\n");
                write(out, "<< /Length " + stream.length + " >>\nstream\n");
                out.writeBytes(stream);
                write(out, "\nendstream\nendobj\n");
            }

            int xrefOffset = out.size();
            write(out, "xref\n0 " + (objectCount + 1) + "\n");
            write(out, "0000000000 65535 f \n");
            for (Integer offset : offsets) {
                write(out, String.format("%010d 00000 n \n", offset));
            }
            write(out, "trailer\n<< /Size " + (objectCount + 1) + " /Root 1 0 R >>\n");
            write(out, "startxref\n" + xrefOffset + "\n%%EOF\n");
            return out.toByteArray();
        }

        private void writeObject(ByteArrayOutputStream out, List<Integer> offsets, int number, String body) {
            offsets.add(out.size());
            write(out, number + " 0 obj\n" + body + "\nendobj\n");
        }

        private byte[] contentStream(PdfPage page) {
            StringBuilder content = new StringBuilder();
            for (PdfElement element : page.elemente()) {
                if (element instanceof PdfText text) {
                    content.append("BT\n")
                            .append(text.bold() ? "/F2 " : "/F1 ").append(text.fontSize()).append(" Tf\n")
                            .append("1 0 0 1 ").append(text.x()).append(" ").append(text.y()).append(" Tm\n")
                            .append("(").append(escape(text.text())).append(") Tj\n")
                            .append("ET\n");
                } else if (element instanceof PdfLine line) {
                    content.append(line.x1()).append(" ").append(line.y1()).append(" m ")
                            .append(line.x2()).append(" ").append(line.y2()).append(" l S\n");
                } else if (element instanceof PdfRect rect) {
                    content.append(rect.x()).append(" ").append(rect.y()).append(" ")
                            .append(rect.width()).append(" ").append(rect.height()).append(" re S\n");
                }
            }
            return content.toString().getBytes(PDF_CHARSET);
        }

        private String escape(String text) {
            return Objects.toString(text, "")
                    .replace("\\", "\\\\")
                    .replace("(", "\\(")
                    .replace(")", "\\)");
        }

        private void write(ByteArrayOutputStream out, String value) {
            out.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
        }
    }
}
