"""Read-only reproduction of the pinned upstream public smoke request. No credentials."""
import datetime, json, pathlib, re, time, urllib.error, urllib.parse, urllib.request

SITE = 'https://www.ximalaya.com'
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15',
    'Referer': SITE + '/', 'Accept': 'application/json',
}

def safe_message(value):
    if not isinstance(value, str):
        return None
    value = re.sub(r'https?://\S+', '[redacted-url]', value)
    value = re.sub(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}', '[redacted-email]', value)
    value = re.sub(r'[A-Za-z0-9_=-]{32,}', '[redacted-value]', value)
    return value[:240]

rows = []
for label, path, params in [
    ('public-track-metadata', '/revision/track/simple', {'trackId': '47740352'}),
    ('original-public-playback', '/mobile-playpage/track/v3/baseInfo/' + str(int(time.time() * 1000)),
     {'device': 'web', 'trackId': '47740352', 'trackQualityLevel': '1'}),
]:
    row = {'request': label, 'trackId': '47740352', 'credentialsUsed': False}
    try:
        request = urllib.request.Request(SITE + path + '?' + urllib.parse.urlencode(params), headers=HEADERS)
        try:
            response = urllib.request.urlopen(request, timeout=12)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            row['httpStatus'] = response.status
            body = json.loads(response.read(1024 * 1024))
        row['ret'] = body.get('ret')
        row['message'] = safe_message(body.get('msg') or body.get('message'))
        info = body.get('trackInfo') or {}
        row['returnedTrackMatches'] = str(info.get('trackId', '')) == '47740352'
        row['hasPlaybackCandidates'] = isinstance(info.get('playUrlList'), list) and len(info['playUrlList']) > 0
    except Exception as error:
        row['errorType'] = type(error).__name__
    rows.append(row)
report = {'sourceCommit': '5084fa6ed17ff2b698fb7ccd554d028c6630bc1c',
          'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'scope': 'Pinned upstream public sample and original public request only; no account or alternate endpoint',
          'rows': rows}
pathlib.Path('ximalaya-public-probe.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(json.dumps(report, ensure_ascii=False, indent=2))
