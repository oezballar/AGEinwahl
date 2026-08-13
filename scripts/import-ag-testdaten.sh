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
  local verantwortlicher="$5"
  local ort="$6"
  local maximale_teilnehmerzahl="$7"
  local jahrgaenge="$8"
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
      --data-urlencode "klasse=${klasse}"
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
    post_teilnehmer "$vorname" "$name" "$klasse"
    participant_index=$((participant_index + 1))
  done
}

printf "Importiere AG-Testdaten nach %s\n" "$BASE_URL"

post_ag MONTAG NACHMITTAG ENTDECKERANGEBOT "Klänge und Geschichten" "Herr Werner" "Versammlungsraum" 12 "1,2"
post_ag MONTAG NACHMITTAG ENTDECKERANGEBOT "Experimente" "Frau Westphal" "mittlerer Betreuungsraum" 15 "1,2"
post_ag MONTAG NACHMITTAG ENTDECKERANGEBOT "Entspannungs-AG" "Frau Wollstädter" "Turnhalle" 15 "1,2"
post_ag MONTAG NACHMITTAG ENTDECKERANGEBOT "Spielzeit Montag" "Frau Sperling" "vorderer, hinterer Betreuungsraum" 20 "1,2"

post_ag DIENSTAG VORMITTAG AG "Chor" "Herr Werner/ Herr Bro Larsen" "Versammlungsraum" 30 "1,2,3,4"
post_ag DIENSTAG NACHMITTAG JAHRES_AG "Schwarzlicht-Theater" "Frau Monsholo" "Theaterraum" 10 "2,3"
post_ag DIENSTAG NACHMITTAG AG "Naturfreunde" "Frau Westphal" "mittlerer Betreuungsraum" 15 "1,2,3,4"
post_ag DIENSTAG NACHMITTAG AG "Stark & fair: ringen, raufen, Kräfte messen" "Herr Dewan" "Turnhalle" 12 "1,2"
post_ag DIENSTAG NACHMITTAG AG "Entdecke die Welt des Programmierens" "Frau Diegel" "" 10 "3,4"
post_ag DIENSTAG NACHMITTAG JAHRES_AG "Theater" "Herr Werner/ Frau Lauer" "102N/Versammlungsraum" 14 "3,4"
post_ag DIENSTAG NACHMITTAG AG "Fußball" "Herr Rößler" "Turnhalle/Sportplatz" 16 "2,3,4"
post_ag DIENSTAG NACHMITTAG AG "Spielzeit Dienstag" "Frau Sperling" "Schulhof/Betreuungsräume vorne und hinten" 20 "1,2,3,4"
post_ag DIENSTAG NACHMITTAG AG "Töpfern" "Frau Latsch" "Dachgeschoss" 9 "1,2"

post_ag MITTWOCH VORMITTAG AG "Mini-Orchester" "Herr Werner" "Versammlungsraum" 12 "3,4"
post_ag MITTWOCH NACHMITTAG AG "Spielzeit Mittwoch" "Frau Pelger" "Schulhof/Betreuungsräume vorne und hinten" 20 "1,2"
post_ag MITTWOCH NACHMITTAG AG "Musikalischer Grundkurs" "Frau Martinez" "Versammlungsraum/Theaterraum" 14 "1,2"
post_ag MITTWOCH NACHMITTAG AG "Trommeln" "Herr Diallo" "Klassenraum grüne Tür" 12 "1,2"
post_ag MITTWOCH NACHMITTAG AG "Tischtennis" "Herr Heinrich" "Turnhalle" 10 "1,2"

post_ag DONNERSTAG NACHMITTAG AG "Spielzeit Donnerstag" "Frau Pelger" "Schulhof/Betreuungsräume vorne und hinten" 23 "1,2"
post_ag DONNERSTAG NACHMITTAG AG "Textiles Gestalten" "Frau Sperling" "mittlerer Betreuungsraum" 11 "1,2"
post_ag DONNERSTAG NACHMITTAG AG "Geräteturnen" "Frau Schiffer" "Turnhalle" 13 "1,2"

post_ag FREITAG NACHMITTAG ENTDECKERANGEBOT "Basteln" "Frau Pelger" "mittlerer Betreuungsraum" 12 "1,2"
post_ag FREITAG NACHMITTAG ENTDECKERANGEBOT "Kinderleichtathletik" "Frau Stoll" "Turnhalle" 15 "1,2"
post_ag FREITAG NACHMITTAG ENTDECKERANGEBOT "Bewegungsyoga" "Frau Winkler" "Yogaquartier" 8 "1,2"
post_ag FREITAG NACHMITTAG ENTDECKERANGEBOT "Spielzeit Freitag" "Frau Sperling" "Schulhof/Betreuungsräume" 20 "1,2"

printf "Importiere Teilnehmer-Testdaten\n"

post_teilnehmer_gruppe "1a" 12
post_teilnehmer_gruppe "1b" 14
post_teilnehmer_gruppe "2" 20
post_teilnehmer_gruppe "3a" 15
post_teilnehmer_gruppe "3b" 14
post_teilnehmer_gruppe "4" 21
