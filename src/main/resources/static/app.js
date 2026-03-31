const COACH_ENABLED = document.body && document.body.dataset.coachEnabled === "true";
const GEMINI_KEY_STORAGE_KEY = "userGeminiApiKey";

const els = {
    modeRadios: Array.from(document.querySelectorAll("input[name=\"inputMode\"]")),
    tailorButton: document.getElementById("tailorButton"),
    bottomTailorButton: document.getElementById("bottomTailorButton"),
    copyOutputButton: document.getElementById("copyOutputButton"),
    copyButton: document.getElementById("copyButton"),
    bottomApplyButton: document.getElementById("bottomApplyButton"),

    statusPill: document.getElementById("statusPill"),
    statusLabel: document.getElementById("statusLabel"),
    statusTime: document.getElementById("statusTime"),

    inputsColumn: document.getElementById("inputsColumn"),
    resultsColumn: document.getElementById("resultsColumn"),

    mobilePrimaryTabs: Array.from(document.querySelectorAll(".mobile-primary-tabs .tab")),
    resultsTabButtons: Array.from(document.querySelectorAll("#resultsTabs .tab-btn")),
    tabReview: document.getElementById("tabReview"),
    tabInsights: document.getElementById("tabInsights"),
    tabExport: document.getElementById("tabExport"),

    summaryStrip: document.getElementById("summaryStrip"),
    summaryBullets: document.getElementById("summaryBullets"),
    summaryRejected: document.getElementById("summaryRejected"),
    summaryCoverage: document.getElementById("summaryCoverage"),
    summaryAtsWrap: document.getElementById("summaryAtsWrap"),
    summaryAts: document.getElementById("summaryAts"),
    summaryWarningsButton: document.getElementById("summaryWarningsButton"),
    summaryWarnings: document.getElementById("summaryWarnings"),
    summaryAcceptSafe: document.getElementById("summaryAcceptSafe"),
    summaryShowRejected: document.getElementById("summaryShowRejected"),
    summaryClearFilters: document.getElementById("summaryClearFilters"),

    latexInputBlock: document.getElementById("latexInputBlock"),
    uploadInputBlock: document.getElementById("uploadInputBlock"),
    resumeLatex: document.getElementById("resumeLatex"),
    geminiApiKey: document.getElementById("geminiApiKey"),
    toggleApiKeyVisibility: document.getElementById("toggleApiKeyVisibility"),
    clearApiKeyButton: document.getElementById("clearApiKeyButton"),
    apiKeyHint: document.getElementById("apiKeyHint"),
    jobDescription: document.getElementById("jobDescription"),
    clearInputsButton: document.getElementById("clearInputsButton"),
    helperText: document.getElementById("helperText"),

    dropzone: document.getElementById("dropzone"),
    resumeFile: document.getElementById("resumeFile"),
    fileSummary: document.getElementById("fileSummary"),
    removeFileButton: document.getElementById("removeFileButton"),

    reviewSearch: document.getElementById("reviewSearch"),
    pinnedKeywords: document.getElementById("pinnedKeywords"),
    filterRejected: document.getElementById("filterRejected"),
    filterChanged: document.getElementById("filterChanged"),
    bulkAcceptSafe: document.getElementById("bulkAcceptSafe"),
    bulkRejectAll: document.getElementById("bulkRejectAll"),
    bulkResetEdits: document.getElementById("bulkResetEdits"),
    clearFilters: document.getElementById("clearFilters"),
    changesContainer: document.getElementById("changesContainer"),

    updatedLatex: document.getElementById("updatedLatex"),
    exportBulletsList: document.getElementById("exportBulletsList"),
    outputTitle: document.getElementById("outputTitle"),
    exportHint: document.getElementById("exportHint"),
    applyChangesButton: document.getElementById("applyChangesButton"),
    applyStatus: document.getElementById("applyStatus"),
    warnings: document.getElementById("warnings"),

    coveragePercent: document.getElementById("coveragePercent"),
    coverageBar: document.getElementById("coverageBar"),
    matchedKeywords: document.getElementById("matchedKeywords"),
    missingKeywords: document.getElementById("missingKeywords"),
    copyMissingKeywords: document.getElementById("copyMissingKeywords"),
    pinMissingKeywords: document.getElementById("pinMissingKeywords"),
    insightsNote: document.getElementById("insightsNote"),

    atsScore: document.getElementById("atsScore"),
    atsBar: document.getElementById("atsBar"),
    mustHaveKeywords: document.getElementById("mustHaveKeywords"),
    niceToHaveKeywords: document.getElementById("niceToHaveKeywords"),
    bulletHeatmap: document.getElementById("bulletHeatmap"),
    atsNote: document.getElementById("atsNote"),

    generalSuggestions: document.getElementById("generalSuggestions"),

    toastHost: document.getElementById("toastHost"),

    coachFab: document.getElementById("coachFab"),
    coachUnread: document.getElementById("coachUnread"),
    coachScrim: document.getElementById("coachScrim"),
    coachDrawer: document.getElementById("coachDrawer"),
    coachResizeHandle: document.getElementById("coachResizeHandle"),
    coachClose: document.getElementById("coachClose"),
    coachExpand: document.getElementById("coachExpand"),
    coachContext: document.getElementById("coachContext"),
    coachMessages: document.getElementById("coachMessages"),
    coachQuickChips: document.getElementById("coachQuickChips"),
    coachInput: document.getElementById("coachInput"),
    coachSend: document.getElementById("coachSend")
};

const state = {
    mode: "upload",
    activeTopTab: "inputs", // mobile only: inputs|results
    activeResultsTab: "review", // review|insights|export
    run: { status: "idle", label: "Ready", at: "" },
    bullets: [],
    warnings: [],
    keywordInsights: null,
    atsInsights: null,
    suggestions: [],
    pinned: new Set(),
    filters: { query: "", onlyRejected: false, onlyChanged: false },
    file: null,
    clientConfig: { userGeminiKeyRequired: true },
    userGeminiApiKey: "",
    chat: { open: false, pending: false, unread: false, messages: [], width: null, expanded: false }
};

function clamp(n, min, max) {
    return Math.max(min, Math.min(max, n));
}

function loadUserGeminiApiKey() {
    try {
        const raw = sessionStorage.getItem(GEMINI_KEY_STORAGE_KEY);
        state.userGeminiApiKey = String(raw || "").trim();
    } catch {
        state.userGeminiApiKey = "";
    }
    if (els.geminiApiKey) {
        els.geminiApiKey.value = state.userGeminiApiKey;
    }
}

function saveUserGeminiApiKey(value) {
    state.userGeminiApiKey = String(value || "").trim();
    try {
        if (state.userGeminiApiKey) {
            sessionStorage.setItem(GEMINI_KEY_STORAGE_KEY, state.userGeminiApiKey);
        } else {
            sessionStorage.removeItem(GEMINI_KEY_STORAGE_KEY);
        }
    } catch {
        // ignore
    }
}

function renderApiKeyUi() {
    if (!els.apiKeyHint || !els.geminiApiKey) return;
    const required = !!state.clientConfig.userGeminiKeyRequired;
    els.geminiApiKey.setAttribute("aria-required", required ? "true" : "false");
    els.apiKeyHint.textContent = required
        ? "Create your own Gemini API key in Google AI Studio. Google offers a free tier, enter your key to continue."
        : "Create your own Gemini API key in Google AI Studio. Google offers a free tier, limits may apply.";
}

function getUserGeminiApiKey() {
    const value = els.geminiApiKey ? els.geminiApiKey.value : state.userGeminiApiKey;
    return String(value || "").trim();
}

function buildAiRequestHeaders(includeJsonContentType) {
    const headers = {};
    if (includeJsonContentType) {
        headers["Content-Type"] = "application/json";
    }
    const apiKey = getUserGeminiApiKey();
    if (apiKey) {
        headers["X-Gemini-Api-Key"] = apiKey;
    }
    return headers;
}

function ensureGeminiApiKeyReady() {
    const apiKey = getUserGeminiApiKey();
    saveUserGeminiApiKey(apiKey);
    if (!state.clientConfig.userGeminiKeyRequired || apiKey) {
        return true;
    }

    state.warnings = ["Enter your Gemini API key to continue."];
    setRunStatus("error", "Missing API key");
    renderAll();
    toast("Enter your Gemini API key to continue.", "warn");
    setActiveTopTab("inputs");
    if (els.geminiApiKey) {
        els.geminiApiKey.focus();
    }
    return false;
}

async function loadClientConfig() {
    try {
        const response = await fetch("/api/resume-tailor/client-config");
        if (!response.ok) return;
        const payload = await response.json();
        state.clientConfig.userGeminiKeyRequired = !!payload.userGeminiKeyRequired;
    } catch {
        // keep defaults
    }
    renderApiKeyUi();
}

function loadCoachPrefs() {
    if (!COACH_ENABLED) return;
    try {
        const raw = localStorage.getItem("coachPrefs");
        if (!raw) return;
        const data = JSON.parse(raw);
        const w = Number(data.width);
        if (Number.isFinite(w)) state.chat.width = w;
        state.chat.expanded = !!data.expanded;
    } catch {
        // ignore
    }
}

function saveCoachPrefs() {
    if (!COACH_ENABLED) return;
    try {
        localStorage.setItem("coachPrefs", JSON.stringify({
            width: state.chat.width,
            expanded: state.chat.expanded
        }));
    } catch {
        // ignore
    }
}

function applyCoachWidth() {
    if (!COACH_ENABLED) return;
    if (!els.coachDrawer) return;
    const defaultWidth = 420;
    const expandedWidth = 720;
    const minW = 360;
    const maxW = Math.min(860, Math.floor(window.innerWidth * 0.92));

    let w = state.chat.width;
    if (!Number.isFinite(w)) w = state.chat.expanded ? expandedWidth : defaultWidth;
    if (state.chat.expanded && (!Number.isFinite(state.chat.width))) w = expandedWidth;
    w = clamp(w, minW, maxW);
    state.chat.width = w;
    els.coachDrawer.style.setProperty("--coach-width", `${w}px`);
    if (els.coachExpand) els.coachExpand.textContent = state.chat.expanded ? "Collapse" : "Expand";
    saveCoachPrefs();
}

function isMobileLayout() {
    return window.matchMedia && window.matchMedia("(max-width: 980px)").matches;
}

function nowTimeLabel() {
    const d = new Date();
    const hh = String(d.getHours()).padStart(2, "0");
    const mm = String(d.getMinutes()).padStart(2, "0");
    return `${hh}:${mm}`;
}

function setRunStatus(status, label) {
    state.run.status = status;
    state.run.label = label || state.run.label;
    state.run.at = nowTimeLabel();
    renderStatus();
}

function renderStatus() {
    els.statusLabel.textContent = state.run.label;
    els.statusTime.textContent = state.run.at ? ` ${state.run.at}` : "";
    els.statusPill.classList.remove("running", "error", "success");
    if (state.run.status === "running") els.statusPill.classList.add("running");
    if (state.run.status === "error") els.statusPill.classList.add("error");
    if (state.run.status === "success") els.statusPill.classList.add("success");
}

function toast(message, kind) {
    const node = document.createElement("div");
    node.className = `toast ${kind || "info"}`;
    node.innerHTML = `<span class="toast-dot" aria-hidden="true"></span><p>${escapeHtml(message)}</p>`;
    els.toastHost.appendChild(node);
    setTimeout(() => {
        node.style.opacity = "0";
        node.style.transform = "translateY(2px)";
        setTimeout(() => node.remove(), 220);
    }, 2600);
}

function setCoachUnread(on) {
    if (!COACH_ENABLED) return;
    state.chat.unread = !!on;
    if (!els.coachUnread) return;
    els.coachUnread.style.display = state.chat.unread ? "" : "none";
}

function isCoachOpen() {
    if (!COACH_ENABLED) return false;
    return !!state.chat.open;
}

function openCoach() {
    if (!COACH_ENABLED) return;
    state.chat.open = true;
    setCoachUnread(false);
    if (els.coachScrim) {
        els.coachScrim.hidden = false;
        requestAnimationFrame(() => els.coachScrim.classList.add("open"));
    }
    if (els.coachDrawer) {
        els.coachDrawer.hidden = false;
        applyCoachWidth();
        requestAnimationFrame(() => els.coachDrawer.classList.add("open"));
        els.coachDrawer.setAttribute("aria-hidden", "false");
    }
    renderCoach();
    if (els.coachInput) {
        setTimeout(() => els.coachInput.focus(), 50);
    }
}

function closeCoach() {
    if (!COACH_ENABLED) return;
    state.chat.open = false;
    if (els.coachScrim) {
        els.coachScrim.classList.remove("open");
        setTimeout(() => { if (!state.chat.open) els.coachScrim.hidden = true; }, 180);
    }
    if (els.coachDrawer) {
        els.coachDrawer.classList.remove("open");
        els.coachDrawer.setAttribute("aria-hidden", "true");
        setTimeout(() => { if (!state.chat.open) els.coachDrawer.hidden = true; }, 210);
    }
}

function toggleCoach() {
    if (!COACH_ENABLED) return;
    if (isCoachOpen()) closeCoach();
    else openCoach();
}

function coachSystemDivider(text) {
    if (!COACH_ENABLED) return;
    state.chat.messages.push({ role: "system", content: text });
    if (!isCoachOpen()) setCoachUnread(true);
    renderCoach();
}

function coachAddAssistant(content, actions) {
    if (!COACH_ENABLED) return;
    state.chat.messages.push({ role: "assistant", content: normalizeWs(content), actions: actions || [] });
    if (!isCoachOpen()) setCoachUnread(true);
    renderCoach();
}

function coachAddUser(content) {
    if (!COACH_ENABLED) return;
    state.chat.messages.push({ role: "user", content: normalizeWs(content) });
    renderCoach();
}

function coachQuickPrompts() {
    return [
        "Make these bullets more impact-focused without adding new claims.",
        "Shorten the bullets and remove filler words.",
        "Explain which bullets are weak for this job and why.",
        "Suggest safer rewrites for the rejected bullets.",
        "Which keywords can I truthfully incorporate based on the current bullets?"
    ];
}

function renderCoachContext() {
    if (!COACH_ENABLED) return;
    if (!els.coachContext) return;
    const bullets = state.bullets.length;
    const rejected = state.bullets.filter((b) => b.safetyRejected).length;
    const coverage = state.keywordInsights ? Number(state.keywordInsights.coveragePercent || 0) : 0;
    const ats = state.atsInsights ? Number(state.atsInsights.overallScore || 0) : null;

    const pills = [];
    pills.push(`<span class="pill">Mode: ${escapeHtml(state.mode)}</span>`);
    pills.push(`<span class="pill">Bullets: ${bullets}</span>`);
    pills.push(`<span class="pill">Rejected: ${rejected}</span>`);
    pills.push(`<span class="pill">Coverage: ${Math.max(0, Math.min(100, coverage))}%</span>`);
    if (ats !== null && state.mode === "upload") {
        pills.push(`<span class="pill">ATS: ${Math.max(0, Math.min(100, ats))}</span>`);
    }
    els.coachContext.innerHTML = pills.join("");
}

function renderCoachQuickChips() {
    if (!COACH_ENABLED) return;
    if (!els.coachQuickChips) return;
    els.coachQuickChips.innerHTML = "";
    coachQuickPrompts().slice(0, 6).forEach((text) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "chip chip-button";
        btn.dataset.action = "coachQuick";
        btn.dataset.prompt = text;
        btn.textContent = text;
        els.coachQuickChips.appendChild(btn);
    });
}

function renderCoach() {
    if (!COACH_ENABLED) return;
    if (!els.coachMessages) return;
    renderCoachContext();
    renderCoachQuickChips();

    els.coachMessages.innerHTML = "";
    const msgs = state.chat.messages || [];
    if (msgs.length === 0) {
        const empty = document.createElement("div");
        empty.className = "coach-msg assistant";
        empty.innerHTML = `<p>${escapeHtml("Ask me to improve bullets, explain rejections, or increase job alignment without inventing experience.")}</p>`;
        els.coachMessages.appendChild(empty);
        return;
    }

    msgs.slice(-80).forEach((m, idx) => {
        const node = document.createElement("div");
        const role = m.role === "user" || m.role === "assistant" || m.role === "system" ? m.role : "assistant";
        node.className = `coach-msg ${role}`;
        node.innerHTML = `<p>${escapeHtml(m.content || "")}</p>`;

        if (role === "assistant" && m.actions && m.actions.length > 0) {
            const actionsWrap = document.createElement("div");
            actionsWrap.className = "coach-actions";
            m.actions.forEach((a) => {
                actionsWrap.appendChild(renderCoachActionCard(a));
            });
            node.appendChild(actionsWrap);
        }

        els.coachMessages.appendChild(node);

        // Keep newest visible.
        if (idx === msgs.length - 1) {
            setTimeout(() => {
                try { node.scrollIntoView({ block: "end", behavior: "smooth" }); } catch { /* ignore */ }
            }, 0);
        }
    });
}

function renderCoachActionCard(action) {
    const type = (action && action.type) ? String(action.type) : "";
    const card = document.createElement("div");
    card.className = "coach-action";

    if (type === "edit_bullet") {
        const idx = Number(action.bulletIndex);
        const safetyRejected = !!action.safetyRejected;
        const title = Number.isFinite(idx) ? `Edit Bullet ${idx + 1}` : "Edit Bullet";
        const reason = action.reason || "";
        const suggested = action.suggestedText || "";
        const rejectedDraft = action.rejectedDraft || "";

        card.innerHTML = `
            <h4>${escapeHtml(title)}${safetyRejected ? `<span class="coach-badge-rejected">SAFETY REJECTED</span>` : ""}</h4>
            <p class="muted">${escapeHtml(reason)}</p>
            ${safetyRejected ? `
                <details>
                    <summary>Rejected draft</summary>
                    <p>${escapeHtml(rejectedDraft)}</p>
                </details>
            ` : `
                <details>
                    <summary>Suggested text</summary>
                    <p>${escapeHtml(suggested)}</p>
                </details>
            `}
            <div class="row">
                <button type="button" class="secondary" data-action="coachViewReview" data-index="${escapeHtml(String(idx))}">View in Review</button>
                ${(!safetyRejected && Number.isFinite(idx)) ? `<button type="button" class="primary" data-action="coachApplyEdit" data-index="${escapeHtml(String(idx))}" data-text="${escapeHtml(suggested)}">Apply</button>` : ""}
            </div>
        `;
        return card;
    }

    if (type === "pin_keywords") {
        const reason = action.reason || "";
        const kws = Array.isArray(action.keywords) ? action.keywords : [];
        card.innerHTML = `
            <h4>Pin Keywords</h4>
            <p class="muted">${escapeHtml(reason)}</p>
            <div class="chips">${kws.slice(0, 12).map((kw) => `<button type="button" class="chip chip-button" data-action="coachPinKeyword" data-keyword="${escapeHtml(kw)}">${escapeHtml(kw)}</button>`).join("")}</div>
        `;
        return card;
    }

    if (type === "ask_user") {
        const reason = action.reason || "";
        const question = action.question || "";
        card.innerHTML = `
            <h4>Quick Question</h4>
            <p class="muted">${escapeHtml(reason)}</p>
            <p>${escapeHtml(question)}</p>
            <div class="row">
                <button type="button" class="secondary" data-action="coachUseQuestion" data-question="${escapeHtml(question)}">Use this</button>
            </div>
        `;
        return card;
    }

    card.innerHTML = `
        <h4>Coach Action</h4>
        <p class="muted">${escapeHtml(action && action.reason ? action.reason : "Suggested by coach.")}</p>
    `;
    return card;
}

function escapeHtml(value) {
    return String(value || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function normalizeWs(value) {
    return String(value || "").trim().replaceAll(/\\s+/g, " ");
}

function disableActions(disabled) {
    els.tailorButton.disabled = disabled;
    els.bottomTailorButton.disabled = disabled;
    els.applyChangesButton.disabled = disabled;
    els.bottomApplyButton.disabled = disabled;
    els.copyButton.disabled = disabled;
    els.copyOutputButton.disabled = disabled;
}

function setFile(file) {
    state.file = file;
    if (!file) {
        els.fileSummary.style.display = "none";
        els.removeFileButton.style.display = "none";
        els.fileSummary.textContent = "";
        els.resumeFile.value = "";
        return;
    }
    const sizeKb = Math.round(file.size / 1024);
    els.fileSummary.textContent = `${file.name} (${sizeKb} KB)`;
    els.fileSummary.style.display = "";
    els.removeFileButton.style.display = "";
}

function setActiveTopTab(tab) {
    state.activeTopTab = tab === "results" ? "results" : "inputs";
    els.mobilePrimaryTabs.forEach((btn) => btn.classList.toggle("active", btn.dataset.primaryTab === state.activeTopTab));

    if (!isMobileLayout()) {
        els.inputsColumn.style.display = "";
        els.resultsColumn.style.display = "";
        return;
    }

    els.inputsColumn.style.display = state.activeTopTab === "inputs" ? "" : "none";
    els.resultsColumn.style.display = state.activeTopTab === "results" ? "" : "none";
}

function setActiveResultsTab(tab) {
    state.activeResultsTab = ["review", "insights", "export"].includes(tab) ? tab : "review";

    els.resultsTabButtons.forEach((btn) => {
        const active = btn.dataset.tab === state.activeResultsTab;
        btn.classList.toggle("active", active);
        btn.setAttribute("aria-selected", active ? "true" : "false");
    });

    els.tabReview.style.display = state.activeResultsTab === "review" ? "" : "none";
    els.tabInsights.style.display = state.activeResultsTab === "insights" ? "" : "none";
    els.tabExport.style.display = state.activeResultsTab === "export" ? "" : "none";
}

function setMode(mode) {
    state.mode = mode === "latex" ? "latex" : "upload";
    state.bullets = [];
    state.warnings = [];
    state.keywordInsights = null;
    state.atsInsights = null;
    state.suggestions = [];
    state.pinned = new Set();
    state.filters = { query: "", onlyRejected: false, onlyChanged: false };
    setFile(null);
    els.updatedLatex.value = "";

    if (state.mode === "upload") {
        els.uploadInputBlock.style.display = "";
        els.latexInputBlock.style.display = "none";
        els.outputTitle.textContent = "Accepted Bullets";
        els.exportHint.textContent = "Apply accepted bullets to produce a copy-ready bullet list.";
        els.applyChangesButton.textContent = "Build bullet list";
        els.updatedLatex.placeholder = "Accepted bullets will appear here...";
        els.helperText.textContent = "Tip: Upload mode is best if you only have a PDF.";
    } else {
        els.uploadInputBlock.style.display = "none";
        els.latexInputBlock.style.display = "";
        els.outputTitle.textContent = "Updated LaTeX";
        els.exportHint.textContent = "Apply accepted bullets to update LaTeX safely.";
        els.applyChangesButton.textContent = "Apply to LaTeX";
        els.updatedLatex.placeholder = "Updated LaTeX will appear here...";
        els.helperText.textContent = "Tip: switch to Upload mode if you only have a PDF.";
    }

    setActiveResultsTab("review");
    renderAll();
    renderCoach();
}

function buildBulletStateFromChanges(changes) {
    return (changes || []).map((c) => {
        const safetyRejected = !!c.safetyRejected;
        const revised = normalizeWs(c.revisedBullet || "");
        const original = normalizeWs(c.originalBullet || "");
        return {
            accepted: !safetyRejected,
            original,
            suggested: revised,
            editedText: revised,
            reason: c.reason || "",
            safetyRejected,
            rejectedDraft: c.rejectedDraft || null,
            showDiff: true,
            diffCacheKey: "",
            diffCacheHtml: ""
        };
    });
}

function isChanged(b) {
    return normalizeWs(b.editedText) !== normalizeWs(b.original);
}

function currentFilteredBullets() {
    const q = normalizeWs(state.filters.query).toLowerCase();
    return state.bullets
        .map((b, idx) => ({ b, idx }))
        .filter(({ b }) => {
            if (state.filters.onlyRejected && !b.safetyRejected) return false;
            if (state.filters.onlyChanged && !isChanged(b)) return false;
            if (!q) return true;
            const hay = `${b.original} ${b.editedText} ${b.reason} ${(b.rejectedDraft || "")}`.toLowerCase();
            return hay.includes(q);
        });
}

function renderPinnedKeywords() {
    els.pinnedKeywords.innerHTML = "";
    if (!state.pinned || state.pinned.size === 0) return;

    Array.from(state.pinned.values()).slice(0, 20).forEach((kw) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "chip chip-button";
        btn.dataset.action = "pinned";
        btn.dataset.keyword = kw;
        btn.textContent = kw;
        els.pinnedKeywords.appendChild(btn);
    });
}

function renderWarnings() {
    els.warnings.innerHTML = "";
    if (!state.warnings || state.warnings.length === 0) return;
    state.warnings.forEach((w) => {
        const li = document.createElement("li");
        li.textContent = w;
        els.warnings.appendChild(li);
    });
}

function renderAll() {
    renderStatus();
    renderPinnedKeywords();
    renderWarnings();
    renderKeywordInsights();
    renderAtsInsights();
    renderSuggestions();
    renderReview();
    renderSummaryStrip();
    renderExportBulletsList();
}

function renderExportBulletsList() {
    if (!els.exportBulletsList) return;

    // Only show the friendly list in Upload mode export. LaTeX mode is better represented as LaTeX text.
    if (state.mode !== "upload") {
        els.exportBulletsList.style.display = "none";
        els.updatedLatex.style.display = "";
        return;
    }

    const accepted = [];
    state.bullets.forEach((b) => {
        if (!b.accepted) return;
        const t = normalizeWs(b.editedText);
        if (!t) return;
        accepted.push(t);
    });

    // If user hasn't applied yet, keep textarea visible as the single output source of truth.
    const outputText = String(els.updatedLatex.value || "").trim();
    if (!outputText || accepted.length === 0) {
        els.exportBulletsList.style.display = "none";
        els.updatedLatex.style.display = "";
        return;
    }

    els.exportBulletsList.innerHTML = "";
    accepted.forEach((text, idx) => {
        const item = document.createElement("div");
        item.className = "export-bullet";
        item.innerHTML = `
            <div class="export-bullet-head">
                <strong>Bullet ${idx + 1}</strong>
                <button type="button" class="secondary" data-action="copyOneBullet" data-text="${escapeHtml(`- ${text}`)}">Copy</button>
            </div>
            <code>${escapeHtml(`- ${text}`)}</code>
        `;
        els.exportBulletsList.appendChild(item);
    });

    // Show both: textarea for "copy all", list for per-bullet copy.
    els.updatedLatex.style.display = "";
    els.exportBulletsList.style.display = "";
}

function renderKeywordInsights() {
    const insights = state.keywordInsights;
    if (!insights) {
        els.coveragePercent.textContent = "0%";
        els.coverageBar.style.width = "0%";
        els.matchedKeywords.innerHTML = "";
        els.missingKeywords.innerHTML = "";
        els.insightsNote.textContent = "Run Tailor to populate insights.";
        return;
    }

    const percent = Math.max(0, Math.min(100, Number(insights.coveragePercent || 0)));
    els.coveragePercent.textContent = `${percent}%`;
    els.coverageBar.style.width = `${percent}%`;

    els.matchedKeywords.innerHTML = "";
    (insights.matchedKeywords || []).forEach((kw) => {
        const li = document.createElement("li");
        li.innerHTML = `<button type="button" class="chip chip-button kw-present" data-action="filterKeyword" data-keyword="${escapeHtml(kw)}">${escapeHtml(kw)}</button>`;
        els.matchedKeywords.appendChild(li);
    });

    els.missingKeywords.innerHTML = "";
    (insights.missingKeywords || []).forEach((kw) => {
        const li = document.createElement("li");
        li.innerHTML = `<button type="button" class="chip chip-button kw-missing" data-action="pinKeyword" data-keyword="${escapeHtml(kw)}">${escapeHtml(kw)}</button>`;
        els.missingKeywords.appendChild(li);
    });

    els.insightsNote.textContent = (insights.notes && insights.notes.length > 0) ? insights.notes[0] : "Keyword insights updated.";
}

function renderAtsInsights() {
    const ats = state.atsInsights;
    if (!ats) {
        els.atsScore.textContent = "0";
        els.atsBar.style.width = "0%";
        els.mustHaveKeywords.innerHTML = "";
        els.niceToHaveKeywords.innerHTML = "";
        els.bulletHeatmap.innerHTML = "";
        els.atsNote.textContent = "Run Tailor to populate ATS insights.";
        return;
    }

    const score = Math.max(0, Math.min(100, Number(ats.overallScore || 0)));
    els.atsScore.textContent = String(score);
    els.atsBar.style.width = `${score}%`;

    const renderAtsList = (target, items) => {
        target.innerHTML = "";
        (items || []).slice(0, 18).forEach((k) => {
            const present = !!k.present;
            const refs = present ? (k.evidenceBulletIds || []) : (k.suggestionBulletIds || []);
            const kw = k.keyword || "";
            const li = document.createElement("li");
            li.innerHTML = `
                <button type="button"
                        class="chip chip-button ${present ? "kw-present" : "kw-missing"}"
                        data-action="filterKeyword"
                        data-keyword="${escapeHtml(kw)}">${escapeHtml(kw)}</button>
                ${escapeHtml(refs.join(", "))}
            `;
            target.appendChild(li);
        });
    };

    renderAtsList(els.mustHaveKeywords, ats.mustHave);
    renderAtsList(els.niceToHaveKeywords, ats.niceToHave);

    els.bulletHeatmap.innerHTML = "";
    (ats.bulletHeatmap || []).forEach((b) => {
        const card = document.createElement("div");
        card.className = "heat-card";
        const kw = (b.matchedKeywords || []).slice(0, 10);
        card.innerHTML = `
            <strong>${escapeHtml(b.bulletId || "")}</strong>
            <div class="chips">${kw.map((x) => `<button type="button" class="chip chip-button" data-action="filterKeyword" data-keyword="${escapeHtml(x)}">${escapeHtml(x)}</button>`).join("")}</div>
        `;
        els.bulletHeatmap.appendChild(card);
    });

    els.atsNote.textContent = (ats.notes && ats.notes.length > 0) ? ats.notes[0] : "ATS insights updated.";
}

function renderSuggestions() {
    els.generalSuggestions.innerHTML = "";
    if (!state.suggestions || state.suggestions.length === 0) return;
    state.suggestions.slice(0, 24).forEach((s) => {
        const li = document.createElement("li");
        li.textContent = s;
        els.generalSuggestions.appendChild(li);
    });
}

function tokenizeWords(text) {
    return String(text || "")
        .replaceAll(/\\s+/g, " ")
        .trim()
        .split(" ")
        .filter(Boolean);
}

function computeDiffHtml(original, revised) {
    const a = tokenizeWords(original);
    const b = tokenizeWords(revised);
    const n = a.length;
    const m = b.length;
    const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));

    for (let i = n - 1; i >= 0; i--) {
        for (let j = m - 1; j >= 0; j--) {
            dp[i][j] = a[i] === b[j] ? 1 + dp[i + 1][j + 1] : Math.max(dp[i + 1][j], dp[i][j + 1]);
        }
    }

    const parts = [];
    let i = 0;
    let j = 0;
    while (i < n && j < m) {
        if (a[i] === b[j]) {
            parts.push(escapeHtml(a[i]));
            i++;
            j++;
            continue;
        }
        if (dp[i + 1][j] >= dp[i][j + 1]) {
            parts.push(`<span class="diff-removed">${escapeHtml(a[i])}</span>`);
            i++;
        } else {
            parts.push(`<span class="diff-added">${escapeHtml(b[j])}</span>`);
            j++;
        }
    }
    while (i < n) {
        parts.push(`<span class="diff-removed">${escapeHtml(a[i])}</span>`);
        i++;
    }
    while (j < m) {
        parts.push(`<span class="diff-added">${escapeHtml(b[j])}</span>`);
        j++;
    }
    return parts.join(" ");
}

function bulletDiffHtml(b) {
    const key = `${b.original}||${b.editedText}`;
    if (b.diffCacheKey === key && b.diffCacheHtml) return b.diffCacheHtml;
    b.diffCacheKey = key;
    b.diffCacheHtml = computeDiffHtml(b.original, b.editedText);
    return b.diffCacheHtml;
}

function renderReview() {
    els.changesContainer.innerHTML = "";

    if (state.run.status === "running") {
        const wrap = document.createElement("div");
        wrap.className = "review-list";
        for (let i = 0; i < 4; i++) {
            const card = document.createElement("div");
            card.className = "review-card";
            card.innerHTML = `
                <div class="skeleton" style="width: 38%; height: 16px;"></div>
                <div style="height: 10px;"></div>
                <div class="skeleton" style="width: 92%;"></div>
                <div class="skeleton" style="width: 86%;"></div>
                <div class="skeleton" style="width: 74%;"></div>
            `;
            wrap.appendChild(card);
        }
        els.changesContainer.appendChild(wrap);
        return;
    }

    if (!state.bullets || state.bullets.length === 0) {
        const empty = document.createElement("article");
        empty.className = "empty";
        empty.innerHTML = `<h3>No results yet</h3><p>Run Tailor to see bullet rewrites, keyword coverage, and ATS insights.</p>`;
        els.changesContainer.appendChild(empty);
        return;
    }

    const filtered = currentFilteredBullets();
    if (filtered.length === 0) {
        const empty = document.createElement("article");
        empty.className = "empty";
        empty.innerHTML = `<h3>No matches</h3><p>Try clearing filters or searching different keywords.</p>`;
        els.changesContainer.appendChild(empty);
        return;
    }

    filtered.forEach(({ b, idx }) => {
        const edited = normalizeWs(b.editedText);
        const showRegen = state.mode === "latex";
        const editedBadge = normalizeWs(edited) !== normalizeWs(b.suggested) ? `<span class="badge">Edited</span>` : "";
        const acceptBadge = b.accepted ? `<span class="badge ok">Accepted</span>` : `<span class="badge">Not accepted</span>`;
        const safetyBadge = b.safetyRejected ? `<span class="badge warn">Safety rejected</span>` : "";

        const diffOrFinal = b.showDiff
            ? `<div class="diff-block">${bulletDiffHtml(b)}</div>`
            : `<textarea class="editor" data-action="edit" data-index="${idx}">${escapeHtml(edited)}</textarea>`;

        const regenButton = showRegen
            ? `<button type="button" class="secondary" data-action="regenerate" data-index="${idx}">Regenerate</button>`
            : "";

        const rejectedDetails = b.safetyRejected && b.rejectedDraft
            ? `
                <details>
                    <summary>Rejected AI draft</summary>
                    <div class="details-body">${escapeHtml(b.rejectedDraft)}</div>
                </details>
            `
            : "";

        const originalDetails = `
            <details>
                <summary>Original</summary>
                <div class="details-body">${escapeHtml(b.original)}</div>
            </details>
        `;

        const reasonDetails = `
            <details>
                <summary>Why changed</summary>
                <div class="details-body">${escapeHtml(b.reason || "")}</div>
            </details>
        `;

        const card = document.createElement("article");
        const classNames = ["review-card"];
        if (b.safetyRejected) classNames.push("is-rejected");
        if (b.accepted) classNames.push("is-accepted");
        if (normalizeWs(edited) !== normalizeWs(b.suggested)) classNames.push("is-edited");
        card.className = classNames.join(" ");
        card.innerHTML = `
            <header>
                <h3>Bullet ${idx + 1}</h3>
                <div class="badges">
                    ${acceptBadge}
                    ${safetyBadge}
                    ${editedBadge}
                </div>
            </header>

            <div class="card-actions">
                <label class="pill">
                    <input type="checkbox" data-action="accept" data-index="${idx}" ${b.accepted ? "checked" : ""}>
                    Accept
                </label>
                <button type="button" class="diff-toggle" data-action="toggleView" data-index="${idx}">${b.showDiff ? "Final text" : "Diff"}</button>
                ${regenButton}
            </div>

            <div class="mini-subhead">
                <h4>${b.showDiff ? "Changes" : "Revised (editable)"}</h4>
            </div>
            ${diffOrFinal}

            ${originalDetails}
            ${rejectedDetails}
            ${reasonDetails}
        `;
        els.changesContainer.appendChild(card);
    });
}

function renderSummaryStrip() {
    const hasResults = state.bullets && state.bullets.length > 0;
    els.summaryStrip.style.display = hasResults ? "" : "none";
    if (!hasResults) return;

    const bulletsCount = state.bullets.length;
    const rejectedCount = state.bullets.filter((b) => b.safetyRejected).length;
    const coverage = state.keywordInsights ? Math.max(0, Math.min(100, Number(state.keywordInsights.coveragePercent || 0))) : 0;
    const warningsCount = state.warnings ? state.warnings.length : 0;

    els.summaryBullets.textContent = String(bulletsCount);
    els.summaryRejected.textContent = String(rejectedCount);
    els.summaryCoverage.textContent = `${coverage}%`;
    els.summaryWarnings.textContent = String(warningsCount);

    if (state.atsInsights) {
        els.summaryAtsWrap.style.display = "";
        const atsScore = Math.max(0, Math.min(100, Number(state.atsInsights.overallScore || 0)));
        els.summaryAts.textContent = String(atsScore);
    } else {
        els.summaryAtsWrap.style.display = "none";
    }
}

async function runTailor() {
    const jd = String(els.jobDescription.value || "").trim();
    if (!jd) {
        state.warnings = ["Job description is required."];
        setRunStatus("error", "Missing JD");
        renderAll();
        toast("Add a job description to continue.", "warn");
        setActiveTopTab("inputs");
        return;
    }

    if (!ensureGeminiApiKeyReady()) {
        return;
    }

    if (state.mode === "latex") {
        const latex = String(els.resumeLatex.value || "").trim();
        if (!latex) {
            state.warnings = ["Resume LaTeX is required in LaTeX mode."];
            setRunStatus("error", "Missing resume");
            renderAll();
            toast("Add resume LaTeX to continue.", "warn");
            setActiveTopTab("inputs");
            return;
        }
        await runLatexTailor(latex, jd);
        return;
    }

    if (!state.file) {
        state.warnings = ["Please upload a resume PDF or image."];
        setRunStatus("error", "Missing file");
        renderAll();
        toast("Upload a resume PDF/image to continue.", "warn");
        setActiveTopTab("inputs");
        return;
    }

    await runUploadTailor(state.file, jd);
}

async function runLatexTailor(resumeLatex, jobDescription) {
    setRunStatus("running", "Tailoring...");
    state.warnings = [];
    state.keywordInsights = null;
    state.atsInsights = null;
    state.suggestions = [];
    state.bullets = [];
    els.updatedLatex.value = "";
    renderAll();

    disableActions(true);
    try {
        const response = await fetch("/api/resume-tailor", {
            method: "POST",
            headers: buildAiRequestHeaders(true),
            body: JSON.stringify({ resumeLatex, jobDescription })
        });
        const payload = await response.json();
        if (!response.ok) {
            const details = payload.details && payload.details.length > 0 ? payload.details : [payload.message || "Request failed."];
            state.warnings = details;
            setRunStatus("error", "Failed");
            renderAll();
            toast(details[0] || "Tailor failed.", "warn");
            return;
        }

        state.warnings = payload.warnings || [];
        state.keywordInsights = payload.keywordInsights || null;
        state.atsInsights = payload.atsInsights || null;
        state.suggestions = payload.generalSuggestions || [];
        state.bullets = buildBulletStateFromChanges(payload.changes || []);
        els.updatedLatex.value = payload.updatedLatex || "";
        setRunStatus("success", "Tailored");
        setActiveTopTab("results");
        setActiveResultsTab("review");
        renderAll();
        coachSystemDivider(`New run context loaded at ${state.run.at}`);
        if (els.coachFab) {
            els.coachFab.classList.add("pulse");
            setTimeout(() => els.coachFab.classList.remove("pulse"), 900);
        }
        toast("Tailoring complete. Review bullets first.", "ok");
    } catch (e) {
        state.warnings = [`Network error: ${e.message}`];
        setRunStatus("error", "Network error");
        renderAll();
        toast("Network error while tailoring.", "warn");
    } finally {
        disableActions(false);
    }
}

async function runUploadTailor(file, jobDescription) {
    setRunStatus("running", "Analyzing...");
    state.warnings = [];
    state.keywordInsights = null;
    state.atsInsights = null;
    state.suggestions = [];
    state.bullets = [];
    els.updatedLatex.value = "";
    renderAll();

    disableActions(true);
    try {
        const form = new FormData();
        form.append("resumeFile", file);
        form.append("jobDescription", jobDescription);
        const response = await fetch("/api/resume-tailor/ai-doc", {
            method: "POST",
            headers: buildAiRequestHeaders(false),
            body: form
        });
        const payload = await response.json();
        if (!response.ok) {
            const details = payload.details && payload.details.length > 0 ? payload.details : [payload.message || "Request failed."];
            state.warnings = details;
            setRunStatus("error", "Failed");
            renderAll();
            toast(details[0] || "Analyze failed.", "warn");
            return;
        }

        state.warnings = payload.warnings || [];
        state.keywordInsights = payload.keywordInsights || null;
        state.atsInsights = payload.atsInsights || null;
        state.suggestions = payload.generalSuggestions || [];
        state.bullets = buildBulletStateFromChanges(payload.changes || []);
        setRunStatus("success", "Analyzed");
        setActiveTopTab("results");
        setActiveResultsTab("review");
        renderAll();
        coachSystemDivider(`New run context loaded at ${state.run.at}`);
        if (els.coachFab) {
            els.coachFab.classList.add("pulse");
            setTimeout(() => els.coachFab.classList.remove("pulse"), 900);
        }
        toast("Analysis complete. Review bullets first.", "ok");
    } catch (e) {
        state.warnings = [`Network error: ${e.message}`];
        setRunStatus("error", "Network error");
        renderAll();
        toast("Network error while analyzing.", "warn");
    } finally {
        disableActions(false);
    }
}

function buildOverrides() {
    const overrides = [];
    for (let i = 0; i < state.bullets.length; i++) {
        const b = state.bullets[i];
        overrides.push({
            index: i,
            accepted: !!b.accepted,
            revisedBullet: normalizeWs(b.editedText || "")
        });
    }
    return overrides;
}

function buildCopyReadyBullets() {
    const lines = [];
    state.bullets.forEach((b) => {
        if (!b.accepted) return;
        const t = normalizeWs(b.editedText);
        if (!t) return;
        lines.push(`- ${t}`);
    });
    return lines.join("\n");
}

async function applyAccepted() {
    if (!state.bullets || state.bullets.length === 0) {
        toast("Nothing to apply yet. Run Tailor first.", "warn");
        return;
    }

    if (state.mode === "upload") {
        els.updatedLatex.value = buildCopyReadyBullets();
        toast("Built copy-ready bullet list.", "ok");
        setActiveTopTab("results");
        setActiveResultsTab("export");
        return;
    }

    const resumeLatex = String(els.resumeLatex.value || "").trim();
    if (!resumeLatex) {
        toast("Resume LaTeX is missing.", "warn");
        setActiveTopTab("inputs");
        return;
    }

    disableActions(true);
    els.applyStatus.textContent = "Applying accepted changes...";
    try {
        const response = await fetch("/api/resume-tailor/apply-bullets", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ resumeLatex, overrides: buildOverrides() })
        });
        const payload = await response.json();
        if (!response.ok) {
            const details = payload.details && payload.details.length > 0 ? payload.details : [payload.message || "Request failed."];
            state.warnings = details;
            renderWarnings();
            toast(details[0] || "Apply failed.", "warn");
            els.applyStatus.textContent = "Apply failed.";
            return;
        }
        state.warnings = payload.warnings || [];
        renderWarnings();
        els.updatedLatex.value = payload.updatedLatex || "";
        toast("Applied to LaTeX output.", "ok");
        els.applyStatus.textContent = "Applied. Output updated.";
        setActiveTopTab("results");
        setActiveResultsTab("export");
    } catch (e) {
        state.warnings = [`Network error: ${e.message}`];
        renderWarnings();
        toast("Network error while applying.", "warn");
        els.applyStatus.textContent = "Apply failed.";
    } finally {
        disableActions(false);
    }
}

async function regenerateBullet(index) {
    if (state.mode !== "latex") return;
    const resumeLatex = String(els.resumeLatex.value || "").trim();
    const jobDescription = String(els.jobDescription.value || "").trim();
    if (!resumeLatex || !jobDescription) {
        toast("Add LaTeX and a job description first.", "warn");
        setActiveTopTab("inputs");
        return;
    }
    if (!ensureGeminiApiKeyReady()) {
        return;
    }

    const b = state.bullets[index];
    if (!b) return;

    disableActions(true);
    setRunStatus("running", `Rewriting bullet ${index + 1}...`);
    try {
        const response = await fetch("/api/resume-tailor/regenerate-bullet", {
            method: "POST",
            headers: buildAiRequestHeaders(true),
            body: JSON.stringify({
                resumeLatex,
                jobDescription,
                bulletIndex: index,
                currentBulletText: normalizeWs(b.editedText || "")
            })
        });
        const payload = await response.json();
        if (!response.ok) {
            const details = payload.details && payload.details.length > 0 ? payload.details : [payload.message || "Request failed."];
            state.warnings = details;
            renderWarnings();
            toast(details[0] || "Regenerate failed.", "warn");
            setRunStatus("error", "Failed");
            return;
        }

        b.safetyRejected = !!payload.safetyRejected;
        b.rejectedDraft = payload.rejectedDraft || null;
        b.reason = payload.reason || b.reason;
        if (!b.safetyRejected) {
            b.suggested = normalizeWs(payload.revisedBullet || b.suggested);
            b.editedText = b.suggested;
            b.accepted = true;
        } else {
            b.accepted = false;
        }
        b.diffCacheKey = "";
        b.diffCacheHtml = "";
        setRunStatus("success", "Regenerated");
        renderAll();
        toast(`Bullet ${index + 1} regenerated.`, "ok");
    } catch (e) {
        toast("Network error while regenerating.", "warn");
        setRunStatus("error", "Network error");
    } finally {
        disableActions(false);
    }
}

async function copyOutput() {
    const value = String(els.updatedLatex.value || "");
    if (!value.trim()) {
        toast("Nothing to copy yet.", "warn");
        return;
    }
    try {
        await navigator.clipboard.writeText(value);
        toast("Copied output to clipboard.", "ok");
    } catch {
        toast("Copy failed. Your browser may block clipboard access.", "warn");
    }
}

function pinAllMissing() {
    const insights = state.keywordInsights;
    if (!insights || !insights.missingKeywords) {
        toast("Run Tailor to get missing keywords first.", "warn");
        return;
    }
    (insights.missingKeywords || []).slice(0, 30).forEach((kw) => state.pinned.add(kw));
    renderPinnedKeywords();
    toast("Pinned missing keywords. Click chips to filter bullets.", "ok");
    setActiveTopTab("results");
    setActiveResultsTab("review");
}

function copyMissingKeywords() {
    const insights = state.keywordInsights;
    const missing = insights && insights.missingKeywords ? insights.missingKeywords : [];
    if (!missing || missing.length === 0) {
        toast("No missing keywords to copy.", "warn");
        return;
    }
    const text = missing.join(", ");
    navigator.clipboard.writeText(text)
        .then(() => toast("Copied missing keywords.", "ok"))
        .catch(() => toast("Copy failed.", "warn"));
}

function clearInputs() {
    if (els.resumeLatex) {
        els.resumeLatex.value = "";
    }
    els.jobDescription.value = "";
    setFile(null);
    toast("Cleared inputs.", "info");
}

function clearFilters() {
    state.filters.query = "";
    state.filters.onlyRejected = false;
    state.filters.onlyChanged = false;
    els.reviewSearch.value = "";
    els.filterRejected.checked = false;
    els.filterChanged.checked = false;
    renderReview();
}

function setQueryFilter(keyword) {
    state.filters.query = keyword || "";
    els.reviewSearch.value = keyword || "";
    renderReview();
    setActiveTopTab("results");
    setActiveResultsTab("review");
}

function togglePinnedKeyword(kw) {
    if (!kw) return;
    if (state.pinned.has(kw)) state.pinned.delete(kw);
    else state.pinned.add(kw);
    renderPinnedKeywords();
}

function coachHistoryForRequest() {
    if (!COACH_ENABLED) return [];
    const trimmed = (state.chat.messages || [])
        .filter((m) => m && (m.role === "user" || m.role === "assistant"))
        .slice(-12)
        .map((m) => ({ role: m.role, content: normalizeWs(m.content || "") }))
        .filter((m) => m.content);
    return trimmed;
}

async function sendCoachMessage(text) {
    if (!COACH_ENABLED) {
        toast("Coach is disabled.", "info");
        return;
    }
    const message = normalizeWs(text);
    if (!message) return;
    const jd = String(els.jobDescription.value || "").trim();
    if (!jd || !state.bullets || state.bullets.length === 0) {
        toast("Run Tailor first so Coach has context.", "warn");
        openCoach();
        return;
    }

    if (state.chat.pending) return;
    state.chat.pending = true;

    coachAddUser(message);
    if (els.coachInput) els.coachInput.value = "";

    if (els.coachSend) els.coachSend.disabled = true;
    setRunStatus(state.run.status === "running" ? "running" : state.run.status, state.run.label);

    try {
        const payload = {
            mode: state.mode,
            jobDescription: jd,
            resumeLatex: state.mode === "latex" ? String(els.resumeLatex.value || "") : null,
            bullets: state.bullets.map((b, idx) => ({
                index: idx,
                bulletId: null,
                original: normalizeWs(b.original || ""),
                current: normalizeWs(b.editedText || ""),
                accepted: !!b.accepted,
                safetyRejected: !!b.safetyRejected
            })),
            keywordInsights: state.keywordInsights,
            atsInsights: state.atsInsights,
            messages: coachHistoryForRequest(),
            options: { maxEdits: 3, tone: "professional" }
        };

        const res = await fetch("/api/resume-tailor/chat", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) {
            const details = data.details && data.details.length > 0 ? data.details : [data.message || "Request failed."];
            coachAddAssistant(details[0] || "Coach request failed.", []);
            toast(details[0] || "Coach failed.", "warn");
            return;
        }

        const assistantMessage = data.assistantMessage || "";
        const actions = Array.isArray(data.actions) ? data.actions : [];
        coachAddAssistant(assistantMessage, actions);
        if (Array.isArray(data.warnings) && data.warnings.length > 0) {
            toast(data.warnings[0], "info");
        }
    } catch (e) {
        coachAddAssistant(`Network error: ${e.message}`, []);
        toast("Network error while chatting.", "warn");
    } finally {
        state.chat.pending = false;
        if (els.coachSend) els.coachSend.disabled = false;
    }
}

function setupEvents() {
    els.modeRadios.forEach((r) => {
        r.addEventListener("change", () => {
            const selected = els.modeRadios.find((x) => x.checked);
            setMode(selected ? selected.value : "upload");
        });
    });

    els.mobilePrimaryTabs.forEach((btn) => {
        btn.addEventListener("click", () => setActiveTopTab(btn.dataset.primaryTab));
    });

    els.resultsTabButtons.forEach((btn) => {
        btn.addEventListener("click", () => setActiveResultsTab(btn.dataset.tab));
    });

    window.addEventListener("resize", () => setActiveTopTab(state.activeTopTab));

    els.tailorButton.addEventListener("click", runTailor);
    els.bottomTailorButton.addEventListener("click", runTailor);
    els.applyChangesButton.addEventListener("click", applyAccepted);
    els.bottomApplyButton.addEventListener("click", applyAccepted);
    els.copyButton.addEventListener("click", copyOutput);
    els.copyOutputButton.addEventListener("click", copyOutput);

    els.clearInputsButton.addEventListener("click", clearInputs);
    els.removeFileButton.addEventListener("click", () => setFile(null));
    if (els.geminiApiKey) {
        els.geminiApiKey.addEventListener("input", () => saveUserGeminiApiKey(els.geminiApiKey.value));
    }
    if (els.toggleApiKeyVisibility && els.geminiApiKey) {
        els.toggleApiKeyVisibility.addEventListener("click", () => {
            const show = els.geminiApiKey.type === "password";
            els.geminiApiKey.type = show ? "text" : "password";
            els.toggleApiKeyVisibility.textContent = show ? "Hide key" : "Show key";
        });
    }
    if (els.clearApiKeyButton) {
        els.clearApiKeyButton.addEventListener("click", () => {
            if (els.geminiApiKey) {
                els.geminiApiKey.value = "";
                els.geminiApiKey.type = "password";
            }
            if (els.toggleApiKeyVisibility) {
                els.toggleApiKeyVisibility.textContent = "Show key";
            }
            saveUserGeminiApiKey("");
            renderApiKeyUi();
            toast("Cleared Gemini API key for this session.", "info");
        });
    }

    els.summaryWarningsButton.addEventListener("click", () => {
        setActiveTopTab("results");
        setActiveResultsTab("export");
    });
    els.summaryAcceptSafe.addEventListener("click", () => els.bulkAcceptSafe.click());
    els.summaryShowRejected.addEventListener("click", () => {
        els.filterRejected.checked = true;
        state.filters.onlyRejected = true;
        setActiveTopTab("results");
        setActiveResultsTab("review");
        renderReview();
    });
    els.summaryClearFilters.addEventListener("click", () => {
        clearFilters();
        setActiveTopTab("results");
        setActiveResultsTab("review");
    });

    els.reviewSearch.addEventListener("input", () => {
        state.filters.query = els.reviewSearch.value || "";
        renderReview();
    });
    els.filterRejected.addEventListener("change", () => {
        state.filters.onlyRejected = !!els.filterRejected.checked;
        renderReview();
    });
    els.filterChanged.addEventListener("change", () => {
        state.filters.onlyChanged = !!els.filterChanged.checked;
        renderReview();
    });
    els.clearFilters.addEventListener("click", clearFilters);

    els.bulkAcceptSafe.addEventListener("click", () => {
        state.bullets.forEach((b) => {
            if (!b.safetyRejected) b.accepted = true;
        });
        renderReview();
        renderSummaryStrip();
        toast("Accepted all safe bullets.", "ok");
    });
    els.bulkRejectAll.addEventListener("click", () => {
        state.bullets.forEach((b) => { b.accepted = false; });
        renderReview();
        renderSummaryStrip();
        toast("Rejected all bullets.", "info");
    });
    els.bulkResetEdits.addEventListener("click", () => {
        state.bullets.forEach((b) => {
            const fallback = b.safetyRejected ? b.original : b.suggested;
            b.editedText = fallback;
            b.diffCacheKey = "";
            b.diffCacheHtml = "";
        });
        renderReview();
        toast("Reset edits.", "info");
    });

    els.copyMissingKeywords.addEventListener("click", copyMissingKeywords);
    els.pinMissingKeywords.addEventListener("click", pinAllMissing);

    if (!COACH_ENABLED) {
        if (els.coachFab) els.coachFab.hidden = true;
        if (els.coachScrim) els.coachScrim.hidden = true;
        if (els.coachDrawer) els.coachDrawer.hidden = true;
    } else if (els.coachFab) {
        els.coachFab.addEventListener("click", () => toggleCoach());
    }
    if (COACH_ENABLED && els.coachClose) {
        els.coachClose.addEventListener("click", () => closeCoach());
    }
    if (COACH_ENABLED && els.coachExpand) {
        els.coachExpand.addEventListener("click", () => {
            state.chat.expanded = !state.chat.expanded;
            // If the user has dragged to a width, keep it; otherwise use the expanded/collapsed defaults.
            if (!Number.isFinite(state.chat.width)) {
                state.chat.width = null;
            }
            applyCoachWidth();
        });
    }
    if (COACH_ENABLED && els.coachScrim) {
        els.coachScrim.addEventListener("click", () => closeCoach());
    }
    if (COACH_ENABLED && els.coachSend) {
        els.coachSend.addEventListener("click", () => sendCoachMessage(els.coachInput ? els.coachInput.value : ""));
    }
    if (COACH_ENABLED && els.coachInput) {
        els.coachInput.addEventListener("keydown", (e) => {
            if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                sendCoachMessage(els.coachInput.value);
            }
        });
    }

    document.addEventListener("keydown", (e) => {
        if (COACH_ENABLED && e.key === "Escape" && isCoachOpen()) {
            e.preventDefault();
            closeCoach();
        }
    });

    if (COACH_ENABLED && els.coachResizeHandle && els.coachDrawer) {
        const beginResize = (clientX) => {
            const minW = 360;
            const maxW = Math.min(860, Math.floor(window.innerWidth * 0.92));
            const w = clamp(window.innerWidth - clientX, minW, maxW);
            state.chat.width = w;
            state.chat.expanded = w >= 640;
            applyCoachWidth();
        };

        els.coachResizeHandle.addEventListener("pointerdown", (e) => {
            try { els.coachResizeHandle.setPointerCapture(e.pointerId); } catch { /* ignore */ }
            e.preventDefault();
            beginResize(e.clientX);

            const onMove = (evt) => beginResize(evt.clientX);
            const onUp = () => {
                window.removeEventListener("pointermove", onMove);
                window.removeEventListener("pointerup", onUp);
            };
            window.addEventListener("pointermove", onMove);
            window.addEventListener("pointerup", onUp);
        });

        els.coachResizeHandle.addEventListener("dblclick", () => {
            state.chat.expanded = !state.chat.expanded;
            state.chat.width = null;
            applyCoachWidth();
        });

        els.coachResizeHandle.addEventListener("keydown", (e) => {
            if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
            e.preventDefault();
            const delta = e.key === "ArrowLeft" ? 24 : -24;
            const current = Number.isFinite(state.chat.width) ? state.chat.width : 420;
            state.chat.width = current + delta;
            applyCoachWidth();
        });
    }

    els.dropzone.addEventListener("click", () => els.resumeFile.click());
    els.dropzone.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            els.resumeFile.click();
        }
    });
    els.resumeFile.addEventListener("change", () => {
        const f = els.resumeFile.files && els.resumeFile.files.length > 0 ? els.resumeFile.files[0] : null;
        setFile(f);
    });

    ["dragenter", "dragover"].forEach((evt) => {
        els.dropzone.addEventListener(evt, (e) => {
            e.preventDefault();
            e.stopPropagation();
            els.dropzone.classList.add("dragover");
        });
    });
    ["dragleave", "drop"].forEach((evt) => {
        els.dropzone.addEventListener(evt, (e) => {
            e.preventDefault();
            e.stopPropagation();
            els.dropzone.classList.remove("dragover");
        });
    });
    els.dropzone.addEventListener("drop", (e) => {
        const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0 ? e.dataTransfer.files[0] : null;
        if (f) setFile(f);
    });

    document.body.addEventListener("click", (e) => {
        const t = e.target;
        if (!(t instanceof HTMLElement)) return;

        const action = t.dataset.action;
        if (action === "filterKeyword") {
            setQueryFilter(t.dataset.keyword || "");
            return;
        }
        if (action === "pinKeyword") {
            const kw = t.dataset.keyword || "";
            togglePinnedKeyword(kw);
            toast(state.pinned.has(kw) ? `Pinned: ${kw}` : `Unpinned: ${kw}`, "info");
            return;
        }
        if (action === "pinned") {
            const kw = t.dataset.keyword || "";
            setQueryFilter(kw);
            return;
        }

        if (COACH_ENABLED && action === "coachQuick") {
            const prompt = t.dataset.prompt || "";
            openCoach();
            if (els.coachInput) {
                els.coachInput.value = prompt;
                els.coachInput.focus();
            }
            return;
        }
        if (COACH_ENABLED && action === "coachUseQuestion") {
            const q = t.dataset.question || "";
            openCoach();
            if (els.coachInput) {
                els.coachInput.value = q;
                els.coachInput.focus();
            }
            return;
        }
        if (COACH_ENABLED && action === "coachPinKeyword") {
            const kw = t.dataset.keyword || "";
            if (kw) {
                togglePinnedKeyword(kw);
                setQueryFilter(kw);
                toast(`Pinned and filtered: ${kw}`, "ok");
            }
            return;
        }
        if (COACH_ENABLED && action === "coachViewReview") {
            setActiveTopTab("results");
            setActiveResultsTab("review");
            closeCoach();
            return;
        }
        if (COACH_ENABLED && action === "coachApplyEdit") {
            const idx = Number(t.dataset.index);
            const text = t.dataset.text || "";
            if (!Number.isFinite(idx) || !state.bullets[idx]) return;
            state.bullets[idx].editedText = text;
            state.bullets[idx].accepted = true;
            state.bullets[idx].diffCacheKey = "";
            state.bullets[idx].diffCacheHtml = "";
            renderReview();
            renderSummaryStrip();
            toast(`Applied to Bullet ${idx + 1}.`, "ok");
            return;
        }

        if (action === "toggleView") {
            const idx = Number(t.dataset.index);
            if (!Number.isFinite(idx) || !state.bullets[idx]) return;
            state.bullets[idx].showDiff = !state.bullets[idx].showDiff;
            renderReview();
            return;
        }
        if (action === "regenerate") {
            const idx = Number(t.dataset.index);
            if (!Number.isFinite(idx)) return;
            regenerateBullet(idx);
        }

        if (action === "copyOneBullet") {
            const text = t.dataset.text || "";
            if (!text) return;
            navigator.clipboard.writeText(text)
                .then(() => toast("Copied bullet.", "ok"))
                .catch(() => toast("Copy failed.", "warn"));
            return;
        }
    });

    document.body.addEventListener("change", (e) => {
        const t = e.target;
        if (!(t instanceof HTMLInputElement)) return;
        const action = t.dataset.action;
        if (action === "accept") {
            const idx = Number(t.dataset.index);
            if (!Number.isFinite(idx) || !state.bullets[idx]) return;
            state.bullets[idx].accepted = !!t.checked;
            renderReview();
            renderSummaryStrip();
        }
    });

    document.body.addEventListener("input", (e) => {
        const t = e.target;
        if (!(t instanceof HTMLTextAreaElement)) return;
        const action = t.dataset.action;
        if (action === "edit") {
            const idx = Number(t.dataset.index);
            if (!Number.isFinite(idx) || !state.bullets[idx]) return;
            state.bullets[idx].editedText = t.value;
            state.bullets[idx].diffCacheKey = "";
            state.bullets[idx].diffCacheHtml = "";
        }
    });
}

async function init() {
    setupEvents();
    loadUserGeminiApiKey();
    loadCoachPrefs();
    applyCoachWidth();
    await loadClientConfig();

    const selected = els.modeRadios.find((x) => x.checked);
    setMode(selected ? selected.value : "upload");
    setActiveTopTab(isMobileLayout() ? "inputs" : "inputs");
    setActiveResultsTab("review");
    renderAll();
}

init();
