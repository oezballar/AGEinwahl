#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

FIRST_NAMES=(
  Mila Noah Emma Ben Lina Paul
  Ella Leon Hanna Finn Lea Elias
  Clara Jonas Marie Felix Anna Luis
  Frieda Theo Mia Oskar Leni Emil
  Nele Anton Ida Moritz Paula Henry
  Greta Jakob
)

LAST_NAMES=(
  Meyer Schmidt Schneider Fischer Weber Wagner
  Becker Hoffmann Schäfer Koch Bauer Richter
  Klein Wolf Schröder Neumann Schwarz Zimmermann
  Braun Krüger Hartmann Lange Schmitt Werner
  Schmitz Krause Lehmann Schulz Maier Köhler
  Herrmann König
)

post_ag() {
  local wochentag="$1"
  local zeit="$2"
  local kategorie="$3"
  local titel="$4"
  local beschreibung="$5"
  local verantwortlicher="$6"
  local ort="$7"
  local maximale_teilnehmerzahl="$8"
  local jahrgaenge="$9"
  local response_file
  local http_code

  response_file="$(mktemp)"

  local args=(
    -sS
    -o "$response_file"
    -w "%{http_code}"
    -X POST "${BASE_URL}/ags"
    --data-urlencode "wochentag=${wochentag}"
    --data-urlencode "zeit=${zeit}"
    --data-urlencode "kategorie=${kategorie}"
    --data-urlencode "titel=${titel}"
    --data-urlencode "beschreibung=${beschreibung}"
    --data-urlencode "verantwortlicher=${verantwortlicher}"
    --data-urlencode "ort=${ort}"
    --data-urlencode "maximaleTeilnehmerzahl=${maximale_teilnehmerzahl}"
  )

  IFS="," read -ra years <<< "$jahrgaenge"
  for year in "${years[@]}"; do
    args+=(--data-urlencode "erlaubteJahrgaenge=${year}")
  done

  http_code="$(curl "${args[@]}")"
  rm -f "$response_file"

  if [[ "$http_code" == "302" ]]; then
    printf "OK  %s\n" "$titel"
  elif [[ "$http_code" == "200" ]]; then
    printf "SKIP %s\n" "$titel"
  else
    printf "ERR %s HTTP %s\n" "$titel" "$http_code" >&2
    return 1
  fi
}

post_teilnehmer() {
  local vorname="$1"
  local name="$2"
  local klasse="$3"
  local gt_teilnahme="$4"
  local response_file
  local http_code

  response_file="$(mktemp)"

  http_code="$(
    curl -sS \
      -o "$response_file" \
      -w "%{http_code}" \
      -X POST "${BASE_URL}/teilnehmer" \
      --data-urlencode "vorname=${vorname}" \
      --data-urlencode "name=${name}" \
      --data-urlencode "klasse=${klasse}" \
      --data-urlencode "gtTeilnahme=${gt_teilnahme}"
  )"
  rm -f "$response_file"

  if [[ "$http_code" == "302" ]]; then
    printf "OK  %s %s (%s)\n" "$vorname" "$name" "$klasse"
  elif [[ "$http_code" == "200" ]]; then
    printf "SKIP %s %s (%s)\n" "$vorname" "$name" "$klasse"
  else
    printf "ERR %s %s (%s) HTTP %s\n" "$vorname" "$name" "$klasse" "$http_code" >&2
    return 1
  fi
}

participant_index=0

post_teilnehmer_gruppe() {
  local klasse="$1"
  local anzahl="$2"

  for ((i = 0; i < anzahl; i++)); do
    local vorname="${FIRST_NAMES[$((participant_index % ${#FIRST_NAMES[@]}))]}"
    local name="${LAST_NAMES[$(((participant_index / ${#FIRST_NAMES[@]}) % ${#LAST_NAMES[@]}))]}"
    local gt_teilnahme="JA"
    if ((participant_index % 7 == 0)); then
      gt_teilnahme="NEIN"
    fi
    post_teilnehmer "$vorname" "$name" "$klasse" "$gt_teilnahme"
    participant_index=$((participant_index + 1))
  done
}

printf "Importiere AG-Testdaten nach %s\n" "$BASE_URL"

post_ag MONTAG NACHMITTAG ENTDECKERANGEBOT "Klänge und Geschichten" "Wir hören Musik, erfinden kleine Geschichten und probieren aus, wie Töne Gefühle und Bilder im Kopf entstehen lassen." "Herr Werner" "Versammlungsraum" 12 "1,2"
post_ag MONTAG NACHMITTAG ENTDECKERANGEBOT "Experimente" "Hier wird geforscht, gestaunt und ausprobiert. Mit einfachen Versuchen entdecken wir spannende Dinge aus Natur und Alltag." "Frau Westphal" "mittlerer Betreuungsraum" 15 "1,2"
post_ag MONTAG NACHMITTAG ENTDECKERANGEBOT "Entspannungs-AG" "Wir machen ruhige Spiele, kleine Bewegungsübungen und Pausen für Körper und Kopf, damit du wieder neue Kraft sammeln kannst." "Frau Wollstädter" "Turnhalle" 15 "1,2"
post_ag MONTAG NACHMITTAG ENTDECKERANGEBOT "Spielzeit Montag" "Zum Wochenstart ist Zeit zum Spielen, Bauen, Bewegen und Zusammensein mit anderen Kindern." "Frau Sperling" "vorderer, hinterer Betreuungsraum" 20 "1,2"

post_ag DIENSTAG VORMITTAG AG "Chor" "Wenn du gern singst, bist du hier richtig. Wir üben Lieder, hören aufeinander und singen gemeinsam mit Freude." "Herr Werner/ Herr Bro Larsen" "Versammlungsraum" 30 "1,2,3,4"
post_ag DIENSTAG NACHMITTAG JAHRES_AG "Schwarzlicht-Theater" "Im Schwarzlicht lassen wir leuchtende Dinge tanzen und erzählen kleine Geschichten auf der Bühne." "Frau Monsholo" "Theaterraum" 10 "2,3"
post_ag DIENSTAG NACHMITTAG AG "Naturfreunde" "Wir entdecken Pflanzen, Tiere und Naturmaterialien und lernen, wie wir gut auf unsere Umwelt achten können." "Frau Westphal" "mittlerer Betreuungsraum" 15 "1,2,3,4"
post_ag DIENSTAG NACHMITTAG AG "Stark & fair: ringen, raufen, Kräfte messen" "Du darfst dich auspowern, Kräfte messen und dabei lernen, wie man fair und respektvoll miteinander umgeht." "Herr Dewan" "Turnhalle" 12 "1,2"
post_ag DIENSTAG NACHMITTAG AG "Entdecke die Welt des Programmierens" "Wir lösen kleine Aufgaben am Computer und lernen spielerisch, wie man Figuren und Abläufe programmiert." "Frau Diegel" "" 10 "3,4"
post_ag DIENSTAG NACHMITTAG JAHRES_AG "Theater" "Wir schlüpfen in Rollen, erfinden Szenen und üben, mutig vor anderen aufzutreten." "Herr Werner/ Frau Lauer" "102N/Versammlungsraum" 14 "3,4"
post_ag DIENSTAG NACHMITTAG AG "Fußball" "Wir trainieren Passen, Dribbeln und Zusammenspiel und spielen faire Fußballspiele im Team." "Herr Rößler" "Turnhalle/Sportplatz" 16 "2,3,4"
post_ag DIENSTAG NACHMITTAG AG "Spielzeit Dienstag" "Hier kannst du frei spielen, neue Ideen ausprobieren und mit anderen Kindern eine gute Zeit haben." "Frau Sperling" "Schulhof/Betreuungsräume vorne und hinten" 20 "1,2,3,4"
post_ag DIENSTAG NACHMITTAG AG "Töpfern" "Aus Ton formen wir Tiere, Schalen, Figuren und eigene Ideen. Deine Hände dürfen kreativ werden." "Frau Latsch" "Dachgeschoss" 9 "1,2"

post_ag MITTWOCH VORMITTAG AG "Mini-Orchester" "Wir probieren Instrumente aus, hören auf den gemeinsamen Klang und machen zusammen Musik." "Herr Werner" "Versammlungsraum" 12 "3,4"
post_ag MITTWOCH NACHMITTAG AG "Spielzeit Mittwoch" "In der Wochenmitte ist Platz für Bewegung, Spiele, Bauen und gemeinsame Ideen." "Frau Pelger" "Schulhof/Betreuungsräume vorne und hinten" 20 "1,2"
post_ag MITTWOCH NACHMITTAG AG "Musikalischer Grundkurs" "Wir entdecken Rhythmus, Melodien und Instrumente und machen erste eigene Musikstücke." "Frau Martinez" "Versammlungsraum/Theaterraum" 14 "1,2"
post_ag MITTWOCH NACHMITTAG AG "Trommeln" "Mit Trommeln und Rhythmusübungen spüren wir den Takt und spielen gemeinsam kraftvolle Klänge." "Herr Diallo" "Klassenraum grüne Tür" 12 "1,2"
post_ag MITTWOCH NACHMITTAG AG "Tischtennis" "Wir üben Schlägerhaltung, Aufschläge und kleine Spiele. Dabei zählen Konzentration, Bewegung und Fairness." "Herr Heinrich" "Turnhalle" 10 "1,2"

post_ag DONNERSTAG NACHMITTAG AG "Spielzeit Donnerstag" "Du kannst drinnen und draußen spielen, dich bewegen oder mit anderen Kindern eigene Spielideen umsetzen." "Frau Pelger" "Schulhof/Betreuungsräume vorne und hinten" 23 "1,2"
post_ag DONNERSTAG NACHMITTAG AG "Textiles Gestalten" "Mit Stoff, Faden und Wolle gestalten wir kleine Kunstwerke und lernen einfache Handarbeitstechniken kennen." "Frau Sperling" "mittlerer Betreuungsraum" 11 "1,2"
post_ag DONNERSTAG NACHMITTAG AG "Geräteturnen" "Wir klettern, balancieren, springen und turnen an Geräten. Dabei probierst du aus, was dein Körper alles kann." "Frau Schiffer" "Turnhalle" 13 "1,2"

post_ag FREITAG NACHMITTAG ENTDECKERANGEBOT "Basteln" "Zum Wochenausklang schneiden, kleben, malen und gestalten wir bunte Dinge zum Mitnehmen oder Verschenken." "Frau Pelger" "mittlerer Betreuungsraum" 12 "1,2"
post_ag FREITAG NACHMITTAG ENTDECKERANGEBOT "Kinderleichtathletik" "Wir laufen, springen und werfen in kleinen Übungen und Spielen. Bewegung und Spaß stehen im Mittelpunkt." "Frau Stoll" "Turnhalle" 15 "1,2"
post_ag FREITAG NACHMITTAG ENTDECKERANGEBOT "Bewegungsyoga" "Mit ruhigen Bewegungen, Fantasiereisen und Atemübungen starten wir entspannt ins Wochenende." "Frau Winkler" "Yogaquartier" 8 "1,2"
post_ag FREITAG NACHMITTAG ENTDECKERANGEBOT "Spielzeit Freitag" "Am Freitag spielen wir gemeinsam, bewegen uns und lassen die Woche fröhlich ausklingen." "Frau Sperling" "Schulhof/Betreuungsräume" 20 "1,2"

printf "Importiere Teilnehmer-Testdaten\n"

post_teilnehmer_gruppe "1a" 12
post_teilnehmer_gruppe "1b" 14
post_teilnehmer_gruppe "2" 20
post_teilnehmer_gruppe "3a" 15
post_teilnehmer_gruppe "3b" 14
post_teilnehmer_gruppe "4" 21
