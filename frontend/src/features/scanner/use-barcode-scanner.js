"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/** Formats a supermarket till needs. Restricting the list makes decoding faster and more reliable. */
export const SCAN_FORMATS = ["EAN_13", "EAN_8", "UPC_A", "UPC_E", "CODE_128"];

/**
 * Camera barcode scanning with the full lifecycle:
 *  - asks for camera permission only when started (getUserMedia needs HTTPS or localhost);
 *  - lists cameras after permission (labels are hidden until then) and remembers the choice;
 *  - de-duplicates: the same code is ignored for `cooldownMs` so one hold over the camera
 *    does not add an item five times (a different code is accepted immediately);
 *  - always stops the media tracks on stop/close/unmount so the camera light goes off.
 *
 * `createReader` is injectable for tests; by default it lazy-loads @zxing/browser.
 */
export function useBarcodeScanner({ onDetected, cooldownMs = 1500, createReader } = {}) {
  const videoRef = useRef(null);
  const controlsRef = useRef(null);
  const lastRef = useRef({ code: null, at: 0 });
  const onDetectedRef = useRef(onDetected);
  onDetectedRef.current = onDetected;

  const [status, setStatus] = useState("idle"); // idle | starting | scanning | error
  const [error, setError] = useState(null); // { kind, message }
  const [devices, setDevices] = useState([]);
  const [deviceId, setDeviceId] = useState(() => {
    try {
      return window.localStorage.getItem("pos.cameraId") || undefined;
    } catch {
      return undefined;
    }
  });

  const stop = useCallback(() => {
    try {
      controlsRef.current?.stop();
    } catch {
      /* already stopped */
    }
    controlsRef.current = null;
    const video = videoRef.current;
    const stream = video?.srcObject;
    if (stream && typeof stream.getTracks === "function") {
      stream.getTracks().forEach((t) => t.stop());
    }
    if (video) video.srcObject = null;
    setStatus((s) => (s === "error" ? s : "idle"));
  }, []);

  const start = useCallback(
    async (requestedDeviceId) => {
      if (typeof navigator === "undefined" || !navigator.mediaDevices?.getUserMedia) {
        setError({
          kind: "unsupported",
          message:
            "This browser cannot access a camera here. Camera access needs HTTPS (or http://localhost during development). Type the barcode instead.",
        });
        setStatus("error");
        return;
      }
      stop();
      setError(null);
      setStatus("starting");
      try {
        const reader = createReader ? await createReader() : await defaultReader();
        const id = requestedDeviceId ?? deviceId;
        controlsRef.current = await reader.decodeFromVideoDevice(id || undefined, videoRef.current, (result) => {
          if (!result) return; // "not found" frames are normal while aiming
          const code = result.getText();
          const format = formatName(result.getBarcodeFormat?.());
          const now = Date.now();
          if (code === lastRef.current.code && now - lastRef.current.at < cooldownMs) return;
          lastRef.current = { code, at: now };
          onDetectedRef.current?.(code, format);
        });
        setStatus("scanning");
        // Device labels are only available after permission was granted.
        const list = await navigator.mediaDevices.enumerateDevices();
        setDevices(list.filter((d) => d.kind === "videoinput"));
      } catch (e) {
        stop();
        setError(describe(e));
        setStatus("error");
      }
    },
    [createReader, deviceId, cooldownMs, stop],
  );

  const chooseDevice = useCallback(
    (id) => {
      setDeviceId(id);
      try {
        window.localStorage.setItem("pos.cameraId", id);
      } catch {
        /* storage unavailable */
      }
      start(id);
    },
    [start],
  );

  /** Allows the same code to be scanned again right away (explicit re-scan). */
  const resetCooldown = useCallback(() => {
    lastRef.current = { code: null, at: 0 };
  }, []);

  useEffect(() => stop, [stop]);

  return { videoRef, status, error, devices, deviceId, start, stop, chooseDevice, resetCooldown };
}

let zxingModule = null;
async function defaultReader() {
  if (!zxingModule) {
    const [browser, library] = await Promise.all([import("@zxing/browser"), import("@zxing/library")]);
    zxingModule = { browser, library };
  }
  const { browser, library } = zxingModule;
  const hints = new Map();
  hints.set(
    library.DecodeHintType.POSSIBLE_FORMATS,
    SCAN_FORMATS.map((f) => library.BarcodeFormat[f]),
  );
  return new browser.BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 120 });
}

const FORMAT_NAMES = { 7: "EAN_13", 6: "EAN_8", 14: "UPC_A", 15: "UPC_E", 4: "CODE_128" };
function formatName(value) {
  if (typeof value === "string") return value;
  return FORMAT_NAMES[value] ?? undefined;
}

function describe(e) {
  const name = e?.name || "";
  if (name === "NotAllowedError" || name === "SecurityError") {
    return {
      kind: "denied",
      message:
        "Camera permission was denied. Allow camera access for this site in the browser's address bar, then try again.",
    };
  }
  if (name === "NotFoundError" || name === "OverconstrainedError") {
    return { kind: "notfound", message: "No camera was found. Connect a camera or type the barcode." };
  }
  if (name === "NotReadableError" || name === "AbortError") {
    return { kind: "busy", message: "The camera is in use by another application. Close it and try again." };
  }
  return { kind: "unknown", message: "The camera could not be started. Try again or type the barcode." };
}
