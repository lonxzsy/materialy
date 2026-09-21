"""
Materialy Music PC Server — FastAPI + yt-dlp
Handles YouTube / SoundCloud downloads for Android client.
Mandatory: yt-dlp nightly, ffmpeg optional (fallback to native ext).
"""
import uuid
import pathlib
import asyncio
import traceback
from enum import Enum
from typing import Optional, List, Dict, Any

import yt_dlp
from fastapi import FastAPI, BackgroundTasks, HTTPException, Query
from fastapi.responses import FileResponse, RedirectResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

APP_DIR = pathlib.Path("/tmp/materialy_downloads")
APP_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="Materialy Music Server", version="1.0.0", description="PC backend for downloading YT/SoundCloud via yt-dlp")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

class JobStatus(str, Enum):
    queued = "queued"
    downloading = "downloading"
    completed = "completed"
    failed = "failed"

JOBS: Dict[str, Dict[str, Any]] = {}

class DownloadRequest(BaseModel):
    url: str
    formatId: Optional[str] = None
    audioFormat: str = "opus"  # opus/mp3/m4a/aac
    audioQuality: str = "0"  # 0 best, 320K etc, or bitrate
    embedMetadata: bool = True

class FormatInfo(BaseModel):
    formatId: str
    ext: str
    acodec: Optional[str] = None
    vcodec: Optional[str] = None
    abr: Optional[int] = None
    vbr: Optional[int] = None
    note: str = ""
    filesize: Optional[int] = None
    filesizeFormatted: Optional[str] = None
    qualityLabel: str = ""
    qualityTier: str = "standard"  # ultra, high, standard, low
    isRecommended: bool = False

class SearchResultItem(BaseModel):
    id: str
    title: str
    uploader: str
    duration: int
    durationFormatted: str
    thumbnail: Optional[str] = None
    url: str

class SearchResponse(BaseModel):
    query: str
    results: List[SearchResultItem] = []

class OnlineTrackItem(BaseModel):
    id: str
    title: str
    uploader: str
    duration: int
    durationFormatted: str
    thumbnail: Optional[str] = None
    url: str
    streamUrl: Optional[str] = None

class ResolvedOnlineResponse(BaseModel):
    isPlaylist: bool = False
    playlistTitle: Optional[str] = None
    playlistUploader: Optional[str] = None
    itemCount: int = 0
    items: List[OnlineTrackItem] = []

class InfoResponse(BaseModel):
    id: str
    title: str
    uploader: str
    duration: int
    thumbnail: Optional[str] = None
    extractor: Optional[str] = None
    formats: List[FormatInfo] = []
    description: Optional[str] = None

class JobResponse(BaseModel):
    jobId: str
    status: str
    message: Optional[str] = None

class JobStatusResponse(BaseModel):
    jobId: str
    status: str
    progress: float = 0
    filename: Optional[str] = None
    filesize: Optional[int] = None
    speed: Optional[str] = None
    eta: Optional[str] = None
    error: Optional[str] = None


def _yt_opts(skip_download=True, outtmpl=None, format_spec=None, audio_format=None, client_chain=None):
    # Detect available JS runtime (node, bun, deno)
    import shutil, glob, os
    js_runtimes = {}
    node_bin = shutil.which("node")
    if not node_bin:
        nvm_matches = glob.glob(os.path.expanduser("~/.nvm/versions/node/*/bin/node"))
        if nvm_matches:
            node_bin = sorted(nvm_matches)[-1]

    bun_bin = shutil.which("bun")
    if not bun_bin and os.path.exists(os.path.expanduser("~/.bun/bin/bun")):
        bun_bin = os.path.expanduser("~/.bun/bin/bun")

    deno_bin = shutil.which("deno")
    if not deno_bin and os.path.exists(os.path.expanduser("~/.deno/bin/deno")):
        deno_bin = os.path.expanduser("~/.deno/bin/deno")

    if node_bin:
        js_runtimes["node"] = {"path": node_bin}
    elif bun_bin:
        js_runtimes["bun"] = {"path": bun_bin}
    elif deno_bin:
        js_runtimes["deno"] = {"path": deno_bin}

    use_bgutil = False
    try:
        import importlib.util
        if importlib.util.find_spec("bgutil_ytdlp_pot_provider") is not None:
            use_bgutil = True
    except: pass

    extractor_args = {}
    if client_chain is not None:
        extractor_args["youtube"] = {"player_client": client_chain}

    if use_bgutil:
        extractor_args["youtubepot"] = {"bgutil": ["http://127.0.0.1:4416"]}
        if "youtube" not in extractor_args:
            extractor_args["youtube"] = {}
        extractor_args["youtube"]["po_token"] = ["web+bgutil:http://127.0.0.1:4416", "web_embedded+bgutil:http://127.0.0.1:4416"]

    cookie_path = pathlib.Path(__file__).parent / "cookies.txt"
    opts = {
        "skip_download": skip_download,
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "socket_timeout": 15,
        "retries": 3,
        "fragment_retries": 3,
        "extractor_retries": 3,
        "remote_components": ["ejs:github"],
        "cookiefile": str(cookie_path) if cookie_path.exists() else None,
    }
    if extractor_args:
        opts["extractor_args"] = extractor_args
    if js_runtimes:
        opts["js_runtimes"] = js_runtimes

    # убрать None значения
    opts = {k: v for k, v in opts.items() if v is not None}
    if outtmpl:
        opts["outtmpl"] = outtmpl
    if format_spec:
        opts["format"] = format_spec
    if audio_format and not skip_download:
        import shutil
        if shutil.which("ffmpeg"):
            opts["postprocessors"] = [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": audio_format,
                "preferredquality": "0",
            }]
            opts["writethumbnail"] = False
            opts["embed_metadata"] = True
            opts["embed_thumbnail"] = False
        else:
            opts["postprocessors"] = []
    return opts

@app.get("/health")
def health():
    return {"status": "ok", "jobs": len(JOBS), "yt_dlp": yt_dlp.version.__version__}

@app.get("/search", response_model=SearchResponse)
def search_music(q: str = Query(..., description="Keywords to search on YouTube"), limit: int = 10):
    clean_q = q.strip()
    if not clean_q:
        return SearchResponse(query=q, results=[])

    search_query = f"ytsearch{min(limit, 20)}:{clean_q}"
    client_chains = [["android", "ios", "web_creator"], ["ios"], ["android"], ["web_creator"]]
    for chain in client_chains:
        try:
            opts = {
                "extract_flat": "in_playlist",
                "skip_download": True,
                "quiet": True,
                "no_warnings": True,
                "socket_timeout": 10,
                "extractor_args": {"youtube": {"player_client": chain}}
            }
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(search_query, download=False)
                if not info or "entries" not in info:
                    continue
                entries = [e for e in info.get("entries", []) if e]
                results = []
                for e in entries:
                    vid_id = str(e.get("id", ""))
                    dur = int(e.get("duration", 0) or 0)
                    mins = dur // 60
                    secs = dur % 60
                    dur_str = f"{mins}:{secs:02d}"
                    thumb = e.get("thumbnail")
                    if not thumb and e.get("thumbnails"):
                        thumb = e["thumbnails"][-1].get("url")
                    if not thumb and vid_id:
                        thumb = f"https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg"
                    title = str(e.get("title") or "Untitled")
                    uploader = str(e.get("uploader") or e.get("channel") or "Unknown")
                    results.append(
                        SearchResultItem(
                            id=vid_id,
                            title=title,
                            uploader=uploader,
                            duration=dur,
                            durationFormatted=dur_str,
                            thumbnail=thumb,
                            url=f"https://www.youtube.com/watch?v={vid_id}"
                        )
                    )
                if results:
                    return SearchResponse(query=q, results=results)
        except Exception:
            traceback.print_exc()
            continue
    return SearchResponse(query=q, results=[])

def _format_filesize(size_bytes: Optional[int]) -> Optional[str]:
    if not size_bytes:
        return None
    if size_bytes >= 1024 * 1024:
        return f"{round(size_bytes / (1024 * 1024), 1)} MB"
    elif size_bytes >= 1024:
        return f"{round(size_bytes / 1024, 1)} KB"
    return f"{size_bytes} B"

@app.get("/info", response_model=InfoResponse)
def get_info(url: str = Query(..., description="YouTube or SoundCloud URL")):
    last_err = None
    chains = [
        None,
        ["ios"],
        ["web_creator"],
        ["tv"],
        ["web_embedded", "mweb"],
        ["android"],
    ]
    for chain in chains:
        try:
            opts = _yt_opts(skip_download=True, client_chain=chain)
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
                if not info:
                    continue
                if "entries" in info:
                    entries = list(info["entries"])
                    if not entries:
                        continue
                    info = entries[0]

                raw_formats = info.get("formats", [])
                valid_fmts = []
                for f in raw_formats:
                    fid = str(f.get("format_id", ""))
                    ext = str(f.get("ext", ""))
                    acodec = f.get("acodec")
                    vcodec = f.get("vcodec")
                    note = str(f.get("format_note", f.get("note", "")))

                    if ext == "mhtml" or fid.startswith("sb") or "storyboard" in note.lower():
                        continue
                    if acodec == "none" and vcodec == "none":
                        continue
                    if not acodec and not vcodec:
                        continue

                    abr = int(round(f["abr"])) if f.get("abr") else None
                    vbr = int(round(f["vbr"])) if f.get("vbr") else None
                    filesize = f.get("filesize") or f.get("filesize_approx")
                    filesize_str = _format_filesize(filesize)

                    is_audio_only = (vcodec == "none" or vcodec is None) and (acodec and acodec != "none")

                    # Человекочитаемые подписи качества
                    tier = "standard"
                    label = ""
                    if is_audio_only:
                        if "opus" in str(acodec).lower() or ext in ["opus", "webm", "ogg"]:
                            if (abr or 0) >= 130:
                                tier = "ultra"
                                label = f"Максимальное качество (Opus {abr}k)"
                            elif (abr or 0) >= 70:
                                tier = "high"
                                label = f"Высокое качество (Opus {abr}k)"
                            elif (abr or 0) >= 50:
                                tier = "standard"
                                label = f"Стандартное качество (Opus {abr}k)"
                            else:
                                tier = "low"
                                label = f"Эконом качество (Opus {abr or 'LQ'}k)"
                        elif "mp4a" in str(acodec).lower() or ext in ["m4a", "aac", "mp4"]:
                            if (abr or 0) >= 120:
                                tier = "high"
                                label = f"Высокое качество (AAC {abr}k)"
                            else:
                                tier = "standard"
                                label = f"Стандартное качество (AAC {abr or 'LQ'}k)"
                        elif ext == "mp3":
                            tier = "high"
                            label = f"MP3 Audio ({abr or 320}k)"
                        else:
                            label = f"Аудио ({ext.upper()} {abr or ''}k)"
                    else:
                        label = f"Видео {f.get('resolution') or note or ext.upper()}"

                    if not note:
                        note = label

                    valid_fmts.append((
                        is_audio_only,
                        abr or 0,
                        FormatInfo(
                            formatId=fid,
                            ext=ext,
                            acodec=acodec,
                            vcodec=vcodec,
                            abr=abr,
                            vbr=vbr,
                            note=note,
                            filesize=filesize,
                            filesizeFormatted=filesize_str,
                            qualityLabel=label,
                            qualityTier=tier,
                            isRecommended=False
                        )
                    ))

                if not valid_fmts:
                    if "soundcloud" in url.lower() or "trycloudflare" in url:
                        break
                    continue

                audio_fmts = [f[2] for f in valid_fmts if f[0]]
                audio_fmts.sort(key=lambda x: x.abr or 0, reverse=True)

                # Помечаем лучший аудиопоток как рекомендованный
                if audio_fmts:
                    audio_fmts[0].isRecommended = True

                other_fmts = [f[2] for f in valid_fmts if not f[0]]
                final_formats = (audio_fmts + other_fmts)[:60]

                return InfoResponse(
                    id=str(info.get("id", "")),
                    title=str(info.get("title", "Untitled")),
                    uploader=str(info.get("uploader") or info.get("channel") or info.get("uploader_id") or "Unknown"),
                    duration=int(info.get("duration", 0) or 0),
                    thumbnail=info.get("thumbnail"),
                    extractor=info.get("extractor"),
                    formats=final_formats,
                    description=(str(info.get("description", ""))[:500] if info.get("description") else None)
                )
        except Exception as e:
            last_err = e
            msg = str(e)
            if "trycloudflare" in url or "soundcloud" in url.lower():
                break
            if "needs to be reloaded" in msg or "Video unavailable" in msg or "no formats" in msg.lower():
                continue
            else:
                traceback.print_exc()
                continue
    traceback.print_exc()
    hint = ""
    if last_err and "needs to be reloaded" in str(last_err):
        hint = " (YouTube BotGuard: положи cookies.txt рядом с app.py)"
    raise HTTPException(status_code=500, detail=f"yt-dlp info failed: {last_err}{hint}")

@app.get("/resolve_online", response_model=ResolvedOnlineResponse)
def resolve_online(url: str = Query(..., description="YouTube/SoundCloud track or playlist URL")):
    clean_url = url.strip()
    if not clean_url:
        raise HTTPException(status_code=400, detail="URL cannot be empty")

    # Direct audio file check
    if clean_url.endswith((".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wav")):
        filename = pathlib.Path(clean_url.split("?")[0]).stem or "Direct Audio Stream"
        item = OnlineTrackItem(
            id=str(uuid.uuid4()),
            title=filename,
            uploader="Direct Stream",
            duration=0,
            durationFormatted="",
            thumbnail=None,
            url=clean_url,
            streamUrl=clean_url
        )
        return ResolvedOnlineResponse(isPlaylist=False, itemCount=1, items=[item])

    last_err = None
    chains = [None, ["ios"], ["android"], ["web_creator"], ["tv"], ["web_embedded", "mweb"]]
    for chain in chains:
        try:
            opts = {
                "extract_flat": "in_playlist",
                "skip_download": True,
                "quiet": True,
                "no_warnings": True,
                "socket_timeout": 15,
                "retries": 2,
            }
            if chain:
                opts["extractor_args"] = {"youtube": {"player_client": chain}}
            
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(clean_url, download=False)
                if not info:
                    continue

                is_playlist = info.get("_type") == "playlist" or "entries" in info
                if is_playlist:
                    raw_entries = info.get("entries", [])
                    entries = [e for e in raw_entries if e]
                    playlist_title = str(info.get("title") or "Онлайн Плейлист")
                    playlist_uploader = str(info.get("uploader") or info.get("channel") or info.get("uploader_id") or "Various Artists")
                    items = []
                    for e in entries:
                        vid_id = str(e.get("id") or "")
                        dur = int(e.get("duration", 0) or 0)
                        dur_str = f"{dur // 60}:{dur % 60:02d}" if dur > 0 else ""
                        thumb = e.get("thumbnail")
                        if not thumb and e.get("thumbnails"):
                            thumb = e["thumbnails"][-1].get("url")
                        if not thumb and vid_id and len(vid_id) == 11:
                            thumb = f"https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg"
                        
                        entry_title = str(e.get("title") or "Untitled")
                        entry_uploader = str(e.get("uploader") or e.get("channel") or playlist_uploader)
                        entry_url = e.get("url") or e.get("webpage_url")
                        if not entry_url or not entry_url.startswith("http"):
                            if vid_id and len(vid_id) == 11:
                                entry_url = f"https://www.youtube.com/watch?v={vid_id}"
                            else:
                                entry_url = clean_url

                        items.append(
                            OnlineTrackItem(
                                id=vid_id or str(uuid.uuid4()),
                                title=entry_title,
                                uploader=entry_uploader,
                                duration=dur,
                                durationFormatted=dur_str,
                                thumbnail=thumb,
                                url=entry_url,
                                streamUrl=None
                            )
                        )
                    return ResolvedOnlineResponse(
                        isPlaylist=True,
                        playlistTitle=playlist_title,
                        playlistUploader=playlist_uploader,
                        itemCount=len(items),
                        items=items
                    )
                else:
                    # Single video / audio track
                    vid_id = str(info.get("id") or "")
                    dur = int(info.get("duration", 0) or 0)
                    dur_str = f"{dur // 60}:{dur % 60:02d}" if dur > 0 else ""
                    thumb = info.get("thumbnail")
                    if not thumb and vid_id and len(vid_id) == 11:
                        thumb = f"https://i.ytimg.com/vi/{vid_id}/hqdefault.jpg"
                    
                    title = str(info.get("title") or "Untitled")
                    uploader = str(info.get("uploader") or info.get("channel") or info.get("uploader_id") or "Unknown")
                    web_url = info.get("webpage_url") or clean_url
                    stream_url = info.get("url")
                    if stream_url and stream_url.endswith(".m3u8"):
                        stream_url = None

                    item = OnlineTrackItem(
                        id=vid_id or str(uuid.uuid4()),
                        title=title,
                        uploader=uploader,
                        duration=dur,
                        durationFormatted=dur_str,
                        thumbnail=thumb,
                        url=web_url,
                        streamUrl=stream_url
                    )
                    return ResolvedOnlineResponse(
                        isPlaylist=False,
                        playlistTitle=None,
                        playlistUploader=None,
                        itemCount=1,
                        items=[item]
                    )
        except Exception as e:
            last_err = e
            traceback.print_exc()
            continue

    raise HTTPException(status_code=500, detail=f"Failed to resolve online URL: {last_err}")


@app.get("/stream")
def stream_audio(url: str = Query(..., description="YouTube or SoundCloud URL")):
    clean_url = url.strip()
    if not clean_url:
        raise HTTPException(status_code=400, detail="URL cannot be empty")

    if clean_url.endswith((".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wav")):
        return RedirectResponse(url=clean_url, status_code=307)

    chains = [None, ["ios"], ["android"], ["web_creator"], ["tv"], ["web_embedded", "mweb"]]
    for chain in chains:
        try:
            opts = _yt_opts(skip_download=True, format_spec="bestaudio/best", client_chain=chain)
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(clean_url, download=False)
                if not info:
                    continue
                if "entries" in info:
                    entries = list(info["entries"])
                    if not entries:
                        continue
                    info = entries[0]

                stream_url = info.get("url")
                if not stream_url or stream_url.endswith(".m3u8"):
                    for f in reversed(info.get("formats", [])):
                        acodec = f.get("acodec")
                        vcodec = f.get("vcodec")
                        if acodec and acodec != "none" and (vcodec == "none" or vcodec is None):
                            f_url = f.get("url")
                            if f_url and not f_url.endswith(".m3u8"):
                                stream_url = f_url
                                break

                if stream_url:
                    return RedirectResponse(url=stream_url, status_code=307)
        except Exception:
            traceback.print_exc()
            continue

    raise HTTPException(status_code=500, detail="Could not resolve direct audio stream")


def _download_task(job_id: str, url: str, fmt_id: Optional[str], audio_fmt: str):
    JOBS[job_id]["status"] = JobStatus.downloading
    last_err = None
    chains = [None, ["ios"], ["web_creator"], ["tv"], ["web_embedded", "mweb"]]
    for chain in chains:
        try:
            out = str(APP_DIR / f"{job_id}.%(ext)s")
            if fmt_id:
                fmt = fmt_id
            else:
                if audio_fmt in ["opus", "ogg"]:
                    fmt = "bestaudio[ext=opus]/bestaudio[ext=webm]/bestaudio/best"
                elif audio_fmt == "m4a":
                    fmt = "bestaudio[ext=m4a]/bestaudio/best"
                elif audio_fmt == "mp3":
                    fmt = "bestaudio/best"
                else:
                    fmt = "bestaudio/best"
            opts = _yt_opts(skip_download=False, outtmpl=out, format_spec=fmt, audio_format=audio_fmt if audio_fmt != "best" else None, client_chain=chain)
            def hook(d):
                if d.get("status") == "downloading":
                    try:
                        total = d.get("total_bytes") or d.get("total_bytes_estimate") or 1
                        downloaded = d.get("downloaded_bytes", 0)
                        JOBS[job_id]["progress"] = min(0.99, downloaded / total if total else 0)
                        JOBS[job_id]["filesize"] = total
                        if d.get("_speed_str"):
                            JOBS[job_id]["speed"] = d.get("_speed_str")
                        if d.get("_eta_str"):
                            JOBS[job_id]["eta"] = str(d.get("_eta_str")) + "s"
                    except: pass
            opts["progress_hooks"] = [hook]
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=True)
                if not info:
                    continue
                if "entries" in info:
                    info = list(info["entries"])[0]
                filename = ydl.prepare_filename(info)
                import glob
                candidates = glob.glob(str(APP_DIR / f"{job_id}.*"))
                if candidates:
                    filename = candidates[0]
                JOBS[job_id]["filename"] = pathlib.Path(filename).name
                JOBS[job_id]["filepath"] = filename
                JOBS[job_id]["status"] = JobStatus.completed
                JOBS[job_id]["progress"] = 1.0
                return
        except Exception as e:
            last_err = e
            msg = str(e)
            traceback.print_exc()
            continue
    JOBS[job_id]["status"] = JobStatus.failed
    JOBS[job_id]["error"] = str(last_err) + " (hint: для YouTube положи cookies.txt рядом с app.py)"

@app.post("/download", response_model=JobResponse)
def start_download(req: DownloadRequest, background: BackgroundTasks):
    job_id = str(uuid.uuid4())
    JOBS[job_id] = {"status": JobStatus.queued, "progress": 0, "url": req.url, "filename": None, "filepath": None, "error": None}
    background.add_task(_download_task, job_id, req.url, req.formatId, req.audioFormat)
    return JobResponse(jobId=job_id, status=JobStatus.queued)

@app.get("/status/{job_id}", response_model=JobStatusResponse)
def job_status(job_id: str):
    j = JOBS.get(job_id)
    if not j:
        raise HTTPException(status_code=404, detail="job not found")
    return JobStatusResponse(
        jobId=job_id,
        status=j["status"],
        progress=j.get("progress", 0),
        filename=j.get("filename"),
        filesize=j.get("filesize"),
        speed=j.get("speed"),
        eta=j.get("eta"),
        error=j.get("error")
    )

@app.get("/file/{job_id}")
def get_file(job_id: str):
    j = JOBS.get(job_id)
    if not j:
        raise HTTPException(status_code=404, detail="job not found")
    if j["status"] != JobStatus.completed:
        raise HTTPException(status_code=425, detail=f"job not completed, status={j['status']}")
    fp = j.get("filepath")
    if not fp or not pathlib.Path(fp).exists():
        raise HTTPException(status_code=404, detail="file not found on disk")
    # try to infer media type
    return FileResponse(fp, filename=j.get("filename") or pathlib.Path(fp).name, media_type="application/octet-stream")

@app.delete("/job/{job_id}")
def delete_job(job_id: str):
    j = JOBS.pop(job_id, None)
    if not j:
        raise HTTPException(status_code=404, detail="not found")
    fp = j.get("filepath")
    if fp:
        try: pathlib.Path(fp).unlink(missing_ok=True)
        except: pass
    return {"deleted": job_id}

@app.get("/")
def root():
    return {
        "name": "Materialy Music Server",
        "docs": "/docs",
        "health": "/health",
        "endpoints": [
            "/info?url=",
            "/resolve_online?url=",
            "/stream?url=",
            "/download (POST)",
            "/status/{jobId}",
            "/file/{jobId}"
        ]
    }
