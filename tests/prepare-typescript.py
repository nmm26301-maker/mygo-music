"""Pin the official MIT-licensed Babel browser compiler; never download code at app startup."""
import base64, hashlib, io, pathlib, tarfile, urllib.request

root=pathlib.Path(__file__).resolve().parents[1]
version='7.29.13'
integrity='N8oXikyXZJfWWQoO26w+KLheK1P4itiJx15C29FNWpYsxYhbie2EyfZDvxZfBL+Gx+hInpGI0CO7y/pSh6hHkg=='
with urllib.request.urlopen(f'https://registry.npmjs.org/@babel/standalone/-/standalone-{version}.tgz',timeout=45) as response:
    data=response.read(16*1024*1024)
assert base64.b64encode(hashlib.sha512(data).digest()).decode()==integrity,'Babel integrity mismatch'
with tarfile.open(fileobj=io.BytesIO(data),mode='r:gz') as archive:
    for name,target in [('babel.min.js',root/'app/src/main/assets/vendor/babel.min.js'),('LICENSE',root/'app/src/main/assets/third-party/Babel-MIT.txt')]:
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(archive.extractfile('package/'+name).read())
print('Verified official @babel/standalone',version,'and retained its MIT license')
