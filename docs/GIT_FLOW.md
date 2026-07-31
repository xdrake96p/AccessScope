# AccessScope — Git Flow (canonico)

> **Fonte di verità** del flusso Git del repository.  
> Gate obbligatorio (prima di branch/commit/push/tag): [`.cursor/rules/git-flow-gate.mdc`](../.cursor/rules/git-flow-gate.mdc)  
> Regola Cursor sintetica: [`.cursor/rules/git-release-workflow.mdc`](../.cursor/rules/git-release-workflow.mdc)  
> Release plugin (dettaglio tecnico): [`.cursor/rules/plugin-release-workflow.mdc`](../.cursor/rules/plugin-release-workflow.mdc) · [`.cursor/rules/plugin-parity.mdc`](../.cursor/rules/plugin-parity.mdc)

## 1. Scopo

Garantire che:

1. tutto lo sviluppo passi da `develop`;
2. `main` contenga **solo** ciò che è (o sarà) rilasciato;
3. ogni tag `v*` pubblichi **app + plugin** via CI;
4. dopo ogni release si riparta da `develop` con versioni già pronte al ciclo successivo.

---

## 2. Branch permanenti

| Branch | Ruolo |
|--------|--------|
| `develop` | Sviluppo quotidiano. **Tutti** i branch di lavoro partono da qui (salvo hotfix). |
| `main` | Stabile, allineata alle release pubblicate. Vietati commit di feature diretti. |

**Vietato** taggare su `develop`. I tag vivono **solo** su commit di `main`.

---

## 3. Nomenclatura branch di lavoro

Sempre da `develop` aggiornata (`git pull`), tranne `hotfix/` (da `main`).

| Prefisso | Quando usarlo | Esempio |
|----------|---------------|---------|
| `feature/` | Nuova funzionalità user-facing o modulo nuovo | `feature/maestro-wait-planner` |
| `bugfix/` | Fix non urgente (non bloccante in produzione) | `bugfix/scan-crash-api34` |
| `chore/` | Tooling, CI, refactor senza feature utente, regole repo | `chore/git-flow-docs` |
| `docs/` | Solo documentazione | `docs/manuale-utente` |
| `hotfix/` | Fix **urgente** già in produzione — **solo da `main`** | `hotfix/1.3.2-signing` |

### Regole naming

- kebab-case, massimo ~50 caratteri;
- un topic = un branch (niente “restyle + release” sullo stesso branch);
- **vietati** branch senza prefisso (`restyle`, `fix-ui`, `davide`, …);
- niente versioni nel nome `feature/` (`feature/v1.4-foo` → no); la versione sta nel commit/tag di release.

---

## 4. Flusso giornaliero (feature / bugfix / chore / docs)

```text
develop ──checkout -b──► feature/nome ──PR/merge──► develop ──delete branch──► (fine)
```

1. `git checkout develop && git pull origin develop`
2. `git checkout -b feature/nome` (o `bugfix/` / `chore/` / `docs/`)
3. Commit atomici, messaggi imperativi (es. `Fix tap target on scroll containers`)
4. Push + **PR verso `develop`** (preferito). Merge diretto solo se lavoro solitario e `develop` pulita.
5. Dopo il merge: **eliminare il branch locale e remoto** (obbligatorio).
6. **Mai** merge di un `feature/*` direttamente in `main`.

### Eliminazione branch (obbligatoria)

```bash
git checkout develop
git pull origin develop
git branch -d feature/nome          # locale
git push origin --delete feature/nome   # remoto
```

Su GitHub: spuntare **Delete branch** al merge della PR.  
Branch stale > 14 giorni: cleanup periodico.

---

## 5. Release — sequenza obbligatoria

Due versioni indipendenti:

| Artefatto | Dove | Esempio attuale |
|-----------|------|-----------------|
| **App** | `app/build.gradle.kts` → `versionName` + `versionCode` | `1.3.1` / `5` |
| **Plugin** AS + VS Code | stessa stringa SemVer in entrambi | `1.0.8` |

- Android Studio: `access-scope-plugin/android-studio-plugin/build.gradle.kts` → `version`
- VS Code/Cursor: `access-scope-plugin/vscode-extension/package.json` → `version`
- Novità: `whats-new/<ver>.html` + sezione `## <ver>` in `CHANGELOG.md` (parità obbligatoria)

### 5.1 Commit di release su `develop`

Quando `develop` è stabile e pronta da pubblicare:

1. Aggiornare **app** (`versionCode` / `versionName`) se questa release pubblica una nuova app.
2. Se in questa release ci sono **cambiamenti plugin** (UI IDE, CLI, comandi, bridge, artefatti):
   - bump **stessa** versione AS + VS Code;
   - creare `whats-new/<ver>.html`;
   - aggiornare `CHANGELOG.md`;
   - CLI fatJar + copia in `vscode-extension/bin/` se il CLI è cambiato;
   - opzionale: cartella `access-scope-plugin/releases/<ver>/`.
3. Documentare in `docs/PROJECT.md` cosa cambia per **app** e/o **plugin**.
4. Commit dedicato, es.:

   ```text
   release: AccessScope 1.4.0 (plugin 1.0.9)
   ```

   Se i plugin non cambiano: `release: AccessScope 1.4.0 (plugin invariato 1.0.8)`.

### 5.2 Promote su `main` + tag

```bash
git checkout develop && git pull origin develop
# (commit release già su develop)

git checkout main && git pull origin main
git merge develop          # no squash: preserva storia
git push origin main

git tag -a v1.4.0 -m "AccessScope 1.4.0"
git push origin v1.4.0
```

- Il tag **deve** corrispondere a `versionName` dell’app con prefisso `v` (`1.4.0` → `v1.4.0`).
- CI (`.github/workflows/release.yml`) parte sul push del tag `v*`:
  - verifica che il commit del tag sia su `main`;
  - builda APK release firmato;
  - builda plugin AS (ZIP) + VSIX;
  - pubblica GitHub Release con APK, `release-manifest.json` (include `minPluginVersion` = versione plugin nel repo), ZIP e VSIX.

**Quindi: tag = pubblicazione app + plugin** alla versione presente nei file al momento del tag.

### 5.3 Dopo il tag — ripartenza su `develop` (obbligatoria)

Subito dopo una release riuscita, su `develop`:

1. **Aumentare sempre** la versione **app** al prossimo SemVer di sviluppo (es. `1.4.0` rilasciata → develop a `1.4.1` o `1.5.0-dev` policy: preferire bump patch `versionName` + `versionCode++` per il ciclo successivo).
2. **Valutare** se bumpare anche i **plugin**:
   - **Sì**, se il ciclo successivo toccherà IDE/CLI/bridge/parità, oppure se `minPluginVersion` dovrà salire con la prossima app.
   - **No**, se il lavoro previsto è solo app e i plugin restano compatibili così come pubblicati.
3. Commit dedicato, es.:

   ```text
   chore: bump develop to 1.4.1 after v1.4.0 release
   ```

   oppure, se servono anche i plugin:

   ```text
   chore: bump develop app 1.4.1 + plugin 1.0.10 after v1.4.0
   ```

Criterio rapido “serve bump plugin?”:

| Situazione sul prossimo ciclo | Bump plugin? |
|-------------------------------|--------------|
| Solo motori scan / UI app / Maestro in-app | No |
| Comandi IDE, sidebar, install/fetch CLI, bridge | Sì |
| Nuova app richiede plugin minimo più alto | Sì (e allineare novità/changelog) |
| Solo docs Git / regole Cursor | No |

---

## 6. Hotfix (eccezione)

Solo per fix urgenti già in produzione:

1. `git checkout main && git pull`
2. `git checkout -b hotfix/1.3.2-descrizione`
3. Fix + bump patch app (e plugin solo se il fix li riguarda)
4. Merge in `main` → tag patch → push tag (CI pubblica di nuovo)
5. **Merge o cherry-pick obbligatorio su `develop`**
6. Delete branch hotfix (locale + remoto)
7. Su `develop`: di nuovo passo **5.3** (bump app post-release; valuta plugin)

---

## 7. Cosa non fare

- Tag o release da `feature/*` / `bugfix/*` / direttamente da `develop`
- Force-push su `develop` o `main`
- Lasciare branch già mergiati su remoto
- Bump versione “a caso” dentro un feature branch (farlo nel commit `release:` o nel `chore:` post-release)
- Pubblicare plugin AS e VS Code a versioni diverse
- Saltare `whats-new` / `CHANGELOG` quando si bumpa il plugin
- Sviluppare feature su `main`

---

## 8. Comandi di riferimento (happy path)

```bash
# --- lavoro ---
git checkout develop && git pull origin develop
git checkout -b feature/mia-feature
# ... commit ...
git push -u origin HEAD
# apri PR → merge in develop → Delete branch

git checkout develop && git pull origin develop
git branch -d feature/mia-feature
git push origin --delete feature/mia-feature

# --- release ---
git checkout develop && git pull origin develop
# modifica versioni + PROJECT.md + (se serve) plugin whats-new/CHANGELOG
git add -A && git commit -m "release: AccessScope 1.4.0 (plugin 1.0.9)"

git checkout main && git pull origin main
git merge develop
git push origin main
git tag -a v1.4.0 -m "AccessScope 1.4.0"
git push origin v1.4.0

# --- ripartenza develop ---
git checkout develop && git pull origin develop
# bump app (sempre) + valuta plugin
git add -A && git commit -m "chore: bump develop to 1.4.1 after v1.4.0 release"
git push origin develop
```

---

## 9. Checklist

### Prima di mergiare un branch in `develop`

- [ ] Branch creato da `develop` aggiornata (hotfix da `main`)
- [ ] Prefisso naming corretto
- [ ] Target PR = `develop` (non `main`)
- [ ] Dopo merge: branch eliminato locale **e** remoto

### Prima del tag di release

- [ ] Commit `release:` su `develop` con note in `docs/PROJECT.md`
- [ ] App: `versionName` / `versionCode` allineati al tag previsto
- [ ] Se plugin cambiati: stessa versione AS+VS Code, `whats-new`, `CHANGELOG`, CLI se toccato
- [ ] Merge `develop` → `main` completato
- [ ] Tag annotated `vX.Y.Z` **solo** su `main`
- [ ] Push tag → verificare GitHub Release (APK + ZIP + VSIX + manifest)

### Subito dopo la release

- [ ] Su `develop`: bump **app** per il ciclo successivo
- [ ] Valutato bump **plugin** (sì/no secondo tabella §5.3)
- [ ] Commit `chore:` pushato su `develop`
