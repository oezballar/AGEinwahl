#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

teilnehmer_einwahl_pfade() {
  curl -sS "${BASE_URL}/teilnehmer" \
    | rg -o '/teilnehmer/[0-9]+/einwahl' \
    | sort -u -V
}

feldnamen() {
  local html_file="$1"
  local prefix="$2"

  rg -o "name=\"${prefix}_[0-9]+\"" "$html_file" | rg -o "${prefix}_[0-9]+"
}

felder_mit_titel() {
  local html_file="$1"
  local prefix="$2"

  perl -0ne '
    my $prefix = $ENV{"PREFIX"};
    while (/<label class="selection-item".*?<\/label>/sg) {
      my $item = $&;
      next unless $item =~ /<strong>(.*?)<\/strong>/s;
      my $title = $1;
      next unless $item =~ /name="('"$prefix"'_[0-9]+)"/;
      my $field = $1;
      $title =~ s/&amp;/\&/g;
      print "$field\t$title\n";
    }
  ' "$html_file"
}

nachmittags_tage() {
  local html_file="$1"

  perl -0ne '
    while (/<section class="selection-panel".*?<\/section>/sg) {
      my $day = $&;
      while ($day =~ /<div class="selection-time-group".*?<\/div>\s*<\/div>/sg) {
        my $group = $&;
        next unless $group =~ /<h3>NACHMITTAG<\/h3>/;
        my @pairs = ();
        while ($group =~ /<label class="selection-item".*?<\/label>/sg) {
          my $item = $&;
          next unless $item =~ /name="(nachmittag_[0-9]+)"/;
          my $field = $1;
          my $title = "";
          $title = $1 if $item =~ /<strong>(.*?)<\/strong>/s;
          $title =~ s/&amp;/\&/g;
          push @pairs, "$field=$title";
        }
        print join("\t", @pairs), "\n" if @pairs;
      }
    }
  ' "$html_file"
}

wahl_index_fuer_titel() {
  local teilnehmer_index="$1"
  local tag_index="$2"
  local anzahl="$3"
  local i
  shift 3
  local -a titel=("$@")

  if ((anzahl == 0)); then
    echo 0
    return
  fi

  for ((i = 0; i < anzahl; i++)); do
    if [[ "${titel[$i]}" == "Entdecke die Welt des Programmierens" ]]; then
      local idx=$((teilnehmer_index % 50))
      if ((idx < 10)); then titel_index "Schwarzlicht-Theater" "${titel[@]}"; return; fi
      if ((idx < 20)); then titel_index "Entdecke die Welt des Programmierens" "${titel[@]}"; return; fi
      if ((idx < 34)); then titel_index "Theater" "${titel[@]}"; return; fi
      titel_index "Fußball" "${titel[@]}"
      return
    fi
  done

  for ((i = 0; i < anzahl; i++)); do
    if [[ "${titel[$i]}" == "Töpfern" ]]; then
      local idx=$((teilnehmer_index % 46))
      if ((idx < 16)); then titel_index "Naturfreunde" "${titel[@]}"; return; fi
      if ((idx < 28)); then titel_index "Stark & fair: ringen, raufen, Kräfte messen" "${titel[@]}"; return; fi
      if ((idx < 37)); then titel_index "Töpfern" "${titel[@]}"; return; fi
      titel_index "Spielzeit Dienstag" "${titel[@]}"
      return
    fi
  done

  for ((i = 0; i < anzahl; i++)); do
    if [[ "${titel[$i]}" == "Tischtennis" ]]; then
      local idx=$((teilnehmer_index % 46))
      if ((idx < 11)); then titel_index "Tischtennis" "${titel[@]}"; return; fi
      if ((idx < 31)); then titel_index "Spielzeit Mittwoch" "${titel[@]}"; return; fi
      if ((idx < 45)); then titel_index "Musikalischer Grundkurs" "${titel[@]}"; return; fi
      titel_index "Trommeln" "${titel[@]}"
      return
    fi
  done

  for ((i = 0; i < anzahl; i++)); do
    if [[ "${titel[$i]}" == "Textiles Gestalten" ]]; then
      local idx=$((teilnehmer_index % 46))
      if ((idx < 23)); then titel_index "Spielzeit Donnerstag" "${titel[@]}"; return; fi
      if ((idx < 34)); then titel_index "Textiles Gestalten" "${titel[@]}"; return; fi
      titel_index "Geräteturnen" "${titel[@]}"
      return
    fi
  done

  # Fallback: wenige, bewusst knapp ueberbuchte AGs fuer ein realistisches Dashboard.
  for ((i = 0; i < anzahl; i++)); do
    if [[ "${titel[$i]}" == "Naturfreunde" && "$teilnehmer_index" -lt 16 ]]; then
      echo "$i"
      return
    fi
    if [[ "${titel[$i]}" == "Töpfern" && "$teilnehmer_index" -lt 10 ]]; then
      echo "$i"
      return
    fi
    if [[ "${titel[$i]}" == "Tischtennis" && "$teilnehmer_index" -lt 11 ]]; then
      echo "$i"
      return
    fi
  done

  local -a kandidaten=()
  for ((i = 0; i < anzahl; i++)); do
    case "${titel[$i]}" in
      Naturfreunde|Töpfern|Tischtennis|Geräteturnen|Bewegungsyoga)
        ;;
      *)
        kandidaten+=("$i")
        ;;
    esac
  done

  if ((${#kandidaten[@]} > 0)); then
    echo "${kandidaten[$(((teilnehmer_index + tag_index) % ${#kandidaten[@]}))]}"
  else
    echo $(((teilnehmer_index + tag_index) % anzahl))
  fi
}

titel_index() {
  local gesucht="$1"
  shift
  local -a titel=("$@")
  local i

  for ((i = 0; i < ${#titel[@]}; i++)); do
    if [[ "${titel[$i]}" == "$gesucht" ]]; then
      echo "$i"
      return
    fi
  done

  echo 0
}

post_teilnehmer_einwahl() {
  local path="$1"
  local teilnehmer_index="$2"
  local vollstaendig="$3"
  local html_file
  local feld
  local index
  local gesetzt
  local tag_index
  local field
  local i
  local title
  local pair
  local -a entdecker_felder
  local -a vormittag_felder
  local -a nachmittag_felder
  local -a tag_paare
  local -a tag_felder
  local -a tag_titel
  local -a args
  declare -A werte=()

  html_file="$(mktemp)"
  curl -sS "${BASE_URL}${path}" -o "$html_file"

  mapfile -t entdecker_felder < <(feldnamen "$html_file" "entdecker")
  mapfile -t vormittag_felder < <(feldnamen "$html_file" "vormittag")
  mapfile -t nachmittag_felder < <(feldnamen "$html_file" "nachmittag")

  for feld in "${entdecker_felder[@]}" "${vormittag_felder[@]}" "${nachmittag_felder[@]}"; do
    werte["$feld"]=""
  done

  # Entdecker: vollstaendige Datensaetze beantworten alles, sonst bleibt ein Teil offen.
  index=0
  while IFS=$'\t' read -r field title; do
    [[ -z "$field" ]] && continue
    if [[ "$vollstaendig" == "true" || $((index % 4)) -ne 0 ]]; then
      if [[ "$title" == "Bewegungsyoga" ]]; then
        werte["$field"]=$([[ "$teilnehmer_index" -lt 7 ]] && printf "JA" || printf "NEIN")
      else
        werte["$field"]=$([[ $(((teilnehmer_index + index) % 6)) -eq 0 ]] && printf "JA" || printf "NEIN")
      fi
    fi
    index=$((index + 1))
  done < <(PREFIX="entdecker" felder_mit_titel "$html_file" "entdecker")

  # Vormittag: Ja/Nein-Auswahl, offen bleibt nur ein Teil der unvollstaendigen Datensaetze.
  index=0
  while IFS=$'\t' read -r field title; do
    [[ -z "$field" ]] && continue
    if [[ "$vollstaendig" == "true" || $((index % 3)) -ne 0 ]]; then
      if [[ "$title" == "Chor" && "$teilnehmer_index" -lt 24 ]]; then
        werte["$field"]="JA"
      elif [[ "$title" == "Mini-Orchester" && $((teilnehmer_index % 5)) -eq 0 ]]; then
        werte["$field"]="JA"
      else
        werte["$field"]="NEIN"
      fi
    fi
    index=$((index + 1))
  done < <(PREFIX="vormittag" felder_mit_titel "$html_file" "vormittag")

  # Nachmittag: fuer jede besuchbare Wochentagsgruppe genau eine echte Zuweisung.
  tag_index=0
  while IFS= read -r zeile; do
    [[ -z "$zeile" ]] && continue
    IFS=$'\t' read -ra tag_paare <<< "$zeile"
    tag_felder=()
    tag_titel=()
    for pair in "${tag_paare[@]}"; do
      tag_felder+=("${pair%%=*}")
      tag_titel+=("${pair#*=}")
    done

    index="$(wahl_index_fuer_titel "$teilnehmer_index" "$tag_index" "${#tag_felder[@]}" "${tag_titel[@]}")"
    werte["${tag_felder[$index]}"]="1"

    for ((i = 0; i < ${#tag_felder[@]}; i++)); do
      if [[ -z "${werte[${tag_felder[$i]}]}" && ("$vollstaendig" == "true" || $(((teilnehmer_index + i + tag_index) % 4)) -ne 0) ]]; then
        werte["${tag_felder[$i]}"]=$(((i + tag_index) % 2 + 2))
      fi
    done
    tag_index=$((tag_index + 1))
  done < <(nachmittags_tage "$html_file")

  args=(-sS -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}${path}")
  gesetzt=0
  for feld in "${entdecker_felder[@]}" "${vormittag_felder[@]}" "${nachmittag_felder[@]}"; do
    [[ -n "${werte[$feld]}" ]] && gesetzt=$((gesetzt + 1))
    args+=(--data-urlencode "${feld}=${werte[$feld]}")
  done

  local http_code
  http_code="$(curl "${args[@]}")"
  rm -f "$html_file"

  if [[ "$http_code" != "302" ]]; then
    printf "ERR %s HTTP %s\n" "$path" "$http_code" >&2
    return 1
  fi

  printf "OK  %s (%s, %s von %s Feldern gesetzt)\n" "$path" "$([[ "$vollstaendig" == "true" ]] && printf "vollstaendig" || printf "offen")" "$gesetzt" "$(( ${#entdecker_felder[@]} + ${#vormittag_felder[@]} + ${#nachmittag_felder[@]} ))"
}

printf "Importiere realistische Einwahl-Testdaten nach %s\n" "$BASE_URL"

mapfile -t pfade < <(teilnehmer_einwahl_pfade)
vollstaendig_bis=$((${#pfade[@]} * 20 / 100))

for ((i = 0; i < ${#pfade[@]}; i++)); do
  if ((i < vollstaendig_bis)); then
    post_teilnehmer_einwahl "${pfade[$i]}" "$i" "true"
  else
    post_teilnehmer_einwahl "${pfade[$i]}" "$i" "false"
  fi
done

printf "Einwahl-Testdaten importiert: %s von %s Teilnehmereinwahlen vollstaendig\n" "$vollstaendig_bis" "${#pfade[@]}"
