package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    try {
      val url = java.net.URI("https://firestore.googleapis.com/v1/projects/kontejnery-ostrava/databases/(default)/documents/containers?pageSize=100").toURL()
      val conn = url.openConnection() as java.net.HttpURLConnection
      conn.connectTimeout = 8000
      conn.readTimeout = 8000
      val body = conn.inputStream.bufferedReader().readText()
      val sb = StringBuilder()
      sb.appendLine("=== ALL CONTAINERS IMAGE AUDIT ===")
      val docBlocks = body.split("projects/kontejnery-ostrava/databases/(default)/documents/containers/").drop(1)
      for (blk in docBlocks) {
        val id = blk.substringBefore("\"")
        fun fieldStr(name: String): String {
          val r = Regex(""""$name":\s*\{\s*"stringValue":\s*"([^"]*)"""")
          return r.find(blk)?.groupValues?.get(1) ?: ""
        }
        val des = fieldStr("designation")
        val type = fieldStr("type")
        val desc = fieldStr("description")
        val img = fieldStr("imageUri")
        val extra = fieldStr("extraImages")
        fun summarizeUri(u: String): String = when {
          u.isEmpty() -> "EMPTY"
          u.startsWith("data:image/svg") -> "SVG_B64(len=${u.length})"
          u.startsWith("data:image/jpeg") -> "JPEG_B64(len=${u.length})"
          u.startsWith("data:image/png") -> "PNG_B64(len=${u.length})"
          u.startsWith("https://") -> "HTTPS($u)"
          else -> "LOCAL($u)"
        }
        val extraSummary = if (extra.isEmpty()) "EMPTY" else extra.split("|", ",").map { summarizeUri(it) }.joinToString(" ; ")
        println("CONT_ROW: ID=$id | des='$des' | type='$type' | img=${summarizeUri(img)} | extra=$extraSummary | desc='$desc'")
      }
      for (bucket in listOf("kontejnery-ostrava.firebasestorage.app", "kontejnery-ostrava.appspot.com")) {
        try {
          val stUrl = java.net.URI("https://firebasestorage.googleapis.com/v0/b/$bucket/o?maxResults=200").toURL()
          val stConn = stUrl.openConnection() as java.net.HttpURLConnection
          stConn.connectTimeout = 8000
          stConn.readTimeout = 8000
          val stBody = stConn.inputStream.bufferedReader().readText()
          val names = Regex(""""name":\s*"([^"]+)"""").findAll(stBody).map { it.groupValues[1] }.toList()
          println("STORAGE_BUCKET($bucket): count=${names.size} -> ${names.joinToString(", ")}")
        } catch (e: Exception) {
          println("STORAGE_BUCKET_ERR($bucket): ${e.message}")
        }
      }
    } catch (e: Exception) {
      println("=== FIRESTORE ERROR: ${e.message} ===")
    }
    assertEquals(4, 2 + 2)
  }

  @Test
  fun containerMethodology_isCorrect() {
    // 1. ISO 6346 format (single-line and multi-line assembly, hyphen and space unified to hyphen)
    assertEquals("CARU 002238-0", com.example.api.normalizeContainerDesignation("CARU 002238-0"))
    assertEquals("CARU 002266-7", com.example.api.normalizeContainerDesignation("CARU 002266 7"))
    assertEquals("CARU 002238-0", com.example.api.normalizeContainerDesignation("CARU\n002238-0"))
    assertEquals("CARU 002266-7", com.example.api.normalizeContainerDesignation("CARU\n002266 7"))
    assertEquals("DSFU 237856-7", com.example.api.normalizeContainerDesignation("DSFU 237856 7"))

    // 2. Partially covered prefix (NO reconstruction — only visible letters)
    assertEquals("U 251501-9", com.example.api.normalizeContainerDesignation("U 251501 9"))
    assertEquals("LU 251501-9", com.example.api.normalizeContainerDesignation("LU\n251501-9"))

    // 3. Highest priority internal stickers SK (3 to 8 digits) and OB
    assertEquals("SK 6240221", com.example.api.normalizeContainerDesignation("SK 6240221"))
    assertEquals("SK 0789", com.example.api.normalizeContainerDesignation("SK 0789"))
    assertEquals("OB 8967", com.example.api.normalizeContainerDesignation("OB 8967"))
    // SK/OB overrides ISO if both appear in raw text
    assertEquals("SK 6240221", com.example.api.normalizeContainerDesignation("CARU 002238-0 SK 6240221"))

    // 4. Unreadable fallback: strictly XXX <last 2 chars of photo>, never KONT-XX
    assertEquals("XXX 01", com.example.api.normalizeContainerDesignation("", "IMG_2026_01.jpg"))
    assertEquals("XXX 02", com.example.api.normalizeContainerDesignation("nečitelné", "foto_02"))
    assertEquals("XXX 05", com.example.api.normalizeContainerDesignation("KONT-05", "foto_05"))

    // 5. Filtering out technical weight/volume values on 2nd and lower rows (LBS, KGS, MAX. GROSS, TARE, 22G1)
    assertEquals(
      "MEDU 311432-9",
      com.example.api.normalizeContainerDesignation(
        "MEDU 311432 9\n22G1\nMAX. GROSS 30 480 KGS 67 200 LBS\nTARE 2 180 KGS 4 810 LBS\nNET 28 300 KGS 62 390 LBS"
      )
    )
    assertEquals(
      "MEDU 311432-9",
      com.example.api.normalizeContainerDesignation("LBS 311432-9 MEDU 311432 9")
    )
    // If AI mistakenly returned only "LBS311432-9" without visible owner code, LBS is stripped as a forbidden unit
    assertEquals(
      "XXX 05",
      com.example.api.normalizeContainerDesignation("LBS311432-9", "WhatsApp_Image_05.jpeg")
    )
  }
}
