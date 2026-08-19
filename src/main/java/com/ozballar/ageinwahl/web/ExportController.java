package com.ozballar.ageinwahl.web;

import java.util.stream.StreamSupport;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ozballar.ageinwahl.export.AgTeilnehmerlistenExport;
import com.ozballar.ageinwahl.export.KlassenuebersichtExport;
import com.ozballar.ageinwahl.export.TeilnehmerStundenplanExport;
import com.ozballar.ageinwahl.export.WunschzettelExport;
import com.ozballar.ageinwahl.service.AgService;
import com.ozballar.ageinwahl.service.TeilnehmerService;

@Controller
@RequestMapping("/exports")
public class ExportController {

    private final AgService agService;
    private final TeilnehmerService teilnehmerService;
    private final AgTeilnehmerlistenExport agTeilnehmerlistenExport;
    private final WunschzettelExport wunschzettelExport;
    private final KlassenuebersichtExport klassenuebersichtExport;
    private final TeilnehmerStundenplanExport teilnehmerStundenplanExport;

    public ExportController(
            AgService agService,
            TeilnehmerService teilnehmerService,
            AgTeilnehmerlistenExport agTeilnehmerlistenExport,
            WunschzettelExport wunschzettelExport,
            KlassenuebersichtExport klassenuebersichtExport,
            TeilnehmerStundenplanExport teilnehmerStundenplanExport
    ) {
        this.agService = agService;
        this.teilnehmerService = teilnehmerService;
        this.agTeilnehmerlistenExport = agTeilnehmerlistenExport;
        this.wunschzettelExport = wunschzettelExport;
        this.klassenuebersichtExport = klassenuebersichtExport;
        this.teilnehmerStundenplanExport = teilnehmerStundenplanExport;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("agAnzahl", anzahl(agService.findeAlle()));
        model.addAttribute("teilnehmerAnzahl", anzahl(teilnehmerService.findeAlle()));
        return "exports/index";
    }

    @GetMapping("/ag-teilnehmerlisten.pdf")
    public ResponseEntity<byte[]> agTeilnehmerlisten() {
        return pdfAntwort(agTeilnehmerlistenExport.erstellen(), "ag-teilnehmerlisten.pdf");
    }

    @GetMapping("/ag-teilnehmerlisten.zip")
    public ResponseEntity<byte[]> agTeilnehmerlistenZip() {
        return zipAntwort(agTeilnehmerlistenExport.erstellenZip(), "ag-teilnehmerlisten.zip");
    }

    @GetMapping("/einwahlbroschuere.pdf")
    public ResponseEntity<byte[]> einwahlbroschuere() {
        return wunschzettel();
    }

    @GetMapping("/wunschzettel.pdf")
    public ResponseEntity<byte[]> wunschzettel() {
        return pdfAntwort(wunschzettelExport.erstellen(), "wunschzettel.pdf");
    }

    @GetMapping("/klassenuebersicht.pdf")
    public ResponseEntity<byte[]> klassenuebersicht() {
        return pdfAntwort(klassenuebersichtExport.erstellen(), "klassenuebersicht.pdf");
    }

    @GetMapping("/klassenuebersicht.zip")
    public ResponseEntity<byte[]> klassenuebersichtZip() {
        return zipAntwort(klassenuebersichtExport.erstellenZip(), "klassenuebersicht.zip");
    }

    @GetMapping("/teilnehmer-stundenplaene.pdf")
    public ResponseEntity<byte[]> teilnehmerStundenplaene() {
        return pdfAntwort(teilnehmerStundenplanExport.erstellen(), "teilnehmer-stundenplaene.pdf");
    }

    private ResponseEntity<byte[]> pdfAntwort(byte[] pdf, String dateiname) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(dateiname)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }

    private ResponseEntity<byte[]> zipAntwort(byte[] zip, String dateiname) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(dateiname)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(zip);
    }

    private long anzahl(Iterable<?> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).count();
    }
}
