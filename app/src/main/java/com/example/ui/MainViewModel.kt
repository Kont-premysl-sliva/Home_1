package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.analyzeContainer
import com.example.data.ContainerCard
import com.example.data.ContainerRepository
import com.example.data.EventRecord
import com.example.data.InventoryRecord
import com.example.data.TransportRecord
import com.example.data.BackupManager
import com.example.api.PohodaMServerService
import com.example.api.PohodaServerConfig
import com.example.api.PohodaSyncResult
import com.example.api.MPohodaApiService
import com.example.api.MPohodaConfig
import com.example.api.MPohodaSyncResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ContainerAnalysisResult(
    val designation: String = "",
    val color: String = "",
    val description: String = "",
    val type: String = "",
    val spz: String? = null,
    val containerBox: List<Int>? = null,
    val designationBox: List<Int>? = null
)

data class ProtocolAnalysisResult(
    val soldCustomerName: String = "",
    val soldCustomerId: String = "",
    val soldPhone: String = "",
    val soldEmail: String = "",
    val soldDelivery: String = ""
)

data class PrepravaAnalysisResult(
    val designation: String = "",
    val date: String = "",
    val company: String = "",
    val spz: String = "",
    val driver: String = "",
    val containerType: String = "",
    val color: String = "",
    val description: String = "",
    val note: String = "",
    val rawExtractedText: String = ""
)

data class QuickInventoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var designation: String = "",
    var type: String = "",
    var color: String = "",
    var description: String = "",
    var spz: String = "",
    var imageUri: String = ""
)

enum class UserRole(
    val code: String,
    val title: String,
    val shortLabel: String,
    val subtitle: String
) {
    ADMIN(
        code = "ADMIN",
        title = "1. Admin",
        shortLabel = "ADMIN",
        subtitle = "Vidí a edituje vše"
    ),
    DEPO(
        code = "DEPO",
        title = "2. DEPO (správce DEPA)",
        shortLabel = "DEPO",
        subtitle = "KONTEJNERY • PŘEPRAVA • ZÁJEMCI • NASTAVENÍ"
    ),
    PREPRAVCE(
        code = "PREPRAVCE",
        title = "3. PŘEPRAVCE",
        shortLabel = "PŘEPRAVCE",
        subtitle = "Vidí a edituje pouze sekci PŘEPRAVA"
    );

    companion object {
        fun fromCode(code: String?): UserRole {
            return entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: ADMIN
        }
    }
}

class MainViewModel(
    private val repository: ContainerRepository, 
    private val filesDir: File,
    private val context: Context? = null
) : ViewModel() {

    private val themeFile = File(filesDir, "theme_preference.txt")
    private val _themePreference = MutableStateFlow("system")
    val themePreference: StateFlow<String> = _themePreference.asStateFlow()

    private val userNameFile = File(filesDir, "user_name.txt")
    private val _userName = MutableStateFlow("premysl.sliva@gmail.com")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val userRoleFile = File(filesDir, "user_role.txt")
    private val _userRole = MutableStateFlow(UserRole.ADMIN)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    private val _roleConfirmedOnStartup = MutableStateFlow(false)
    val roleConfirmedOnStartup: StateFlow<Boolean> = _roleConfirmedOnStartup.asStateFlow()

    private val _backupProgress = MutableStateFlow<Int?>(null)
    val backupProgress: StateFlow<Int?> = _backupProgress.asStateFlow()

    private val _reindexProgress = MutableStateFlow<Boolean>(false)
    val reindexProgress: StateFlow<Boolean> = _reindexProgress.asStateFlow()

    private val ownersFile = File(filesDir, "owners_list.txt")
    private val _owners = MutableStateFlow<List<String>>(listOf("KONTEJNERY", "CARU", "OVLU", "MAGITU"))
    val owners: StateFlow<List<String>> = _owners.asStateFlow()

    private val prepravciFile = File(filesDir, "prepravci_list.txt")
    private val _prepravci = MutableStateFlow<List<String>>(
        listOf("Jirka", "Martin", "Vlastní doprava", "METRANS", "Čechofracht")
    )
    val prepravci: StateFlow<List<String>> = _prepravci.asStateFlow()

    private val equipmentFile = File(filesDir, "equipment_list.txt")
    private val _equipmentList = MutableStateFlow<List<String>>(
        listOf("Přímotop", "Židle", "Stůl", "Skříňka", "Šatní", "Klimatizace")
    )
    val equipmentList: StateFlow<List<String>> = _equipmentList.asStateFlow()

    private val driversFile = File(filesDir, "drivers_list.txt")
    private val _drivers = MutableStateFlow<List<String>>(
        listOf("Jirka", "Martin", "Petr", "Pavel")
    )
    val drivers: StateFlow<List<String>> = _drivers.asStateFlow()

    private val vehiclesFile = File(filesDir, "vehicles_list.txt")
    private val _vehicles = MutableStateFlow<List<String>>(
        listOf("Volvo", "Mercedes")
    )
    val vehicles: StateFlow<List<String>> = _vehicles.asStateFlow()

    private val customersFile = File(filesDir, "customers_sync.json")
    private val nabidkyFile = File(filesDir, "nabidky_sync.json")
    private val zakazkyFile = File(filesDir, "zakazky_sync.json")
    private val fakturyFile = File(filesDir, "faktury_sync.json")
    private val zalohoveFakturyFile = File(filesDir, "zalohove_faktury_sync.json")
    private val zajemciFile = File(filesDir, "zajemci_sync.json")
    private val containersFile = File(filesDir, "containers_sync.json")
    private val containerSysNumsFile = File(filesDir, "container_sys_nums.json")
    private val transportsFile = File(filesDir, "transports_sync.json")
    private val deletedIdsFile = File(filesDir, "deleted_sync_ids.json")
    private val deletedIds = mutableSetOf<String>()
    private val _localTransports = MutableStateFlow<List<TransportRecord>>(emptyList())
    private val _localContainers = MutableStateFlow<List<ContainerCard>>(emptyList())

    private val _customers = MutableStateFlow<List<com.example.data.CustomerRecord>>(emptyList())
    val customers: StateFlow<List<com.example.data.CustomerRecord>> = _customers.asStateFlow()

    private val _nabidky = MutableStateFlow<List<com.example.data.NabidkaRecord>>(emptyList())
    val nabidky: StateFlow<List<com.example.data.NabidkaRecord>> = _nabidky.asStateFlow()

    private val _zakazky = MutableStateFlow<List<com.example.data.ZakazkaRecord>>(emptyList())
    val zakazky: StateFlow<List<com.example.data.ZakazkaRecord>> = _zakazky.asStateFlow()

    private val _faktury = MutableStateFlow<List<com.example.data.FakturaRecord>>(emptyList())
    val faktury: StateFlow<List<com.example.data.FakturaRecord>> = _faktury.asStateFlow()

    private val _zalohoveFaktury = MutableStateFlow<List<com.example.data.FakturaRecord>>(emptyList())
    val zalohoveFaktury: StateFlow<List<com.example.data.FakturaRecord>> = _zalohoveFaktury.asStateFlow()

    private val _zajemci = MutableStateFlow<List<com.example.data.ZajemceRecord>>(emptyList())
    val zajemci: StateFlow<List<com.example.data.ZajemceRecord>> = _zajemci.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncStatus = MutableStateFlow("Synchronizováno")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private val catalogFile = File(filesDir, "polozky_catalog.json")
    private val _catalogItems = MutableStateFlow<List<com.example.data.CatalogItemRecord>>(defaultCatalogItems())
    val catalogItems: StateFlow<List<com.example.data.CatalogItemRecord>> = _catalogItems.asStateFlow()

    init {
        loadDeletedIds()
        loadLocalCatalogItems()
        loadLocalAgendasCache()
        viewModelScope.launch {
            repository.getDeletedIds().collect { remoteDeletedIds ->
                if (remoteDeletedIds.isNotEmpty()) {
                    val newDeletions = remoteDeletedIds.filter { it !in deletedIds }
                    if (newDeletions.isNotEmpty()) {
                        deletedIds.addAll(newDeletions)
                        saveDeletedIds()
                        _customers.value = _customers.value.filter { it.id !in deletedIds }
                        saveLocalCustomers(_customers.value)
                        _nabidky.value = _nabidky.value.filter { it.id !in deletedIds }
                        saveLocalNabidky(_nabidky.value)
                        _zakazky.value = _zakazky.value.filter { it.id !in deletedIds }
                        saveLocalZakazky(_zakazky.value)
                        _faktury.value = _faktury.value.filter { it.id !in deletedIds }
                        saveLocalFaktury(_faktury.value)
                        _zalohoveFaktury.value = _zalohoveFaktury.value.filter { it.id !in deletedIds }
                        saveLocalZalohoveFaktury(_zalohoveFaktury.value)
                        _zajemci.value = _zajemci.value.filter { it.id !in deletedIds }
                        saveLocalZajemci(_zajemci.value)
                        _localTransports.value = _localTransports.value.filter { it.id !in deletedIds }
                        saveLocalTransports(_localTransports.value)
                        _localContainers.value = _localContainers.value.filter { it.id !in deletedIds }
                        saveLocalContainers(_localContainers.value)
                        _catalogItems.value = _catalogItems.value.filter { it.id !in deletedIds }
                        saveLocalCatalogItems(_catalogItems.value)
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.getCatalogItems().collect { remoteList ->
                if (remoteList.isNotEmpty()) {
                    val merged = mergeCatalogWithDefaults(remoteList.filter { it.id !in deletedIds })
                    _catalogItems.value = sortCatalogList(merged)
                    saveLocalCatalogItems(_catalogItems.value)
                } else {
                    val current = _catalogItems.value
                    current.forEach { item ->
                        try { repository.upsertCatalogItem(item) } catch (_: Exception) {}
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.getCustomers().collect { remoteCustomers ->
                val merged = mergeByTimestamp(_customers.value, remoteCustomers).filter { it.id !in deletedIds }
                _customers.value = merged
                saveLocalCustomers(merged)
                updateSyncTimestamp()
            }
        }
        viewModelScope.launch {
            repository.getNabidky().collect { remoteNabidky ->
                val merged = mergeNabidkyByTimestamp(_nabidky.value, remoteNabidky).filter { it.id !in deletedIds }
                _nabidky.value = merged
                saveLocalNabidky(merged)
                syncContactsFromDocumentsToAdresar(merged, _zakazky.value)
                updateSyncTimestamp()
            }
        }
        viewModelScope.launch {
            repository.getZakazky().collect { remoteZakazky ->
                val merged = mergeZakazkyByTimestamp(_zakazky.value, remoteZakazky).filter { it.id !in deletedIds }
                _zakazky.value = merged
                saveLocalZakazky(merged)
                syncContactsFromDocumentsToAdresar(_nabidky.value, merged)
                updateSyncTimestamp()
            }
        }
        viewModelScope.launch {
            repository.getFaktury().collect { remoteFaktury ->
                val merged = mergeFakturyByTimestamp(_faktury.value, remoteFaktury).filter { it.id !in deletedIds }
                _faktury.value = merged
                saveLocalFaktury(merged)
                updateSyncTimestamp()
            }
        }
        viewModelScope.launch {
            repository.getZalohoveFaktury().collect { remoteZal ->
                val merged = mergeFakturyByTimestamp(_zalohoveFaktury.value, remoteZal).filter { it.id !in deletedIds }
                _zalohoveFaktury.value = merged
                saveLocalZalohoveFaktury(merged)
                updateSyncTimestamp()
            }
        }
        viewModelScope.launch {
            repository.getZajemci().collect { remoteZaj ->
                val merged = mergeZajemciByTimestamp(_zajemci.value, remoteZaj).filter { it.id !in deletedIds }
                _zajemci.value = merged
                saveLocalZajemci(merged)
                updateSyncTimestamp()
            }
        }
        viewModelScope.launch {
            repository.getSeznam("").collect { allConts ->
                val merged = mergeContainersByTimestamp(_localContainers.value, allConts).filter { it.id !in deletedIds }
                _localContainers.value = merged
                syncMaxContainerSystemNumbers(merged)
                saveLocalContainers(merged)
                updateSyncTimestamp()
                allConts.forEach { c ->
                    val img = c.imageUri ?: ""
                    val extras = c.extraImages ?: ""
                    if ((img.startsWith("/data/") || img.startsWith("file://") || img.startsWith("/storage/")) ||
                        (extras.contains("/data/") || extras.contains("file://") || extras.contains("/storage/"))
                    ) {
                        try { repository.update(c) } catch (_: Exception) {}
                    }
                }
            }
        }
        autoArchiveSoldContainers()

        if (themeFile.exists()) {
            try {
                val loaded = themeFile.readText().trim()
                if (loaded == "system" || loaded == "light" || loaded == "dark") {
                    _themePreference.value = loaded
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (ownersFile.exists()) {
            try {
                val loadedList = ownersFile.readText().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (loadedList.isNotEmpty()) {
                    _owners.value = loadedList
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (prepravciFile.exists()) {
            try {
                val loadedList = prepravciFile.readText().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (loadedList.isNotEmpty()) {
                    _prepravci.value = loadedList
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (equipmentFile.exists()) {
            try {
                val loadedList = equipmentFile.readText().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (loadedList.isNotEmpty()) {
                    _equipmentList.value = loadedList
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (driversFile.exists()) {
            try {
                val loadedList = driversFile.readText().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (loadedList.isNotEmpty()) {
                    _drivers.value = loadedList
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (vehiclesFile.exists()) {
            try {
                val loadedList = vehiclesFile.readText().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (loadedList.isNotEmpty()) {
                    _vehicles.value = loadedList
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (userNameFile.exists()) {
            try {
                val loaded = userNameFile.readText().trim()
                if (loaded.isNotEmpty()) {
                    _userName.value = loaded
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (userRoleFile.exists()) {
            try {
                val loadedRole = userRoleFile.readText().trim()
                if (loadedRole.isNotEmpty()) {
                    _userRole.value = UserRole.fromCode(loadedRole)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        reindexEntireDatabase()
    }

    fun addOwner(owner: String) {
        val current = _owners.value.toMutableList()
        if (!current.contains(owner) && owner.isNotBlank()) {
            current.add(owner)
            _owners.value = current
            saveOwners()
        }
    }

    fun removeOwner(owner: String) {
        val current = _owners.value.toMutableList()
        if (current.remove(owner)) {
            _owners.value = current
            saveOwners()
        }
    }

    fun addPrepravce(name: String) {
        val trimmed = name.trim()
        val current = _prepravci.value.toMutableList()
        if (trimmed.isNotBlank() && !current.contains(trimmed)) {
            current.add(trimmed)
            _prepravci.value = current
            savePrepravci()
        }
    }

    fun removePrepravce(name: String) {
        val current = _prepravci.value.toMutableList()
        if (current.remove(name)) {
            _prepravci.value = current
            savePrepravci()
        }
    }

    private fun savePrepravci() {
        viewModelScope.launch {
            try {
                prepravciFile.writeText(_prepravci.value.joinToString("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addEquipment(name: String) {
        val trimmed = name.trim()
        val current = _equipmentList.value.toMutableList()
        if (trimmed.isNotBlank() && !current.contains(trimmed)) {
            current.add(trimmed)
            _equipmentList.value = current
            saveEquipment()
        }
    }

    fun removeEquipment(name: String) {
        val current = _equipmentList.value.toMutableList()
        if (current.remove(name)) {
            _equipmentList.value = current
            saveEquipment()
        }
    }

    private fun saveEquipment() {
        viewModelScope.launch {
            try {
                equipmentFile.writeText(_equipmentList.value.joinToString("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addDriver(name: String) {
        val trimmed = name.trim()
        val current = _drivers.value.toMutableList()
        if (trimmed.isNotBlank() && !current.contains(trimmed)) {
            current.add(trimmed)
            _drivers.value = current
            saveDrivers()
        }
    }

    fun removeDriver(name: String) {
        val current = _drivers.value.toMutableList()
        if (current.remove(name)) {
            _drivers.value = current
            saveDrivers()
        }
    }

    private fun saveDrivers() {
        viewModelScope.launch {
            try {
                driversFile.writeText(_drivers.value.joinToString("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addVehicle(spz: String) {
        val trimmed = spz.trim()
        val current = _vehicles.value.toMutableList()
        if (trimmed.isNotBlank() && !current.any { it.equals(trimmed, ignoreCase = true) }) {
            current.add(trimmed)
            _vehicles.value = current
            saveVehicles()
        }
    }

    fun removeVehicle(spz: String) {
        val current = _vehicles.value.toMutableList()
        if (current.remove(spz)) {
            _vehicles.value = current
            saveVehicles()
        }
    }

    private fun saveVehicles() {
        viewModelScope.launch {
            try {
                vehiclesFile.writeText(_vehicles.value.joinToString("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setUserName(name: String) {
        if (name.isNotBlank()) {
            _userName.value = name.trim()
            viewModelScope.launch {
                try {
                    userNameFile.writeText(name.trim())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setUserRole(role: UserRole) {
        _userRole.value = role
        _roleConfirmedOnStartup.value = true
        viewModelScope.launch {
            try {
                userRoleFile.writeText(role.code)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun reopenRoleSelection() {
        _roleConfirmedOnStartup.value = false
    }

    private fun saveOwners() {
        viewModelScope.launch {
            try {
                ownersFile.writeText(_owners.value.joinToString("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setThemePreference(pref: String) {
        if (pref == "system" || pref == "light" || pref == "dark") {
            _themePreference.value = pref
            viewModelScope.launch {
                try {
                    themeFile.writeText(pref)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentDepo = MutableStateFlow<String?>("Depo 1")
    val currentDepo: StateFlow<String?> = _currentDepo.asStateFlow()

    fun setCurrentDepo(depo: String?) {
        _currentDepo.value = depo
    }

    private fun mergeContainersByTimestamp(
        local: List<ContainerCard>,
        remote: List<ContainerCard>
    ): List<ContainerCard> {
        val map = LinkedHashMap<String, ContainerCard>()
        local.forEach { c ->
            if (c.id.isNotBlank() && c.id !in deletedIds) {
                map[c.id] = repository.healContainerCard(c)
            }
        }
        remote.forEach { remRaw ->
            if (remRaw.id.isNotBlank() && remRaw.id !in deletedIds) {
                val rem = repository.healContainerCard(remRaw)
                val ex = map[rem.id]
                val remMod = if (rem.lastModified > 0L) rem.lastModified else rem.timestamp
                val exMod = if (ex != null) (if (ex.lastModified > 0L) ex.lastModified else ex.timestamp) else 0L
                if (ex == null || remMod >= exMod) {
                    map[rem.id] = rem
                }
            }
        }
        return map.values.toList()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val containers: StateFlow<List<ContainerCard>> = kotlinx.coroutines.flow.combine(
        repository.getSeznam(""),
        _localContainers,
        _searchQuery,
        _currentDepo
    ) { remoteList, localList, query, depo ->
        val merged = mergeContainersByTimestamp(localList, remoteList).filter { it.id !in deletedIds }
        val depoFiltered = if (depo == null) {
            merged
        } else {
            val isDepo2 = depo.equals("Depo 2", ignoreCase = true) || depo.equals("DEPO 02", ignoreCase = true)
            merged.filter { c ->
                val cIsDepo2 = c.depo.equals("Depo 2", ignoreCase = true) || c.depo.equals("DEPO 02", ignoreCase = true) || c.status.equals("DEPO 02", ignoreCase = true)
                cIsDepo2 == isDepo2
            }
        }
        val queryFiltered = if (query.isBlank()) {
            depoFiltered
        } else {
            depoFiltered.filter {
                it.designation.contains(query, ignoreCase = true) ||
                    it.type.contains(query, ignoreCase = true) ||
                    it.color.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.poznamka.contains(query, ignoreCase = true) ||
                    it.vlastnik.contains(query, ignoreCase = true) ||
                    it.customerCompany.contains(query, ignoreCase = true) ||
                    it.transporter.contains(query, ignoreCase = true) ||
                    it.spz.contains(query, ignoreCase = true)
            }
        }
        queryFiltered.sortedWith(
            compareByDescending<ContainerCard> { it.effectiveSystemNumber() }
                .thenByDescending { it.timestamp }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun readSavedContainerSysNums(): Pair<Int, Int> {
        if (!containerSysNumsFile.exists()) return 0 to 0
        return try {
            val obj = org.json.JSONObject(containerSysNumsFile.readText())
            obj.optInt("depo1", 0) to obj.optInt("depo2", 0)
        } catch (_: Throwable) {
            0 to 0
        }
    }

    private fun writeSavedContainerSysNums(depo1Max: Int, depo2Max: Int) {
        try {
            val obj = org.json.JSONObject().apply {
                put("depo1", depo1Max)
                put("depo2", depo2Max)
            }
            containerSysNumsFile.writeText(obj.toString())
        } catch (_: Throwable) {}
    }

    private fun syncMaxContainerSystemNumbers(allConts: List<ContainerCard>) {
        val (savedD1, savedD2) = readSavedContainerSysNums()
        val listD1 = allConts.filter { it.depo != "Depo 2" && it.status != "DEPO 02" }.maxOfOrNull { it.systemNumber } ?: 0
        val listD2 = allConts.filter { it.depo == "Depo 2" || it.status == "DEPO 02" }.maxOfOrNull { it.systemNumber } ?: 0
        val newD1 = maxOf(savedD1, listD1)
        val newD2 = maxOf(savedD2, listD2)
        if (newD1 != savedD1 || newD2 != savedD2) {
            writeSavedContainerSysNums(newD1, newD2)
        }
    }

    fun getNextContainerSystemNumber(depo: String): Int {
        val (savedD1, savedD2) = readSavedContainerSysNums()
        val isD2 = depo == "Depo 2" || depo == "DEPO 02"
        val currentListMax = (containers.value + _localContainers.value)
            .filter { (it.depo == "Depo 2" || it.status == "DEPO 02") == isD2 }
            .maxOfOrNull { it.systemNumber } ?: 0
        return if (isD2) {
            val next = maxOf(savedD2, currentListMax) + 1
            writeSavedContainerSysNums(savedD1, next)
            next
        } else {
            val next = maxOf(savedD1, currentListMax) + 1
            writeSavedContainerSysNums(next, savedD2)
            next
        }
    }

    val archive: StateFlow<List<ContainerCard>> = kotlinx.coroutines.flow.combine(
        repository.getArchiv(),
        _localContainers,
        _currentDepo
    ) { list, localList, depo ->
        val localArchiv = localList.filter { it.status == "ARCHIV" }
        val merged = mergeContainersByTimestamp(localArchiv, list).filter { it.id !in deletedIds && it.status == "ARCHIV" }
        val filtered = if (depo == null) merged else merged.filter { it.depo == depo }
        filtered.sortedWith(
            compareByDescending<ContainerCard> { it.effectiveSystemNumber() }
                .thenByDescending { it.timestamp }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pronajem: StateFlow<List<ContainerCard>> = kotlinx.coroutines.flow.combine(
        repository.getPronajem(),
        _localContainers,
        _currentDepo
    ) { list, localList, depo ->
        val localPronajem = localList.filter { it.status == "PRONÁJEM" || it.status == "PRONAJATO" || it.status == "PRONAJ." || it.status == "PRONÁJ" }
        val merged = mergeContainersByTimestamp(localPronajem, list).filter { it.id !in deletedIds }
        val filtered = if (depo == null) merged else merged.filter { it.depo == depo }
        filtered.sortedWith(
            compareByDescending<ContainerCard> { it.effectiveSystemNumber() }
                .thenByDescending { it.timestamp }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val localInventoriesFile: File
        get() = File(filesDir, "inventories_sync.json")

    suspend fun analyzeContainerBitmap(
        bitmap: android.graphics.Bitmap,
        photoHint: String? = null
    ): ContainerAnalysisResult {
        return try {
            val resultJson = analyzeContainer(bitmap, photoHint)
            val cleanJson = if (resultJson.startsWith("Chybí") || resultJson.startsWith("Chyba")) {
                "{}"
            } else {
                resultJson.trim().removePrefix("```json").removeSuffix("```").trim()
            }
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val adapter: JsonAdapter<ContainerAnalysisResult> = moshi.adapter(ContainerAnalysisResult::class.java)
            val parsed = adapter.fromJson(cleanJson) ?: ContainerAnalysisResult()
            val normalizedColor = com.example.api.normalizeContainerColorAdjective(parsed.color)
            val shortDesc = com.example.api.formatContainerShortDescription(normalizedColor, parsed.type, parsed.description)
            val normalizedDesig = com.example.api.normalizeContainerDesignation(parsed.designation, photoHint)
            parsed.copy(
                designation = normalizedDesig,
                color = normalizedColor,
                description = shortDesc
            )
        } catch (_: Exception) {
            ContainerAnalysisResult()
        }
    }

    private val _localInventories = MutableStateFlow<List<InventoryRecord>>(loadLocalInventories())

    private fun loadLocalInventories(): List<InventoryRecord> {
        return try {
            if (!localInventoriesFile.exists()) return emptyList()
            val arr = org.json.JSONArray(localInventoriesFile.readText())
            val list = mutableListOf<InventoryRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    InventoryRecord(
                        id = o.optString("id", "inv_${System.currentTimeMillis()}_$i"),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                        dateText = o.optString("dateText", ""),
                        author = o.optString("author", ""),
                        containerDesignations = o.optString("containerDesignations", ""),
                        depo = o.optString("depo", "Depo 1").ifBlank { "Depo 1" },
                        status = o.optString("status", "Nové").ifBlank { "Nové" },
                        summaryNote = o.optString("summaryNote", ""),
                        sessionDataJson = o.optString("sessionDataJson", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveLocalInventories(list: List<InventoryRecord>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { inv ->
                val o = org.json.JSONObject().apply {
                    put("id", inv.id)
                    put("timestamp", inv.timestamp)
                    put("dateText", inv.dateText)
                    put("author", inv.author)
                    put("containerDesignations", inv.containerDesignations)
                    put("depo", inv.depo)
                    put("status", inv.status.ifBlank { "Nové" })
                    put("summaryNote", inv.summaryNote)
                    put("sessionDataJson", inv.sessionDataJson)
                }
                arr.put(o)
            }
            localInventoriesFile.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val inventories: StateFlow<List<InventoryRecord>> = kotlinx.coroutines.flow.combine(
        _currentDepo.flatMapLatest { depo ->
            if (depo == null) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getInventoriesByDepo(depo)
        },
        _localInventories,
        _currentDepo
    ) { remoteList, localList, depo ->
        val map = LinkedHashMap<String, InventoryRecord>()
        localList
            .filter { it.id !in deletedIds && (depo == null || it.depo == depo) }
            .forEach { map[it.id] = it }
        remoteList
            .filter { it.id !in deletedIds && (depo == null || it.depo == depo) }
            .forEach { rem ->
                val ex = map[rem.id]
                if (ex == null || rem.timestamp >= ex.timestamp) {
                    map[rem.id] = rem
                }
            }
        map.values.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()
    
    private val _pendingScannedResult = MutableStateFlow<ContainerCard?>(null)
    val pendingScannedResult: StateFlow<ContainerCard?> = _pendingScannedResult.asStateFlow()
    
    private val _duplicationWarning = MutableStateFlow<Boolean>(false)
    val duplicationWarning: StateFlow<Boolean> = _duplicationWarning.asStateFlow()

    private val _isAnalyzingSearch = MutableStateFlow(false)
    val isAnalyzingSearch: StateFlow<Boolean> = _isAnalyzingSearch.asStateFlow()

    fun analyzeForSearch(photoFile: File) {
        if (_isAnalyzingSearch.value) return
        viewModelScope.launch {
            _isAnalyzingSearch.value = true
            try {
                val options = android.graphics.BitmapFactory.Options()
                options.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, options)
                options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 512)
                options.inJustDecodeBounds = false
                val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, options)
                
                if (bitmap != null) {
                    val resultJson = analyzeContainer(bitmap)
                    if (!resultJson.startsWith("Chybí") && !resultJson.startsWith("Chyba")) {
                        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                        val adapter = moshi.adapter(ContainerAnalysisResult::class.java)
                        val cleanJson = resultJson.trim().removePrefix("```json").removeSuffix("```").trim()
                        val res = adapter.fromJson(cleanJson)
                        if (res != null && res.designation.isNotBlank()) {
                            _searchQuery.value = res.designation
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAnalyzingSearch.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearPendingResult() {
        _pendingScannedResult.value = null
        _duplicationWarning.value = false
    }
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val events: StateFlow<List<EventRecord>> = _currentDepo.flatMapLatest { depo ->
        if (depo == null) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getEventsByDepo(depo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transports: StateFlow<List<TransportRecord>> = kotlinx.coroutines.flow.combine(
        _currentDepo.flatMapLatest { depo ->
            if (depo == null) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getTransportsByDepo(depo)
        },
        _localTransports,
        _currentDepo
    ) { remoteList, localList, depo ->
        val map = LinkedHashMap<String, TransportRecord>()
        localList
            .filter { it.id !in deletedIds && (depo == null || it.depo == depo) }
            .forEach { map[it.id] = it }
        remoteList
            .filter { it.id !in deletedIds && (depo == null || it.depo == depo) }
            .forEach { rem ->
                val ex = map[rem.id]
                if (ex == null || rem.timestamp >= ex.timestamp) {
                    map[rem.id] = rem
                }
            }
        map.values.sortedWith(
            compareBy<TransportRecord> {
                val m = com.example.data.parseTransportDateToMillis(it.dateText, 0L)
                if (m > 0L) m else Long.MAX_VALUE
            }.thenBy { it.timestamp }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveTransport(transport: TransportRecord) {
        val now = System.currentTimeMillis()
        val finalId = transport.id.ifBlank { "tr_$now" }
        val resolvedCompany = transport.company.ifBlank { transport.customerCompany }
        val resolvedCustomerCompany = transport.customerCompany.ifBlank { transport.company }
        val resolvedInitials = transport.customerInitials.ifBlank {
            com.example.data.computeCustomerInitials(transport.customerName, resolvedCustomerCompany)
        }
        val finalTransport = transport.copy(
            id = finalId,
            timestamp = now,
            depo = if (transport.id.isEmpty()) (_currentDepo.value ?: "Depo 1") else transport.depo.ifBlank { _currentDepo.value ?: "Depo 1" },
            company = resolvedCompany,
            customerCompany = resolvedCustomerCompany,
            customerInitials = resolvedInitials
        )
        val updatedLocal = _localTransports.value.filter { it.id != finalId } + finalTransport
        _localTransports.value = updatedLocal.sortedWith(
            compareBy<TransportRecord> {
                val m = com.example.data.parseTransportDateToMillis(it.dateText, 0L)
                if (m > 0L) m else Long.MAX_VALUE
            }.thenBy { it.timestamp }
        )
        saveLocalTransports(_localTransports.value)
        viewModelScope.launch {
            try {
                if (transport.id.isEmpty()) {
                    repository.insertTransport(finalTransport)
                } else {
                    repository.updateTransport(finalTransport)
                }
            } catch (_: Exception) {}
            scheduleBackgroundGitHubDataSync()
        }
    }

    fun deleteTransport(id: String) {
        markIdDeleted(id)
        _localTransports.value = _localTransports.value.filter { it.id != id }
        saveLocalTransports(_localTransports.value)
        viewModelScope.launch {
            try { repository.deleteTransportById(id) } catch (_: Exception) {}
            scheduleBackgroundGitHubDataSync()
        }
    }

    fun logEvent(description: String) {
        viewModelScope.launch {
            repository.insertEvent(EventRecord(description = description, author = _userName.value, depo = _currentDepo.value ?: "Depo 1"))
        }
    }

    fun confirmAndSavePending() {
        val pending = _pendingScannedResult.value ?: return
        val now = System.currentTimeMillis()
        val finalId = pending.id.ifBlank { UUID.randomUUID().toString() }
        val sysNum = if (pending.systemNumber > 0) pending.systemNumber else getNextContainerSystemNumber(pending.depo)
        val updatedPending = pending.copy(id = finalId, lastModified = now, systemNumber = sysNum)
        _localContainers.value = (_localContainers.value.filter { it.id != finalId } + updatedPending)
        saveLocalContainers(_localContainers.value)
        viewModelScope.launch {
            repository.insert(updatedPending)
            logEvent("Nový kontejner založen (#$sysNum): ${updatedPending.designation}")
            clearPendingResult()
        }
    }

    fun normalizeContainerDesignation(raw: String): String =
        raw.uppercase().replace(Regex("[^A-Z0-9]"), "")

    fun findDuplicateContainer(designation: String, excludeId: String? = null): ContainerCard? {
        val norm = normalizeContainerDesignation(designation)
        if (norm.isBlank()) return null
        return _localContainers.value.firstOrNull { c ->
            (excludeId == null || c.id != excludeId) &&
                normalizeContainerDesignation(c.designation) == norm
        }
    }

    fun findDuplicateTransportForContainer(designation: String, excludeTransportId: String? = null): TransportRecord? {
        val norm = normalizeContainerDesignation(designation)
        if (norm.isBlank()) return null
        return transports.value.firstOrNull { tr ->
            (excludeTransportId == null || tr.id != excludeTransportId) &&
                !tr.status.equals("ARCHIV", ignoreCase = true) &&
                !tr.status.equals("REALIZOVÁNO", ignoreCase = true) &&
                (
                    normalizeContainerDesignation(tr.designation) == norm ||
                        tr.designation.split(",", ";", "/").any { normalizeContainerDesignation(it) == norm } ||
                        tr.containersJson.uppercase().replace(Regex("[^A-Z0-9\"{}]"), "").contains("\"$norm\"")
                )
        }
    }

    fun saveContainer(container: ContainerCard) {
        val now = System.currentTimeMillis()
        if (container.transporter.isNotBlank()) {
            addPrepravce(container.transporter)
        }
        val duplicate = findDuplicateContainer(container.designation, excludeId = container.id.ifBlank { null })
        if (container.id.isEmpty() && duplicate != null) {
            val merged = duplicate.copy(
                type = container.type.ifBlank { duplicate.type },
                status = container.status.ifBlank { duplicate.status },
                depo = container.depo.ifBlank { duplicate.depo },
                vlastnik = container.vlastnik.ifBlank { duplicate.vlastnik },
                price = if (container.price > 0L) container.price else duplicate.price,
                poznamka = container.poznamka.ifBlank { duplicate.poznamka },
                description = container.description.ifBlank { duplicate.description },
                color = container.color.ifBlank { duplicate.color },
                spz = container.spz.ifBlank { duplicate.spz },
                imageUri = container.imageUri ?: duplicate.imageUri,
                extraImages = container.extraImages.ifBlank { duplicate.extraImages },
                lastModified = now
            )
            _localContainers.value = (_localContainers.value.filter { it.id != merged.id } + merged)
            saveLocalContainers(_localContainers.value)
            viewModelScope.launch {
                repository.update(merged)
                logEvent("Kontejner aktualizován (zabráněno duplicitě): ${merged.designation}")
            }
            return
        }
        if (container.id.isEmpty()) {
            val finalId = UUID.randomUUID().toString()
            val sysNum = if (container.systemNumber > 0) container.systemNumber else getNextContainerSystemNumber(container.depo)
            val ts = if (container.timestamp > 0) container.timestamp else now
            val updatedContainer = container.copy(
                id = finalId,
                timestamp = ts,
                arrivalDate = container.arrivalDate ?: ts,
                lastModified = now,
                systemNumber = sysNum
            )
            _localContainers.value = (_localContainers.value.filter { it.id != finalId } + updatedContainer)
            saveLocalContainers(_localContainers.value)
            viewModelScope.launch {
                repository.insert(updatedContainer)
                logEvent("Kontejner vložen ze zadání (#$sysNum): ${updatedContainer.designation}")
                scheduleBackgroundGitHubDataSync()
            }
        } else {
            val updatedContainer = container.copy(lastModified = now)
            _localContainers.value = (_localContainers.value.filter { it.id != updatedContainer.id } + updatedContainer)
            saveLocalContainers(_localContainers.value)
            viewModelScope.launch {
                repository.update(updatedContainer)
                logEvent("Kontejner upraven: ${updatedContainer.designation}")
                scheduleBackgroundGitHubDataSync()
            }
        }
    }

    suspend fun getContainerById(id: String): ContainerCard? {
        return repository.getContainerById(id) ?: _localContainers.value.find { it.id == id }
    }

    suspend fun getContainersByDesignations(designationsStr: String): List<ContainerCard> {
        if (designationsStr.isBlank()) return emptyList()
        val tags = designationsStr.split(",")
        val results = mutableListOf<ContainerCard>()
        for (tag in tags) {
            val c = repository.findByDesignation(tag.trim())
                ?: _localContainers.value.find { it.designation.equals(tag.trim(), ignoreCase = true) }
            if (c != null) results.add(c)
        }
        return results
    }

    fun updateStatus(card: ContainerCard, newStatus: String) {
        val updated = card.copy(status = newStatus, lastModified = System.currentTimeMillis())
        _localContainers.value = (_localContainers.value.filter { it.id != card.id } + updated)
        saveLocalContainers(_localContainers.value)
        viewModelScope.launch {
            repository.update(updated)
            logEvent("Změna stavu kontejneru ${card.designation} na $newStatus")
        }
    }

    fun deleteContainer(card: ContainerCard) {
        if (card.id.isNotBlank()) {
            markIdDeleted(card.id)
            _localContainers.value = _localContainers.value.filter { it.id != card.id }
            saveLocalContainers(_localContainers.value)
        }
        viewModelScope.launch {
            repository.delete(card)
            logEvent("Smazán kontejner: ${card.designation}")
            card.imageUri?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            scheduleBackgroundGitHubDataSync()
        }
    }

    fun deleteAllContainersInDepo(depo: String) {
        val toRemove = _localContainers.value.filter { it.depo == depo }
        toRemove.forEach { if (it.id.isNotBlank()) markIdDeleted(it.id) }
        _localContainers.value = _localContainers.value.filter { it.depo != depo }
        saveLocalContainers(_localContainers.value)
        viewModelScope.launch {
            repository.deleteAllContainersInDepo(depo)
            logEvent("Hromadně smazány všechny kontejnery v depu: $depo")
        }
    }

    suspend fun updateInventory(inventory: InventoryRecord) {
        val finalId = inventory.id.ifBlank { "inv_${System.currentTimeMillis()}" }
        val finalInv = inventory.copy(id = finalId)
        val updatedLocal = _localInventories.value.filter { it.id != finalId } + finalInv
        _localInventories.value = updatedLocal.sortedByDescending { it.timestamp }
        saveLocalInventories(_localInventories.value)
        try {
            repository.updateInventory(finalInv)
        } catch (_: Exception) {}
    }

    suspend fun deleteInventory(inventory: InventoryRecord) {
        if (inventory.id.isNotBlank()) {
            deletedIds.add(inventory.id)
            saveDeletedIds()
        }
        _localInventories.value = _localInventories.value.filter { it.id != inventory.id }
        saveLocalInventories(_localInventories.value)
        try {
            repository.deleteInventory(inventory)
        } catch (_: Exception) {}
    }

    suspend fun saveNewInventory(inventory: InventoryRecord) {
        val finalId = inventory.id.ifBlank { "inv_${System.currentTimeMillis()}" }
        val finalInv = inventory.copy(id = finalId)
        val updatedLocal = _localInventories.value.filter { it.id != finalId } + finalInv
        _localInventories.value = updatedLocal.sortedByDescending { it.timestamp }
        saveLocalInventories(_localInventories.value)
        try {
            repository.insertInventory(finalInv)
        } catch (_: Exception) {}
    }

    suspend fun createInventory(author: String) {
        val depo = _currentDepo.value ?: "Depo 1"
        val activeContainers = repository.getActiveContainersSnapshot().let { list ->
            if (_currentDepo.value != null) list.filter { it.depo == _currentDepo.value } else list
        }
        val designations = activeContainers.joinToString(",") { it.designation }
        val dateText = java.text.SimpleDateFormat("dd. MM. yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val inventory = InventoryRecord(
            id = "inv_${System.currentTimeMillis()}",
            dateText = dateText,
            author = author,
            containerDesignations = designations,
            depo = depo
        )
        saveNewInventory(inventory)
    }

    fun analyzeAndSave(photoFile: File, photoTimestampOverride: Long? = null) {
        if (_isAnalyzing.value) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analysisError.value = null
            try {
                // 1. Extract photo capture timestamp before any modification
                val photoTimeMs = photoTimestampOverride ?: extractPhotoTimestamp(context, null, photoFile)

                // 2. Copy and apply EXIF rotation first so AI and crop work on upright photo
                val tempUprightFile = File(filesDir, "upright_${UUID.randomUUID()}.jpg")
                photoFile.copyTo(tempUprightFile, overwrite = true)
                try {
                    val exif = android.media.ExifInterface(photoFile.absolutePath)
                    val orient = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    val deg = when (orient) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (deg != 0f) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath)
                        if (bmp != null) {
                            val mat = android.graphics.Matrix().apply { postRotate(deg) }
                            val rotated = android.graphics.Bitmap.createBitmap(
                                bmp, 0, 0, bmp.width, bmp.height, mat, true
                            )
                            java.io.FileOutputStream(tempUprightFile).use { out ->
                                rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 3. Decode downsampled upright bitmap for Gemini API
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath, options)
                options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 1024)
                options.inJustDecodeBounds = false
                val bitmap = android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath, options)

                if (bitmap == null) {
                    tempUprightFile.delete()
                    _analysisError.value = "Chyba při zpracování obrázku z fotoaparátu."
                    return@launch
                }

                val resultJson = try {
                    analyzeContainer(bitmap)
                } catch (_: Exception) {
                    "{}"
                }
                Log.d("Gemini_Response", resultJson)

                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter: JsonAdapter<ContainerAnalysisResult> = moshi.adapter(ContainerAnalysisResult::class.java)

                val cleanJson = if (resultJson.startsWith("Chybí") || resultJson.startsWith("Chyba")) {
                    "{}"
                } else {
                    resultJson.trim().removePrefix("```json").removeSuffix("```").trim()
                }

                val res = try {
                    adapter.fromJson(cleanJson) ?: ContainerAnalysisResult()
                } catch (_: Exception) {
                    ContainerAnalysisResult()
                }

                // 4. Crop upright image so the whole container + small margin around it is visible
                val fullOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath, fullOptions)
                fullOptions.inSampleSize = maxOf(1, maxOf(fullOptions.outWidth, fullOptions.outHeight) / 1920)
                fullOptions.inJustDecodeBounds = false
                val fullBmp = android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath, fullOptions) ?: bitmap
                val (croppedBmp, biasPair) = cropContainerBitmapWithSmallMargin(
                    fullBmp,
                    res.containerBox,
                    res.designationBox
                )
                val filename = "${UUID.randomUUID()}_bx${biasPair.first}_by${biasPair.second}.jpg"
                val finalFile = File(filesDir, filename)
                java.io.FileOutputStream(finalFile).use { out ->
                    croppedBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                }
                tempUprightFile.delete()

                val uriString = finalFile.absolutePath

                val mappedType = when (res.type.trim().uppercase()) {
                    "45G1", "40HC", "40 HC" -> "40 HC"
                    "22G1", "20DC", "20DV", "20 DV" -> "20 DV"
                    "42G1", "40DC", "40DV", "40 DV" -> "40 DV"
                    "OBYTNÝ", "OBYTNY", "OB" -> "OB"
                    "SKLADOVÝ", "SKLADOVY", "SK" -> "SK"
                    else -> res.type
                }

                val depo = _currentDepo.value ?: "Depo 1"
                val defaultStatus = if (depo == "Depo 1") "DEPO 01" else "DEPO 02"
                val sysNum = getNextContainerSystemNumber(depo)
                val normalizedColor = com.example.api.normalizeContainerColorAdjective(res.color)
                val shortDescription = com.example.api.formatContainerShortDescription(normalizedColor, mappedType, res.description)
                val normalizedDesig = com.example.api.normalizeContainerDesignation(res.designation, photoFile.name)
                val card = ContainerCard(
                    designation = normalizedDesig,
                    type = mappedType,
                    color = normalizedColor,
                    description = shortDescription,
                    imageUri = uriString,
                    extraImages = uriString,
                    timestamp = photoTimeMs,
                    arrivalDate = photoTimeMs,
                    lastModified = System.currentTimeMillis(),
                    spz = res.spz ?: "",
                    depo = depo,
                    status = defaultStatus,
                    systemNumber = sysNum
                )

                val existing = findDuplicateContainer(normalizedDesig) ?: repository.findByDesignation(normalizedDesig)
                if (existing != null) {
                    _pendingScannedResult.value = card
                    _duplicationWarning.value = true
                } else {
                    _localContainers.value = _localContainers.value + card
                    saveLocalContainers(_localContainers.value)
                    repository.insert(card)
                    logEvent("Kontejner přidán fotkou: ${card.designation}")
                }
            } catch (e: Throwable) {
                _analysisError.value = "Nepodařilo se zpracovat: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun analyzePhotoForDraftContainer(
        photoFile: File,
        photoTimestampOverride: Long? = null,
        onResult: (ContainerAnalysisResult, String, Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1. Extract photo capture timestamp before any modification
                val photoTimeMs = photoTimestampOverride ?: extractPhotoTimestamp(context, null, photoFile)

                // 2. Copy and apply EXIF rotation first
                val tempUprightFile = File(filesDir, "draft_upright_${UUID.randomUUID()}.jpg")
                photoFile.copyTo(tempUprightFile, overwrite = true)
                try {
                    val exif = android.media.ExifInterface(photoFile.absolutePath)
                    val orient = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    val deg = when (orient) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (deg != 0f) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath)
                        if (bmp != null) {
                            val mat = android.graphics.Matrix().apply { postRotate(deg) }
                            val rotated = android.graphics.Bitmap.createBitmap(
                                bmp, 0, 0, bmp.width, bmp.height, mat, true
                            )
                            java.io.FileOutputStream(tempUprightFile).use { out ->
                                rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                            }
                        }
                    }
                } catch (_: Exception) {}

                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath, options)
                options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 1024)
                options.inJustDecodeBounds = false
                val bitmap = android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath, options)

                if (bitmap == null) {
                    tempUprightFile.delete()
                    onError("Chyba při čtení obrázku.")
                    return@launch
                }

                val resultJson = try {
                    analyzeContainer(bitmap, photoFile.nameWithoutExtension)
                } catch (_: Exception) {
                    "{}"
                }

                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter: JsonAdapter<ContainerAnalysisResult> = moshi.adapter(ContainerAnalysisResult::class.java)
                val cleanJson = if (resultJson.startsWith("Chybí") || resultJson.startsWith("Chyba")) {
                    "{}"
                } else {
                    resultJson.trim().removePrefix("```json").removeSuffix("```").trim()
                }

                val rawRes = try {
                    adapter.fromJson(cleanJson) ?: ContainerAnalysisResult()
                } catch (_: Exception) {
                    ContainerAnalysisResult()
                }
                val normColor = com.example.api.normalizeContainerColorAdjective(rawRes.color)
                val res = rawRes.copy(
                    color = normColor,
                    description = com.example.api.formatContainerShortDescription(normColor, rawRes.type, rawRes.description)
                )

                // 3. Crop upright image so the whole container + small margin around it is visible
                val fullOptions = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath, fullOptions)
                fullOptions.inSampleSize = maxOf(1, maxOf(fullOptions.outWidth, fullOptions.outHeight) / 1920)
                fullOptions.inJustDecodeBounds = false
                val fullBmp = android.graphics.BitmapFactory.decodeFile(tempUprightFile.absolutePath, fullOptions) ?: bitmap
                val (croppedBmp, biasPair) = cropContainerBitmapWithSmallMargin(
                    fullBmp,
                    res.containerBox,
                    res.designationBox
                )
                val filename = "${UUID.randomUUID()}_bx${biasPair.first}_by${biasPair.second}.jpg"
                val destFile = File(filesDir, filename)
                java.io.FileOutputStream(destFile).use { out ->
                    croppedBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                }
                tempUprightFile.delete()

                val mappedType = when (res.type.trim().uppercase()) {
                    "45G1", "40HC", "40 HC" -> "40 HC"
                    "22G1", "20DC", "20DV", "20 DV" -> "20 DV"
                    "42G1", "40DC", "40DV", "40 DV" -> "40 DV"
                    "OBYTNÝ", "OBYTNY", "OB" -> "OB"
                    "SKLADOVÝ", "SKLADOVY", "SK" -> "SK"
                    else -> res.type.ifBlank { "20 DV" }
                }

                val normalizedDesig = com.example.api.normalizeContainerDesignation(res.designation, photoFile.name)
                onResult(res.copy(type = mappedType, designation = normalizedDesig), destFile.absolutePath, photoTimeMs)
            } catch (e: Throwable) {
                onError("Nepodařilo se načíst údaje z fotky: ${e.localizedMessage ?: "Chyba"}")
            }
        }
    }

    fun analyzeProtocolForContainer(photoFile: File, onResult: (ProtocolAnalysisResult) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val options = android.graphics.BitmapFactory.Options()
                options.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, options)
                options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 1024)
                options.inJustDecodeBounds = false
                val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, options)
                
                if (bitmap == null) {
                    onError("Chyba při zpracování obrázku z fotoaparátu.")
                    return@launch
                }
                
                val resultJson = com.example.api.analyzeProtocol(bitmap)
                if (resultJson.startsWith("Chybí") || resultJson.startsWith("Chyba")) {
                    onError(resultJson)
                    return@launch
                }
                
                val moshi = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                val adapter: com.squareup.moshi.JsonAdapter<ProtocolAnalysisResult> = moshi.adapter(ProtocolAnalysisResult::class.java)
                val cleanJson = resultJson.trim().removePrefix("```json").removeSuffix("```").trim()
                val res = adapter.fromJson(cleanJson)
                
                if (res != null) {
                    onResult(res)
                } else {
                    onError("Neočekávaný formát dat z AI.")
                }
            } catch (e: Throwable) {
                onError("Nepodařilo se zpracovat: ${e.localizedMessage}")
            }
        }
    }

    // Cloud version doesn't need local backup/restore functionality

    // Share server
    private var _shareServer: com.example.api.ShareServer? = null
    private val _shareServerUrl = MutableStateFlow<String?>(null)
    val shareServerUrl: StateFlow<String?> = _shareServerUrl.asStateFlow()

    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs.asStateFlow()

    fun startShareServer(context: android.content.Context) {
        if (_shareServer == null) {
            _shareServer = com.example.api.ShareServer(context.applicationContext, repository) { log ->
                addServerLog(log)
            }
        }
        val url = _shareServer?.start()
        _shareServerUrl.value = url
    }

    fun stopShareServer() {
        _shareServer?.stop()
        _shareServerUrl.value = null
    }

    fun isShareServerRunning(): Boolean {
        return _shareServer?.isRunning() ?: false
    }

    private fun updateSyncTimestamp() {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _lastSyncStatus.value = "Sync $timeStr"
    }

    private fun loadDeletedIds() {
        if (!deletedIdsFile.exists()) return
        try {
            deletedIds.addAll(deletedIdsFile.readLines().map { it.trim() }.filter { it.isNotEmpty() })
        } catch (_: Exception) {}
    }

    private fun saveDeletedIds() {
        viewModelScope.launch {
            try { deletedIdsFile.writeText(deletedIds.joinToString("\n")) } catch (_: Exception) {}
        }
    }

    private fun markIdDeleted(id: String) {
        if (id.isBlank()) return
        deletedIds.add(id)
        saveDeletedIds()
    }

    private fun mergeByTimestamp(
        local: List<com.example.data.CustomerRecord>,
        remote: List<com.example.data.CustomerRecord>
    ): List<com.example.data.CustomerRecord> {
        val map = LinkedHashMap<String, com.example.data.CustomerRecord>()
        local.forEach { if (it.id.isNotBlank() && it.id !in deletedIds) map[it.id] = it }
        remote.forEach { rem ->
            if (rem.id.isNotBlank() && rem.id !in deletedIds) {
                val ex = map[rem.id]
                if (ex == null || rem.timestamp >= ex.timestamp) {
                    map[rem.id] = if (ex != null) {
                        rem.copy(
                            ic = rem.ic.ifBlank { ex.ic },
                            dic = rem.dic.ifBlank { ex.dic },
                            ulice = rem.ulice.ifBlank { ex.ulice },
                            cisloZakaznika = rem.cisloZakaznika.ifBlank { ex.cisloZakaznika },
                            isOrder = rem.isOrder || ex.isOrder,
                            isAdresar = rem.isAdresar || ex.isAdresar
                        )
                    } else rem
                }
            }
        }
        return map.values.sortedByDescending { it.timestamp }
    }

    private fun mergeNabidkyByTimestamp(
        local: List<com.example.data.NabidkaRecord>,
        remote: List<com.example.data.NabidkaRecord>
    ): List<com.example.data.NabidkaRecord> {
        val map = LinkedHashMap<String, com.example.data.NabidkaRecord>()
        local.forEach { if (it.id.isNotBlank() && it.id !in deletedIds) map[it.id] = it }
        remote.forEach { rem ->
            if (rem.id.isNotBlank() && rem.id !in deletedIds) {
                val ex = map[rem.id]
                if (ex == null || rem.timestamp >= ex.timestamp) map[rem.id] = rem
            }
        }
        return map.values.sortedByDescending { it.timestamp }
    }

    private fun mergeZakazkyByTimestamp(
        local: List<com.example.data.ZakazkaRecord>,
        remote: List<com.example.data.ZakazkaRecord>
    ): List<com.example.data.ZakazkaRecord> {
        val map = LinkedHashMap<String, com.example.data.ZakazkaRecord>()
        local.forEach { if (it.id.isNotBlank() && it.id !in deletedIds) map[it.id] = it }
        remote.forEach { rem ->
            if (rem.id.isNotBlank() && rem.id !in deletedIds) {
                val ex = map[rem.id]
                if (ex == null || rem.timestamp >= ex.timestamp) map[rem.id] = rem
            }
        }
        return map.values.sortedByDescending { it.timestamp }
    }

    private fun loadLocalAgendasCache() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        if (customersFile.exists()) {
            try {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.CustomerRecord::class.java)
                val list = moshi.adapter<List<com.example.data.CustomerRecord>>(type).fromJson(customersFile.readText())
                if (!list.isNullOrEmpty()) _customers.value = list.filter { it.id !in deletedIds }
            } catch (_: Exception) {}
        }
        val adresarImportV200Flag = File(filesDir, "adresar_imported_v200.flag")
        if (!adresarImportV200Flag.exists() || _customers.value.count { it.isAdresar } < 1000) {
            try { adresarImportV200Flag.writeText("imported_v200") } catch (_: Exception) {}
            loadInitialAdresarFromAssets(force = false)
        }
        if (nabidkyFile.exists()) {
            try {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.NabidkaRecord::class.java)
                val list = moshi.adapter<List<com.example.data.NabidkaRecord>>(type).fromJson(nabidkyFile.readText())
                if (!list.isNullOrEmpty()) _nabidky.value = list.filter { it.id !in deletedIds }
            } catch (_: Exception) {}
        }
        if (zakazkyFile.exists()) {
            try {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.ZakazkaRecord::class.java)
                val list = moshi.adapter<List<com.example.data.ZakazkaRecord>>(type).fromJson(zakazkyFile.readText())
                if (!list.isNullOrEmpty()) _zakazky.value = list.filter { it.id !in deletedIds }
            } catch (_: Exception) {}
        }
        if (fakturyFile.exists()) {
            try {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.FakturaRecord::class.java)
                val list = moshi.adapter<List<com.example.data.FakturaRecord>>(type).fromJson(fakturyFile.readText())
                if (!list.isNullOrEmpty()) _faktury.value = list.filter { it.id !in deletedIds }
            } catch (_: Exception) {}
        }
        if (zalohoveFakturyFile.exists()) {
            try {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.FakturaRecord::class.java)
                val list = moshi.adapter<List<com.example.data.FakturaRecord>>(type).fromJson(zalohoveFakturyFile.readText())
                if (!list.isNullOrEmpty()) _zalohoveFaktury.value = list.filter { it.id !in deletedIds }
            } catch (_: Exception) {}
        }
        if (zajemciFile.exists()) {
            try {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.ZajemceRecord::class.java)
                val list = moshi.adapter<List<com.example.data.ZajemceRecord>>(type).fromJson(zajemciFile.readText())
                if (!list.isNullOrEmpty()) _zajemci.value = list.filter { it.id !in deletedIds }
            } catch (_: Exception) {}
        }
        if (transportsFile.exists()) {
            try {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, TransportRecord::class.java)
                val list = moshi.adapter<List<TransportRecord>>(type).fromJson(transportsFile.readText())
                if (!list.isNullOrEmpty()) _localTransports.value = list.filter { it.id !in deletedIds }
            } catch (_: Exception) {}
        }
        if (containersFile.exists()) {
            try {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ContainerCard::class.java)
                val list = moshi.adapter<List<ContainerCard>>(type).fromJson(containersFile.readText())
                if (!list.isNullOrEmpty()) {
                    val healed = list.filter { it.id !in deletedIds }.map { repository.healContainerCard(it) }
                    _localContainers.value = healed
                    syncMaxContainerSystemNumbers(healed)
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveLocalZajemci(list: List<com.example.data.ZajemceRecord>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.ZajemceRecord::class.java)
                zajemciFile.writeText(moshi.adapter<List<com.example.data.ZajemceRecord>>(type).toJson(list))
            } catch (_: Throwable) {}
        }
    }

    private fun mergeZajemciByTimestamp(
        local: List<com.example.data.ZajemceRecord>,
        remote: List<com.example.data.ZajemceRecord>
    ): List<com.example.data.ZajemceRecord> {
        val map = LinkedHashMap<String, com.example.data.ZajemceRecord>()
        local.forEach { if (it.id.isNotBlank() && it.id !in deletedIds) map[it.id] = it }
        remote.forEach { rem ->
            if (rem.id.isNotBlank() && rem.id !in deletedIds) {
                val ex = map[rem.id]
                if (ex == null || rem.timestamp >= ex.timestamp) map[rem.id] = rem
            }
        }
        return map.values.sortedByDescending { it.timestamp }
    }

    private fun saveLocalTransports(list: List<TransportRecord>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, TransportRecord::class.java)
                transportsFile.writeText(moshi.adapter<List<TransportRecord>>(type).toJson(list))
            } catch (_: Throwable) {}
        }
    }

    private fun saveLocalCustomers(list: List<com.example.data.CustomerRecord>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.CustomerRecord::class.java)
                customersFile.writeText(moshi.adapter<List<com.example.data.CustomerRecord>>(type).toJson(list))
            } catch (_: Throwable) {}
        }
    }

    fun loadInitialAdresarFromAssets(force: Boolean = false): Int {
        var count = 0
        try {
            val jsonText: String? = try {
                context?.assets?.open("initial_adresar.json")?.bufferedReader()?.use { it.readText() }
            } catch (_: Throwable) {
                null
            } ?: File(filesDir, "initial_adresar.json").takeIf { it.exists() }?.readText()

            if (!jsonText.isNullOrBlank()) {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.CustomerRecord::class.java)
                val adapter: JsonAdapter<List<com.example.data.CustomerRecord>> = moshi.adapter(type)
                val initialList = adapter.fromJson(jsonText)
                if (!initialList.isNullOrEmpty()) {
                    val currentMap = _customers.value.associateBy { it.id }.toMutableMap()
                    initialList.forEach { rec ->
                        if (rec.id !in deletedIds && (force || !currentMap.containsKey(rec.id))) {
                            currentMap[rec.id] = rec
                            count++
                        }
                    }
                    _customers.value = currentMap.values.sortedByDescending { it.timestamp }
                    saveLocalCustomers(_customers.value)
                }
            }
        } catch (_: Throwable) {}
        return count
    }

    fun reloadPohodaAdresar(): Int {
        return loadInitialAdresarFromAssets(force = true)
    }

    private fun saveLocalNabidky(list: List<com.example.data.NabidkaRecord>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.NabidkaRecord::class.java)
                nabidkyFile.writeText(moshi.adapter<List<com.example.data.NabidkaRecord>>(type).toJson(list))
            } catch (_: Throwable) {}
        }
    }

    private fun saveLocalZakazky(list: List<com.example.data.ZakazkaRecord>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.ZakazkaRecord::class.java)
                zakazkyFile.writeText(moshi.adapter<List<com.example.data.ZakazkaRecord>>(type).toJson(list))
            } catch (_: Throwable) {}
        }
    }

    private fun saveLocalContainers(list: List<ContainerCard>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ContainerCard::class.java)
                containersFile.writeText(moshi.adapter<List<ContainerCard>>(type).toJson(list))
            } catch (_: Throwable) {}
        }
    }

    private var bgGitHubSyncJob: kotlinx.coroutines.Job? = null

    fun scheduleBackgroundGitHubDataSync(delayMs: Long = 2500L) {
        val ctx = context ?: return
        bgGitHubSyncJob?.cancel()
        bgGitHubSyncJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(delayMs)
            try {
                com.example.data.GitHubBackupManager.syncDataOnlyWithGitHub(
                    context = ctx,
                    pullFirst = false,
                    pushAfter = true
                )
            } catch (_: Exception) {}
        }
    }

    fun syncAllAgendasNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                kotlinx.coroutines.withTimeoutOrNull(45000L) {
                    // 0. Nejprve stáhneme a sloučíme containers_sync.json z GitHubu (pokud je dostupný)
                    if (context != null) {
                        try {
                            val pulled = com.example.data.GitHubBackupManager.syncDataOnlyWithGitHub(
                                context = context,
                                pullFirst = true,
                                pushAfter = false
                            )
                            if (pulled) {
                                loadLocalCatalogItems()
                                loadLocalAgendasCache()
                                _localInventories.value = loadLocalInventories()
                            }
                        } catch (_: Exception) {}
                    }

                    val remCus = repository.getAllCustomersDirect().filter { it.id !in deletedIds }
                    val mergedCus = mergeByTimestamp(_customers.value, remCus)
                    _customers.value = mergedCus
                    saveLocalCustomers(mergedCus)
                    val remCusIds = remCus.associateBy { it.id }
                    mergedCus.filter { it.isOrder || !remCusIds.containsKey(it.id) }.take(50).forEach { loc ->
                        val rem = remCusIds[loc.id]
                        if (rem == null || loc.timestamp > rem.timestamp) {
                            try { repository.insertCustomer(loc) } catch (_: Exception) {}
                        }
                    }

                    val remNab = repository.getAllNabidkyDirect().filter { it.id !in deletedIds }
                    val mergedNab = mergeNabidkyByTimestamp(_nabidky.value, remNab)
                    _nabidky.value = mergedNab
                    saveLocalNabidky(mergedNab)
                    val remNabIds = remNab.associateBy { it.id }
                    mergedNab.forEach { loc ->
                        val rem = remNabIds[loc.id]
                        if (rem == null || loc.timestamp > rem.timestamp) {
                            try { repository.insertNabidka(loc, loc.text) } catch (_: Exception) {}
                        }
                    }

                    val remZak = repository.getAllZakazkyDirect().filter { it.id !in deletedIds }
                    val mergedZak = mergeZakazkyByTimestamp(_zakazky.value, remZak)
                    _zakazky.value = mergedZak
                    saveLocalZakazky(mergedZak)
                    val remZakIds = remZak.associateBy { it.id }
                    mergedZak.forEach { loc ->
                        val rem = remZakIds[loc.id]
                        if (rem == null || loc.timestamp > rem.timestamp) {
                            try { repository.insertZakazka(loc, loc.lokalita, loc.items) } catch (_: Exception) {}
                        }
                    }

                    val remFak = repository.getAllFakturyDirect().filter { it.id !in deletedIds }
                    val mergedFak = mergeFakturyByTimestamp(_faktury.value, remFak)
                    _faktury.value = mergedFak
                    saveLocalFaktury(mergedFak)
                    val remFakIds = remFak.associateBy { it.id }
                    mergedFak.forEach { loc ->
                        val rem = remFakIds[loc.id]
                        if (rem == null || loc.timestamp > rem.timestamp) {
                            try { repository.insertFaktura(loc) } catch (_: Exception) {}
                        }
                    }

                    val remZal = repository.getAllZalohoveFakturyDirect().filter { it.id !in deletedIds }
                    val mergedZal = mergeFakturyByTimestamp(_zalohoveFaktury.value, remZal)
                    _zalohoveFaktury.value = mergedZal
                    saveLocalZalohoveFaktury(mergedZal)
                    val remZalIds = remZal.associateBy { it.id }
                    mergedZal.forEach { loc ->
                        val rem = remZalIds[loc.id]
                        if (rem == null || loc.timestamp > rem.timestamp) {
                            try { repository.insertFaktura(loc.copy(typDokladu = "ZALOHOVA_FAKTURA")) } catch (_: Exception) {}
                        }
                    }

                    val remZaj = repository.getAllZajemciDirect().filter { it.id !in deletedIds }
                    val mergedZaj = mergeZajemciByTimestamp(_zajemci.value, remZaj)
                    _zajemci.value = mergedZaj
                    saveLocalZajemci(mergedZaj)
                    val remZajIds = remZaj.associateBy { it.id }
                    mergedZaj.forEach { loc ->
                        val rem = remZajIds[loc.id]
                        if (rem == null || loc.timestamp > rem.timestamp) {
                            try { repository.insertZajemce(loc) } catch (_: Exception) {}
                        }
                    }

                    val remTr = repository.getAllTransportsDirect().filter { it.id !in deletedIds }
                    val trMap = LinkedHashMap<String, TransportRecord>()
                    _localTransports.value.filter { it.id.isNotBlank() && it.id !in deletedIds }.forEach { trMap[it.id] = it }
                    remTr.forEach { rem ->
                        val ex = trMap[rem.id]
                        if (ex == null || rem.timestamp >= ex.timestamp) trMap[rem.id] = rem
                    }
                    val mergedTr = trMap.values.toList()
                    _localTransports.value = mergedTr
                    saveLocalTransports(mergedTr)
                    val remTrIds = remTr.associateBy { it.id }
                    mergedTr.forEach { loc ->
                        val rem = remTrIds[loc.id]
                        if (rem == null || loc.timestamp > rem.timestamp) {
                            try { repository.updateTransport(loc) } catch (_: Exception) {}
                        }
                    }

                    val remCat = repository.getAllCatalogItemsDirect().filter { it.id !in deletedIds }
                    if (remCat.isNotEmpty()) {
                        val mergedCat = sortCatalogList(mergeCatalogWithDefaults(remCat))
                        _catalogItems.value = mergedCat
                        saveLocalCatalogItems(mergedCat)
                    }

                    val remConts = repository.getAllContainersDirect().filter { it.id !in deletedIds }
                    val mergedConts = mergeContainersByTimestamp(_localContainers.value, remConts)
                    _localContainers.value = mergedConts
                    syncMaxContainerSystemNumbers(mergedConts)
                    saveLocalContainers(mergedConts)
                    val remContIds = remConts.associateBy { it.id }
                    mergedConts.forEach { loc ->
                        val rem = remContIds[loc.id]
                        val locMod = if (loc.lastModified > 0L) loc.lastModified else loc.timestamp
                        val remMod = if (rem != null) (if (rem.lastModified > 0L) rem.lastModified else rem.timestamp) else 0L
                        if (rem == null || locMod > remMod) {
                            try { repository.update(loc) } catch (_: Exception) {}
                        }
                    }
                }

                syncContactsFromDocumentsToAdresar(_nabidky.value, _zakazky.value)
                updateSyncTimestamp()
                scheduleBackgroundGitHubDataSync(1500L)
            } catch (_: Exception) {
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun mergeUniqueFieldTokens(vararg values: String, separator: String = ", "): String {
        val result = mutableListOf<String>()
        values.forEach { raw ->
            raw.split(",", ";", "•")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "-" }
                .forEach { token ->
                    if (result.none { it.equals(token, ignoreCase = true) }) {
                        result.add(token)
                    }
                }
        }
        return result.joinToString(separator)
    }

    fun buildMergedCustomerRecord(records: List<com.example.data.CustomerRecord>): com.example.data.CustomerRecord {
        val sorted = records.sortedByDescending { it.timestamp }
        val primary = sorted.firstOrNull { !it.isOrder && it.isAdresar }
            ?: sorted.firstOrNull { it.cisloZakaznika.startsWith("CUS-") }
            ?: sorted.first()

        val bestCompany = sorted.map { it.company.trim() }
            .filter { it.isNotEmpty() }
            .maxByOrNull { it.length }
            ?: primary.company

        val uniqueNames = sorted.map { it.name.trim() }
            .filter { it.isNotEmpty() && !it.equals(bestCompany, ignoreCase = true) }
            .distinctBy { it.lowercase() }
        val mergedName = if (uniqueNames.isNotEmpty()) uniqueNames.joinToString(", ") else bestCompany

        val mergedPhone = mergeUniqueFieldTokens(*sorted.map { it.phone }.toTypedArray())
        val mergedEmail = mergeUniqueFieldTokens(*sorted.map { it.email }.toTypedArray())
        val mergedIc = sorted.map { com.example.data.normalizeCompanyIco(it.ic) }.firstOrNull { it.isNotEmpty() } ?: primary.ic
        val mergedDic = sorted.map { it.dic.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
        val mergedUlice = mergeUniqueFieldTokens(*sorted.map { it.ulice }.toTypedArray(), separator = " • ")
        val mergedInquiry = mergeUniqueFieldTokens(*sorted.map { it.inquiryType }.toTypedArray(), separator = " • ")
        val mergedMessage = mergeUniqueFieldTokens(*sorted.map { it.message }.toTypedArray(), separator = " | ")
        val bestCislo = sorted.map { it.cisloZakaznika.trim() }
            .firstOrNull { it.startsWith("CUS-") }
            ?: sorted.map { it.cisloZakaznika.trim() }.firstOrNull { it.isNotEmpty() }
            ?: getNextCustomerNumber()
        val bestDepo = sorted.map { it.depo.trim() }.firstOrNull { it.isNotEmpty() } ?: (_currentDepo.value ?: "Depo 1")
        val allEvents = sorted.flatMap { it.udalosti }
            .distinctBy { "${it.typ}_${it.cisloDokladu}_${it.popis}" }
            .sortedByDescending { it.timestamp }
        val mergedPhotos = sorted.flatMap { it.photos }.distinct().take(3)

        return primary.copy(
            company = bestCompany,
            name = mergedName,
            phone = mergedPhone,
            email = mergedEmail,
            ic = mergedIc,
            dic = mergedDic,
            ulice = mergedUlice,
            inquiryType = mergedInquiry,
            message = mergedMessage,
            cisloZakaznika = bestCislo,
            depo = bestDepo,
            isAdresar = true,
            udalosti = allEvents,
            photos = mergedPhotos,
            timestamp = System.currentTimeMillis()
        )
    }

    fun mergeCustomersByIco(rawIco: String): com.example.data.CustomerRecord? {
        val normIco = com.example.data.normalizeCompanyIco(rawIco)
        if (normIco.length < 4) return null
        val all = _customers.value
        val matching = all.filter { com.example.data.normalizeCompanyIco(it.ic).equals(normIco, ignoreCase = true) }
        if (matching.isEmpty()) return null
        val merged = buildMergedCustomerRecord(matching)
        val mergedIds = matching.map { it.id }.toSet()

        val updatedCustomers = mutableListOf<com.example.data.CustomerRecord>()
        updatedCustomers.add(merged)
        all.forEach { rec ->
            if (rec.id !in mergedIds) {
                updatedCustomers.add(rec)
            } else if (rec.id != merged.id) {
                if (rec.isOrder) {
                    // Preserve order in Objednávky with unified contact details, but hide from Adresář so it's not duplicated
                    val updatedOrder = rec.copy(
                        company = merged.company,
                        name = merged.name,
                        phone = merged.phone,
                        email = merged.email,
                        ic = merged.ic,
                        dic = merged.dic,
                        ulice = merged.ulice,
                        cisloZakaznika = merged.cisloZakaznika,
                        isAdresar = false
                    )
                    updatedCustomers.add(updatedOrder)
                    viewModelScope.launch {
                        try { repository.insertCustomer(updatedOrder) } catch (_: Exception) {}
                    }
                } else {
                    markIdDeleted(rec.id)
                    viewModelScope.launch {
                        try { repository.deleteCustomer(rec.id) } catch (_: Exception) {}
                    }
                }
            }
        }
        _customers.value = updatedCustomers
        saveLocalCustomers(updatedCustomers)
        viewModelScope.launch {
            try { repository.insertCustomer(merged) } catch (_: Exception) {}
        }
        return merged
    }

    fun findDuplicateIcoGroupsInAdresar(): Map<String, List<com.example.data.CustomerRecord>> {
        return _customers.value
            .filter { it.isAdresar || !it.isOrder }
            .groupBy { com.example.data.normalizeCompanyIco(it.ic) }
            .filter { (ico, list) -> ico.length >= 4 && list.size > 1 }
    }

    fun findExistingAdresarByIco(rawIco: String, excludeId: String? = null): List<com.example.data.CustomerRecord> {
        val norm = com.example.data.normalizeCompanyIco(rawIco)
        if (norm.length < 4) return emptyList()
        return _customers.value.filter {
            (it.isAdresar || !it.isOrder) &&
                it.id != excludeId &&
                com.example.data.normalizeCompanyIco(it.ic).equals(norm, ignoreCase = true)
        }
    }

    fun mergeCustomerRecordsByIco(rawIco: String): com.example.data.CustomerRecord? =
        mergeCustomersByIco(rawIco)

    fun mergeAllDuplicateIcoInAdresar(): Int =
        mergeAllDuplicateCustomersByIco()

    fun mergeAllDuplicateCustomersByIco(): Int {
        val groups = _customers.value
            .filter { it.isAdresar || !it.isOrder }
            .groupBy { com.example.data.normalizeCompanyIco(it.ic).lowercase() }
            .filter { (ico, list) -> ico.length >= 4 && list.size > 1 }
        var mergedCount = 0
        groups.keys.forEach { ico ->
            if (mergeCustomersByIco(ico) != null) {
                mergedCount += 1
            }
        }
        return mergedCount
    }

    private fun syncContactsFromDocumentsToAdresar(
        nabidkyList: List<com.example.data.NabidkaRecord>,
        zakazkyList: List<com.example.data.ZakazkaRecord>
    ) {
        var currentList = _customers.value.toMutableList()
        var changed = false

        fun upsertExtractedContact(
            firma: String,
            jmeno: String,
            ic: String,
            dic: String,
            ulice: String,
            telefon: String,
            email: String,
            depo: String
        ) {
            if (firma.isBlank() && jmeno.isBlank()) return
            val normIc = com.example.data.normalizeCompanyIco(ic)
            val matchIdx = currentList.indexOfFirst { c ->
                (normIc.length >= 4 && com.example.data.normalizeCompanyIco(c.ic).equals(normIc, ignoreCase = true)) ||
                (firma.isNotBlank() && c.company.trim().equals(firma.trim(), ignoreCase = true)) ||
                (firma.isBlank() && jmeno.isNotBlank() && c.name.trim().equals(jmeno.trim(), ignoreCase = true) && (telefon.isBlank() || c.phone == telefon))
            }
            if (matchIdx >= 0) {
                val existing = currentList[matchIdx]
                val updated = existing.copy(
                    company = existing.company.ifBlank { firma.trim() },
                    name = existing.name.ifBlank { jmeno.trim() },
                    ic = existing.ic.ifBlank { normIc },
                    dic = existing.dic.ifBlank { dic.trim() },
                    ulice = mergeUniqueFieldTokens(existing.ulice, ulice, separator = " • "),
                    phone = mergeUniqueFieldTokens(existing.phone, telefon),
                    email = mergeUniqueFieldTokens(existing.email, email),
                    depo = existing.depo.ifBlank { depo },
                    cisloZakaznika = existing.cisloZakaznika.ifBlank { getNextCustomerNumberFromList(currentList) },
                    isAdresar = true
                )
                if (updated != existing) {
                    currentList[matchIdx] = updated
                    changed = true
                }
            }
        }

        nabidkyList.forEach { n ->
            upsertExtractedContact(n.firma, n.jmeno, n.ic, n.dic, n.ulice, n.telefon, n.email, n.depo)
        }
        zakazkyList.forEach { z ->
            upsertExtractedContact(z.firma, z.vyrizuje, z.ic, z.dic, z.ulice.ifBlank { z.lokalita }, z.telefon, z.email, z.depo)
        }

        if (changed) {
            _customers.value = currentList.sortedByDescending { it.timestamp }
            saveLocalCustomers(_customers.value)
        }
    }

    fun clearAllAdresarCompanies(): Int {
        val all = _customers.value
        val toDelete = all.filter { it.isAdresar || !it.isOrder }
        val count = toDelete.size
        toDelete.forEach { c ->
            markIdDeleted(c.id)
            viewModelScope.launch {
                try { repository.deleteCustomer(c.id) } catch (_: Exception) {}
                try { repository.markIdDeletedRemote(c.id, "customers") } catch (_: Exception) {}
            }
        }
        val remainingOrders = all.filter { it.isOrder }.map { it.copy(isAdresar = false) }
        _customers.value = remainingOrders
        saveLocalCustomers(remainingOrders)
        return count
    }

    fun wipeAllDataForProduction(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _customers.value = emptyList()
            _nabidky.value = emptyList()
            _zakazky.value = emptyList()
            _faktury.value = emptyList()
            _zalohoveFaktury.value = emptyList()
            _localTransports.value = emptyList()
            deletedIds.clear()

            listOf(
                customersFile,
                nabidkyFile,
                zakazkyFile,
                fakturyFile,
                zalohoveFakturyFile,
                containersFile,
                transportsFile,
                deletedIdsFile,
                containerSysNumsFile
            ).forEach { file ->
                try { if (file.exists()) file.delete() } catch (_: Exception) {}
            }

            try {
                repository.wipeAllTestDataFromCloud()
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }

    private fun getNextCustomerNumberFromList(list: List<com.example.data.CustomerRecord>): String {
        val maxNum = list.mapNotNull {
            if (it.cisloZakaznika.startsWith("A-")) {
                it.cisloZakaznika.removePrefix("A-").toIntOrNull()
            } else if (it.cisloZakaznika.startsWith("CUS-")) {
                it.cisloZakaznika.removePrefix("CUS-").toIntOrNull()
            } else null
        }.filter { it >= 1000 }.maxOrNull() ?: 1000
        return "A-" + (maxNum + 1)
    }

    fun getNextCustomerNumber(): String = getNextCustomerNumberFromList(_customers.value)

    fun ensureCustomerInAdresar(
        firma: String,
        jmeno: String,
        telefon: String,
        email: String,
        ic: String = "",
        dic: String = "",
        ulice: String = "",
        depo: String = "",
        eventTyp: String = "",
        eventDoklad: String = "",
        eventPopis: String = "",
        eventCastka: Double = 0.0
    ): com.example.data.CustomerRecord? {
        if (firma.isBlank() && jmeno.isBlank()) return null
        val normIc = com.example.data.normalizeCompanyIco(ic)
        val existing = _customers.value.find { c ->
            (normIc.length >= 4 && com.example.data.normalizeCompanyIco(c.ic).equals(normIc, ignoreCase = true)) ||
            (firma.isNotBlank() && c.company.trim().equals(firma.trim(), ignoreCase = true)) ||
            (firma.isBlank() && jmeno.isNotBlank() && c.name.trim().equals(jmeno.trim(), ignoreCase = true))
        }
        val targetDepo = depo.ifBlank { _currentDepo.value ?: "Depo 1" }
        val today = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val currentEvents = existing?.udalosti?.toMutableList() ?: mutableListOf()
        if (eventTyp.isNotBlank() && (eventDoklad.isNotBlank() || eventPopis.isNotBlank())) {
            val alreadyExists = currentEvents.any { it.typ == eventTyp && it.cisloDokladu == eventDoklad && eventDoklad.isNotBlank() }
            if (!alreadyExists) {
                currentEvents.add(
                    0,
                    com.example.data.CustomerEventRecord(
                        id = "ev_${System.currentTimeMillis()}",
                        datum = today,
                        typ = eventTyp,
                        cisloDokladu = eventDoklad,
                        popis = eventPopis,
                        castka = eventCastka,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                val idx = currentEvents.indexOfFirst { it.typ == eventTyp && it.cisloDokladu == eventDoklad }
                if (idx >= 0) {
                    currentEvents[idx] = currentEvents[idx].copy(
                        datum = today,
                        popis = eventPopis.ifBlank { currentEvents[idx].popis },
                        castka = if (eventCastka > 0.0) eventCastka else currentEvents[idx].castka,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
        }
        val record = if (existing != null) {
            existing.copy(
                company = existing.company.ifBlank { firma.trim() },
                name = jmeno.ifBlank { existing.name.ifBlank { firma.trim() } },
                phone = mergeUniqueFieldTokens(existing.phone, telefon),
                email = mergeUniqueFieldTokens(existing.email, email),
                ic = normIc.ifBlank { existing.ic },
                dic = dic.ifBlank { existing.dic },
                ulice = mergeUniqueFieldTokens(existing.ulice, ulice, separator = " • "),
                depo = existing.depo.ifBlank { targetDepo },
                cisloZakaznika = existing.cisloZakaznika.ifBlank { getNextCustomerNumber() },
                isAdresar = true,
                udalosti = currentEvents,
                timestamp = System.currentTimeMillis()
            )
        } else {
            com.example.data.CustomerRecord(
                id = "adr_${System.currentTimeMillis()}",
                name = jmeno.ifBlank { firma }.trim(),
                company = firma.trim(),
                phone = telefon.trim(),
                email = email.trim(),
                ic = normIc,
                dic = dic.trim(),
                ulice = ulice.trim(),
                depo = targetDepo,
                cisloZakaznika = getNextCustomerNumber(),
                isOrder = false,
                isAdresar = true,
                udalosti = currentEvents,
                timestamp = System.currentTimeMillis()
            )
        }
        val updatedList = if (existing != null) {
            _customers.value.map { if (it.id == existing.id) record else it }
        } else {
            listOf(record) + _customers.value
        }
        _customers.value = updatedList
        saveLocalCustomers(updatedList)
        viewModelScope.launch {
            try { repository.insertCustomer(record) } catch (_: Exception) {}
        }
        return record
    }

    fun saveAdresarContact(
        id: String = "",
        cisloZakaznika: String = "",
        company: String,
        name: String,
        phone: String,
        email: String,
        ic: String = "",
        dic: String = "",
        ulice: String = "",
        depo: String,
        bonita: String = "",
        bonitaDetail: String = "",
        udalosti: List<com.example.data.CustomerEventRecord>? = null,
        photos: List<String>? = null
    ): com.example.data.CustomerRecord {
        val existing = if (id.isNotBlank()) _customers.value.find { it.id == id } else null
        val finalId = id.ifBlank { "adr_${System.currentTimeMillis()}" }
        val finalCislo = cisloZakaznika.ifBlank { existing?.cisloZakaznika?.ifBlank { null } ?: getNextCustomerNumber() }
        val record = (existing ?: com.example.data.CustomerRecord(id = finalId, isOrder = false)).copy(
            id = finalId,
            cisloZakaznika = finalCislo,
            company = company.trim(),
            name = name.trim().ifBlank { company.trim() },
            phone = phone.trim(),
            email = email.trim(),
            ic = ic.trim(),
            dic = dic.trim(),
            ulice = ulice.trim(),
            depo = depo.ifBlank { _currentDepo.value ?: "Depo 1" },
            isAdresar = true,
            bonita = bonita.ifBlank { existing?.bonita?.ifBlank { "AAA" } ?: "AAA" },
            bonitaDetail = bonitaDetail.ifBlank { existing?.bonitaDetail ?: "Platební morálka výborná • Bez exekucí • Bez dluhů u FÚ" },
            udalosti = udalosti ?: existing?.udalosti ?: emptyList(),
            photos = (photos ?: existing?.photos ?: emptyList()).take(3),
            timestamp = System.currentTimeMillis()
        )
        val current = _customers.value.toMutableList()
        val idx = current.indexOfFirst { it.id == finalId }
        if (idx >= 0) current[idx] = record else current.add(0, record)
        _customers.value = current
        saveLocalCustomers(current)
        viewModelScope.launch {
            try { repository.insertCustomer(record) } catch (_: Exception) {}
        }
        return record
    }

    fun saveOrderRecord(
        id: String = "",
        name: String,
        company: String,
        phone: String,
        email: String,
        ic: String = "",
        dic: String = "",
        ulice: String = "",
        inquiryType: String,
        message: String,
        status: String = "NOVÁ",
        depo: String,
        photos: List<String>? = null
    ): com.example.data.CustomerRecord {
        val existing = if (id.isNotBlank()) _customers.value.find { it.id == id } else null
        val finalId = id.ifBlank { "cus_${System.currentTimeMillis()}" }
        val record = (existing ?: com.example.data.CustomerRecord(id = finalId)).copy(
            id = finalId,
            name = name.trim().ifBlank { company.trim() },
            company = company.trim(),
            phone = phone.trim(),
            email = email.trim(),
            ic = ic.trim(),
            dic = dic.trim(),
            ulice = ulice.trim(),
            inquiryType = inquiryType.ifBlank { "Koupě kontejneru" },
            message = message.trim(),
            status = status.ifBlank { "NOVÁ" },
            depo = depo,
            cisloZakaznika = existing?.cisloZakaznika?.ifBlank { getNextCustomerNumber() } ?: getNextCustomerNumber(),
            isOrder = true,
            isAdresar = true,
            photos = (photos ?: existing?.photos ?: emptyList()).take(3),
            timestamp = System.currentTimeMillis()
        )
        val current = _customers.value.toMutableList()
        val idx = current.indexOfFirst { it.id == finalId }
        if (idx >= 0) current[idx] = record else current.add(0, record)
        _customers.value = current
        saveLocalCustomers(current)
        viewModelScope.launch {
            try { repository.insertCustomer(record) } catch (_: Exception) {}
        }
        return record
    }

    fun updateCustomerPhotos(customerId: String, newPhotos: List<String>) {
        val current = _customers.value.toMutableList()
        val idx = current.indexOfFirst { it.id == customerId }
        if (idx >= 0) {
            val updated = current[idx].copy(
                photos = newPhotos.take(3),
                timestamp = System.currentTimeMillis()
            )
            current[idx] = updated
            _customers.value = current
            saveLocalCustomers(current)
            viewModelScope.launch {
                try { repository.insertCustomer(updated) } catch (_: Exception) {}
            }
        }
    }

    fun splitOrderIntoBothDepos(order: com.example.data.CustomerRecord) {
        val now = System.currentTimeMillis()
        val orderDepo1 = order.copy(depo = "Depo 1", isOrder = true, timestamp = now)
        val orderDepo2 = order.copy(
            id = "cus_${now + 1}",
            depo = "Depo 2",
            isOrder = true,
            timestamp = now + 1
        )
        val updated = listOf(orderDepo2) + _customers.value.map { if (it.id == order.id) orderDepo1 else it }
        _customers.value = updated
        saveLocalCustomers(updated)
        viewModelScope.launch {
            try {
                repository.insertCustomer(orderDepo1)
                repository.insertCustomer(orderDepo2)
            } catch (_: Exception) {}
        }
    }

    fun updateCustomerStatus(id: String, status: String) {
        val now = System.currentTimeMillis()
        _customers.value = _customers.value.map { if (it.id == id) it.copy(status = status, timestamp = now) else it }
        saveLocalCustomers(_customers.value)
        viewModelScope.launch {
            try { repository.updateCustomerStatus(id, status) } catch (_: Exception) {}
        }
    }

    fun updateCustomerDepo(id: String, depo: String) {
        val now = System.currentTimeMillis()
        _customers.value = _customers.value.map { if (it.id == id) it.copy(depo = depo, timestamp = now) else it }
        saveLocalCustomers(_customers.value)
        viewModelScope.launch {
            try { repository.updateCustomerDepo(id, depo) } catch (_: Exception) {}
        }
    }

    fun updateNabidkaDepo(id: String, depo: String) {
        val existing = _nabidky.value.find { it.id == id } ?: return
        val updated = existing.copy(depo = depo, timestamp = System.currentTimeMillis())
        _nabidky.value = _nabidky.value.map { if (it.id == id) updated else it }
        saveLocalNabidky(_nabidky.value)
        viewModelScope.launch {
            try { repository.insertNabidka(updated, updated.text) } catch (_: Exception) {}
        }
    }

    fun updateNabidkaStatus(id: String, status: String) {
        val existing = _nabidky.value.find { it.id == id } ?: return
        val updated = existing.copy(status = status, timestamp = System.currentTimeMillis())
        _nabidky.value = _nabidky.value.map { if (it.id == id) updated else it }
        saveLocalNabidky(_nabidky.value)
        viewModelScope.launch {
            try { repository.insertNabidka(updated, updated.text) } catch (_: Exception) {}
        }
    }

    fun updateZakazkaDepo(id: String, depo: String) {
        val existing = _zakazky.value.find { it.id == id } ?: return
        val updated = existing.copy(depo = depo, timestamp = System.currentTimeMillis())
        _zakazky.value = _zakazky.value.map { if (it.id == id) updated else it }
        saveLocalZakazky(_zakazky.value)
        viewModelScope.launch {
            try { repository.insertZakazka(updated, updated.lokalita, updated.items) } catch (_: Exception) {}
        }
    }

    fun updateZakazkaStatus(id: String, status: String) {
        val existing = _zakazky.value.find { it.id == id } ?: return
        val updated = existing.copy(status = status, timestamp = System.currentTimeMillis())
        _zakazky.value = _zakazky.value.map { if (it.id == id) updated else it }
        saveLocalZakazky(_zakazky.value)
        viewModelScope.launch {
            try { repository.insertZakazka(updated, updated.lokalita, updated.items) } catch (_: Exception) {}
        }
    }

    fun updateTransportStatus(transport: TransportRecord, newStatus: String) {
        saveTransport(transport.copy(status = newStatus))
    }

    fun updateTransportDepo(transport: TransportRecord, newDepo: String) {
        saveTransport(transport.copy(depo = newDepo))
    }

    fun saveZajemce(
        id: String = "",
        jmeno: String,
        firma: String = "",
        telefon: String = "",
        email: String = "",
        depo: String = "Depo 1",
        typKontejneru: String = "OB 6 - Obytný 6m",
        zamer: String = "Nákup",
        rozpocet: String = "",
        status: String = "AKTIVNÍ",
        poznamka: String = "",
        vizitkaUri: String = "",
        photos: List<String> = emptyList()
    ): com.example.data.ZajemceRecord {
        val finalId = if (id.isNotBlank()) id else "zj_${System.currentTimeMillis()}"
        val existing = _zajemci.value.find { it.id == finalId }
        val cisloZaj = existing?.cisloZajemce?.ifBlank {
            "ZJ${java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date())}-${(_zajemci.value.size + 1).toString().padStart(3, '0')}"
        } ?: "ZJ${java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date())}-${(_zajemci.value.size + 1).toString().padStart(3, '0')}"
        val finalPhotos = (photos.take(3).ifEmpty { existing?.photos ?: emptyList() }).take(3)
        val record = com.example.data.ZajemceRecord(
            id = finalId,
            cisloZajemce = cisloZaj,
            depo = depo,
            jmeno = jmeno.trim(),
            firma = firma.trim(),
            telefon = telefon.trim(),
            email = email.trim(),
            typKontejneru = typKontejneru,
            zamer = zamer,
            rozpocet = rozpocet,
            status = status.ifBlank { "AKTIVNÍ" },
            poznamka = poznamka.trim(),
            vizitkaUri = vizitkaUri,
            timestamp = System.currentTimeMillis(),
            photos = finalPhotos
        )
        val current = _zajemci.value.toMutableList()
        val idx = current.indexOfFirst { it.id == finalId }
        if (idx >= 0) current[idx] = record else current.add(0, record)
        _zajemci.value = current
        saveLocalZajemci(current)
        ensureCustomerInAdresar(
            firma = record.firma,
            jmeno = record.jmeno,
            telefon = record.telefon,
            email = record.email,
            ic = "",
            dic = "",
            ulice = "",
            depo = record.depo,
            eventTyp = "POPTÁVKA"
        )
        viewModelScope.launch {
            try { repository.upsertZajemce(record) } catch (_: Exception) {}
        }
        return record
    }

    fun updateZajemceStatus(id: String, status: String) {
        val existing = _zajemci.value.find { it.id == id } ?: return
        val updated = existing.copy(status = status, timestamp = System.currentTimeMillis())
        _zajemci.value = _zajemci.value.map { if (it.id == id) updated else it }
        saveLocalZajemci(_zajemci.value)
        viewModelScope.launch {
            try { repository.upsertZajemce(updated) } catch (_: Exception) {}
        }
    }

    fun updateZajemceDepo(id: String, depo: String) {
        val existing = _zajemci.value.find { it.id == id } ?: return
        val updated = existing.copy(depo = depo, timestamp = System.currentTimeMillis())
        _zajemci.value = _zajemci.value.map { if (it.id == id) updated else it }
        saveLocalZajemci(_zajemci.value)
        viewModelScope.launch {
            try { repository.upsertZajemce(updated) } catch (_: Exception) {}
        }
    }

    fun deleteZajemce(id: String) {
        markIdDeleted(id)
        _zajemci.value = _zajemci.value.filter { it.id != id }
        saveLocalZajemci(_zajemci.value)
        viewModelScope.launch {
            try { repository.deleteZajemce(id) } catch (_: Exception) {}
        }
    }

    fun convertZajemceToNabidka(zajemce: com.example.data.ZajemceRecord): String {
        val nabidkaCislo = getNextNabidkaNumber()
        val itemText = "Poptávka z DEPA (${zajemce.depo}): ${zajemce.typKontejneru} (${zajemce.zamer}). Pozn: ${zajemce.poznamka.ifBlank { "Bez poznámky" }}"
        val defaultItems = listOf(
            com.example.data.NabidkaItemRecord(
                id = "item_${System.currentTimeMillis()}",
                kod = "KONT",
                text = "${zajemce.typKontejneru} (${zajemce.zamer})",
                mnozstvi = 1.0,
                mj = "ks",
                jCena = 0.0,
                dphPct = 21.0,
                sleva = 0.0,
                pdp = false,
                castkaBez = 0.0,
                dphVal = 0.0,
                stav = "Nová"
            )
        )
        val nabidka = com.example.data.NabidkaRecord(
            id = "nab_${System.currentTimeMillis()}",
            cislo = nabidkaCislo,
            depo = zajemce.depo,
            datum = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
            firma = zajemce.firma.ifBlank { zajemce.jmeno },
            jmeno = zajemce.jmeno,
            telefon = zajemce.telefon,
            email = zajemce.email,
            text = itemText,
            status = "NOVÁ",
            items = defaultItems,
            timestamp = System.currentTimeMillis()
        )
        _nabidky.value = listOf(nabidka) + _nabidky.value
        saveLocalNabidky(_nabidky.value)

        val updatedZajemce = zajemce.copy(
            status = "POSLÁNA NABÍDKA",
            timestamp = System.currentTimeMillis()
        )
        val currentZ = _zajemci.value.toMutableList()
        val idx = currentZ.indexOfFirst { it.id == zajemce.id }
        if (idx >= 0) currentZ[idx] = updatedZajemce
        _zajemci.value = currentZ
        saveLocalZajemci(currentZ)

        viewModelScope.launch {
            try { repository.insertNabidka(nabidka, itemText) } catch (_: Exception) {}
            try { repository.upsertZajemce(updatedZajemce) } catch (_: Exception) {}
        }
        return nabidka.id
    }

    fun deleteCustomer(id: String) {
        markIdDeleted(id)
        _customers.value = _customers.value.filter { it.id != id }
        saveLocalCustomers(_customers.value)
        viewModelScope.launch {
            try { repository.deleteCustomer(id) } catch (_: Exception) {}
        }
    }

    fun getNextNabidkaNumber(): String {
        val yearPrefix = java.text.SimpleDateFormat("yy", java.util.Locale.getDefault()).format(java.util.Date()) + "NA"
        val maxNum = _nabidky.value.mapNotNull {
            if (it.cislo.startsWith(yearPrefix)) it.cislo.removePrefix(yearPrefix).toIntOrNull() else null
        }.maxOrNull() ?: 0
        return yearPrefix + (maxNum + 1).toString().padStart(4, '0')
    }

    fun getNextZakazkaNumber(): String {
        val yearPrefix = "26ZA"
        val maxNum = _zakazky.value.mapNotNull {
            if (it.cisloZakazky.startsWith(yearPrefix)) it.cisloZakazky.removePrefix(yearPrefix).toIntOrNull() else null
        }.maxOrNull() ?: 0
        return yearPrefix + (maxNum + 1).toString().padStart(4, '0')
    }

    fun getNextTransportNumber(): String {
        val yearPrefix = "26PR"
        val maxNum = transports.value.mapNotNull {
            if (it.designation.startsWith(yearPrefix)) it.designation.removePrefix(yearPrefix).toIntOrNull() else null
        }.maxOrNull() ?: 0
        return yearPrefix + (maxNum + 1).toString().padStart(4, '0')
    }

    fun createNabidkaFromCustomer(customer: com.example.data.CustomerRecord): com.example.data.NabidkaRecord {
        val targetDepo = customer.depo.ifBlank { _currentDepo.value ?: "Depo 1" }
        val newCislo = getNextNabidkaNumber()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val itemText = listOf(customer.inquiryType, customer.message).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Kontejner" }
        val defaultItems = listOf(
            com.example.data.NabidkaItemRecord(
                id = "nitem_${System.currentTimeMillis()}",
                kod = "KONT",
                text = itemText,
                mnozstvi = 1.0,
                mj = "ks",
                jCena = 0.0,
                dphPct = 21.0,
                sleva = 0.0,
                pdp = false,
                castkaBez = 0.0,
                dphVal = 0.0
            )
        )
        val nabidka = com.example.data.NabidkaRecord(
            id = "nab_${System.currentTimeMillis()}",
            cislo = newCislo,
            depo = targetDepo,
            datum = today,
            ic = customer.ic,
            dic = customer.dic,
            ulice = customer.ulice,
            firma = customer.company.ifBlank { customer.name },
            jmeno = customer.name,
            telefon = customer.phone,
            email = customer.email,
            text = itemText,
            zdrojObjednavkaId = if (customer.isOrder) customer.id else "",
            adresarId = customer.id,
            items = defaultItems,
            timestamp = System.currentTimeMillis()
        )
        _nabidky.value = listOf(nabidka) + _nabidky.value
        saveLocalNabidky(_nabidky.value)
        ensureCustomerInAdresar(
            firma = nabidka.firma,
            jmeno = nabidka.jmeno,
            telefon = nabidka.telefon,
            email = nabidka.email,
            ic = nabidka.ic,
            dic = nabidka.dic,
            ulice = nabidka.ulice,
            depo = targetDepo,
            eventTyp = "CN",
            eventDoklad = nabidka.cislo,
            eventPopis = "Vystavena CN Nabídka: ${nabidka.text}"
        )
        if (customer.isOrder) {
            updateCustomerDepo(customer.id, targetDepo)
            updateCustomerStatus(customer.id, "V ŘEŠENÍ")
        }
        viewModelScope.launch {
            try { repository.insertNabidka(nabidka, itemText) } catch (_: Exception) {}
        }
        return nabidka
    }

    fun createZakazkaFromCustomer(customer: com.example.data.CustomerRecord): com.example.data.ZakazkaRecord {
        val targetDepo = customer.depo.ifBlank { _currentDepo.value ?: "Depo 1" }
        val newCislo = getNextZakazkaNumber()
        val itemText = listOf(customer.inquiryType, customer.message).filter { it.isNotBlank() }.joinToString(": ").ifBlank { customer.ulice }
        val zakazka = com.example.data.ZakazkaRecord(
            id = "zak_${System.currentTimeMillis()}",
            cisloZakazky = newCislo,
            depo = targetDepo,
            ic = customer.ic,
            dic = customer.dic,
            ulice = customer.ulice,
            firma = customer.company.ifBlank { customer.name },
            vyrizuje = customer.name,
            email = customer.email,
            telefon = customer.phone,
            lokalita = itemText,
            splatnost = "14",
            zdrojObjednavkaId = if (customer.isOrder) customer.id else "",
            adresarId = customer.id,
            timestamp = System.currentTimeMillis()
        )
        _zakazky.value = listOf(zakazka) + _zakazky.value
        saveLocalZakazky(_zakazky.value)
        ensureCustomerInAdresar(
            firma = zakazka.firma,
            jmeno = zakazka.vyrizuje,
            telefon = zakazka.telefon,
            email = zakazka.email,
            ic = zakazka.ic,
            dic = zakazka.dic,
            ulice = zakazka.ulice,
            depo = targetDepo,
            eventTyp = "ZAKÁZKA",
            eventDoklad = zakazka.cisloZakazky,
            eventPopis = "Založena zakázka (${zakazka.stredisko}): ${zakazka.lokalita}"
        )
        if (customer.isOrder) {
            updateCustomerDepo(customer.id, targetDepo)
            updateCustomerStatus(customer.id, "VYŘÍZENO")
        }
        viewModelScope.launch {
            try { repository.insertZakazka(zakazka, itemText) } catch (_: Exception) {}
        }
        return zakazka
    }

    fun createCustomCustomer(
        name: String,
        company: String,
        phone: String,
        email: String,
        inquiryType: String,
        message: String,
        depo: String,
        ic: String = "",
        dic: String = "",
        ulice: String = ""
    ): com.example.data.CustomerRecord {
        return saveOrderRecord(
            name = name,
            company = company,
            phone = phone,
            email = email,
            ic = ic,
            dic = dic,
            ulice = ulice,
            inquiryType = inquiryType,
            message = message,
            status = "NOVÁ",
            depo = depo
        )
    }

    fun createZakazkaFromNabidka(nabidka: com.example.data.NabidkaRecord): com.example.data.ZakazkaRecord {
        val targetDepo = nabidka.depo.ifBlank { _currentDepo.value ?: "Depo 1" }
        val newCislo = getNextZakazkaNumber()
        val zakazka = com.example.data.ZakazkaRecord(
            id = "zak_${System.currentTimeMillis()}",
            cisloZakazky = newCislo,
            cisloNabidky = nabidka.cislo,
            depo = targetDepo,
            ic = nabidka.ic,
            dic = nabidka.dic,
            ulice = nabidka.ulice,
            firma = nabidka.firma.ifBlank { nabidka.jmeno },
            vyrizuje = nabidka.jmeno,
            email = nabidka.email,
            telefon = nabidka.telefon,
            lokalita = nabidka.text.ifBlank { nabidka.ulice },
            splatnost = "14",
            zdrojNabidkaId = nabidka.id,
            adresarId = nabidka.adresarId,
            items = nabidka.items,
            timestamp = System.currentTimeMillis()
        )
        _zakazky.value = listOf(zakazka) + _zakazky.value
        saveLocalZakazky(_zakazky.value)
        val sumSDph = zakazka.items.sumOf { it.castkaBez + it.dphVal }
        ensureCustomerInAdresar(
            firma = nabidka.firma,
            jmeno = nabidka.jmeno,
            telefon = nabidka.telefon,
            email = nabidka.email,
            ic = nabidka.ic,
            dic = nabidka.dic,
            ulice = nabidka.ulice,
            depo = targetDepo,
            eventTyp = "ZAKÁZKA",
            eventDoklad = zakazka.cisloZakazky,
            eventPopis = "Zakázka z CN ${nabidka.cislo}: ${zakazka.lokalita}",
            eventCastka = sumSDph
        )
        viewModelScope.launch {
            try { repository.insertZakazka(zakazka, nabidka.text, nabidka.items) } catch (_: Exception) {}
        }
        return zakazka
    }

    private fun defaultCatalogItems(): List<com.example.data.CatalogItemRecord> {
        val containerCategories = listOf(
            "Obytné kontejnery" to "obyt",
            "Skladové kontejnery" to "sklad",
            "Skladové námořní kontejnery" to "namor",
            "Speciální kontejnery" to "spec"
        )
        val list = mutableListOf<com.example.data.CatalogItemRecord>()
        for ((catName, codePrefix) in containerCategories) {
            list.add(
                com.example.data.CatalogItemRecord(
                    id = "def_${codePrefix}_001",
                    sekce = "KONTEJNERY",
                    kategorie = catName,
                    oznaceni = "001",
                    nazev = "Kontejner délka 6 m ($catName)",
                    pocet = 1.0,
                    cenaBezDph = 0.0,
                    dphPct = 21.0,
                    slevaPct = 0.0,
                    celkemBezDph = 0.0,
                    celkemSDph = 0.0,
                    poradi = 1
                )
            )
            list.add(
                com.example.data.CatalogItemRecord(
                    id = "def_${codePrefix}_002",
                    sekce = "KONTEJNERY",
                    kategorie = catName,
                    oznaceni = "002",
                    nazev = "Kontejner 12 m ($catName)",
                    pocet = 1.0,
                    cenaBezDph = 0.0,
                    dphPct = 21.0,
                    slevaPct = 0.0,
                    celkemBezDph = 0.0,
                    celkemSDph = 0.0,
                    poradi = 2
                )
            )
            list.add(
                com.example.data.CatalogItemRecord(
                    id = "def_${codePrefix}_003",
                    sekce = "KONTEJNERY",
                    kategorie = catName,
                    oznaceni = "003",
                    nazev = "Kontejner 12 m HC ($catName)",
                    pocet = 1.0,
                    cenaBezDph = 0.0,
                    dphPct = 21.0,
                    slevaPct = 0.0,
                    celkemBezDph = 0.0,
                    celkemSDph = 0.0,
                    poradi = 3
                )
            )
        }
        val equipmentNames = listOf(
            "Židle",
            "Stoly",
            "Skříňky",
            "Klimatizace",
            "Pojištění",
            "Kostky na podložení"
        )
        equipmentNames.forEachIndexed { index, eqName ->
            val num = (index + 1).toString().padStart(3, '0')
            list.add(
                com.example.data.CatalogItemRecord(
                    id = "def_vyb_$num",
                    sekce = "VYBAVENÍ",
                    kategorie = "Vybavení",
                    oznaceni = num,
                    nazev = eqName,
                    pocet = 1.0,
                    cenaBezDph = 0.0,
                    dphPct = 21.0,
                    slevaPct = 0.0,
                    celkemBezDph = 0.0,
                    celkemSDph = 0.0,
                    poradi = index + 1
                )
            )
        }
        val transportNames = listOf(
            "Přeprava tam",
            "Přeprava zpátky",
            "Manipulace (nakládka / vykládka)",
            "Složení hydraulickou rukou (HR)",
            "Přeprava nadměrného nákladu / 12 m",
            "Čekání vozidla / prostoj"
        )
        transportNames.forEachIndexed { index, trName ->
            val num = (index + 1).toString().padStart(3, '0')
            list.add(
                com.example.data.CatalogItemRecord(
                    id = "def_prep_$num",
                    sekce = "PŘEPRAVA",
                    kategorie = "Přeprava",
                    oznaceni = num,
                    nazev = trName,
                    pocet = 1.0,
                    cenaBezDph = 0.0,
                    dphPct = 21.0,
                    slevaPct = 0.0,
                    celkemBezDph = 0.0,
                    celkemSDph = 0.0,
                    poradi = index + 1
                )
            )
        }
        return list
    }

    private val customSectionsFile = File(filesDir, "custom_catalog_sections.json")
    private val defaultSectionNames = listOf(
        "Obytné kontejnery",
        "Skladové kontejnery",
        "Skladové námořní kontejnery",
        "Speciální kontejnery",
        "Vybavení",
        "Přeprava"
    )
    private val _catalogSections = MutableStateFlow<List<String>>(loadSavedSections())
    val catalogSections: StateFlow<List<String>> = _catalogSections.asStateFlow()

    private fun loadSavedSections(): List<String> {
        if (!customSectionsFile.exists()) return defaultSectionNames
        return try {
            val arr = org.json.JSONArray(customSectionsFile.readText())
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotBlank() && s !in list) list.add(s)
            }
            if (list.isEmpty()) defaultSectionNames else list
        } catch (_: Exception) {
            defaultSectionNames
        }
    }

    private fun saveSectionsList(list: List<String>) {
        _catalogSections.value = list
        viewModelScope.launch {
            try {
                val arr = org.json.JSONArray()
                list.forEach { arr.put(it) }
                customSectionsFile.writeText(arr.toString())
            } catch (_: Exception) {}
        }
    }

    fun addCatalogSection(sectionName: String, parentSekce: String = "KONTEJNERY") {
        val clean = sectionName.trim()
        if (clean.isBlank()) return
        val current = _catalogSections.value.toMutableList()
        if (clean !in current) {
            current.add(clean)
            saveSectionsList(current)
        }
        val hasItems = _catalogItems.value.any { it.kategorie.equals(clean, ignoreCase = true) }
        if (!hasItems) {
            upsertCatalogItem(
                com.example.data.CatalogItemRecord(
                    id = "pol_${System.currentTimeMillis()}",
                    sekce = parentSekce.ifBlank { "KONTEJNERY" },
                    kategorie = clean,
                    oznaceni = "001",
                    nazev = "Položka – $clean",
                    pocet = 1.0,
                    cenaBezDph = 0.0,
                    dphPct = 21.0,
                    slevaPct = 0.0,
                    poradi = 1
                )
            )
        }
    }

    fun removeCatalogSection(sectionName: String, deleteItems: Boolean = true) {
        val clean = sectionName.trim()
        if (clean.isBlank()) return
        val updatedSections = _catalogSections.value.filter { !it.equals(clean, ignoreCase = true) }
        saveSectionsList(updatedSections)
        if (deleteItems) {
            val toDelete = _catalogItems.value.filter { it.kategorie.equals(clean, ignoreCase = true) }
            toDelete.forEach { deleteCatalogItem(it.id) }
        }
    }

    private val zakazkaStrediskaFile = File(filesDir, "zakazka_strediska.json")
    private val defaultStrediska = listOf("Pronájem", "Prodej", "Ostatní")
    private val _zakazkaStrediska = MutableStateFlow<List<String>>(loadSavedStrediska())
    val zakazkaStrediska: StateFlow<List<String>> = _zakazkaStrediska.asStateFlow()

    private fun loadSavedStrediska(): List<String> {
        if (!zakazkaStrediskaFile.exists()) return defaultStrediska
        return try {
            val arr = org.json.JSONArray(zakazkaStrediskaFile.readText())
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotBlank() && s !in list) list.add(s)
            }
            if (list.isEmpty()) defaultStrediska else list
        } catch (_: Exception) {
            defaultStrediska
        }
    }

    private fun saveStrediskaList(list: List<String>) {
        val final = if (list.isEmpty()) defaultStrediska else list
        _zakazkaStrediska.value = final
        viewModelScope.launch {
            try {
                val arr = org.json.JSONArray()
                final.forEach { arr.put(it) }
                zakazkaStrediskaFile.writeText(arr.toString())
            } catch (_: Exception) {}
        }
    }

    fun addZakazkaStredisko(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val current = _zakazkaStrediska.value.toMutableList()
        if (clean !in current) {
            current.add(clean)
            saveStrediskaList(current)
        }
    }

    fun renameZakazkaStredisko(oldName: String, newName: String) {
        val cleanNew = newName.trim()
        if (cleanNew.isBlank()) return
        val current = _zakazkaStrediska.value.map { if (it == oldName) cleanNew else it }.distinct()
        saveStrediskaList(current)
        _zakazky.value.filter { it.stredisko == oldName }.forEach { zak ->
            updateZakazkaStredisko(zak.id, cleanNew)
        }
    }

    fun removeZakazkaStredisko(name: String) {
        val current = _zakazkaStrediska.value.filter { it != name }
        saveStrediskaList(if (current.isEmpty()) defaultStrediska else current)
    }

    fun updateZakazkaStredisko(id: String, stredisko: String) {
        val existing = _zakazky.value.find { it.id == id } ?: return
        val updated = existing.copy(stredisko = stredisko, timestamp = System.currentTimeMillis())
        _zakazky.value = _zakazky.value.map { if (it.id == id) updated else it }
        saveLocalZakazky(_zakazky.value)
        viewModelScope.launch {
            try { repository.insertZakazka(updated, updated.lokalita, updated.items) } catch (_: Exception) {}
        }
    }

    private fun categoryOrderIndex(kategorie: String): Int = when (kategorie) {
        "Obytné kontejnery" -> 0
        "Skladové kontejnery" -> 1
        "Skladové námořní kontejnery" -> 2
        "Speciální kontejnery" -> 3
        "Vybavení" -> 4
        "Přeprava" -> 5
        else -> 6 + _catalogSections.value.indexOf(kategorie).coerceAtLeast(0)
    }

    private fun sortCatalogList(items: List<com.example.data.CatalogItemRecord>): List<com.example.data.CatalogItemRecord> {
        return items.sortedWith(
            compareBy<com.example.data.CatalogItemRecord> { categoryOrderIndex(it.kategorie) }
                .thenBy { it.poradi }
                .thenBy { it.oznaceni }
        )
    }

    private fun normalizeLegacyTransportItems(items: List<com.example.data.CatalogItemRecord>): List<com.example.data.CatalogItemRecord> {
        val legacyTransportNames = setOf("přeprava tam", "přeprava zpátky", "manipulace", "složení")
        return items.map { rec ->
            if (rec.kategorie == "Vybavení" && rec.nazev.trim().lowercase() in legacyTransportNames) {
                rec.copy(sekce = "PŘEPRAVA", kategorie = "Přeprava")
            } else if (rec.kategorie == "Přeprava" && rec.sekce != "PŘEPRAVA") {
                rec.copy(sekce = "PŘEPRAVA")
            } else {
                rec
            }
        }
    }

    private fun mergeCatalogWithDefaults(remoteList: List<com.example.data.CatalogItemRecord>): List<com.example.data.CatalogItemRecord> {
        val map = LinkedHashMap<String, com.example.data.CatalogItemRecord>()
        defaultCatalogItems().filter { it.id !in deletedIds }.forEach { map[it.id] = it }
        normalizeLegacyTransportItems(_catalogItems.value).filter { it.id !in deletedIds }.forEach { map[it.id] = it }
        normalizeLegacyTransportItems(remoteList).filter { it.id !in deletedIds }.forEach { map[it.id] = it }
        return map.values.toList()
    }

    fun reloadLocalCaches() {
        loadLocalCatalogItems()
        loadLocalAgendasCache()
        _localInventories.value = loadLocalInventories()
        syncAllAgendasNow()
    }

    fun resetCatalogToDefaults() {
        val defaults = defaultCatalogItems()
        defaults.forEach { deletedIds.remove(it.id) }
        saveDeletedIds()
        val merged = sortCatalogList(mergeCatalogWithDefaults(defaults))
        _catalogItems.value = merged
        saveLocalCatalogItems(merged)
        viewModelScope.launch {
            defaults.forEach { item ->
                try { repository.upsertCatalogItem(item) } catch (_: Exception) {}
            }
        }
    }

    private fun loadLocalCatalogItems() {
        if (!catalogFile.exists()) return
        try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.CatalogItemRecord::class.java)
            val adapter: JsonAdapter<List<com.example.data.CatalogItemRecord>> = moshi.adapter(type)
            val loaded = adapter.fromJson(catalogFile.readText())
            if (!loaded.isNullOrEmpty()) {
                val merged = mergeCatalogWithDefaults(loaded)
                _catalogItems.value = sortCatalogList(merged)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveLocalCatalogItems(list: List<com.example.data.CatalogItemRecord>) {
        viewModelScope.launch {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.CatalogItemRecord::class.java)
                val adapter: JsonAdapter<List<com.example.data.CatalogItemRecord>> = moshi.adapter(type)
                catalogFile.writeText(adapter.toJson(list))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun upsertCatalogItem(item: com.example.data.CatalogItemRecord) {
        val cleanPocet = if (item.pocet <= 0.0) 1.0 else item.pocet
        val cleanDph = if (item.dphPct == 5.0) 5.0 else 21.0
        val cleanSleva = item.slevaPct.coerceIn(0.0, 100.0)
        val celkemBez = kotlin.math.round(cleanPocet * item.cenaBezDph * (1.0 - cleanSleva / 100.0))
        val celkemS = kotlin.math.round(celkemBez * (1.0 + cleanDph / 100.0))
        val finalId = item.id.ifBlank { "pol_${System.currentTimeMillis()}" }
        val finalItem = item.copy(
            id = finalId,
            pocet = cleanPocet,
            dphPct = cleanDph,
            slevaPct = cleanSleva,
            celkemBezDph = celkemBez,
            celkemSDph = celkemS,
            timestamp = System.currentTimeMillis()
        )
        val current = _catalogItems.value.toMutableList()
        val idx = current.indexOfFirst { it.id == finalId }
        if (idx >= 0) {
            current[idx] = finalItem
        } else {
            current.add(finalItem)
        }
        val sorted = sortCatalogList(current)
        _catalogItems.value = sorted
        saveLocalCatalogItems(sorted)
        viewModelScope.launch {
            try { repository.upsertCatalogItem(finalItem) } catch (_: Exception) {}
        }
    }

    fun deleteCatalogItem(id: String) {
        markIdDeleted(id)
        val updated = _catalogItems.value.filter { it.id != id }
        _catalogItems.value = updated
        saveLocalCatalogItems(updated)
        viewModelScope.launch {
            try { repository.deleteCatalogItem(id) } catch (_: Exception) {}
        }
    }

    fun moveCatalogItem(item: com.example.data.CatalogItemRecord, moveUp: Boolean) {
        val all = _catalogItems.value.toMutableList()
        val sameCat = all.filter { it.kategorie == item.kategorie }
            .sortedWith(compareBy<com.example.data.CatalogItemRecord> { it.poradi }.thenBy { it.oznaceni })
            .toMutableList()
        val index = sameCat.indexOfFirst { it.id == item.id }
        if (index < 0) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in sameCat.indices) return

        val swapped = sameCat[targetIndex]
        sameCat[targetIndex] = item
        sameCat[index] = swapped

        val reindexed = sameCat.mapIndexed { idx, rec ->
            rec.copy(poradi = idx + 1, timestamp = System.currentTimeMillis())
        }
        val reindexedMap = reindexed.associateBy { it.id }
        val newAll = sortCatalogList(all.map { reindexedMap[it.id] ?: it })
        _catalogItems.value = newAll
        saveLocalCatalogItems(newAll)
        viewModelScope.launch {
            reindexed.forEach { rec ->
                try { repository.upsertCatalogItem(rec) } catch (_: Exception) {}
            }
        }
    }

    fun catalogItemToNabidkaItem(cat: com.example.data.CatalogItemRecord, customQty: Double? = null): com.example.data.NabidkaItemRecord {
        val qty = customQty ?: cat.pocet.coerceAtLeast(1.0)
        val bez = kotlin.math.round(qty * cat.cenaBezDph * (1.0 - cat.slevaPct / 100.0))
        val dphVal = kotlin.math.round(bez * (cat.dphPct / 100.0))
        return com.example.data.NabidkaItemRecord(
            id = "nitem_${System.currentTimeMillis()}_${(100..999).random()}",
            kod = cat.oznaceni.ifBlank { "KONT" },
            text = "${cat.oznaceni} - ${cat.nazev}".trim(' ', '-'),
            mnozstvi = qty,
            mj = "ks",
            jCena = cat.cenaBezDph,
            dphPct = cat.dphPct,
            sleva = cat.slevaPct,
            pdp = false,
            castkaBez = bez,
            dphVal = dphVal
        )
    }

    fun updateNabidkaItems(nabidkaId: String, newItems: List<com.example.data.NabidkaItemRecord>) {
        val existing = _nabidky.value.find { it.id == nabidkaId } ?: return
        val updated = existing.copy(items = newItems, timestamp = System.currentTimeMillis())
        _nabidky.value = _nabidky.value.map { if (it.id == nabidkaId) updated else it }
        saveLocalNabidky(_nabidky.value)
        viewModelScope.launch {
            try { repository.insertNabidka(updated, updated.text) } catch (_: Exception) {}
        }
    }

    fun updateZakazkaItems(zakazkaId: String, newItems: List<com.example.data.NabidkaItemRecord>) {
        val existing = _zakazky.value.find { it.id == zakazkaId } ?: return
        val updated = existing.copy(items = newItems, timestamp = System.currentTimeMillis())
        _zakazky.value = _zakazky.value.map { if (it.id == zakazkaId) updated else it }
        saveLocalZakazky(_zakazky.value)
        viewModelScope.launch {
            try { repository.insertZakazka(updated, updated.lokalita, newItems) } catch (_: Exception) {}
        }
    }

    fun saveFullNabidka(
        id: String = "",
        cislo: String = "",
        datum: String = "",
        platnost: String = "",
        firma: String,
        jmeno: String,
        telefon: String,
        email: String,
        text: String,
        depo: String,
        ic: String = "",
        dic: String = "",
        ulice: String = "",
        adresarId: String = "",
        items: List<com.example.data.NabidkaItemRecord> = emptyList()
    ): com.example.data.NabidkaRecord {
        val existing = if (id.isNotBlank()) _nabidky.value.find { it.id == id } else null
        val finalId = id.ifBlank { "nab_${System.currentTimeMillis()}" }
        val finalCislo = cislo.ifBlank { existing?.cislo?.ifBlank { null } ?: getNextNabidkaNumber() }
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val syncedAdr = ensureCustomerInAdresar(
            firma = firma,
            jmeno = jmeno,
            telefon = telefon,
            email = email,
            ic = ic,
            dic = dic,
            ulice = ulice,
            depo = depo,
            eventTyp = "CN",
            eventDoklad = finalCislo,
            eventPopis = text.ifBlank { "Cenová nabídka $finalCislo" },
            eventCastka = items.sumOf { it.castkaBez + it.dphVal }
        )
        val nabidka = (existing ?: com.example.data.NabidkaRecord(id = finalId)).copy(
            id = finalId,
            cislo = finalCislo,
            depo = depo.ifBlank { "Depo 1" },
            datum = datum.ifBlank { existing?.datum?.ifBlank { null } ?: today },
            platnost = platnost,
            ic = ic.trim(),
            dic = dic.trim(),
            firma = firma.trim().ifBlank { jmeno.trim() },
            jmeno = jmeno.trim(),
            telefon = telefon.trim(),
            email = email.trim(),
            ulice = ulice.trim(),
            text = text.trim(),
            adresarId = adresarId.ifBlank { syncedAdr?.id ?: existing?.adresarId.orEmpty() },
            items = items.ifEmpty { existing?.items ?: emptyList() },
            timestamp = System.currentTimeMillis()
        )
        val current = _nabidky.value.toMutableList()
        val idx = current.indexOfFirst { it.id == finalId }
        if (idx >= 0) current[idx] = nabidka else current.add(0, nabidka)
        _nabidky.value = current
        saveLocalNabidky(current)
        viewModelScope.launch {
            try { repository.insertNabidka(nabidka, text) } catch (_: Exception) {}
        }
        return nabidka
    }

    fun saveFullZakazka(
        id: String = "",
        cisloZakazky: String = "",
        datumCasZakazky: String = "",
        cisloNabidky: String = "",
        cisloObjednavky: String = "",
        stredisko: String = "Pronájem",
        prepravaInfo: String = "",
        firma: String,
        vyrizuje: String,
        telefon: String,
        email: String,
        ic: String = "",
        dic: String = "",
        ulice: String = "",
        lokalita: String,
        osobaKPrevzeti: String = "",
        kontaktPrevzeti: String = "",
        pocetOB: String = "0",
        pocetSK: String = "0",
        pocetSA: String = "0",
        pocetMA: String = "0",
        pocetZI: String = "0",
        pocetST: String = "0",
        pocetSK2: String = "0",
        pocetSA2: String = "0",
        poznamkaZObjednavky: String = "",
        splatnost: String = "14",
        obdobiOd: String = "",
        obdobiDo: String = "",
        datumDodano: String = "",
        datumVraceno: String = "",
        preprava1: String = "",
        zpet1: String = "",
        preprava2: String = "",
        zpet2: String = "",
        cenovaNabidkaCislo: String = "",
        protokolTam: String = "",
        smlouvaCislo: String = "",
        protokolZpet: String = "",
        kauceDatum: String = "",
        kauceCastka: String = "",
        kauceVraceni: String = "",
        kauceVraceniCastka: String = "",
        interniPoznamka: String = "",
        photos: List<String> = emptyList(),
        fakturaceDatum: String = "",
        fakturaceObdobi: String = "",
        depo: String,
        adresarId: String = "",
        zdrojNabidkaId: String = "",
        status: String = "NOVÁ",
        items: List<com.example.data.NabidkaItemRecord> = emptyList()
    ): com.example.data.ZakazkaRecord {
        val existing = if (id.isNotBlank()) _zakazky.value.find { it.id == id } else null
        val finalId = id.ifBlank { "zak_${System.currentTimeMillis()}" }
        val finalCislo = cisloZakazky.ifBlank { existing?.cisloZakazky?.ifBlank { null } ?: getNextZakazkaNumber() }
        val effectiveItems = items.ifEmpty { existing?.items ?: emptyList() }
        val finalStredisko = stredisko.ifBlank { existing?.stredisko?.ifBlank { "Pronájem" } ?: "Pronájem" }
        val finalPreprava = prepravaInfo.ifBlank { existing?.prepravaInfo.orEmpty() }
        val syncedAdr = ensureCustomerInAdresar(
            firma = firma,
            jmeno = vyrizuje,
            telefon = telefon,
            email = email,
            ic = ic,
            dic = dic,
            ulice = ulice.ifBlank { lokalita },
            depo = depo,
            eventTyp = "ZAKÁZKA",
            eventDoklad = finalCislo,
            eventPopis = "Zakázka ($finalStredisko): ${lokalita.ifBlank { firma }}",
            eventCastka = effectiveItems.sumOf { it.castkaBez + it.dphVal }
        )
        val zakazka = (existing ?: com.example.data.ZakazkaRecord(id = finalId)).copy(
            id = finalId,
            cisloZakazky = finalCislo,
            datumCasZakazky = datumCasZakazky.ifBlank { existing?.datumCasZakazky.orEmpty() },
            cisloNabidky = cisloNabidky.trim(),
            cisloObjednavky = cisloObjednavky.trim().ifBlank { existing?.cisloObjednavky.orEmpty() },
            depo = depo.ifBlank { "Depo 1" },
            stredisko = finalStredisko,
            prepravaInfo = finalPreprava,
            ic = ic.trim(),
            dic = dic.trim(),
            ulice = ulice.trim(),
            firma = firma.trim().ifBlank { vyrizuje.trim() },
            vyrizuje = vyrizuje.trim(),
            email = email.trim(),
            telefon = telefon.trim(),
            lokalita = lokalita.trim(),
            osobaKPrevzeti = osobaKPrevzeti.trim().ifBlank { existing?.osobaKPrevzeti.orEmpty() },
            kontaktPrevzeti = kontaktPrevzeti.trim().ifBlank { existing?.kontaktPrevzeti.orEmpty() },
            pocetOB = pocetOB.ifBlank { existing?.pocetOB ?: "0" },
            pocetSK = pocetSK.ifBlank { existing?.pocetSK ?: "0" },
            pocetSA = pocetSA.ifBlank { existing?.pocetSA ?: "0" },
            pocetMA = pocetMA.ifBlank { existing?.pocetMA ?: "0" },
            pocetZI = pocetZI.ifBlank { existing?.pocetZI ?: "0" },
            pocetST = pocetST.ifBlank { existing?.pocetST ?: "0" },
            pocetSK2 = pocetSK2.ifBlank { existing?.pocetSK2 ?: "0" },
            pocetSA2 = pocetSA2.ifBlank { existing?.pocetSA2 ?: "0" },
            poznamkaZObjednavky = poznamkaZObjednavky.ifBlank { existing?.poznamkaZObjednavky.orEmpty() },
            splatnost = splatnost.ifBlank { "14" },
            obdobiOd = obdobiOd.ifBlank { existing?.obdobiOd.orEmpty() },
            obdobiDo = obdobiDo.ifBlank { existing?.obdobiDo.orEmpty() },
            datumDodano = datumDodano.ifBlank { existing?.datumDodano.orEmpty() },
            datumVraceno = datumVraceno.ifBlank { existing?.datumVraceno.orEmpty() },
            preprava1 = preprava1.ifBlank { existing?.preprava1.orEmpty() },
            zpet1 = zpet1.ifBlank { existing?.zpet1.orEmpty() },
            preprava2 = preprava2.ifBlank { existing?.preprava2.orEmpty() },
            zpet2 = zpet2.ifBlank { existing?.zpet2.orEmpty() },
            cenovaNabidkaCislo = cenovaNabidkaCislo.ifBlank { existing?.cenovaNabidkaCislo.orEmpty() },
            protokolTam = protokolTam.ifBlank { existing?.protokolTam.orEmpty() },
            smlouvaCislo = smlouvaCislo.ifBlank { existing?.smlouvaCislo.orEmpty() },
            protokolZpet = protokolZpet.ifBlank { existing?.protokolZpet.orEmpty() },
            kauceDatum = kauceDatum.ifBlank { existing?.kauceDatum.orEmpty() },
            kauceCastka = kauceCastka.ifBlank { existing?.kauceCastka.orEmpty() },
            kauceVraceni = kauceVraceni.ifBlank { existing?.kauceVraceni.orEmpty() },
            kauceVraceniCastka = kauceVraceniCastka.ifBlank { existing?.kauceVraceniCastka.orEmpty() },
            interniPoznamka = interniPoznamka.ifBlank { existing?.interniPoznamka.orEmpty() },
            photos = if (photos.isNotEmpty()) photos else (existing?.photos ?: emptyList()),
            fakturaceDatum = fakturaceDatum.ifBlank { existing?.fakturaceDatum.orEmpty() },
            fakturaceObdobi = fakturaceObdobi.ifBlank { existing?.fakturaceObdobi.orEmpty() },
            status = status.ifBlank { existing?.status ?: "NOVÁ" },
            zdrojNabidkaId = zdrojNabidkaId.ifBlank { existing?.zdrojNabidkaId.orEmpty() },
            adresarId = adresarId.ifBlank { syncedAdr?.id ?: existing?.adresarId.orEmpty() },
            items = effectiveItems,
            timestamp = System.currentTimeMillis()
        )
        val current = _zakazky.value.toMutableList()
        val idx = current.indexOfFirst { it.id == finalId }
        if (idx >= 0) current[idx] = zakazka else current.add(0, zakazka)
        _zakazky.value = current
        saveLocalZakazky(current)
        viewModelScope.launch {
            try { repository.insertZakazka(zakazka, lokalita, zakazka.items) } catch (_: Exception) {}
        }
        return zakazka
    }

    private val pohodaIcoFile = File(filesDir, "pohoda_ico.txt")
    private val pohodaConfigFile = File(filesDir, "pohoda_mserver_config.json")

    private val _pohodaIco = MutableStateFlow(
        if (pohodaIcoFile.exists()) runCatching { pohodaIcoFile.readText().trim() }.getOrDefault("25388720").ifBlank { "25388720" } else "25388720"
    )
    val pohodaIco: StateFlow<String> = _pohodaIco.asStateFlow()

    private val _pohodaServerConfig = MutableStateFlow(loadInitialPohodaConfig())
    val pohodaServerConfig: StateFlow<PohodaServerConfig> = _pohodaServerConfig.asStateFlow()

    private fun loadInitialPohodaConfig(): PohodaServerConfig {
        if (pohodaConfigFile.exists()) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(PohodaServerConfig::class.java)
                val loaded = adapter.fromJson(pohodaConfigFile.readText())
                if (loaded != null) return loaded
            } catch (_: Exception) {}
        }
        val defaultIco = if (pohodaIcoFile.exists()) runCatching { pohodaIcoFile.readText().trim() }.getOrDefault("25388720") else "25388720"
        return PohodaServerConfig(ico = defaultIco.ifBlank { "25388720" }, username = "Prema")
    }

    fun setPohodaIco(ico: String) {
        val clean = ico.trim()
        _pohodaIco.value = clean
        if (_pohodaServerConfig.value.ico != clean) {
            _pohodaServerConfig.value = _pohodaServerConfig.value.copy(ico = clean)
        }
        viewModelScope.launch {
            try { pohodaIcoFile.writeText(clean) } catch (_: Exception) {}
        }
    }

    fun updatePohodaServerConfig(newConfig: PohodaServerConfig) {
        _pohodaServerConfig.value = newConfig
        val cleanIco = newConfig.ico.trim()
        _pohodaIco.value = cleanIco
        viewModelScope.launch(Dispatchers.IO) {
            try {
                pohodaIcoFile.writeText(cleanIco)
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(PohodaServerConfig::class.java)
                pohodaConfigFile.writeText(adapter.toJson(newConfig))
            } catch (_: Exception) {}
        }
    }

    suspend fun testPohodaConnection(cfg: PohodaServerConfig? = null): PohodaSyncResult {
        return PohodaMServerService.testConnection(cfg ?: _pohodaServerConfig.value)
    }

    suspend fun sendInvoicesToPohoda(invoices: List<com.example.data.FakturaRecord>, cfg: PohodaServerConfig? = null): PohodaSyncResult {
        val config = cfg ?: _pohodaServerConfig.value
        val result = PohodaMServerService.sendInvoices(config, invoices)
        if (result.success || result.syncedCount > 0) {
            val now = System.currentTimeMillis()
            val syncedIds = invoices.map { it.id }.toSet()

            val updatedFaktury = _faktury.value.map { f ->
                if (f.id in syncedIds) {
                    val packKey = "FA_${f.cislo}"
                    val assignedNumber = result.docNumberMapping[packKey] ?: f.cislo
                    f.copy(
                        pohodaId = assignedNumber,
                        pohodaSyncedAt = now,
                        pohodaStatus = "SYNCED",
                        pohodaError = ""
                    )
                } else f
            }
            _faktury.value = updatedFaktury
            saveLocalFaktury(updatedFaktury)

            val updatedZalohove = _zalohoveFaktury.value.map { f ->
                if (f.id in syncedIds) {
                    val packKey = "FA_${f.cislo}"
                    val assignedNumber = result.docNumberMapping[packKey] ?: f.cislo
                    f.copy(
                        pohodaId = assignedNumber,
                        pohodaSyncedAt = now,
                        pohodaStatus = "SYNCED",
                        pohodaError = ""
                    )
                } else f
            }
            _zalohoveFaktury.value = updatedZalohove
            saveLocalZalohoveFaktury(updatedZalohove)

            viewModelScope.launch {
                (updatedFaktury.filter { it.id in syncedIds } + updatedZalohove.filter { it.id in syncedIds }).forEach {
                    try { repository.insertFaktura(it) } catch (_: Exception) {}
                }
                logEvent("Odesláno ${result.syncedCount} dokladů do SW POHODA mServer")
            }
        }
        return result
    }

    suspend fun sendCustomersToPohoda(customers: List<com.example.data.CustomerRecord>, cfg: PohodaServerConfig? = null): PohodaSyncResult {
        val config = cfg ?: _pohodaServerConfig.value
        val result = PohodaMServerService.sendCustomers(config, customers)
        if (result.success || result.syncedCount > 0) {
            val now = System.currentTimeMillis()
            val syncedIds = customers.map { it.id }.toSet()
            val updatedCustomers = _customers.value.map { c ->
                if (c.id in syncedIds) {
                    c.copy(
                        pohodaSyncedAt = now,
                        pohodaStatus = "SYNCED",
                        pohodaError = ""
                    )
                } else c
            }
            _customers.value = updatedCustomers
            saveLocalCustomers(updatedCustomers)
            viewModelScope.launch {
                updatedCustomers.filter { it.id in syncedIds }.forEach {
                    try { repository.insertCustomer(it) } catch (_: Exception) {}
                }
                logEvent("Odesláno ${result.syncedCount} kontaktů do SW POHODA mServer")
            }
        }
        return result
    }

    suspend fun fetchAddressBookFromPohoda(cfg: PohodaServerConfig? = null): Pair<PohodaSyncResult, Int> {
        val config = cfg ?: _pohodaServerConfig.value
        val (result, remoteList) = PohodaMServerService.fetchAddressBook(config)
        var addedCount = 0
        if (result.success && remoteList.isNotEmpty()) {
            val currentMap = _customers.value.associateBy { it.id }.toMutableMap()
            val icoMap = _customers.value.filter { it.ic.isNotBlank() }.associateBy { it.ic }

            remoteList.forEach { remoteCust ->
                val existingByIco = if (remoteCust.ic.isNotBlank()) icoMap[remoteCust.ic] else null
                val targetId = existingByIco?.id ?: remoteCust.id
                val existing = currentMap[targetId]
                if (existing != null) {
                    currentMap[targetId] = existing.copy(
                        company = remoteCust.company.ifBlank { existing.company },
                        name = remoteCust.name.ifBlank { existing.name },
                        ic = remoteCust.ic.ifBlank { existing.ic },
                        dic = remoteCust.dic.ifBlank { existing.dic },
                        ulice = remoteCust.ulice.ifBlank { existing.ulice },
                        mesto = remoteCust.mesto.ifBlank { existing.mesto },
                        psc = remoteCust.psc.ifBlank { existing.psc },
                        phone = remoteCust.phone.ifBlank { existing.phone },
                        email = remoteCust.email.ifBlank { existing.email },
                        pohodaSyncedAt = System.currentTimeMillis(),
                        pohodaStatus = "SYNCED"
                    )
                } else {
                    currentMap[targetId] = remoteCust
                    addedCount++
                }
            }
            val newList = currentMap.values.sortedByDescending { it.timestamp }
            _customers.value = newList
            saveLocalCustomers(newList)
            viewModelScope.launch {
                newList.filter { it.pohodaStatus == "SYNCED" }.take(50).forEach {
                    try { repository.insertCustomer(it) } catch (_: Exception) {}
                }
                logEvent("Stažen adresář z POHODA mServeru (celkem ${remoteList.size} kontaktů, nově zaevidováno $addedCount)")
            }
        }
        return Pair(result, addedCount)
    }

    // --- mPOHODA CLOUD & POHODA KOMPLET ---
    private val mpohodaApiKeyFile = File(filesDir, "mpohoda_api_key.txt")
    private val mpohodaConfigFile = File(filesDir, "mpohoda_config.json")

    private val _mpohodaConfig = MutableStateFlow(loadInitialMPohodaConfig())
    val mpohodaConfig: StateFlow<MPohodaConfig> = _mpohodaConfig.asStateFlow()

    private val _mpohodaApiKey = MutableStateFlow(
        _mpohodaConfig.value.apiKey.ifBlank { _mpohodaConfig.value.communicationKey }
    )
    val mpohodaApiKey: StateFlow<String> = _mpohodaApiKey.asStateFlow()

    private fun loadInitialMPohodaConfig(): MPohodaConfig {
        if (mpohodaConfigFile.exists()) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(MPohodaConfig::class.java)
                val loaded = adapter.fromJson(mpohodaConfigFile.readText())
                if (loaded != null) return loaded
            } catch (_: Exception) {}
        }
        val legacyKey = if (mpohodaApiKeyFile.exists()) runCatching { mpohodaApiKeyFile.readText().trim() }.getOrDefault("") else ""
        return MPohodaConfig(
            companyGuid = "501ebfc5-fec2-46db-a9a3-365be2848df1",
            communicationKey = "D94ABB55A16E4C0599E3DCC6660E41A4",
            apiKey = legacyKey.ifBlank { "D94ABB55A16E4C0599E3DCC6660E41A4" },
            autoSyncOnSave = true
        )
    }

    fun setMpohodaConfig(config: MPohodaConfig) {
        _mpohodaConfig.value = config
        _mpohodaApiKey.value = config.apiKey.ifBlank { config.communicationKey }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(MPohodaConfig::class.java)
                mpohodaConfigFile.writeText(adapter.toJson(config))
                mpohodaApiKeyFile.writeText(config.apiKey.ifBlank { config.communicationKey })
            } catch (_: Exception) {}
        }
    }

    fun setMpohodaApiKey(key: String) {
        val current = _mpohodaConfig.value
        setMpohodaConfig(current.copy(apiKey = key.trim()))
    }

    fun setMpohodaAutoSyncOnSave(enabled: Boolean) {
        val current = _mpohodaConfig.value
        setMpohodaConfig(current.copy(autoSyncOnSave = enabled))
    }

    suspend fun testMpohodaConnection(apiKey: String? = null, config: MPohodaConfig? = null): MPohodaSyncResponse {
        val effectiveConfig = config ?: (if (apiKey != null) _mpohodaConfig.value.copy(apiKey = apiKey) else _mpohodaConfig.value)
        return MPohodaApiService.testConnection(effectiveConfig)
    }

    suspend fun sendInvoiceToMPohoda(faktura: com.example.data.FakturaRecord): MPohodaSyncResponse {
        val res = MPohodaApiService.sendInvoice(faktura, _mpohodaConfig.value)
        if (res.success) {
            val now = System.currentTimeMillis()
            val isZal = faktura.typDokladu == "ZALOHOVA_FAKTURA"
            val assignedCode = res.code.ifBlank { res.id }.ifBlank { faktura.cislo }
            val updated = faktura.copy(
                pohodaId = assignedCode,
                pohodaSyncedAt = now,
                pohodaStatus = "MPOHODA",
                pohodaError = ""
            )
            if (isZal) {
                val list = _zalohoveFaktury.value.map { if (it.id == faktura.id) updated else it }
                _zalohoveFaktury.value = list
                saveLocalZalohoveFaktury(list)
            } else {
                val list = _faktury.value.map { if (it.id == faktura.id) updated else it }
                _faktury.value = list
                saveLocalFaktury(list)
            }
            viewModelScope.launch {
                try { repository.insertFaktura(updated) } catch (_: Exception) {}
                logEvent("Odeslána ${if (isZal) "zálohová faktura" else "faktura"} do mPOHODA cloudu: ${updated.cislo}")
            }
        }
        return res
    }

    suspend fun sendInvoicesBatchToMPohoda(invoices: List<com.example.data.FakturaRecord>): Pair<Int, Int> {
        var successCount = 0
        var errorCount = 0
        invoices.forEach { f ->
            val res = sendInvoiceToMPohoda(f)
            if (res.success) successCount++ else errorCount++
        }
        return Pair(successCount, errorCount)
    }

    fun createCustomNabidka(
        firma: String,
        jmeno: String,
        telefon: String,
        email: String,
        text: String,
        depo: String,
        ic: String = "",
        dic: String = "",
        ulice: String = "",
        platnost: String = "",
        kodPolozky: String = "KONT",
        mnozstvi: Double = 1.0,
        mj: String = "ks",
        jCena: Double = 0.0,
        dphPct: Double = 21.0,
        sleva: Double = 0.0,
        pdp: Boolean = false,
        selectedCatalogItems: List<com.example.data.NabidkaItemRecord> = emptyList()
    ): com.example.data.NabidkaRecord {
        val newCislo = getNextNabidkaNumber()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val itemsList = if (selectedCatalogItems.isNotEmpty()) {
            selectedCatalogItems
        } else {
            val castkaBez = kotlin.math.round(mnozstvi * jCena * (1.0 - (sleva / 100.0)))
            val dphVal = if (pdp) 0.0 else kotlin.math.round(castkaBez * (dphPct / 100.0))
            listOf(
                com.example.data.NabidkaItemRecord(
                    id = "nitem_${System.currentTimeMillis()}",
                    kod = kodPolozky.ifBlank { "KONT" },
                    text = text.ifBlank { "Kontejner" },
                    mnozstvi = mnozstvi,
                    mj = mj.ifBlank { "ks" },
                    jCena = jCena,
                    dphPct = dphPct,
                    sleva = sleva,
                    pdp = pdp,
                    castkaBez = castkaBez,
                    dphVal = dphVal
                )
            )
        }
        val nabidka = com.example.data.NabidkaRecord(
            id = "nab_${System.currentTimeMillis()}",
            cislo = newCislo,
            depo = depo,
            datum = today,
            platnost = platnost,
            ic = ic,
            dic = dic,
            firma = firma.ifBlank { jmeno },
            jmeno = jmeno,
            telefon = telefon,
            email = email,
            ulice = ulice,
            text = text,
            items = itemsList,
            timestamp = System.currentTimeMillis()
        )
        _nabidky.value = listOf(nabidka) + _nabidky.value
        saveLocalNabidky(_nabidky.value)
        ensureCustomerInAdresar(firma, jmeno, telefon, email, ic, dic, ulice, depo)
        viewModelScope.launch {
            try { repository.insertNabidka(nabidka, text) } catch (_: Exception) {}
        }
        return nabidka
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun buildPohodaOffersXml(nabidkyList: List<com.example.data.NabidkaRecord>, customIco: String = ""): String {
        val resolvedIco = customIco.ifBlank { _pohodaIco.value }.ifBlank { "25388720" }
        val packId = "CN_${System.currentTimeMillis()}"
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        val itemsXml = nabidkyList.mapIndexed { idx, n ->
            val cislo = n.cislo.ifBlank { "CN${idx + 1}" }
            val datum = n.datum.ifBlank { today }
            val depo = n.depo.ifBlank { "Depo 1" }
            val headerText = n.text.ifBlank { "Cenová nabídka $cislo - ${n.firma.ifBlank { n.jmeno }}" }

            val parts = n.ulice.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val street = parts.firstOrNull() ?: n.ulice
            val city = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

            val effectiveItems = n.items.ifEmpty {
                listOf(
                    com.example.data.NabidkaItemRecord(
                        id = "nitem_1",
                        kod = "KONT",
                        text = headerText,
                        mnozstvi = 1.0,
                        mj = "ks",
                        jCena = 0.0,
                        dphPct = 21.0,
                        sleva = 0.0,
                        pdp = false,
                        castkaBez = 0.0,
                        dphVal = 0.0
                    )
                )
            }

            val detailXml = effectiveItems.joinToString("\n") { item ->
                val vatRate = when {
                    item.dphPct <= 0.0 -> "none"
                    item.dphPct <= 15.0 -> "low"
                    else -> "high"
                }
                val priceBez = if (item.castkaBez != 0.0) item.castkaBez else kotlin.math.round(item.mnozstvi * item.jCena * (1.0 - item.sleva / 100.0))
                val priceVat = if (item.pdp) 0.0 else if (item.dphVal != 0.0) item.dphVal else kotlin.math.round(priceBez * (item.dphPct / 100.0))
                val priceSum = priceBez + priceVat
                buildString {
                    appendLine("        <ofr:offerItem>")
                    if (item.kod.isNotBlank()) appendLine("          <ofr:code>${escapeXml(item.kod)}</ofr:code>")
                    appendLine("          <ofr:text>${escapeXml(item.text.ifBlank { "Položka nabídky" })}</ofr:text>")
                    appendLine("          <ofr:quantity>${item.mnozstvi}</ofr:quantity>")
                    appendLine("          <ofr:unit>${escapeXml(item.mj.ifBlank { "ks" }.take(10))}</ofr:unit>")
                    appendLine("          <ofr:payVAT>false</ofr:payVAT>")
                    appendLine("          <ofr:rateVAT>$vatRate</ofr:rateVAT>")
                    if (item.sleva > 0.0) appendLine("          <ofr:discountPercentage>${item.sleva}</ofr:discountPercentage>")
                    appendLine("          <ofr:homeCurrency>")
                    appendLine("            <typ:unitPrice>${String.format(java.util.Locale.US, "%.2f", item.jCena)}</typ:unitPrice>")
                    appendLine("            <typ:price>${String.format(java.util.Locale.US, "%.2f", priceBez)}</typ:price>")
                    appendLine("            <typ:priceVAT>${String.format(java.util.Locale.US, "%.2f", priceVat)}</typ:priceVAT>")
                    appendLine("            <typ:priceSum>${String.format(java.util.Locale.US, "%.2f", priceSum)}</typ:priceSum>")
                    appendLine("          </ofr:homeCurrency>")
                    if (item.pdp) appendLine("          <ofr:PDP>true</ofr:PDP>")
                    append("        </ofr:offerItem>")
                }
            }

            buildString {
                appendLine("  <dat:dataPackItem id=\"${escapeXml(cislo)}\" version=\"2.0\">")
                appendLine("    <ofr:offer version=\"2.0\">")
                appendLine("      <ofr:offerHeader>")
                appendLine("        <ofr:offerType>issuedOffer</ofr:offerType>")
                appendLine("        <ofr:number>")
                appendLine("          <typ:numberRequested>${escapeXml(cislo)}</typ:numberRequested>")
                appendLine("        </ofr:number>")
                appendLine("        <ofr:date>${escapeXml(datum)}</ofr:date>")
                if (n.platnost.isNotBlank()) appendLine("        <ofr:validTill>${escapeXml(n.platnost)}</ofr:validTill>")
                appendLine("        <ofr:text>${escapeXml(headerText.take(240))}</ofr:text>")
                appendLine("        <ofr:partnerIdentity>")
                appendLine("          <typ:address>")
                if (n.firma.isNotBlank()) appendLine("            <typ:company>${escapeXml(n.firma.take(96))}</typ:company>")
                if (n.jmeno.isNotBlank()) appendLine("            <typ:name>${escapeXml(n.jmeno.take(32))}</typ:name>")
                if (city.isNotBlank()) appendLine("            <typ:city>${escapeXml(city.take(45))}</typ:city>")
                if (street.isNotBlank()) appendLine("            <typ:street>${escapeXml(street.take(64))}</typ:street>")
                if (n.ic.isNotBlank()) appendLine("            <typ:ico>${escapeXml(n.ic.replace(" ", "").take(15))}</typ:ico>")
                if (n.dic.isNotBlank()) appendLine("            <typ:dic>${escapeXml(n.dic.replace(" ", "").take(18))}</typ:dic>")
                if (n.telefon.isNotBlank()) appendLine("            <typ:phone>${escapeXml(n.telefon.take(40))}</typ:phone>")
                if (n.email.isNotBlank()) appendLine("            <typ:email>${escapeXml(n.email.take(98))}</typ:email>")
                appendLine("          </typ:address>")
                appendLine("        </ofr:partnerIdentity>")
                appendLine("        <ofr:centre>")
                appendLine("          <typ:ids>${escapeXml(depo)}</typ:ids>")
                appendLine("        </ofr:centre>")
                appendLine("        <ofr:note>${escapeXml(n.text)}</ofr:note>")
                appendLine("        <ofr:isExecuted>false</ofr:isExecuted>")
                appendLine("      </ofr:offerHeader>")
                appendLine("      <ofr:offerDetail>")
                appendLine(detailXml)
                appendLine("      </ofr:offerDetail>")
                appendLine("      <ofr:offerSummary>")
                appendLine("        <ofr:roundingDocument>math2one</ofr:roundingDocument>")
                appendLine("      </ofr:offerSummary>")
                appendLine("    </ofr:offer>")
                append("  </dat:dataPackItem>")
            }
        }.joinToString("\n")

        return """<?xml version="1.0" encoding="UTF-8"?>
<dat:dataPack id="${escapeXml(packId)}" ico="${escapeXml(resolvedIco)}" application="SpravaKontejneru" version="2.0" note="Import Cenovych nabidek (CN) do SW POHODA"
  xmlns:dat="http://www.stormware.cz/schema/version_2/data.xsd"
  xmlns:ofr="http://www.stormware.cz/schema/version_2/offer.xsd"
  xmlns:typ="http://www.stormware.cz/schema/version_2/type.xsd">
$itemsXml
</dat:dataPack>"""
    }

    fun createCustomZakazka(
        firma: String,
        vyrizuje: String,
        telefon: String,
        email: String,
        lokalita: String,
        depo: String,
        ic: String = "",
        dic: String = "",
        ulice: String = "",
        cisloNabidky: String = "",
        zdrojNabidkaId: String = "",
        adresarId: String = "",
        selectedCatalogItems: List<com.example.data.NabidkaItemRecord> = emptyList()
    ): com.example.data.ZakazkaRecord {
        return saveFullZakazka(
            cisloNabidky = cisloNabidky,
            firma = firma,
            vyrizuje = vyrizuje,
            telefon = telefon,
            email = email,
            ic = ic,
            dic = dic,
            ulice = ulice,
            lokalita = lokalita,
            depo = depo,
            adresarId = adresarId,
            zdrojNabidkaId = zdrojNabidkaId,
            items = selectedCatalogItems
        )
    }

    fun deleteNabidka(id: String) {
        markIdDeleted(id)
        _nabidky.value = _nabidky.value.filter { it.id != id }
        saveLocalNabidky(_nabidky.value)
        viewModelScope.launch {
            try { repository.deleteNabidka(id) } catch (_: Exception) {}
        }
    }

    fun deleteZakazka(id: String) {
        markIdDeleted(id)
        _zakazky.value = _zakazky.value.filter { it.id != id }
        saveLocalZakazky(_zakazky.value)
        viewModelScope.launch {
            try { repository.deleteZakazka(id) } catch (_: Exception) {}
        }
    }

    fun deleteCard(card: ContainerCard) {
        deleteContainer(card)
    }

    private fun parseAresSubjectJson(obj: org.json.JSONObject): AresSubjectResult? {
        val ico = obj.optString("ico", "").trim()
        val company = obj.optString("obchodniJmeno", "").trim()
        if (ico.isBlank() && company.isBlank()) return null
        val dicRaw = obj.optString("dic", "").trim()
        val dic = if (dicRaw.isNotBlank() && dicRaw != "null") dicRaw else if (ico.isNotBlank()) "CZ$ico" else ""
        val sidlo = obj.optJSONObject("sidlo")
        val address = if (sidlo != null) {
            val textova = sidlo.optString("textovaAdresa", "").trim()
            if (textova.isNotBlank() && textova != "null") {
                textova
            } else {
                val ulice = sidlo.optString("nazevUlice", "").ifBlank { sidlo.optString("nazevCastiObce", "") }
                val cd = sidlo.optInt("cisloDomovni", 0)
                val co = sidlo.optInt("cisloOrientacni", 0)
                val cisloStr = when {
                    cd > 0 && co > 0 -> "$cd/$co"
                    cd > 0 -> "$cd"
                    else -> ""
                }
                val psc = sidlo.optInt("psc", 0).let { if (it > 0) it.toString() else "" }
                val obec = sidlo.optString("nazevObce", "")
                listOf(
                    listOf(ulice, cisloStr).filter { it.isNotBlank() }.joinToString(" "),
                    listOf(psc, obec).filter { it.isNotBlank() }.joinToString(" ")
                ).filter { it.isNotBlank() }.joinToString(", ")
            }
        } else ""
        val reg = obj.optJSONObject("seznamRegistraci")
        val irStav = reg?.optString("stavZdrojeIr", "") ?: ""
        val dphStav = reg?.optString("stavZdrojeDph", "") ?: ""
        val zanik = obj.optString("datumZaniku", "").trim()
        val vznik = obj.optString("datumVzniku", "").trim()
        val (bonita, bonitaDetail) = when {
            zanik.isNotBlank() && zanik != "null" -> "CCC" to "Subjekt v zániku / likvidaci ($zanik) • Riziko exekuce"
            irStav.equals("AKTIVNI", ignoreCase = true) -> "CCC" to "Záznam v Insolvenčním rejstříku (ISIR) • Před exekucí / insolvence"
            dphStav.equals("AKTIVNI", ignoreCase = true) -> "AAA" to "Aktivní plátce DPH u FÚ • Bez záznamu v ISIR a exekucích • Výborná platební morálka"
            vznik.isNotBlank() && vznik.take(4).toIntOrNull()?.let { it <= 2022 } == true -> "AAA" to "Stabilní firma (od $vznik) • Bez insolvence a dluhů u FÚ"
            else -> "AA" to "Aktivní subjekt v ARES • Bez záznamu v insolvenčním rejstříku"
        }
        return AresSubjectResult(
            ico = ico,
            dic = dic,
            company = company,
            address = address,
            bonita = bonita,
            bonitaDetail = bonitaDetail
        )
    }

    fun getCustomerFullEngagementEvents(customer: com.example.data.CustomerRecord): List<com.example.data.CustomerEventRecord> {
        val result = mutableListOf<com.example.data.CustomerEventRecord>()
        val seenKeys = HashSet<String>()
        val custIc = customer.ic.trim()
        val custCompany = customer.company.trim().lowercase()
        val custName = customer.name.trim().lowercase()

        fun matchesCompany(idRef: String, icVal: String, compVal: String, nameVal: String): Boolean {
            if (idRef.isNotBlank() && idRef == customer.id) return true
            if (custIc.isNotBlank() && icVal.trim().equals(custIc, ignoreCase = true)) return true
            val cLow = compVal.trim().lowercase()
            if (custCompany.isNotBlank() && cLow.isNotBlank() && (cLow == custCompany || cLow.contains(custCompany) || custCompany.contains(cLow))) return true
            val nLow = nameVal.trim().lowercase()
            if (custCompany.isBlank() && custName.isNotBlank() && nLow.isNotBlank() && nLow == custName) return true
            return false
        }

        // 1. Zakázky
        _zakazky.value.forEach { z ->
            if (matchesCompany(z.adresarId, z.ic, z.firma, z.vyrizuje)) {
                val sum = z.items.sumOf { it.castkaBez + it.dphVal }
                val dt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(z.timestamp))
                val key = "ZAK_${z.cisloZakazky.ifBlank { z.id }}"
                seenKeys.add(key)
                result.add(
                    com.example.data.CustomerEventRecord(
                        id = "auto_zak_${z.id}",
                        datum = dt,
                        typ = "ZAKÁZKA",
                        cisloDokladu = z.cisloZakazky,
                        popis = "Středisko: ${z.stredisko} • ${z.lokalita.ifBlank { "Zakázka" }} (${z.items.size} pol.)",
                        castka = sum,
                        timestamp = z.timestamp
                    )
                )
            }
        }

        // 2. Cenové nabídky (CN)
        _nabidky.value.forEach { n ->
            if (matchesCompany(n.adresarId, n.ic, n.firma, n.jmeno)) {
                val sum = n.items.sumOf { it.castkaBez + it.dphVal }
                val dt = n.datum.ifBlank { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(n.timestamp)) }
                val key = "CN_${n.cislo.ifBlank { n.id }}"
                seenKeys.add(key)
                result.add(
                    com.example.data.CustomerEventRecord(
                        id = "auto_cn_${n.id}",
                        datum = dt,
                        typ = "CN",
                        cisloDokladu = n.cislo,
                        popis = "${n.text.ifBlank { "Cenová nabídka" }} (${n.items.size} pol.)",
                        castka = sum,
                        timestamp = n.timestamp
                    )
                )
            }
        }

        // 3. Objednávky / Poptávky
        _customers.value.filter { it.isOrder }.forEach { ord ->
            if (ord.id == customer.id || matchesCompany("", ord.ic, ord.company, ord.name)) {
                if (ord.inquiryType.isNotBlank() || ord.message.isNotBlank()) {
                    val dt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(ord.timestamp))
                    result.add(
                        com.example.data.CustomerEventRecord(
                            id = "auto_ord_${ord.id}",
                            datum = dt,
                            typ = "OBJEDNÁVKA",
                            cisloDokladu = ord.cisloZakaznika.ifBlank { "POPTÁVKA" },
                            popis = "${ord.inquiryType}: ${ord.message} (${ord.status})",
                            castka = 0.0,
                            timestamp = ord.timestamp
                        )
                    )
                }
            }
        }

        // 4. Faktury / Prodeje / Pronájmy kontejnerů
        (containers.value + archive.value + pronajem.value).distinctBy { it.id }.forEach { cont ->
            val contComp = cont.customerCompany.ifBlank { cont.soldCustomerName }.ifBlank { cont.reservedName }
            if (contComp.isNotBlank() && matchesCompany(cont.soldCustomerId, "", contComp, contComp)) {
                val dt = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(cont.sellDate ?: cont.lastModified))
                val typ = if (cont.status.contains("PLATBA") || cont.status.contains("PRODEJ") || cont.status.contains("PRODÁNO")) "FAKTURA" else "KONTEJNER"
                result.add(
                    com.example.data.CustomerEventRecord(
                        id = "auto_cont_${cont.id}",
                        datum = dt,
                        typ = typ,
                        cisloDokladu = cont.designation,
                        popis = "Kontejner ${cont.designation} (${cont.type}) – Stav: ${cont.status} (${cont.depo})",
                        castka = cont.price.toDouble(),
                        timestamp = cont.sellDate ?: cont.lastModified
                    )
                )
            }
        }

        // 5. Přepravy
        transports.value.forEach { tr ->
            if (tr.company.isNotBlank() && matchesCompany("", "", tr.company, tr.driver)) {
                val dt = tr.dateText.ifBlank { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(tr.timestamp)) }
                result.add(
                    com.example.data.CustomerEventRecord(
                        id = "auto_tr_${tr.id}",
                        datum = dt,
                        typ = "PŘEPRAVA",
                        cisloDokladu = tr.designation.ifBlank { tr.spz },
                        popis = "Přeprava ${tr.containerType} • SPZ: ${tr.spz} • ${tr.note}",
                        castka = 0.0,
                        timestamp = tr.timestamp
                    )
                )
            }
        }

        // 5b. Vystavené Faktury a Zálohové faktury
        (_faktury.value + _zalohoveFaktury.value).forEach { fa ->
            if (matchesCompany(fa.adresarId, fa.ic, fa.firma, fa.jmeno)) {
                val sum = fa.items.sumOf { it.castkaBez + it.dphVal }
                val dt = fa.datumVystaveni.ifBlank { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(fa.timestamp)) }
                val typLabel = if (fa.typDokladu == "ZALOHOVA_FAKTURA") "ZÁLOHOVÁ FA" else "FAKTURA"
                val ucelLabel = if (fa.ucelFaktury == "PRONAJEM") "Pronájem" else "Prodej"
                val key = "${typLabel}_${fa.cislo.ifBlank { fa.id }}"
                seenKeys.add(key)
                seenKeys.add("FAKTURA_${fa.cislo}")
                result.add(
                    com.example.data.CustomerEventRecord(
                        id = "auto_fa_${fa.id}",
                        datum = dt,
                        typ = typLabel,
                        cisloDokladu = fa.cislo,
                        popis = "$ucelLabel kontejneru ${fa.oznaceniKontejneru} • ${fa.text} (${fa.stavUhrady})",
                        castka = sum,
                        timestamp = fa.timestamp
                    )
                )
            }
        }

        // 6. Ručně zapsané události / faktury na kartě zákazníka
        customer.udalosti.forEach { ev ->
            val key = "${ev.typ}_${ev.cisloDokladu}"
            if (ev.cisloDokladu.isBlank() || key !in seenKeys) {
                result.add(ev)
            }
        }

        return result.sortedByDescending { it.timestamp }
    }

    fun addManualCustomerEvent(
        customerId: String,
        typ: String,
        cisloDokladu: String,
        popis: String,
        castka: Double
    ) {
        val existing = _customers.value.find { it.id == customerId } ?: return
        val today = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val newEv = com.example.data.CustomerEventRecord(
            id = "ev_${System.currentTimeMillis()}",
            datum = today,
            typ = typ.ifBlank { "FAKTURA" },
            cisloDokladu = cisloDokladu.trim(),
            popis = popis.trim(),
            castka = castka,
            timestamp = System.currentTimeMillis()
        )
        val updated = existing.copy(
            udalosti = listOf(newEv) + existing.udalosti,
            timestamp = System.currentTimeMillis()
        )
        _customers.value = _customers.value.map { if (it.id == customerId) updated else it }
        saveLocalCustomers(_customers.value)
        viewModelScope.launch {
            try { repository.insertCustomer(updated) } catch (_: Exception) {}
        }
    }

    fun updateCustomerBonita(customerId: String, bonita: String, bonitaDetail: String) {
        val existing = _customers.value.find { it.id == customerId } ?: return
        val updated = existing.copy(
            bonita = bonita.ifBlank { "AAA" },
            bonitaDetail = bonitaDetail,
            timestamp = System.currentTimeMillis()
        )
        _customers.value = _customers.value.map { if (it.id == customerId) updated else it }
        saveLocalCustomers(_customers.value)
        viewModelScope.launch {
            try { repository.insertCustomer(updated) } catch (_: Exception) {}
        }
    }

    fun createPrepravaFromZakazka(
        zakazka: com.example.data.ZakazkaRecord,
        spz: String,
        driver: String,
        dateText: String,
        note: String,
        transportCatalogItem: com.example.data.CatalogItemRecord? = null
    ) {
        val now = System.currentTimeMillis()
        val trRecord = com.example.data.TransportRecord(
            id = "tr_$now",
            timestamp = now,
            status = "čeká",
            designation = zakazka.cisloZakazky,
            dateText = dateText.ifBlank { java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(now)) },
            company = zakazka.firma.ifBlank { zakazka.vyrizuje },
            spz = spz,
            driver = driver,
            containerType = zakazka.stredisko,
            note = "Zakázka ${zakazka.cisloZakazky} (${zakazka.lokalita}) – $note".trim(' ', '–'),
            depo = zakazka.depo,
            customerName = zakazka.vyrizuje,
            customerCompany = zakazka.firma,
            customerIc = zakazka.ic,
            customerDic = zakazka.dic,
            customerAddress = zakazka.lokalita.ifBlank { zakazka.ulice },
            customerPhone = zakazka.telefon,
            customerEmail = zakazka.email,
            customerInitials = com.example.data.computeCustomerInitials(zakazka.vyrizuje, zakazka.firma),
            customerSource = "ZAKÁZKA",
            sourceZakazkaId = zakazka.id,
            cisloDokladuZdroj = zakazka.cisloZakazky
        )
        saveTransport(trRecord)
        val updatedItems = if (transportCatalogItem != null) {
            zakazka.items + catalogItemToNabidkaItem(transportCatalogItem)
        } else {
            zakazka.items
        }
        val infoSummary = "Přeprava: ${trRecord.dateText} • ${spz.ifBlank { "Doprava" }} • ${note.ifBlank { "Objednáno" }}"
        saveFullZakazka(
            id = zakazka.id,
            cisloZakazky = zakazka.cisloZakazky,
            cisloNabidky = zakazka.cisloNabidky,
            stredisko = zakazka.stredisko,
            prepravaInfo = infoSummary,
            firma = zakazka.firma,
            vyrizuje = zakazka.vyrizuje,
            telefon = zakazka.telefon,
            email = zakazka.email,
            ic = zakazka.ic,
            dic = zakazka.dic,
            ulice = zakazka.ulice,
            lokalita = zakazka.lokalita,
            splatnost = zakazka.splatnost,
            depo = zakazka.depo,
            adresarId = zakazka.adresarId,
            zdrojNabidkaId = zakazka.zdrojNabidkaId,
            items = updatedItems
        )
    }

    fun lookupAresSubjects(
        query: String,
        onResult: (List<AresSubjectResult>) -> Unit,
        onError: (String) -> Unit
    ) {
        val q = query.trim()
        if (q.isBlank()) {
            onError("Zadejte IČ nebo název firmy pro vyhledání v ARES.")
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val results = mutableListOf<AresSubjectResult>()
            try {
                val digitsOnly = q.replace(" ", "")
                val isIcoQuery = digitsOnly.all { it.isDigit() } && digitsOnly.length in 6..8
                if (isIcoQuery) {
                    val paddedIco = digitsOnly.padStart(8, '0')
                    val url = java.net.URL("https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/ekonomicke-subjekty/$paddedIco")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val parsed = parseAresSubjectJson(org.json.JSONObject(body))
                        if (parsed != null) results.add(parsed)
                    }
                    conn.disconnect()
                } else {
                    val url = java.net.URL("https://ares.gov.cz/ekonomicke-subjekty-v-be/rest/ekonomicke-subjekty/vyhledat")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Accept", "application/json")
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    val reqJson = org.json.JSONObject().apply {
                        put("obchodniJmeno", q)
                        put("start", 0)
                        put("pocet", 8)
                    }.toString()
                    conn.outputStream.use { it.write(reqJson.toByteArray(Charsets.UTF_8)) }
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val root = org.json.JSONObject(body)
                        val arr = root.optJSONArray("ekonomickeSubjekty")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val itemObj = arr.optJSONObject(i) ?: continue
                                val parsed = parseAresSubjectJson(itemObj)
                                if (parsed != null) results.add(parsed)
                            }
                        }
                    }
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w("ARES", "Online ARES dotaz selhal, zkouším lokální registr: ${e.message}")
            }

            if (results.isEmpty()) {
                val fallbackDirectory = listOf(
                    AresSubjectResult("27771164", "CZ27771164", "Kontejnery Ostrava s.r.o.", "Pavlovova 2701/50, 700 30 Ostrava - Jih"),
                    AresSubjectResult("25360735", "CZ25360735", "STORMWARE s.r.o.", "Za Prachárnou 4962/45, 586 01 Jihlava"),
                    AresSubjectResult("00299308", "CZ00299308", "Statutární město Ostrava", "Prokešovo náměstí 1803/8, 729 30 Ostrava"),
                    AresSubjectResult("45193680", "CZ45193680", "Metrostav a.s.", "Koželužská 2450/4, 180 00 Praha 8"),
                    AresSubjectResult("26274548", "CZ26274548", "STRABAG a.s.", "Kačírkova 982/4, 158 00 Praha 5"),
                    AresSubjectResult("25834150", "CZ25834150", "VÍTKOVICE, a.s.", "Ruská 2887/101, 703 00 Ostrava - Vítkovice")
                ) + _customers.value.mapNotNull { c ->
                    if (c.company.isNotBlank() || c.ic.isNotBlank()) {
                        AresSubjectResult(
                            ico = c.ic,
                            dic = c.dic.ifBlank { if (c.ic.isNotBlank()) "CZ${c.ic}" else "" },
                            company = c.company.ifBlank { c.name },
                            address = c.ulice
                        )
                    } else null
                }
                val qLow = q.lowercase()
                results.addAll(
                    fallbackDirectory.filter {
                        it.ico.contains(qLow) || it.company.lowercase().contains(qLow)
                    }.distinctBy { "${it.ico}_${it.company}" }
                )
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (results.isNotEmpty()) {
                    onResult(results)
                } else {
                    onError("V registru ARES nebyl nalezen subjekt pro '$q'.")
                }
            }
        }
    }

    fun createManualContainer(
        designation: String,
        depo: String,
        type: String,
        status: String,
        price: Long,
        vlastnik: String = "",
        customerCompany: String = "",
        poznamka: String = "",
        description: String = "",
        color: String = "",
        spz: String = "",
        imageUri: String? = null,
        extraImages: String = "",
        photoTimestamp: Long? = null
    ): ContainerCard {
        val now = System.currentTimeMillis()
        val acquiredAt = photoTimestamp ?: now
        val finalDepo = depo.ifBlank { _currentDepo.value ?: "Depo 1" }
        val sysNum = getNextContainerSystemNumber(finalDepo)
        val finalCustomer = customerCompany.ifBlank { vlastnik }.trim()
        val finalDescription = description.ifBlank { poznamka }.trim()
        val finalExtras = extraImages.ifBlank { imageUri ?: "" }
        val existingDup = findDuplicateContainer(designation)
        if (existingDup != null) {
            val updatedExisting = existingDup.copy(
                type = type.ifBlank { existingDup.type },
                status = status.ifBlank { existingDup.status },
                depo = finalDepo,
                vlastnik = vlastnik.ifBlank { existingDup.vlastnik },
                customerCompany = finalCustomer.ifBlank { existingDup.customerCompany },
                price = if (price > 0L) price else existingDup.price,
                poznamka = poznamka.ifBlank { existingDup.poznamka },
                description = finalDescription.ifBlank { existingDup.description },
                color = color.ifBlank { existingDup.color },
                spz = spz.ifBlank { existingDup.spz },
                imageUri = imageUri ?: existingDup.imageUri,
                extraImages = finalExtras.ifBlank { existingDup.extraImages },
                lastModified = now
            )
            saveContainer(updatedExisting)
            return updatedExisting
        }
        val card = ContainerCard(
            id = UUID.randomUUID().toString(),
            designation = designation.trim().ifBlank { "XXX ${sysNum.toString().padStart(2, '0')}" },
            depo = finalDepo,
            type = type.trim().ifBlank { "OB" },
            status = status.ifBlank { if (finalDepo == "Depo 2") "DEPO 02" else "DEPO 01" },
            price = price,
            vlastnik = vlastnik.trim(),
            customerCompany = finalCustomer,
            reservedName = finalCustomer,
            poznamka = poznamka.trim(),
            description = finalDescription,
            color = color.trim(),
            spz = spz.trim(),
            imageUri = imageUri,
            extraImages = finalExtras,
            timestamp = acquiredAt,
            arrivalDate = acquiredAt,
            lastModified = now,
            systemNumber = sysNum
        )
        _localContainers.value = (_localContainers.value.filter { it.id != card.id } + card)
        saveLocalContainers(_localContainers.value)
        viewModelScope.launch {
            repository.insert(card)
            logEvent("Založena karta kontejneru (#$sysNum): ${card.designation}")
        }
        return card
    }

    private fun addServerLog(message: String) {
        val current = _serverLogs.value.toMutableList()
        current.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $message")
        if (current.size > 50) current.removeAt(0)
        _serverLogs.value = current
    }
    private fun autoArchiveSoldContainers() {
        viewModelScope.launch {
            try {
                val allContainers = repository.getAllContainersDirect()
                val now = System.currentTimeMillis()
                val fourteenDays = 14L * 24 * 60 * 60 * 1000L
                // Purge demo Hyundai containers permanently as requested
                val hyundaiContainers = allContainers.filter {
                    it.id == "kont_hyundai_8x3m" || it.id == "hyundai_8x3m" ||
                    it.designation.contains("Hyundai", ignoreCase = true)
                }
                hyundaiContainers.forEach {
                    try { repository.delete(it) } catch (_: Exception) {}
                }

                allContainers.forEach { container ->
                    val healed = repository.healContainerCard(container)
                    if (healed.imageUri != container.imageUri || healed.extraImages != container.extraImages) {
                        repository.update(healed)
                    }
                    if ((container.status == "PRODEJ" || container.status == "PRODÁNO") && container.sellDate != null) {
                        if (now - container.sellDate > fourteenDays) {
                            val archived = healed.copy(status = "ARCHIV")
                            repository.update(archived)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- FAKTURACE A ZÁLOHOVÉ FAKTURY (PRODEJ / PRONÁJEM KONTEJNERU + SW POHODA) ---

    private fun mergeFakturyByTimestamp(
        local: List<com.example.data.FakturaRecord>,
        remote: List<com.example.data.FakturaRecord>
    ): List<com.example.data.FakturaRecord> {
        val map = LinkedHashMap<String, com.example.data.FakturaRecord>()
        local.forEach { if (it.id.isNotBlank() && it.id !in deletedIds) map[it.id] = it }
        remote.forEach { rem ->
            if (rem.id.isNotBlank() && rem.id !in deletedIds) {
                val ex = map[rem.id]
                if (ex == null || rem.timestamp >= ex.timestamp) {
                    map[rem.id] = rem
                }
            }
        }
        return map.values.sortedByDescending { it.timestamp }
    }

    private fun saveLocalFaktury(list: List<com.example.data.FakturaRecord>) {
        try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.FakturaRecord::class.java)
            val adapter = moshi.adapter<List<com.example.data.FakturaRecord>>(type)
            fakturyFile.writeText(adapter.toJson(list))
        } catch (_: Exception) {}
    }

    private fun saveLocalZalohoveFaktury(list: List<com.example.data.FakturaRecord>) {
        try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.FakturaRecord::class.java)
            val adapter = moshi.adapter<List<com.example.data.FakturaRecord>>(type)
            zalohoveFakturyFile.writeText(adapter.toJson(list))
        } catch (_: Exception) {}
    }

    fun getNextFakturaNumber(isZalohova: Boolean): String {
        val prefix = if (isZalohova) "26ZF" else "26FA"
        val list = if (isZalohova) _zalohoveFaktury.value else _faktury.value
        val maxNum = list.mapNotNull { rec ->
            val digits = rec.cislo.removePrefix(prefix).filter { it.isDigit() }
            digits.toIntOrNull()
        }.maxOrNull() ?: 0
        return prefix + (maxNum + 1).toString().padStart(4, '0')
    }

    fun saveFaktura(faktura: com.example.data.FakturaRecord) {
        val isZal = faktura.typDokladu == "ZALOHOVA_FAKTURA"
        val now = System.currentTimeMillis()
        val finalId = faktura.id.ifBlank {
            if (isZal) "zf_${now}_${(100..999).random()}" else "fa_${now}_${(100..999).random()}"
        }
        val finalCislo = faktura.cislo.ifBlank { getNextFakturaNumber(isZal) }
        val finalVs = faktura.variabilniSymbol.ifBlank { finalCislo.filter { it.isDigit() } }
        val updated = faktura.copy(
            id = finalId,
            cislo = finalCislo,
            variabilniSymbol = finalVs,
            timestamp = now
        )
        if (isZal) {
            val list = _zalohoveFaktury.value.toMutableList()
            val idx = list.indexOfFirst { it.id == finalId }
            if (idx >= 0) list[idx] = updated else list.add(0, updated)
            _zalohoveFaktury.value = list
            saveLocalZalohoveFaktury(list)
        } else {
            val list = _faktury.value.toMutableList()
            val idx = list.indexOfFirst { it.id == finalId }
            if (idx >= 0) list[idx] = updated else list.add(0, updated)
            _faktury.value = list
            saveLocalFaktury(list)
        }
        viewModelScope.launch {
            try {
                repository.insertFaktura(updated)
                logEvent("Uložena ${if (isZal) "zálohová faktura" else "faktura"}: ${updated.cislo} (${updated.firma})")
                // Automatické odeslání do mPohody při uložení
                if (_mpohodaConfig.value.autoSyncOnSave &&
                    (_mpohodaConfig.value.apiKey.isNotBlank() || _mpohodaConfig.value.communicationKey.isNotBlank())
                ) {
                    sendInvoiceToMPohoda(updated)
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteFaktura(id: String, isZalohova: Boolean) {
        if (id.isBlank()) return
        markIdDeleted(id)
        if (isZalohova) {
            _zalohoveFaktury.value = _zalohoveFaktury.value.filter { it.id != id }
            saveLocalZalohoveFaktury(_zalohoveFaktury.value)
        } else {
            _faktury.value = _faktury.value.filter { it.id != id }
            saveLocalFaktury(_faktury.value)
        }
        viewModelScope.launch {
            try { repository.deleteFaktura(id, isZalohova) } catch (_: Exception) {}
        }
    }

    fun createFakturaFromNabidka(
        nabidka: com.example.data.NabidkaRecord,
        ucelFaktury: String = "PRODEJ",
        isZalohova: Boolean = false
    ): com.example.data.FakturaRecord {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val today = sdf.format(java.util.Date())
        val due = sdf.format(java.util.Date(System.currentTimeMillis() + 14L * 86400000L))
        val cislo = getNextFakturaNumber(isZalohova)
        val ucel = if (ucelFaktury.uppercase().contains("PRON")) "PRONAJEM" else "PRODEJ"
        val items = if (nabidka.items.isNotEmpty()) {
            nabidka.items
        } else {
            listOf(
                com.example.data.NabidkaItemRecord(
                    id = "fi_${System.currentTimeMillis()}",
                    kod = if (ucel == "PRONAJEM") "PRON" else "PRODEJ",
                    text = nabidka.text.ifBlank { if (ucel == "PRONAJEM") "Pronájem kontejneru" else "Prodej kontejneru" },
                    mnozstvi = 1.0,
                    mj = if (ucel == "PRONAJEM") "měs." else "ks",
                    jCena = 0.0,
                    dphPct = 21.0
                )
            )
        }
        val rec = com.example.data.FakturaRecord(
            id = "${if (isZalohova) "zf" else "fa"}_${System.currentTimeMillis()}",
            cislo = cislo,
            variabilniSymbol = cislo.filter { it.isDigit() },
            typDokladu = if (isZalohova) "ZALOHOVA_FAKTURA" else "FAKTURA",
            ucelFaktury = ucel,
            depo = nabidka.depo.ifBlank { _currentDepo.value ?: "Depo 1" },
            stredisko = if (ucel == "PRONAJEM") "Pronájem" else "Prodej",
            datumVystaveni = today,
            datumZdanitelnehoPlneni = today,
            datumSplatnosti = due,
            ic = nabidka.ic,
            dic = nabidka.dic,
            firma = nabidka.firma,
            jmeno = nabidka.jmeno,
            ulice = nabidka.ulice,
            telefon = nabidka.telefon,
            email = nabidka.email,
            cisloNabidky = nabidka.cislo,
            zdrojNabidkaId = nabidka.id,
            adresarId = nabidka.adresarId,
            text = "${if (ucel == "PRONAJEM") "Fakturace pronájmu kontejneru" else "Fakturace prodeje kontejneru"} dle CN ${nabidka.cislo}",
            items = items,
            timestamp = System.currentTimeMillis()
        )
        saveFaktura(rec)
        return rec
    }

    fun createFakturaFromZakazka(
        zakazka: com.example.data.ZakazkaRecord,
        ucelFaktury: String = "",
        isZalohova: Boolean = false
    ): com.example.data.FakturaRecord {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val today = sdf.format(java.util.Date())
        val splDays = zakazka.splatnost.toLongOrNull() ?: 14L
        val due = sdf.format(java.util.Date(System.currentTimeMillis() + splDays * 86400000L))
        val cislo = getNextFakturaNumber(isZalohova)
        val effectiveUcel = when {
            ucelFaktury.isNotBlank() -> if (ucelFaktury.uppercase().contains("PRON")) "PRONAJEM" else "PRODEJ"
            zakazka.stredisko.contains("Prodej", ignoreCase = true) -> "PRODEJ"
            else -> "PRONAJEM"
        }
        val items = if (zakazka.items.isNotEmpty()) {
            zakazka.items
        } else {
            listOf(
                com.example.data.NabidkaItemRecord(
                    id = "fi_${System.currentTimeMillis()}",
                    kod = if (effectiveUcel == "PRONAJEM") "PRON" else "PRODEJ",
                    text = if (effectiveUcel == "PRONAJEM") "Pronájem kontejneru dle zakázky ${zakazka.cisloZakazky}" else "Prodej kontejneru dle zakázky ${zakazka.cisloZakazky}",
                    mnozstvi = 1.0,
                    mj = if (effectiveUcel == "PRONAJEM") "měs." else "ks",
                    jCena = 0.0,
                    dphPct = 21.0
                )
            )
        }
        val rec = com.example.data.FakturaRecord(
            id = "${if (isZalohova) "zf" else "fa"}_${System.currentTimeMillis()}",
            cislo = cislo,
            variabilniSymbol = cislo.filter { it.isDigit() },
            typDokladu = if (isZalohova) "ZALOHOVA_FAKTURA" else "FAKTURA",
            ucelFaktury = effectiveUcel,
            depo = zakazka.depo.ifBlank { _currentDepo.value ?: "Depo 1" },
            stredisko = zakazka.stredisko.ifBlank { if (effectiveUcel == "PRONAJEM") "Pronájem" else "Prodej" },
            datumVystaveni = today,
            datumZdanitelnehoPlneni = today,
            datumSplatnosti = due,
            ic = zakazka.ic,
            dic = zakazka.dic,
            firma = zakazka.firma,
            jmeno = zakazka.vyrizuje,
            ulice = zakazka.ulice.ifBlank { zakazka.lokalita },
            telefon = zakazka.telefon,
            email = zakazka.email,
            mistoDodani = zakazka.lokalita,
            cisloZakazky = zakazka.cisloZakazky,
            cisloNabidky = zakazka.cisloNabidky,
            zdrojZakazkaId = zakazka.id,
            zdrojNabidkaId = zakazka.zdrojNabidkaId,
            adresarId = zakazka.adresarId,
            text = "${if (effectiveUcel == "PRONAJEM") "Pronájem kontejneru" else "Prodej kontejneru"} – Zakázka ${zakazka.cisloZakazky}",
            poznamka = zakazka.prepravaInfo,
            items = items,
            timestamp = System.currentTimeMillis()
        )
        saveFaktura(rec)
        return rec
    }

    fun createFakturaFromContainer(
        card: com.example.data.ContainerCard,
        ucelFaktury: String = "PRODEJ",
        isZalohova: Boolean = false
    ): com.example.data.FakturaRecord {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val today = sdf.format(java.util.Date())
        val due = sdf.format(java.util.Date(System.currentTimeMillis() + 14L * 86400000L))
        val cislo = getNextFakturaNumber(isZalohova)
        val ucel = if (ucelFaktury.uppercase().contains("PRON")) "PRONAJEM" else "PRODEJ"
        val basePrice = card.price.toDouble()
        val dph = kotlin.math.round(basePrice * 0.21)
        val itemText = if (ucel == "PRONAJEM") {
            "Pronájem kontejneru ${card.type} č. ${card.designation}"
        } else {
            "Prodej kontejneru ${card.type} č. ${card.designation} (${card.color.ifBlank { "standard" }})"
        }
        val items = listOf(
            com.example.data.NabidkaItemRecord(
                id = "fi_${System.currentTimeMillis()}",
                kod = if (ucel == "PRONAJEM") "PRON" else "PRODEJ",
                text = itemText,
                mnozstvi = 1.0,
                mj = if (ucel == "PRONAJEM") "měs." else "ks",
                jCena = basePrice,
                dphPct = 21.0,
                castkaBez = basePrice,
                dphVal = dph
            )
        )
        val firma = card.customerCompany.ifBlank { card.soldCustomerName }.ifBlank { card.reservedName }
        val rec = com.example.data.FakturaRecord(
            id = "${if (isZalohova) "zf" else "fa"}_${System.currentTimeMillis()}",
            cislo = cislo,
            variabilniSymbol = cislo.filter { it.isDigit() },
            typDokladu = if (isZalohova) "ZALOHOVA_FAKTURA" else "FAKTURA",
            ucelFaktury = ucel,
            depo = card.depo.ifBlank { _currentDepo.value ?: "Depo 1" },
            stredisko = if (ucel == "PRONAJEM") "Pronájem" else "Prodej",
            datumVystaveni = today,
            datumZdanitelnehoPlneni = today,
            datumSplatnosti = due,
            ic = card.soldCustomerId,
            firma = firma,
            jmeno = card.reservedName.ifBlank { card.soldCustomerName },
            ulice = card.soldDelivery,
            telefon = card.soldPhone.ifBlank { card.reservedPhone },
            email = card.soldEmail.ifBlank { card.reservedEmail },
            oznaceniKontejneru = card.designation,
            typKontejneru = card.type,
            mistoDodani = card.soldDelivery,
            zdrojKontejnerId = card.id,
            text = itemText,
            poznamka = card.description,
            items = items,
            timestamp = System.currentTimeMillis()
        )
        saveFaktura(rec)
        return rec
    }

    fun convertZalohovaToVyuctovaciFaktura(zalohova: com.example.data.FakturaRecord): com.example.data.FakturaRecord {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        val today = sdf.format(java.util.Date())
        val due = sdf.format(java.util.Date(System.currentTimeMillis() + 14L * 86400000L))
        val newCislo = getNextFakturaNumber(false)
        val vyuctovaci = zalohova.copy(
            id = "fa_${System.currentTimeMillis()}",
            cislo = newCislo,
            variabilniSymbol = newCislo.filter { it.isDigit() },
            typDokladu = "FAKTURA",
            datumVystaveni = today,
            datumZdanitelnehoPlneni = today,
            datumSplatnosti = due,
            poznamka = "Vyúčtování zálohové faktury č. ${zalohova.cislo}. ${zalohova.poznamka}".trim(),
            timestamp = System.currentTimeMillis()
        )
        saveFaktura(vyuctovaci)
        saveFaktura(zalohova.copy(stavUhrady = "Vyúčtováno"))
        return vyuctovaci
    }

    /**
     * Kompletní reindexace, oprava vazeb, přečíslování a vyčištění celé databáze (Room + JSON agendy).
     */
    fun reindexEntireDatabase(onComplete: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            if (_reindexProgress.value) return@launch
            _reindexProgress.value = true
            try {
                // 1. Reindexace kontejnerů (Room + _localContainers + containerSystemNumbers)
                val dbContainers = try { repository.getAllContainersDirect() } catch (_: Exception) { emptyList() }
                val mergedContainersMap = LinkedHashMap<String, ContainerCard>()
                (dbContainers + _localContainers.value).forEach { c ->
                    if (c.id.isNotBlank() && c.id !in deletedIds &&
                        !c.id.contains("hyundai", ignoreCase = true) &&
                        !c.designation.contains("hyundai", ignoreCase = true)
                    ) {
                        val existing = mergedContainersMap[c.id]
                        if (existing == null || c.timestamp >= existing.timestamp) {
                            mergedContainersMap[c.id] = repository.healContainerCard(c)
                        }
                    }
                }
                val sortedContainers = mergedContainersMap.values.sortedBy { if (it.timestamp > 0L) it.timestamp else Long.MAX_VALUE }
                val usedD1 = mutableSetOf<Int>()
                val usedD2 = mutableSetOf<Int>()
                var nextD1 = 1
                var nextD2 = 1
                val reindexedContainers = sortedContainers.map { c ->
                    val isD2 = c.depo == "Depo 2" || c.status == "DEPO 02"
                    val usedSet = if (isD2) usedD2 else usedD1
                    val assignedSysNum = if (c.systemNumber > 0 && usedSet.add(c.systemNumber)) {
                        c.systemNumber
                    } else {
                        if (isD2) {
                            while (!usedD2.add(nextD2)) nextD2++
                            nextD2
                        } else {
                            while (!usedD1.add(nextD1)) nextD1++
                            nextD1
                        }
                    }
                    val normDesig = normalizeContainerDesignation(c.designation).ifBlank { c.designation.trim() }
                    val cleanDepo = if (isD2) "Depo 2" else "Depo 1"
                    val cleanStatus = c.status.ifBlank { if (isD2) "DEPO 02" else "DEPO 01" }
                    val updated = c.copy(
                        systemNumber = assignedSysNum,
                        designation = normDesig,
                        depo = cleanDepo,
                        status = cleanStatus
                    )
                    try { repository.insert(updated) } catch (_: Exception) {}
                    updated
                }.sortedByDescending { it.timestamp }
                syncMaxContainerSystemNumbers(reindexedContainers)
                _localContainers.value = reindexedContainers
                saveLocalContainers(reindexedContainers)

                // 2. Reindexace zákazníků / adresáře / objednávek
                val custById = LinkedHashMap<String, com.example.data.CustomerRecord>()
                _customers.value.forEach { c ->
                    val id = c.id.ifBlank { "cust_${System.currentTimeMillis()}_${(100..999).random()}" }
                    if (id !in deletedIds && (c.company.isNotBlank() || c.name.isNotBlank() || c.phone.isNotBlank() || c.email.isNotBlank())) {
                        val existing = custById[id]
                        if (existing == null || c.timestamp >= existing.timestamp) {
                            custById[id] = c.copy(id = id, depo = c.depo.ifBlank { "Depo 1" })
                        }
                    }
                }
                var custSeq = 1
                val reindexedCustomers = custById.values
                    .sortedBy { if (it.timestamp > 0L) it.timestamp else Long.MAX_VALUE }
                    .map { c ->
                        val cislo = c.cisloZakaznika.ifBlank {
                            "26ZA" + (custSeq++).toString().padStart(4, '0')
                        }
                        c.copy(cisloZakaznika = cislo)
                    }
                    .sortedByDescending { it.timestamp }
                _customers.value = reindexedCustomers
                saveLocalCustomers(reindexedCustomers)

                fun findAdresarMatchId(ic: String, firma: String, jmeno: String, email: String, tel: String): String {
                    val match = reindexedCustomers.firstOrNull { c ->
                        (ic.isNotBlank() && c.ic.equals(ic, ignoreCase = true)) ||
                        (firma.isNotBlank() && c.company.equals(firma, ignoreCase = true)) ||
                        (email.isNotBlank() && c.email.equals(email, ignoreCase = true)) ||
                        (tel.isNotBlank() && c.phone.replace(" ", "") == tel.replace(" ", "")) ||
                        (firma.isBlank() && jmeno.isNotBlank() && c.name.equals(jmeno, ignoreCase = true))
                    }
                    return match?.id ?: ""
                }

                // 3. Reindexace nabídek (CN)
                val nabById = LinkedHashMap<String, com.example.data.NabidkaRecord>()
                _nabidky.value.forEach { n ->
                    val id = n.id.ifBlank { "nab_${System.currentTimeMillis()}_${(100..999).random()}" }
                    if (id !in deletedIds) {
                        val existing = nabById[id]
                        if (existing == null || n.timestamp >= existing.timestamp) {
                            nabById[id] = n.copy(id = id)
                        }
                    }
                }
                var nabSeq = 1
                val reindexedNabidky = nabById.values
                    .sortedBy { if (it.timestamp > 0L) it.timestamp else Long.MAX_VALUE }
                    .map { n ->
                        val cislo = n.cislo.ifBlank { "26CN" + (nabSeq++).toString().padStart(4, '0') }
                        val adrId = n.adresarId.ifBlank { findAdresarMatchId(n.ic, n.firma, n.jmeno, n.email, n.telefon) }
                        n.copy(
                            cislo = cislo,
                            depo = n.depo.ifBlank { "Depo 1" },
                            adresarId = adrId
                        )
                    }
                    .sortedByDescending { it.timestamp }
                _nabidky.value = reindexedNabidky
                saveLocalNabidky(reindexedNabidky)

                // 4. Reindexace zakázek (ZA)
                val zakById = LinkedHashMap<String, com.example.data.ZakazkaRecord>()
                _zakazky.value.forEach { z ->
                    val id = z.id.ifBlank { "zak_${System.currentTimeMillis()}_${(100..999).random()}" }
                    if (id !in deletedIds) {
                        val existing = zakById[id]
                        if (existing == null || z.timestamp >= existing.timestamp) {
                            zakById[id] = z.copy(id = id)
                        }
                    }
                }
                var zakSeq = 1
                val reindexedZakazky = zakById.values
                    .sortedBy { if (it.timestamp > 0L) it.timestamp else Long.MAX_VALUE }
                    .map { z ->
                        val cislo = z.cisloZakazky.ifBlank { "26ZA" + (zakSeq++).toString().padStart(4, '0') }
                        val adrId = z.adresarId.ifBlank { findAdresarMatchId(z.ic, z.firma, z.vyrizuje, z.email, z.telefon) }
                        z.copy(
                            cisloZakazky = cislo,
                            depo = z.depo.ifBlank { "Depo 1" },
                            adresarId = adrId
                        )
                    }
                    .sortedByDescending { it.timestamp }
                _zakazky.value = reindexedZakazky
                saveLocalZakazky(reindexedZakazky)

                // 5. Reindexace faktur (FA) a zálohových faktur (ZF)
                fun reindexInvoiceList(list: List<com.example.data.FakturaRecord>, isZal: Boolean): List<com.example.data.FakturaRecord> {
                    val prefix = if (isZal) "26ZF" else "26FA"
                    val map = LinkedHashMap<String, com.example.data.FakturaRecord>()
                    list.forEach { f ->
                        val id = f.id.ifBlank { "${if (isZal) "zf" else "fa"}_${System.currentTimeMillis()}_${(100..999).random()}" }
                        if (id !in deletedIds) {
                            val existing = map[id]
                            if (existing == null || f.timestamp >= existing.timestamp) {
                                map[id] = f.copy(id = id)
                            }
                        }
                    }
                    var seq = 1
                    return map.values
                        .sortedBy { if (it.timestamp > 0L) it.timestamp else Long.MAX_VALUE }
                        .map { f ->
                            val cislo = f.cislo.ifBlank { prefix + (seq++).toString().padStart(4, '0') }
                            val vs = f.variabilniSymbol.ifBlank { cislo.filter { it.isDigit() } }
                            val adrId = f.adresarId.ifBlank { findAdresarMatchId(f.ic, f.firma, f.jmeno, f.email, f.telefon) }
                            f.copy(
                                cislo = cislo,
                                variabilniSymbol = vs,
                                depo = f.depo.ifBlank { "Depo 1" },
                                adresarId = adrId
                            )
                        }
                        .sortedByDescending { it.timestamp }
                }

                val reindexedFaktury = reindexInvoiceList(_faktury.value, false)
                _faktury.value = reindexedFaktury
                saveLocalFaktury(reindexedFaktury)

                val reindexedZalohove = reindexInvoiceList(_zalohoveFaktury.value, true)
                _zalohoveFaktury.value = reindexedZalohove
                saveLocalZalohoveFaktury(reindexedZalohove)

                // 6. Reindexace přeprav (Room + _localTransports)
                val dbTransports = try { repository.getAllTransportsDirect() } catch (_: Exception) { emptyList() }
                val transMap = LinkedHashMap<String, TransportRecord>()
                (dbTransports + _localTransports.value).forEach { t ->
                    if (t.id.isNotBlank() && t.id !in deletedIds) {
                        val existing = transMap[t.id]
                        if (existing == null || t.timestamp >= existing.timestamp) {
                            transMap[t.id] = t.copy(
                                depo = t.depo.ifBlank { "Depo 1" },
                                status = t.status.ifBlank { "čeká" }
                            )
                        }
                    }
                }
                val reindexedTransports = transMap.values.sortedByDescending { it.timestamp }
                reindexedTransports.forEach { t ->
                    try { repository.insertTransport(t) } catch (_: Exception) {}
                }
                _localTransports.value = reindexedTransports
                saveLocalTransports(reindexedTransports)

                // 7. Reindexace zájemců z depa
                val zajMap = LinkedHashMap<String, com.example.data.ZajemceRecord>()
                _zajemci.value.forEach { z ->
                    val id = z.id.ifBlank { "zaj_${System.currentTimeMillis()}_${(100..999).random()}" }
                    if (id !in deletedIds) {
                        val existing = zajMap[id]
                        if (existing == null || z.timestamp >= existing.timestamp) {
                            zajMap[id] = z.copy(
                                id = id,
                                depo = z.depo.ifBlank { "Depo 1" },
                                status = z.status.ifBlank { "NOVÝ" }
                            )
                        }
                    }
                }
                val reindexedZajemci = zajMap.values.sortedByDescending { it.timestamp }
                _zajemci.value = reindexedZajemci
                saveLocalZajemci(reindexedZajemci)

                // 8. Reindexace inventur
                val invMap = LinkedHashMap<String, InventoryRecord>()
                _localInventories.value.forEach { inv ->
                    val id = inv.id.ifBlank { "inv_${System.currentTimeMillis()}_${(100..999).random()}" }
                    if (id !in deletedIds) {
                        val existing = invMap[id]
                        if (existing == null || inv.timestamp >= existing.timestamp) {
                            invMap[id] = inv.copy(id = id, depo = inv.depo.ifBlank { "Depo 1" })
                        }
                    }
                }
                val reindexedInventories = invMap.values.sortedByDescending { it.timestamp }
                _localInventories.value = reindexedInventories
                saveLocalInventories(reindexedInventories)

                val summary = "Reindexováno (${reindexedContainers.size + reindexedCustomers.size + reindexedNabidky.size + reindexedZakazky.size + reindexedFaktury.size + reindexedZalohove.size + reindexedTransports.size})"
                logEvent(summary)
                addServerLog(summary)
                onComplete?.invoke(summary)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete?.invoke("Chyba reindexace: ${e.message}")
            } finally {
                _reindexProgress.value = false
            }
        }
    }
}

class MainViewModelFactory(
    private val repository: ContainerRepository,
    private val filesDir: File,
    private val context: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, filesDir, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class AresSubjectResult(
    val ico: String,
    val dic: String,
    val company: String,
    val address: String,
    val bonita: String = "AAA",
    val bonitaDetail: String = "Aktivní subjekt v ARES • Bez záznamu v ISIR a exekucích"
)
