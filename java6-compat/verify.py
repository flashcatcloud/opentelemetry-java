#!/usr/bin/env python3
"""Build from source and verify SDK behavior on genuine Java6, plus native SDK interop."""
from pathlib import Path
import argparse,json,subprocess,sys,time
p=argparse.ArgumentParser();p.add_argument('--jdk6',required=True,type=Path);a=p.parse_args()
root=Path(__file__).resolve().parents[1];b=root/'java6-compat/build';logs=b/'logs';logs.mkdir(parents=True,exist_ok=True)
results=[]
def run(name,cmd,marker=None):
    start=time.monotonic()
    r=subprocess.run([str(x) for x in cmd],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=300)
    (logs/(name+'.log')).write_text(r.stdout)
    results.append(dict(name=name,command=[str(x) for x in cmd],exit=r.returncode,seconds=round(time.monotonic()-start,2)))
    (b/'results.json').write_text(json.dumps(results,indent=2)+'\n')
    if r.returncode or (marker and marker not in r.stdout):
        print(r.stdout);raise RuntimeError(name+' failed')
    print('PASS '+name,flush=True)
run('dependencies',[sys.executable,root/'java6-compat/fetch.py'])
run('prepare',[sys.executable,root/'java6-compat/prepare.py'])
common=['docker','run','--rm','--platform','linux/amd64','-v',str(root)+':/repo','-w','/repo/java6-compat']
eight=common+['-v',str(b/'deps/build')+':/deps:ro','-v',str(b/'deps/native')+':/native:ro','eclipse-temurin:8-jdk']
six=common+['-v',str(a.jdk6.resolve())+':/jdk:ro','debian:bookworm-slim','/jdk/bin/java','-Xverify:all']
run('jdk6-version',six+['-version'],'1.6.0_45')
run('build',eight+['sh','build.sh'])
run('jdk6-links',[sys.executable,root/'java6-compat/check_jdk6.py',a.jdk6/'jre/lib/rt.jar',b/'java6'],'missing JDK6 links: 0')
run('package',[sys.executable,root/'java6-compat/package.py',b/'deps/build'])
cp='build/dist/*'
run('java6-behavior',six+['-cp',cp,'SdkCompatibilityTest'],'PASS parent-based sampling java=1.6.0_45')
run('java6-strict-context',six+['-Dio.opentelemetry.context.enableStrictContext=true','-cp',cp,'SdkCompatibilityTest'],'PASS parent-based sampling')
run('java8-backport-behavior',eight+['java','-XX:TieredStopAtLevel=1','-Xverify:all','-cp',cp,'SdkCompatibilityTest'],'PASS parent-based sampling')
run('native-compile',eight+['sh','-c',"mkdir -p build/native-tests; javac -J-XX:TieredStopAtLevel=1 -proc:none -cp '/native/*' -d build/native-tests tests/*.java"])
native=eight+['java','-XX:TieredStopAtLevel=1','-Xverify:all','-cp','build/native-tests:/native/*']
run('java8-official-behavior',native+['SdkCompatibilityTest'],'PASS parent-based sampling')
seed='00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01'
def read(path):
    # Fixtures use ASCII values; java.util.Properties escapes '=' in values.
    return dict(line.replace('\\=', '=').split('=',1) for line in path.read_text().splitlines() if '=' in line and not line.startswith('#'))
for direction,peers in [('8-6-8',[native,six+['-cp',cp],native]),('6-8-6',[six+['-cp',cp],native,six+['-cp',cp]])]:
    previous=b/(direction+'-0.properties');previous.write_text('traceparent='+seed+'\ntracestate=vendor=value\nbaggage=tenant=flashcat\n')
    parent='00f067aa0ba902b7'
    for i,peer in enumerate(peers,1):
        out=b/(direction+'-'+str(i)+'.properties')
        run('interop-'+direction+'-'+str(i),peer+['PropagationPeer','build/'+previous.name,'build/'+out.name],'PASS peer')
        data=read(out)
        assert data['observed.trace']=='4bf92f3577b34da6a3ce929d0e0e4736'
        assert data['observed.parent']==parent
        assert data['traceparent']=='00-'+data['observed.trace']+'-'+data['observed.span']+'-01'
        assert data['tracestate']=='vendor=value' and data['baggage']=='tenant=flashcat'
        parent=data['observed.span'];previous=out
print('PASS both directions preserve trace ID, parent IDs, tracestate and baggage')
