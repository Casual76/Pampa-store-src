# Pampa Store - contesto del progetto

Pampa Store e un catalogo Android nativo scritto con Kotlin, Jetpack Compose, Material 3
Expressive e Fluid Engine. Permette di scoprire, installare, aprire e aggiornare APK pubblicati
nei repository GitHub indicizzati da Casual76/Pampa-store.

## Comandi

- Build debug: ./gradlew :app:assembleDebug
- Test unitari: ./gradlew :app:testDebugUnitTest
- Lint: ./gradlew :app:lintDebug
- Installazione sul dispositivo: ./gradlew :app:installDebug
- Build release: ./gradlew :app:assembleRelease

## Architettura

- :app contiene applicazione Android, dati, installazione, stato e UI Compose.
- :engine-* contiene i moduli Fluid Engine caricati dal submodule engine/.
- plugins/pampa-store-publisher contiene il publisher CLI Android-only per release e manifest.

Il package di produzione resta com.pampa.store; la build debug usa com.pampa.store.debug.
Il catalogo remoto viene memorizzato in DataStore per mantenere l'app utilizzabile offline.

## Windows

La versione Windows e obsoleta e non piu supportata. Gli installer storici possono restare nelle
vecchie release a scopo archivistico, ma non devono essere generati, pubblicati o referenziati dai
nuovi manifest.
