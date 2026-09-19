import { boards } from "./boards.js";
import { planUpdate, PARTITION_TABLE_OFFSET, PARTITION_TABLE_SIZE } from "./flashplan.js";
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
  const versionSelect = $("version");
  const notesBox = $("release-notes");
  const notesText = $("release-notes-text");
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
  const flashMode = () => (document.querySelector('input[name="flash-mode"]:checked') || {}).value || "update";
  const autoscrollChk = $("autoscroll-chk");

  let manifest = [];
  let selectedLocalFile = null;
  let telemetryData = { chip: '', mac: '', flash: '', features: '' };
  
  // Serial Monitor variables
  let activeTab = 'logs'; // 'logs' | 'monitor'
  let monitorPort = null;
  let monitorReader = null;
  let isMonitorConnected = false;
  // Kept as { msg, cls } lines and always rendered as text. The serial monitor
  // shows whatever the node prints, including chat received from other nodes,
  // so none of it may ever be interpreted as HTML.
  const LOG_BUFFER_LIMIT = 5000;
  let logBufferLogs = [];
  let logBufferMonitor = [];

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
      fwSelect.innerHTML = '<option value="">Nothing published on this channel yet - drag a file below</option>';
      return;
    }
    const target = targetSelect.value;
    let filtered = manifest.map((m, i) => ({ ...m, index: i }));
    const board = boards.find(b => b.flasherId === target);
    filtered = filtered.filter(m => matchesBoard(m, board) && m.file.endsWith(".bin") && !m.file.includes("-ota"));
    
    if (filtered.length === 0) {
      fwSelect.innerHTML = '<option value="">No matching builds found for target - upload a local file below</option>';
    } else {
      fwSelect.replaceChildren(...filtered.map((m) => {
        const option = document.createElement("option");
        option.value = String(m.index);
        option.textContent = m.name || m.file;
        return option;
      }));
    }
  }

  const GITHUB_RELEASES_WEB = "https://github.com/SilentWolf75/AetherMesh/releases";

  function setChannelNote(text, isError) {
    if (!channelNote) return;
    channelNote.textContent = text;
    channelNote.className = isError ? "hint note-error" : "hint";
  }

  // A channel with nothing on it yet. Say so plainly and offer the way
  // forward, rather than leaving an error and a bare URL.
  function setEmptyChannelNote(channel, message) {
    if (!channelNote) return;
    channelNote.className = "hint note-empty";
    const text = document.createElement("span");
    const releases = document.createElement("a");
    releases.href = GITHUB_RELEASES_WEB;
    releases.target = "_blank";
    releases.rel = "noopener";
    releases.textContent = "all releases";
    if (channel === "release") {
      text.textContent = message + " Stable versions appear here once one has been tested on hardware. ";
      const useBeta = document.createElement("button");
      useBeta.type = "button";
      useBeta.className = "secondary compact";
      useBeta.id = "use-beta-btn";
      useBeta.textContent = "Use Beta instead";
      useBeta.onclick = () => {
        if (!channelSelect) return;
        channelSelect.value = "beta";
        channelSelect.dispatchEvent(new Event("change"));
      };
      channelNote.replaceChildren(text, useBeta, document.createTextNode(" or see "), releases, document.createTextNode("."));
    } else {
      text.textContent = message + " See ";
      channelNote.replaceChildren(text, releases, document.createTextNode("."));
    }
  }

  // Each channel's published build is mirrored onto this site by the Pages
  // deploy (tools/mirror_release_channels.py). Browsers cannot download GitHub
  // Release assets from another origin — the download links redirect to a host
  // that sends no CORS headers — so the flasher reads same-origin copies. The
  // manifest is the release's own, so every download is still verified against
  // the SHA-256 that release published.
  let channelIndex = [];

  // Notes come from the release page, which anyone with write access to the
  // repository can edit, so they are shown as text, never as HTML.
  function showNotes(entry) {
    if (!notesBox || !notesText) return;
    const notes = entry && entry.notes ? String(entry.notes) : "";
    notesText.textContent = notes;
    notesBox.style.display = notes ? "block" : "none";
  }

  async function loadVersion(channel, tag) {
    const entry = channelIndex.find((e) => e.tag === tag);
    const base = "./firmware/" + channel + "/" + tag + "/";
    const resp = await fetch(base + "manifest.json?t=" + Date.now());
    if (!resp.ok) throw new Error("That version's files are missing from this site.");
    const list = await resp.json();
    manifest = (Array.isArray(list) ? list : []).map((item) => ({ ...item, url: base + item.file, releaseTag: tag }));
    const label = channel === "beta" ? "Beta " : "Stable ";
    const older = channelIndex.length && channelIndex[0].tag !== tag ? " (an older version)" : "";
    setChannelNote(label + tag + older + ". Verified against the SHA-256 published in that release.", false);
    showNotes(entry);
    populateFirmwareDropdown();
  }

  async function loadChannel(channel) {
    fwSelect.innerHTML = '<option value="">Loading builds...</option>';
    versionSelect.innerHTML = "";
    versionSelect.disabled = true;
    showNotes(null);
    manifest = [];
    try {
      const resp = await fetch("./firmware/" + channel + "/index.json?t=" + Date.now());
      if (!resp.ok) throw new Error(channel === "beta" ? "No beta published yet." : "No stable version published yet.");
      const list = await resp.json();
      channelIndex = Array.isArray(list) ? list.filter((e) => e && e.tag) : [];
      if (!channelIndex.length) throw new Error(channel === "beta" ? "No beta published yet." : "No stable version published yet.");
      for (const [i, entry] of channelIndex.entries()) {
        const option = document.createElement("option");
        option.value = entry.tag;
        const date = entry.published ? " · " + String(entry.published).slice(0, 10) : "";
        option.textContent = entry.tag + date + (i === 0 ? " (newest)" : "");
        versionSelect.appendChild(option);
      }
      versionSelect.disabled = channelIndex.length < 2;
      await loadVersion(channel, channelIndex[0].tag);
    } catch (error) {
      channelIndex = [];
      manifest = [];
      const option = document.createElement("option");
      option.value = "";
      option.textContent = "None";
      versionSelect.appendChild(option);
      const message = error && error.message ? error.message : "Could not load that channel.";
      if (/published yet/.test(message)) {
        setEmptyChannelNote(channel, message);
      } else {
        setChannelNote(message + " Try again, or download from " + GITHUB_RELEASES_WEB + ".", true);
      }
      populateFirmwareDropdown();
    }
  }

  if (versionSelect) {
    versionSelect.addEventListener("change", () => {
      const channel = channelSelect ? channelSelect.value : "release";
      loadVersion(channel, versionSelect.value).catch((error) => {
        manifest = [];
        setChannelNote(error.message || "Could not load that version.", true);
        populateFirmwareDropdown();
      });
    });
  }

  if (channelSelect) {
    channelSelect.addEventListener("change", () => loadChannel(channelSelect.value));
  }

  // Load the selected channel's published builds.
  loadChannel(channelSelect ? channelSelect.value : "release")
    .then(() => {

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
    const buffer = activeTab === 'logs' ? logBufferLogs : logBufferMonitor;
    buffer.push({ msg: String(msg), cls: cls || "" });
    if (buffer.length > LOG_BUFFER_LIMIT) buffer.splice(0, buffer.length - LOG_BUFFER_LIMIT);

    // Parse device telemetry output as it is logged
    parseTelemetry(msg);
  }

  // Rebuilds a log view from buffered lines, as text.
  function renderLog(lines) {
    logEl.replaceChildren();
    for (const line of lines) {
      const div = document.createElement("div");
      if (line.cls) div.className = line.cls;
      div.textContent = line.msg;
      logEl.appendChild(div);
    }
  }

  // Status text often carries file names and error messages from downloads, so
  // it is set as text rather than markup.
  function setStatus(msg, cls) {
    let icon = "⚙";
    if (cls === "ok") icon = "✓";
    if (cls === "err") icon = "⚠";
    const iconEl = document.createElement("span");
    if (cls) iconEl.className = cls;
    iconEl.textContent = icon;
    const msgEl = document.createElement("span");
    msgEl.textContent = msg == null ? "" : String(msg);
    statusEl.replaceChildren(iconEl, document.createTextNode(" "), msgEl);
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
      
      document.querySelector("#uf2-guide h3").textContent = `Install on the ${boardName} by USB drive`;
      const driveStep = document.querySelector("#uf2-guide ol li:nth-child(2) span");
      const driveLabel = document.createElement("strong");
      driveLabel.textContent = driveName;
      driveStep.replaceChildren(
        document.createTextNode("A drive named "), driveLabel,
        document.createTextNode(" appears on your computer."));

      $("download-uf2-btn").textContent = `Download ${boardName} firmware`;
      setStatus(`${boardName} selected. Follow the steps in step 2.`, "info");
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
  const hardwareCards = Array.from(document.querySelectorAll(".hardware-card"));
  hardwareCards.forEach((card, index) => {
    card.onclick = () => {
      hardwareCards.forEach(c => {
        c.classList.remove("selected");
        c.setAttribute("aria-checked", "false");
        c.tabIndex = -1;
      });
      card.classList.add("selected");
      card.setAttribute("aria-checked", "true");
      card.tabIndex = 0;
      targetSelect.value = card.getAttribute("data-val");
      targetSelect.onchange(); // trigger logic directly
    };
    // Radio-group keyboard behaviour: Space/Enter pick, arrows move.
    card.onkeydown = (e) => {
      if (e.key === " " || e.key === "Enter") {
        e.preventDefault();
        card.click();
        return;
      }
      const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
      if (!step) return;
      e.preventDefault();
      const next = hardwareCards[(index + step + hardwareCards.length) % hardwareCards.length];
      next.focus();
      next.click();
    };
  });

  // Reboot an nRF52 board into its UF2 bootloader. The Adafruit bootloader
  // these boards use treats opening the USB serial port at 1200 baud and then
  // closing it as a request to enter the bootloader, so no button is needed.
  // Firmware that predates USB serial handling ignores it, hence the hint.
  $("enter-bootloader-btn").onclick = async () => {
    if (!("serial" in navigator)) {
      setStatus("This browser cannot open serial ports. Use Chrome or Edge, or double-tap RST.", "err");
      return;
    }
    let port;
    try {
      port = await navigator.serial.requestPort();
      await port.open({ baudRate: 1200 });
      try { await port.setSignals({ dataTerminalReady: false }); } catch (_) {}
      await port.close();
      setStatus("Sent. The board should reappear as a USB drive in a few seconds.", "ok");
    } catch (error) {
      try { if (port) await port.close(); } catch (_) {}
      const msg = error && error.message ? error.message : String(error);
      // Choosing no port is a cancel, not a failure.
      if (/No port selected|cancel/i.test(msg)) { setStatus("No port chosen.", "info"); return; }
      setStatus("Could not reach the board (" + msg + "). Double-tap RST instead.", "err");
    }
  };

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
      const which = channelSelect && channelSelect.value === "beta" ? "Beta" : "Stable";
      setStatus("No " + boardName + " UF2 build on the " + which + " channel yet. Try the other channel.", "err");
    }
  };

  // Clear Logs
  clearLogsBtn.onclick = () => {
    logEl.innerHTML = "";
    logEl.style.display = "none";
    if (activeTab === 'logs') {
      logBufferLogs = [];
      setStatus("");
      resetProgress();
      resetTelemetry();
    } else {
      logBufferMonitor = [];
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
  dropzone.onkeydown = (e) => {
    if (e.key === " " || e.key === "Enter") {
      e.preventDefault();
      fileInput.click();
    }
  };

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
      return { name: file.name, bytes: new Uint8Array(buf), data: toBinaryString(new Uint8Array(buf)) };
    }

    const idx = fwSelect.value;
    if (idx === "" || !manifest[idx]) {
      throw new Error("Choose a bundled build or upload a .bin file first.");
    }

    const buf = await fetchVerifiedArtifact(manifest[idx]);
    return { name: manifest[idx].file, bytes: new Uint8Array(buf), data: toBinaryString(new Uint8Array(buf)) };
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
      const mode = flashMode();

      const esploader = new ESPLoader({
        transport,
        baudrate: targetBaudrate,
        romBaudrate: 115200,
        terminal: espTerminal,
      });

      setStatus("Establishing connection...");
      const chip = await esploader.main();
      log("Detected Target: " + chip, "ok");

      // Update writes around the settings partition so the node keeps its
      // name, channels and identity key; anything that would land on them is
      // refused before a byte is written. Full erase is the deliberate reset.
      let fileArray = [{ data: fw.data, address: targetOffset }];
      if (mode === "update") {
        setStatus("Reading the board's partition table...");
        let deviceTable = null;
        try {
          deviceTable = await esploader.readFlash(PARTITION_TABLE_OFFSET, PARTITION_TABLE_SIZE);
        } catch (e) {
          log("Could not read partition table: " + (e && e.message ? e.message : e), "warn");
        }
        const plan = planUpdate(fw.bytes, targetOffset, deviceTable);
        if (!plan.ok) throw new Error(plan.reason);
        fileArray = plan.segments.map((seg) => ({ data: fw.data.substring(seg.start, seg.end), address: seg.address }));
        if (plan.keeps) {
          log("Keeping settings at 0x" + plan.keeps.offset.toString(16) + " (" + plan.keeps.size + " bytes).", "ok");
        }
      } else {
        log("Full erase: this node's settings and identity key will be reset.", "warn");
      }

      barWrap.style.display = "block";
      setStatus("Flashing device, do not unplug...");
      await esploader.writeFlash({
        fileArray,
        flashSize: "keep",
        flashMode: "keep",
        flashFreq: "keep",
        eraseAll: mode === "full",
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
    logEl.style.display = logBufferLogs.length ? "block" : "none";
    renderLog(logBufferLogs);
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
    renderLog(logBufferMonitor.length ? logBufferMonitor : [{
      msg: '[Serial Monitor Mode. Select baud rate in Step 2, then click "Connect Monitor" above.]', cls: "info" }]);
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
      
      logEl.replaceChildren();
      logBufferMonitor = [];
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
