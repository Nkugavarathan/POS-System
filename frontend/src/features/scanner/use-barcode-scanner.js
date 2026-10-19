"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/** Formats a supermarket till needs. Restricting the list makes decoding faster and more reliable. */
export const SCAN_FORMATS = ["EAN_13", "EAN_8", "UPC_A", "UPC_E", "CODE_128"];

/** The part of the picture inside the on-screen frame (fractions of the video), shared with the dialog. */
export const SCAN_REGION = { width: 0.8, height: 0.4 };

const NATIVE_FORMATS = { EAN_13: "ean_13", EAN_8: "ean_8", UPC_A: "upc_a", UPC_E: "upc_e", CODE_128: "code_128" };
const FROM_NATIVE = Object.fromEntries(Object.entries(NATIVE_FORMATS).map(([k, v]) => [v, k]));

/**
 * Camera barcode scanning with the full lifecycle:
 *  - asks for camera permission only when started (getUserMedia needs HTTPS or localhost);
 *  - requests an HD stream with continuous autofocus, so small or thin barcodes have enough pixels;
 *  - decodes only the framed area at native resolution, alternating plain/sharpened images and
 *    two binarizers (sharpening and the global one cope with soft-focus laptop webcams), TRY_HARDER;
 *  - prefers the browser's native BarcodeDetector when available (faster, very robust);
 *  - lists cameras after permission (labels are hidden until then) and remembers the choice;
 *  - de-duplicates: the same code is ignored for `cooldownMs` so one hold over the camera
 *    does not add an item five times (a different code is accepted immediately);
 *  - exposes zoom / torch when the camera supports them;
 *  - always stops the media tracks on stop/close/unmount so the camera light goes off.
 *
 * `createReader` is injectable for tests: it returns an object with `decodeFromVideoDevice`.
 */
export function useBarcodeScanner({ onDetected, cooldownMs = 1500, createReader } = {}) {
  const videoRef = useRef(null);
  const controlsRef = useRef(null);
  const trackRef = useRef(null);
  const lastRef = useRef({ code: null, at: 0 });
  const onDetectedRef = useRef(onDetected);
  onDetectedRef.current = onDetected;

  const [status, setStatus] = useState("idle"); // idle | starting | scanning | error
  const [error, setError] = useState(null); // { kind, message }
  const [devices, setDevices] = useState([]);
  const [mirrored, setMirrored] = useState(true);
  const [zoom, setZoomState] = useState(null); // { min, max, step, value } when supported
  const [torch, setTorchState] = useState(null); // boolean when supported
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
    trackRef.current = null;
    const video = videoRef.current;
    const stream = video?.srcObject;
    if (stream && typeof stream.getTracks === "function") {
      stream.getTracks().forEach((t) => t.stop());
    }
    if (video) video.srcObject = null;
    setZoomState(null);
    setTorchState(null);
    setStatus((s) => (s === "error" ? s : "idle"));
  }, []);

  const report = useCallback(
    (code, format) => {
      const now = Date.now();
      if (code === lastRef.current.code && now - lastRef.current.at < cooldownMs) return;
      lastRef.current = { code, at: now };
      onDetectedRef.current?.(code, format);
    },
    [cooldownMs],
  );

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
      const id = requestedDeviceId === null ? undefined : (requestedDeviceId ?? deviceId);
      try {
        if (createReader) {
          // Test seam: a reader that drives the video element itself.
          const reader = await createReader();
          controlsRef.current = await reader.decodeFromVideoDevice(id || undefined, videoRef.current, (result) => {
            if (!result) return;
            report(result.getText(), formatName(result.getBarcodeFormat?.()));
          });
        } else {
          controlsRef.current = await startCamera(id, videoRef.current, report, {
            onTrack: (track) => {
              trackRef.current = track;
              const caps = typeof track.getCapabilities === "function" ? track.getCapabilities() : {};
              const settings = typeof track.getSettings === "function" ? track.getSettings() : {};
              setMirrored(settings.facingMode !== "environment");
              if (caps.zoom && caps.zoom.max > caps.zoom.min) {
                setZoomState({ min: caps.zoom.min, max: caps.zoom.max, step: caps.zoom.step || 0.1, value: settings.zoom ?? caps.zoom.min });
              }
              if (caps.torch) setTorchState(false);
            },
          });
        }
        setStatus("scanning");
        // Device labels are only available after permission was granted.
        const list = await navigator.mediaDevices.enumerateDevices();
        setDevices(list.filter((d) => d.kind === "videoinput"));
      } catch (e) {
        stop();
        // A remembered camera that was unplugged: fall back to the default camera once.
        if (id && (e?.name === "OverconstrainedError" || e?.name === "NotFoundError")) {
          try {
            window.localStorage.removeItem("pos.cameraId");
          } catch {
            /* storage unavailable */
          }
          setDeviceId(undefined);
          return start(null);
        }
        setError(describe(e));
        setStatus("error");
      }
    },
    [createReader, deviceId, report, stop],
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

  const setZoom = useCallback(async (value) => {
    const track = trackRef.current;
    if (!track) return;
    try {
      await track.applyConstraints({ advanced: [{ zoom: value }] });
      setZoomState((z) => (z ? { ...z, value } : z));
    } catch {
      /* camera refused; keep the previous zoom */
    }
  }, []);

  const toggleTorch = useCallback(async () => {
    const track = trackRef.current;
    if (!track) return;
    const next = !torch;
    try {
      await track.applyConstraints({ advanced: [{ torch: next }] });
      setTorchState(next);
    } catch {
      /* not supported after all */
    }
  }, [torch]);

  /** Allows the same code to be scanned again right away (explicit re-scan). */
  const resetCooldown = useCallback(() => {
    lastRef.current = { code: null, at: 0 };
  }, []);

  useEffect(() => stop, [stop]);

  return {
    videoRef,
    status,
    error,
    devices,
    deviceId,
    mirrored,
    zoom,
    torch,
    start,
    stop,
    chooseDevice,
    setZoom,
    toggleTorch,
    resetCooldown,
  };
}

/** Opens the camera, attaches it to `video`, and runs the decode loop. Returns `{ stop }`. */
async function startCamera(deviceId, video, onCode, { onTrack }) {
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: {
      ...(deviceId ? { deviceId: { exact: deviceId } } : { facingMode: { ideal: "environment" } }),
      width: { ideal: 1920 },
      height: { ideal: 1080 },
      frameRate: { ideal: 30 },
    },
  });
  const track = stream.getVideoTracks()[0];
  try {
    await track.applyConstraints({ advanced: [{ focusMode: "continuous" }] });
  } catch {
    /* most laptop webcams are fixed-focus */
  }
  onTrack?.(track);
  video.srcObject = stream;
  video.setAttribute("playsinline", "true");
  video.muted = true;
  await video.play();

  const decode = await createDecoder();
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  let stopped = false;
  let timer = null;
  let pass = 0;

  const tick = async () => {
    if (stopped) return;
    const vw = video.videoWidth;
    const vh = video.videoHeight;
    if (vw && vh && video.readyState >= 2) {
      // Crop the framed area at the camera's native resolution (enlarging blurs bars further).
      const sw = Math.round(vw * SCAN_REGION.width);
      const sh = Math.round(vh * SCAN_REGION.height);
      canvas.width = sw;
      canvas.height = sh;
      ctx.drawImage(video, Math.round((vw - sw) / 2), Math.round((vh - sh) / 2), sw, sh, 0, 0, sw, sh);
      try {
        const hit = await decode(canvas, ctx, video, pass);
        if (hit && !stopped) onCode(hit.code, hit.format);
      } catch {
        /* a frame that cannot be decoded is normal while aiming */
      }
      pass += 1;
    }
    timer = setTimeout(tick, 80);
  };
  tick();

  return {
    stop() {
      stopped = true;
      clearTimeout(timer);
      stream.getTracks().forEach((t) => t.stop());
    },
  };
}

/** Returns `decode(canvas, ctx, video, pass) → { code, format } | null`, native detector first. */
async function createDecoder() {
  let native = null;
  if (typeof window !== "undefined" && "BarcodeDetector" in window) {
    try {
      const supported = await window.BarcodeDetector.getSupportedFormats();
      const formats = SCAN_FORMATS.map((f) => NATIVE_FORMATS[f]).filter((f) => supported.includes(f));
      if (formats.length) native = new window.BarcodeDetector({ formats });
    } catch {
      native = null;
    }
  }
  const zx = await zxingDecoder();
  return async (canvas, ctx, video, pass) => {
    if (native) {
      const found = await native.detect(pass % 2 === 0 ? video : canvas);
      if (found.length) return { code: found[0].rawValue, format: FROM_NATIVE[found[0].format] };
    }
    return zx(ctx.getImageData(0, 0, canvas.width, canvas.height), pass);
  };
}

let zxingModule = null;
async function loadZxing() {
  if (!zxingModule) {
    zxingModule = { library: await import("@zxing/library") };
  }
  return zxingModule;
}

/** Grey levels (0-255) from RGBA pixels. */
function luminance({ data, width, height }) {
  const out = new Uint8ClampedArray(width * height);
  for (let i = 0, j = 0; j < out.length; i += 4, j += 1) {
    out[j] = (data[i] * 77 + data[i + 1] * 150 + data[i + 2] * 29) >> 8;
  }
  return out;
}

/**
 * Horizontal unsharp mask: restores the edges of bars softened by a fixed-focus webcam.
 * 1D barcodes are read along rows, so sharpening across columns is what matters.
 */
function sharpen(lum, width, height, amount = 1) {
  const out = new Uint8ClampedArray(lum.length);
  for (let y = 0; y < height; y++) {
    const row = y * width;
    for (let x = 0; x < width; x++) {
      const i = row + x;
      const left = x > 0 ? lum[i - 1] : lum[i];
      const right = x < width - 1 ? lum[i + 1] : lum[i];
      out[i] = lum[i] + amount * (2 * lum[i] - left - right);
    }
  }
  return out;
}

async function zxingDecoder() {
  const { library } = await loadZxing();
  const hints = new Map();
  hints.set(library.DecodeHintType.POSSIBLE_FORMATS, SCAN_FORMATS.map((f) => library.BarcodeFormat[f]));
  hints.set(library.DecodeHintType.TRY_HARDER, true);
  const reader = new library.MultiFormatReader();
  reader.setHints(hints);
  return (image, pass) => {
    // Four passes in turn: plain/sharpened image × hybrid/global-histogram binarizer
    // (hybrid suits uneven light, global suits soft 1D bars).
    const { width, height } = image;
    let lum = luminance(image);
    if (pass % 4 >= 2) lum = sharpen(lum, width, height);
    const source = new library.RGBLuminanceSource(lum, width, height);
    const binarizer = pass % 2 === 0 ? new library.HybridBinarizer(source) : new library.GlobalHistogramBinarizer(source);
    try {
      const result = reader.decodeWithState(new library.BinaryBitmap(binarizer));
      return { code: result.getText(), format: formatName(result.getBarcodeFormat()) };
    } catch {
      return null;
    } finally {
      reader.reset();
    }
  };
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
