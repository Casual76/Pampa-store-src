# Publish Pampa Store Release

Guida per pubblicare release di app nel Pampa Store.

## Requisiti Fondamentali

### 1. APK Firmato (CRITICO)
L'APK **DEVE** essere firmato con una release keystore valida:
- ✅ Contiene `META-INF/MANIFEST.MF`
- ✅ Contiene certificato di firma (`.RSA`, `.EC`, o `.DSA` in `META-INF/`)
- ✅ Non è unsigned (il nome del file non contiene "unsigned")
- ❌ ❌ APK unsigned = ERRORE: non sarà accettato dal sistema

**Come fare:**
```gradle
// In build.gradle del modulo Android:
android {
    signingConfigs {
        release {
            storeFile file("keystore.jks")
            storePassword "your_store_password"
            keyAlias "your_key_alias"
            keyPassword "your_key_password"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

Poi build con: `./gradlew assembleRelease` (oppure `bundleRelease` per bundle)

### 2. Versione Sempre Crescente (CRITICO)
La versione della nuova release **DEVE** essere sempre maggiore della precedente:
- `1.0.0` → `1.0.1` ✅
- `1.0.0` → `1.1.0` ✅
- `1.0.0` → `2.0.0` ✅
- `1.0.0` → `1.0.0` ❌ ERRORE
- `1.0.1` → `1.0.0` ❌ ERRORE

**Versioning consigliato:** Semantic Versioning (MAJOR.MINOR.PATCH)

### 3. Formato della Versione
- Supportati: `1.0.0`, `1.0.0-beta`, `1.0.0-alpha.1`, `1.0.0-rc1`
- Non supportati: versioni non-numeriche

## Tool: `publish_pampa_store_android_release`

### Parametri Obbligatori
```json
{
  "channel": "stable",  // oppure "beta"
  "repoRoot": "/path/to/android/repo"  // opzionale se è la repo corrente
}
```

### Parametri Opzionali (ma consigliati)
```json
{
  "version": "1.0.1",           // se non specificato, rileva automaticamente
  "appId": "my-app",            // identificativo univoco nello store
  "appName": "My App",          // nome display
  "packageName": "com.example.myapp",
  "description": "...",         // descrizione app
  "developer": "Your Name",
  "category": "Productivity",
  "changelog": "Fixed bugs...", // changelog per la release
  "skipBuild": false            // se true, cerca APK già buildato
  "apkPath": "/path/to/app.apk" // se fornito, usa questo APK invece di buildare
}
```

### Flusso di Esecuzione

#### 1. **Modalità di Default** (build automatico)
```
CLI: publish --channel stable --version 1.0.1
┌─→ Valida che il repo ha un modulo Android
├─→ Esegue: ./gradlew assembleRelease
│  ├─ ❌ Se build fallisce: ERRORE (non continua)
│  └─ ✅ Se succede: APK generato
├─→ Trova l'APK buildato (ordine: più recente prima)
├─→ Valida che APK è firma (META-INF/*, firma presente)
│  └─ ❌ Se unsigned: ERRORE (non continua)
├─→ Carica su GitHub release
├─→ Aggiorna manifest app
├─→ Aggiorna indice store
└─→ ✅ Successo
```

#### 2. **Modalità Skip Build** (APK già buildato)
```
CLI: publish --channel stable --version 1.0.1 --skipBuild
┌─→ Cerca APK nel build/outputs
├─→ Valida firma
├─→ Carica su GitHub
└─→ ✅ Successo
```

#### 3. **Modalità APK Esplicito** (APK custom)
```
CLI: publish --channel stable --apkPath /home/user/my-app.apk
┌─→ Valida che il file esiste
├─→ Valida che è un APK (ZIP valido)
├─→ Valida che è firmato
├─→ Carica su GitHub
└─→ ✅ Successo
```

#### 4. **Dry Run** (anteprima)
```
CLI: publish --channel stable --dryRun
┌─→ Esegue tutti i passi come sopra
├─→ Mostra preview dei manifesti
└─→ NON carica niente su GitHub/Store
```

## Errori Comuni e Soluzioni

### ❌ "APK is not signed"
**Causa:** L'APK non è firmato o la firma è corrotta.

**Soluzione:**
1. Assicurati che `signingConfig` è configurato in `build.gradle`
2. Verifica che le credenziali keystore sono corrette
3. Ribuildo: `./gradlew clean assembleRelease`
4. Verifica con: `keytool -list -v -keystore keystore.jks`

### ❌ "APK file is not a valid ZIP archive"
**Causa:** L'APK è corrotto o non è un vero file APK.

**Soluzione:**
1. Verifica che il file esiste e ha estensione `.apk`
2. Prova a rinominarlo in `.zip` e aprilo (deve avere META-INF/)
3. Ribuildo dall'Android Studio o da CLI: `./gradlew assembleRelease`

### ❌ "Version '1.0.0' is not newer than current '1.0.0'"
**Causa:** La versione non è crescente.

**Soluzione:**
1. Incrementa il version code in `build.gradle`:
   ```gradle
   versionCode 2      // incrementa questo
   versionName "1.0.1" // e questo
   ```
2. Ribuildo

### ❌ "No Android application module found"
**Causa:** Non c'è un modulo Android nel repo.

**Soluzione:**
1. Verifica che il repo è un progetto Android Gradle
2. Controlla che esiste `build.gradle` nel modulo principale
3. Se è un progetto Kotlin Multiplatform, specifica il modulo:
   ```
   --module androidApp
   ```

### ❌ "No GitHub token found"
**Causa:** Token GitHub non è configurato.

**Soluzione:**
1. Crea un token: https://github.com/settings/tokens/new
   - Scope: `repo` (accesso completo ai repo)
2. Setta la variabile ambiente:
   ```bash
   export PAMPA_GH_TOKEN="ghp_xxxxx..."
   ```
   oppure
   ```bash
   export GITHUB_TOKEN="ghp_xxxxx..."
   ```

## Requisiti Sistema

### Token GitHub
- Scope richiesto: `repo` (accesso al repo owner)
- Env vars supportate:
  - `PAMPA_GH_TOKEN` (prioritario)
  - `GITHUB_TOKEN`
  - `GH_TOKEN`

### Android SDK
- Build tools >= 30
- Gradle wrapper (automatico in progetti Android)

## Flusso Completo di Pubblicazione

```bash
# 1. Aggiorna versione nel build.gradle
# 2. Prepara changelog
# 3. Build locale per testare
./gradlew assembleRelease

# 4. Pubblica su Store (automatizza build, firma, GitHub, manifesti)
publish --channel stable --version 1.0.1 --changelog "Fixed critical bug"

# Output atteso:
# ✅ Release pushed to GitHub
# ✅ App manifest updated
# ✅ Store index updated
```

## Struttura dei Manifesti

### App Manifest (`manifest.json` nel repo app)
```json
{
  "app": {
    "id": "my-app",
    "name": "My App",
    "packageName": "com.example.myapp",
    "stable": {
      "version": "1.0.1",
      "releaseDate": "2025-04-10",
      "changelog": "...",
      "apkAsset": "my-app-1.0.1.apk",
      "releaseTag": "stable-my-app-v1.0.1"
    },
    "history": [...]
  }
}
```

### Store Index (`manifest.json` nel Pampa-store)
```json
{
  "apps": [
    {
      "id": "my-app",
      "repoOwner": "username",
      "repoName": "my-app-repo",
      "manifestPath": "manifest.json"
    }
  ]
}
```

## Tips & Best Practices

✅ **DO:**
- Usa versioning semantico (MAJOR.MINOR.PATCH)
- Scrivi changelog chiari e utili
- Testa la build prima di pubblicare
- Usa `--dryRun` per anteprima

❌ **DON'T:**
- Non usare APK unsigned
- Non rilasciare versioni decrescenti
- Non cancellare release pubblicate
- Non modificare i tag manualmente su GitHub
