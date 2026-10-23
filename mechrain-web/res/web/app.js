"use strict";

/* ============================================================
 * Mechrain Web – Web-UI
 *
 * Frontend-Teil des mechrain-web Moduls. Alle Daten kommen über
 * die lokale HTTP-API (getState / getLogs / createSignal /
 * deleteSignal / setSinkSignal / setTaskSignal), die vom
 * mechrain-web Server-Backend bereitgestellt wird (MechrainClient
 * + StatePoller + SignalFlowDriver). Die Datenformate entsprechen
 * den Response-Beans aus mechrain-common.
 * ============================================================ */

/* ---------------- Hilfsfunktionen ---------------- */

const $ = (sel) => document.querySelector(sel);
const el = (tag, className, text) => {
	const e = document.createElement(tag);
	if (className) e.className = className;
	if (text !== undefined) e.textContent = text;
	return e;
};
const fmtTime = (ts) => new Date(ts).toLocaleTimeString("de-DE", { hour12: false });
const fmtAgo = (ts) => {
	const s = Math.max(0, Math.round((Date.now() - ts) / 1000));
	if (s < 60) return `vor ${s} s`;
	const m = Math.round(s / 60);
	if (m < 60) return `vor ${m} min`;
	return `vor ${Math.round(m / 60)} h`;
};
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({
	"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
}[c]));

const TYPE_CLASS = {
	"Threshold": "type-threshold",
	"Time Window": "type-timewindow",
	"Logic Gate": "type-gate",
	"Inverter": "type-inverter",
	"Staleness": "type-staleness",
	"Manual": "type-manual"
};

const COMPARATORS = [
	{ value: "GT", label: "&gt;  (größer als)" },
	{ value: "GTE", label: "≥  (größer gleich)" },
	{ value: "LT", label: "&lt;  (kleiner als)" },
	{ value: "LTE", label: "≤  (kleiner gleich)" }
];
const COMP_SYM = { GT: ">", GTE: ">=", LT: "<", LTE: "<=" };
const COMP_OFF_SYM = { GT: "<", GTE: "<=", LT: ">", LTE: ">=" };
const ISO_TO_DAYNAME = {
	1: "MONDAY", 2: "TUESDAY", 3: "WEDNESDAY", 4: "THURSDAY",
	5: "FRIDAY", 6: "SATURDAY", 0: "SUNDAY"
};
const DAYS_UI = [
	{ iso: 1, name: "Mo" }, { iso: 2, name: "Di" }, { iso: 3, name: "Mi" },
	{ iso: 4, name: "Do" }, { iso: 5, name: "Fr" }, { iso: 6, name: "Sa" },
	{ iso: 0, name: "So" }
];

/* ============================================================
 * Backend – HTTP-API des mechrain-web Servers
 *
 * Die Server-Seite (MechrainClient + StatePoller + SignalFlowDriver)
 * kapselt das komplette Mechrain-Protokoll; hier nur einfache
 * fetch-Aufrufe gegen die lokale HTTP-API.
 * ============================================================ */

const Backend = (() => {

	async function fetchJson(url, options) {
		const r = await fetch(url, options);
		let body = null;
		try { body = await r.json(); } catch (e) { /* kein JSON */ }
		if (!r.ok) {
			const msg = (body && body.error) ? body.error : ("HTTP " + r.status);
			const err = new Error(msg);
			err.status = r.status;
			throw err;
		}
		return body;
	}

	async function getState() {
		return fetchJson("/api/state");
	}

	async function getLogs() {
		return fetchJson("/api/logs");
	}

	async function createSignal(spec) {
		return fetchJson("/api/signals", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(spec)
		});
	}

	async function deleteSignal(id) {
		return fetchJson("/api/signals/" + id, { method: "DELETE" });
	}

	async function setSinkSignal(deviceId, sinkId, signalId) {
		return fetchJson("/api/sinks/" + deviceId + "/" + sinkId + "/signal", {
			method: "PATCH",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ signalId })
		});
	}

	async function setTaskSignal(deviceId, taskId, signalId) {
		return fetchJson("/api/tasks/" + deviceId + "/" + taskId + "/signal", {
			method: "PATCH",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ signalId })
		});
	}

	async function setDeviceName(deviceId, name) {
		return fetchJson("/api/devices/" + deviceId + "/name", {
			method: "PATCH",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ name })
		});
	}

	async function setSignalState(id, active) {
		return fetchJson("/api/signals/" + id + "/state", {
			method: "PATCH",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ active })
		});
	}

	return { getState, getLogs, createSignal, deleteSignal, setSinkSignal, setTaskSignal, setDeviceName, setSignalState };
})();

/* ============================================================
 * App – UI
 * ============================================================ */

const App = (() => {

	const POLL_MS = 3000;
	const MAX_LOG_LINES = 200;

	let state = null;
	let lastLogId = 0;
	let prevActive = new Map();
	let selectedType = null;
	let busy = false;

	/* ---------------- Polling ---------------- */

	async function poll() {
		const s = await Backend.getState();
		state = s;
		updateConnectionUi();
		const changed = detectChanges(s);
		renderSignals(changed);
		renderGraph(changed);
		renderDevices();
		await pollLogs();
	}

	async function pollLogs() {
		const { logs } = await Backend.getLogs();
		const fresh = logs.filter((l) => l.id > lastLogId);
		if (!fresh.length) return;
		lastLogId = logs[logs.length - 1].id;
		const feed = $("#log-feed");
		const nearBottom = feed.scrollHeight - feed.scrollTop - feed.clientHeight < 40;
		for (const l of fresh) {
			const line = el("div", "log-line new");
			line.innerHTML =
				`<span class="log-ts">${fmtTime(l.ts)}</span>` +
				`<span class="log-level ${l.level.toLowerCase()}">${esc(l.level)}</span>` +
				`<span class="log-msg">${esc(l.message)}</span>`;
			feed.append(line);
		}
		while (feed.children.length > MAX_LOG_LINES) feed.firstChild.remove();
		if (nearBottom) feed.scrollTop = feed.scrollHeight;
	}

	function detectChanges(s) {
		const changed = new Set();
		for (const sig of s.signals) {
			const prev = prevActive.get(sig.id);
			if (prev !== undefined && prev !== sig.active) changed.add(sig.id);
			prevActive.set(sig.id, sig.active);
		}
		prevActive = new Map([...prevActive].filter(([id]) => s.signals.some((x) => x.id === id)));
		return changed;
	}

	/* ---------------- Verbindungs-UI ---------------- */

	function updateConnectionUi() {
		const badge = $("#conn-badge");
		const info = $("#server-info");
		if (state.connected) {
			badge.className = "conn ok";
			badge.innerHTML = '<span class="pulse-dot"></span>Verbunden';
			info.textContent = `Server ${state.serverVersion} · Protokoll v${state.protocolVersion}`
				+ (state.readOnly ? " · Nur Lesen" : "");
		} else if (state.reconnecting) {
			badge.className = "conn reconnecting";
			badge.innerHTML = '<span class="pulse-dot"></span>Verbinde …';
			info.textContent = "";
		} else {
			badge.className = "conn disconnected";
			badge.innerHTML = '<span class="pulse-dot"></span>Getrennt';
			info.textContent = "";
		}
		$("#btn-new-signal").disabled = !state.connected || state.readOnly;
	}

	/* ---------------- Signal-Tabelle ---------------- */

	function renderSignals(changed) {
		const tbody = $("#signal-rows");
		tbody.innerHTML = "";
		if (!state.signals.length) {
			tbody.append(Object.assign(el("tr"), { innerHTML: '<td colspan="7" class="none">Keine Signale</td>' }));
			return;
		}
		for (const s of state.signals) {
			const tr = el("tr");
			tr.dataset.id = s.id;
			if (changed.has(s.id)) tr.classList.add("flash");

			const kids = s.childSignalIds
				? s.childSignalIds.map((id) => `<span class="chip chip-signal" data-signal="${id}">#${id}</span>`).join("")
				: '<span class="none">–</span>';
			const used = s.usedBy.length
				? s.usedBy.map((u) => `<span class="chip" title="${esc(u)}">${esc(u)}</span>`).join("")
				: '<span class="none">–</span>';

			let actions = "";
			if (!state.readOnly) {
				if (s.type === "Manual") {
					actions += `<button class="btn-icon sig-toggle${s.active ? " on" : ""}" data-sig="${s.id}" title="${s.active ? "Auf OFF schalten" : "Auf ON schalten"}">⏻</button>`;
				}
				actions += `<button class="btn-icon del" title="${s.usedBy.length ? "In Verwendung: " + esc(s.usedBy.join(", ")) : "Signal löschen"}">✕</button>`;
			}

			tr.innerHTML =
				`<td class="sig-id">${s.id}</td>` +
				`<td><span class="type-badge ${TYPE_CLASS[s.type] || ""}">${esc(s.type)}</span></td>` +
				`<td class="sig-desc">${esc(s.description)}</td>` +
				`<td><span class="state-badge ${s.active ? "active" : "inactive"}"><span class="dot"></span>${s.active ? "AKTIV" : "inaktiv"}</span></td>` +
				`<td>${kids}</td>` +
				`<td>${used}</td>` +
				`<td class="col-actions">${actions}</td>`;
			tbody.append(tr);
		}
	}

	/* ---------------- Graph (SVG) ---------------- */

	function renderGraph(changed) {
		const wrap = $("#graph");
		wrap.innerHTML = "";
		const signals = state.signals;
		const devices = state.devices;
		if (!signals.length && !devices.length) {
			wrap.append(el("div", "graph-hint", "Keine Daten"));
			return;
		}
		const byId = Object.fromEntries(signals.map((s) => [s.id, s]));

		// Ebenen: Quelle links, Konsument rechts
		const layerOf = {};
		const computeLayer = (id) => {
			if (layerOf[id] !== undefined) return layerOf[id];
			const s = byId[id];
			const kids = (s.childSignalIds || []).filter((k) => byId[k]);
			layerOf[id] = kids.length === 0 ? 0 : 1 + Math.max(...kids.map(computeLayer));
			return layerOf[id];
		};
		signals.forEach((s) => computeLayer(s.id));
		const layers = [];
		signals.forEach((s) => {
			(layers[layerOf[s.id]] = layers[layerOf[s.id]] || []).push(s);
		});
		layers.forEach((l) => l.sort((a, b) => a.id - b.id));

		// Geräte bilden die letzte Spalte
		const deviceCol = devices.length ? layers.length : Math.max(0, layers.length - 1);

		const nodeW = 200, nodeH = 56, colGap = 270, rowGap = 74, pad = 24;
		const maxRows = Math.max(1, ...layers.map((l) => l.length), devices.length);
		const width = pad * 2 + deviceCol * colGap + nodeW;
		const height = pad * 2 + maxRows * rowGap;
		const yCentered = (count, si) => pad + si * rowGap + (maxRows - count) * (rowGap / 2);
		const pos = {};
		layers.forEach((l, li) => l.forEach((s, si) => {
			pos["s" + s.id] = { x: pad + li * colGap, y: yCentered(l.length, si) };
		}));
		devices.forEach((d, di) => {
			pos["d" + d.id] = { x: pad + deviceCol * colGap, y: yCentered(devices.length, di) };
		});

		const NS = "http://www.w3.org/2000/svg";
		const svg = document.createElementNS(NS, "svg");
		svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
		svg.setAttribute("width", width);
		svg.setAttribute("height", height);

		const defs = document.createElementNS(NS, "defs");
		defs.innerHTML =
			'<marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">' +
			'<path d="M 0 0 L 10 5 L 0 10 z" class="gedge-arrow"/></marker>';
		svg.append(defs);

		const drawEdge = (from, to, label) => {
			const a = pos[from], b = pos[to];
			const x1 = a.x + nodeW, y1 = a.y + nodeH / 2;
			const x2 = b.x - 4, y2 = b.y + nodeH / 2;
			const dx = Math.max(30, (x2 - x1) / 2);
			const path = document.createElementNS(NS, "path");
			path.setAttribute("d", `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`);
			path.setAttribute("class", "gedge");
			path.setAttribute("marker-end", "url(#arrow)");
			path.dataset.from = from;
			path.dataset.to = to;
			svg.append(path);
			if (label) {
				const t = document.createElementNS(NS, "text");
				t.setAttribute("x", (x1 + x2) / 2);
				t.setAttribute("y", (y1 + y2) / 2 - 5);
				t.setAttribute("class", "gedge-label");
				t.dataset.from = from;
				t.dataset.to = to;
				t.textContent = label;
				svg.append(t);
			}
		};

		// Kanten: Kind → Elternsignal
		for (const s of signals) {
			for (const cid of (s.childSignalIds || [])) {
				if (byId[cid]) drawEdge("s" + cid, "s" + s.id, null);
			}
		}

		// Kanten: Signal → Gerät (Gate-Zuweisungen) mit Beschriftung
		for (const d of devices) {
			const gates = [];
			for (const sink of Object.values(d.sinks)) {
				if (sink.signalId != null) gates.push({ signalId: sink.signalId, label: `Sink ${sink.id} · ${sink.type}` });
			}
			for (const task of Object.values(d.tasks)) {
				if (task.signalId != null) gates.push({ signalId: task.signalId, label: `Task ${task.id} · ${task.measurement}` });
			}
			for (const gate of gates) {
				if (byId[gate.signalId]) drawEdge("s" + gate.signalId, "d" + d.id, gate.label);
			}
		}

		// Knoten
		for (const s of signals) {
			const p = pos["s" + s.id];
			const g = document.createElementNS(NS, "g");
			g.setAttribute("class", `gnode ${s.active ? "active" : "inactive"}`);
			g.dataset.id = "s" + s.id;
			if (changed.has(s.id)) g.classList.add("flash");

			const rect = document.createElementNS(NS, "rect");
			rect.setAttribute("x", p.x); rect.setAttribute("y", p.y);
			rect.setAttribute("width", nodeW); rect.setAttribute("height", nodeH);
			rect.setAttribute("rx", 9);
			g.append(rect);

			const t1 = document.createElementNS(NS, "text");
			t1.setAttribute("x", p.x + 12); t1.setAttribute("y", p.y + 21);
			t1.setAttribute("class", "n-title");
			t1.textContent = `#${s.id} ${s.type}`;
			g.append(t1);

			const t2 = document.createElementNS(NS, "text");
			t2.setAttribute("x", p.x + 12); t2.setAttribute("y", p.y + 38);
			t2.setAttribute("class", "n-desc");
			t2.textContent = s.description.length > 28 ? s.description.slice(0, 27) + "…" : s.description;
			g.append(t2);

			const t3 = document.createElementNS(NS, "text");
			t3.setAttribute("x", p.x + nodeW - 12); t3.setAttribute("y", p.y + 21);
			t3.setAttribute("class", "n-state");
			t3.setAttribute("text-anchor", "end");
			t3.textContent = s.active ? "AKTIV" : "INAKTIV";
			g.append(t3);

			const title = document.createElementNS(NS, "title");
			title.textContent = `Signal ${s.id}: ${s.type}\n${s.description}` +
				(s.usedBy.length ? `\nVerwendet von: ${s.usedBy.join(", ")}` : "\nNicht verwendet");
			g.append(title);

			svg.append(g);
		}

		// Gerät-Knoten
		for (const d of devices) {
			const p = pos["d" + d.id];
			const g = document.createElementNS(NS, "g");
			g.setAttribute("class", `gnode device ${d.connected ? "active" : "inactive"}`);
			g.dataset.id = "d" + d.id;

			const rect = document.createElementNS(NS, "rect");
			rect.setAttribute("x", p.x); rect.setAttribute("y", p.y);
			rect.setAttribute("width", nodeW); rect.setAttribute("height", nodeH);
			rect.setAttribute("rx", 9);
			g.append(rect);

			const t1 = document.createElementNS(NS, "text");
			t1.setAttribute("x", p.x + 12); t1.setAttribute("y", p.y + 21);
			t1.setAttribute("class", "n-title");
			const devTitle = "#" + d.id + " " + (d.name || d.description || "unbenannt");
			t1.textContent = devTitle.length > 24 ? devTitle.slice(0, 23) + "…" : devTitle;
			g.append(t1);

			const gateInfo = [];
			let gateTotal = 0, gateActive = 0;
			for (const sink of Object.values(d.sinks)) {
				if (sink.signalId == null) continue;
				gateTotal++;
				const sig = byId[sink.signalId];
				if (sig && sig.active) gateActive++;
				gateInfo.push(`Signal ${sink.signalId} → Sink ${sink.id} (${sink.type})${sig && sig.active ? " [aktiv]" : ""}`);
			}
			for (const task of Object.values(d.tasks)) {
				if (task.signalId == null) continue;
				gateTotal++;
				const sig = byId[task.signalId];
				if (sig && sig.active) gateActive++;
				gateInfo.push(`Signal ${task.signalId} → Task ${task.id} (${task.measurement})${sig && sig.active ? " [aktiv]" : ""}`);
			}

			const t2 = document.createElementNS(NS, "text");
			t2.setAttribute("x", p.x + 12); t2.setAttribute("y", p.y + 38);
			t2.setAttribute("class", "n-desc");
			t2.textContent = (d.name && d.description)
				? (d.description.length > 28 ? d.description.slice(0, 27) + "…" : d.description)
				: (d.connected
					? (gateTotal ? `verbunden · ${gateActive}/${gateTotal} Gates aktiv` : "verbunden · keine Gates")
					: (gateTotal ? `getrennt · ${gateTotal} Gates` : "getrennt"));
			g.append(t2);

			const t3 = document.createElementNS(NS, "text");
			t3.setAttribute("x", p.x + nodeW - 12); t3.setAttribute("y", p.y + 21);
			t3.setAttribute("class", "n-state");
			t3.setAttribute("text-anchor", "end");
			t3.textContent = d.connected ? "VERBUNDEN" : "GETRENNT";
			g.append(t3);

			const title = document.createElementNS(NS, "title");
			title.textContent = `Device ${d.id}: ${d.name || d.description || "unbenannt"}\n${d.connected ? "verbunden" : "getrennt"}${d.buildId ? " · " + d.buildId : ""}` +
				(d.name && d.description ? "\n" + d.description : "") +
				(gateInfo.length ? "\nGates:\n" + gateInfo.join("\n") : "\nKeine Gates");
			g.append(title);

			svg.append(g);
		}

		// Hover-Highlight
		svg.addEventListener("mouseover", (e) => {
			const node = e.target.closest(".gnode");
			if (!node) return;
			const nid = node.dataset.id;
			const isDevice = nid.charAt(0) === "d";
			const id = Number(nid.slice(1));
			const related = new Set([nid]);
			if (isDevice) {
				const dev = devices.find((x) => x.id === id);
				if (dev) {
					for (const sink of Object.values(dev.sinks)) if (sink.signalId != null) related.add("s" + sink.signalId);
					for (const task of Object.values(dev.tasks)) if (task.signalId != null) related.add("s" + task.signalId);
			}
			} else {
				const sig = byId[id];
				(sig.childSignalIds || []).forEach((k) => related.add("s" + k));
				for (const s of signals) if ((s.childSignalIds || []).includes(id)) related.add("s" + s.id);
				for (const d of devices) {
					const hits = Object.values(d.sinks).concat(Object.values(d.tasks)).some((x) => x.signalId === id);
					if (hits) related.add("d" + d.id);
				}
			}
			svg.querySelectorAll(".gnode").forEach((n) => n.classList.toggle("dim", !related.has(n.dataset.id)));
			svg.querySelectorAll(".gedge, .gedge-label").forEach((ed) => {
				const on = ed.dataset.from === nid || ed.dataset.to === nid;
				ed.classList.toggle("hl", on);
				ed.classList.toggle("dim", !on);
			});
		});
		svg.addEventListener("mouseout", () => {
			svg.querySelectorAll(".gnode").forEach((n) => n.classList.remove("dim"));
			svg.querySelectorAll(".gedge, .gedge-label").forEach((ed) => ed.classList.remove("hl", "dim"));
		});
		// Klick: Signal → Tabellenzeile, Gerät → Geräte-Karte
		svg.addEventListener("click", (e) => {
			const node = e.target.closest(".gnode");
			if (!node) return;
			const nid = node.dataset.id;
			const target = nid.charAt(0) === "d"
				? document.querySelector(`.device-card[data-device="${nid.slice(1)}"]`)
				: document.querySelector(`#signal-rows tr[data-id="${nid.slice(1)}"]`);
			if (target) {
				target.scrollIntoView({ behavior: "smooth", block: "center" });
				target.classList.remove("flash");
				void target.offsetWidth;
				target.classList.add("flash");
			}
		});

		wrap.append(svg);
	}

	/* ---------------- Geräte ---------------- */

	function gateSelect(deviceId, kind, id, current, signalActive) {
		const sel = el("select", "gate-select");
		if (state.readOnly) sel.title = "Nur-Lese-Modus";		sel.dataset.device = deviceId;
		sel.dataset.kind = kind;
		sel.dataset.id = id;
		sel.disabled = !state.connected || state.readOnly;
		const none = el("option", "", "kein Gate");
		none.value = "";
		sel.append(none);
		for (const s of state.signals) {
			const opt = el("option", "", `#${s.id} ${s.type}: ${s.description}`);
			opt.value = s.id;
			sel.append(opt);
		}
		sel.value = current != null ? String(current) : "";
		sel.addEventListener("change", async () => {
			const signalId = sel.value === "" ? null : Number(sel.value);
			sel.disabled = true;
			try {
				const result = kind === "sink"
					? await Backend.setSinkSignal(deviceId, id, signalId)
					: await Backend.setTaskSignal(deviceId, id, signalId);
				if (result.success) {
					toast(signalId == null ? "Gate entfernt" : `Gate auf Signal ${signalId} gesetzt`, "success");
					sel.classList.remove("flash");
					void sel.offsetWidth;
					sel.classList.add("flash");
				} else {
					toast(result.message, "error");
				}
			} catch (err) {
				toast(err.message, "error");
			} finally {
				sel.disabled = !state.connected || state.readOnly;
				poll();
			}
		});
		return sel;
	}

	function miniState(active) {
		return el("span", "mini-state " + (active === null ? "none" : active ? "on" : "off"));
	}

		/* ---------------- Gerät umbenennen ---------------- */

	function nameEditButton(d) {
		const btn = el("button", "btn btn-ghost dev-rename-btn", "✎");
		btn.type = "button";
		btn.title = "Gerätenamen ändern";
		btn.addEventListener("click", () => startNameEdit(d, btn));
		return btn;
	}

	function startNameEdit(d, btn) {
		const input = el("input", "dev-rename-input");
		input.type = "text";
		input.value = d.name || "";
		input.placeholder = "(leer = ohne Namen)";
		btn.replaceWith(input);
		input.focus();
		input.select();

		let done = false;
		const finish = (save) => {
			if (done) return;
			done = true;
			if (!save) {
				renderDevices();
				return;
			}
			const newName = input.value.trim();
			busy = true;
			Backend.setDeviceName(d.id, newName).then(() => {
				toast(newName ? `Gerät ${d.id} heißt jetzt „${newName}“` : `Name von Gerät ${d.id} entfernt`, "success");
				poll();
			}).catch((e) => {
				toast(e.message, "error");
			}).finally(() => {
				busy = false;
				renderDevices();
			});
		};

		input.addEventListener("keydown", (e) => {
			if (e.key === "Enter") {
				e.preventDefault();
				finish(true);
			} else if (e.key === "Escape") {
				e.preventDefault();
				finish(false);
			}
		});
		input.addEventListener("blur", () => finish(true));
	}

	function renderDevices() {
		const wrap = $("#devices");
		wrap.innerHTML = "";
		for (const d of state.devices) {
			const card = el("div", "device-card");
			card.dataset.device = d.id;

			const head = el("div", "device-head");
			head.append(
				el("span", "device-name", `#${d.id} ${d.name || d.description || "unbenannt"}`),
				el("span", "dev-badge " + (d.connected ? "on" : "off"), d.connected ? "verbunden" : "getrennt")
			);
			if (!state.readOnly) head.append(nameEditButton(d));
			const meta = el("span", "device-meta");
			meta.textContent = d.connected
				? `${d.buildId || ""}`
				: `getrennt seit ${fmtAgo(d.lastContactAt)}`;
			head.append(meta);
			card.append(head);
			// Beschreibung nur extra anzeigen, wenn sie nicht bereits als Titel dient
			if (d.name && d.description) card.append(el("div", "device-desc", d.description));

			const makeRows = (items, kind, nameOf, subOf) => {
				if (!items.length) return;
				card.append(el("div", "subhead", kind === "sink" ? "Sinks" : "Tasks"));
				const tbl = el("table", "device-table");
				for (const it of items) {
					const tr = el("tr");
					const tdId = el("td", "d-id", String(it.id));
					const tdName = el("td");
					tdName.append(el("span", "d-name", nameOf(it)));
					const sub = subOf(it);
					if (sub) tdName.append(Object.assign(el("div"), { className: "d-sub", textContent: sub }));
					const tdGate = el("td", "d-gate");
					tdGate.append(miniState(it.signalActive));
					tdGate.append(gateSelect(d.id, kind, it.id, it.signalId, it.signalActive));
					tr.append(tdId, tdName, tdGate);
					tbl.append(tr);
				}
				card.append(tbl);
			};

			makeRows(Object.values(d.sinks), "sink",
				(s) => s.type,
				(s) => [s.description, s.filterNames ? `Filter: ${s.filterNames.join(", ")}` : null].filter(Boolean).join(" · "));
			makeRows(Object.values(d.tasks), "task",
				(t) => t.measurement,
				(t) => `${t.interval} ${t.timeUnit.toLowerCase()}${t.adaptive ? " · adaptiv" : ""}`);

			wrap.append(card);
		}
	}

	/* ---------------- Dialog: Signal anlegen ---------------- */

	const TYPE_DEFS = [
		{ key: "threshold", name: "Schwellwert", desc: "Messwert über/unter Grenze" },
		{ key: "gate", name: "Logik-Gatter", desc: "AND/OR aus mehreren Signalen" },
		{ key: "inverter", name: "Inverter", desc: "Negation eines Signals" },
		{ key: "staleness", name: "Staleness", desc: "Keine Daten seit X Minuten" },
		{ key: "timewindow", name: "Zeitfenster", desc: "Aktiv zu bestimmten Zeiten" },
		{ key: "manual", name: "Manuell", desc: "Schalter: per Klick oder CLI ein/aus" }
	];

	function renderTypePicker() {
		const picker = $("#type-picker");
		picker.innerHTML = "";
		for (const t of TYPE_DEFS) {
			const card = el("div", "type-card" + (t.key === selectedType ? " selected" : ""));
			card.innerHTML = `<div class="tc-name">${t.name}</div><div class="tc-desc">${t.desc}</div>`;
			card.addEventListener("click", () => {
				selectedType = t.key;
				renderTypePicker();
				buildFields(t.key);
			});
			picker.append(card);
		}
	}

	function selectInput(id, options) {
		const sel = el("select");
		sel.id = id;
		for (const o of options) {
			const opt = el("option", "", o.label);
			opt.value = o.value;
			sel.append(opt);
		}
		return sel;
	}

	function numberInput(id, step) {
		const inp = el("input");
		inp.type = "number";
		inp.id = id;
		if (step) inp.step = step;
		return inp;
	}

	function deviceSelect(id) {
		const sel = el("select");
		sel.id = id;
		for (const d of state.devices) {
			const opt = el("option", "", `${d.id} – ${d.name || d.description || "unbenannt"}`);
			opt.value = d.id;
			sel.append(opt);
		}
		return sel;
	}

	function measurementOptions(deviceId) {
		const d = state.devices.find((x) => x.id === Number(deviceId));
		if (!d) return [{ value: "", label: "–" }];
		return Object.values(d.tasks)
			.map((t) => t.measurement)
			.filter((m, i, a) => a.indexOf(m) === i)
			.map((m) => ({ value: m, label: m }));
	}

	function signalChecklist() {
		const box = el("div", "child-list");
		for (const s of state.signals) {
			const lbl = el("label", "child-label");
			const cb = el("input");
			cb.type = "checkbox";
			cb.name = "f-child";
			cb.value = s.id;
			const txt = el("span", "child-text");
			txt.innerHTML = `<span class="ct-id">#${s.id}</span> <span class="ct-type">${esc(s.type)}</span> ${esc(s.description)}`;
			lbl.append(cb, txt);
			box.append(lbl);
		}
		return box;
	}

	function signalSingleSelect(id) {
		const sel = el("select");
		sel.id = id;
		sel.append(el("option", "", "– wählen –"));
		for (const s of state.signals) {
			const opt = el("option", "", `#${s.id} ${s.type}: ${s.description}`);
			opt.value = s.id;
			sel.append(opt);
		}
		return sel;
	}

	function buildFields(type) {
		const c = $("#signal-fields");
		c.innerHTML = "";
		const add = (label, input) => {
			const w = el("div", "field");
			w.append(el("label", "field-label", label), input);
			c.append(w);
		};
		const addRow = (fields) => {
			const row = el("div", "field-row");
			fields.forEach(([label, input]) => {
				const w = el("div", "field");
				w.append(el("label", "field-label", label), input);
				row.append(w);
			});
			c.append(row);
		};

		if (type === "threshold") {
			const devSel = deviceSelect("f-device");
			const measSel = selectInput("f-meas", measurementOptions(devSel.value));
			devSel.addEventListener("change", () => {
				const tmp = selectInput("f-meas", measurementOptions(devSel.value));
				measSel.replaceWith(tmp);
			});
			add("Ziel-Gerät", devSel);
			add("Messgröße", measSel);
			add("Vergleich", selectInput("f-comp", COMPARATORS));
			const row = el("div", "field-row");
			const f1 = el("div", "field");
			f1.append(el("label", "field-label", "Schwellwert"), numberInput("f-threshold", "any"));
			const f2 = el("div", "field");
			f2.append(el("label", "field-label", "Ausschalt-Schwellwert (optional)"), numberInput("f-off", "any"));
			const f3 = el("div", "field");
			f3.append(el("label", "field-label", "Stabil für (Minuten, optional)"), numberInput("f-stable"));
			row.append(f1, f2, f3);
			c.append(row);
		} else if (type === "gate") {
			add("Operation", selectInput("f-op", [
				{ value: "AND", label: "AND – alle Signale aktiv" },
				{ value: "OR", label: "OR – mindestens eines aktiv" }
			]));
			add("Kindsignale (mehrere möglich)", signalChecklist());
		} else if (type === "inverter") {
			add("Signal zum Invertieren", signalSingleSelect("f-child"));
		} else if (type === "staleness") {
			const devSel = deviceSelect("f-device");
			const measSel = selectInput("f-meas", [{ value: "", label: "beliebige Daten (inkl. Heartbeats)" }].concat(measurementOptions(state.devices.length ? state.devices[0].id : null)));
			devSel.addEventListener("change", () => {
				const tmp = selectInput("f-meas", [{ value: "", label: "beliebige Daten (inkl. Heartbeats)" }].concat(measurementOptions(devSel.value)));
				measSel.replaceWith(tmp);
			});
			add("Ziel-Gerät", devSel);
			add("Messgröße", measSel);
			add("Timeout (Minuten)", numberInput("f-timeout"));
		} else if (type === "timewindow") {
			const start = el("input"); start.type = "time"; start.id = "f-start"; start.value = "22:00";
			const end = el("input"); end.type = "time"; end.id = "f-end"; end.value = "06:00";
			addRow([["Startzeit", start], ["Endzeit", end]]);
			const dayBox = el("div", "day-row");
			for (const d of DAYS_UI) {
				const chip = el("div", "day-chip", d.name);
				chip.dataset.iso = d.iso;
				chip.addEventListener("click", () => chip.classList.toggle("selected"));
				dayBox.append(chip);
			}
			add("Tage (leer = alle Tage)", dayBox);
		} else if (type === "manual") {
			c.append(el("div", "graph-hint", "Keine Parameter – der Zustand wird über den Schalter in der Signal-Liste (oder per CLI: config signals set <id> on|off) gesetzt."));
		}
	}

	function openDialog() {
		selectedType = null;
		renderTypePicker();
		$("#signal-fields").innerHTML =
			'<div class="graph-hint">Typ wählen, um die Felder anzuzeigen</div>';
		$("#form-error").classList.add("hidden");
		$("#btn-create").textContent = "Anlegen";
		$("#btn-create").disabled = false;
		$("#modal-overlay").classList.remove("hidden");
	}

	function closeDialog() {
		$("#modal-overlay").classList.add("hidden");
	}

	function collectSpec(type) {
		const val = (id) => (document.getElementById(id) || {}).value || "";
		const num = (id) => val(id) === "" ? null : Number(val(id));
		switch (type) {
			case "threshold":
				return {
					type, targetDeviceId: Number(val("f-device")), measurement: val("f-meas"),
					comparator: val("f-comp"), threshold: num("f-threshold"),
					offThreshold: num("f-off"), stableForMinutes: num("f-stable")
				};
			case "gate":
				return {
					type, operator: val("f-op"),
					childSignalIds: [...document.querySelectorAll('input[name="f-child"]:checked')].map((c) => Number(c.value))
				};
			case "inverter":
				return { type, childSignalId: val("f-child") === "" ? null : Number(val("f-child")) };
			case "staleness":
				return {
					type, targetDeviceId: Number(val("f-device")),
					measurement: val("f-meas") || null, timeoutMinutes: num("f-timeout")
				};
			case "timewindow":
				return {
					type, start: val("f-start"), end: val("f-end"),
					days: [...document.querySelectorAll(".day-chip.selected")].map((c) => Number(c.dataset.iso))
				};
			case "manual":
				return { type };
		}
	}

	function clientValidate(spec) {
		const errors = [];
		switch (spec.type) {
			case "threshold":
				if (!spec.measurement) errors.push("Messgröße wählen");
				if (spec.threshold == null || !Number.isFinite(spec.threshold)) errors.push("Schwellwert angeben");
				break;
			case "gate":
				if (!spec.childSignalIds.length) errors.push("Mindestens ein Kindsignal auswählen");
				break;
			case "inverter":
				if (spec.childSignalId == null) errors.push("Signal auswählen");
				break;
			case "staleness":
				if (!spec.timeoutMinutes || spec.timeoutMinutes <= 0) errors.push("Timeout > 0 angeben");
				break;
			case "timewindow":
				if (!spec.start || !spec.end) errors.push("Start- und Endzeit angeben");
				else if (spec.start === spec.end) errors.push("Start und Ende dürfen nicht gleich sein");
				if (!spec.days.length) errors.push("Mindestens einen Wochentag wählen");
				break;
		}
		return errors;
	}

	async function onSubmit(e) {
		e.preventDefault();
		if (!selectedType || busy) return;
		const spec = collectSpec(selectedType);
		const errors = clientValidate(spec);
		const errBox = $("#form-error");
		if (errors.length) {
			errBox.textContent = errors.join("; ");
			errBox.classList.remove("hidden");
			return;
		}
		errBox.classList.add("hidden");
		busy = true;
		const btn = $("#btn-create");
		btn.disabled = true;
		btn.textContent = "Wird erstellt …";
		try {
			const sig = await Backend.createSignal(spec);
			toast(`Signal ${sig.id} angelegt: ${sig.description}`, "success");
			closeDialog();
			poll();
		} catch (err) {
			errBox.textContent = err.message;
			errBox.classList.remove("hidden");
		} finally {
			busy = false;
			btn.disabled = false;
			btn.textContent = "Anlegen";
		}
	}

	/* ---------------- Toasts ---------------- */

	function toast(message, kind = "info") {
		const t = el("div", "toast " + kind, message);
		$("#toasts").append(t);
		setTimeout(() => t.remove(), 4500);
	}

	/* ---------------- Events & Init ---------------- */

	function init() {
		$("#btn-new-signal").addEventListener("click", openDialog);
		$("#btn-cancel").addEventListener("click", closeDialog);
		$("#modal-overlay").addEventListener("click", (e) => {
			if (e.target.id === "modal-overlay") closeDialog();
		});
		document.addEventListener("keydown", (e) => {
			if (e.key === "Escape") closeDialog();
		});
		$("#signal-form").addEventListener("submit", onSubmit);
		// Demo-Button und Demo-Banner (nur im Prototyp relevant) im echten Backend verstecken
		const demoBtn = $("#demo-conn-btn");
		if (demoBtn) demoBtn.style.display = "none";
		const demoBanner = document.querySelector(".demo-banner");
		if (demoBanner) demoBanner.style.display = "none";

		// Signal-Tabelle: Löschen + Chip-Klicks
		$("#signal-rows").addEventListener("click", async (e) => {
			const chip = e.target.closest(".chip-signal");
			if (chip) {
				const row = document.querySelector(`#signal-rows tr[data-id="${chip.dataset.signal}"]`);
				if (row) {
					row.scrollIntoView({ behavior: "smooth", block: "center" });
					row.classList.remove("flash");
					void row.offsetWidth;
					row.classList.add("flash");
				}
				return;
			}
			const tog = e.target.closest(".sig-toggle");
			if (tog) {
				const tid = Number(tog.dataset.sig);
				const tsig = state.signals.find((s) => s.id === tid);
				if (!tsig || busy) return;
				tog.disabled = true;
				try {
					const res = await Backend.setSignalState(tid, !tsig.active);
					toast(`Signal ${tid} ist jetzt ${res.active ? "AN" : "AUS"}`, "success");
					poll();
				} catch (err) {
					toast(err.message, "error");
					tog.disabled = false;
				}
				return;
			}

			const del = e.target.closest(".del");
			if (!del) return;
			const tr = del.closest("tr");
			const id = Number(tr.dataset.id);
			const sig = state.signals.find((s) => s.id === id);
			if (!confirm(`Signal ${id} löschen?\n\n${sig.description}`)) return;
			del.disabled = true;
			try {
				await Backend.deleteSignal(id);
				toast(`Signal ${id} gelöscht`, "success");
				poll();
			} catch (err) {
				toast(err.message, "error");
				del.disabled = false;
			}
		});

		poll();
		setInterval(poll, POLL_MS);
	}

	document.addEventListener("DOMContentLoaded", init);
	return { init };
})();
