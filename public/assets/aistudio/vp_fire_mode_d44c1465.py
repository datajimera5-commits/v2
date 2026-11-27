# -- coding: utf-8 --
"""
==================== vp_fire_mode.py (DO NOT REMOVE) ====================
FIRE MODE — dedicated module. Method me Pro Mode ke aage 'Fire Mode' button.
Fire Mode = NO ACTIVATION. Fire ka APNA '🔥 Generate' button (purana gen_btn hide),
isliye v24/GF58/ribbon usko swap hi nahi kar sakte -> koi redirect/activation NAHI.
Order -> Google Drive (Service Account) -> admin Vid Order -> admin UID naam se video
daale -> software detect (thumbnail) -> Save = download -> Drive video+order auto-delete.
Config: local fire_config.json (drive_url + sa_json) primary, backend fallback.
======================================================================
"""
import os
import io
import re
import json
import time
import base64
import hashlib
import sys
import shutil
import threading
import subprocess
import urllib.request
import urllib.parse

VER = "vp-fire-module-v10 fire-diag-v11 oauth-v14 admin-status-v16 clean-v21"
BASE = os.path.dirname(os.path.abspath(__file__))
ACCENT = "#F97316"
IDLE_TX = "#9CA3AF"
DRIVE_KEY = "fire_drive_url"
SA_KEY = "fire_sa_json"
ST = {"inst": False, "tick": False, "orders": {}, "lock": threading.RLock(),
      "poll_t": 0.0, "tok": None, "tok_exp": 0.0, "loaded": False}


def _last(msg):
    # fire-diag-v11: aakhri Drive error yaad rakho (log me asli reason dikhe)
    try:
        ST["last_err"] = str(msg)[:300]
        print("🔎 [" + VER + "] " + str(msg)[:300])
    except Exception:
        pass
    return ""


def _log(app, m):
    try:
        app.log(m)
    except Exception:
        try:
            print(m)
        except Exception:
            pass


# ---------- config ----------
def _folder_id(link):
    try:
        m = re.search(r"/folders/([A-Za-z0-9_-]{10,})", str(link or ""))
        if m:
            return m.group(1)
        m = re.search(r"[?&]id=([A-Za-z0-9_-]{10,})", str(link or ""))
        if m:
            return m.group(1)
    except Exception:
        pass
    return ""


def _cfg(app):
    for cand in (os.path.join(BASE, "fire_config.json"),
                 os.path.join(BASE, "admin", "fire_config.json")):
        try:
            if os.path.isfile(cand):
                with open(cand, "r", encoding="utf-8") as f:
                    d = json.loads(f.read() or "{}")
                link = str(d.get("drive_url") or d.get("fire_drive_url") or "")
                sa = d.get("sa_json") or d.get("sa") or {}
                if isinstance(sa, str):
                    sa = json.loads(sa or "{}")
                fid = _folder_id(link)
                if fid and sa.get("client_email"):
                    return fid, sa
        except Exception:
            pass
    # backend fallback (read-only)
    try:
        base = (app._main_api_url() or "").rstrip("/")
        link, sa = "", {}
        for pl in ({"op": "list"}, {"action": "list"}, {}):
            try:
                res = app.sub_api_at(base, "/admin/settings", pl) if hasattr(app, "sub_api_at") else None
            except Exception:
                res = None
            rows = None
            if isinstance(res, dict):
                rows = res.get("settings") or res.get("rows") or res.get("data")
            elif isinstance(res, list):
                rows = res
            if isinstance(rows, dict):
                rows = [{"key": k, "value": v} for k, v in rows.items()]
            if isinstance(rows, list):
                for r in rows:
                    if str(r.get("key", "")) == DRIVE_KEY:
                        link = str(r.get("value", "") or "")
                    if str(r.get("key", "")) == SA_KEY:
                        try:
                            sa = json.loads(str(r.get("value", "") or "{}"))
                        except Exception:
                            sa = {}
                break
        return _folder_id(link), sa
    except Exception:
        return "", {}


def _device(app):
    try:
        return str(getattr(app, "sub_device", None) or app.dlh_device_id() or "dev")
    except Exception:
        return "dev"


def _user(app):
    try:
        return str((getattr(app, "sub_info", None) or {}).get("user_id", "") or "user")
    except Exception:
        return "user"


def _tabuid(app, p):
    if getattr(p, "_fire_tabuid", None):
        return p._fire_tabuid
    seed = _user(app) + "|" + _device(app) + "|T" + str(p.index)
    h = hashlib.md5(seed.encode()).hexdigest()[:6].upper()
    p._fire_tabuid = "FO-" + h + "-T" + str(p.index)
    return p._fire_tabuid


# ---------- Service Account JWT (pure stdlib RS256) ----------
def _der_len(b, i):
    l = b[i]
    i += 1
    if l & 0x80:
        n = l & 0x7F
        l = int.from_bytes(b[i:i + n], "big")
        i += n
    return l, i


def _der_children(b):
    out, i = [], 0
    while i < len(b):
        tag = b[i]
        l, j = _der_len(b, i + 1)
        out.append((tag, b[j:j + l]))
        i = j + l
    return out


def _b64u(b):
    return base64.urlsafe_b64encode(b).decode().rstrip("=")


def _sa_token(sa):
    try:
        now = int(time.time())
        if ST["tok"] and now < int(ST["tok_exp"]) - 60:
            return ST["tok"]
        pem = str(sa.get("private_key", "") or "")
        der = base64.b64decode("".join(pem.splitlines()[1:-1]))
        rsa = _der_children(_der_children(der)[0][1])[2][1]
        ints = [int.from_bytes(v, "big") for t, v in _der_children(rsa) if t == 0x02]
        n, d = ints[1], ints[3]
        k = (n.bit_length() + 7) // 8
        h = _b64u(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
        c = _b64u(json.dumps({"iss": sa.get("client_email", ""),
            "scope": "https://www.googleapis.com/auth/drive",
            "aud": "https://oauth2.googleapis.com/token", "iat": now, "exp": now + 3600}).encode())
        si = h + "." + c
        dg = hashlib.sha256(si.encode()).digest()
        t = bytes.fromhex("3031300d060960864801650304020105000420") + dg
        em = b"\x00\x01" + b"\xff" * (k - len(t) - 3) + b"\x00" + t
        sig = pow(int.from_bytes(em, "big"), d, n).to_bytes(k, "big")
        jwt = si + "." + _b64u(sig)
        data = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                       "assertion": jwt}).encode()
        rq = urllib.request.Request("https://oauth2.googleapis.com/token", data=data,
                                    headers={"Content-Type": "application/x-www-form-urlencoded"})
        res = json.loads(urllib.request.urlopen(rq, timeout=30).read().decode())
        tok = str(res.get("access_token", "") or "")
        if tok:
            ST["tok"], ST["tok_exp"] = tok, now + int(res.get("expires_in", 3600) or 3600)
        else:
            _last("Drive TOKEN fail: " + json.dumps(res)[:220])
        return tok
    except urllib.error.HTTPError as _he:
        try:
            _b = _he.read().decode("utf-8", "ignore")[:250]
        except Exception:
            _b = ""
        _last("Drive TOKEN HTTP " + str(_he.code) + ": " + _b)
        return ""
    except Exception as _e:
        _last("Drive TOKEN error: " + str(_e)[:200])
        return ""


def _dreq(url, tok, method="GET", data=None, ctype=None):
    h = {"Authorization": "Bearer " + tok}
    if ctype:
        h["Content-Type"] = ctype
    return urllib.request.urlopen(urllib.request.Request(url, data=data, headers=h, method=method), timeout=60)


def _dlist(fid, sa):
    tok = _sa_token(sa)
    if not tok:
        return []
    q = urllib.parse.quote("'" + fid + "' in parents and trashed=false")
    url = ("https://www.googleapis.com/drive/v3/files?q=" + q +
           "&fields=files(id,name,mimeType,size)&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true")
    try:
        return list(json.loads(_dreq(url, tok).read().decode()).get("files", []) or [])
    except Exception:
        return []


def _dupload(fid, sa, name, mime, data):
    tok = _sa_token(sa)
    if not tok:
        return ""
    b = "----fb" + str(int(time.time() * 1000))
    meta = json.dumps({"name": name, "parents": [fid]})
    body = (("--" + b + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta +
             "\r\n--" + b + "\r\nContent-Type: " + mime + "\r\n\r\n").encode() + data +
            ("\r\n--" + b + "--\r\n").encode())
    try:
        res = json.loads(_dreq("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true",
                               tok, "POST", body, "multipart/related; boundary=" + b).read().decode())
        _id = str(res.get("id", "") or "")
        if not _id:
            _last("Drive UPLOAD: id nahi aaya — " + json.dumps(res)[:220])
        return _id
    except urllib.error.HTTPError as _he:
        try:
            _b = _he.read().decode("utf-8", "ignore")[:250]
        except Exception:
            _b = ""
        _last("Drive UPLOAD HTTP " + str(_he.code) + ": " + _b)
        return ""
    except Exception as _e:
        _last("Drive UPLOAD error: " + str(_e)[:200])
        return ""


def _ddownload(fid, sa, dest):
    tok = _sa_token(sa)
    if not tok:
        return False
    try:
        resp = _dreq("https://www.googleapis.com/drive/v3/files/" + fid + "?alt=media&supportsAllDrives=true", tok)
        part = dest + ".part" + str(threading.get_ident())
        try:
            with open(part, "wb") as f:
                while True:
                    c = resp.read(262144)
                    if not c:
                        break
                    f.write(c)
            if os.path.getsize(part) <= 1024:
                return False
            os.replace(part, dest)      # poori file ban jaye tabhi dest dikhe
            return True
        finally:
            try:
                if os.path.isfile(part):
                    os.remove(part)
            except Exception:
                pass
    except Exception:
        return False


def _tokens(sa):
    """OAuth (file owner) pehle, phir Service Account. Owner token se hi permanent delete chalta hai."""
    out = []
    for fn in (_o14_token, _sa_token):
        try:
            t = fn(sa)
            if t and t not in out:
                out.append(t)
        except Exception:
            pass
    return out


def _ddelete(fid, sa):
    """True = file ab Drive me nahi hai (delete / trash / pehle se gayab). False = kisi token se delete nahi hui."""
    fid = str(fid or "")
    if not fid:
        return True
    url = "https://www.googleapis.com/drive/v3/files/" + fid + "?supportsAllDrives=true"
    miss = 0
    toks = _tokens(sa)
    for tok in toks:
        try:
            _dreq(url, tok, "DELETE")
            return True
        except urllib.error.HTTPError as e:
            if e.code == 404:
                miss += 1
                continue
        except Exception:
            pass
        # permanent delete allowed nahi (owner nahi) -> trash me daalo (editor ko allowed)
        try:
            _dreq(url, tok, "PATCH", json.dumps({"trashed": True}).encode(), "application/json")
            return True
        except urllib.error.HTTPError as e:
            if e.code == 404:
                miss += 1
        except Exception:
            pass
    return bool(toks) and miss == len(toks)


def _dstate(fid, sa):
    """'ok' = file hai | 'gone' = delete/trash ho chuki | None = pata nahi (network glitch)."""
    fid = str(fid or "")
    if not fid:
        return None
    url = "https://www.googleapis.com/drive/v3/files/" + fid + "?fields=id,trashed&supportsAllDrives=true"
    res = None
    for tok in _tokens(sa):
        try:
            d = json.loads(_dreq(url, tok).read().decode("utf-8", "ignore") or "{}")
            return "gone" if d.get("trashed") else "ok"
        except urllib.error.HTTPError as e:
            if e.code == 404:
                res = "gone"
        except Exception:
            pass
    return res


def _dthumb(fid, sa):
    """Video ka Drive-generated thumbnail (JPEG bytes) ya None (abhi bana nahi)."""
    fid = str(fid or "")
    if not fid:
        return None
    url = "https://www.googleapis.com/drive/v3/files/" + fid + "?fields=thumbnailLink&supportsAllDrives=true"
    for tok in _tokens(sa):
        try:
            d = json.loads(_dreq(url, tok).read().decode("utf-8", "ignore") or "{}")
            link = str(d.get("thumbnailLink") or "")
            if not link:
                continue
            link = re.sub(r"=s\d+$", "=s640", link)
            rq = urllib.request.Request(link, headers={"Authorization": "Bearer " + tok})
            b = urllib.request.urlopen(rq, timeout=30).read()
            if b and len(b) > 500:
                return b
        except Exception:
            continue
    return None


def _img_ok(b):
    """False = blank / black / bahut dull image (Drive kabhi kaali first-frame deta hai)."""
    try:
        from PIL import Image, ImageStat
        im = Image.open(io.BytesIO(b)).convert("L")
        im.thumbnail((64, 64))
        st = ImageStat.Stat(im)
        return st.mean[0] > 28 and st.stddev[0] > 12
    except Exception:
        return False


def _jpg(im):
    buf = io.BytesIO()
    im.convert("RGB").save(buf, format="JPEG", quality=85)
    return buf.getvalue()


def _ffmpeg_exes():
    out = []
    try:
        import imageio_ffmpeg
        out.append(imageio_ffmpeg.get_ffmpeg_exe())
    except Exception:
        pass
    w = shutil.which("ffmpeg")
    if w:
        out.append(w)
    for rel in ("ffmpeg.exe", os.path.join("ffmpeg", "ffmpeg.exe"), os.path.join("bin", "ffmpeg.exe"),
                os.path.join("ffmpeg", "bin", "ffmpeg.exe"), "ffmpeg"):
        c = os.path.join(BASE, rel)
        if os.path.isfile(c):
            out.append(c)
    return [x for i, x in enumerate(out) if x and x not in out[:i]]


def _frame_from_video(path):
    """Local video se achha (kaala nahi) frame, JPEG bytes. cv2 -> PyAV -> ffmpeg. Kuch na mile to None."""
    try:
        import cv2
        cap = cv2.VideoCapture(path)
        try:
            n = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
            for fr_pos in (0.15, 0.35, 0.55, 0.05, 0.75):
                if n > 10:
                    cap.set(cv2.CAP_PROP_POS_FRAMES, int(n * fr_pos))
                ok, fr = cap.read()
                if ok and fr is not None:
                    ok2, buf = cv2.imencode(".jpg", fr)
                    if ok2 and _img_ok(buf.tobytes()):
                        return buf.tobytes()
        finally:
            cap.release()
    except Exception:
        pass
    try:
        import av
        c = av.open(path)
        try:
            best = None
            for i, fr in enumerate(c.decode(video=0)):
                if i in (12, 30, 60, 90):
                    b = _jpg(fr.to_image())
                    if _img_ok(b):
                        return b
                    best = best or b
                if i > 90:
                    break
        finally:
            c.close()
    except Exception:
        pass
    for ff in _ffmpeg_exes():
        for ss in ("1", "3", "0.3", "6"):
            try:
                r = subprocess.run([ff, "-v", "error", "-ss", ss, "-i", path, "-frames:v", "1",
                                    "-f", "image2pipe", "-vcodec", "mjpeg", "-"],
                                   capture_output=True, timeout=40,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                if r.stdout and len(r.stdout) > 500 and _img_ok(r.stdout):
                    return r.stdout
            except Exception:
                continue
    return None


def _placeholder_art():
    """Thumbnail na mile to bhi box khali nahi: dark card + play icon."""
    try:
        from PIL import Image, ImageDraw
        W, H = 352, 200
        im = Image.new("RGB", (W, H), (23, 26, 38))
        d = ImageDraw.Draw(im)
        for y in range(H):
            k = y / H
            d.line([(0, y), (W, y)], fill=(int(30 + 14 * k), int(34 + 10 * k), int(52 + 6 * k)))
        cx, cy, r = W // 2, H // 2, 38
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(249, 115, 22))
        d.polygon([(cx - 12, cy - 20), (cx - 12, cy + 20), (cx + 22, cy)], fill=(255, 255, 255))
        return _jpg(im)
    except Exception:
        return None


def _downloads_dir():
    """User ka real Downloads folder (Windows me redirect hua ho to bhi)."""
    try:
        if os.name == "nt":
            import ctypes

            class _G(ctypes.Structure):
                _fields_ = [("a", ctypes.c_uint32), ("b", ctypes.c_uint16), ("c", ctypes.c_uint16),
                            ("d", ctypes.c_ubyte * 8)]
            g = _G(0x374DE290, 0x123F, 0x4565, (ctypes.c_ubyte * 8)(0x91, 0x64, 0x39, 0xC4, 0x92, 0x5E, 0x46, 0x7B))
            buf = ctypes.c_wchar_p()
            if ctypes.windll.shell32.SHGetKnownFolderPath(ctypes.byref(g), 0, None, ctypes.byref(buf)) == 0:
                d = buf.value
                ctypes.windll.ole32.CoTaskMemFree(buf)
                if d and os.path.isdir(d):
                    return d
    except Exception:
        pass
    d = os.path.join(os.path.expanduser("~"), "Downloads")
    return d if os.path.isdir(d) else os.path.expanduser("~")


def _save_dir():
    d = os.path.join(_downloads_dir(), "VidoPro Videos")
    try:
        os.makedirs(d, exist_ok=True)
        return d
    except Exception:
        d2 = os.path.join(BASE, "VidoPro Videos")
        try:
            os.makedirs(d2, exist_ok=True)
        except Exception:
            pass
        return d2


def _cache_path(uid):
    d = os.path.join(BASE, "fire_cache")
    try:
        os.makedirs(d, exist_ok=True)
    except Exception:
        pass
    return os.path.join(d, str(uid) + ".mp4")


def _rm(path):
    try:
        if path and os.path.isfile(path):
            os.remove(path)
    except Exception:
        pass


def _open_file(path):
    try:
        if os.name == "nt":
            os.startfile(path)
        elif sys.platform == "darwin":
            subprocess.Popen(["open", path])
        else:
            subprocess.Popen(["xdg-open", path])
        return True
    except Exception:
        return False


def _purge_uid(files, uid, sa, meta):
    """Ek order ki SAARI files (order json + images + video) Drive se hatao. Return = kitni fail hui."""
    ids = set(str(i) for i in (meta.get("imgs") or []) if i)
    for k in ("order_file", "file"):
        if meta.get(k):
            ids.add(str(meta[k]))
    for f in files or []:
        n = str(f.get("name", "") or "")
        if _uid_hit(n, uid) or n == "ORDER_" + uid + ".json" or n.startswith("IMG_" + uid + "_"):
            ids.add(str(f.get("id", "")))
    bad = 0
    for i in ids:
        if i and not _ddelete(i, sa):
            bad += 1
    return bad


def _user_msg(msg):
    """User ko 'admin' / 'backend' jaisa internal text kabhi nahi dikhana."""
    m = str(msg or "").strip()
    if re.search(r"\badmin\b|\bbackend\b|super\s*admin", m, re.I):
        return ""
    return m


# ---------- gather ----------
def _gather(app, p):
    prompt = ""
    for nm in ("prompt_box", "prompt_txt", "prompt_entry", "prompt", "txt_prompt", "prompt_input", "prompt_text"):
        w = getattr(p, nm, None)
        if w is None:
            continue
        try:
            prompt = str(w.get("1.0", "end-1c") or "").strip()
        except Exception:
            try:
                prompt = str(w.get() or "").strip()
            except Exception:
                prompt = ""
        if prompt:
            break
    if not prompt:
        found = []
        def scan(w, d):
            if d > 6 or found:
                return
            for c in list(w.winfo_children()):
                try:
                    cn = c.__class__.__name__.lower()
                    if "text" in cn:
                        try:
                            t = str(c.get("1.0", "end-1c") or "").strip()
                        except Exception:
                            t = ""
                        if t:
                            found.append(t)
                            return
                    scan(c, d + 1)
                except Exception:
                    continue
        try:
            scan(p, 0)
        except Exception:
            pass
        if found:
            prompt = found[0]
    imgs = []
    for nm in ("images", "ref_images", "refs", "_ref_paths", "image_paths", "selected_images"):
        v = getattr(p, nm, None)
        if isinstance(v, (list, tuple)):
            imgs = list(v)
            break
    outs = []
    for it in imgs[:10]:
        if isinstance(it, str):
            outs.append(it)
        elif isinstance(it, dict):
            for k in ("path", "file", "p"):
                if isinstance(it.get(k), str):
                    outs.append(it[k])
                    break
    dur, ratio = 15, "16:9"
    try:
        dur = int(str(p.duration_var.get()).lower().replace("s", "").strip() or 15)
    except Exception:
        pass
    try:
        r = str(p.ratio_var.get()).strip()
        if ":" in r:
            ratio = r
    except Exception:
        pass
    return prompt, outs, dur, ratio


def _img_bytes(path):
    try:
        from PIL import Image
        im = Image.open(path).convert("RGB")
        im.thumbnail((1600, 1600))
        buf = io.BytesIO()
        im.save(buf, format="JPEG", quality=88)
        return buf.getvalue()
    except Exception:
        try:
            with open(path, "rb") as f:
                return f.read()
        except Exception:
            return b""


# ---------- card (professional status panel) ----------
def _dbg_on():
    try:
        for cand in (os.path.join(BASE, "fire_config.json"), os.path.join(BASE, "admin", "fire_config.json")):
            if os.path.isfile(cand):
                with open(cand, "r", encoding="utf-8") as f:
                    return bool(json.loads(f.read() or "{}").get("debug"))
    except Exception:
        pass
    return False


def _dlog(app, m):
    """Detailed diagnostics: hamesha fire_debug.log file me; app log me sirf jab fire_config.json me "debug": true ho."""
    try:
        with open(os.path.join(BASE, "fire_debug.log"), "a", encoding="utf-8") as f:
            f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + str(m) + "\n")
    except Exception:
        pass
    if _dbg_on():
        _log(app, m)


def _fmt_el(sec):
    m, s = divmod(max(0, int(sec)), 60)
    return "%02d:%02d" % (m, s)


SPIN = ["\u25d0", "\u25d3", "\u25d1", "\u25d2"]
# state -> (colour, icon)
STATE_UI = {
    "gen": (ACCENT, "\u2726"), "ready": ("#10B981", "\u2714"), "busy": ("#38BDF8", "\u2193"),
    "ok": ("#10B981", "\u2714"), "err": ("#EF4444", "!"), "hold": ("#38BDF8", "\u2016"),
    "retry": ("#A78BFA", "\u21bb"), "cancelled": ("#EF4444", "\u2715"), "failed": ("#EF4444", "!"),
}
_BAD = ("retry", "cancelled", "failed", "err")
STATE_UI["dlfail"] = ("#EF4444", "!")
_MEDIA = ("ready", "busy", "ok", "dlfail")


def _card(app, p):
    try:
        c = getattr(p, "_fire_card", None)
        if c is not None and c.winfo_exists():
            return c
        import customtkinter as ctk
        c = ctk.CTkFrame(p, fg_color="#0F1117", corner_radius=12, border_width=1, border_color="#262A38")
        top = ctk.CTkFrame(c, fg_color="transparent")
        top.pack(fill="x", padx=16, pady=(14, 12))
        top.grid_columnconfigure(0, weight=1)
        left = ctk.CTkFrame(top, fg_color="transparent")
        left.grid(row=0, column=0, sticky="w")
        head = ctk.CTkFrame(left, fg_color="transparent")
        head.pack(fill="x")
        p._fire_card_hd = head
        ic = ctk.CTkLabel(head, text="\u2726", width=20, font=ctk.CTkFont(size=15, weight="bold"), text_color=ACCENT)
        ic.pack(side="left")
        ti = ctk.CTkLabel(head, text="", anchor="w", font=ctk.CTkFont(size=13, weight="bold"), text_color="#F3F5F9")
        ti.pack(side="left", padx=(8, 0), fill="x", expand=True)
        su = ctk.CTkLabel(left, text="", anchor="w", justify="left", wraplength=420,
                          font=ctk.CTkFont(size=11), text_color="#8B90A0")
        su.pack(fill="x", padx=(28, 0), pady=(3, 0))
        # admin ka custom message (status ke neeche)
        nf = ctk.CTkFrame(c, fg_color="#171A26", corner_radius=8)
        nl = ctk.CTkLabel(nf, text="", anchor="w", justify="left", wraplength=380,
                          font=ctk.CTkFont(size=11), text_color="#E5E7EB")
        nl.pack(fill="x", padx=10, pady=7)
        # video ready: thumbnail + Save + Play
        mf = ctk.CTkFrame(top, fg_color="transparent")   # buttons: text ke right side
        th = None                       # thumbnail hata diya
        bx = ctk.CTkFrame(mf, fg_color="transparent")
        bx.pack(side="right")
        sv = ctk.CTkButton(bx, text="\u2193  Download", width=150, height=38, corner_radius=10,
                           fg_color="#10B981", hover_color="#059669", text_color="#FFFFFF",
                           font=ctk.CTkFont(size=13, weight="bold"), cursor="hand2",
                           command=lambda q=p: _save(app, q, getattr(q, "_fire_cur", "")))
        sv.pack(side="left", padx=(0, 10))
        pl = ctk.CTkButton(bx, text="\u25b6  Play", width=110, height=38, corner_radius=10,
                           fg_color="#1F2430", hover_color="#2A3040", border_width=1, border_color="#3A4156",
                           text_color="#E5E7EB", font=ctk.CTkFont(size=13, weight="bold"), cursor="hand2",
                           command=lambda q=p: _play(app, q))
        pl.pack(side="left")
        bt = ctk.CTkButton(c, text="Try again", width=110, height=30, corner_radius=8,
                           fg_color=ACCENT, hover_color="#EA580C", text_color="#FFFFFF",
                           font=ctk.CTkFont(size=12, weight="bold"), cursor="hand2",
                           command=lambda q=p: _dismiss(app, q))
        try:
            pb = ctk.CTkProgressBar(c, height=4, corner_radius=2, fg_color="#1C2030",
                                    progress_color=ACCENT, mode="indeterminate", indeterminate_speed=1.2)
        except Exception:
            pb = ctk.CTkProgressBar(c, height=4, corner_radius=2, fg_color="#1C2030",
                                    progress_color=ACCENT, mode="indeterminate")
        pb.pack(fill="x", padx=16, pady=(0, 14))
        p._fire_card, p._fire_card_ic, p._fire_card_ti = c, ic, ti
        p._fire_card_su, p._fire_card_pb, p._fire_card_th = su, pb, th
        p._fire_card_nf, p._fire_card_nl, p._fire_card_bt = nf, nl, bt
        p._fire_card_mf, p._fire_card_sv, p._fire_card_pl = mf, sv, pl
        return c
    except Exception:
        return None


def _thumb_reset(p):
    p._fire_thumb_ok = ""


def _set_thumb(app, p, uid, b, force=False):
    return True          # thumbnail feature hata diya


def _media_sync(p):
    """Save / Play buttons ki state: ready=Save+Play, saving=sab disabled, saved=Saved + Play."""
    try:
        st = getattr(p, "_fire_state", "")
        sv, pl = p._fire_card_sv, p._fire_card_pl
        pbusy = bool(getattr(p, "_fire_play_busy", False))
        if st == "busy":
            sv.configure(text="Downloading\u2026", state="disabled", fg_color="#10B981", text_color_disabled="#E7FFF6")
        elif st == "ok":
            sv.configure(text="\u2714  Downloaded", state="disabled", fg_color="#0B7A57", text_color_disabled="#E7FFF6")
        else:
            sv.configure(text="\u2193  Download", state="normal", fg_color="#10B981")
        pl.configure(text="Loading\u2026" if pbusy else "\u25b6  Play",
                     state="disabled" if (pbusy or st == "busy") else "normal")
    except Exception:
        pass


def _btn_sync(p, n=None):
    """Fire ka Generate button: generating me animated + disabled, baaki time normal."""
    try:
        fb = getattr(p, "_fire_gen", None)
        if fb is None or not fb.winfo_exists():
            return
        if getattr(p, "_fire_state", "") == "gen":
            txt = "\u23f3 Generating" + "." * (3 if n is None else n)
            sig = ("gen", txt)
            if getattr(p, "_fire_btn_sig", None) != sig:
                fb.configure(text=txt, state="disabled", fg_color="#9A3F0C", text_color_disabled="#FFE4D0")
                p._fire_btn_sig = sig
        else:
            sig = ("idle", "")
            if getattr(p, "_fire_btn_sig", None) != sig:
                fb.configure(text="\U0001F525 Generate", state="normal", fg_color=ACCENT)
                p._fire_btn_sig = sig
    except Exception:
        pass


def _card_tick(app, p):
    """Generating animation: spinner + dots + elapsed timer (+ Generate button + status line)."""
    try:
        if getattr(p, "_fire_state", "") != "gen" or not p._fire_card.winfo_exists():
            p._fire_tick_on = False
            _btn_sync(p)
            return
        now = time.time()
        n = int(now * 2.5) % 4
        el = now - float(getattr(p, "_fire_t0", now) or now)
        p._fire_card_ic.configure(text=SPIN[int(now * 4) % 4])
        p._fire_card_ti.configure(text="Generating your video" + "." * n + " " * (3 - n))
        p._fire_card_su.configure(text="Elapsed " + _fmt_el(el) + "   \u00b7   You can keep working in other tabs.")
        _btn_sync(p, n)
        k = int(getattr(p, "_fire_tk", 0) or 0)
        p._fire_tk = k + 1
        if k % 10 == 0:   # status line ('Standby' wagairah) ko dobara 'Generating' pe set rakho
            try:
                p.set_result("\U0001F525 Generating your video\u2026", ACCENT)
            except Exception:
                pass
        p._fire_card.after(400, lambda: _card_tick(app, p))
    except Exception:
        p._fire_tick_on = False


def _card_state(app, p, state, title="", sub="", note="", uid=""):
    """state: gen | ready | busy | ok | err | hold | retry | cancelled | failed
    note = admin ka custom message (status ke neeche dikhta hai). Thread-safe (Tk main thread)."""
    def apply():
        try:
            c = _card(app, p)
            if c is None:
                return
            prev = getattr(p, "_fire_state", "")
            p._fire_state = state
            p._fire_err_uid = uid or ""
            if not c.winfo_ismapped():
                try:
                    anc = getattr(p, "_fire_gen", None)
                    if anc is not None and anc.winfo_exists() and anc.winfo_ismapped() and anc.master is c.master:
                        c.pack(fill="x", padx=16, pady=(0, 8), after=anc)
                    else:
                        c.pack(fill="x", padx=16, pady=(0, 8))
                except Exception:
                    c.pack(fill="x", padx=16, pady=(0, 8))
            col, ico = STATE_UI.get(state, (ACCENT, "\u2726"))
            p._fire_card_ic.configure(text=ico, text_color=col)
            p._fire_card_ti.configure(text=title)
            if not (state == "gen" and prev == "gen" and not sub):
                p._fire_card_su.configure(text=sub)
            if state == "ok" and not sub:
                p._fire_card_su.pack_forget()          # 'Saved as ...' line nahi dikhani
            elif not p._fire_card_su.winfo_ismapped():
                p._fire_card_su.pack(fill="x", padx=(28, 0), pady=(3, 0), after=p._fire_card_hd)
            pb = p._fire_card_pb
            if note:
                p._fire_card_nl.configure(text="\U0001F4AC  " + str(note))
                if not p._fire_card_nf.winfo_ismapped():
                    p._fire_card_nf.pack(fill="x", padx=(44, 16), pady=(0, 10), before=pb)
            else:
                p._fire_card_nf.pack_forget()
            if state in _BAD:
                p._fire_card_bt.configure(text="Try again" if state in ("retry", "err", "failed") else "Dismiss")
                if not p._fire_card_bt.winfo_ismapped():
                    p._fire_card_bt.pack(anchor="e", padx=16, pady=(0, 10), before=pb)
            else:
                p._fire_card_bt.pack_forget()
            if state in ("gen", "busy"):
                pb.configure(mode="indeterminate", progress_color=col)
                pb.start()
            else:
                pb.stop()
                pb.configure(mode="determinate", progress_color=col)
                pb.set(1.0 if state in ("ready", "ok") else (0.35 if state == "hold" else 0.0))
            if state in _MEDIA:
                if not p._fire_card_mf.winfo_ismapped():
                    p._fire_card_mf.grid(row=0, column=1, sticky="e", padx=(24, 0))
                _media_sync(p)
            else:
                try:
                    p._fire_card_mf.grid_remove()
                except Exception:
                    pass
            if state == "gen" and not getattr(p, "_fire_tick_on", False):
                if not getattr(p, "_fire_t0", None):
                    p._fire_t0 = time.time()
                p._fire_tick_on = True
                _card_tick(app, p)
            _btn_sync(p)
        except Exception:
            pass
    try:
        app.after(0, apply)
    except Exception:
        apply()


def _dismiss(app, p):
    """Cancel/Retry/Failed card band karo, order Drive se saaf karo, Generate dobara free."""
    uid = str(getattr(p, "_fire_err_uid", "") or "")
    with ST["lock"]:
        meta = dict(ST["orders"].pop(uid, None) or {}) if uid else {}
    p._fire_state = ""
    p._fire_t0 = None
    p._fire_err_uid = ""
    try:
        p._fire_card.pack_forget()
    except Exception:
        pass
    _btn_sync(p)
    _rm(_cache_path(uid)) if uid else None
    if meta:
        def work():
            fid, sa = _cfg(app)
            try:
                _purge_uid(_dlist(fid, sa), uid, sa, meta)
            except Exception:
                pass
        threading.Thread(target=work, daemon=True).start()


# ---------- order ----------
def _click(app, p):
    if time.time() - float(getattr(p, "_fire_click_t", 0) or 0) < 1.5:
        return   # double-click guard
    p._fire_click_t = time.time()
    _log(app, "[GEN| TAB" + str(p.index) + "] request started.")
    prompt, images, dur, ratio = _gather(app, p)
    _dlog(app, "[FIRE| TAB" + str(p.index) + "] prompt " + str(len(prompt)) +
          " chars, " + str(len(images)) + " images, " + str(dur) + "s " + ratio)
    if not prompt:
        _card_state(app, p, "err", "Prompt required", "Please enter a prompt before generating.")
        try:
            app.show_transient_popup("\u26a0\ufe0f Please enter a prompt first.", ms=3000)
        except Exception:
            pass
        return
    # turant 'Generating' (animation + button) — Drive upload background me chalta hai
    p._fire_t0 = time.time()
    p._fire_tk = 0
    _card_state(app, p, "gen", "Generating your video", "Starting\u2026")
    _send(app, p, prompt, images, dur, ratio)


def _send(app, p, prompt, images, dur, ratio):
    idx = p.index
    tabuid = _tabuid(app, p)
    ms = int(time.time() * 1000)
    seq = int(getattr(p, "_fire_seq", 0) or 0) + 1
    p._fire_seq = seq
    uid = tabuid + "-" + str(ms)[-6:] + str(ms % 97).zfill(2) + "-" + str(seq).zfill(2)

    def fail(code, detail):
        _dlog(app, "[FIRE| TAB" + str(idx) + "] " + detail)
        _log(app, "[GEN| TAB" + str(idx) + "] request failed (" + code + ").")
        _card_state(app, p, "err", "Couldn't start generation", "Please try again in a moment.")

    def work():
        fid, sa = _cfg(app)
        if not fid or not sa.get("client_email"):
            fail("E1", "config missing - fire_config.json (software folder) me drive_url + sa_json daalo.")
            return
        _dlog(app, "[FIRE| TAB" + str(idx) + "] Drive upload shuru - folder " + fid[:12] + "...")
        img_ids = []
        for i, path in enumerate(images[:10]):
            b = _img_bytes(path)
            if b:
                iid = _dupload(fid, sa, "IMG_" + uid + "_" + str(i) + ".jpg", "image/jpeg", b)
                if iid:
                    img_ids.append(iid)
        meta = {"uid": uid, "tabuid": tabuid, "user": _user(app), "device": _device(app),
                "tab": idx, "prompt": prompt, "dur": dur, "ratio": ratio, "imgs": img_ids,
                "status": "pending", "ts": int(time.time()), "file": ""}
        oid = _dupload(fid, sa, "ORDER_" + uid + ".json", "application/json", json.dumps(meta).encode())
        if not oid:
            fail("E2", "Drive upload FAIL - " + str(ST.get("last_err") or "reason unknown"))
            return
        meta["order_file"] = oid
        with ST["lock"]:
            ST["orders"][uid] = meta
        _dlog(app, "[FIRE| TAB" + str(idx) + "] Order placed - UID " + uid + " (" + str(len(img_ids)) + " images).")
        _log(app, "[GEN| TAB" + str(idx) + "] request queued.")
        # user ko 'order sent' message nahi - card 'Generating' animation pe hi rehta hai
    threading.Thread(target=work, daemon=True).start()


def _popup(app, msg):
    try:
        app.show_transient_popup(msg, ms=3000)
    except Exception:
        pass


def _mark_gone(app, p, uid, meta, sa, files=None):
    """Order Drive se hat chuka (admin ne remove kiya) -> user ko sirf 'Generation failed, retry'.
    Bachi hui files (video/images) bhi saaf. Background thread se call karo."""
    with ST["lock"]:
        cur = ST["orders"].get(uid)
        if cur is not None:
            cur["dead"] = True
            cur["status"] = "gone"
    if p is not None:
        _card_state(app, p, "failed", "Generation failed",
                    "Something went wrong while generating. Please retry.", uid=uid)
        _dlog(app, "[FIRE| TAB" + str(p.index) + "] order removed -> failed card (UID " + uid + ").")
    try:
        if files is None:
            fid, sa2 = _cfg(app)
            files = _dlist(fid, sa2)
        _purge_uid(files, uid, sa, meta)
    except Exception:
        pass
    _rm(_cache_path(uid))


def _ready(app, p, meta):
    uid = str(meta["uid"])
    p._fire_cur = uid
    p._fire_saved = ""
    p._fire_play_busy = False
    _thumb_reset(p)
    _card_state(app, p, "ready", "Your video is ready", "Press Download to save it to your computer.")
    _dlog(app, "[FIRE| TAB" + str(p.index) + "] Video Drive me mila (UID " + uid + ").")
    _log(app, "[GEN| TAB" + str(p.index) + "] result ready.")
    fid, sa = _cfg(app)
    vf = str(meta.get("file", "") or "")


def _save(app, p, uid):
    uid = str(uid or getattr(p, "_fire_cur", "") or "")
    with ST["lock"]:
        meta = dict(ST["orders"].get(uid) or {})
    if not meta or getattr(p, "_fire_state", "") not in ("ready", "dlfail"):
        return
    _card_state(app, p, "busy", "Downloading your video", "Saving to your computer\u2026")

    def finish_ui(dest):
        p._fire_saved = dest
        _card_state(app, p, "ok", "Video downloaded", "")
        try:
            p.set_result("\u2705 Video downloaded: " + dest, "#10B981")
        except Exception:
            pass
        _log(app, "[GEN| TAB" + str(p.index) + "] Video saved: " + dest)

    def cleanup(dest, fid, sa, cache):
        # Drive saaf + thumbnail: sab background me, UI ko kabhi nahi rokta
        try:
            left = _purge_uid(_dlist(fid, sa), uid, sa, meta)
            if left:
                _dlog(app, "[FIRE| TAB" + str(p.index) + "] auto-delete incomplete (" + str(left) + " file) - Drive permission check karo.")
        except Exception as e:
            _dlog(app, "[FIRE| TAB" + str(p.index) + "] purge error: " + str(e)[:150])
        with ST["lock"]:
            ST["orders"].pop(uid, None)
        _rm(cache)

    def work():
        dest, cache, fid, sa, ok = "", "", "", None, False
        try:
            fid, sa = _cfg(app)
            vdir = _save_dir()               # <Downloads>\VidoPro Videos
            dest = os.path.join(vdir, uid + ".mp4")
            vf = str(meta.get("file", "") or "")
            cache = _cache_path(uid)
            if os.path.isfile(cache) and os.path.getsize(cache) > 1024:   # Play ke time already download
                try:
                    shutil.copyfile(cache, dest)
                    ok = os.path.getsize(dest) > 1024
                except Exception:
                    ok = False
            if not ok:
                ok = _ddownload(vf, sa, dest)
            if not ok:
                if _dstate(vf, sa) == "gone":
                    _mark_gone(app, p, uid, meta, sa)
                else:
                    _card_state(app, p, "dlfail", "Download failed", "Please press Download again.")
                return
        except Exception as e:
            _dlog(app, "[FIRE| TAB" + str(p.index) + "] save error: " + str(e)[:200])
            _card_state(app, p, "dlfail", "Download failed", "Please press Download again.")
            return
        # video disk par aa chuka -> UI turant 'Saved'
        try:
            app.after(0, lambda: finish_ui(dest))
        except Exception:
            finish_ui(dest)
        threading.Thread(target=cleanup, args=(dest, fid, sa, cache), daemon=True).start()
    threading.Thread(target=work, daemon=True).start()


def _play(app, p):
    if getattr(p, "_fire_play_busy", False):
        return
    uid = str(getattr(p, "_fire_cur", "") or "")
    saved = str(getattr(p, "_fire_saved", "") or "")
    if getattr(p, "_fire_state", "") == "ok" and saved and os.path.isfile(saved):
        if not _open_file(saved):
            _popup(app, "\u26a0\ufe0f Couldn't open the video player.")
        return
    cache = _cache_path(uid)
    if os.path.isfile(cache) and os.path.getsize(cache) > 1024:
        if not _open_file(cache):
            _popup(app, "\u26a0\ufe0f Couldn't open the video player.")
        return
    with ST["lock"]:
        meta = dict(ST["orders"].get(uid) or {})
    if not meta:
        return
    p._fire_play_busy = True
    _media_sync(p)

    def work():
        fid, sa = _cfg(app)
        ok = _ddownload(str(meta.get("file", "") or ""), sa, cache)

        def done():
            p._fire_play_busy = False
            _media_sync(p)
            if ok:
                if not _open_file(cache):
                    _popup(app, "\u26a0\ufe0f Couldn't open the video player.")
            else:
                _popup(app, "\u26a0\ufe0f Couldn't load the video. Please try again.")
        app.after(0, done)
    threading.Thread(target=work, daemon=True).start()


_KNOWN = ("pending", "processing", "hold", "retry", "cancelled", "failed")


def _norm_status(s):
    s = str(s or "").strip().lower()
    return {"canceled": "cancelled", "on hold": "hold", "on_hold": "hold", "in progress": "processing",
            "generating": "processing", "queued": "pending", "waiting": "pending", "new": "pending",
            "sent": "pending", "done": "ready", "completed": "ready"}.get(s, s)


def _uid_hit(name, uid):
    """'<uid>.mp4' / '<uid>_x.mp4' match; '<uid>0.mp4' (dusra order) nahi."""
    n, u = str(name or "").lower(), str(uid or "").lower()
    if not u or not n.startswith(u) or n.startswith(("order_", "img_")):
        return False
    nx = n[len(u):len(u) + 1]
    return (not nx) or (not nx.isdigit())


def _dget_json(file_id, sa):
    """Order JSON padho -> (dict|None, http_code). 404 = admin ne order delete kar diya."""
    tok = _sa_token(sa)
    if not tok:
        return None, 0
    try:
        r = _dreq("https://www.googleapis.com/drive/v3/files/" + str(file_id) + "?alt=media&supportsAllDrives=true", tok)
        return json.loads(r.read().decode("utf-8", "ignore") or "{}"), 200
    except urllib.error.HTTPError as e:
        return None, int(e.code)
    except Exception:
        return None, -1


def _apply_admin(app, p, m):
    """Admin ka status + custom message user ke card me."""
    st, uid = str(m.get("lstate", "pending")), str(m.get("uid", ""))
    msg = _user_msg(m.get("admin_msg", ""))
    if st in ("pending", "processing"):
        _card_state(app, p, "gen", "Generating your video", "", note=msg, uid=uid)
    elif st == "hold":
        _card_state(app, p, "hold", "Your order is on hold", "We'll update you shortly.", note=msg, uid=uid)
    elif st == "retry":
        _card_state(app, p, "retry", "Please try again",
                    "Your generation didn't complete. Press Try again, then Generate once more.", note=msg, uid=uid)
    elif st == "cancelled":
        _card_state(app, p, "cancelled", "Order cancelled", "This generation was cancelled.", note=msg, uid=uid)
    elif st == "failed":
        _card_state(app, p, "failed", "Generation failed",
                    "Something went wrong while generating. Please retry.", note=msg, uid=uid)
    _dlog(app, "[FIRE| TAB" + str(p.index) + "] admin status -> " + st + (" | " + msg[:60] if msg else ""))


def _poll(app):
    now = time.time()
    if now - float(ST["poll_t"]) < 8 or ST.get("polling"):
        return
    ST["poll_t"] = now
    with ST["lock"]:
        pend = [dict(m) for m in ST["orders"].values() if not m.get("dead")]
    if not pend:
        return
    fid, sa = _cfg(app)
    if not fid or not sa.get("client_email"):
        return
    ST["polling"] = True

    def panel_of(m):
        try:
            return app.get_panel(int(m.get("tab", 0)))
        except Exception:
            return None

    def work():
        try:
            files = _dlist(fid, sa)
            listed = set(str(f.get("id", "")) for f in files)
            for meta in pend:
                uid = str(meta.get("uid", ""))
                oid = str(meta.get("order_file", "") or "")
                was_ready = str(meta.get("status", "")) == "ready"
                # --- order Drive se hata diya gaya? (list se gayab -> metadata se confirm; trash bhi 'gone')
                gone = False
                for x in ([oid, str(meta.get("file", "") or "")] if was_ready else [oid]):
                    if x and x not in listed and _dstate(x, sa) == "gone":
                        gone = True
                        break
                adm, code = ({}, 0)
                if not gone and oid:
                    adm, code = _dget_json(oid, sa)
                    adm = adm or {}
                    if code == 404 or adm.get("removed"):   # removed=True: admin console ne delete ki jagah mark kiya
                        gone = True
                if gone:
                    with ST["lock"]:
                        cur = ST["orders"].get(uid)
                        if not cur or cur.get("dead"):
                            continue
                        m2 = dict(cur)
                    _mark_gone(app, panel_of(m2), uid, m2, sa, files)
                    continue
                if was_ready:
                    continue                  # ready + abhi tak Drive me hai -> kuch nahi karna
                hit = ""
                for f in files:
                    if _uid_hit(f.get("name", ""), uid):
                        hit = str(f.get("id", ""))
                        break
                msg, ts = str(adm.get("admin_msg", "") or ""), adm.get("admin_ts", 0)
                st = _norm_status(adm.get("status"))
                if hit and st in ("", "pending", "processing", "ready"):
                    lstate = "ready"
                else:
                    lstate = st if st in _KNOWN else "pending"
                if code != 200 and not hit:
                    continue                  # network glitch - purani state rehne do
                sig = lstate + "|" + msg + "|" + str(ts)
                with ST["lock"]:
                    cur = ST["orders"].get(uid)
                    if not cur:
                        continue          # user ne dismiss kar diya
                    if lstate != "ready" and cur.get("sig") == sig:
                        continue
                    cur["sig"], cur["lstate"], cur["admin_msg"] = sig, lstate, msg
                    if lstate == "ready":
                        cur["status"], cur["file"] = "ready", hit
                    m2 = dict(cur)
                p = panel_of(m2)
                if p is None:
                    continue
                if lstate == "ready":
                    app.after(0, lambda q=p, m=m2: _ready(app, q, m))
                else:
                    app.after(0, lambda q=p, m=m2: _apply_admin(app, q, m))
        finally:
            ST["polling"] = False
    threading.Thread(target=work, daemon=True).start()


def _load(app):
    if ST["loaded"]:
        return
    ST["loaded"] = True
    try:
        _cd = os.path.join(BASE, "fire_cache")
        for _n in (os.listdir(_cd) if os.path.isdir(_cd) else []):
            _fp = os.path.join(_cd, _n)
            if time.time() - os.path.getmtime(_fp) > 3 * 86400:
                _rm(_fp)
    except Exception:
        pass
    fid, sa = _cfg(app)
    if not fid or not sa.get("client_email"):
        return
    dev = _device(app)

    def work():
        for f in _dlist(fid, sa):
            if not str(f.get("name", "")).startswith("ORDER_"):
                continue
            meta, code = _dget_json(str(f.get("id")), sa)
            if not meta or str(meta.get("device", "")) != dev:
                continue
            if meta.get("removed"):          # admin ne remove kiya tha -> saaf karo, resume nahi
                try:
                    _purge_uid(_dlist(fid, sa), str(meta.get("uid", "")), sa, dict(meta, order_file=str(f.get("id"))))
                except Exception:
                    pass
                continue
            meta["order_file"] = str(f.get("id"))
            meta["status"] = "pending"      # local: 'ready' sirf video milne par
            with ST["lock"]:
                ST["orders"][str(meta.get("uid", ""))] = meta
            try:
                pn = app.get_panel(int(meta.get("tab", 0)))
                if pn is not None:
                    pn._fire_t0 = float(meta.get("ts") or time.time())
                    _card_state(app, pn, "gen", "Generating your video", "Resuming\u2026")
            except Exception:
                pass
        ST["poll_t"] = 0.0                  # turant poll -> asli admin status card me aa jaye
    threading.Thread(target=work, daemon=True).start()


# ---------- GUI: mode button + OWN generate button ----------
def _style(p, on):
    try:
        b = getattr(p, "_fire_btn", None)
        if b is not None:
            b.configure(fg_color=ACCENT if on else "transparent", text_color="#FFFFFF" if on else IDLE_TX)
    except Exception:
        pass


def _fire_on(app, p):
    p._vp_fire = True
    for fl in ("_vp_pro_new", "_vp_legit2", "_vp_pro_new2", "_vp_pro2"):
        try:
            setattr(p, fl, False)
        except Exception:
            pass
    _style(p, True)
    try:
        if getattr(p, "r2v_btn", None) is not None:
            p.r2v_btn.configure(fg_color="transparent", text_color=IDLE_TX)
    except Exception:
        pass
    _log(app, "🔥 [TAB" + str(p.index) + "] Fire Mode select — apna Generate button (koi activation NAHI).")


def _fire_off(p):
    p._vp_fire = False
    _style(p, False)
    _legacy_restore(p)


def _select(app, p):
    try:
        p.switch_mode("r2v")
    except Exception:
        pass
    _fire_on(app, p)


def _ensure_mode_btn(app, p):
    b = getattr(p, "_fire_btn", None)
    try:
        if b is not None and not b.winfo_exists():
            b = None
            p._fire_btn = None
    except Exception:
        b = None
    if b is None:
        mr = getattr(p, "mode_row", None)
        if mr is None:
            return
        import customtkinter as ctk
        b = ctk.CTkButton(mr, text="Seedance 2.5", width=95, height=28,
                          font=ctk.CTkFont(size=11, weight="bold"),
                          fg_color="transparent", hover_color="#1F2430", text_color=IDLE_TX,
                          corner_radius=6, command=lambda q=p: _select(app, q), cursor="hand2")
        p._fire_btn = b
        try:
            a = getattr(p, "r2v_btn", None)
            if a is not None and a.winfo_exists():
                b.pack(side="left", padx=2, pady=4, after=a)
            else:
                b.pack(side="left", padx=2, pady=4)
        except Exception:
            try:
                b.pack(side="left", padx=2, pady=4)
            except Exception:
                pass
    _style(p, bool(getattr(p, "_vp_fire", False)))


def _ensure_gen_btn(app, p):
    """Fire ka APNA generate button — purana gen_btn hide (koi layer isko touch nahi karta)."""
    on = bool(getattr(p, "_vp_fire", False))
    gb = getattr(p, "gen_btn", None)
    fb = getattr(p, "_fire_gen", None)
    try:
        if fb is not None and not fb.winfo_exists():
            fb = None
            p._fire_gen = None
    except Exception:
        fb = None
    if on:
        # purana gen_btn hide (pack/grid/place teeno try)
        if gb is not None:
            try:
                if gb.winfo_exists() and gb.winfo_ismapped():
                    try:
                        gb.pack_forget()
                    except Exception:
                        pass
                    try:
                        gb.grid_forget()
                    except Exception:
                        pass
                    try:
                        gb.place_forget()
                    except Exception:
                        pass
            except Exception:
                pass
        if fb is None:
            import customtkinter as ctk
            parent = gb.master if gb is not None else p
            fb = ctk.CTkButton(parent, text="🔥 Generate", height=44, corner_radius=10,
                               fg_color=ACCENT, hover_color="#EA580C", text_color="#FFFFFF",
                               font=ctk.CTkFont(size=14, weight="bold"),
                               command=lambda q=p: _click(app, q), cursor="hand2")
            p._fire_gen = fb
            try:
                fb.pack(fill="x", padx=16, pady=(4, 8))
            except Exception:
                try:
                    fb.place(relx=0.5, rely=0.5, anchor="center", relwidth=0.9)
                except Exception:
                    pass
        else:
            try:
                if not fb.winfo_ismapped():
                    fb.pack(fill="x", padx=16, pady=(4, 8))
            except Exception:
                pass
        # overlay + deactive badge clean
        try:
            ov = getattr(p, "_vp_m2_ov", None)
            if ov is not None and ov.winfo_exists() and ov.winfo_ismapped():
                try:
                    ov.place_forget()
                    ov.pack_forget()
                except Exception:
                    pass
        except Exception:
            pass
        _btn_sync(p)
    else:
        # fire off -> apna button hatao, purana gen_btn wapas
        if fb is not None:
            try:
                fb.pack_forget()
                fb.place_forget()
            except Exception:
                pass
        if gb is not None:
            try:
                if gb.winfo_exists() and not gb.winfo_ismapped():
                    gb.pack(fill="x", padx=16, pady=(4, 8))
            except Exception:
                pass


def _wrap_switch(app, p):
    if getattr(p, "_fire_sw", False):
        return
    orig = getattr(p, "switch_mode", None)
    if not callable(orig):
        return
    p._fire_sw = True
    def sm(mode, *a, **k):
        try:
            if str(mode) in ("t2v", "i2v", "legit"):
                _fire_off(p)
        except Exception:
            pass
        return orig(mode, *a, **k)
    p.switch_mode = sm


def _wrap_pro_btn(app, p):
    if getattr(p, "_fire_pro_w", False):
        return
    b = getattr(p, "r2v_btn", None)
    if b is None:
        return
    try:
        orig = b.cget("command")
    except Exception:
        orig = None
    if not callable(orig):
        return
    p._fire_pro_w = True
    def cmd(*a, **k):
        try:
            _fire_off(p)
        except Exception:
            pass
        return orig(*a, **k)
    b.configure(command=cmd)


# ---------- legacy 'Status / Standby / Save / Play' strip (Fire Mode me hide) ----------
_LEG_STATUS = re.compile(r"status\s*:", re.I)
_LEG_BTN = re.compile(r"^\W*(save|play)\W*$", re.I)
_LEG_LBL = re.compile(r"^\W*(ready|standby|idle|stand by)\W*$", re.I)
AUTO_FIRE_NEW_TABS = True      # naya tab apne aap Fire Mode me khule (False = purana behaviour)
_FORBID = ("reference", "generate", "mode", "clear all")


def _walk(w, skip, depth=0):
    if depth > 10:
        return
    try:
        kids = list(w.winfo_children())
    except Exception:
        return
    for c in kids:
        if c in skip:
            continue
        yield c
        for x in _walk(c, skip, depth + 1):
            yield x


def _wtxt(w):
    try:
        return str(w.cget("text") or "")
    except Exception:
        return ""


def _has_forbidden(w, skip):
    for c in _walk(w, skip):
        n = c.__class__.__name__
        if n in ("CTkTextbox", "CTkEntry", "Text", "Entry"):
            return True
        if n == "CTkButton":
            t = _wtxt(c).lower()
            if any(k in t for k in _FORBID):
                return True
    return False


def _is_inside(w, anc):
    x = w
    for _ in range(20):
        if x is anc:
            return True
        x = getattr(x, "master", None)
        if x is None:
            return False
    return False


def _legacy_targets(p):
    skip = set()
    card = getattr(p, "_fire_card", None)
    if card is not None:
        skip.add(card)
    bad = set()
    for nm in ("_fire_card", "_fire_gen", "_fire_btn", "mode_row", "gen_btn"):
        x = getattr(p, nm, None)
        for _ in range(16):
            if x is None:
                break
            bad.add(x)
            x = getattr(x, "master", None)

    def top(w):
        a = w
        while True:
            par = getattr(a, "master", None)
            if par is None or par is p or par in bad or _has_forbidden(par, skip):
                return a
            a = par

    status_w, saves, plays, bars, lbls = None, [], [], [], []
    for c in _walk(p, skip):
        n = c.__class__.__name__
        if n == "CTkLabel" and status_w is None and _LEG_STATUS.search(_wtxt(c)):
            status_w = c
        elif n == "CTkLabel" and _LEG_LBL.match(_wtxt(c)):
            lbls.append(c)
        elif n == "CTkButton":
            m = _LEG_BTN.match(_wtxt(c))
            if m:
                (saves if m.group(1).lower() == "save" else plays).append(c)
        elif n == "CTkProgressBar":
            bars.append(c)
    tg = []
    if status_w is not None:
        tg.append(top(status_w))
    for s in saves:
        row = next((q for q in plays if q.master is s.master), None)
        if row is not None:
            tg.append(top(s))
            break
    for lb in lbls:
        tg.append(lb)
    parents = set(getattr(t, "master", None) for t in tg)
    for b in bars:
        if b is getattr(p, "_fire_card_pb", None):
            continue
        if any(_is_inside(b, t) for t in tg):
            continue
        if getattr(b, "master", None) in parents:
            tg.append(b)
    out = []
    for t in tg:
        if t not in out:
            out.append(t)
    return out


def _hide_w(p, w):
    try:
        if not w.winfo_exists():
            return
        mgr = w.winfo_manager()
        if not mgr:
            return
        hid = p.__dict__.setdefault("_fire_hid", {})
        rec = hid.get(id(w))
        if rec is None:
            rec = {"w": w, "mgr": mgr}
            if mgr == "pack":
                rec["info"] = w.pack_info()
                sibs = list(w.master.pack_slaves())
                try:
                    rec["next"] = sibs[sibs.index(w) + 1] if sibs.index(w) + 1 < len(sibs) else None
                except ValueError:
                    rec["next"] = None
            elif mgr == "grid":
                rec["info"] = w.grid_info()
            elif mgr == "place":
                rec["info"] = w.place_info()
            else:
                return
            hid[id(w)] = rec
        if mgr == "pack":
            w.pack_forget()
        elif mgr == "grid":
            w.grid_remove()
        elif mgr == "place":
            w.place_forget()
    except Exception:
        pass


def _legacy_restore(p):
    hid = p.__dict__.get("_fire_hid") or {}
    if not hid:
        return
    import tkinter as _tk
    for rec in list(hid.values()):
        w, mgr = rec["w"], rec["mgr"]
        try:
            if not w.winfo_exists():
                continue
            info = dict(rec.get("info") or {})
            info.pop("in", None)
            if mgr == "pack" and not w.winfo_manager():
                nxt = rec.get("next")
                try:
                    if nxt is not None and nxt.winfo_exists() and nxt.winfo_manager() == "pack":
                        info["before"] = nxt
                except Exception:
                    pass
                _tk.Pack.pack_configure(w, **info)
            elif mgr == "grid" and not w.winfo_manager():
                _tk.Grid.grid_configure(w, **info)
            elif mgr == "place" and not w.winfo_manager():
                _tk.Place.place_configure(w, **info)
        except Exception:
            pass
    p._fire_hid = {}
    p._fire_leg = []


def _ensure_legacy(app, p):
    if not getattr(p, "_vp_fire", False):
        _legacy_restore(p)
        return
    now = time.time()
    if not getattr(p, "_fire_leg", None) or now - float(getattr(p, "_fire_leg_t", 0) or 0) > 5:
        p._fire_leg_t = now
        found = _legacy_targets(p)
        if found:
            old = [w for w in (getattr(p, "_fire_leg", None) or []) if w not in found]
            p._fire_leg = found + old
    for w in list(getattr(p, "_fire_leg", []) or []):
        _hide_w(p, w)


# ================= ui-rename-v2 (DO NOT REMOVE) =================
# Sirf UI display names (logic untouched):
#   Pro Mode -> Veo 3.1 | Legit Mode 2 -> Kling 3.0 | Pro Mode New -> Grok 1.5
UI_RENAME = {
    "pro mode": "Veo 3.1",
    "legit mode 2": "Kling 3.0",
    "pro mode new": "Grok 1.5",
    "fire mode": "Seedance 2.5",
}


def _rename_walk(w, depth):
    if depth > 4:
        return
    try:
        kids = list(w.winfo_children())
    except Exception:
        kids = []
    for c in kids:
        try:
            if c.__class__.__name__ == "CTkButton":
                t = str(c.cget("text") or "").strip()
                n = UI_RENAME.get(t.lower())
                if n and t != n:
                    c.configure(text=n)
        except Exception:
            pass
        _rename_walk(c, depth + 1)


def _rename_ui_btns(p):
    try:
        mr = getattr(p, "mode_row", None)
        if mr is not None:
            _rename_walk(mr, 0)
        else:
            _rename_walk(p, 0)
    except Exception:
        pass
# ================= ui-rename-v2 END =================


def _tick(app):
    try:
        _load(app)
        for p in list(getattr(app, "panels", []) or []):
            try:
                _wrap_switch(app, p)
                _wrap_pro_btn(app, p)
                if (getattr(p, "_vp_pro_new", False) or getattr(p, "_vp_legit2", False)
                        or getattr(p, "_vp_pro_new2", False) or getattr(p, "_vp_pro2", False)):
                    _fire_off(p)
                _ensure_mode_btn(app, p)
                _rename_ui_btns(p)
                if (AUTO_FIRE_NEW_TABS and not p.__dict__.get("_fire_auto")
                        and getattr(p, "mode_row", None) is not None
                        and getattr(p, "_fire_btn", None) is not None):
                    p._fire_auto = True
                    _select(app, p)
                _ensure_gen_btn(app, p)
                _ensure_legacy(app, p)
            except Exception:
                pass
        _poll(app)
    except Exception:
        pass
    try:
        app.after(900, lambda: _tick(app))
    except Exception:
        pass


def install(app):
    if ST["inst"]:
        return True
    ST["inst"] = True
    if not ST["tick"]:
        ST["tick"] = True
        try:
            app.after(900, lambda: _tick(app))
        except Exception:
            pass
    _log(app, "🔥 [" + VER + "] Fire Mode ready — apna Generate button, koi activation/redirect NAHI.")
    return True


# ==================== fire-key-fix-v12 (DO NOT REMOVE) ====================
# private_key ke corrupt newlines / PKCS#1-PKCS#8 dono handle — key parse crash fix.
import re as _k12_re
import base64 as _k12_b64
import hashlib as _k12_sha
import json as _k12_json
import time as _k12_time
import urllib.request as _k12_req
import urllib.parse as _k12_parse
import urllib.error as _k12_err


def _k12_norm(pk):
    s = str(pk or "")
    s = s.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\r", "\n")
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    return s.strip()


def _k12_der(pk):
    s = _k12_norm(pk)
    m = _k12_re.search(r"-----BEGIN [A-Z ]*PRIVATE KEY-----(.*?)-----END [A-Z ]*PRIVATE KEY-----", s, _k12_re.S)
    body = m.group(1) if m else s
    body = _k12_re.sub(r"-----[A-Z ]+-----", "", body)
    body = _k12_re.sub(r"\s+", "", body)
    if not body:
        raise ValueError("private_key khali")
    return _k12_b64.b64decode(body)


def _k12_len(b, i):
    if i >= len(b):
        raise ValueError("DER length out of range")
    l = b[i]
    i += 1
    if l & 0x80:
        n = l & 0x7F
        if n == 0 or i + n > len(b):
            raise ValueError("DER long-length corrupt")
        l = int.from_bytes(b[i:i + n], "big")
        i += n
    return l, i


def _k12_kids(b):
    out, i = [], 0
    while i < len(b):
        tag = b[i]
        l, j = _k12_len(b, i + 1)
        if j + l > len(b):
            raise ValueError("DER child truncated")
        out.append((tag, b[j:j + l]))
        i = j + l
    return out


def _k12_nd(der):
    top = _k12_kids(der)
    if not top or top[0][0] != 0x30:
        raise ValueError("DER top SEQUENCE nahi")
    inner = _k12_kids(top[0][1])
    if len(inner) >= 3 and inner[0][0] == 0x02 and inner[1][0] == 0x30 and inner[2][0] == 0x04:
        rsa = _k12_kids(inner[2][1])
    else:
        rsa = inner
    ints = [int.from_bytes(v, "big") for t, v in rsa if t == 0x02]
    if len(ints) < 4:
        raise ValueError("RSA ints kam (" + str(len(ints)) + ")")
    if len(ints) >= 9:
        return ints[1], ints[3]
    return ints[0], ints[2]


def _k12_b64u(b):
    return _k12_b64.urlsafe_b64encode(b).decode().rstrip("=")


def _sa_token(sa):
    """fire-key-fix-v12: robust key parse + detailed error (purana crash-prone parser replace)."""
    try:
        now = int(_k12_time.time())
        if ST.get("tok") and now < int(ST.get("tok_exp", 0)) - 60:
            return ST["tok"]
        n, d = _k12_nd(_k12_der(sa.get("private_key", "")))
        k = (n.bit_length() + 7) // 8
        h = _k12_b64u(_k12_json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
        c = _k12_b64u(_k12_json.dumps({"iss": sa.get("client_email", ""),
            "scope": "https://www.googleapis.com/auth/drive",
            "aud": "https://oauth2.googleapis.com/token", "iat": now, "exp": now + 3600}).encode())
        si = h + "." + c
        dg = _k12_sha.sha256(si.encode()).digest()
        t = bytes.fromhex("3031300d060960864801650304020105000420") + dg
        em = b"\x00\x01" + b"\xff" * (k - len(t) - 3) + b"\x00" + t
        sig = pow(int.from_bytes(em, "big"), d, n).to_bytes(k, "big")
        jwt = si + "." + _k12_b64u(sig)
        data = _k12_parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                     "assertion": jwt}).encode()
        rq = _k12_req.Request("https://oauth2.googleapis.com/token", data=data,
                              headers={"Content-Type": "application/x-www-form-urlencoded"})
        res = _k12_json.loads(_k12_req.urlopen(rq, timeout=30).read().decode())
        tok = str(res.get("access_token", "") or "")
        if tok:
            ST["tok"], ST["tok_exp"] = tok, now + int(res.get("expires_in", 3600) or 3600)
        else:
            try:
                _last("Drive TOKEN fail: " + _k12_json.dumps(res)[:220])
            except Exception:
                pass
        return tok
    except _k12_err.HTTPError as he:
        try:
            body = he.read().decode("utf-8", "ignore")[:250]
        except Exception:
            body = ""
        try:
            _last("Drive TOKEN HTTP " + str(he.code) + ": " + body)
        except Exception:
            pass
        return ""
    except Exception as e:
        try:
            _last("Drive TOKEN/KEY error: " + str(e)[:220])
        except Exception:
            pass
        return ""
# ==================== fire-key-fix-v12 END ====================


# ==================== fire-crypto-v13 (DO NOT REMOVE) ====================
# Standard 'cryptography' library se RS256 sign (custom DER parser ki zaroorat nahi).
# Purana parser fallback ke roop me safe rakha hai — kuch cut NAHI.
import json as _c13_json
import time as _c13_time
import base64 as _c13_b64
import urllib.request as _c13_req
import urllib.parse as _c13_parse
import urllib.error as _c13_err

_c13_prev_token = _sa_token


def _c13_b64u(b):
    return _c13_b64.urlsafe_b64encode(b).decode().rstrip("=")


def _c13_norm(pk):
    s = str(pk or "")
    s = s.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\r", "\n")
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    return s.strip()


def _sa_token(sa):
    """fire-crypto-v13: cryptography se sign; fail ho to purana parser fallback."""
    try:
        now = int(_c13_time.time())
        if ST.get("tok") and now < int(ST.get("tok_exp", 0)) - 60:
            return ST["tok"]
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
        from cryptography.hazmat.primitives import hashes
        from cryptography.hazmat.primitives.asymmetric import padding
        key = load_pem_private_key(_c13_norm(sa.get("private_key", "")).encode("utf-8"), password=None)
        h = _c13_b64u(_c13_json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
        c = _c13_b64u(_c13_json.dumps({"iss": sa.get("client_email", ""),
            "scope": "https://www.googleapis.com/auth/drive",
            "aud": "https://oauth2.googleapis.com/token", "iat": now, "exp": now + 3600}).encode())
        si = h + "." + c
        sig = key.sign(si.encode(), padding.PKCS1v15(), hashes.SHA256())
        jwt = si + "." + _c13_b64u(sig)
        data = _c13_parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                     "assertion": jwt}).encode()
        rq = _c13_req.Request("https://oauth2.googleapis.com/token", data=data,
                              headers={"Content-Type": "application/x-www-form-urlencoded"})
        res = _c13_json.loads(_c13_req.urlopen(rq, timeout=30).read().decode())
        tok = str(res.get("access_token", "") or "")
        if tok:
            ST["tok"], ST["tok_exp"] = tok, now + int(res.get("expires_in", 3600) or 3600)
            return tok
        try:
            _last("Drive TOKEN fail: " + _c13_json.dumps(res)[:220])
        except Exception:
            pass
        return ""
    except _c13_err.HTTPError as he:
        try:
            body = he.read().decode("utf-8", "ignore")[:250]
        except Exception:
            body = ""
        try:
            _last("Drive TOKEN HTTP " + str(he.code) + ": " + body)
        except Exception:
            pass
        return ""
    except Exception as e:
        try:
            _last("Drive crypto sign note: " + str(e)[:160] + " — purana parser try.")
        except Exception:
            pass
        try:
            return _c13_prev_token(sa)
        except Exception:
            return ""
# ==================== fire-crypto-v13 END ====================

# ==================== fire-oauth-upload-v14 (DO NOT REMOVE) ====================
# Service Account ke paas Drive storage quota nahi hota (403 "Service Accounts do not
# have storage quota"). Isliye UPLOAD admin ke real Google account (OAuth refresh token)
# se hota hai -> file admin ki quota me jaati hai. Read/list/delete pehle jaise SA se.
# fire_config.json ke sa_json me ye 3 keys add karo:
#   "oauth_client_id", "oauth_client_secret", "oauth_refresh_token"
# Keys na ho to purana (SA) upload chalega.
_o14_prev_upload = _dupload
_O14 = {"tok": "", "exp": 0.0}


def _o14_token(sa):
    try:
        cid = str(sa.get("oauth_client_id", "") or "")
        cs = str(sa.get("oauth_client_secret", "") or "")
        rt = str(sa.get("oauth_refresh_token", "") or "")
        if not (cid and cs and rt):
            return ""
        now = int(time.time())
        if _O14["tok"] and now < int(_O14["exp"]) - 60:
            return _O14["tok"]
        data = urllib.parse.urlencode({"client_id": cid, "client_secret": cs,
                                       "refresh_token": rt, "grant_type": "refresh_token"}).encode()
        rq = urllib.request.Request("https://oauth2.googleapis.com/token", data=data,
                                    headers={"Content-Type": "application/x-www-form-urlencoded"})
        res = json.loads(urllib.request.urlopen(rq, timeout=30).read().decode())
        tok = str(res.get("access_token", "") or "")
        if tok:
            _O14["tok"], _O14["exp"] = tok, now + int(res.get("expires_in", 3600) or 3600)
        else:
            _last("OAuth TOKEN fail: " + json.dumps(res)[:220])
        return tok
    except urllib.error.HTTPError as _he:
        try:
            _b = _he.read().decode("utf-8", "ignore")[:250]
        except Exception:
            _b = ""
        _last("OAuth TOKEN HTTP " + str(_he.code) + ": " + _b)
        return ""
    except Exception as _e:
        _last("OAuth TOKEN error: " + str(_e)[:200])
        return ""


def _dupload(fid, sa, name, mime, data):
    tok = _o14_token(sa)
    if not tok:
        _r = _o14_prev_upload(fid, sa, name, mime, data)
        if not _r and not sa.get("oauth_refresh_token"):
            _last("OAUTH KEYS NAHI MILI (fire_config.json software folder me nahi / keys missing) | " + str(ST.get("last_err", ""))[:150])
        return _r
    b = "----fb" + str(int(time.time() * 1000))
    meta = json.dumps({"name": name, "parents": [fid]})
    body = (("--" + b + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta +
             "\r\n--" + b + "\r\nContent-Type: " + mime + "\r\n\r\n").encode() + data +
            ("\r\n--" + b + "--\r\n").encode())
    try:
        res = json.loads(_dreq("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true",
                               tok, "POST", body, "multipart/related; boundary=" + b).read().decode())
        _id = str(res.get("id", "") or "")
        if not _id:
            _last("Drive UPLOAD(oauth): id nahi aaya — " + json.dumps(res)[:220])
        return _id
    except urllib.error.HTTPError as _he:
        try:
            _b = _he.read().decode("utf-8", "ignore")[:250]
        except Exception:
            _b = ""
        _last("Drive UPLOAD(oauth) HTTP " + str(_he.code) + ": " + _b)
        return ""
    except Exception as _e:
        _last("Drive UPLOAD(oauth) error: " + str(_e)[:200])
        return ""
# ==================== fire-oauth-upload-v14 END ====================
