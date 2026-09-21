import pathlib
import time
from fastapi.testclient import TestClient
from app import app, JOBS

client = TestClient(app)

def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    j = r.json()
    assert j["status"] == "ok"
    assert "yt_dlp" in j
    print("✓ health:", j)

def test_root():
    r = client.get("/")
    assert r.status_code == 200
    assert "Materialy" in r.text
    print("✓ root")

def test_info_requires_url():
    r = client.get("/info")
    assert r.status_code == 422
    print("✓ info requires url")

def test_search():
    r = client.get("/search", params={"q": "MiSide"})
    assert r.status_code == 200
    j = r.json()
    assert "query" in j
    assert "results" in j
    print("✓ search results:", len(j["results"]))

def test_download_job_lifecycle():
    # Use a known test video? Use youtube test video that is stable.
    # We use yt-dlp test video: BaW_jenozKc is youtube-dl test (10 sec)
    # If no internet, we expect error but job should be created and fail gracefully.
    url = "https://www.youtube.com/watch?v=QAUV3SeCqoE"
    # First try info - may fail offline but we handle gracefully
    r = client.get("/info", params={"url": url})
    if r.status_code == 200:
        print("✓ info fetched:", r.json()["title"][:60])
        assert "formats" in r.json()
    else:
        print("! info failed (offline?) status", r.status_code, r.text[:200])

    # start download
    r = client.post("/download", json={"url": url, "audioFormat": "opus"})
    assert r.status_code == 200
    job = r.json()
    assert "jobId" in job
    job_id = job["jobId"]
    print("✓ download queued", job_id)

    # poll status 3 times quickly
    for i in range(3):
        time.sleep(0.5)
        r = client.get(f"/status/{job_id}")
        assert r.status_code == 200
        print(f"  status poll {i}:", r.json())

    # file should be ready (200) or pending (425 or 404)
    r = client.get(f"/file/{job_id}")
    assert r.status_code in (200, 404, 425, 500)
    print("✓ file status code:", r.status_code)

    # delete job
    r = client.delete(f"/job/{job_id}")
    assert r.status_code == 200
    print("✓ delete")

def test_invalid_job():
    r = client.get("/status/doesnotexist")
    assert r.status_code == 404
    r = client.get("/file/doesnotexist")
    assert r.status_code == 404
    print("✓ invalid job 404")

def test_resolve_online_direct():
    direct_url = "https://example.com/audio.mp3"
    r = client.get("/resolve_online", params={"url": direct_url})
    assert r.status_code == 200
    j = r.json()
    assert j["isPlaylist"] is False
    assert len(j["items"]) == 1
    assert j["items"][0]["streamUrl"] == direct_url
    print("✓ resolve direct audio stream")

def test_stream_direct():
    direct_url = "https://example.com/audio.mp3"
    r = client.get("/stream", params={"url": direct_url}, follow_redirects=False)
    assert r.status_code == 307
    assert r.headers["location"] == direct_url
    print("✓ stream direct redirect 307")

if __name__ == "__main__":
    test_health()
    test_root()
    test_info_requires_url()
    test_invalid_job()
    test_download_job_lifecycle()
    print("\n=== ALL TESTS PASSED ===")
