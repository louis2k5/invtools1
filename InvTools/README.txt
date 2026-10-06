InvTools - Paper-Plugin (Quellcode)

Bauen (Java 25 + Maven, Internetzugang zu repo.papermc.io nötig):
    mvn clean package
Ergebnis: target/InvTools-1.0.0.jar -> in den plugins-Ordner des Servers legen.

Falls Maven die Paper-API nicht findet: in pom.xml die Property <paper.api.version> anpassen
(passende Version auf https://repo.papermc.io unter io/papermc/paper/paper-api nachsehen).
Falls dein Server eine andere Java-Version nutzt: <java.version> anpassen.
