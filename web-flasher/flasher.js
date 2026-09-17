import { boards } from "./boards.js";
import { ESPLoader, Transport } from "https://unpkg.com/esptool-js@0.5.4/bundle.js";

  const $ = (id) => document.getElementById(id);
  const logEl = $("log");
  const statusEl = $("status");
  const bar = $("bar");
  const barWrap = $("barWrap");
  const flashBtn = $("flash");
  const readInfoBtn = $("read-info");
  const fileInput = $("file");
  const dropzone = $("dropzone");
  const fileChip = $("file-chip");
  const fileChipName = $("file-chip-name");
  const fileChipSize = $("file-chip-size");
  const fileChipRemove = $("file-chip-remove");
  const fwSelect = $("fw");
  const channelSelect = $("channel");
  const channelNote = $("channel-note");
  const targetSelect = $("target");
  const baudrateSelect = $("baudrate");
  const offsetInput = $("offset");
  const copyLogsBtn = $("copy-logs");
  const clearLogsBtn = $("clear-logs");
  const compatBanner = $("compat-banner");

  const telemetryGrid = $("telemetry");
  const telChip = $("telemetry-chip");
  const telMac = $("telemetry-mac");
  const telFlash = $("telemetry-flash");
  const telFeatures = $("telemetry-features");

  const card1 = $("step-1-card");
  const card2 = $("step-2-card");
  const card3 = $("step-3-card");

  // Tab Elements
  const tabLogsBtn = $("tab-logs-btn");
  const tabMonitorBtn = $("tab-monitor-btn");
  const monitorConnectBtn = $("monitor-connect-btn");
  const consoleInputBar = $("consoleInputBar");
  const consoleInput = $("consoleInput");
  const consoleSendBtn = $("consoleSendBtn");
  const downloadFwBtn = $("download-fw");
  const eraseFlashChk = $("erase-flash");
  const autoscrollChk = $("autoscroll-chk");

  let manifest = [];
  let selectedLocalFile = null;
  let telemetryData = { chip: '', mac: '', flash: '', features: '' };
  
  // Serial Monitor variables
  let activeTab = 'logs'; // 'logs' | 'monitor'
  let monitorPort = null;
  let monitorReader = null;
  let isMonitorConnected = false;
  let logBufferLogs = "";
  let logBufferMonitor = "";

  // Set initial step highlights
  function setStepActive(stepNum) {
    [card1, card2, card3].forEach((c, idx) => {
      if (idx + 1 === stepNum) {
        c.classList.add("active");
      } else {
        c.classList.remove("active");
      }
    });
  }

  // System compatibility check
  const isCompatible = "serial" in navigator;
  if (isCompatible) {
    compatBanner.innerHTML = `
      <div class="compatibility-card compatible">
        <span class="compatibility-icon">✓</span>
        <span>Your browser is compatible. Web Serial API is fully supported.</span>
      </div>
    `;
    setStepActive(2); // Enable step 2 selection
  } else {
    compatBanner.innerHTML = `
      <div class="compatibility-card uncompatible">
        <span class="compatibility-icon">⚠</span>
        <span>This browser does not support Web Serial. Use desktop Chrome, Edge, or Opera with a USB data cable.</span>
      </div>
    `;
    flashBtn.disabled = true;
    readInfoBtn.disabled = true;
    [card1, card2, card3].forEach(c => c.classList.add("disabled"));
  }

  // Dynamic Serial Connection Events
  if (isCompatible) {
    navigator.serial.addEventListener("connect", (e) => {
      setStatus("USB serial device plugged in. Ready.", "ok");
      log("[System] USB Serial Port connected.", "ok");
    });
    navigator.serial.addEventListener("disconnect", (e) => {
      setStatus("USB serial device unplugged.", "err");
      log("[System] USB Serial Port disconnected.", "err");
      if (isMonitorConnected) {
        disconnectMonitor();
      }
    });
  }

  function matchesBoard(artifact, board) {
    if (!board) return false;
    if (artifact.board) return artifact.board === board.id;
    const file = (artifact.file || "").toLowerCase().replaceAll("_", "-");
    return board.fileAliases.some(alias => file.includes(alias));
  }

  function populateFirmwareDropdown() {
    if (manifest.length === 0) {
      fwSelect.innerHTML = '<option value="">No bundled builds found - drag a file below</option>';
      return;
    }
    const target = targetSelect.value;
    let filtered = manifest.map((m, i) => ({ ...m, index: i }));
    const board = boards.find(b => b.flasherId === target);
    filtered = filtered.filter(m => matchesBoard(m, board) && m.file.endsWith(".bin") && !m.file.includes("-ota"));
    
    if (filtered.length === 0) {
      fwSelect.innerHTML = '<option value="">No matching builds found for target - upload a local file below</option>';
    } else {
      fwSelect.innerHTML = filtered
        .map((m) => `<option value="${m.index}">${m.name || m.file}</option>`)
        .join("");
    }
  }

  const GITHUB_RELEASES_API =
    "https://api.github.com/repos/SilentWolf75/AetherMesh/releases?per_page=30";
  const GITHUB_RELEASES_WEB = "https://github.com/SilentWolf75/AetherMesh/releases";

  function setChannelNote(text, isError) {
    if (!channelNote) return;
    channelNote.textContent = text;
    channelNote.style.color = isError ? "#f0999b" : "";
  }

  // A release channel is only usable if its release published manifest.json:
  // without it there is no SHA-256 to check the download against, and this tool
  // does not write unverified bytes to a board.
  async function loadReleaseChannel(wantPrerelease) {
    const resp = await fetch(GITHUB_RELEASES_API, {
      headers: { "Accept": "application/vnd.github+json" }
    });
    if (!resp.ok) throw new Error("GitHub Releases returned HTTP " + resp.status);
    const releases = await resp.json();
    const match = (Array.isArray(releases) ? releases : [])
      .filter((r) => !r.draft && !!r.prerelease === wantPrerelease)
      .find((r) => (r.assets || []).some((a) => a.name === "manifest.json"));
    if (!match) {
      throw new Error(wantPrerelease
        ? "No beta release published yet."
        : "No stable release published yet.");
    }
    const manifestAsset = match.assets.find((a) => a.name === "manifest.json");
    const manifestResp = await fetch(manifestAsset.browser_download_url);
    if (!manifestResp.ok) throw new Error("Could not read that release's manifest.");
    const list = await manifestResp.json();
    const byName = new Map((match.assets || []).map((a) => [a.name, a.browser_download_url]));
    // Keep only entries whose binary is actually attached to this release.
    return (Array.isArray(list) ? list : [])
      .filter((entry) => byName.has(entry.file))
      .map((entry) => ({ ...entry, url: byName.get(entry.file), releaseTag: match.tag_name }));
  }

  async function loadChannel(channel) {
    fwSelect.innerHTML = '<option value="">Loading builds...</option>';
    try {
      if (channel === "latest") {
        const r = await fetch("./firmware/manifest.json?t=" + Date.now());
        manifest = r.ok ? await r.json() : [];
        setChannelNote("Latest build of main. Verified against the SHA-256 published beside it.", false);
      } else {
        manifest = await loadReleaseChannel(channel === "beta");
        const tag = manifest.length ? manifest[0].releaseTag : "";
        setChannelNote(
          (channel === "beta" ? "Beta " : "Stable ") + tag +
          ". Verified against the SHA-256 published in that release.", false);
      }
    } catch (error) {
      manifest = [];
      setChannelNote(
        (error && error.message ? error.message : "Could not load that channel.") +
        " Open " + GITHUB_RELEASES_WEB + " to download it manually, or switch channel.", true);
    }
    if (!Array.isArray(manifest)) manifest = [];
    populateFirmwareDropdown();
  }

  if (channelSelect) {
    channelSelect.addEventListener("change", () => loadChannel(channelSelect.value));
  }

  // Load manifest firmware
  fetch("./firmware/manifest.json?t=" + Date.now())
    .then((r) => r.ok ? r.json() : [])
    .then((list) => {
      manifest = Array.isArray(list) ? list : [];
      populateFirmwareDropdown();

      // Stamp app version next to the APK download (app-version.json from Pages deploy).
      fetch("./app-version.json?t=" + Date.now())
        .then((r) => r.ok ? r.json() : null)
        .then((info) => {
          const verEl = document.getElementById("apk-version");
          if (!verEl || !info || !info.version) return;
          const commit = info.commit ? ` · ${info.commit}` : "";
          verEl.textContent = `v${info.version}${commit}`;
        })
        .catch(() => {});

      // Stamp the build hash from firmware manifest when app-version.json is absent.
      const verEl = document.getElementById("apk-version");
      if (verEl && !verEl.textContent && manifest.length > 0) {
        const m = (manifest[0].name || manifest[0].file || "").match(/([0-9a-f]{7})/);
        if (m) verEl.textContent = "build " + m[1];
      }

      // Highlight step 3 once options are available
      if (isCompatible && fwSelect.value !== "") setStepActive(3);
    })
    .catch(() => {
      fwSelect.innerHTML = '<option value="">Choose a file below</option>';
    });

  // Log outputs to terminal simulated window
  function log(msg, cls) {
    logEl.style.display = "block";
    const line = document.createElement("div");
    if (cls) line.className = cls;
    line.textContent = msg;
    logEl.appendChild(line);
    
    // Auto-scroll logic
    if (autoscrollChk.checked) {
      logEl.scrollTop = logEl.scrollHeight;
    }

    // Buffer logs depending on active mode
    if (activeTab === 'logs') {
      logBufferLogs += msg + "\n";
    } else {
      logBufferMonitor += msg + "\n";
    }

    // Parse device telemetry output as it is logged
    parseTelemetry(msg);
  }

  function setStatus(msg, cls) {
    let icon = "⚙";
    if (cls === "ok") icon = "✓";
    if (cls === "err") icon = "⚠";
    statusEl.innerHTML = `<span class="${cls || ''}">${icon}</span> <span>${msg}</span>`;
  }

  function resetProgress() {
    bar.style.width = "0";
    barWrap.style.display = "none";
  }

  function resetTelemetry() {
    telemetryData = { chip: '', mac: '', flash: '', features: '' };
    telChip.textContent = "-";
    telMac.textContent = "-";
    telFlash.textContent = "-";
    telFeatures.textContent = "-";
    telemetryGrid.style.display = "none";
  }

  function parseTelemetry(msg) {
    let matched = false;

    // Detect Chip Name
    const chipMatch = msg.match(/Chip is\s*([^\n]+)/i);
    if (chipMatch) {
      telemetryData.chip = chipMatch[1].trim();
      telChip.textContent = telemetryData.chip;
      matched = true;
    }

    // Detect MAC Address
    const macMatch = msg.match(/MAC:\s*([a-fA-F0-9:]{17})/i);
    if (macMatch) {
      telemetryData.mac = macMatch[1].trim();
      telMac.textContent = telemetryData.mac;
      matched = true;
    }

    // Detect Flash Size
    const flashMatch = msg.match(/(?:Detected flash size|Flash size):\s*([^\n]+)/i);
    if (flashMatch) {
      telemetryData.flash = flashMatch[1].trim();
      telFlash.textContent = telemetryData.flash;
      matched = true;
    }

    // Detect Features
    const featuresMatch = msg.match(/Features:\s*([^\n]+)/i);
    if (featuresMatch) {
      telemetryData.features = featuresMatch[1].trim();
      telFeatures.textContent = telemetryData.features;
      matched = true;
    }

    // Show telemetry if we got any details
    if (matched && (telemetryData.chip || telemetryData.mac || telemetryData.flash)) {
      telemetryGrid.style.display = "grid";
    }
  }

  // Target Hardware Selection Handler
  targetSelect.onchange = () => {
    const val = targetSelect.value;
    if (boards.find(b => b.flasherId === val)?.updateFormat === "nordic-dfu") {
      card2.style.display = "none";
      card3.style.display = "none";
      $("uf2-guide").style.display = "block";
      
      const boardName = val === "lilygo-t-echo" ? "T-Echo"
        : (val === "seeed-t1000-e" ? "T1000-E"
        : (val === "rak3401-1w" ? "RAK3401 1W"
        : (val === "rak19026" ? "RAK19026" : "RAK4631")));
      const driveName = val === "lilygo-t-echo" ? "T-ECHO"
        : (val === "seeed-t1000-e" ? "T1000-E"
        : (val === "rak3401-1w" ? "RAK3401/NORDIC"
        : (val === "rak19026" ? "RAK19026/NORDIC" : "RAK4631/NORDIC")));
      
      document.querySelector("#uf2-guide h3").textContent = `${boardName} Bootloader Flow`;
      document.querySelector("#uf2-guide p").innerHTML = 
        `The ${boardName} uses a USB Mass Storage bootloader. Double-tap <code>RST</code> to enter bootloader mode, then drag and drop the firmware:`;
      document.querySelector("#uf2-guide ol li:nth-child(2)").innerHTML = 
        `A USB folder named <strong>${driveName}</strong> will mount on your computer.`;
      
      $("download-uf2-btn").innerHTML = `<span>⬇</span> Download ${boardName} UF2 Firmware`;
      setStatus(`${boardName} selected. Follow the UF2 guide below.`, "info");
    } else {
      card2.style.display = "block";
      card3.style.display = "block";
      $("uf2-guide").style.display = "none";
      setStatus("Ready.", "info");
      
      // Update firmware options list based on target
      populateFirmwareDropdown();
      
      // Update steps status
      if (isCompatible) {
        setStepActive(2);
        if (fwSelect.value !== "" || fileInput.files.length > 0) {
          setStepActive(3);
        }
      }
    }
  };

  // Visual Hardware Card Selector click binding
  document.querySelectorAll(".hardware-card").forEach(card => {
    card.onclick = () => {
      document.querySelectorAll(".hardware-card").forEach(c => c.classList.remove("selected"));
      card.classList.add("selected");
      targetSelect.value = card.getAttribute("data-val");
      targetSelect.onchange(); // trigger logic directly
    };
  });

  // Download nRF52 UF2 firmware.
  $("download-uf2-btn").onclick = async () => {
    const val = targetSelect.value;
    const board = boards.find(b => b.flasherId === val);
    const uf2 = manifest.find(m =>
      m.file?.toLowerCase().endsWith(".uf2") && matchesBoard(m, board));
    if (uf2) {
      const boardName = val === "lilygo-t-echo" ? "T-Echo"
        : (val === "seeed-t1000-e" ? "T1000-E"
        : (val === "rak3401-1w" ? "RAK3401 1W"
        : (val === "rak19026" ? "RAK19026" : "RAK4631")));
      try {
        setStatus("Verifying " + uf2.file + "...", "info");
        await downloadVerifiedArtifact(uf2);
        setStatus("Verified " + uf2.file + " - drag it onto the " + boardName + " drive.", "ok");
      } catch (error) {
        setStatus(error.message || "Firmware verification failed.", "err");
      }
    } else {
      const boardName = val === "lilygo-t-echo" ? "T-Echo"
        : (val === "seeed-t1000-e" ? "T1000-E"
        : (val === "rak3401-1w" ? "RAK3401 1W"
        : (val === "rak19026" ? "RAK19026" : "RAK4631")));
      setStatus("No " + boardName + " UF2 build is available on this site yet.", "err");
    }
  };

  // Clear Logs
  clearLogsBtn.onclick = () => {
    logEl.innerHTML = "";
    logEl.style.display = "none";
    if (activeTab === 'logs') {
      logBufferLogs = "";
      setStatus("");
      resetProgress();
      resetTelemetry();
    } else {
      logBufferMonitor = "";
    }
  };

  // Copy Logs
  copyLogsBtn.onclick = () => {
    const text = logEl.innerText || logEl.textContent;
    if (text) {
      navigator.clipboard.writeText(text)
        .then(() => {
          const oldText = copyLogsBtn.textContent;
          copyLogsBtn.textContent = "Copied!";
          setTimeout(() => copyLogsBtn.textContent = oldText, 1500);
        })
        .catch((err) => console.error("Could not copy logs: ", err));
    }
  };

  const espTerminal = {
    clean() { logEl.innerHTML = ""; },
    writeLine(data) { log(data); },
    write(data) { log(data); },
  };

  // Drag & drop handlers
  dropzone.onclick = () => fileInput.click();

  dropzone.ondragover = (e) => {
    e.preventDefault();
    dropzone.classList.add("dragover");
  };

  dropzone.ondragleave = () => {
    dropzone.classList.remove("dragover");
  };

  dropzone.ondrop = (e) => {
    e.preventDefault();
    dropzone.classList.remove("dragover");
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleFileSelection(e.dataTransfer.files[0]);
    }
  };

  fileInput.onchange = () => {
    if (fileInput.files && fileInput.files.length > 0) {
      handleFileSelection(fileInput.files[0]);
    }
  };

  function handleFileSelection(file) {
    if (!file.name.toLowerCase().endsWith(".bin")) {
      selectedLocalFile = null;
      fileInput.value = "";
      setStatus("Selected file must be a .bin firmware binary.", "err");
      return;
    }
    
    selectedLocalFile = file;

    // Select local option in dropdown
    fwSelect.value = "";
    
    // UI changes
    fileChipName.textContent = file.name;
    fileChipSize.textContent = (file.size / 1024).toFixed(1) + " KB";
    fileChip.style.display = "flex";
    dropzone.style.display = "none";
    
    setStatus(`Loaded local file: ${file.name}`, "ok");
    setStepActive(3);
  }

  fileChipRemove.onclick = (e) => {
    e.stopPropagation();
    fileInput.value = "";
    selectedLocalFile = null;
    fileChip.style.display = "none";
    dropzone.style.display = "flex";
    
    // Re-highlight step 2 / 3
    if (fwSelect.value === "") {
      setStatus("No firmware file selected.", "err");
    } else {
      setStatus("Ready to flash bundled build.", "ok");
    }
  };

  fwSelect.onchange = () => {
    if (fwSelect.value !== "") {
      // Clear file upload selection
      fileInput.value = "";
      selectedLocalFile = null;
      fileChip.style.display = "none";
      dropzone.style.display = "flex";
      setStatus(`Bundled build selected.`, "ok");
      setStepActive(3);
    }
  };

  async function verifyBundledArtifact(entry, buffer) {
    if (!Number.isSafeInteger(entry.size) || entry.size <= 0 || !/^[0-9a-f]{64}$/i.test(entry.sha256 || "")) {
      throw new Error("Firmware verification metadata is missing or invalid.");
    }
    if (buffer.byteLength !== entry.size) {
      throw new Error(`Size check failed for ${entry.file}.`);
    }
    if (entry.sha256) {
      const digest = await crypto.subtle.digest("SHA-256", buffer);
      const actual = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
      if (actual !== entry.sha256.toLowerCase()) {
        throw new Error(`SHA-256 check failed for ${entry.file}.`);
      }
    }
  }

  async function fetchVerifiedArtifact(entry) {
    const url = entry.url || ("./firmware/" + entry.file);
    const resp = await fetch(url);
    if (!resp.ok) throw new Error("Could not download " + entry.file);
    const buffer = await resp.arrayBuffer();
    await verifyBundledArtifact(entry, buffer);
    return buffer;
  }

  async function downloadVerifiedArtifact(entry) {
    const buffer = await fetchVerifiedArtifact(entry);
    const url = URL.createObjectURL(new Blob([buffer], { type: "application/octet-stream" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = entry.file;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  // Download Bundled Build Action
  downloadFwBtn.onclick = async () => {
    const idx = fwSelect.value;
    if (idx === "" || !manifest[idx]) {
      setStatus("Select a bundled build from the dropdown first.", "err");
      return;
    }
    const entry = manifest[idx];
    try {
      setStatus(`Verifying ${entry.file}...`, "info");
      await downloadVerifiedArtifact(entry);
      setStatus(`Downloaded and verified ${entry.file}.`, "ok");
    } catch (error) {
      setStatus(error.message || "Firmware verification failed.", "err");
    }
  };

  async function loadFirmwareBinary() {
    if (selectedLocalFile) {
      const file = selectedLocalFile;
      const buf = await file.arrayBuffer();
      return { name: file.name, data: toBinaryString(new Uint8Array(buf)) };
    }

    const idx = fwSelect.value;
    if (idx === "" || !manifest[idx]) {
      throw new Error("Choose a bundled build or upload a .bin file first.");
    }

    const buf = await fetchVerifiedArtifact(manifest[idx]);
    return { name: manifest[idx].file, data: toBinaryString(new Uint8Array(buf)) };
  }

  function toBinaryString(bytes) {
    let s = "";
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      s += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }
    return s;
  }

  // FLASH ACTION
  flashBtn.onclick = async () => {
    let transport;
    try {
      flashBtn.disabled = true;
      readInfoBtn.disabled = true;
      resetProgress();
      resetTelemetry();
      
      setStatus("Preparing firmware...");
      const fw = await loadFirmwareBinary();
      log("Loaded Firmware: " + fw.name + " (" + fw.data.length + " bytes)");

      setStatus("Select the ESP32 port from browser window...");
      const port = await navigator.serial.requestPort();
      transport = new Transport(port, true);

      const targetBaudrate = parseInt(baudrateSelect.value) || 921600;
      const targetOffset = parseInt(offsetInput.value, 16) || 0;
      const shouldErase = eraseFlashChk.checked;

      const esploader = new ESPLoader({
        transport,
        baudrate: targetBaudrate,
        romBaudrate: 115200,
        terminal: espTerminal,
      });

      setStatus("Establishing connection...");
      const chip = await esploader.main();
      log("Detected Target: " + chip, "ok");

      barWrap.style.display = "block";
      setStatus("Flashing device, do not unplug...");
      await esploader.writeFlash({
        fileArray: [{ data: fw.data, address: targetOffset }],
        flashSize: "keep",
        flashMode: "keep",
        flashFreq: "keep",
        eraseAll: shouldErase,
        compress: true,
        reportProgress: (_fileIndex, written, total) => {
          const pct = Math.round((written / total) * 100);
          bar.style.width = pct + "%";
          setStatus("Flashing... " + pct + "%");
        },
      });

      setStatus("Done. Tap RST to boot the new firmware.", "ok");
      log("Flash operation complete.", "ok");
      await esploader.after();
    } catch (e) {
      const msg = e && e.message ? e.message : String(e);
      console.error(e);
      setStatus("Failed: " + msg, "err");
      log("ERROR: " + msg, "err");
      log("If connection fails, hold PRG, tap RST, release PRG, and try again.", "warn");
    } finally {
      try {
        if (transport) await transport.disconnect();
      } catch (_) {}
      flashBtn.disabled = false;
      readInfoBtn.disabled = false;
    }
  };

  // READ INFO ACTION
  readInfoBtn.onclick = async () => {
    let transport;
    try {
      flashBtn.disabled = true;
      readInfoBtn.disabled = true;
      resetProgress();
      resetTelemetry();

      setStatus("Select the ESP32 port from browser window...");
      const port = await navigator.serial.requestPort();
      transport = new Transport(port, true);

      const targetBaudrate = parseInt(baudrateSelect.value) || 921600;

      const esploader = new ESPLoader({
        transport,
        baudrate: targetBaudrate,
        romBaudrate: 115200,
        terminal: espTerminal,
      });

      setStatus("Connecting to device...");
      const chip = await esploader.main();
      
      // Wait briefly for details to be printed by stub
      setStatus("Querying device info...");
      await new Promise(r => setTimeout(r, 1200));

      setStatus("Device information parsed successfully.", "ok");
      await esploader.after();
    } catch (e) {
      const msg = e && e.message ? e.message : String(e);
      console.error(e);
      setStatus("Failed: " + msg, "err");
      log("ERROR: " + msg, "err");
    } finally {
      try {
        if (transport) await transport.disconnect();
      } catch (_) {}
      flashBtn.disabled = false;
      readInfoBtn.disabled = false;
    }
  };

  // ==========================================
  // SERIAL MONITOR INTERACTIVE LOGIC
  // ==========================================

  // Tab switching
  tabLogsBtn.onclick = () => {
    if (activeTab === 'logs') return;
    activeTab = 'logs';
    tabLogsBtn.classList.add("active");
    tabMonitorBtn.classList.remove("active");
    
    // Hide monitor components
    monitorConnectBtn.style.display = "none";
    consoleInputBar.style.display = "none";
    
    // If monitor was connected, disconnect it to avoid clashes
    if (isMonitorConnected) {
      disconnectMonitor();
    }
    
    // Restore logs view
    logEl.style.display = logBufferLogs ? "block" : "none";
    logEl.innerHTML = logBufferLogs;
    logEl.scrollTop = logEl.scrollHeight;
    
    setStatus("Ready.", "info");
  };

  tabMonitorBtn.onclick = () => {
    if (activeTab === 'monitor') return;
    activeTab = 'monitor';
    tabMonitorBtn.classList.add("active");
    tabLogsBtn.classList.remove("active");
    
    // Show monitor Connect button
    monitorConnectBtn.style.display = "inline-flex";
    
    // Set view to monitor buffers
    logEl.style.display = "block";
    logEl.innerHTML = logBufferMonitor || '<div class="info">[Serial Monitor Mode. Select baud rate in Step 2, then click "Connect Monitor" above.]</div>';
    logEl.scrollTop = logEl.scrollHeight;
  };

  monitorConnectBtn.onclick = () => {
    toggleMonitor();
  };

  async function toggleMonitor() {
    if (isMonitorConnected) {
      await disconnectMonitor();
    } else {
      await connectMonitor();
    }
  }

  async function connectMonitor() {
    try {
      setStatus("Requesting serial port...", "info");
      monitorPort = await navigator.serial.requestPort();
      
      const baudRate = parseInt(baudrateSelect.value) || 115200;
      setStatus(`Connecting at ${baudRate} baud...`, "info");
      
      await monitorPort.open({ baudRate });
      isMonitorConnected = true;
      
      monitorConnectBtn.textContent = "Disconnect";
      monitorConnectBtn.classList.add("connected");
      consoleInputBar.style.display = "flex";
      
      logEl.innerHTML = "";
      logBufferMonitor = "";
      log(`--- Serial Monitor Connected at ${baudRate} baud ---\n`, "ok");
      setStatus("Connected to device console.", "ok");
      
      // Disable flashing buttons while active to prevent port locks
      flashBtn.disabled = true;
      readInfoBtn.disabled = true;
      
      // Listen to incoming data stream
      readMonitorLoop();
    } catch (err) {
      const msg = err && err.message ? err.message : String(err);
      console.error(err);
      setStatus("Monitor Failed: " + msg, "err");
      log("\n[Connection Error: " + msg + "]\n", "err");
      await disconnectMonitor();
    }
  }

  async function disconnectMonitor() {
    isMonitorConnected = false;
    monitorConnectBtn.textContent = "Connect Monitor";
    monitorConnectBtn.classList.remove("connected");
    consoleInputBar.style.display = "none";
    
    if (monitorReader) {
      try {
        await monitorReader.cancel();
      } catch (_) {}
    }
    
    if (monitorPort) {
      try {
        await monitorPort.close();
      } catch (_) {}
    }
    
    monitorPort = null;
    monitorReader = null;
    
    log("\n--- Serial Monitor Disconnected ---\n", "info");
    setStatus("Monitor disconnected.", "info");
    
    // Restore flashing actions if browser compatible
    if (isCompatible) {
      flashBtn.disabled = false;
      readInfoBtn.disabled = false;
    }
  }

  async function readMonitorLoop() {
    while (monitorPort && monitorPort.readable && isMonitorConnected) {
      try {
        monitorReader = monitorPort.readable.getReader();
        while (isMonitorConnected) {
          const { value, done } = await monitorReader.read();
          if (done) break;
          const text = new TextDecoder().decode(value);
          log(text);
        }
      } catch (err) {
        console.error(err);
        if (isMonitorConnected) {
          log("\n[Serial connection lost: " + err.message + "]\n", "err");
          break;
        }
      } finally {
        if (monitorReader) {
          monitorReader.releaseLock();
          monitorReader = null;
        }
      }
    }
    if (isMonitorConnected) {
      await disconnectMonitor();
    }
  }

  async function sendMonitorCommand() {
    const cmd = consoleInput.value;
    if (!cmd || !monitorPort || !monitorPort.writable) return;
    
    try {
      const writer = monitorPort.writable.getWriter();
      const encoder = new TextEncoder();
      // Send command string + carriage return
      await writer.write(encoder.encode(cmd + "\r\n"));
      writer.releaseLock();
      
      // Display typed command inside monitor terminal log
      log(`\n> ${cmd}\n`, "cmd-sent");
      consoleInput.value = "";
    } catch (err) {
      log("\n[Failed to transmit command: " + err.message + "]\n", "err");
    }
  }

  consoleSendBtn.onclick = sendMonitorCommand;
  consoleInput.onkeydown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      sendMonitorCommand();
    }
  };
