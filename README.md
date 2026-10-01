# SENECA – JCICS-Publikationsversion, portiert auf modernes Java und CDK

Ziel: Funktional 1:1 identische Portierung von SENECA (Steinbeck, J. Chem. Inf. Comput. Sci. 2001, 41(6), 1500–1507,
DOI 10.1021/ci000407n) auf aktuelles Java, aktuelles CDK und aktuelle Bibliotheken, lauffähig als `Seneca.app` unter macOS.
Verteiltes Rechnen über RMI entfällt; die Strukturgenerierung läuft im Prozess.

- `legacy/original/` – unveränderter Quellbaum der Publikationsversion (aus github.com/steinbeck/seneca-jcics, Commit fb5ffa3)
- `legacy/dist-2001/` – das 2001 gebaute Original-Jar `seneca-client.jar`
