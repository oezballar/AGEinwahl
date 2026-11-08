#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"

teilnehmer_einwahl_pfade() {
  curl -sS "${BASE_URL}/teilnehmer" \
    | grep -oE '/teilnehmer/[0-9]+/einwahl' \
    | sort -u -V
}

feldnamen() {
  local html_file="$1"
  local prefix="$2"

  grep -oE "name=\"${prefix}_[0-9]+\"" "$html_file" | grep -oE "${prefix}_[0-9]+"
}

zuweisungs_feldnamen() {
  local html_file="$1"

  perl -0ne '
    while (/<div class="selection-time-group".*?<\/div>\s*<\/div>/sg) {
      my $group = $&;
      next unless $group =~ /data-zuweisungs-typ="prioritaet"/;
      next unless $group =~ /name="(zugewiesen_[A-Z]+_NACHMITTAG)"/;
      print "$1\n";
    }
  ' "$html_file"
}

ja_nein_zuweisungs_gruppen() {
  local html_file="$1"
  local prefix="$2"

  perl -0ne '
    my $prefix = $ENV{"PREFIX"};
    while (/<div class="selection-time-group".*?<\/div>\s*<\/div>/sg) {
      my $group = $&;
      next unless $group =~ /data-zuweisungs-typ="ja-nein"/;
      next unless $group =~ /name="(zugewiesen_'"$prefix"'_[A-Z]+_[A-Z]+)"/;
      my $assignment = $1;
      my @fields = ();
      while ($group =~ /name="('"$prefix"'_[0-9]+)"/g) {
        push @fields, $1;
      }
      print join("\t", $assignment, @fields), "\n" if @fields;
    }
  ' "$html_file"
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
    while (/<div class="selection-time-group".*?<\/div>\s*<\/div>/sg) {
      my $group = $&;
      next unless $group =~ /data-zuweisungs-typ="prioritaet"/;
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
  ' "$html_file"
}

nachmittag_ordinal_bis() {
  local teilnehmer_index="$1"
  local anzahl=0
  local index

  for ((index = 0; index <= teilnehmer_index; index++)); do
    if ((index % 7 != 0)); then
      anzahl=$((anzahl + 1))
    fi
  done
  echo "$anzahl"
}

jahrgang12_ordinal_bis() {
  local teilnehmer_index="$1"
  local anzahl=0
  local index
  local ende=$teilnehmer_index

  if ((ende > 45)); then
    ende=45
  fi
  for ((index = 0; index <= ende; index++)); do
    if ((index % 7 != 0)); then
      anzahl=$((anzahl + 1))
    fi
  done
  echo "$anzahl"
}

jahrgang34_ordinal_bis() {
  local teilnehmer_index="$1"
  local anzahl=0
  local index

  for ((index = 46; index <= teilnehmer_index; index++)); do
    if ((index % 7 != 0)); then
      anzahl=$((anzahl + 1))
    fi
  done
  echo "$anzahl"
}

wahl_index_fuer_titel() {
  local teilnehmer_index="$1"
  local tag_index="$2"
  local anzahl="$3"
  local i
  local ordinal
  local ziel
  shift 3
  local -a titel=("$@")

  if ((anzahl == 0)); then
    echo 0
    return
  fi

  # Dienstag: Naturfreunde wird dreifach belegt, Spielzeit Dienstag knapp ueberbelegt.
  if titel_vorhanden "Naturfreunde" "${titel[@]}" && titel_vorhanden "Spielzeit Dienstag" "${titel[@]}"; then
    ordinal="$(nachmittag_ordinal_bis "$teilnehmer_index")"
    if ((ordinal <= 45)); then
      titel_index "Naturfreunde" "${titel[@]}"
      return
    fi
    if ((ordinal <= 66)); then
      titel_index "Spielzeit Dienstag" "${titel[@]}"
      return
    fi
  fi

  # Mittwoch: Tischtennis wird dreifach belegt. Die restlichen Jahrgaenge werden
  # kontrolliert auf die drei anderen Nachmittags-AGs verteilt.
  if titel_vorhanden "Tischtennis" "${titel[@]}"; then
    if ((teilnehmer_index <= 45)); then
      ordinal="$(jahrgang12_ordinal_bis "$teilnehmer_index")"
      if ((ordinal <= 30)); then
        titel_index "Tischtennis" "${titel[@]}"
        return
      fi
      titel_index "Spielzeit Mittwoch" "${titel[@]}"
      return
    fi
  fi

  if titel_vorhanden "Spielzeit Mittwoch" "${titel[@]}" && ((teilnehmer_index > 45)); then
    ordinal="$(jahrgang34_ordinal_bis "$teilnehmer_index")"
    if ((ordinal <= 20)); then
      titel_index "Spielzeit Mittwoch" "${titel[@]}"
    elif ((ordinal <= 34)); then
      titel_index "Musikalischer Grundkurs" "${titel[@]}"
    else
      titel_index "Trommeln" "${titel[@]}"
    fi
    return
  fi

  # Donnerstag: Spielzeit Donnerstag wird mit 70 Kindern deutlich ueberbelegt.
  if titel_vorhanden "Spielzeit Donnerstag" "${titel[@]}"; then
    ordinal="$(nachmittag_ordinal_bis "$teilnehmer_index")"
    if ((ordinal <= 70)); then
      titel_index "Spielzeit Donnerstag" "${titel[@]}"
      return
    fi
    if ((ordinal % 2 == 0)); then
      ziel="Textiles Gestalten"
    else
      ziel="Geräteturnen"
    fi
    titel_index "$ziel" "${titel[@]}"
    return
  fi

  # Fallback fuer Montag, Freitag und die restlichen Dienstag-/Mittwoch-AGs.
  # Die gezielt ueberbelegten AGs werden dabei nicht nochmals verwendet.
  local -a kandidaten=()
  for ((i = 0; i < anzahl; i++)); do
    case "${titel[$i]}" in
      Naturfreunde|"Spielzeit Dienstag"|Tischtennis|"Spielzeit Donnerstag"|Bewegungsyoga)
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

titel_vorhanden() {
  local gesucht="$1"
  shift
  local titel

  for titel in "$@"; do
    if [[ "$titel" == "$gesucht" ]]; then
      return 0
    fi
  done
  return 1
}

post_teilnehmer_einwahl() {
  local path="$1"
  local teilnehmer_index="$2"
  local html_file
  local feld
  local index
  local gesetzt
  local tag_index
  local field
  local i
  local title
  local pair
  local prioritaet
  local -a entdecker_felder
  local -a vormittag_felder
  local -a nachmittag_felder
  local -a zuweisung_felder
  local -a entdecker_zuweisung_gruppen
  local -a vormittag_zuweisung_gruppen
  local -a gruppe
  local -a tag_paare
  local -a tag_felder
  local -a tag_titel
  local -a args
  local gesamt_felder
  local zuweisungs_gruppen_anzahl
  declare -A werte=()

  html_file="$(mktemp)"
  curl -sS "${BASE_URL}${path}" -o "$html_file"

  mapfile -t entdecker_felder < <(feldnamen "$html_file" "entdecker")
  mapfile -t vormittag_felder < <(feldnamen "$html_file" "vormittag")
  mapfile -t nachmittag_felder < <(feldnamen "$html_file" "nachmittag")
  mapfile -t zuweisung_felder < <(zuweisungs_feldnamen "$html_file")
  mapfile -t entdecker_zuweisung_gruppen < <(PREFIX="entdecker" ja_nein_zuweisungs_gruppen "$html_file" "entdecker")
  mapfile -t vormittag_zuweisung_gruppen < <(PREFIX="vormittag" ja_nein_zuweisungs_gruppen "$html_file" "vormittag")

  for feld in "${entdecker_felder[@]}" "${vormittag_felder[@]}" "${nachmittag_felder[@]}" "${zuweisung_felder[@]}"; do
    werte["$feld"]=""
  done

  # Entdecker: alle Auswahlfelder werden ausgefuellt.
  index=0
  while IFS=$'\t' read -r field title; do
    [[ -z "$field" ]] && continue
    if [[ "$title" == "Bewegungsyoga" ]]; then
      werte["$field"]=$([[ "$teilnehmer_index" -lt 7 ]] && printf "JA" || printf "NEIN")
    else
      werte["$field"]=$([[ $(((teilnehmer_index + index) % 6)) -eq 0 ]] && printf "JA" || printf "NEIN")
    fi
    index=$((index + 1))
  done < <(PREFIX="entdecker" felder_mit_titel "$html_file" "entdecker")

  # Vormittag: alle Ja/Nein-Auswahlen werden ausgefuellt.
  index=0
  while IFS=$'\t' read -r field title; do
    [[ -z "$field" ]] && continue
    if [[ "$title" == "Chor" && "$teilnehmer_index" -lt 24 ]]; then
      werte["$field"]="JA"
    elif [[ "$title" == "Mini-Orchester" && $((teilnehmer_index % 5)) -eq 0 ]]; then
      werte["$field"]="JA"
    else
      werte["$field"]="NEIN"
    fi
    index=$((index + 1))
  done < <(PREFIX="vormittag" felder_mit_titel "$html_file" "vormittag")

  # Nachmittag: Prioritaeten pro AG plus genau eine echte Zuweisung pro besuchbarem Wochentag.
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
    werte["${zuweisung_felder[$tag_index]}"]="${tag_felder[$index]#nachmittag_}"

    prioritaet=2
    for ((i = 0; i < ${#tag_felder[@]}; i++)); do
      if ((i == index)); then
        werte["${tag_felder[$i]}"]="1"
      else
        werte["${tag_felder[$i]}"]="$prioritaet"
        prioritaet=$((prioritaet + 1))
      fi
    done
    tag_index=$((tag_index + 1))
  done < <(nachmittags_tage "$html_file")

  for zeile in "${entdecker_zuweisung_gruppen[@]}" "${vormittag_zuweisung_gruppen[@]}"; do
    [[ -z "$zeile" ]] && continue
    IFS=$'\t' read -ra gruppe <<< "$zeile"
    werte["${gruppe[0]}"]=""
    local ja_feld=""
    for ((i = 1; i < ${#gruppe[@]}; i++)); do
      if [[ "${werte[${gruppe[$i]}]}" == "JA" ]]; then
        ja_feld="${gruppe[$i]}"
        break
      fi
    done
    if [[ -z "$ja_feld" && ${#gruppe[@]} -gt 1 ]]; then
      ja_feld="${gruppe[1]}"
      werte["$ja_feld"]="JA"
    fi
    if [[ -n "$ja_feld" ]]; then
      werte["${gruppe[0]}"]="${ja_feld##*_}"
    fi
  done

  args=(-sS -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}${path}")
  gesetzt=0
  for feld in "${!werte[@]}"; do
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

  zuweisungs_gruppen_anzahl=$(( ${#zuweisung_felder[@]} + ${#entdecker_zuweisung_gruppen[@]} + ${#vormittag_zuweisung_gruppen[@]} ))
  gesamt_felder=$(( ${#entdecker_felder[@]} + ${#vormittag_felder[@]} + ${#nachmittag_felder[@]} + zuweisungs_gruppen_anzahl ))
  printf "OK  %s (vollstaendig, %s von %s Feldern gesetzt)\n" "$path" "$gesetzt" "$gesamt_felder"
}

printf "Importiere realistische Einwahl-Testdaten nach %s\n" "$BASE_URL"

mapfile -t pfade < <(teilnehmer_einwahl_pfade)
if ((${#pfade[@]} == 0)); then
  printf "ERR Keine Teilnehmereinwahlen gefunden. Laeuft die Anwendung unter %s und wurden AG-/Teilnehmer-Testdaten importiert?\n" "$BASE_URL" >&2
  exit 1
fi

for ((i = 0; i < ${#pfade[@]}; i++)); do
  post_teilnehmer_einwahl "${pfade[$i]}" "$i"
done

printf "Einwahl-Testdaten importiert: %s von %s Teilnehmereinwahlen vollstaendig\n" "${#pfade[@]}" "${#pfade[@]}"
