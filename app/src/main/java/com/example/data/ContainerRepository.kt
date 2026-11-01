package com.example.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ContainerRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun encodeLocalFileToDataUriIfExists(rawPath: String): String? {
        return try {
            val cleanPath = rawPath.removePrefix("file://").trim()
            if (cleanPath.isEmpty() || !cleanPath.startsWith("/")) return null
            val file = java.io.File(cleanPath)
            if (!file.exists() || !file.isFile || file.length() <= 0L) return null
            val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            val maxRawDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            val sampleSize = when {
                maxRawDim > 2400 -> 4
                maxRawDim > 1200 -> 2
                else -> 1
            }
            val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            val maxDim = 800
            val ratio = minOf(
                maxDim.toFloat() / bmp.width.coerceAtLeast(1),
                maxDim.toFloat() / bmp.height.coerceAtLeast(1),
                1f
            )
            val scaled = if (ratio < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * ratio).toInt().coerceAtLeast(1),
                    (bmp.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else bmp
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
            val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }

    private fun isPortablePhotoUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val s = uri.trim()
        return s.startsWith("data:image/") || s.startsWith("http://") || s.startsWith("https://")
    }

    private fun sanitizeSingleImageUri(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        if (s.contains("base64,file://")) {
            s = s.replace(Regex("base64,(file://)+"), "base64,")
        }
        if (s.startsWith("file://data:image")) {
            s = s.removePrefix("file://")
        }
        if (s.startsWith("file:///9j/") || s.startsWith("/9j/")) {
            val cleanB64 = s.replaceFirst(Regex("^(file://)+"), "")
            return "data:image/jpeg;base64,$cleanB64"
        }
        if (s.startsWith("file://iVBOR") || s.startsWith("iVBOR")) {
            val cleanB64 = s.replaceFirst(Regex("^(file://)+"), "")
            return "data:image/png;base64,$cleanB64"
        }
        if (s == "data:image/jpeg;base64" || s == "data:image/png;base64" || s == "data:image/webp;base64") {
            return null
        }
        if (isLocalFilePath(s)) {
            val encoded = encodeLocalFileToDataUriIfExists(s)
            if (!encoded.isNullOrBlank()) return encoded
        }
        return s
    }

    private fun parseExtraImagesSafe(rawExtra: String?): List<String> {
        if (rawExtra.isNullOrBlank()) return emptyList()
        val repairedRaw = rawExtra.replace(Regex("base64,(file://)+"), "base64,")
        val pipeParts = repairedRaw.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        for (part in pipeParts) {
            val commaTokens = part.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            var i = 0
            while (i < commaTokens.size) {
                val tk = commaTokens[i]
                if (tk.startsWith("data:image") && !tk.contains(",") && i + 1 < commaTokens.size) {
                    val payload = commaTokens[i + 1].replaceFirst(Regex("^(file://)+"), "")
                    result.add("$tk,$payload")
                    i += 2
                } else {
                    val san = sanitizeSingleImageUri(tk)
                    if (!san.isNullOrBlank()) result.add(san)
                    i += 1
                }
            }
        }
        return result.distinct()
    }

    private fun generateHyundai8x3PhotoDataUri(designation: String, type: String): String {
        return try {
            val bmp = android.graphics.Bitmap.createBitmap(640, 400, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            // Sky & concrete yard background
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, 0f, 260f,
                android.graphics.Color.parseColor("#DBEAFE"),
                android.graphics.Color.parseColor("#EFF6FF"),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, 640f, 260f, paint)
            paint.shader = null
            paint.color = android.graphics.Color.parseColor("#CBD5E1")
            canvas.drawRect(0f, 260f, 640f, 400f, paint)
            // Shadow
            paint.color = android.graphics.Color.parseColor("#94A3B8")
            canvas.drawRoundRect(android.graphics.RectF(55f, 318f, 595f, 340f), 12f, 12f, paint)
            // Container body (Hyundai 8x3m Obytný / Skladový module)
            paint.color = android.graphics.Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(android.graphics.RectF(60f, 90f, 580f, 325f), 6f, 6f, paint)
            // Dark navy frame & roof trim (Kontejnery Ostrava / Hyundai style)
            paint.color = android.graphics.Color.parseColor("#0B1B3D")
            canvas.drawRect(60f, 90f, 580f, 118f, paint)
            canvas.drawRect(60f, 308f, 580f, 325f, paint)
            canvas.drawRect(60f, 90f, 76f, 325f, paint)
            canvas.drawRect(564f, 90f, 580f, 325f, paint)
            // Corrugation ribs
            paint.color = android.graphics.Color.parseColor("#E2E8F0")
            paint.strokeWidth = 3f
            for (rx in 95..550 step 22) {
                canvas.drawLine(rx.toFloat(), 120f, rx.toFloat(), 306f, paint)
            }
            // Double windows + entrance door for 8x3m Hyundai container
            paint.color = android.graphics.Color.parseColor("#1E3A8A")
            canvas.drawRoundRect(android.graphics.RectF(120f, 148f, 220f, 235f), 4f, 4f, paint)
            canvas.drawRoundRect(android.graphics.RectF(255f, 148f, 355f, 235f), 4f, 4f, paint)
            paint.color = android.graphics.Color.parseColor("#BAE6FD")
            canvas.drawRect(126f, 154f, 168f, 229f, paint)
            canvas.drawRect(172f, 154f, 214f, 229f, paint)
            canvas.drawRect(261f, 154f, 303f, 229f, paint)
            canvas.drawRect(307f, 154f, 349f, 229f, paint)
            // Entrance door
            paint.color = android.graphics.Color.parseColor("#334155")
            canvas.drawRoundRect(android.graphics.RectF(420f, 140f, 505f, 306f), 4f, 4f, paint)
            paint.color = android.graphics.Color.parseColor("#F1F5F9")
            canvas.drawRect(426f, 146f, 499f, 302f, paint)
            paint.color = android.graphics.Color.parseColor("#93C5FD")
            canvas.drawRect(442f, 162f, 483f, 215f, paint)
            // Branding & Hyundai 8x3m badge
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 16f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            canvas.drawText("KONTEJNERY OSTRAVA • HYUNDAI 8x3m", 84f, 109f, paint)
            paint.color = android.graphics.Color.parseColor("#0B1B3D")
            canvas.drawRoundRect(android.graphics.RectF(80f, 252f, 375f, 294f), 6f, 6f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 18f
            canvas.drawText(designation.ifBlank { "HYUNDAI 8x3m" }, 94f, 279f, paint)
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
            val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (_: Exception) {
            ""
        }
    }

    private fun isGeneratedSvgUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return true
        return uri.trim().startsWith("data:image/svg+xml")
    }

    private fun parseContainerDoc(doc: com.google.firebase.firestore.DocumentSnapshot): ContainerCard? {
        val rawCard = doc.toObject(ContainerCard::class.java)?.copy(id = doc.id) ?: return null
        val docImagesList = (doc.get("images") as? List<*>)
            ?.mapNotNull { sanitizeSingleImageUri(it?.toString()) }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val combinedExtra = if (docImagesList.isNotEmpty()) {
            val existingExtras = parseExtraImagesSafe(rawCard.extraImages)
            (existingExtras + docImagesList).distinct().joinToString("|")
        } else {
            rawCard.extraImages
        }
        return healContainerCard(rawCard.copy(extraImages = combinedExtra))
    }

    fun healContainerCard(card: ContainerCard): ContainerCard {
        val rawExtras = parseExtraImagesSafe(card.extraImages)
            .filter { isPortablePhotoUri(it) }
            .toMutableList()
        var mainImg = sanitizeSingleImageUri(card.imageUri)
        if (!isPortablePhotoUri(mainImg)) {
            mainImg = null
        }

        // If mainImg is a real photo, make sure it is also present in rawExtras
        if (!mainImg.isNullOrBlank() && !isGeneratedSvgUri(mainImg) && mainImg !in rawExtras) {
            rawExtras.add(0, mainImg)
        }

        // Prefer real non-SVG photos if any exist in extras
        val realExtras = rawExtras.filter { !isGeneratedSvgUri(it) }
        val extras = if (realExtras.isNotEmpty()) realExtras.toMutableList() else rawExtras

        if ((mainImg.isNullOrBlank() || isGeneratedSvgUri(mainImg)) && realExtras.isNotEmpty()) {
            mainImg = realExtras.first()
        } else if (mainImg.isNullOrBlank() && extras.isNotEmpty()) {
            mainImg = extras.first()
        }

        val combinedText = "${card.designation} ${card.type} ${card.description} ${card.poznamka}".lowercase()
        if ((mainImg.isNullOrBlank() || isGeneratedSvgUri(mainImg)) && (combinedText.contains("hyundai") || combinedText.contains("8x3") || combinedText.contains("8 x 3"))) {
            val generated = generateHyundai8x3PhotoDataUri(card.designation, card.type)
            if (generated.isNotBlank()) {
                mainImg = generated
                if (extras.none { !isGeneratedSvgUri(it) }) {
                    extras.clear()
                    extras.add(generated)
                }
            }
        }
        if (!mainImg.isNullOrBlank() && extras.isEmpty()) {
            extras.add(mainImg)
        }
        val normalizedDepo = when {
            card.depo.equals("Depo 2", ignoreCase = true) ||
                card.depo.equals("DEPO 02", ignoreCase = true) ||
                card.status.equals("DEPO 02", ignoreCase = true) -> "Depo 2"
            else -> "Depo 1"
        }
        val normalizedStatus = when {
            card.status.isBlank() || card.status.equals("SKLADEM", ignoreCase = true) ->
                if (normalizedDepo == "Depo 2") "DEPO 02" else "DEPO 01"
            else -> card.status
        }
        val normalizedLastMod = if (card.lastModified > 0L) card.lastModified else card.timestamp
        return card.copy(
            depo = normalizedDepo,
            status = normalizedStatus,
            lastModified = normalizedLastMod,
            imageUri = mainImg,
            extraImages = extras.distinct().joinToString("|")
        )
    }

    private fun ensureSystemNumbersAndSort(rawList: List<ContainerCard>): List<ContainerCard> {
        val byDepo = rawList.groupBy { if (it.depo == "Depo 2" || it.status == "DEPO 02") "Depo 2" else "Depo 1" }
        val result = mutableListOf<ContainerCard>()
        for ((_, group) in byDepo) {
            var currentMax = group.maxOfOrNull { it.effectiveSystemNumber() } ?: 0
            val withSys = group.sortedWith(compareBy<ContainerCard> { it.timestamp }.thenBy { it.id }).map { card ->
                val eff = card.effectiveSystemNumber()
                if (eff > 0) {
                    if (card.systemNumber != eff) card.copy(systemNumber = eff) else card
                } else {
                    currentMax += 1
                    card.copy(systemNumber = currentMax)
                }
            }
            result.addAll(withSys)
        }
        return result.sortedWith(
            compareByDescending<ContainerCard> { it.effectiveSystemNumber() }
                .thenByDescending { it.timestamp }
        )
    }

    fun getSeznam(query: String): Flow<List<ContainerCard>> = callbackFlow {
        val listener = db.collection("containers")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = ensureSystemNumbersAndSort(
                        snapshot.documents.mapNotNull { doc ->
                            parseContainerDoc(doc)
                        }
                    )
                    val filtered = if (query.isBlank()) list else list.filter { 
                        it.designation.contains(query, ignoreCase = true) || 
                        it.customerCompany.contains(query, ignoreCase = true) ||
                        it.type.contains(query, ignoreCase = true) ||
                        it.systemNumber.toString() == query.trim().removePrefix("#")
                    }
                    trySend(filtered)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getArchiv(): Flow<List<ContainerCard>> = callbackFlow {
        val listener = db.collection("containers")
            .whereIn("status", listOf("ARCHIV", "PRODEJ", "PRODÁNO"))
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = ensureSystemNumbersAndSort(
                        snapshot.documents.mapNotNull { doc ->
                            parseContainerDoc(doc)
                        }
                    )
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    fun getPronajem(): Flow<List<ContainerCard>> = callbackFlow {
        val listener = db.collection("containers")
            .whereIn("status", listOf("REZERV.", "PRONÁJEM", "PRONAJATO", "PRONAJ.", "PRONÁJ"))
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = ensureSystemNumbersAndSort(
                        snapshot.documents.mapNotNull { doc ->
                            parseContainerDoc(doc)
                        }
                    )
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getContainerById(id: String): ContainerCard? {
        val doc = db.collection("containers").document(id).get().await()
        return parseContainerDoc(doc)
    }

    suspend fun findByDesignation(designation: String): ContainerCard? {
        val snapshot = db.collection("containers").whereEqualTo("designation", designation).limit(1).get().await()
        return snapshot.documents.firstOrNull()?.let { parseContainerDoc(it) }
    }

    suspend fun getActiveContainersSnapshot(): List<ContainerCard> {
        val snapshot = db.collection("containers").get().await()
        return ensureSystemNumbersAndSort(
            snapshot.documents.mapNotNull { doc ->
                parseContainerDoc(doc)
            }
        )
    }

    suspend fun getAllContainersDirect(): List<ContainerCard> {
        val snapshot = db.collection("containers").get().await()
        return ensureSystemNumbersAndSort(
            snapshot.documents.mapNotNull { doc ->
                parseContainerDoc(doc)
            }
        )
    }

    suspend fun insert(container: ContainerCard) {
        val now = System.currentTimeMillis()
        val assignedSysNum = if (container.systemNumber > 0) {
            container.systemNumber
        } else {
            try {
                val targetDepo = if (container.depo == "Depo 2" || container.status == "DEPO 02") "Depo 2" else "Depo 1"
                val allInDepo = getAllContainersDirect().filter {
                    (if (it.depo == "Depo 2" || it.status == "DEPO 02") "Depo 2" else "Depo 1") == targetDepo
                }
                (allInDepo.maxOfOrNull { it.systemNumber } ?: allInDepo.size) + 1
            } catch (_: Exception) {
                1
            }
        }
        val finalContainer = processImages(container).copy(
            timestamp = if (container.timestamp > 0L) container.timestamp else now,
            lastModified = now,
            systemNumber = assignedSysNum
        )
        val docRef = if (finalContainer.id.isEmpty()) db.collection("containers").document() else db.collection("containers").document(finalContainer.id)
        docRef.set(finalContainer.copy(id = docRef.id)).await()
    }

    suspend fun update(container: ContainerCard) {
        if (container.id.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val finalContainer = processImages(container).copy(
                timestamp = if (container.timestamp > 0L) container.timestamp else now,
                lastModified = now,
                systemNumber = container.systemNumber
            )
            db.collection("containers").document(finalContainer.id).set(finalContainer).await()
        }
    }

    private fun isLocalFilePath(uri: String): Boolean {
        if (uri.startsWith("data:image")) return false
        if (uri.startsWith("/9j/") || uri.startsWith("file:///9j/") || uri.startsWith("iVBOR")) return false
        return uri.startsWith("content://") || uri.startsWith("file://") ||
            uri.startsWith("/data/") || uri.startsWith("/storage/") || uri.startsWith("/sdcard/") || uri.startsWith("/mnt/")
    }

    private suspend fun processImages(container: ContainerCard): ContainerCard {
        val healed = healContainerCard(container)
        val uploadedCache = mutableMapOf<String, String>()
        
        val depoCode = when {
            healed.status.contains("01") -> "01"
            healed.status.contains("02") -> "02"
            else -> "00"
        }
        val customBaseName = "depo${depoCode}_${healed.designation}_${healed.type}"

        var newMainImage = sanitizeSingleImageUri(healed.imageUri)
        if (newMainImage != null && isLocalFilePath(newMainImage)) {
            if (newMainImage.startsWith("/")) {
                newMainImage = "file://$newMainImage"
            }
            val remoteUrl = uploadImage(newMainImage, customBaseName)
            uploadedCache[newMainImage] = remoteUrl
            newMainImage = remoteUrl
        }
        
        val extraUris = parseExtraImagesSafe(healed.extraImages)
        val newExtras = mutableListOf<String>()
        for (uri in extraUris) {
            var localUri = sanitizeSingleImageUri(uri) ?: continue
            if (isLocalFilePath(localUri)) {
                if (localUri.startsWith("/")) {
                    localUri = "file://$localUri"
                }
                if (uploadedCache.containsKey(localUri)) {
                    newExtras.add(uploadedCache[localUri]!!)
                } else {
                    val remoteUrl = uploadImage(localUri, customBaseName)
                    uploadedCache[localUri] = remoteUrl
                    newExtras.add(remoteUrl)
                }
            } else {
                newExtras.add(localUri)
            }
        }

        if (newMainImage.isNullOrBlank() && newExtras.isNotEmpty()) {
            newMainImage = newExtras.first()
        }
        if (!newMainImage.isNullOrBlank() && newExtras.isEmpty()) {
            newExtras.add(newMainImage)
        }
        
        return healed.copy(
            imageUri = newMainImage,
            extraImages = newExtras.distinct().joinToString("|")
        )
    }

    private suspend fun uploadImage(localUriStr: String, baseName: String): String {
        return try {
            val encoded = encodeLocalFileToDataUriIfExists(localUriStr)
            if (encoded != null) return encoded
            val cleanPath = localUriStr.removePrefix("file://")
            val file = java.io.File(cleanPath)
            if (file.exists() && file.length() > 0) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bmp != null) {
                    val maxDim = 800
                    val ratio = minOf(maxDim.toFloat() / bmp.width.coerceAtLeast(1), maxDim.toFloat() / bmp.height.coerceAtLeast(1), 1f)
                    val scaled = if (ratio < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * ratio).toInt().coerceAtLeast(1),
                            (bmp.height * ratio).toInt().coerceAtLeast(1),
                            true
                        )
                    } else bmp
                    val out = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                    val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    return "data:image/jpeg;base64,$b64"
                }
            }
            localUriStr
        } catch (e: Exception) {
            localUriStr
        }
    }

    suspend fun delete(container: ContainerCard) {
        if (container.id.isNotEmpty()) {
            markIdDeletedRemote(container.id, "containers")
            db.collection("containers").document(container.id).delete().await()
        }
    }

    suspend fun deleteAllContainersInDepo(depo: String) {
        val snapshot = db.collection("containers").whereEqualTo("depo", depo).get().await()
        for (doc in snapshot.documents) {
            markIdDeletedRemote(doc.id, "containers")
            doc.reference.delete().await()
        }
    }

    suspend fun wipeAllTestDataFromCloud() {
        val collections = listOf(
            "containers",
            "transports",
            "customers",
            "zajemci",
            "objednavky",
            "adresar",
            "nabidky",
            "zakazky",
            "faktury",
            "zalohove_faktury",
            "inventories",
            "events",
            "deleted_sync_ids"
        )
        for (col in collections) {
            try {
                val snapshot = db.collection(col).get().await()
                for (doc in snapshot.documents) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun getInventoriesByDepo(depo: String): Flow<List<InventoryRecord>> = callbackFlow {
        val listener = db.collection("inventories")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(InventoryRecord::class.java)?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    val filtered = list.filter { inv ->
                        depo.isBlank() || depo == "VŠE" || inv.depo.isBlank() || inv.depo.equals(depo, ignoreCase = true)
                    }
                    trySend(filtered.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    private suspend fun processInventoryImages(inventory: InventoryRecord): InventoryRecord {
        val raw = inventory.sessionDataJson.trim()
        if (raw.isBlank()) return inventory
        return try {
            val uploadedCache = mutableMapOf<String, String>()
            suspend fun resolveInventoryPhotoUri(uriStr: String, designation: String): String {
                val trimmed = uriStr.trim()
                if (trimmed.isBlank()) return ""
                if (trimmed.startsWith("data:image")) return trimmed
                var localUri = sanitizeSingleImageUri(trimmed) ?: return trimmed
                if (isLocalFilePath(localUri)) {
                    if (localUri.startsWith("/")) {
                        localUri = "file://$localUri"
                    }
                    return uploadedCache.getOrPut(localUri) {
                        uploadImage(localUri, "Inventura_${designation.ifBlank { "Kontejner" }}")
                    }
                }
                return localUri
            }

            if (raw.startsWith("{")) {
                val obj = org.json.JSONObject(raw)
                val arr = obj.optJSONArray("items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val itemObj = arr.optJSONObject(i) ?: continue
                        val img = itemObj.optString("imageUri", "")
                        val des = itemObj.optString("designation", "")
                        if (img.isNotBlank()) {
                            itemObj.put("imageUri", resolveInventoryPhotoUri(img, des))
                        }
                    }
                }
                inventory.copy(sessionDataJson = obj.toString())
            } else if (raw.startsWith("[")) {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val itemObj = arr.optJSONObject(i) ?: continue
                    val img = itemObj.optString("imageUri", "")
                    val des = itemObj.optString("designation", "")
                    if (img.isNotBlank()) {
                        itemObj.put("imageUri", resolveInventoryPhotoUri(img, des))
                    }
                }
                inventory.copy(sessionDataJson = arr.toString())
            } else {
                inventory
            }
        } catch (_: Exception) {
            inventory
        }
    }

    suspend fun insertInventory(inventory: InventoryRecord) {
        val finalInventory = processInventoryImages(inventory)
        val docRef = if (finalInventory.id.isEmpty()) db.collection("inventories").document() else db.collection("inventories").document(finalInventory.id)
        docRef.set(finalInventory.copy(id = docRef.id)).await()
    }

    suspend fun updateInventory(inventory: InventoryRecord) {
        if (inventory.id.isNotEmpty()) {
            val finalInventory = processInventoryImages(inventory)
            db.collection("inventories").document(finalInventory.id).set(finalInventory).await()
        }
    }

    suspend fun deleteInventory(inventory: InventoryRecord) {
        if (inventory.id.isNotEmpty()) {
            markIdDeletedRemote(inventory.id, "inventories")
            db.collection("inventories").document(inventory.id).delete().await()
        }
    }

    fun getEventsByDepo(depo: String): Flow<List<EventRecord>> = callbackFlow {
        val listener = db.collection("events")
            .whereEqualTo("depo", depo)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(EventRecord::class.java)?.copy(id = it.id) }
                    trySend(list.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun insertEvent(event: EventRecord) {
        val docRef = if (event.id.isEmpty()) db.collection("events").document() else db.collection("events").document(event.id)
        docRef.set(event).await()
    }

    private fun healTransportRecord(transport: TransportRecord): TransportRecord {
        val healedMain = sanitizeSingleImageUri(transport.imageUri)
        val healedContainer = sanitizeSingleImageUri(transport.containerImageUri)
        val parsedExtras = parseExtraImagesSafe(transport.extraImages).toMutableList()
        if (!healedContainer.isNullOrBlank() && healedContainer !in parsedExtras) {
            parsedExtras.add(0, healedContainer)
        }
        val finalContainerMain = healedContainer ?: parsedExtras.firstOrNull()
        return transport.copy(
            imageUri = healedMain,
            containerImageUri = finalContainerMain,
            extraImages = parsedExtras.distinct().joinToString("|")
        )
    }

    private fun parseTransportDoc(doc: com.google.firebase.firestore.DocumentSnapshot): TransportRecord? {
        return try {
            val rawDateText = doc.getString("dateText")?.ifBlank { null }
                ?: doc.getString("date")?.ifBlank { null }
                ?: doc.getString("transportDate")?.ifBlank { null }
                ?: ""
            val rawInternalNote = doc.getString("internalNote")?.ifBlank { null }
                ?: doc.getString("interniPoznamka")?.ifBlank { null }
                ?: ""
            val rawIsVraceni = doc.getBoolean("isVraceni")
                ?: doc.getBoolean("vraceni")
                ?: (doc.getString("customerSource")?.equals("VRÁCENÍ", ignoreCase = true) == true)
            val c1 = doc.getString("containerImageUri")
            val c2 = doc.getString("containerImageUri2")?.takeIf { it.isNotBlank() }
            val c3 = doc.getString("containerImageUri3")?.takeIf { it.isNotBlank() }
            val rawExtra = doc.getString("extraImages") ?: ""
            val combinedExtra = listOfNotNull(
                rawExtra.takeIf { it.isNotBlank() },
                c2,
                c3
            ).joinToString("|")
            val ts = (doc.get("timestamp") as? Number)?.toLong()
                ?: doc.getString("timestamp")?.toLongOrNull()
                ?: System.currentTimeMillis()
            val comp = doc.getString("company") ?: ""
            val custComp = doc.getString("customerCompany")?.ifBlank { comp } ?: comp
            val addr = doc.getString("customerAddress")?.ifBlank { null }
                ?: doc.getString("destinationLocation")
                ?: ""
            val dest = doc.getString("destinationLocation")?.ifBlank { addr } ?: addr
            val custName = doc.getString("customerName")?.ifBlank { null }
                ?: doc.getString("prevzalaOsoba")
                ?: ""
            val rec = TransportRecord(
                id = doc.id,
                timestamp = ts,
                status = doc.getString("status")?.ifBlank { "NOVÁ" } ?: "NOVÁ",
                designation = doc.getString("designation") ?: "",
                dateText = rawDateText,
                company = comp.ifBlank { custComp },
                spz = doc.getString("spz") ?: "",
                driver = doc.getString("driver") ?: "",
                containerType = doc.getString("containerType") ?: "",
                note = doc.getString("note") ?: "",
                imageUri = doc.getString("imageUri"),
                containerImageUri = c1,
                extraImages = combinedExtra,
                rawExtractedText = doc.getString("rawExtractedText") ?: "",
                depo = doc.getString("depo")?.ifBlank { "Depo 1" } ?: "Depo 1",
                customerName = custName,
                customerCompany = custComp,
                customerIc = doc.getString("customerIc") ?: "",
                customerDic = doc.getString("customerDic") ?: "",
                customerAddress = addr,
                customerPhone = doc.getString("customerPhone") ?: "",
                customerEmail = doc.getString("customerEmail") ?: "",
                customerInitials = doc.getString("customerInitials") ?: "",
                customerSource = doc.getString("customerSource") ?: "",
                sourceOrderId = doc.getString("sourceOrderId") ?: "",
                sourceZakazkaId = doc.getString("sourceZakazkaId") ?: "",
                cisloDokladuZdroj = doc.getString("cisloDokladuZdroj") ?: "",
                destinationLocation = dest,
                containersJson = doc.getString("containersJson") ?: "",
                deliveryPhotos = doc.getString("deliveryPhotos") ?: "",
                isVraceni = rawIsVraceni,
                prevzalaOsoba = doc.getString("prevzalaOsoba")?.ifBlank { custName } ?: custName,
                prevzalaMobil = doc.getString("prevzalaMobil") ?: "",
                internalNote = rawInternalNote,
                transportDate = doc.getString("transportDate")?.ifBlank { rawDateText } ?: rawDateText
            )
            healTransportRecord(rec)
        } catch (_: Exception) {
            try {
                doc.toObject(TransportRecord::class.java)?.copy(id = doc.id)?.let { healTransportRecord(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun transportToFirestoreMap(t: TransportRecord): Map<String, Any?> {
        val dt = t.dateText.ifBlank { t.transportDate }
        return mapOf(
            "id" to t.id,
            "timestamp" to t.timestamp,
            "status" to t.status,
            "designation" to t.designation,
            "dateText" to dt,
            "date" to dt,
            "transportDate" to t.transportDate.ifBlank { dt },
            "company" to t.company,
            "spz" to t.spz,
            "driver" to t.driver,
            "containerType" to t.containerType,
            "note" to t.note,
            "imageUri" to t.imageUri,
            "containerImageUri" to t.containerImageUri,
            "extraImages" to t.extraImages,
            "rawExtractedText" to t.rawExtractedText,
            "depo" to t.depo.ifBlank { "Depo 1" },
            "customerName" to t.customerName,
            "customerCompany" to t.customerCompany.ifBlank { t.company },
            "customerIc" to t.customerIc,
            "customerDic" to t.customerDic,
            "customerAddress" to t.customerAddress,
            "customerPhone" to t.customerPhone,
            "customerEmail" to t.customerEmail,
            "customerInitials" to t.customerInitials,
            "customerSource" to t.customerSource,
            "sourceOrderId" to t.sourceOrderId,
            "sourceZakazkaId" to t.sourceZakazkaId,
            "cisloDokladuZdroj" to t.cisloDokladuZdroj,
            "destinationLocation" to t.destinationLocation.ifBlank { t.customerAddress },
            "containersJson" to t.containersJson,
            "deliveryPhotos" to t.deliveryPhotos,
            "isVraceni" to t.isVraceni,
            "vraceni" to t.isVraceni,
            "prevzalaOsoba" to t.prevzalaOsoba,
            "prevzalaMobil" to t.prevzalaMobil,
            "internalNote" to t.internalNote,
            "interniPoznamka" to t.internalNote
        )
    }

    fun getTransportsByDepo(depo: String): Flow<List<TransportRecord>> = callbackFlow {
        val listener = db.collection("transports")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val isDepo2 = depo.equals("Depo 2", ignoreCase = true) || depo.equals("DEPO 02", ignoreCase = true)
                    val list = snapshot.documents.mapNotNull { doc ->
                        parseTransportDoc(doc)
                    }.filter { tr ->
                        val trIsDepo2 = tr.depo.equals("Depo 2", ignoreCase = true) || tr.depo.equals("DEPO 02", ignoreCase = true)
                        trIsDepo2 == isDepo2
                    }
                    trySend(
                        list.sortedWith(
                            compareByDescending<TransportRecord> { parseTransportDateToMillis(it.dateText, it.timestamp) }
                                .thenByDescending { it.timestamp }
                        )
                    )
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getAllTransportsDirect(): List<TransportRecord> {
        return try {
            val snap = db.collection("transports").get().await()
            snap.documents.mapNotNull { doc ->
                parseTransportDoc(doc)
            }.sortedWith(
                compareByDescending<TransportRecord> { parseTransportDateToMillis(it.dateText, it.timestamp) }
                    .thenByDescending { it.timestamp }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun insertTransport(transport: TransportRecord) {
        val finalTransport = processTransportImages(transport)
        val docRef = if (finalTransport.id.isEmpty()) db.collection("transports").document() else db.collection("transports").document(finalTransport.id)
        val withId = finalTransport.copy(id = docRef.id)
        docRef.set(transportToFirestoreMap(withId), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun updateTransport(transport: TransportRecord) {
        if (transport.id.isNotEmpty()) {
            val finalTransport = processTransportImages(transport)
            db.collection("transports").document(finalTransport.id).set(transportToFirestoreMap(finalTransport), com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    private suspend fun processTransportImages(transport: TransportRecord): TransportRecord {
        val healed = healTransportRecord(transport)
        val uploadedCache = mutableMapOf<String, String>()
        var newMainImage = healed.imageUri
        val baseName = if (healed.spz.isNotBlank()) healed.spz else if (healed.designation.isNotBlank()) healed.designation else "Transport"
        if (newMainImage != null && isLocalFilePath(newMainImage)) {
            if (newMainImage.startsWith("/")) {
                newMainImage = "file://$newMainImage"
            }
            val remoteUrl = uploadImage(newMainImage, baseName)
            uploadedCache[newMainImage] = remoteUrl
            newMainImage = remoteUrl
        }
        val extraUris = parseExtraImagesSafe(healed.extraImages)
        val newExtras = mutableListOf<String>()
        for (uri in extraUris) {
            var localUri = sanitizeSingleImageUri(uri) ?: continue
            if (isLocalFilePath(localUri)) {
                if (localUri.startsWith("/")) {
                    localUri = "file://$localUri"
                }
                if (uploadedCache.containsKey(localUri)) {
                    newExtras.add(uploadedCache[localUri]!!)
                } else {
                    val remoteUrl = uploadImage(localUri, baseName)
                    uploadedCache[localUri] = remoteUrl
                    newExtras.add(remoteUrl)
                }
            } else {
                newExtras.add(localUri)
            }
        }
        var newContainerImage = healed.containerImageUri
        if (newContainerImage != null && isLocalFilePath(newContainerImage)) {
            if (newContainerImage.startsWith("/")) {
                newContainerImage = "file://$newContainerImage"
            }
            if (uploadedCache.containsKey(newContainerImage)) {
                newContainerImage = uploadedCache[newContainerImage]!!
            } else {
                val remoteUrl = uploadImage(newContainerImage, baseName)
                uploadedCache[newContainerImage] = remoteUrl
                newContainerImage = remoteUrl
            }
        }
        if (!newContainerImage.isNullOrBlank() && newContainerImage !in newExtras) {
            newExtras.add(0, newContainerImage)
        }
        if (newContainerImage.isNullOrBlank() && newExtras.isNotEmpty()) {
            newContainerImage = newExtras.first()
        }
        return healed.copy(
            imageUri = newMainImage,
            containerImageUri = newContainerImage,
            extraImages = newExtras.distinct().joinToString("|")
        )
    }

    private fun parseCustomerDoc(doc: com.google.firebase.firestore.DocumentSnapshot, sourceCol: String): CustomerRecord? {
        return try {
            val name = doc.getString("name") ?: doc.getString("jmeno") ?: ""
            val company = doc.getString("company") ?: doc.getString("firma") ?: ""
            val phone = doc.getString("phone") ?: doc.getString("telefon") ?: ""
            val email = doc.getString("email") ?: ""
            val ic = doc.getString("ic") ?: doc.getString("ico") ?: ""
            val dic = doc.getString("dic") ?: ""
            val ulice = doc.getString("ulice") ?: doc.getString("adresa") ?: ""
            val inquiryType = doc.getString("inquiryType") ?: doc.getString("zamer") ?: ""
            val message = doc.getString("message") ?: doc.getString("zprava") ?: ""
            val status = doc.getString("status") ?: "NOVÁ"
            val depo = doc.getString("depo") ?: ""
            val cisloZakaznika = doc.getString("cisloZakaznika") ?: ""
            val bonita = doc.getString("bonita")?.ifBlank { "AAA" } ?: "AAA"
            val bonitaDetail = doc.getString("bonitaDetail")?.ifBlank { "Platební morálka výborná • Bez exekucí • Bez dluhů u FÚ" }
                ?: "Platební morálka výborná • Bez exekucí • Bez dluhů u FÚ"
            val rawUdalosti = (doc.get("udalosti") as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                CustomerEventRecord(
                    id = (map["id"] as? String) ?: "ev_${System.currentTimeMillis()}",
                    datum = (map["datum"] as? String) ?: "",
                    typ = (map["typ"] as? String) ?: "ZAKÁZKA",
                    cisloDokladu = (map["cisloDokladu"] as? String) ?: "",
                    popis = (map["popis"] as? String) ?: "",
                    castka = (map["castka"] as? Number)?.toDouble() ?: 0.0,
                    timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
            } ?: emptyList()
            val rawIsOrder = doc.getBoolean("isOrder")
            val rawIsAdresar = doc.getBoolean("isAdresar")
            val isOrder = when {
                sourceCol == "objednavky" -> true
                rawIsOrder != null -> rawIsOrder
                inquiryType.isNotBlank() || message.isNotBlank() -> true
                rawIsAdresar == true || sourceCol == "adresar" || cisloZakaznika.isNotBlank() -> false
                else -> true
            }
            val isAdresar = when {
                sourceCol == "adresar" -> true
                rawIsAdresar != null -> rawIsAdresar
                cisloZakaznika.isNotBlank() || ic.isNotBlank() || ulice.isNotBlank() -> true
                !isOrder -> true
                else -> false
            }
            val rawPhotos = (doc.get("photos") as? List<*>)?.mapNotNull { it as? String }
                ?: (doc.getString("extraImages")?.let { com.example.ui.parseExtraImageUris(it) })
                ?: listOfNotNull(
                    doc.getString("photo1"),
                    doc.getString("photo2"),
                    doc.getString("photo3")
                ).filter { it.isNotBlank() }
            val ts = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis()
            if (name.isBlank() && company.isBlank() && phone.isBlank() && email.isBlank() && message.isBlank()) {
                null
            } else {
                CustomerRecord(
                    id = doc.id,
                    name = name,
                    company = company,
                    phone = phone,
                    email = email,
                    ic = ic,
                    dic = dic,
                    ulice = ulice,
                    inquiryType = inquiryType,
                    message = message,
                    timestamp = ts,
                    status = status,
                    depo = depo,
                    cisloZakaznika = cisloZakaznika,
                    isOrder = isOrder,
                    isAdresar = isAdresar,
                    bonita = bonita,
                    bonitaDetail = bonitaDetail,
                    udalosti = rawUdalosti,
                    photos = rawPhotos.take(3)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getCustomers(): Flow<List<CustomerRecord>> = callbackFlow {
        var customersDocs: List<CustomerRecord> = emptyList()
        var objednavkyDocs: List<CustomerRecord> = emptyList()
        var adresarDocs: List<CustomerRecord> = emptyList()

        fun emitMerged() {
            val map = LinkedHashMap<String, CustomerRecord>()
            (adresarDocs + objednavkyDocs + customersDocs).forEach { rec ->
                val existing = map[rec.id]
                if (existing == null) {
                    map[rec.id] = rec
                } else {
                    val newer = if (rec.timestamp >= existing.timestamp) rec else existing
                    val older = if (rec.timestamp >= existing.timestamp) existing else rec
                    map[rec.id] = newer.copy(
                        name = newer.name.ifBlank { older.name },
                        company = newer.company.ifBlank { older.company },
                        phone = newer.phone.ifBlank { older.phone },
                        email = newer.email.ifBlank { older.email },
                        ic = newer.ic.ifBlank { older.ic },
                        dic = newer.dic.ifBlank { older.dic },
                        ulice = newer.ulice.ifBlank { older.ulice },
                        inquiryType = newer.inquiryType.ifBlank { older.inquiryType },
                        message = newer.message.ifBlank { older.message },
                        depo = newer.depo.ifBlank { older.depo },
                        cisloZakaznika = newer.cisloZakaznika.ifBlank { older.cisloZakaznika },
                        isOrder = newer.isOrder || older.isOrder,
                        isAdresar = newer.isAdresar || older.isAdresar,
                        bonita = newer.bonita.ifBlank { older.bonita }.ifBlank { "AAA" },
                        bonitaDetail = newer.bonitaDetail.ifBlank { older.bonitaDetail },
                        udalosti = if (newer.udalosti.isNotEmpty()) newer.udalosti else older.udalosti
                    )
                }
            }
            trySend(map.values.sortedByDescending { it.timestamp })
        }

        val l1 = db.collection("customers").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                customersDocs = snapshot.documents.mapNotNull { parseCustomerDoc(it, "customers") }
                emitMerged()
            }
        }
        val l2 = db.collection("objednavky").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                objednavkyDocs = snapshot.documents.mapNotNull { parseCustomerDoc(it, "objednavky") }
                emitMerged()
            }
        }
        val l3 = db.collection("adresar").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                adresarDocs = snapshot.documents.mapNotNull { parseCustomerDoc(it, "adresar") }
                emitMerged()
            }
        }
        awaitClose {
            l1.remove()
            l2.remove()
            l3.remove()
        }
    }

    suspend fun getAllCustomersDirect(): List<CustomerRecord> {
        val map = LinkedHashMap<String, CustomerRecord>()
        listOf("adresar", "objednavky", "customers").forEach { col ->
            try {
                val snap = db.collection(col).get().await()
                snap.documents.mapNotNull { parseCustomerDoc(it, col) }.forEach { rec ->
                    val existing = map[rec.id]
                    if (existing == null || rec.timestamp >= existing.timestamp) {
                        map[rec.id] = if (existing != null) {
                            rec.copy(
                                isOrder = rec.isOrder || existing.isOrder,
                                isAdresar = rec.isAdresar || existing.isAdresar
                            )
                        } else rec
                    }
                }
            } catch (_: Exception) {}
        }
        return map.values.sortedByDescending { it.timestamp }
    }

    suspend fun updateCustomerStatus(id: String, status: String) {
        if (id.isNotEmpty()) {
            val updateMap = mapOf("status" to status, "timestamp" to System.currentTimeMillis())
            try { db.collection("customers").document(id).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
            try { db.collection("objednavky").document(id).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
        }
    }

    suspend fun updateCustomerDepo(id: String, depo: String) {
        if (id.isNotEmpty()) {
            val updateMap = mapOf("depo" to depo, "timestamp" to System.currentTimeMillis())
            try { db.collection("customers").document(id).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
            try { db.collection("objednavky").document(id).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
            try { db.collection("adresar").document(id).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await() } catch (_: Exception) {}
        }
    }

    suspend fun deleteCustomer(id: String) {
        if (id.isNotEmpty()) {
            try { db.collection("customers").document(id).delete().await() } catch (_: Exception) {}
            try { db.collection("objednavky").document(id).delete().await() } catch (_: Exception) {}
            try { db.collection("adresar").document(id).delete().await() } catch (_: Exception) {}
            markIdDeletedRemote(id, "customers")
        }
    }

    fun getDeletedIds(): Flow<List<String>> = callbackFlow {
        val listener = db.collection("deleted_sync_ids").addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val ids = snapshot?.documents?.mapNotNull { it.id } ?: emptyList()
            trySend(ids)
        }
        awaitClose { listener.remove() }
    }

    suspend fun markIdDeletedRemote(id: String, collection: String = "general") {
        if (id.isBlank()) return
        try {
            val map = mapOf(
                "id" to id,
                "collection" to collection,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("deleted_sync_ids").document(id).set(map).await()
        } catch (_: Exception) {}
    }

    private fun parseNabidkaDoc(doc: com.google.firebase.firestore.DocumentSnapshot): NabidkaRecord? {
        return try {
            val rawItems = (doc.get("items") as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                NabidkaItemRecord(
                    id = (map["id"] as? String) ?: "",
                    kod = (map["kod"] as? String) ?: "KONT",
                    text = (map["text"] as? String) ?: "",
                    mnozstvi = (map["mnozstvi"] as? Number)?.toDouble() ?: 1.0,
                    mj = (map["mj"] as? String) ?: "ks",
                    jCena = (map["jCena"] as? Number)?.toDouble() ?: 0.0,
                    dphPct = (map["dphPct"] as? Number)?.toDouble() ?: 21.0,
                    sleva = (map["sleva"] as? Number)?.toDouble() ?: 0.0,
                    pdp = (map["pdp"] as? Boolean) ?: false,
                    castkaBez = (map["castkaBez"] as? Number)?.toDouble() ?: 0.0,
                    dphVal = (map["dphVal"] as? Number)?.toDouble() ?: 0.0,
                    stav = (map["stav"] as? String) ?: "Nová"
                )
            } ?: emptyList()
            NabidkaRecord(
                id = doc.id,
                cislo = doc.getString("cislo") ?: "",
                depo = doc.getString("depo") ?: "Depo 1",
                datum = doc.getString("datum") ?: "",
                platnost = doc.getString("platnost") ?: "",
                ic = doc.getString("ic") ?: "",
                dic = doc.getString("dic") ?: "",
                firma = doc.getString("firma") ?: "",
                jmeno = doc.getString("jmeno") ?: "",
                telefon = doc.getString("telefon") ?: "",
                email = doc.getString("email") ?: "",
                ulice = doc.getString("ulice") ?: "",
                text = doc.getString("text") ?: "",
                zdrojObjednavkaId = doc.getString("zdrojObjednavkaId") ?: "",
                adresarId = doc.getString("adresarId") ?: "",
                status = doc.getString("status")?.ifBlank { "NOVÁ" } ?: "NOVÁ",
                items = rawItems,
                timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getNabidky(): Flow<List<NabidkaRecord>> = callbackFlow {
        val listener = db.collection("nabidky")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseNabidkaDoc(it) }
                    trySend(list.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getAllNabidkyDirect(): List<NabidkaRecord> {
        return try {
            val snap = db.collection("nabidky").get().await()
            snap.documents.mapNotNull { parseNabidkaDoc(it) }.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseZakazkaDoc(doc: com.google.firebase.firestore.DocumentSnapshot): ZakazkaRecord? {
        return try {
            val rawItems = (doc.get("items") as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                NabidkaItemRecord(
                    id = (map["id"] as? String) ?: "",
                    kod = (map["kod"] as? String) ?: "KONT",
                    text = (map["text"] as? String) ?: "",
                    mnozstvi = (map["mnozstvi"] as? Number)?.toDouble() ?: 1.0,
                    mj = (map["mj"] as? String) ?: "ks",
                    jCena = (map["jCena"] as? Number)?.toDouble() ?: 0.0,
                    dphPct = (map["dphPct"] as? Number)?.toDouble() ?: 21.0,
                    sleva = (map["sleva"] as? Number)?.toDouble() ?: 0.0,
                    pdp = (map["pdp"] as? Boolean) ?: false,
                    castkaBez = (map["castkaBez"] as? Number)?.toDouble() ?: 0.0,
                    dphVal = (map["dphVal"] as? Number)?.toDouble() ?: 0.0,
                    stav = (map["stav"] as? String) ?: "Předán",
                    komentar = (map["komentar"] as? String) ?: "",
                    datumPredani = (map["datumPredani"] as? String) ?: "",
                    datumVraceni = (map["datumVraceni"] as? String) ?: "",
                    predkontace = (map["predkontace"] as? String) ?: "Skladové"
                )
            } ?: emptyList()
            val rawPhotos = (doc.get("photos") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            ZakazkaRecord(
                id = doc.id,
                cisloZakazky = doc.getString("cisloZakazky") ?: "",
                datumCasZakazky = doc.getString("datumCasZakazky") ?: "",
                cisloNabidky = doc.getString("cisloNabidky") ?: "",
                cisloObjednavky = doc.getString("cisloObjednavky") ?: "",
                depo = doc.getString("depo") ?: "Depo 1",
                stredisko = doc.getString("stredisko")?.ifBlank { "Pronájem" } ?: "Pronájem",
                prepravaInfo = doc.getString("prepravaInfo") ?: "",
                ic = doc.getString("ic") ?: "",
                dic = doc.getString("dic") ?: "",
                ulice = doc.getString("ulice") ?: "",
                firma = doc.getString("firma") ?: "",
                vyrizuje = doc.getString("vyrizuje") ?: "",
                email = doc.getString("email") ?: "",
                telefon = doc.getString("telefon") ?: "",
                lokalita = doc.getString("lokalita") ?: "",
                osobaKPrevzeti = doc.getString("osobaKPrevzeti") ?: "",
                kontaktPrevzeti = doc.getString("kontaktPrevzeti") ?: "",
                pocetOB = doc.getString("pocetOB") ?: "0",
                pocetSK = doc.getString("pocetSK") ?: "0",
                pocetSA = doc.getString("pocetSA") ?: "0",
                pocetMA = doc.getString("pocetMA") ?: "0",
                pocetZI = doc.getString("pocetZI") ?: "0",
                pocetST = doc.getString("pocetST") ?: "0",
                pocetSK2 = doc.getString("pocetSK2") ?: "0",
                pocetSA2 = doc.getString("pocetSA2") ?: "0",
                poznamkaZObjednavky = doc.getString("poznamkaZObjednavky") ?: "",
                splatnost = doc.getString("splatnost") ?: "14",
                obdobiOd = doc.getString("obdobiOd") ?: "",
                obdobiDo = doc.getString("obdobiDo") ?: "",
                datumDodano = doc.getString("datumDodano") ?: "",
                datumVraceno = doc.getString("datumVraceno") ?: "",
                preprava1 = doc.getString("preprava1") ?: "",
                zpet1 = doc.getString("zpet1") ?: "",
                preprava2 = doc.getString("preprava2") ?: "",
                zpet2 = doc.getString("zpet2") ?: "",
                cenovaNabidkaCislo = doc.getString("cenovaNabidkaCislo") ?: "",
                protokolTam = doc.getString("protokolTam") ?: "",
                smlouvaCislo = doc.getString("smlouvaCislo") ?: "",
                protokolZpet = doc.getString("protokolZpet") ?: "",
                kauceDatum = doc.getString("kauceDatum") ?: "",
                kauceCastka = doc.getString("kauceCastka") ?: "",
                kauceVraceni = doc.getString("kauceVraceni") ?: "",
                kauceVraceniCastka = doc.getString("kauceVraceniCastka") ?: "",
                interniPoznamka = doc.getString("interniPoznamka") ?: "",
                photos = rawPhotos,
                fakturaceDatum = doc.getString("fakturaceDatum") ?: "",
                fakturaceObdobi = doc.getString("fakturaceObdobi") ?: "",
                zdrojObjednavkaId = doc.getString("zdrojObjednavkaId") ?: "",
                zdrojNabidkaId = doc.getString("zdrojNabidkaId") ?: "",
                adresarId = doc.getString("adresarId") ?: "",
                status = doc.getString("status")?.ifBlank { "NOVÁ" } ?: "NOVÁ",
                items = rawItems,
                timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getZakazky(): Flow<List<ZakazkaRecord>> = callbackFlow {
        val listener = db.collection("zakazky")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseZakazkaDoc(it) }
                    trySend(list.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    private fun parseZajemceDoc(doc: com.google.firebase.firestore.DocumentSnapshot): ZajemceRecord? {
        return try {
            ZajemceRecord(
                id = doc.id,
                cisloZajemce = doc.getString("cisloZajemce") ?: "",
                depo = doc.getString("depo") ?: "Depo 1",
                jmeno = doc.getString("jmeno") ?: "",
                firma = doc.getString("firma") ?: "",
                telefon = doc.getString("telefon") ?: "",
                email = doc.getString("email") ?: "",
                typKontejneru = doc.getString("typKontejneru") ?: "Obytný kontejner",
                zamer = doc.getString("zamer") ?: "Nákup",
                rozpocet = doc.getString("rozpocet") ?: "",
                status = doc.getString("status") ?: "AKTIVNÍ",
                poznamka = doc.getString("poznamka") ?: "",
                timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis(),
                isDeleted = doc.getBoolean("isDeleted") ?: false,
                photos = (doc.get("photos") as? List<*>)?.mapNotNull { it as? String }
                    ?: (doc.getString("extraImages")?.let { com.example.ui.parseExtraImageUris(it) } ?: emptyList())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getZajemci(): Flow<List<ZajemceRecord>> = callbackFlow {
        val listener = db.collection("zajemci")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseZajemceDoc(it) }
                    trySend(list.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertZajemce(zajemce: ZajemceRecord) {
        val docRef = if (zajemce.id.isEmpty()) db.collection("zajemci").document() else db.collection("zajemci").document(zajemce.id)
        val payload = mapOf(
            "id" to docRef.id,
            "cisloZajemce" to zajemce.cisloZajemce,
            "depo" to zajemce.depo,
            "jmeno" to zajemce.jmeno,
            "firma" to zajemce.firma,
            "telefon" to zajemce.telefon,
            "email" to zajemce.email,
            "typKontejneru" to zajemce.typKontejneru,
            "zamer" to zajemce.zamer,
            "rozpocet" to zajemce.rozpocet,
            "status" to zajemce.status,
            "poznamka" to zajemce.poznamka,
            "timestamp" to zajemce.timestamp,
            "isDeleted" to zajemce.isDeleted,
            "photos" to zajemce.photos.take(3),
            "extraImages" to zajemce.photos.take(3).joinToString("|")
        )
        docRef.set(payload).await()
    }

    suspend fun deleteZajemce(id: String) {
        if (id.isNotEmpty()) {
            db.collection("zajemci").document(id).delete().await()
        }
    }

    suspend fun getAllZakazkyDirect(): List<ZakazkaRecord> {
        return try {
            val snap = db.collection("zakazky").get().await()
            snap.documents.mapNotNull { parseZakazkaDoc(it) }.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCatalogDoc(doc: com.google.firebase.firestore.DocumentSnapshot): CatalogItemRecord? {
        return try {
            val pocet = (doc.get("pocet") as? Number)?.toDouble() ?: 1.0
            val cenaBezDph = (doc.get("cenaBezDph") as? Number)?.toDouble() ?: 0.0
            val dphPct = (doc.get("dphPct") as? Number)?.toDouble() ?: 21.0
            val slevaPct = (doc.get("slevaPct") as? Number)?.toDouble() ?: 0.0
            val celkemBez = (doc.get("celkemBezDph") as? Number)?.toDouble()
                ?: kotlin.math.round(pocet * cenaBezDph * (1.0 - slevaPct / 100.0))
            val celkemSDph = (doc.get("celkemSDph") as? Number)?.toDouble()
                ?: kotlin.math.round(celkemBez * (1.0 + dphPct / 100.0))
            CatalogItemRecord(
                id = doc.id,
                sekce = doc.getString("sekce") ?: "KONTEJNERY",
                kategorie = doc.getString("kategorie") ?: "Obytné kontejnery",
                oznaceni = doc.getString("oznaceni") ?: "001",
                nazev = doc.getString("nazev") ?: "",
                pocet = pocet,
                cenaBezDph = cenaBezDph,
                dphPct = dphPct,
                slevaPct = slevaPct,
                celkemBezDph = celkemBez,
                celkemSDph = celkemSDph,
                poradi = (doc.get("poradi") as? Number)?.toInt() ?: 1,
                timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getCatalogItems(): Flow<List<CatalogItemRecord>> = callbackFlow {
        var polozkyList: List<CatalogItemRecord> = emptyList()
        var catalogItemsList: List<CatalogItemRecord> = emptyList()

        fun emitMerged() {
            val map = LinkedHashMap<String, CatalogItemRecord>()
            (catalogItemsList + polozkyList).forEach { item ->
                val ex = map[item.id]
                if (ex == null || item.timestamp >= ex.timestamp) {
                    map[item.id] = item
                }
            }
            trySend(map.values.toList())
        }

        val l1 = db.collection("polozky").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                polozkyList = snapshot.documents.mapNotNull { parseCatalogDoc(it) }
                emitMerged()
            }
        }
        val l2 = db.collection("catalog_items").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                catalogItemsList = snapshot.documents.mapNotNull { parseCatalogDoc(it) }
                emitMerged()
            }
        }
        awaitClose {
            l1.remove()
            l2.remove()
        }
    }

    suspend fun getAllCatalogItemsDirect(): List<CatalogItemRecord> {
        val map = LinkedHashMap<String, CatalogItemRecord>()
        listOf("catalog_items", "polozky").forEach { col ->
            try {
                val snap = db.collection(col).get().await()
                snap.documents.mapNotNull { parseCatalogDoc(it) }.forEach { item ->
                    val ex = map[item.id]
                    if (ex == null || item.timestamp >= ex.timestamp) {
                        map[item.id] = item
                    }
                }
            } catch (_: Exception) {}
        }
        return map.values.toList()
    }

    suspend fun upsertCatalogItem(item: CatalogItemRecord) {
        val docRef = if (item.id.isEmpty()) db.collection("polozky").document() else db.collection("polozky").document(item.id)
        val celkemBez = kotlin.math.round(item.pocet * item.cenaBezDph * (1.0 - item.slevaPct / 100.0))
        val celkemS = kotlin.math.round(celkemBez * (1.0 + item.dphPct / 100.0))
        val payload = mapOf(
            "id" to docRef.id,
            "sekce" to item.sekce,
            "kategorie" to item.kategorie,
            "oznaceni" to item.oznaceni,
            "nazev" to item.nazev,
            "pocet" to item.pocet,
            "mj" to "ks",
            "cenaBezDph" to item.cenaBezDph,
            "dphPct" to item.dphPct,
            "slevaPct" to item.slevaPct,
            "celkemBezDph" to celkemBez,
            "celkemSDph" to celkemS,
            "poradi" to item.poradi,
            "timestamp" to item.timestamp
        )
        docRef.set(payload).await()
        try { db.collection("catalog_items").document(docRef.id).set(payload).await() } catch (_: Exception) {}
    }

    suspend fun deleteCatalogItem(id: String) {
        if (id.isNotEmpty()) {
            try { db.collection("polozky").document(id).delete().await() } catch (_: Exception) {}
            try { db.collection("catalog_items").document(id).delete().await() } catch (_: Exception) {}
        }
    }

    suspend fun insertNabidka(nabidka: NabidkaRecord, defaultItemText: String = "") {
        val docRef = if (nabidka.id.isEmpty()) db.collection("nabidky").document() else db.collection("nabidky").document(nabidka.id)
        val itemsList = if (nabidka.items.isNotEmpty()) {
            nabidka.items.map { item ->
                mapOf(
                    "id" to item.id.ifBlank { "nitem_${System.currentTimeMillis()}" },
                    "kod" to item.kod,
                    "text" to item.text,
                    "mnozstvi" to item.mnozstvi,
                    "mj" to item.mj,
                    "jCena" to item.jCena,
                    "dphPct" to item.dphPct,
                    "sleva" to item.sleva,
                    "pdp" to item.pdp,
                    "castkaBez" to item.castkaBez,
                    "dphVal" to item.dphVal
                )
            }
        } else if (defaultItemText.isNotBlank()) {
            listOf(
                mapOf(
                    "id" to "nitem_${System.currentTimeMillis()}",
                    "kod" to "KONT",
                    "text" to defaultItemText,
                    "mnozstvi" to 1,
                    "mj" to "ks",
                    "jCena" to 0,
                    "dphPct" to 21,
                    "sleva" to 0,
                    "pdp" to false,
                    "castkaBez" to 0,
                    "dphVal" to 0
                )
            )
        } else {
            emptyList()
        }
        val payload = mapOf(
            "id" to docRef.id,
            "cislo" to nabidka.cislo,
            "depo" to nabidka.depo,
            "datum" to nabidka.datum,
            "platnost" to nabidka.platnost,
            "ic" to nabidka.ic,
            "dic" to nabidka.dic,
            "firma" to nabidka.firma,
            "jmeno" to nabidka.jmeno,
            "telefon" to nabidka.telefon,
            "email" to nabidka.email,
            "ulice" to nabidka.ulice,
            "text" to nabidka.text,
            "zdrojObjednavkaId" to nabidka.zdrojObjednavkaId,
            "adresarId" to nabidka.adresarId,
            "status" to nabidka.status.ifBlank { "NOVÁ" },
            "items" to itemsList,
            "timestamp" to nabidka.timestamp
        )
        docRef.set(payload).await()
    }

    suspend fun insertCustomer(customer: CustomerRecord) {
        val docRef = if (customer.id.isEmpty()) db.collection("customers").document() else db.collection("customers").document(customer.id)
        val payload = mapOf(
            "id" to docRef.id,
            "name" to customer.name,
            "company" to customer.company,
            "phone" to customer.phone,
            "email" to customer.email,
            "ic" to customer.ic,
            "dic" to customer.dic,
            "ulice" to customer.ulice,
            "inquiryType" to customer.inquiryType,
            "message" to customer.message,
            "status" to customer.status.ifBlank { "NOVÁ" },
            "depo" to customer.depo,
            "cisloZakaznika" to customer.cisloZakaznika,
            "isOrder" to customer.isOrder,
            "isAdresar" to customer.isAdresar,
            "bonita" to customer.bonita.ifBlank { "AAA" },
            "bonitaDetail" to customer.bonitaDetail,
            "photos" to customer.photos.take(3),
            "extraImages" to customer.photos.take(3).joinToString("|"),
            "udalosti" to customer.udalosti.map { ev ->
                mapOf(
                    "id" to ev.id.ifBlank { "ev_${System.currentTimeMillis()}" },
                    "datum" to ev.datum,
                    "typ" to ev.typ,
                    "cisloDokladu" to ev.cisloDokladu,
                    "popis" to ev.popis,
                    "castka" to ev.castka,
                    "timestamp" to ev.timestamp
                )
            },
            "timestamp" to customer.timestamp
        )
        docRef.set(payload).await()
        if (customer.isOrder) {
            try { db.collection("objednavky").document(docRef.id).set(payload).await() } catch (_: Exception) {}
        }
        if (customer.isAdresar) {
            try { db.collection("adresar").document(docRef.id).set(payload).await() } catch (_: Exception) {}
        }
    }

    suspend fun insertZakazka(
        zakazka: ZakazkaRecord,
        defaultItemText: String = "",
        sourceItems: List<NabidkaItemRecord> = emptyList()
    ) {
        val docRef = if (zakazka.id.isEmpty()) db.collection("zakazky").document() else db.collection("zakazky").document(zakazka.id)
        val effectiveItems = if (sourceItems.isNotEmpty()) sourceItems else zakazka.items
        val itemsList = if (effectiveItems.isNotEmpty()) {
            effectiveItems.map { item ->
                mapOf(
                    "id" to item.id.ifBlank { "zitem_${System.currentTimeMillis()}" },
                    "kod" to item.kod,
                    "text" to item.text,
                    "mnozstvi" to item.mnozstvi,
                    "mj" to item.mj,
                    "jCena" to item.jCena,
                    "dphPct" to item.dphPct,
                    "sleva" to item.sleva,
                    "pdp" to item.pdp,
                    "castkaBez" to item.castkaBez,
                    "dphVal" to item.dphVal,
                    "stav" to item.stav.ifBlank { "Předán" },
                    "komentar" to item.komentar,
                    "datumPredani" to item.datumPredani,
                    "datumVraceni" to item.datumVraceni,
                    "predkontace" to item.predkontace.ifBlank { "Skladové" }
                )
            }
        } else if (defaultItemText.isNotBlank()) {
            listOf(
                mapOf(
                    "id" to "zitem_${System.currentTimeMillis()}",
                    "kod" to "001",
                    "text" to defaultItemText,
                    "mnozstvi" to 1,
                    "mj" to "ks",
                    "jCena" to 0,
                    "dphPct" to 21,
                    "sleva" to 0,
                    "pdp" to false,
                    "castkaBez" to 0,
                    "dphVal" to 0,
                    "stav" to "Předán",
                    "komentar" to "",
                    "datumPredani" to "",
                    "datumVraceni" to "",
                    "predkontace" to "Skladové"
                )
            )
        } else {
            emptyList()
        }
        val payload = mapOf(
            "id" to docRef.id,
            "cisloZakazky" to zakazka.cisloZakazky,
            "datumCasZakazky" to zakazka.datumCasZakazky,
            "cisloNabidky" to zakazka.cisloNabidky,
            "cisloObjednavky" to zakazka.cisloObjednavky,
            "depo" to zakazka.depo,
            "stredisko" to zakazka.stredisko.ifBlank { "Pronájem" },
            "prepravaInfo" to zakazka.prepravaInfo,
            "ic" to zakazka.ic,
            "dic" to zakazka.dic,
            "ulice" to zakazka.ulice,
            "firma" to zakazka.firma,
            "vyrizuje" to zakazka.vyrizuje,
            "email" to zakazka.email,
            "telefon" to zakazka.telefon,
            "lokalita" to zakazka.lokalita,
            "osobaKPrevzeti" to zakazka.osobaKPrevzeti,
            "kontaktPrevzeti" to zakazka.kontaktPrevzeti,
            "pocetOB" to zakazka.pocetOB,
            "pocetSK" to zakazka.pocetSK,
            "pocetSA" to zakazka.pocetSA,
            "pocetMA" to zakazka.pocetMA,
            "pocetZI" to zakazka.pocetZI,
            "pocetST" to zakazka.pocetST,
            "pocetSK2" to zakazka.pocetSK2,
            "pocetSA2" to zakazka.pocetSA2,
            "poznamkaZObjednavky" to zakazka.poznamkaZObjednavky,
            "splatnost" to zakazka.splatnost,
            "obdobiOd" to zakazka.obdobiOd,
            "obdobiDo" to zakazka.obdobiDo,
            "datumDodano" to zakazka.datumDodano,
            "datumVraceno" to zakazka.datumVraceno,
            "preprava1" to zakazka.preprava1,
            "zpet1" to zakazka.zpet1,
            "preprava2" to zakazka.preprava2,
            "zpet2" to zakazka.zpet2,
            "cenovaNabidkaCislo" to zakazka.cenovaNabidkaCislo,
            "protokolTam" to zakazka.protokolTam,
            "smlouvaCislo" to zakazka.smlouvaCislo,
            "protokolZpet" to zakazka.protokolZpet,
            "kauceDatum" to zakazka.kauceDatum,
            "kauceCastka" to zakazka.kauceCastka,
            "kauceVraceni" to zakazka.kauceVraceni,
            "kauceVraceniCastka" to zakazka.kauceVraceniCastka,
            "interniPoznamka" to zakazka.interniPoznamka,
            "photos" to zakazka.photos,
            "fakturaceDatum" to zakazka.fakturaceDatum,
            "fakturaceObdobi" to zakazka.fakturaceObdobi,
            "zdrojObjednavkaId" to zakazka.zdrojObjednavkaId,
            "zdrojNabidkaId" to zakazka.zdrojNabidkaId,
            "adresarId" to zakazka.adresarId,
            "status" to zakazka.status.ifBlank { "NOVÁ" },
            "items" to itemsList,
            "timestamp" to zakazka.timestamp
        )
        docRef.set(payload).await()
    }

    suspend fun deleteNabidka(id: String) {
        if (id.isNotEmpty()) {
            db.collection("nabidky").document(id).delete().await()
        }
    }

    suspend fun deleteZakazka(id: String) {
        if (id.isNotEmpty()) {
            db.collection("zakazky").document(id).delete().await()
        }
    }

    suspend fun deleteTransportById(id: String) {
        if (id.isNotEmpty()) {
            markIdDeletedRemote(id, "transports")
            db.collection("transports").document(id).delete().await()
        }
    }

    private fun parseFakturaDoc(doc: com.google.firebase.firestore.DocumentSnapshot, defaultTyp: String = "FAKTURA"): FakturaRecord? {
        return try {
            val rawItems = (doc.get("items") as? List<*>)?.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val mn = (map["mnozstvi"] as? Number)?.toDouble() ?: 1.0
                val jc = (map["jCena"] as? Number)?.toDouble() ?: 0.0
                val dphPct = (map["dphPct"] as? Number)?.toDouble() ?: 21.0
                val sleva = (map["sleva"] as? Number)?.toDouble() ?: 0.0
                val pdp = map["pdp"] as? Boolean ?: false
                val castkaBez = (map["castkaBez"] as? Number)?.toDouble()
                    ?: kotlin.math.round(mn * jc * (1.0 - sleva / 100.0))
                val dphVal = (map["dphVal"] as? Number)?.toDouble()
                    ?: if (pdp) 0.0 else kotlin.math.round(castkaBez * (dphPct / 100.0))
                NabidkaItemRecord(
                    id = map["id"]?.toString() ?: "",
                    kod = map["kod"]?.toString() ?: "KONT",
                    text = map["text"]?.toString() ?: "",
                    mnozstvi = mn,
                    mj = map["mj"]?.toString() ?: "ks",
                    jCena = jc,
                    dphPct = dphPct,
                    sleva = sleva,
                    pdp = pdp,
                    castkaBez = castkaBez,
                    dphVal = dphVal,
                    stav = map["stav"]?.toString() ?: "Nová"
                )
            } ?: emptyList()

            FakturaRecord(
                id = doc.id,
                cislo = doc.getString("cislo") ?: "",
                variabilniSymbol = doc.getString("variabilniSymbol") ?: (doc.getString("cislo")?.filter { it.isDigit() } ?: ""),
                typDokladu = doc.getString("typDokladu") ?: defaultTyp,
                ucelFaktury = doc.getString("ucelFaktury") ?: "PRODEJ",
                depo = doc.getString("depo") ?: "Depo 1",
                stredisko = doc.getString("stredisko") ?: "Prodej",
                datumVystaveni = doc.getString("datumVystaveni") ?: "",
                datumZdanitelnehoPlneni = doc.getString("datumZdanitelnehoPlneni") ?: "",
                datumSplatnosti = doc.getString("datumSplatnosti") ?: "",
                formaUhrady = doc.getString("formaUhrady") ?: "Příkazem",
                cisloUctu = doc.getString("cisloUctu") ?: "2101234567/2010",
                iban = doc.getString("iban") ?: "CZ6520100000002101234567",
                ic = doc.getString("ic") ?: "",
                dic = doc.getString("dic") ?: "",
                firma = doc.getString("firma") ?: "",
                jmeno = doc.getString("jmeno") ?: "",
                ulice = doc.getString("ulice") ?: "",
                telefon = doc.getString("telefon") ?: "",
                email = doc.getString("email") ?: "",
                oznaceniKontejneru = doc.getString("oznaceniKontejneru") ?: "",
                typKontejneru = doc.getString("typKontejneru") ?: "",
                obdobiPronajmu = doc.getString("obdobiPronajmu") ?: "",
                mistoDodani = doc.getString("mistoDodani") ?: "",
                cisloZakazky = doc.getString("cisloZakazky") ?: "",
                cisloNabidky = doc.getString("cisloNabidky") ?: "",
                zdrojNabidkaId = doc.getString("zdrojNabidkaId") ?: "",
                zdrojZakazkaId = doc.getString("zdrojZakazkaId") ?: "",
                zdrojKontejnerId = doc.getString("zdrojKontejnerId") ?: "",
                adresarId = doc.getString("adresarId") ?: "",
                stavUhrady = doc.getString("stavUhrady") ?: "Neuhrazeno",
                text = doc.getString("text") ?: "",
                poznamka = doc.getString("poznamka") ?: "",
                items = rawItems,
                timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: System.currentTimeMillis(),
                pohodaId = doc.getString("pohodaId") ?: "",
                pohodaSyncedAt = (doc.get("pohodaSyncedAt") as? Number)?.toLong(),
                pohodaStatus = doc.getString("pohodaStatus") ?: "",
                pohodaError = doc.getString("pohodaError") ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getFaktury(): Flow<List<FakturaRecord>> = callbackFlow {
        val listener = db.collection("faktury")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseFakturaDoc(it, "FAKTURA") }
                    trySend(list.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    fun getZalohoveFaktury(): Flow<List<FakturaRecord>> = callbackFlow {
        val listener = db.collection("zalohove_faktury")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseFakturaDoc(it, "ZALOHOVA_FAKTURA") }
                    trySend(list.sortedByDescending { it.timestamp })
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getAllFakturyDirect(): List<FakturaRecord> {
        return try {
            val snap = db.collection("faktury").get().await()
            snap.documents.mapNotNull { parseFakturaDoc(it, "FAKTURA") }.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAllZalohoveFakturyDirect(): List<FakturaRecord> {
        return try {
            val snap = db.collection("zalohove_faktury").get().await()
            snap.documents.mapNotNull { parseFakturaDoc(it, "ZALOHOVA_FAKTURA") }.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun insertFaktura(faktura: FakturaRecord) {
        val colName = if (faktura.typDokladu == "ZALOHOVA_FAKTURA") "zalohove_faktury" else "faktury"
        val docRef = if (faktura.id.isEmpty()) db.collection(colName).document() else db.collection(colName).document(faktura.id)
        val itemsList = faktura.items.map { item ->
            mapOf(
                "id" to item.id.ifBlank { "fitem_${System.currentTimeMillis()}" },
                "kod" to item.kod,
                "text" to item.text,
                "mnozstvi" to item.mnozstvi,
                "mj" to item.mj,
                "jCena" to item.jCena,
                "dphPct" to item.dphPct,
                "sleva" to item.sleva,
                "pdp" to item.pdp,
                "castkaBez" to item.castkaBez,
                "dphVal" to item.dphVal,
                "stav" to item.stav.ifBlank { "Nová" }
            )
        }
        val payload = mapOf(
            "id" to docRef.id,
            "cislo" to faktura.cislo,
            "variabilniSymbol" to faktura.variabilniSymbol.ifBlank { faktura.cislo.filter { it.isDigit() } },
            "typDokladu" to faktura.typDokladu,
            "ucelFaktury" to faktura.ucelFaktury,
            "depo" to faktura.depo,
            "stredisko" to faktura.stredisko,
            "datumVystaveni" to faktura.datumVystaveni,
            "datumZdanitelnehoPlneni" to faktura.datumZdanitelnehoPlneni,
            "datumSplatnosti" to faktura.datumSplatnosti,
            "formaUhrady" to faktura.formaUhrady,
            "cisloUctu" to faktura.cisloUctu,
            "iban" to faktura.iban,
            "ic" to faktura.ic,
            "dic" to faktura.dic,
            "firma" to faktura.firma,
            "jmeno" to faktura.jmeno,
            "ulice" to faktura.ulice,
            "telefon" to faktura.telefon,
            "email" to faktura.email,
            "oznaceniKontejneru" to faktura.oznaceniKontejneru,
            "typKontejneru" to faktura.typKontejneru,
            "obdobiPronajmu" to faktura.obdobiPronajmu,
            "mistoDodani" to faktura.mistoDodani,
            "cisloZakazky" to faktura.cisloZakazky,
            "cisloNabidky" to faktura.cisloNabidky,
            "zdrojNabidkaId" to faktura.zdrojNabidkaId,
            "zdrojZakazkaId" to faktura.zdrojZakazkaId,
            "zdrojKontejnerId" to faktura.zdrojKontejnerId,
            "adresarId" to faktura.adresarId,
            "stavUhrady" to faktura.stavUhrady,
            "text" to faktura.text,
            "poznamka" to faktura.poznamka,
            "items" to itemsList,
            "timestamp" to faktura.timestamp,
            "pohodaId" to faktura.pohodaId,
            "pohodaSyncedAt" to (faktura.pohodaSyncedAt ?: 0L),
            "pohodaStatus" to faktura.pohodaStatus,
            "pohodaError" to faktura.pohodaError
        )
        docRef.set(payload).await()
    }

    suspend fun deleteFaktura(id: String, isZalohova: Boolean = false) {
        if (id.isNotEmpty()) {
            val colName = if (isZalohova) "zalohove_faktury" else "faktury"
            markIdDeletedRemote(id, colName)
            db.collection(colName).document(id).delete().await()
        }
    }

    suspend fun getAllDeletedIdsDirect(): Set<String> {
        return try {
            val snap = db.collection("deleted_sync_ids").get().await()
            snap.documents.mapNotNull { it.getString("id") ?: it.id }.filter { it.isNotBlank() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun getAllZajemciDirect(): List<ZajemceRecord> {
        return try {
            val snap = db.collection("zajemci").get().await()
            snap.documents.mapNotNull { parseZajemceDoc(it) }
                .filter { !it.isDeleted }
                .sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun insertZajemce(zajemce: ZajemceRecord) {
        val docRef = if (zajemce.id.isBlank()) db.collection("zajemci").document() else db.collection("zajemci").document(zajemce.id)
        val cleanPhotos = zajemce.photos.mapNotNull { sanitizeSingleImageUri(it) }.filter { it.isNotBlank() }
        val payload = mapOf(
            "id" to docRef.id,
            "cisloZajemce" to zajemce.cisloZajemce,
            "depo" to zajemce.depo.ifBlank { "Depo 1" },
            "jmeno" to zajemce.jmeno,
            "firma" to zajemce.firma,
            "telefon" to zajemce.telefon,
            "email" to zajemce.email,
            "typKontejneru" to zajemce.typKontejneru,
            "zamer" to zajemce.zamer,
            "rozpocet" to zajemce.rozpocet,
            "status" to zajemce.status.ifBlank { "AKTIVNÍ" },
            "poznamka" to zajemce.poznamka,
            "vizitkaUri" to (sanitizeSingleImageUri(zajemce.vizitkaUri) ?: ""),
            "timestamp" to (if (zajemce.timestamp > 0L) zajemce.timestamp else System.currentTimeMillis()),
            "isDeleted" to zajemce.isDeleted,
            "photos" to cleanPhotos
        )
        docRef.set(payload).await()
    }
}

