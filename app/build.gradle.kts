import java.awt.BasicStroke
import java.awt.Color as AwtColor
import java.awt.Font as AwtFont
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  id("com.google.gms.google-services")
}

// ============================================================================
// AUTOMATICKÉ VERZOVÁNÍ (+1 PŘI KAŽDÉ ZMĚNĚ ZDROJOVÉHO KÓDU) A SYNCHRONIZACE
// ============================================================================
val autoVersionInfo: Pair<Int, Boolean> = run {
  val gradlePropsFile = File(rootDir, "gradle.properties")
  val srcHtmlFile = File(rootDir, "index.html")
  val navKtFile = file("src/main/java/com/example/ui/AppNavigation.kt")
  val updateMgrKtFile = file("src/main/java/com/example/ui/AppUpdateManager.kt")
  val javaSrcDir = file("src/main/java")

  fun computeSourceFingerprint(): String {
    val md = MessageDigest.getInstance("SHA-256")
    if (srcHtmlFile.exists()) {
      md.update("FILE:index.html\n".toByteArray(Charsets.UTF_8))
      md.update(srcHtmlFile.readBytes())
    }
    if (javaSrcDir.exists()) {
      val ktFiles = javaSrcDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && it.name != "AppNavigation.kt" && it.name != "AppUpdateManager.kt" }
        .sortedBy { it.invariantSeparatorsPath }
        .toList()
      for (f in ktFiles) {
        md.update("FILE:${f.relativeTo(rootDir).invariantSeparatorsPath}\n".toByteArray(Charsets.UTF_8))
        md.update(f.readBytes())
      }
    }
    val resValuesDir = file("src/main/res/values")
    if (resValuesDir.exists()) {
      val xmlFiles = resValuesDir.walkTopDown().filter { it.isFile && it.extension == "xml" }.sortedBy { it.invariantSeparatorsPath }.toList()
      for (f in xmlFiles) {
        md.update("FILE:${f.relativeTo(rootDir).invariantSeparatorsPath}\n".toByteArray(Charsets.UTF_8))
        md.update(f.readBytes())
      }
    }
    val manifestFile = file("src/main/AndroidManifest.xml")
    if (manifestFile.exists()) {
      md.update("FILE:AndroidManifest.xml\n".toByteArray(Charsets.UTF_8))
      md.update(manifestFile.readBytes())
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  val propsText = if (gradlePropsFile.exists()) gradlePropsFile.readText(Charsets.UTF_8) else ""
  val storedVer = Regex("""(?m)^app\.auto\.version\s*=\s*(\d+)""").find(propsText)?.groupValues?.get(1)?.toIntOrNull() ?: 355
  val storedHash = Regex("""(?m)^app\.auto\.sourceHash\s*=\s*([0-9a-fA-F_a-zA-Z]+)""").find(propsText)?.groupValues?.get(1)?.trim() ?: ""

  val htmlTextInitial = if (srcHtmlFile.exists()) srcHtmlFile.readText(Charsets.UTF_8) else ""
  val htmlVer = Regex("""const CURRENT_SW_VERSION\s*=\s*(\d+);""").find(htmlTextInitial)?.groupValues?.get(1)?.toIntOrNull() ?: storedVer
  val navTextInitial = if (navKtFile.exists()) navKtFile.readText(Charsets.UTF_8) else ""
  val ktVer = Regex("""const val APP_VERSION_NUMBER\s*=\s*(\d+)""").find(navTextInitial)?.groupValues?.get(1)?.toIntOrNull() ?: storedVer

  val preHash = computeSourceFingerprint()
  val sourceChanged = preHash != storedHash

  val newVer = if (sourceChanged) {
    val highestBase = maxOf(storedVer, htmlVer, ktVer)
    highestBase + 1
  } else {
    maxOf(storedVer, htmlVer, ktVer)
  }

  if (sourceChanged || htmlVer != newVer || ktVer != newVer) {
    val newVerStr = newVer.toString()
    val nowDate = LocalDate.now(ZoneId.of("Europe/Prague"))
    val todayDot = nowDate.format(DateTimeFormatter.ofPattern("d.M.yyyy"))
    val todaySpaced = nowDate.format(DateTimeFormatter.ofPattern("d. M. yyyy"))

    if (srcHtmlFile.exists()) {
      var updatedHtml = srcHtmlFile.readText(Charsets.UTF_8)
      val oldHtmlVer = Regex("""const CURRENT_SW_VERSION\s*=\s*(\d+);""").find(updatedHtml)?.groupValues?.get(1) ?: storedVer.toString()
      updatedHtml = updatedHtml
        .replace(Regex("""const CURRENT_SW_VERSION\s*=\s*\d+;"""), "const CURRENT_SW_VERSION = $newVerStr;")
        .replace(Regex("""const CURRENT_SW_VERSION_NAME\s*=\s*"\d+";"""), """const CURRENT_SW_VERSION_NAME = "$newVerStr";""")
        .replace(Regex("""const CURRENT_SW_RELEASE_DATE\s*=\s*"[^"]+";"""), """const CURRENT_SW_RELEASE_DATE = "$todayDot";""")
        .replace(Regex("""const CURRENT_SW_RELEASE_DATE_SPACED\s*=\s*"[^"]+";"""), """const CURRENT_SW_RELEASE_DATE_SPACED = "$todaySpaced";""")
        .replace(Regex("""cachedVer\s*>\s*\d+"""), "cachedVer > $newVerStr")
        .replace(Regex("""SEKCE PŘEPRAVA v\d+"""), "SEKCE PŘEPRAVA v$newVerStr")
        .replace(Regex("""\|\|\s*'$oldHtmlVer'"""), "|| '$newVerStr'")
        .replace("v $oldHtmlVer", "v $newVerStr")
      srcHtmlFile.writeText(updatedHtml, Charsets.UTF_8)
    }

    if (navKtFile.exists()) {
      var updatedNav = navKtFile.readText(Charsets.UTF_8)
      updatedNav = updatedNav
        .replace(Regex("""const val APP_VERSION_NUMBER\s*=\s*\d+"""), "const val APP_VERSION_NUMBER = $newVerStr")
        .replace(Regex("""const val APP_VERSION_NAME\s*=\s*"\d+""""), """const val APP_VERSION_NAME = "$newVerStr"""")
        .replace(Regex("""const val APP_VERSION_BADGE\s*=\s*"[^"]+""""), """const val APP_VERSION_BADGE = "v $newVerStr • $todayDot"""")
        .replace(Regex("""const val APP_LAST_UPDATE_DATE\s*=\s*"[^"]+""""), """const val APP_LAST_UPDATE_DATE = "$todayDot"""")
      navKtFile.writeText(updatedNav, Charsets.UTF_8)
    }

    if (updateMgrKtFile.exists()) {
      var updatedMgr = updateMgrKtFile.readText(Charsets.UTF_8)
      updatedMgr = updatedMgr
        .replace(Regex("""const val LATEST_CLOUD_VERSION_NAME\s*=\s*"\d+""""), """const val LATEST_CLOUD_VERSION_NAME = "$newVerStr"""")
        .replace(Regex("""const val LATEST_CLOUD_VERSION_CODE\s*=\s*\d+"""), "const val LATEST_CLOUD_VERSION_CODE = $newVerStr")
        .replace(Regex("""const val LATEST_CLOUD_UPDATE_DATE\s*=\s*"[^"]+""""), """const val LATEST_CLOUD_UPDATE_DATE = "$todayDot"""")
      updateMgrKtFile.writeText(updatedMgr, Charsets.UTF_8)
    }

    val postHash = computeSourceFingerprint()
    if (gradlePropsFile.exists()) {
      var newProps = gradlePropsFile.readText(Charsets.UTF_8)
      newProps = if (Regex("""(?m)^app\.auto\.version\s*=""").containsMatchIn(newProps)) {
        newProps.replace(Regex("""(?m)^app\.auto\.version\s*=.*$"""), "app.auto.version=$newVerStr")
      } else {
        "$newProps\napp.auto.version=$newVerStr"
      }
      newProps = if (Regex("""(?m)^app\.auto\.sourceHash\s*=""").containsMatchIn(newProps)) {
        newProps.replace(Regex("""(?m)^app\.auto\.sourceHash\s*=.*$"""), "app.auto.sourceHash=$postHash")
      } else {
        "$newProps\napp.auto.sourceHash=$postHash"
      }
      gradlePropsFile.writeText(newProps, Charsets.UTF_8)
    }
    println("Auto-version bump: $storedVer -> $newVerStr (sourceChanged=$sourceChanged)")
  }
  Pair(newVer, sourceChanged)
}
val autoResolvedAppVersion: Int = autoVersionInfo.first
val autoSourceChanged: Boolean = autoVersionInfo.second

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
    applicationId = "com.aistudio.containerscanner.cxkqab"
    minSdk = 24
    targetSdk = 36
    versionCode = autoResolvedAppVersion
    versionName = autoResolvedAppVersion.toString()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  sourceSets {
    getByName("main") {
      assets.srcDirs("src/main/assets", "web-aplikace")
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Keep index.html synchronized and generate blue "K" launcher icons across all mipmap densities
run {
  val mipmapSpecs = listOf(
    "mipmap-mdpi" to 48,
    "mipmap-hdpi" to 72,
    "mipmap-xhdpi" to 96,
    "mipmap-xxhdpi" to 144,
    "mipmap-xxxhdpi" to 192
  )
  for ((folder, size) in mipmapSpecs) {
    val dir = file("src/main/res/$folder")
    if (!dir.exists()) dir.mkdirs()
    File(dir, "ic_launcher.webp").delete()
    File(dir, "ic_launcher_round.webp").delete()

    fun renderKIcon(round: Boolean): BufferedImage {
      val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
      val g = img.createGraphics()
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      val s = size.toFloat()
      if (round) {
        g.clip = Ellipse2D.Float(0f, 0f, s, s)
      }
      g.paint = GradientPaint(0f, 0f, AwtColor(0x0A, 0x2E, 0x6E), s, s, AwtColor(0x01, 0x1C, 0x45))
      if (round) {
        g.fill(Ellipse2D.Float(0f, 0f, s, s))
      } else {
        val arc = s * 0.22f
        g.fill(RoundRectangle2D.Float(0f, 0f, s, s, arc, arc))
      }
      // Subtle inner border
      g.color = AwtColor(255, 255, 255, 70)
      g.stroke = BasicStroke((s * 0.03f).coerceAtLeast(1f))
      val pad = s * 0.12f
      if (round) {
        g.draw(Ellipse2D.Float(pad, pad, s - 2 * pad, s - 2 * pad))
      } else {
        val innerArc = s * 0.16f
        g.draw(RoundRectangle2D.Float(pad, pad, s - 2 * pad, s - 2 * pad, innerArc, innerArc))
      }
      // Bold white letter K centered
      g.color = AwtColor.WHITE
      val font = AwtFont("SansSerif", AwtFont.BOLD, (s * 0.62f).toInt())
      g.font = font
      val fm = g.fontMetrics
      val text = "K"
      val tx = (s - fm.stringWidth(text)) / 2f
      val ty = (s - fm.height) / 2f + fm.ascent
      g.drawString(text, tx, ty)
      g.dispose()
      return img
    }

    ImageIO.write(renderKIcon(false), "PNG", File(dir, "ic_launcher.png"))
    ImageIO.write(renderKIcon(true), "PNG", File(dir, "ic_launcher_round.png"))
  }

  val srcHtml = File(rootDir, "index.html")
  providers.fileContents(layout.projectDirectory.file("../index.html")).asText.orNull
  if (srcHtml.exists()) {
    val assetDir = file("src/main/assets")
    if (!assetDir.exists()) assetDir.mkdirs()
    val webAppDir = file("src/main/assets/web-aplikace")
    if (!webAppDir.exists()) webAppDir.mkdirs()

    srcHtml.copyTo(File(assetDir, "index.html"), overwrite = true)
    srcHtml.copyTo(File(webAppDir, "index.html"), overwrite = true)

    // Udržujeme pouze poslední 3 verze programu pro každou ze 3 platforem (1. WEB PC, 2. Android, 3. iPhone):
    val curVerText = srcHtml.readText(Charsets.UTF_8)
    val currentAppVer = autoResolvedAppVersion
    val currentAppVerStr = currentAppVer.toString()
    val keptVers = listOf(currentAppVer, currentAppVer - 1, currentAppVer - 2).filter { it > 0 }
    val keptVersJsonArray = keptVers.joinToString(",") { "\"v$it\"" }
    val todayDot = LocalDate.now(ZoneId.of("Europe/Prague")).format(DateTimeFormatter.ofPattern("d.M.yyyy"))

    // Automatické vygenerování iphone.html se zapnutým iOS režimem a aktuální verzí
    val iphoneHtmlText = curVerText.replaceFirst(
      "<head>",
      "<head><script>window.FORCE_IPHONE_APP_MODE = true; try { localStorage.setItem(\"ko_ios_app_mode\",\"1\"); } catch(e){}</script>"
    )
    File(rootDir, "iphone.html").writeText(iphoneHtmlText, Charsets.UTF_8)
    File(assetDir, "iphone.html").writeText(iphoneHtmlText, Charsets.UTF_8)

    // Automatická aktualizace Kontejnery_iPhone.mobileconfig s vloženou ikonou na aktuální verzi
    val iconPngFile = File(file("src/main/res/mipmap-xxxhdpi"), "ic_launcher.png")
    val iconPngBytes = if (iconPngFile.exists()) iconPngFile.readBytes() else ByteArray(0)
    val iconBase64Tag = if (iconPngBytes.isNotEmpty()) {
      "            <key>Icon</key>\n            <data>${Base64.getEncoder().encodeToString(iconPngBytes)}</data>\n"
    } else ""

    val mobileConfigText = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>PayloadContent</key>
    <array>
        <dict>
            <key>FullScreen</key>
            <true/>
$iconBase64Tag            <key>IgnoreManifestScope</key>
            <true/>
            <key>IsRemovable</key>
            <true/>
            <key>Label</key>
            <string>Přeprava KO</string>
            <key>PayloadDescription</key>
            <string>Mobilní aplikace pro řidiče (Sekce Přeprava) • Kontejnery Ostrava v $currentAppVerStr</string>
            <key>PayloadDisplayName</key>
            <string>Přeprava KO – Řidič (iPhone)</string>
            <key>PayloadIdentifier</key>
            <string>cz.kontejneryostrava.ios.webclip.v$currentAppVerStr</string>
            <key>PayloadType</key>
            <string>com.apple.webClip.managed</string>
            <key>PayloadUUID</key>
            <string>6A4E8F12-90B1-4C3D-A812-000000000$currentAppVerStr</string>
            <key>PayloadVersion</key>
            <integer>1</integer>
            <key>Precomposed</key>
            <true/>
            <key>URL</key>
            <string>https://kont-premysl-sliva.github.io/Home_1/iphone.html?iphone=1</string>
        </dict>
    </array>
    <key>PayloadDescription</key>
    <string>Nainstaluje zkrácenou mobilní aplikaci Přeprava KO pro řidiče (v $currentAppVerStr) na plochu iPhone v režimu celé obrazovky.</string>
    <key>PayloadDisplayName</key>
    <string>Přeprava KO • Aplikace pro řidiče (iPhone) • v $currentAppVerStr</string>
    <key>PayloadIdentifier</key>
    <string>cz.kontejneryostrava.ios.profile.v$currentAppVerStr</string>
    <key>PayloadOrganization</key>
    <string>Kontejnery Ostrava s.r.o.</string>
    <key>PayloadRemovalDisallowed</key>
    <false/>
    <key>PayloadType</key>
    <string>Configuration</string>
    <key>PayloadUUID</key>
    <string>9B7C2D45-11E2-4F8A-B$currentAppVerStr-000000000$currentAppVerStr</string>
    <key>PayloadVersion</key>
    <integer>1</integer>
</dict>
</plist>
"""
    File(rootDir, "Kontejnery_iPhone.mobileconfig").writeText(mobileConfigText, Charsets.UTF_8)

    val srcInstallFile = File(rootDir, "install.html")
    val installHtmlText = if (srcInstallFile.exists()) {
      srcInstallFile.readText(Charsets.UTF_8).replace(Regex("""v\s+\d{3,}"""), "v $currentAppVerStr")
    } else ""
    if (installHtmlText.isNotEmpty()) {
      srcInstallFile.writeText(installHtmlText, Charsets.UTF_8)
      File(assetDir, "install.html").writeText(installHtmlText, Charsets.UTF_8)
      val installSubDir = File(rootDir, "install")
      if (!installSubDir.exists()) installSubDir.mkdirs()
      File(installSubDir, "index.html").writeText(installHtmlText, Charsets.UTF_8)
    }

    // Automatické sestavení iOS .IPA balíčku (Kontejnery_Preprava_iPhone.ipa) pro řidiče (sekce PŘEPRAVA + GitHub update)
    val ipaBos = ByteArrayOutputStream()
    ZipOutputStream(ipaBos).use { zos ->
      fun addZipEntry(entryName: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(data)
        zos.closeEntry()
      }
      val infoPlist = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>cs</string>
    <key>CFBundleDisplayName</key>
    <string>Přeprava KO</string>
    <key>CFBundleExecutable</key>
    <string>PrepravaKO</string>
    <key>CFBundleIdentifier</key>
    <string>cz.kontejneryostrava.preprava.driver</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>PrepravaKO</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>$currentAppVerStr.0</string>
    <key>CFBundleVersion</key>
    <string>$currentAppVerStr</string>
    <key>LSRequiresIPhoneOS</key>
    <true/>
    <key>KOAppRole</key>
    <string>DRIVER_PREPRAVA_ONLY</string>
    <key>KOGitHubRepo</key>
    <string>Kont-premysl-sliva/Home_1</string>
    <key>KOGitHubUpdateURL</key>
    <string>https://kont-premysl-sliva.github.io/Home_1/iphone.html</string>
</dict>
</plist>"""
      addZipEntry("Payload/PrepravaKO.app/Info.plist", infoPlist.toByteArray(Charsets.UTF_8))
      addZipEntry("Payload/PrepravaKO.app/PkgInfo", "APPL????".toByteArray(Charsets.UTF_8))
      addZipEntry("Payload/PrepravaKO.app/PrepravaKO", "#!/bin/sh\nexec \"$(dirname \"$0\")/www/iphone.html\"\n".toByteArray(Charsets.UTF_8))
      addZipEntry("Payload/PrepravaKO.app/www/iphone.html", iphoneHtmlText.toByteArray(Charsets.UTF_8))
      addZipEntry("Payload/PrepravaKO.app/Kontejnery_iPhone.mobileconfig", mobileConfigText.toByteArray(Charsets.UTF_8))
      if (iconPngBytes.isNotEmpty()) {
        addZipEntry("Payload/PrepravaKO.app/Icon.png", iconPngBytes)
        addZipEntry("Payload/PrepravaKO.app/AppIcon60x60@2x.png", iconPngBytes)
      }
    }
    val ipaBytes = ipaBos.toByteArray()
    File(rootDir, "Kontejnery_Preprava_iPhone.ipa").writeBytes(ipaBytes)
    File(assetDir, "Kontejnery_Preprava_iPhone.ipa").writeBytes(ipaBytes)

    // Kořen repozitáře: udržujeme poslední 3 verze S_POHODA_Kontejnery_v*.html
    srcHtml.copyTo(File(rootDir, "S_POHODA_Kontejnery_v${currentAppVer}.html"), overwrite = true)
    val verFileRegex = Regex("""S_POHODA_Kontejnery_v(\d+)\.html""")
    val allVerFiles = rootDir.listFiles { f -> verFileRegex.matches(f.name) }?.sortedByDescending { f ->
      verFileRegex.find(f.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    } ?: emptyList()
    allVerFiles.drop(3).forEach { it.delete() }

    // =========================================================================
    // ROZDĚLENÍ NA 3 SAMOSTATNÉ PLATFORMY (1. pc/, 2. android/, 3. iphone/)
    // U každé platformy udržujeme přesně poslední 3 verze!
    // =========================================================================
    fun pruneLocalPlatformFiles(dir: File, pattern: Regex, maxKeep: Int = 3) {
      val matched = dir.listFiles { f -> f.isFile && pattern.matches(f.name) }?.sortedByDescending { f ->
        pattern.find(f.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
      } ?: emptyList()
      matched.drop(maxKeep).forEach { it.delete() }
    }

    // 1. WEB PC APLIKACE (pc/)
    val pcDir = File(rootDir, "pc")
    if (!pcDir.exists()) pcDir.mkdirs()
    File(pcDir, "index.html").writeText(curVerText, Charsets.UTF_8)
    for (v in keptVers) {
      val f = File(pcDir, "S_POHODA_Kontejnery_PC_v$v.html")
      if (v == currentAppVer || !f.exists()) {
        f.writeText(curVerText, Charsets.UTF_8)
      }
    }
    pruneLocalPlatformFiles(pcDir, Regex("""S_POHODA_Kontejnery_PC_v(\d+)\.html"""), 3)
    val pcVersionJson = """{
  "platform": "WEB_PC_APLIKACE",
  "folder": "pc/",
  "currentVersion": $currentAppVer,
  "versionBadge": "v $currentAppVerStr",
  "releaseDate": "$todayDot",
  "maxKeptVersions": 3,
  "keptVersions": [$keptVersJsonArray],
  "files": [
    "pc/index.html",
    ${keptVers.joinToString(",\n    ") { "\"pc/S_POHODA_Kontejnery_PC_v$it.html\"" }}
  ]
}"""
    File(pcDir, "version.json").writeText(pcVersionJson, Charsets.UTF_8)

    // 2. ANDROID VERZE (android/)
    val androidDir = File(rootDir, "android")
    if (!androidDir.exists()) androidDir.mkdirs()
    for (v in keptVers) {
      val f = File(androidDir, "Kontejnery_Ostrava_Android_v$v.json")
      if (v == currentAppVer || !f.exists()) {
        val manifestJson = """{
  "platform": "ANDROID_VERZE",
  "folder": "android/",
  "applicationId": "com.aistudio.containerscanner.cxkqab",
  "versionCode": $v,
  "versionName": "$v.0",
  "releaseDate": "$todayDot",
  "apkPath": "android/Kontejnery_Ostrava_Android_v$v.apk"
}"""
        f.writeText(manifestJson, Charsets.UTF_8)
      }
    }
    pruneLocalPlatformFiles(androidDir, Regex("""Kontejnery_Ostrava_Android_v(\d+)\.json"""), 3)
    pruneLocalPlatformFiles(androidDir, Regex("""Kontejnery_Ostrava_Android_v(\d+)\.apk"""), 3)
    val androidVersionJson = """{
  "platform": "ANDROID_VERZE",
  "folder": "android/",
  "applicationId": "com.aistudio.containerscanner.cxkqab",
  "currentVersion": $currentAppVer,
  "versionCode": $currentAppVer,
  "versionName": "$currentAppVerStr.0",
  "releaseDate": "$todayDot",
  "maxKeptVersions": 3,
  "keptVersions": [$keptVersJsonArray],
  "files": [
    ${keptVers.joinToString(",\n    ") { "\"android/Kontejnery_Ostrava_Android_v$it.json\"" }}
  ]
}"""
    File(androidDir, "version.json").writeText(androidVersionJson, Charsets.UTF_8)

    // 3. IPHONE VERZE (iphone/)
    val iphoneDir = File(rootDir, "iphone")
    if (!iphoneDir.exists()) iphoneDir.mkdirs()
    File(iphoneDir, "iphone.html").writeText(iphoneHtmlText, Charsets.UTF_8)
    File(iphoneDir, "index.html").writeText(iphoneHtmlText, Charsets.UTF_8)
    File(iphoneDir, "Kontejnery_iPhone.mobileconfig").writeText(mobileConfigText, Charsets.UTF_8)
    File(iphoneDir, "Kontejnery_Preprava_iPhone.ipa").writeBytes(ipaBytes)
    for (v in keptVers) {
      val htmlF = File(iphoneDir, "Kontejnery_iPhone_v$v.html")
      if (v == currentAppVer || !htmlF.exists()) htmlF.writeText(iphoneHtmlText, Charsets.UTF_8)
      val mcF = File(iphoneDir, "Kontejnery_iPhone_v$v.mobileconfig")
      if (v == currentAppVer || !mcF.exists()) mcF.writeText(mobileConfigText, Charsets.UTF_8)
      val ipaF = File(iphoneDir, "Kontejnery_Preprava_iPhone_v$v.ipa")
      if (v == currentAppVer || !ipaF.exists()) ipaF.writeBytes(ipaBytes)
    }
    pruneLocalPlatformFiles(iphoneDir, Regex("""Kontejnery_iPhone_v(\d+)\.html"""), 3)
    pruneLocalPlatformFiles(iphoneDir, Regex("""Kontejnery_iPhone_v(\d+)\.mobileconfig"""), 3)
    pruneLocalPlatformFiles(iphoneDir, Regex("""Kontejnery_Preprava_iPhone_v(\d+)\.ipa"""), 3)
    val iphoneVersionJson = """{
  "platform": "IPHONE_VERZE",
  "folder": "iphone/",
  "currentVersion": $currentAppVer,
  "versionBadge": "v $currentAppVerStr",
  "releaseDate": "$todayDot",
  "maxKeptVersions": 3,
  "keptVersions": [$keptVersJsonArray],
  "files": [
    "iphone/iphone.html",
    ${keptVers.joinToString(",\n    ") { "\"iphone/Kontejnery_iPhone_v$it.html\"" }},
    ${keptVers.joinToString(",\n    ") { "\"iphone/Kontejnery_Preprava_iPhone_v$it.ipa\"" }},
    ${keptVers.joinToString(",\n    ") { "\"iphone/Kontejnery_iPhone_v$it.mobileconfig\"" }}
  ]
}"""
    File(iphoneDir, "version.json").writeText(iphoneVersionJson, Charsets.UTF_8)

    try {
      val htmlText = curVerText
      val ver = currentAppVer
      val verName = currentAppVerStr
      val nowMs = System.currentTimeMillis()
      val chunks = htmlText.chunked(100_000)
      fun jsonStr(s: String): String = buildString(s.length + 128) {
        append('"')
        for (ch in s) {
          when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append(String.format("\\u%04x", ch.code)) else append(ch)
          }
        }
        append('"')
      }
      val commitUrl = URI("https://firestore.googleapis.com/v1/projects/kontejnery-ostrava/databases/(default)/documents:commit?key=AIzaSyBkkArHtJZbNtqejCb8BbuVt4R5Dr66GKE").toURL()
      fun postCommit(payload: String) {
        val conn = (commitUrl.openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 25000
          readTimeout = 40000
          doOutput = true
          setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        println("Firebase Cloud app_updates sync v$verName ($ver) HTTP: $code")
        conn.disconnect()
      }
      for ((idx, chunk) in chunks.withIndex()) {
        val body = """{"writes":[{"update":{"name":"projects/kontejnery-ostrava/databases/(default)/documents/app_updates/chunk_$idx","fields":{"index":{"integerValue":"$idx"},"version":{"integerValue":"$ver"},"content":{"stringValue":${jsonStr(chunk)}},"updatedAt":{"integerValue":"$nowMs"}}}}]}"""
        postCommit(body)
      }
      val metaBody = """{"writes":[{"update":{"name":"projects/kontejnery-ostrava/databases/(default)/documents/app_updates/latest_meta","fields":{"version":{"integerValue":"$ver"},"versionName":{"stringValue":"$verName"},"badge":{"stringValue":"v $verName • $todayDot"},"fileName":{"stringValue":"S_POHODA_Kontejnery_v$verName.html"},"chunkCount":{"integerValue":"${chunks.size}"},"totalLength":{"integerValue":"${htmlText.length}"},"updatedAt":{"integerValue":"$nowMs"}}}}]}"""
      postCommit(metaBody)

      // Automatické odeslání do repozitáře Home_1 při přítomnosti platného systémového tokenu
      val ghRepo = "Kont-premysl-sliva/Home_1"
      val ghPublicRepo = "Kont-premysl-sliva/Home_1"
      val ghBranch = "main"
      val ghToken = System.getenv("GITHUB_TOKEN") ?: ""
      val htmlBase64 = Base64.getEncoder().encodeToString(htmlText.toByteArray(Charsets.UTF_8))
      val iphoneBase64 = Base64.getEncoder().encodeToString(iphoneHtmlText.toByteArray(Charsets.UTF_8))
      val mobileConfigBase64 = Base64.getEncoder().encodeToString(mobileConfigText.toByteArray(Charsets.UTF_8))
      val ipaBase64 = Base64.getEncoder().encodeToString(ipaBytes)
      val pcVersionBase64 = Base64.getEncoder().encodeToString(pcVersionJson.toByteArray(Charsets.UTF_8))
      val androidVersionBase64 = Base64.getEncoder().encodeToString(androidVersionJson.toByteArray(Charsets.UTF_8))
      val iphoneVersionBase64 = Base64.getEncoder().encodeToString(iphoneVersionJson.toByteArray(Charsets.UTF_8))
      val androidManifestCurrBase64 = Base64.getEncoder().encodeToString(File(androidDir, "Kontejnery_Ostrava_Android_v$verName.json").readBytes())

      fun getGhShaForRepo(targetRepo: String, filePath: String): String? {
        if (ghToken.isBlank()) return null
        return try {
          val u = URI("https://api.github.com/repos/$targetRepo/contents/$filePath?ref=$ghBranch").toURL()
          val c = (u.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "Kontejnery-Ostrava-Gradle-Sync")
            setRequestProperty("Authorization", "Bearer $ghToken")
          }
          if (c.responseCode == 200) {
            val resp = c.inputStream.bufferedReader().readText()
            Regex(""""sha"\s*:\s*"([^"]+)"""").find(resp)?.groupValues?.get(1)
          } else null
        } catch (_: Exception) { null }
      }

      fun putGhFileToRepo(targetRepo: String, filePath: String, contentBase64: String, commitMsg: String) {
        if (ghToken.isBlank()) return
        try {
          val sha = getGhShaForRepo(targetRepo, filePath)
          val u = URI("https://api.github.com/repos/$targetRepo/contents/$filePath").toURL()
          val c = (u.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "Kontejnery-Ostrava-Gradle-Sync")
            setRequestProperty("Authorization", "Bearer $ghToken")
          }
          val shaPart = if (!sha.isNullOrBlank()) ""","sha":"$sha"""" else ""
          val payload = """{"message":${jsonStr(commitMsg)},"content":"$contentBase64","branch":"$ghBranch"$shaPart}"""
          c.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
          val code = c.responseCode
          println("GitHub auto-sync [$targetRepo] $filePath (v$verName) HTTP: $code")
          c.disconnect()
        } catch (e: Exception) {
          println("GitHub auto-sync [$targetRepo] $filePath error: ${e.message}")
        }
      }

      for (targetRepo in listOf(ghRepo, ghPublicRepo)) {
        // Kořenové odkazy pro zpětnou kompatibilitu
        putGhFileToRepo(targetRepo, "index.html", htmlBase64, "release: automatická synchronizace v $verName - index.html")
        putGhFileToRepo(targetRepo, "iphone.html", iphoneBase64, "release: automatická synchronizace v $verName - iphone.html")
        putGhFileToRepo(targetRepo, "Kontejnery_iPhone.mobileconfig", mobileConfigBase64, "release: automatická synchronizace v $verName - Kontejnery_iPhone.mobileconfig")
        putGhFileToRepo(targetRepo, "Kontejnery_Preprava_iPhone.ipa", ipaBase64, "release: automatická synchronizace v $verName - Kontejnery_Preprava_iPhone.ipa")
        if (installHtmlText.isNotEmpty()) {
          val installBase64 = Base64.getEncoder().encodeToString(installHtmlText.toByteArray(Charsets.UTF_8))
          putGhFileToRepo(targetRepo, "install.html", installBase64, "release: automatická synchronizace v $verName - install.html")
          putGhFileToRepo(targetRepo, "install/index.html", installBase64, "release: automatická synchronizace v $verName - install/index.html")
        }

        // 1. WEB PC APLIKACE (pc/)
        putGhFileToRepo(targetRepo, "pc/index.html", htmlBase64, "release(pc): WEB PC Aplikace v$verName - pc/index.html")
        putGhFileToRepo(targetRepo, "pc/S_POHODA_Kontejnery_PC_v$verName.html", htmlBase64, "release(pc): WEB PC Aplikace v$verName")
        putGhFileToRepo(targetRepo, "pc/version.json", pcVersionBase64, "release(pc): metadata verze v$verName")

        // 2. ANDROID VERZE (android/)
        putGhFileToRepo(targetRepo, "android/Kontejnery_Ostrava_Android_v$verName.json", androidManifestCurrBase64, "release(android): Android verze v$verName")
        putGhFileToRepo(targetRepo, "android/version.json", androidVersionBase64, "release(android): metadata verze v$verName")

        // 3. IPHONE VERZE (iphone/)
        putGhFileToRepo(targetRepo, "iphone/iphone.html", iphoneBase64, "release(iphone): iPhone verze v$verName - iphone/iphone.html")
        putGhFileToRepo(targetRepo, "iphone/Kontejnery_iPhone_v$verName.html", iphoneBase64, "release(iphone): iPhone verze v$verName HTML")
        putGhFileToRepo(targetRepo, "iphone/Kontejnery_iPhone_v$verName.mobileconfig", mobileConfigBase64, "release(iphone): iPhone verze v$verName mobileconfig")
        putGhFileToRepo(targetRepo, "iphone/Kontejnery_Preprava_iPhone_v$verName.ipa", ipaBase64, "release(iphone): iPhone verze v$verName IPA")
        putGhFileToRepo(targetRepo, "iphone/version.json", iphoneVersionBase64, "release(iphone): metadata verze v$verName")
      }
      putGhFileToRepo(ghRepo, "S_POHODA_Kontejnery_v$verName.html", htmlBase64, "release: automatická synchronizace v $verName - S_POHODA_Kontejnery_v$verName.html")

      // Pomocná funkce pro udržování posledních 3 verzí v dané složce na GitHubu
      fun pruneGhFolder(targetRepo: String, folderPath: String, fileRegex: Regex, maxKeep: Int = 3) {
        try {
          val apiPath = if (folderPath.isBlank()) "contents?ref=$ghBranch" else "contents/$folderPath?ref=$ghBranch"
          val listUrl = URI("https://api.github.com/repos/$targetRepo/$apiPath").toURL()
          val listConn = (listUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "Kontejnery-Ostrava-Gradle-Sync")
            setRequestProperty("Authorization", "Bearer $ghToken")
          }
          if (listConn.responseCode == 200) {
            val listBody = listConn.inputStream.bufferedReader().readText()
            val entryRegex = Regex("""\{\s*"name"\s*:\s*"([^"]+)"[\s\S]*?"path"\s*:\s*"([^"]+)"[\s\S]*?"sha"\s*:\s*"([^"]+)"\s*,""")
            val matched = entryRegex.findAll(listBody).mapNotNull { m ->
              val name = m.groupValues[1]
              val path = m.groupValues[2]
              val sha = m.groupValues[3]
              val verMatch = fileRegex.find(name)
              if (verMatch != null) {
                val vNum = verMatch.groupValues[1].toIntOrNull() ?: 0
                Triple(path, vNum, sha)
              } else null
            }.sortedByDescending { it.second }.toList()
            for (old in matched.drop(maxKeep)) {
              try {
                val delUrl = URI("https://api.github.com/repos/$targetRepo/contents/${old.first}").toURL()
                val delConn = (delUrl.openConnection() as HttpURLConnection).apply {
                  requestMethod = "DELETE"
                  connectTimeout = 10000
                  readTimeout = 15000
                  doOutput = true
                  setRequestProperty("Accept", "application/vnd.github.v3+json")
                  setRequestProperty("Content-Type", "application/json; charset=utf-8")
                  setRequestProperty("User-Agent", "Kontejnery-Ostrava-Gradle-Sync")
                  setRequestProperty("Authorization", "Bearer $ghToken")
                }
                val delPayload = """{"message":"cleanup: delete old version ${old.first} (keep latest $maxKeep)","sha":"${old.third}","branch":"$ghBranch"}"""
                delConn.outputStream.use { it.write(delPayload.toByteArray(Charsets.UTF_8)) }
                println("GitHub cleanup [$targetRepo] ${old.first} HTTP: ${delConn.responseCode}")
                delConn.disconnect()
              } catch (_: Exception) {}
            }
          }
          listConn.disconnect()
        } catch (_: Exception) {}
      }

      // Promažeme starší verze (ponecháme vždy jen poslední 3 verze pro každou platformu!)
      for (targetRepo in listOf(ghRepo, ghPublicRepo)) {
        pruneGhFolder(targetRepo, "", Regex("""S_POHODA_Kontejnery_v(\d+)\.html"""), 3)
        pruneGhFolder(targetRepo, "pc", Regex("""S_POHODA_Kontejnery_PC_v(\d+)\.html"""), 3)
        pruneGhFolder(targetRepo, "android", Regex("""Kontejnery_Ostrava_Android_v(\d+)\.json"""), 3)
        pruneGhFolder(targetRepo, "android", Regex("""Kontejnery_Ostrava_Android_v(\d+)\.apk"""), 3)
        pruneGhFolder(targetRepo, "iphone", Regex("""Kontejnery_iPhone_v(\d+)\.html"""), 3)
        pruneGhFolder(targetRepo, "iphone", Regex("""Kontejnery_iPhone_v(\d+)\.mobileconfig"""), 3)
        pruneGhFolder(targetRepo, "iphone", Regex("""Kontejnery_Preprava_iPhone_v(\d+)\.ipa"""), 3)
      }
    } catch (_: Exception) {}
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation("androidx.fragment:fragment-ktx:1.8.5")
  implementation("androidx.biometric:biometric:1.2.0-alpha05")
  implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation("com.google.mlkit:text-recognition:16.0.1")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
