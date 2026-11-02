# Pampa Store

Il catalogo Android delle app Pampa: scopre, installa, apre e aggiorna gli APK pubblicati nei repository
GitHub indicizzati da [`Casual76/Pampa-store`](https://github.com/Casual76/Pampa-store). Ha una coda di
installazione (una app alla volta, perché Android chiede una conferma per pacchetto), il controllo degli
aggiornamenti ogni sei ore anche a Store chiuso, la cronologia delle versioni di ogni app e gli strumenti per
l'assistente (PampAI/Aria) attraverso il ponte di `engine-ai-bridge`.

È scritto in Kotlin e Jetpack Compose sopra il [Fluid Engine](https://github.com/Casual76/fluid-engine), che
vive nel submodule `engine/` (la versione è in `engine.properties`).

## Costruirlo

```
git clone --recurse-submodules https://github.com/Casual76/Pampa-store-src
./gradlew :app:assembleDebug        # build di debug (com.pampa.store.debug, accanto allo Store vero)
./gradlew :app:testDebugUnitTest    # test unitari
./gradlew :app:assembleRelease      # release: serve la firma, vedi sotto
```

La firma di release si legge da `local.properties` (che non è nel repository) o dalle variabili
`PAMPA_RELEASE_*`; `./gradlew :app:verifyReleaseSigning` dice cosa manca. Il publisher che carica le release
sullo Store è in `plugins/pampa-store-publisher`.

## Licenza

Pampa Store è software libero: si distribuisce sotto la **GNU General Public License, versione 3**
(testo completo in [`LICENSE`](LICENSE)). Chi riceve l'APK ha diritto ai sorgenti, che sono questo
repository, con il submodule `engine` alla versione indicata in `engine.properties`. Si possono leggere,
modificare e ridistribuire alle condizioni della GPL-3.0, con la stessa licenza.

**Perché GPL-3.0.** Dalla 3.0 il Fluid Engine contiene codice di
[LibrePods](https://github.com/librepods-org/librepods) (GPL-3.0): il comportamento dei pulsanti e delle
pagine non è più riscritto, è portato. Un'app che distribuisce l'engine è un'opera derivata, e va distribuita
con la stessa licenza. È la regola di tutte le app Pampa che usano l'engine. Fino alla 1.8.3 (engine 2.13) il
repository non dichiarava nessuna licenza; da questa versione vale la GPL-3.0.

Le opere di altri che l'APK porta con sé (il vetro di Kyant, Apache-2.0; i caratteri Inter, SIL OFL 1.1;
LibrePods) sono elencate nella pagina **Altro** dell'app, in fondo, con gli avvisi che le loro licenze
chiedono; i testi sono in [`engine/LICENSES/`](engine/LICENSES).

Questo programma è distribuito nella speranza che sia utile, ma **senza alcuna garanzia**; vedi la
GNU General Public License per i dettagli.
