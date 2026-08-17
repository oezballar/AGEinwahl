package com.ozballar.ageinwahl.datei;

import java.time.OffsetDateTime;
import java.util.List;

import com.ozballar.ageinwahl.domain.Ag;
import com.ozballar.ageinwahl.domain.EinwahlEntdeckerangebot;
import com.ozballar.ageinwahl.domain.EinwahlVormittagsAG;
import com.ozballar.ageinwahl.domain.Teilnehmer;

public record AgeinwahlDatei(
        String format,
        int version,
        OffsetDateTime gespeichertAm,
        List<AgEintrag> ags,
        List<TeilnehmerEintrag> teilnehmer,
        EinwahlenEintrag einwahlen
) {

    public static final String FORMAT = "AGEinwahl";
    public static final int VERSION = 1;

    public record AgEintrag(
            Integer id,
            Ag.Wochentag wochentag,
            Ag.Zeit zeit,
            Ag.Kategorie kategorie,
            String titel,
            String beschreibung,
            String verantwortlicher,
            String ort,
            Integer maximaleTeilnehmerzahl,
            List<Integer> erlaubteJahrgaenge
    ) {
    }

    public record TeilnehmerEintrag(
            Integer id,
            String vorname,
            String name,
            String klasse,
            Teilnehmer.GtTeilnahme gtTeilnahme
    ) {
    }

    public record EinwahlenEintrag(
            List<EinwahlEntdeckerangebotEintrag> entdeckerangebote,
            List<EinwahlVormittagsAgEintrag> vormittagsAgs,
            List<EinwahlAgEintrag> nachmittagsAgs
    ) {
    }

    public record EinwahlEntdeckerangebotEintrag(
            Integer id,
            Integer teilnehmerId,
            String agTitel,
            EinwahlEntdeckerangebot.Auswahl auswahl,
            Boolean zugewiesen
    ) {
    }

    public record EinwahlVormittagsAgEintrag(
            Integer id,
            Integer teilnehmerId,
            String agTitel,
            EinwahlVormittagsAG.Auswahl auswahl,
            Boolean zugewiesen
    ) {
    }

    public record EinwahlAgEintrag(
            Integer id,
            Integer teilnehmerId,
            String agTitel,
            Integer auswahl,
            Boolean zugewiesen
    ) {
    }
}
