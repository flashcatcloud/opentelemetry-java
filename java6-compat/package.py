#!/usr/bin/env python3
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import shutil, sys
root=Path(__file__).resolve().parents[1];b=root/'java6-compat/build';dist=b/'dist'
if dist.exists(): shutil.rmtree(dist)
dist.mkdir()
for name,is_test in [('opentelemetry-trace-java6.jar',False),('acceptance-tests.jar',True)]:
    with ZipFile(dist/name,'w',ZIP_DEFLATED) as z:
        z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nImplementation-Version: 1.66.0-java6-dev\n\n')
        z.write(root/'LICENSE','META-INF/LICENSE')
        for p in sorted((b/'java6').rglob('*.class')):
            path=p.relative_to(b/'java6').as_posix()
            test=path.startswith(('SdkCompatibilityTest','PropagationPeer'))
            if test==is_test:
                assert int.from_bytes(p.read_bytes()[6:8],'big')==50,path
                z.write(p,path)
for name in ['streamsupport-1.7.4.jar','threetenbp-1.6.8.jar','commons-codec-1.10.jar','jctools-core-4.0.7.jar']:
    shutil.copyfile(Path(sys.argv[1])/name,dist/name)
print('Packaged Java6 runtime and separate acceptance tests')

for p in dist.glob('*.jar'):
    with ZipFile(p) as z:
        for n in z.namelist():
            if n.endswith('.class') and n != 'module-info.class' and not n.startswith('META-INF/versions/'):
                assert int.from_bytes(z.read(n)[6:8], 'big') <= 50, (p.name, n)
print('All base runtime classes are Java6 bytecode or older')
