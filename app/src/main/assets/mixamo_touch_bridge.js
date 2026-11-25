(function () {
    if (window.__MIXAMO_STUDIO_INJECTED__) return;
    window.__MIXAMO_STUDIO_INJECTED__ = true;

    let mouseEmulationEnabled = false;

    function createMouseEvent(type, touchEvent) {
        const touch = touchEvent.changedTouches[0];
        if (!touch) return;
        const mouseEvent = new MouseEvent(type, {
            bubbles: true,
            cancelable: true,
            view: window,
            detail: 1,
            screenX: touch.screenX,
            screenY: touch.screenY,
            clientX: touch.clientX,
            clientY: touch.clientY,
            ctrlKey: touchEvent.ctrlKey,
            shiftKey: touchEvent.shiftKey,
            altKey: touchEvent.altKey,
            metaKey: touchEvent.metaKey,
            button: 0,
            buttons: type === "mouseup" ? 0 : 1
        });
        touch.target.dispatchEvent(mouseEvent);
    }

    function isOverlayElement(target) {
        return target && target.closest && target.closest("#mixamo-studio-hud");
    }

    document.addEventListener("touchstart", function (e) {
        if (!mouseEmulationEnabled || isOverlayElement(e.target)) return;
        createMouseEvent("mousedown", e);
    }, { passive: false, capture: true });

    document.addEventListener("touchmove", function (e) {
        if (!mouseEmulationEnabled || isOverlayElement(e.target)) return;
        e.preventDefault();
        createMouseEvent("mousemove", e);
    }, { passive: false, capture: true });

    document.addEventListener("touchend", function (e) {
        if (!mouseEmulationEnabled || isOverlayElement(e.target)) return;
        createMouseEvent("mouseup", e);
    }, { passive: false, capture: true });

    // Intercepta downloads via Blob URL caso o Mixamo use blob:
    const origClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
        if (this.href && this.href.startsWith("blob:") && window.MixamoNative) {
            const filename = this.download || ("animacao_" + Date.now() + ".fbx");
            fetch(this.href)
                .then(res => res.blob())
                .then(blob => {
                    const reader = new FileReader();
                    reader.onloadend = function () {
                        const base64 = reader.result.split(",")[1];
                        window.MixamoNative.saveBlobDownload(base64, filename);
                    };
                    reader.readAsDataURL(blob);
                })
                .catch(err => console.error("Erro ao capturar blob:", err));
            return;
        }
        return origClick.apply(this, arguments);
    };

    function injectHud() {
        if (document.getElementById("mixamo-studio-hud")) return;

        const bar = document.createElement("div");
        bar.id = "mixamo-studio-hud";
        bar.style.cssText = [
            "position:fixed",
            "bottom:14px",
            "left:14px",
            "z-index:2147483647",
            "display:flex",
            "gap:8px",
            "align-items:center",
            "font-family:system-ui,-apple-system,sans-serif"
        ].join(";");

        const btnMouse = document.createElement("button");
        btnMouse.textContent = "👆 Rolagem";
        btnMouse.style.cssText = [
            "padding:9px 14px",
            "border-radius:999px",
            "border:1px solid rgba(255,255,255,0.22)",
            "background:rgba(15,23,42,0.88)",
            "color:#f8fafc",
            "font-size:12px",
            "font-weight:600",
            "backdrop-filter:blur(8px)",
            "box-shadow:0 4px 14px rgba(0,0,0,0.45)",
            "cursor:pointer"
        ].join(";");

        btnMouse.addEventListener("click", function (ev) {
            ev.stopPropagation();
            mouseEmulationEnabled = !mouseEmulationEnabled;
            if (mouseEmulationEnabled) {
                btnMouse.textContent = "🖱️ Mouse Ativo (Arrastar)";
                btnMouse.style.background = "rgba(16,185,129,0.92)";
                btnMouse.style.color = "#022c22";
            } else {
                btnMouse.textContent = "👆 Rolagem";
                btnMouse.style.background = "rgba(15,23,42,0.88)";
                btnMouse.style.color = "#f8fafc";
            }
        });

        const btnWorld = document.createElement("button");
        btnWorld.textContent = "🌍 Mundo 3D";
        btnWorld.style.cssText = [
            "padding:9px 14px",
            "border-radius:999px",
            "border:1px solid rgba(56,189,248,0.45)",
            "background:rgba(2,132,199,0.92)",
            "color:#ffffff",
            "font-size:12px",
            "font-weight:700",
            "backdrop-filter:blur(8px)",
            "box-shadow:0 4px 14px rgba(0,0,0,0.45)",
            "cursor:pointer"
        ].join(";");

        btnWorld.addEventListener("click", function (ev) {
            ev.stopPropagation();
            if (window.MixamoNative) {
                window.MixamoNative.openWorld3D();
            }
        });

        bar.appendChild(btnMouse);
        bar.appendChild(btnWorld);
        document.body.appendChild(bar);
    }

    if (document.body) {
        injectHud();
    } else {
        document.addEventListener("DOMContentLoaded", injectHud);
    }
})();
