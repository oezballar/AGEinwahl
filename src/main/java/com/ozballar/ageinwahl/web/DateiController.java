package com.ozballar.ageinwahl.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ozballar.ageinwahl.datei.AgeinwahlDateiService;
import com.ozballar.ageinwahl.datei.DateiImportException;

@Controller
@RequestMapping("/datei")
public class DateiController {

    private static final String STANDARD_DATEINAME = "ageinwahl.json";

    private final AgeinwahlDateiService ageinwahlDateiService;

    public DateiController(AgeinwahlDateiService ageinwahlDateiService) {
        this.ageinwahlDateiService = ageinwahlDateiService;
    }

    @PostMapping("/oeffnen")
    public String oeffnen(@RequestParam("datei") MultipartFile datei, RedirectAttributes redirectAttributes) {
        if (datei.isEmpty()) {
            redirectAttributes.addFlashAttribute("fehler", "Bitte eine JSON-Datei auswaehlen.");
            return "redirect:/";
        }

        try {
            ageinwahlDateiService.importiereJson(datei.getInputStream());
            redirectAttributes.addFlashAttribute("erfolg", "Die Datei wurde geoeffnet.");
        } catch (DateiImportException ex) {
            redirectAttributes.addFlashAttribute("fehler", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("fehler", "Die Datei konnte nicht geoeffnet werden.");
        }

        return "redirect:/";
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam("dateiname") String dateiname) {
        return jsonDownload(bereinigterDateiname(dateiname));
    }

    private ResponseEntity<byte[]> jsonDownload(String dateiname) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(dateiname).build().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ageinwahlDateiService.exportiereJson());
    }

    private String bereinigterDateiname(String dateiname) {
        if (dateiname == null || dateiname.isBlank()) {
            return STANDARD_DATEINAME;
        }

        String bereinigt = dateiname.strip()
                .replace('\\', '_')
                .replace('/', '_')
                .replaceAll("[\\r\\n\\t]", "_");
        if (!bereinigt.toLowerCase().endsWith(".json")) {
            bereinigt = bereinigt + ".json";
        }
        return bereinigt;
    }
}
