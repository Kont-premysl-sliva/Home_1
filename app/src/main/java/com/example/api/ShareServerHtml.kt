package com.example.api

fun getHtmlPage(): String {
    val html = """
<!DOCTYPE html>
<html lang="cs" class="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kontejnery Ostrava - Vzdálená správa (PC)</title>
    <!-- Tailwind CSS CDN -->
    <script src="https://cdn.tailwindcss.com"></script>
    <!-- Google Material Icons -->
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    <script>
        tailwind.config = {
            darkMode: 'class',
            theme: {
                extend: {
                    colors: {
                        brand: {
                            50: '#f0f9ff',
                            100: '#e0f2fe',
                            500: '#0ea5e9',
                            600: '#0284c7',
                            700: '#0369a1',
                            900: '#0c4a6e',
                        },
                        slate: {
                            850: '#1e293b'
                        }
                    }
                }
            }
        }
    </script>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;600;700&family=Inter:wght@400;500;600&display=swap');
        body {
            font-family: 'Inter', sans-serif;
        }
        h1, h2, h3, .font-display {
            font-family: 'Space Grotesk', sans-serif;
        }
        /* Custom scrollbar for slate */
        ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        ::-webkit-scrollbar-track {
            background: #0f172a;
        }
        ::-webkit-scrollbar-thumb {
            background: #334155;
            border-radius: 4px;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: #475569;
        }
    </style>
</head>
<body class="bg-slate-950 text-slate-100 min-h-screen flex flex-col">

    <!-- Toast Notifications -->
    <div id="toast-container" class="fixed bottom-5 right-5 z-50 flex flex-col gap-2"></div>

    <!-- Header -->
    <header class="bg-slate-900 border-b border-slate-800 sticky top-0 z-40">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex items-center justify-between">
            <div class="flex items-center space-x-3">
                <span class="material-icons text-brand-500 text-3xl">local_shipping</span>
                <div>
                    <h1 class="text-xl font-bold tracking-tight text-white">Kontejnery Ostrava</h1>
                    <p class="text-xs text-slate-400">Vzdálená správa z PC prohlížeče Chrome</p>
                </div>
            </div>
            <div class="flex items-center space-x-4">
                <div class="flex items-center space-x-2 bg-slate-950 px-3 py-1.5 rounded-full border border-slate-800">
                    <span class="w-2.5 h-2.5 rounded-full bg-sky-500 animate-pulse"></span>
                    <span class="text-xs font-semibold text-sky-400">PŘIPOJENO K TELEFONU</span>
                </div>
            </div>
        </div>
    </header>

    <!-- Main Content Area -->
    <main class="flex-grow max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        
        <!-- Quick Statistics Overview -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <div class="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex items-center justify-between shadow-lg">
                <div>
                    <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Celkem kontejnerů</p>
                    <h3 id="stat-total" class="text-3xl font-bold mt-1 text-white">0</h3>
                </div>
                <div class="bg-brand-900/40 p-3 rounded-xl border border-brand-500/20">
                    <span class="material-icons text-brand-500 text-2xl">inventory</span>
                </div>
            </div>

            <div class="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex items-center justify-between shadow-lg">
                <div>
                    <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Aktivní ve Skladu</p>
                    <h3 id="stat-sklad" class="text-3xl font-bold mt-1 text-sky-400">0</h3>
                </div>
                <div class="bg-sky-950/40 p-3 rounded-xl border border-sky-500/20">
                    <span class="material-icons text-sky-400 text-2xl">warehouse</span>
                </div>
            </div>

            <div class="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex items-center justify-between shadow-lg">
                <div>
                    <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">V pronájmu</p>
                    <h3 id="stat-rent" class="text-3xl font-bold mt-1 text-amber-400">0</h3>
                </div>
                <div class="bg-amber-950/40 p-3 rounded-xl border border-amber-500/20">
                    <span class="material-icons text-amber-400 text-2xl">history_edu</span>
                </div>
            </div>

            <div class="bg-slate-900 border border-slate-800 p-6 rounded-2xl flex items-center justify-between shadow-lg">
                <div>
                    <p class="text-xs font-semibold text-slate-400 uppercase tracking-wider">Prodané / Archiv</p>
                    <h3 id="stat-sold" class="text-3xl font-bold mt-1 text-slate-400">0</h3>
                </div>
                <div class="bg-slate-800 p-3 rounded-xl border border-slate-700/50">
                    <span class="material-icons text-slate-400 text-2xl">archive</span>
                </div>
            </div>
        </div>

        <!-- Action / Management Panel -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 mb-8 shadow-lg">
            <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                <div>
                    <h2 class="text-lg font-bold text-white">Nástroje zálohy a synchronizace</h2>
                    <p class="text-xs text-slate-400">Hromadné operace a správa kompletních dat s telefonem</p>
                </div>
                <div class="flex flex-wrap items-center gap-3">
                    <a href="/pc-terminal.html" target="_blank" class="inline-flex items-center space-x-2 bg-blue-600 hover:bg-blue-500 text-white font-bold text-sm px-4 py-2.5 rounded-xl shadow-md transition duration-150">
                        <span class="material-icons text-sm">open_in_new</span>
                        <span>TERMINÁL</span>
                    </a>

                    <a href="/download-html" download="S_POHODA_Kontejnery_v66.html" class="inline-flex items-center space-x-2 bg-sky-600 hover:bg-sky-500 text-white font-bold text-sm px-4 py-2.5 rounded-xl shadow-md transition duration-150">
                        <span class="material-icons text-sm">download</span>
                        <span>STÁHNOUT HTML</span>
                    </a>

                    <a href="/api/backup/export" class="inline-flex items-center space-x-2 bg-slate-800 hover:bg-slate-700 text-white font-semibold text-sm px-4 py-2.5 rounded-xl border border-slate-700 transition duration-150">
                        <span class="material-icons text-sm">download</span>
                        <span>Stáhnout ZIP zálohu</span>
                    </a>
                    
                    <button onclick="document.getElementById('backup-file-input').click()" class="inline-flex items-center space-x-2 bg-slate-800 hover:bg-slate-700 text-white font-semibold text-sm px-4 py-2.5 rounded-xl border border-slate-700 transition duration-150">
                        <span class="material-icons text-sm">upload</span>
                        <span>Obnovit ze ZIPu</span>
                    </button>
                    <input type="file" id="backup-file-input" class="hidden" accept=".zip" onchange="uploadBackup(event)">

                    <button onclick="openAddModal()" class="inline-flex items-center space-x-2 bg-brand-500 hover:bg-brand-600 text-white font-semibold text-sm px-5 py-2.5 rounded-xl shadow-md shadow-brand-500/10 transition duration-150">
                        <span class="material-icons text-sm">add</span>
                        <span>Přidat kontejner</span>
                    </button>
                </div>
            </div>
        </div>

        <!-- Filter and Search Area -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 mb-6 shadow-lg">
            <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
                <div class="relative">
                    <span class="material-icons absolute left-3.5 top-3 text-slate-500 text-xl">search</span>
                    <input type="text" id="filter-search" oninput="applyFilters()" placeholder="Hledat označení, popis, SPZ..." class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl pl-11 pr-4 py-2.5 text-sm focus:outline-none focus:border-brand-500 transition duration-150">
                </div>
                <div>
                    <select id="filter-depo" onchange="applyFilters()" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500 transition duration-150">
                        <option value="">Všechna depa</option>
                        <option value="Depo 1">Depo 1</option>
                        <option value="Depo 2">Depo 2</option>
                        <option value="Depo Ostrava">Depo Ostrava</option>
                    </select>
                </div>
                <div>
                    <select id="filter-status" onchange="applyFilters()" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500 transition duration-150">
                        <option value="">Všechny stavy</option>
                        <option value="SKLAD">Sklad (Aktivní)</option>
                        <option value="SKLADY">Sklady (Aktivní)</option>
                        <option value="DEPO 01">DEPO 01</option>
                        <option value="DEPO 02">DEPO 02</option>
                        <option value="PRONÁJ">V pronájmu</option>
                        <option value="PRONAJ.">PRONAJ.</option>
                        <option value="REZERV.">Rezervováno</option>
                        <option value="PLATBA">Čeká na platbu</option>
                        <option value="PRODEJ">Prodej / Archiv</option>
                        <option value="ARCHIV">Archiv</option>
                    </select>
                </div>
                <div>
                    <select id="filter-owner" onchange="applyFilters()" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500 transition duration-150">
                        <option value="">Všichni vlastníci</option>
                        <option value="KONTEJNERY">KONTEJNERY</option>
                        <option value="CARU">CARU</option>
                        <option value="OVLU">OVLU</option>
                        <option value="MAGITU">MAGITU</option>
                    </select>
                </div>
                <div>
                    <button onclick="clearAllFilters()" class="w-full bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 rounded-xl px-4 py-2.5 text-sm font-semibold transition duration-150 flex items-center justify-center space-x-1.5">
                        <span class="material-icons text-sm">clear_all</span>
                        <span>Zrušit filtry</span>
                    </button>
                </div>
            </div>
        </div>

        <!-- Containers Table -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-lg">
            <div class="overflow-x-auto">
                <table class="w-full text-left border-collapse">
                    <thead>
                        <tr class="bg-slate-950/60 border-b border-slate-800 text-slate-400 text-xs font-bold uppercase tracking-wider">
                            <th class="px-6 py-4">Foto</th>
                            <th class="px-6 py-4">Označení</th>
                            <th class="px-6 py-4">Typ / Vlastník</th>
                            <th class="px-6 py-4">Depo / Umístění</th>
                            <th class="px-6 py-4">Stav</th>
                            <th class="px-6 py-4">Cena</th>
                            <th class="px-6 py-4 text-right">Akce</th>
                        </tr>
                    </thead>
                    <tbody id="containers-body" class="divide-y divide-slate-800/60 text-sm">
                        <!-- Loading State -->
                        <tr>
                            <td colspan="7" class="text-center py-12 text-slate-500">
                                <div class="inline-block animate-spin rounded-full h-8 w-8 border-4 border-slate-700 border-t-brand-500 mb-2"></div>
                                <p>Načítám kontejnery z mobilu...</p>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

    </main>

    <!-- Footer -->
    <footer class="bg-slate-900 border-t border-slate-800 py-6 text-center text-xs text-slate-500">
        <div class="max-w-7xl mx-auto px-4">
            <p>© 2026 Kontejnery Ostrava. Všechna data jsou uložena lokálně ve vašem telefonu.</p>
        </div>
    </footer>

    <!-- Add / Edit Container Modal -->
    <div id="container-modal" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 backdrop-blur-sm hidden">
        <div class="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-2xl p-8 max-h-[90vh] overflow-y-auto shadow-2xl">
            <div class="flex items-center justify-between border-b border-slate-800 pb-4 mb-6">
                <h3 id="modal-title" class="text-xl font-bold text-white">Přidat nový kontejner</h3>
                <button onclick="closeModal()" class="text-slate-400 hover:text-white transition">
                    <span class="material-icons">close</span>
                </button>
            </div>
            
            <form id="container-form" onsubmit="saveContainer(event)" class="space-y-5">
                <input type="hidden" id="form-id" value="">
                <input type="hidden" id="form-image-uri" value="">
                <input type="hidden" id="form-extra-images" value="">
                <input type="hidden" id="form-timestamp" value="">
                
                <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Označení kontejneru *</label>
                        <input type="text" id="form-designation" required placeholder="Např. AP98" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500">
                    </div>
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Typ kontejneru</label>
                        <input type="text" id="form-type" placeholder="Např. 20ft Sklad" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500">
                    </div>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Depo *</label>
                        <select id="form-depo" required class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500">
                            <option value="Depo 1">Depo 1</option>
                            <option value="Depo 2">Depo 2</option>
                            <option value="Depo Ostrava">Depo Ostrava</option>
                        </select>
                    </div>
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Stav kontejneru *</label>
                        <select id="form-status" required class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500">
                            <option value="DEPO 01">DEPO 01</option>
                            <option value="DEPO 02">DEPO 02</option>
                            <option value="REZERV.">Rezervováno</option>
                            <option value="PLATBA">Čeká na platbu</option>
                            <option value="PRONÁJ">Pronájem</option>
                            <option value="PRONAJ.">PRONAJ.</option>
                            <option value="PRODEJ">Prodej / Archiv</option>
                            <option value="ARCHIV">Archiv</option>
                        </select>
                    </div>
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Vlastník</label>
                        <select id="form-vlastnik" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500">
                            <option value="KONTEJNERY">KONTEJNERY</option>
                            <option value="CARU">CARU</option>
                            <option value="OVLU">OVLU</option>
                            <option value="MAGITU">MAGITU</option>
                        </select>
                    </div>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-3 gap-5">
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Barva</label>
                        <input type="text" id="form-color" placeholder="Např. Modrá" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500">
                    </div>
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Cena (Kč)</label>
                        <input type="number" id="form-price" placeholder="Např. 45000" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500">
                    </div>
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">SPZ auta</label>
                        <input type="text" id="form-spz" placeholder="Např. 1AM 1234" class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:border-brand-500">
                    </div>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Popis / Vybavení</label>
                        <textarea id="form-description" rows="3" placeholder="Dodatečné info o stavu..." class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl p-4 text-sm focus:outline-none focus:border-brand-500"></textarea>
                    </div>
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 uppercase tracking-wide mb-1.5">Poznámka</label>
                        <textarea id="form-poznamka" rows="3" placeholder="Poznámka..." class="w-full bg-slate-950 text-white border border-slate-800 rounded-xl p-4 text-sm focus:outline-none focus:border-brand-500"></textarea>
                    </div>
                </div>

                <div class="flex justify-end items-center space-x-3 border-t border-slate-800 pt-5 mt-6">
                    <button type="button" onclick="closeModal()" class="bg-slate-800 hover:bg-slate-700 text-white font-semibold text-sm px-5 py-2.5 rounded-xl border border-slate-700 transition">Zrušit</button>
                    <button type="submit" class="bg-brand-500 hover:bg-brand-600 text-white font-semibold text-sm px-6 py-2.5 rounded-xl shadow-md transition">Uložit kontejner</button>
                </div>
            </form>
        </div>
    </div>

    <!-- Scripting for Live Synchronizations and Operations -->
    <script>
        let allContainers = [];

        // Toast notifications maker
        function showToast(message, type = 'success') {
            const container = document.getElementById('toast-container');
            const toast = document.createElement('div');
            
            const bgClass = type === 'success' ? 'bg-sky-950 border-sky-500/30 text-sky-300' : 'bg-red-950 border-red-500/30 text-red-300';
            const icon = type === 'success' ? 'check_circle' : 'error';
            
            toast.className = 'flex items-center space-x-2.5 border px-4 py-3 rounded-xl shadow-2xl ' + bgClass + ' transition duration-300 transform translate-y-2 opacity-0';
            toast.innerHTML = ' \
                <span class="material-icons text-xl">' + icon + '</span> \
                <span class="text-sm font-medium">' + message + '</span> \
            ';
            
            container.appendChild(toast);
            
            // Trigger animation
            setTimeout(() => {
                toast.classList.remove('translate-y-2', 'opacity-0');
            }, 50);
            
            // Auto remove
            setTimeout(() => {
                toast.classList.add('opacity-0', 'translate-y-2');
                setTimeout(() => toast.remove(), 300);
            }, 4000);
        }

        // Fetch containers list from Android app
        async function loadContainers() {
            const body = document.getElementById('containers-body');
            try {
                const response = await fetch('/api/containers');
                if (!response.ok) throw new Error('Chyba komunikace s telefonem');
                
                allContainers = await response.json();
                
                updateStatistics();
                applyFilters();
            } catch (error) {
                console.error(error);
                body.innerHTML = ' \
                    <tr> \
                        <td colspan="7" class="text-center py-12 text-red-400"> \
                            <span class="material-icons text-4xl mb-2">wifi_off</span> \
                            <p class="font-semibold">Nepodařilo se navázat spojení se zařízením.</p> \
                            <p class="text-xs text-slate-500 mt-1">Ujistěte se, že je server v telefonu stále zapnutý a obnovte stránku.</p> \
                        </td> \
                    </tr> \
                ';
            }
        }

        // Calculate and display stats
        function updateStatistics() {
            document.getElementById('stat-total').innerText = allContainers.length;
            
            const skladCount = allContainers.filter(c => ['SKLAD', 'SKLADY', 'DEPO 01', 'DEPO 02'].includes(c.status)).length;
            const rentCount = allContainers.filter(c => ['PRONÁJ', 'PRONAJ.'].includes(c.status)).length;
            const soldCount = allContainers.filter(c => ['PRODEJ', 'ARCHIV'].includes(c.status)).length;
            
            document.getElementById('stat-sklad').innerText = skladCount;
            document.getElementById('stat-rent').innerText = rentCount;
            document.getElementById('stat-sold').innerText = soldCount;
        }

        // Apply visual search / filters
        function applyFilters() {
            const search = document.getElementById('filter-search').value.toLowerCase().trim();
            const depo = document.getElementById('filter-depo').value;
            const status = document.getElementById('filter-status').value;
            const owner = document.getElementById('filter-owner').value;
            
            const filtered = allContainers.filter(c => {
                const matchesSearch = !search || 
                    (c.designation && c.designation.toLowerCase().includes(search)) ||
                    (c.description && c.description.toLowerCase().includes(search)) ||
                    (c.color && c.color.toLowerCase().includes(search)) ||
                    (c.spz && c.spz.toLowerCase().includes(search)) ||
                    (c.vlastnik && c.vlastnik.toLowerCase().includes(search));
                
                const matchesDepo = !depo || c.depo === depo;
                const matchesStatus = !status || c.status === status;
                const matchesOwner = !owner || c.vlastnik === owner;
                
                return matchesSearch && matchesDepo && matchesStatus && matchesOwner;
            });
            
            renderContainers(filtered);
        }

        // Clear all filters to display all containers
        function clearAllFilters() {
            document.getElementById('filter-search').value = '';
            document.getElementById('filter-depo').value = '';
            document.getElementById('filter-status').value = '';
            document.getElementById('filter-owner').value = '';
            applyFilters();
        }

        // Render table records
        function renderContainers(containers) {
            const body = document.getElementById('containers-body');
            if (containers.length === 0) {
                body.innerHTML = ' \
                    <tr> \
                        <td colspan="7" class="text-center py-12 text-slate-500"> \
                            <span class="material-icons text-3xl mb-1">sentiment_dissatisfied</span> \
                            <p>Nebyly nalezeny žádné kontejnery odpovídající filtrům.</p> \
                        </td> \
                    </tr> \
                ';
                return;
            }
            
            body.innerHTML = containers.map(c => {
                const imageSrc = c.imageUri ? ("/api/image?path=" + encodeURIComponent(c.imageUri)) : '';
                const imageHtml = imageSrc 
                    ? ("<img src=\"" + imageSrc + "\" class=\"w-12 h-12 rounded-lg object-cover border border-slate-700 shadow-md hover:scale-105 transition duration-150 cursor-zoom-in\" onclick=\"window.open('" + imageSrc + "')\" alt=\"Foto\">")
                    : "<div class=\"w-12 h-12 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-500\"><span class=\"material-icons text-xl\">photo_camera</span></div>";
                
                // Get badge color by status
                let statusBadge = '';
                if (['SKLAD', 'SKLADY', 'DEPO 01', 'DEPO 02'].includes(c.status)) {
                    statusBadge = "<span class=\"bg-sky-950 text-sky-400 border border-sky-500/20 text-[11px] font-semibold px-2.5 py-1 rounded-full uppercase\">" + c.status + "</span>";
                } else if (['PRONÁJ', 'PRONAJ.'].includes(c.status)) {
                    statusBadge = "<span class=\"bg-amber-950 text-amber-400 border border-amber-500/20 text-[11px] font-semibold px-2.5 py-1 rounded-full uppercase\">" + c.status + "</span>";
                } else if (c.status === 'REZERV.') {
                    statusBadge = "<span class=\"bg-blue-950 text-blue-400 border border-blue-500/20 text-[11px] font-semibold px-2.5 py-1 rounded-full uppercase\">" + c.status + "</span>";
                } else if (c.status === 'PLATBA') {
                    statusBadge = "<span class=\"bg-cyan-950 text-cyan-400 border border-cyan-500/20 text-[11px] font-semibold px-2.5 py-1 rounded-full uppercase\">" + c.status + "</span>";
                } else {
                    statusBadge = "<span class=\"bg-slate-800 text-slate-400 border border-slate-700 text-[11px] font-semibold px-2.5 py-1 rounded-full uppercase\">" + c.status + "</span>";
                }

                const priceFormatted = c.price ? (new Intl.NumberFormat('cs-CZ').format(c.price) + ' Kč') : '—';
                
                return ' \
                    <tr class="hover:bg-slate-900/40 transition duration-100"> \
                        <td class="px-6 py-4">' + imageHtml + '</td> \
                        <td class="px-6 py-4 font-bold text-white">' + c.designation + '</td> \
                        <td class="px-6 py-4"> \
                            <div class="text-white font-semibold">' + (c.type || 'Skladový') + '</div> \
                            <div class="text-xs text-slate-400">' + (c.vlastnik || 'KONTEJNERY') + '</div> \
                        </td> \
                        <td class="px-6 py-4"> \
                            <div class="text-white font-medium">' + c.depo + '</div> \
                            <div class="text-xs text-slate-400 truncate max-w-[150px]" title="' + (c.poznamka || '') + '">' + (c.poznamka || 'Bez poznámky') + '</div> \
                        </td> \
                        <td class="px-6 py-4">' + statusBadge + '</td> \
                        <td class="px-6 py-4 font-bold text-brand-400">' + priceFormatted + '</td> \
                        <td class="px-6 py-4 text-right"> \
                            <div class="flex items-center justify-end space-x-2"> \
                                <button onclick="openEditModal(' + c.id + ')" class="p-2 hover:bg-slate-800 rounded-lg text-slate-400 hover:text-white transition" title="Upravit"> \
                                    <span class="material-icons text-xl">edit</span> \
                                </button> \
                                <button onclick="deleteContainer(' + c.id + ', \'' + c.designation + '\')" class="p-2 hover:bg-red-950 rounded-lg text-slate-400 hover:text-red-400 transition" title="Smazat"> \
                                    <span class="material-icons text-xl">delete</span> \
                                </button> \
                            </div> \
                        </td> \
                    </tr> \
                ';
            }).join('');
        }

        // Open Modal to create container
        function openAddModal() {
            document.getElementById('modal-title').innerText = 'Přidat nový kontejner';
            document.getElementById('container-form').reset();
            document.getElementById('form-id').value = '0';
            document.getElementById('form-image-uri').value = '';
            document.getElementById('form-extra-images').value = '';
            document.getElementById('form-timestamp').value = Date.now().toString();
            
            document.getElementById('container-modal').classList.remove('hidden');
        }

        // Open Modal to edit existing container
        function openEditModal(id) {
            const container = allContainers.find(c => c.id === id);
            if (!container) return;
            
            document.getElementById('modal-title').innerText = 'Upravit kontejner: ' + container.designation;
            
            document.getElementById('form-id').value = container.id;
            document.getElementById('form-designation').value = container.designation || '';
            document.getElementById('form-type').value = container.type || '';
            document.getElementById('form-depo').value = container.depo || 'Depo 1';
            document.getElementById('form-status').value = container.status || 'DEPO 02';
            document.getElementById('form-vlastnik').value = container.vlastnik || 'KONTEJNERY';
            document.getElementById('form-color').value = container.color || '';
            document.getElementById('form-price').value = container.price || '';
            document.getElementById('form-spz').value = container.spz || '';
            document.getElementById('form-description').value = container.description || '';
            document.getElementById('form-poznamka').value = container.poznamka || '';
            
            // Pass hidden system properties to preserve them
            document.getElementById('form-image-uri').value = container.imageUri || '';
            document.getElementById('form-extra-images').value = container.extraImages || '';
            document.getElementById('form-timestamp').value = container.timestamp || Date.now();
            
            document.getElementById('container-modal').classList.remove('hidden');
        }

        // Close form modal
        function closeModal() {
            document.getElementById('container-modal').classList.add('hidden');
        }

        // Save container (Add/Update)
        async function saveContainer(event) {
            event.preventDefault();
            
            const id = parseInt(document.getElementById('form-id').value);
            const container = {
                id: id,
                designation: document.getElementById('form-designation').value.trim(),
                type: document.getElementById('form-type').value.trim(),
                depo: document.getElementById('form-depo').value,
                status: document.getElementById('form-status').value,
                vlastnik: document.getElementById('form-vlastnik').value,
                color: document.getElementById('form-color').value.trim(),
                price: parseInt(document.getElementById('form-price').value) || 0,
                spz: document.getElementById('form-spz').value.trim(),
                description: document.getElementById('form-description').value.trim(),
                poznamka: document.getElementById('form-poznamka').value.trim(),
                imageUri: document.getElementById('form-image-uri').value || null,
                extraImages: document.getElementById('form-extra-images').value || '',
                timestamp: parseInt(document.getElementById('form-timestamp').value) || Date.now(),
                author: "PC Admin"
            };

            const method = id === 0 ? 'POST' : 'PUT';
            
            try {
                const response = await fetch('/api/containers', {
                    method: method,
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(container)
                });
                
                const resData = await response.json();
                if (resData.success) {
                    showToast(id === 0 ? 'Kontejner úspěšně přidán do telefonu!' : 'Kontejner uložen a přenesen do telefonu!');
                    closeModal();
                    loadContainers();
                } else {
                    showToast('Chyba: ' + resData.message, 'error');
                }
            } catch (error) {
                console.error(error);
                showToast('Chyba komunikace při ukládání', 'error');
            }
        }

        // Delete container from database
        async function deleteContainer(id, designation) {
            if (!confirm('Opravdu si přejete trvale smazat kontejner "' + designation + '" z databáze telefonu?')) return;
            
            try {
                const response = await fetch('/api/containers?id=' + id, {
                    method: 'DELETE'
                });
                
                const resData = await response.json();
                if (resData.success) {
                    showToast('Kontejner "' + designation + '" byl smazán.');
                    loadContainers();
                } else {
                    showToast('Chyba při mazání: ' + resData.message, 'error');
                }
            } catch (error) {
                console.error(error);
                showToast('Chyba komunikace při mazání', 'error');
            }
        }

        // Upload ZIP backup to restore data on Android phone
        async function uploadBackup(event) {
            const file = event.target.files[0];
            if (!file) return;
            
            if (!confirm('Upozornění: Nahráním této zálohy přepíšete stávající data v telefonu. Přejete si pokračovat?')) {
                event.target.value = '';
                return;
            }
            
            showToast('Nahrávám ZIP zálohu a obnovuji data...');
            
            try {
                const response = await fetch('/api/backup/import', {
                    method: 'POST',
                    body: file,
                    headers: { 'Content-Type': 'application/zip' }
                });
                
                const resData = await response.json();
                if (resData.success) {
                    showToast('Záloha úspěšně obnovena na telefonu!', 'success');
                    setTimeout(() => {
                        loadContainers();
                    }, 1000);
                } else {
                    showToast('Chyba při obnově: ' + resData.message, 'error');
                }
            } catch (error) {
                console.error(error);
                showToast('Nepodařilo se přenést zálohu ZIP na telefon.', 'error');
            } finally {
                event.target.value = '';
            }
        }

        // Initial launch
        window.addEventListener('DOMContentLoaded', loadContainers);
    </script>
</body>
</html>
"""
    return html
}
