package com.ozballar.ageinwahl.datei;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlAG;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.EinwahlVormittagsAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;
import com.ozballar.ageinwahl.repository.AgRepository;
import com.ozballar.ageinwahl.repository.EinwahlAGRepository;
import com.ozballar.ageinwahl.repository.EinwahlEntdeckerangebotRepository;
import com.ozballar.ageinwahl.repository.EinwahlVormittagsAGRepository;
import com.ozballar.ageinwahl.repository.TeilnehmerRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgeinwahlDateiService {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AgRepository agRepository;
    private final TeilnehmerRepository teilnehmerRepository;
    private final EinwahlEntdeckerangebotRepository einwahlEntdeckerangebotRepository;
    private final EinwahlAGRepository einwahlAGRepository;
    private final EinwahlVormittagsAGRepository einwahlVormittagsAGRepository;

    public AgeinwahlDateiService(
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            AgRepository agRepository,
            TeilnehmerRepository teilnehmerRepository,
            EinwahlEntdeckerangebotRepository einwahlEntdeckerangebotRepository,
            EinwahlAGRepository einwahlAGRepository,
            EinwahlVormittagsAGRepository einwahlVormittagsAGRepository
    ) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.agRepository = agRepository;
        this.teilnehmerRepository = teilnehmerRepository;
        this.einwahlEntdeckerangebotRepository = einwahlEntdeckerangebotRepository;
        this.einwahlAGRepository = einwahlAGRepository;
        this.einwahlVormittagsAGRepository = einwahlVormittagsAGRepository;
    }

    public byte[] exportiereJson() {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(aktuelleDatei());
        } catch (JacksonException ex) {
            throw new IllegalStateException("Die Daten konnten nicht als JSON gespeichert werden.", ex);
        }
    }

    @Transactional
    public void importiereJson(InputStream inputStream) {
        AgeinwahlDatei datei = leseDatei(inputStream);
        pruefeDatei(datei);

        leereDatenbank();
        importiereAgs(datei.ags());
        importiereTeilnehmer(datei.teilnehmer());
        importiereEinwahlen(datei.einwahlen());
        setzeIdentityWerte(datei);
    }

    private AgeinwahlDatei aktuelleDatei() {
        return new AgeinwahlDatei(
                AgeinwahlDatei.FORMAT,
                AgeinwahlDatei.VERSION,
                OffsetDateTime.now(),
                listeAus(agRepository.findAll()).stream()
                        .sorted(Comparator.comparing(Ag::titel))
                        .map(ag -> new AgeinwahlDatei.AgEintrag(
                                ag.id(),
                                ag.wochentag(),
                                ag.zeit(),
                                ag.kategorie(),
                                ag.titel(),
                                ag.beschreibung(),
                                ag.verantwortlicher(),
                                ag.ort(),
                                ag.maximaleTeilnehmerzahl(),
                                ag.erlaubteJahrgangszahlen()
                        ))
                        .toList(),
                listeAus(teilnehmerRepository.findAll()).stream()
                        .sorted(Comparator.comparing(Teilnehmer::klasse).thenComparing(Teilnehmer::name).thenComparing(Teilnehmer::vorname))
                        .map(teilnehmer -> new AgeinwahlDatei.TeilnehmerEintrag(
                                teilnehmer.id(),
                                teilnehmer.vorname(),
                                teilnehmer.name(),
                                teilnehmer.klasse(),
                                teilnehmer.gtTeilnahme()
                        ))
                        .toList(),
                new AgeinwahlDatei.EinwahlenEintrag(
                        listeAus(einwahlEntdeckerangebotRepository.findAll()).stream()
                                .sorted(Comparator.comparing(EinwahlEntdeckerangebot::teilnehmerId).thenComparing(EinwahlEntdeckerangebot::agTitel))
                                .map(einwahl -> new AgeinwahlDatei.EinwahlEntdeckerangebotEintrag(
                                        einwahl.id(),
                                        einwahl.teilnehmerId(),
                                        einwahl.agTitel(),
                                        einwahl.auswahl(),
                                        einwahl.zugewiesen()
                                ))
                                .toList(),
                        listeAus(einwahlVormittagsAGRepository.findAll()).stream()
                                .sorted(Comparator.comparing(EinwahlVormittagsAG::teilnehmerId).thenComparing(EinwahlVormittagsAG::agTitel))
                                .map(einwahl -> new AgeinwahlDatei.EinwahlVormittagsAgEintrag(
                                        einwahl.id(),
                                        einwahl.teilnehmerId(),
                                        einwahl.agTitel(),
                                        einwahl.auswahl(),
                                        einwahl.zugewiesen()
                                ))
                                .toList(),
                        listeAus(einwahlAGRepository.findAll()).stream()
                                .sorted(Comparator.comparing(EinwahlAG::teilnehmerId).thenComparing(EinwahlAG::agTitel))
                                .map(einwahl -> new AgeinwahlDatei.EinwahlAgEintrag(
                                        einwahl.id(),
                                        einwahl.teilnehmerId(),
                                        einwahl.agTitel(),
                                        einwahl.auswahl(),
                                        einwahl.zugewiesen()
                                ))
                                .toList()
                )
        );
    }

    private AgeinwahlDatei leseDatei(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, AgeinwahlDatei.class);
        } catch (JacksonException ex) {
            throw new DateiImportException("Die JSON-Datei konnte nicht gelesen werden.", ex);
        }
    }

    private void pruefeDatei(AgeinwahlDatei datei) {
        if (datei == null || !Objects.equals(AgeinwahlDatei.FORMAT, datei.format())) {
            throw new DateiImportException("Die Datei ist keine AGEinwahl-Datei.");
        }
        if (datei.version() != AgeinwahlDatei.VERSION) {
            throw new DateiImportException("Die Dateiversion " + datei.version() + " wird nicht unterstuetzt.");
        }
        if (datei.ags() == null || datei.teilnehmer() == null || datei.einwahlen() == null) {
            throw new DateiImportException("Die Datei ist unvollstaendig.");
        }
    }

    private void leereDatenbank() {
        jdbcTemplate.update("DELETE FROM einwahl_entdeckerangebot");
        jdbcTemplate.update("DELETE FROM einwahl_ag");
        jdbcTemplate.update("DELETE FROM einwahl_vormittags_ag");
        jdbcTemplate.update("DELETE FROM erlaubter_jahrgang");
        jdbcTemplate.update("DELETE FROM ag");
        jdbcTemplate.update("DELETE FROM teilnehmer");
    }

    private void importiereAgs(List<AgeinwahlDatei.AgEintrag> ags) {
        for (AgeinwahlDatei.AgEintrag ag : ags) {
            pruefeId(ag.id(), "AG");
            jdbcTemplate.update("""
                    INSERT INTO ag (id, wochentag, zeit, kategorie, titel, beschreibung, verantwortlicher, ort, maximale_teilnehmerzahl)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    ag.id(),
                    name(ag.wochentag()),
                    name(ag.zeit()),
                    name(ag.kategorie()),
                    ag.titel(),
                    ag.beschreibung(),
                    ag.verantwortlicher(),
                    ag.ort(),
                    ag.maximaleTeilnehmerzahl()
            );

            List<Integer> jahrgaenge = ag.erlaubteJahrgaenge() == null ? List.of() : ag.erlaubteJahrgaenge();
            for (int position = 0; position < jahrgaenge.size(); position++) {
                jdbcTemplate.update(
                        "INSERT INTO erlaubter_jahrgang (ag_id, position, jahrgang) VALUES (?, ?, ?)",
                        ag.id(),
                        position,
                        jahrgaenge.get(position)
                );
            }
        }
    }

    private void importiereTeilnehmer(List<AgeinwahlDatei.TeilnehmerEintrag> teilnehmer) {
        for (AgeinwahlDatei.TeilnehmerEintrag eintrag : teilnehmer) {
            pruefeId(eintrag.id(), "Teilnehmer");
            jdbcTemplate.update(
                    "INSERT INTO teilnehmer (id, vorname, name, klasse, gt_teilnahme) VALUES (?, ?, ?, ?, ?)",
                    eintrag.id(),
                    eintrag.vorname(),
                    eintrag.name(),
                    Teilnehmer.normalisiereKlasse(eintrag.klasse()),
                    name(eintrag.gtTeilnahme() == null ? Teilnehmer.GtTeilnahme.JA : eintrag.gtTeilnahme())
            );
        }
    }

    private void importiereEinwahlen(AgeinwahlDatei.EinwahlenEintrag einwahlen) {
        Map<Integer, Teilnehmer> teilnehmerNachId = listeAus(teilnehmerRepository.findAll()).stream()
                .collect(Collectors.toMap(Teilnehmer::id, teilnehmer -> teilnehmer));
        Map<String, Ag> agNachTitel = listeAus(agRepository.findAll()).stream()
                .collect(Collectors.toMap(Ag::titel, ag -> ag));
        List<AgeinwahlDatei.EinwahlEntdeckerangebotEintrag> entdeckerangebote = einwahlen.entdeckerangebote() == null ? List.of() : einwahlen.entdeckerangebote();
        for (AgeinwahlDatei.EinwahlEntdeckerangebotEintrag einwahl : entdeckerangebote) {
            if (istMittagsveranstaltungOhneGtTeilnahme(einwahl.teilnehmerId(), einwahl.agTitel(), teilnehmerNachId, agNachTitel)) {
                continue;
            }
            pruefeId(einwahl.id(), "Einwahl");
            jdbcTemplate.update(
                    "INSERT INTO einwahl_entdeckerangebot (id, teilnehmer_id, ag_titel, auswahl, zugewiesen) VALUES (?, ?, ?, ?, ?)",
                    einwahl.id(),
                    einwahl.teilnehmerId(),
                    einwahl.agTitel(),
                    name(einwahl.auswahl()),
                    einwahl.zugewiesen()
            );
        }

        List<AgeinwahlDatei.EinwahlVormittagsAgEintrag> vormittagsAgs = einwahlen.vormittagsAgs() == null ? List.of() : einwahlen.vormittagsAgs();
        for (AgeinwahlDatei.EinwahlVormittagsAgEintrag einwahl : vormittagsAgs) {
            pruefeId(einwahl.id(), "Einwahl");
            jdbcTemplate.update(
                    "INSERT INTO einwahl_vormittags_ag (id, teilnehmer_id, ag_titel, auswahl, zugewiesen) VALUES (?, ?, ?, ?, ?)",
                    einwahl.id(),
                    einwahl.teilnehmerId(),
                    einwahl.agTitel(),
                    name(einwahl.auswahl()),
                    einwahl.zugewiesen()
            );
        }

        List<AgeinwahlDatei.EinwahlAgEintrag> nachmittagsAgs = einwahlen.nachmittagsAgs() == null ? List.of() : einwahlen.nachmittagsAgs();
        for (AgeinwahlDatei.EinwahlAgEintrag einwahl : nachmittagsAgs) {
            if (istMittagsveranstaltungOhneGtTeilnahme(einwahl.teilnehmerId(), einwahl.agTitel(), teilnehmerNachId, agNachTitel)) {
                continue;
            }
            pruefeId(einwahl.id(), "Einwahl");
            jdbcTemplate.update(
                    "INSERT INTO einwahl_ag (id, teilnehmer_id, ag_titel, auswahl, zugewiesen) VALUES (?, ?, ?, ?, ?)",
                    einwahl.id(),
                    einwahl.teilnehmerId(),
                    einwahl.agTitel(),
                    einwahl.auswahl(),
                    einwahl.zugewiesen()
            );
        }
    }

    private boolean istMittagsveranstaltungOhneGtTeilnahme(
            Integer teilnehmerId,
            String agTitel,
            Map<Integer, Teilnehmer> teilnehmerNachId,
            Map<String, Ag> agNachTitel
    ) {
        Teilnehmer teilnehmer = teilnehmerNachId.get(teilnehmerId);
        Ag ag = agNachTitel.get(agTitel);
        return teilnehmer != null
                && ag != null
                && ag.zeit() == Ag.Zeit.NACHMITTAG
                && !teilnehmer.nimmtAnMittagsveranstaltungenTeil();
    }

    private void setzeIdentityWerte(AgeinwahlDatei datei) {
        setzeIdentityWert("ag", naechsteId(datei.ags().stream().map(AgeinwahlDatei.AgEintrag::id).toList()));
        setzeIdentityWert("teilnehmer", naechsteId(datei.teilnehmer().stream().map(AgeinwahlDatei.TeilnehmerEintrag::id).toList()));
        setzeIdentityWert("einwahl_entdeckerangebot", naechsteId((datei.einwahlen().entdeckerangebote() == null ? List.<AgeinwahlDatei.EinwahlEntdeckerangebotEintrag>of() : datei.einwahlen().entdeckerangebote()).stream().map(AgeinwahlDatei.EinwahlEntdeckerangebotEintrag::id).toList()));
        setzeIdentityWert("einwahl_vormittags_ag", naechsteId((datei.einwahlen().vormittagsAgs() == null ? List.<AgeinwahlDatei.EinwahlVormittagsAgEintrag>of() : datei.einwahlen().vormittagsAgs()).stream().map(AgeinwahlDatei.EinwahlVormittagsAgEintrag::id).toList()));
        setzeIdentityWert("einwahl_ag", naechsteId((datei.einwahlen().nachmittagsAgs() == null ? List.<AgeinwahlDatei.EinwahlAgEintrag>of() : datei.einwahlen().nachmittagsAgs()).stream().map(AgeinwahlDatei.EinwahlAgEintrag::id).toList()));
    }

    private int naechsteId(List<Integer> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(id -> id + 1)
                .orElse(1);
    }

    private void setzeIdentityWert(String tabelle, int naechsteId) {
        jdbcTemplate.execute("ALTER TABLE " + tabelle + " ALTER COLUMN id RESTART WITH " + naechsteId);
    }

    private void pruefeId(Integer id, String bezeichnung) {
        if (id == null) {
            throw new DateiImportException(bezeichnung + " ohne ID kann nicht importiert werden.");
        }
    }

    private String name(Enum<?> wert) {
        return wert == null ? null : wert.name();
    }

    private <T> List<T> listeAus(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
