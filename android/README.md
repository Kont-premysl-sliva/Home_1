# 🤖 2. Android verze – Kontejnery Ostrava (`android/`)

Tato složka obsahuje metadata a archivy posledních **3 verzí** pro **Android verzi** aplikace Kontejnery Ostrava.

## 📦 Ukládání posledních 3 verzí
- `android/version.json` – přehled aktuální verze Android aplikace a seznam posledních 3 uložených verzí.
- `android/Kontejnery_Ostrava_Android_v{VERZE}.json` – manifesty posledních 3 verzí Android aplikace (starší verze nad limit 3 jsou automaticky mazány při sestavení i při synchronizaci s GitHubem).

## 📱 Architektura a automatické verzování
- **Zdrojový kód aplikace**: Složka `/app` v kořeni repozitáře.
- **Jazyk & UI**: Kotlin, Jetpack Compose, Material Design 3.
- **Automatické navýšení verze**: V `app/build.gradle.kts` i `GitHubBackupManager.kt` je nastaveno automatické navýšení verze (`+1`) při každé změně zdrojového kódu nebo publikaci změn na GitHub.
