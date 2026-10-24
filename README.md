# Call to Artists

Personal Android app that tracks UK open calls for artists: deadlines, hand-in days, private views and fees. It adds dates to your calendar with reminders and shows a countdown widget on the home screen.

**Build:** v0.1.0 (build 1, 2026-10-24) is shown on the Settings screen.

## What it does

| Feature | How |
|---|---|
| Society and prize watch-list | Seeded with checked dates: NPG Portrait Award, Hilliard, Pastel Society, RBA, RI, RMS, plus RP, RA, Jackson's and RP Drawing as "expected". Unknown fields are left blank, never guessed. |
| New calls | Reads the Artquest opportunities page on the phone (native HTTP, no server). Rechecks every 12 h when opened, and a daily background check sends a notification when new listings appear. |
| Other sites | Share a page from Chrome or email into the app (Share → Call to Artists). Dates in the text are detected for you to confirm. CuratorSpace and a-n are not scraped, because of their terms and the a-n members-only paywall. |
| Calendar | Writes directly to the Samsung or Google calendar you choose, with reminders 28, 7 and 1 days before (editable). A Google calendar shows in both apps. |
| Widget | Home-screen countdown to the next deadline, followed by the one after it. |
| Tracker | Interested → Preparing → Submitted → Pre-selected → Selected / Not selected → Delivered → Sold / Collected. |
| Ledger | Fees, costs and income per call, with yearly net totals. |
| Backup | Export or import a JSON backup through the Android share sheet. |

Each call carries a confidence label:
- **Confirmed:** taken from the organiser's site.
- **Listed:** taken from an aggregator. Check it on the organiser's site before you enter.
- **Expected:** this year's dates haven't been announced yet.

## Get the APK

**Option A: GitHub (no setup)**
Every push to `main` builds a debug APK. Go to **Actions → Build Android APK → latest run → Artifacts**, download the zip and unzip it. Install `app-debug.apk` on the phone; you'll need to allow installs from your browser or file manager.

**Option B: Android Studio (Windows)**
```
git clone https://github.com/sourmilkman/call-to-artists
cd call-to-artists
npm install
npx cap sync android
npx cap open android
```
Then click **Run ▶** with the S21 connected over USB (USB debugging on).

## Editing

- The app UI is the single file `www/index.html`. Run `npx cap sync android` after any change.
- Native code is in `android/app/src/main/java/uk/co/ateliermulliner/calltoartists/`:
  - `CtaNativePlugin.java`: calendar, widget data, share intake, export
  - `DeadlineWidget.java`: the home-screen widget
  - `RefreshWorker.java`: the daily Artquest check
- When you bump the version, change `BUILD` in `index.html` and `versionName`/`versionCode` in `android/app/build.gradle` together.

## Known limits

- If Artquest changes its page layout, the reader can stop finding listings. Settings shows a warning when that happens.
- Calendar events already added stay in your calendar if you edit or delete the call in the app.
- Debug APKs are signed with a debug key. Uninstall before switching to a release-signed build.
