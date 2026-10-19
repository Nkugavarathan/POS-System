"use client";

import { useEffect, useRef, useState } from "react";
import { CameraOff, Flashlight, RefreshCw, ScanBarcode, SwitchCamera, ZoomIn } from "lucide-react";
import { Modal } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/feedback";
import { Input, Select } from "@/components/ui/form";
import { SCAN_REGION, useBarcodeScanner } from "./use-barcode-scanner";

/**
 * Camera scanning dialog. Each detected code is passed to `onScan(code, format)`; the dialog
 * stays open for the next item (like a real till). Manual entry is always available.
 */
export function ScannerDialog({ open, onClose, onScan, lastResult, createReader }) {
  const [manual, setManual] = useState("");
  const scanner = useBarcodeScanner({ onDetected: onScan, createReader });
  const { start, stop } = scanner;
  // Keep the latest functions in refs so the effect only reacts to `open` (switching camera restarts itself).
  const startRef = useRef(start);
  const stopRef = useRef(stop);
  startRef.current = start;
  stopRef.current = stop;

  useEffect(() => {
    if (open) startRef.current();
    else stopRef.current();
  }, [open]);

  const close = () => {
    stop();
    onClose();
  };

  return (
    <Modal open={open} onOpenChange={(o) => !o && close()} title="Scan barcode" description="Hold the barcode inside the frame. Each scan adds one item." size="lg">
      <div className="space-y-4">
        {scanner.error ? (
          <Alert tone="danger" title="Camera unavailable">
            <p>{scanner.error.message}</p>
            <Button size="sm" variant="outline" className="mt-3" onClick={() => start()}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
            </Button>
          </Alert>
        ) : (
          <div className="relative overflow-hidden rounded-lg bg-slate-900">
            {/* Front cameras are shown mirrored so aiming feels natural; decoding uses the raw frames. */}
            <video
              ref={scanner.videoRef}
              className="block h-auto min-h-48 w-full"
              style={scanner.mirrored ? { transform: "scaleX(-1)" } : undefined}
              muted
              playsInline
              aria-label="Camera preview"
            />
            {/* Scanning frame = exactly the area that is decoded. */}
            <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
              <div
                className="relative rounded-md border-2 border-secondary shadow-[0_0_0_9999px_rgba(15,23,42,0.45)]"
                style={{ width: `${SCAN_REGION.width * 100}%`, height: `${SCAN_REGION.height * 100}%` }}
              >
                {scanner.status === "scanning" && (
                  <div className="absolute inset-x-2 top-1/2 h-0.5 animate-pulse bg-red-500/80" aria-hidden="true" />
                )}
              </div>
            </div>
            {scanner.status === "starting" && (
              <p className="absolute inset-x-0 bottom-3 text-center text-sm text-white">Starting camera…</p>
            )}
          </div>
        )}

        {!scanner.error && (
          <p className="text-xs text-slate-500">
            Tip: hold the pack 15-25 cm away so the barcode is sharp and spans about half the frame. Laptop cameras
            cannot focus very close - if the bars look blurry, move it back a little. Avoid glare on shiny packs.
          </p>
        )}

        {lastResult && (
          <div
            role="status"
            className={`rounded-md px-3 py-2 text-sm ${lastResult.ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-800"}`}
          >
            {lastResult.message}
          </div>
        )}

        <div className="flex flex-wrap items-end gap-3">
          {scanner.devices.length > 1 && (
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <SwitchCamera className="h-4 w-4" aria-hidden="true" />
              <span className="sr-only">Camera</span>
              <Select value={scanner.deviceId ?? ""} onChange={(e) => scanner.chooseDevice(e.target.value)} className="w-56">
                {scanner.devices.map((d, i) => (
                  <option key={d.deviceId || i} value={d.deviceId}>
                    {d.label || `Camera ${i + 1}`}
                  </option>
                ))}
              </Select>
            </label>
          )}
          {scanner.zoom && (
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <ZoomIn className="h-4 w-4" aria-hidden="true" />
              <span className="sr-only">Zoom</span>
              <input
                type="range"
                min={scanner.zoom.min}
                max={scanner.zoom.max}
                step={scanner.zoom.step}
                value={scanner.zoom.value}
                onChange={(e) => scanner.setZoom(Number(e.target.value))}
                className="w-32 accent-[var(--primary)]"
                aria-label="Camera zoom"
              />
            </label>
          )}
          {scanner.torch !== null && (
            <Button variant={scanner.torch ? "secondary" : "outline"} size="sm" onClick={scanner.toggleTorch} aria-pressed={scanner.torch}>
              <Flashlight className="h-4 w-4" aria-hidden="true" /> Light
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={scanner.resetCooldown}>
            <ScanBarcode className="h-4 w-4" aria-hidden="true" /> Scan same item again
          </Button>
          <Button variant="ghost" size="sm" onClick={close}>
            <CameraOff className="h-4 w-4" aria-hidden="true" /> Stop camera
          </Button>
        </div>

        <form
          className="flex gap-2 border-t border-slate-200 pt-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (manual.trim()) {
              onScan(manual.trim(), undefined);
              setManual("");
            }
          }}
        >
          <Input aria-label="Type barcode" placeholder="Or type the barcode and press Enter" inputMode="numeric" value={manual} onChange={(e) => setManual(e.target.value)} />
          <Button type="submit" variant="outline">
            Add
          </Button>
        </form>
        <p className="text-xs text-slate-500">
          Camera access needs HTTPS in production (http://localhost works during development). A USB barcode scanner also works: it types into the till&apos;s search box.
        </p>
      </div>
    </Modal>
  );
}
