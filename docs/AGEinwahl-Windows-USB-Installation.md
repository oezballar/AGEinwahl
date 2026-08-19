# AGEinwahl auf einem Windows-Laptop einrichten

## Was benoetigt wird

Fuer das Erstellen des Windows-Pakets wird ein **JDK 19** benoetigt. Eine reine JVM/JRE reicht zum Erstellen nicht aus, weil `jpackage` Bestandteil des JDK ist.

Der Laptop, auf dem die Anwendung spaeter genutzt wird, benoetigt danach keine Java-Installation mehr. Das JDK bringt die benoetigte Laufzeit in das Paket ein.

## Paket von Linux aus bauen

Ein Windows-App-Image kann nicht direkt mit `jpackage` unter Linux erzeugt werden. Das Projekt enthaelt deshalb den manuellen GitHub-Actions-Workflow `Windows-Paket bauen`.

Voraussetzungen:

- Das Projekt liegt in einem GitHub-Repository.
- Die Aenderungen wurden nach GitHub hochgeladen.

Vorgehen:

1. Das Repository auf GitHub oeffnen.
2. Den Reiter `Actions` oeffnen.
3. Den Workflow `Windows-Paket bauen` auswaehlen.
4. `Run workflow` anklicken.
5. Nach Abschluss den Workflow-Lauf oeffnen.
6. Unter `Artifacts` das Archiv `AGEinwahl-App` herunterladen.
7. Das Archiv entpacken und den enthaltenen Ordner auf den USB-Stick kopieren.

Der Build laeuft dabei auf einem Windows-System mit JDK 19. Dein Linux-Rechner benoetigt dafuer kein Windows und kein lokal installiertes `jpackage`.

## Paket erstellen

1. Das Projekt auf einen Windows-Rechner kopieren.
2. Ein JDK 19 installieren.
3. Eine Eingabeaufforderung im Projektordner oeffnen.
4. Das Script starten:

   ```text
   scripts\build-windows-usb-package.bat
   ```

5. Nach erfolgreichem Abschluss den gesamten Ordner `dist\AGEinwahl-App` auf den USB-Stick kopieren.

## Anwendung auf dem Laptop bereitstellen

1. Den Ordner `AGEinwahl-App` vom USB-Stick auf den Laptop kopieren, zum Beispiel nach `Dokumente`.
2. Den Ordner nicht auseinandernehmen und die Unterordner nicht verschieben.
3. Fuer die taegliche Nutzung die Datei `Start-AGEinwahl.bat` verwenden.

Die Anwendung bindet ausschliesslich an `127.0.0.1`. Sie ist damit nur auf dem Laptop selbst erreichbar und nicht ueber das Netzwerk.

## Aktualisierung

Bei einer neuen Version ein neues Paket bauen und den gesamten alten Ordner `AGEinwahl-App` ersetzen. Vorher die Daten in der Anwendung ueber `Download` als JSON sichern.
