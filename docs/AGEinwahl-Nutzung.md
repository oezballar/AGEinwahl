# AGEinwahl bedienen

## Starten

1. Den Ordner `AGEinwahl-App` oeffnen.
2. Doppelt auf `Start-AGEinwahl.bat` klicken.
3. Nach wenigen Sekunden oeffnet sich die Anwendung automatisch im Browser.

Die Internetverbindung wird fuer die Anwendung nicht benoetigt. Die Adresse im Browser lautet `http://127.0.0.1:8080`.

## Arbeiten und Daten sichern

Die Anwendung speichert ihre Daten waehrend des Betriebs. Vor dem Beenden oder vor einem Neustart bitte auf der Startseite `Download` verwenden und die JSON-Datei an einem sicheren Ort ablegen.

## Schliessen

1. Den Ordner `AGEinwahl-App` oeffnen.
2. Doppelt auf `Stop-AGEinwahl.bat` klicken.

Danach kann das Fenster geschlossen werden. Beim naechsten Gebrauch wieder `Start-AGEinwahl.bat` starten.

## Rueckmeldezettel fuer Entdeckerangebote

Unter `Exports` gibt es einen eigenen PDF-Download fuer die Entdeckerangebote.
Er verwendet den Brieftext aus `Rueckmeldezettel_Entdeckerangebot.docx`, die
zugewiesenen Angebote und das Material aus dem AG-Datensatz. Die Unterschrift
lautet fest „Deine Annika Wollstädter & deine Anna Stoll“.
Der bisherige allgemeine Rueckmeldezettel bleibt separat verfuegbar.

Vor jeder Angebotsrunde sind diese Einstellungen in `application.properties`
zu aktualisieren (jeweils `JJJJ-MM-TT`):

- `ageinwahl.entdecker-rueckmeldezettel.briefdatum`
- `ageinwahl.entdecker-rueckmeldezettel.beginn`
- `ageinwahl.entdecker-rueckmeldezettel.ende`

Alternativ koennen sie beim Start als Spring-Parameter uebergeben werden,
zum Beispiel `--ageinwahl.entdecker-rueckmeldezettel.briefdatum=2026-11-10`.
Der Beginn wird auch im Erklaerungstext verwendet.

Nur Kinder mit mindestens einem zugewiesenen Entdeckerangebot erhalten eine
Seite, sortiert nach Klasse, Nachname und Vorname. Kinder ohne Zuweisung
werden ausgelassen. Montag und Freitag bleiben sichtbar; fehlt fuer einen Tag
die Zuweisung, steht dort
„Noch nicht zugewiesen“. Leere Materialangaben entfallen.
Mehrere Zuweisungen an einem Tag, Angebote ausserhalb Montag/Freitag und
ungueltige Referenzen verhindern den Download mit einem Hinweis.
Ohne Teilnehmer mit Zuweisung wird wie bei den anderen PDF-Exporten eine Informationsseite
ausgegeben. Der Export befuellt die originale DOCX-Vorlage unter
`src/main/resources/templates/exports/Rueckmeldezettel_Entdeckerangebot.docx`.
Das Schullogo, Comic Sans MS in 12 Punkt, Absatzabstaende, Tabulatoren,
Unterstreichungen und die zentrierte Unterschrift bleiben erhalten.
Zu lange Texte werden umgebrochen. Passen sie mit der Originalformatierung
nicht auf eine Seite je Kind, erscheint ein Hinweis; Schrift und Seitenlayout
werden nicht verkleinert.

Wie beim allgemeinen Rueckmeldezettel werden **LibreOffice** und fuer die
Originalformatierung **Comic Sans MS (normal und fett)** benoetigt. Die Schrift
muss auf dem ausfuehrenden Rechner installiert sein. Eine Ersatzschrift wird
im erzeugten PDF erkannt und mit einem Hinweis abgewiesen. Die Namen bleiben
fest in der Vorlage; nur Datum und Angebotszeitraum sind konfigurierbar.
Ein abweichender LibreOffice-Pfad kann mit `-Dageinwahl.libreoffice=...`
angegeben werden.

Die Tests der echten PDF-Konvertierung koennen auf einem entsprechend
eingerichteten Rechner mit `./mvnw -Dageinwahl.pdf-integration=true test`
ausgefuehrt werden. Die uebrigen Tests benoetigen weder LibreOffice noch die Schrift.

## Wichtiger Hinweis

Die Anwendung ist nur auf diesem Laptop erreichbar. Andere Computer oder Smartphones im WLAN koennen nicht darauf zugreifen.
