# 🚛 Kontejnery Ostrava – Rozdělení aplikací na GitHubu

V rámci repozitáře `Kont-premysl-sliva/Kont_2026` jsou přehledně odděleny **3 samostatné platformy** do vlastních složek (se zachováním hlavních rozcestníků v kořenu repozitáře pro zpětnou kompatibilitu).
U každé ze tří platforem se při změně **automaticky navyšuje číslo verze (+1)** a v archivu každé složky jsou automaticky udržovány **poslední 3 verze** (všechny starší verze se automaticky mažou).

---

## 💻 1. WEB PC Aplikace (`pc/`)
- **Cesta v repozitáři**: Složka `/pc/` (`/pc/index.html`, `/pc/version.json`) a kořenový odkaz `/index.html`.
- **Archiv posledních 3 verzí**: `/pc/S_POHODA_Kontejnery_PC_v{VERZE}.html` (např. poslední 3 verze).
- **Určení**: Pro dispečery a administrátory na PC a noteboocích.
- **Funkce**:
  - Kompletní správa přepravy, zakázek, kontejnerů, fakturace a adresáře.
  - Automatická synchronizace s Firebase Cloud Firestore a GitHubem.

---

## 🤖 2. Android verze (`android/` & `/app`)
- **Cesta v repozitáři**: Složka `/android/` (`/android/version.json`, `/android/README.md`) a zdrojový projekt v `/app/`.
- **Archiv posledních 3 verzí**: `/android/Kontejnery_Ostrava_Android_v{VERZE}.json` (manifesty a odkazy na poslední 3 sestavení).
- **Určení**: Pro Android telefony a tablety řidičů a dispečerů v terénu.
- **Architektura**: Kotlin + Jetpack Compose (Material Design 3) s automatickým navyšováním `versionCode` a `APP_VERSION_NUMBER` v `app/build.gradle.kts` při každé změně zdrojového kódu.

---

## 🍎 3. iPhone verze (`iphone/`)
- **Cesta v repozitáři**: Složka `/iphone/` (`/iphone/iphone.html`, `/iphone/version.json`) a kořenové odkazy `/iphone.html`, `/Kontejnery_iPhone.mobileconfig`, `/Kontejnery_Preprava_iPhone.ipa`.
- **Archiv posledních 3 verzí**:
  - `/iphone/Kontejnery_iPhone_v{VERZE}.html`
  - `/iphone/Kontejnery_iPhone_v{VERZE}.mobileconfig`
  - `/iphone/Kontejnery_Preprava_iPhone_v{VERZE}.ipa`
- **Určení**: Pro řidiče na iPhone / iOS s instalací přímo na plochu bez App Store.

---

## 🔄 Automatické verzování a rotace posledních 3 verzí
- Při každé úpravě zdrojového kódu nebo uložení aktualizace na GitHub se verze automaticky navýší o `+1`.
- Ve složkách `pc/`, `android/`, `iphone/` i v kořenu repozitáře se automaticky ponechávají **pouze poslední 3 verze**, aby se verze jednotlivých platforem mezi sebou nepletly a repozitář zůstával čistý.
