"""Download exact upstream archives only for Android CI, without vendoring plugin code."""
import concurrent.futures, hashlib, json, pathlib, time, urllib.request

root = pathlib.Path(__file__).resolve().parents[1]
destination = root / "app/src/androidTest/assets/market-audit"
destination.mkdir(parents=True, exist_ok=True)
pins = json.loads((root / "tests/market-pins.json").read_text())

def download(pair):
    index, pin = pair
    path = destination / f"{index:02}.zip"
    error = ""
    for attempt in range(3):
        try:
            request = urllib.request.Request(pin["url"], headers={"User-Agent": "MyGo-Android-Compatibility-Audit"})
            with urllib.request.urlopen(request, timeout=45) as response:
                data = response.read(32 * 1024 * 1024 + 1)
            if len(data) > 32 * 1024 * 1024:
                raise ValueError("upstream archive exceeds 32 MiB installation limit")
            if hashlib.sha256(data).hexdigest() != pin["sha256"]:
                raise ValueError("upstream archive SHA-256 changed")
            path.write_bytes(data)
            return dict(pin, archive=path.name)
        except Exception as failure:
            error = str(failure)
            time.sleep(attempt + 1)
    return dict(pin, downloadError=error)

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    rows = list(pool.map(download, enumerate(pins)))
(destination / "index.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2))
print("Pinned market packages:", len(rows), "downloaded:", sum("archive" in r for r in rows))
