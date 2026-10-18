# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Running the App

This is a static web app — no build step for web. Open `index.html` directly in a browser, or serve with any static file server:

```bash
python3 -m http.server 8080
# then open http://localhost:8080
```

The `<base href="/sunday/">` tag is automatically neutralized for localhost by an inline script in `index.html`, so local dev works without path adjustments.

GitHub Pages serves the static web app and PWA directly from the repo root.

## Native Android Build (Capacitor)

The repository also includes an official native Android build wrapped with Capacitor.

- `package.json`: provides Capacitor CLI and Android platform tooling
- `capacitor.config.json`: configuration with `appId: com.schwartzinc.sunday`, `webDir: www`, `androidScheme: https`
- `scripts/build-web.mjs`: packages required web assets into `www/` (whitelist: `index.html`, `app.js`, `styles.css`, `manifest.webmanifest`, `service-worker.js`, `models/`, `vendor/`, `icons/`, `banners/`). **When adding new top-level web assets or directories, add them to `scripts/build-web.mjs` as well as `service-worker.js`!**
- `npm run cap:sync`: runs `build-web.mjs` and synchronizes assets to `android/`
- `.github/workflows/android.yml`: automated GitHub Actions workflow that compiles APKs on push and attaches release APKs to tagged releases

## Architecture

Single-page vanilla JS app. All logic lives in one `TaskManager` class in `app.js`. No framework, no bundler.

**Data flow:**
- On load: `initPersistMode()` (top of `app.js`) prompts once for storage choice, then `TaskManager` constructor calls `loadFromDb()` → populates `this.lists`
- On change: every mutation calls `saveToDb()` immediately
- Storage blob shape: `{ lists, deletedTasks, tags, updatedAt }`

**Core state (on `TaskManager` instance):**
- `this.lists` — `{ 'on-it': TaskList, 'next-up': TaskList, 'back-log': TaskList }`
- `this.deletedTasks` — archived (completed) tasks
- `this.globalTags` — `{ [normalizedName]: { name, color } }`, auto-assigned from `TAG_COLORS` array (14 presets)
- `this.currentlyEditingTask` — task open in the side panel (or `{ columnId, parentTask }` for new tasks)
- `this._panelPendingTags` — tags staged for a not-yet-saved new task

**Models** (`models/task.js`, `models/taskList.js`):
- `Task`: id, name, description (Quill HTML), url, completed, subtasks (nested `Task[]`), tags (array of normalized tag keys)
- `TaskList`: type string + tasks array; `fromJSON`/`toJSON` round-trip

**Persistence modes** (set once via `localStorage['persistMode']`):
- `'LocalStorage'` — reads/writes `localStorage['taskData']`
- `'AirTable'` — syncs to a single Airtable record's `data` field (long text, JSON). Sync merge uses `updatedAt` timestamp — newer side wins; tasks only in the weaker side are dropped if they appear in the stronger side's `deletedTasks`.

**UI panels** (slide-in overlays, toggled via `.active` class):
- `#task-panel` — create/edit a task; hosts Quill editor, subtask list, tag picker
- `#subtask-panel` — create/edit a subtask
- `#deleted-tasks-panel` — view/restore completed tasks

**Drag and drop:**
- Tasks drag between columns; dropping a task *onto* another task converts it to a subtask
- Subtasks in the panel can be dragged out to a main column (promotes to top-level task)
- Order changes call `updateTaskOrder(columnId)` → `saveToDb()`

**Tag dropdown:** custom vanilla implementation appended to `document.body` with `position: fixed`. No third-party library (Algolia was removed due to positioning bugs). Button uses `#` character.

**Quill editors:** two instances (`this.taskQuill`, `this.subtaskQuill`) initialized in constructor with syntax highlighting via highlight.js. Supported code block languages: `plaintext`, `bash`, `diff`, `json`, `xml`, `yaml`, `typescript`.

**Service worker** (`service-worker.js`): caches all static assets under `CACHE_NAME = 'sunday-app-cache-v8'`. When adding new vendor files, add them to `ASSETS_TO_CACHE`. The cache name must be bumped to invalidate old caches. Inside native Android Capacitor runtime, service worker registration is bypassed.

## Version Stamp

`app.js` line 2 holds `APP_VERSION` — a string in `'YYYY-MM-DD HH:MM UTC'` format.
**Update it on every code change** so the dev version badge (triple-click the banner) always reflects the latest edit timestamp.

## Key Conventions

- Task IDs are `Date.now().toString()` — millisecond timestamps as strings
- `updateTaskElement(task)` re-renders an existing task card in-place (replaces innerHTML + re-attaches all listeners); used after any mutation to a task that's already in the DOM
- `createTaskElement(task, columnId)` builds and appends a new card; only called when adding a brand-new task
- Tag keys are normalized names (lowercase); `globalTags[key].name` holds display name
- The `vendor/` directory contains checked-in copies of Quill, highlight.js, Airtable SDK, and Playpen Sans font — do not CDN-load these

## Theme / Colors

Dark theme with orange accents defined in `styles.css`:
- Primary: `#ff6b2b`
- Background: `#1a1a1a`
- Secondary background: `#2d2d2d`
- Text: `#e0e0e0`
