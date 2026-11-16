from pathlib import Path
import hashlib,zipfile,json
root=Path(__file__).resolve().parents[1]
out=root/'release';out.mkdir(exist_ok=True)
files=[]
for folder in ['src','scripts','docs','tests']:
 for p in (root/folder).rglob('*'):
  if not p.is_file() or any(x in {'__pycache__','artifacts'} for x in p.parts) or p.suffix=='.pyc':continue
  files.append(p)
for name in ['pom.xml','README.md','mvnw','Dockerfile','Dockerfile.runtime','compose.yaml','compose.prod.yaml','Caddyfile','.gitignore','.dockerignore','.env.example','target/safeelo-1.0.0.jar']:
 p=root/name
 if p.is_file():files.append(p)
 else:raise SystemExit('Arquivo necessário ausente: '+name)
archive=out/'SafeElo-1.0.0-completo.zip'
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(set(files)):z.write(p,Path('SafeElo')/p.relative_to(root))
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 assert not any('/.local/' in x or '/.env' in x and not x.endswith('/.env.example') for x in z.namelist())
 assert 'SafeElo/target/safeelo-1.0.0.jar' in z.namelist()
summary={'file':archive.name,'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'entries':len(files)}
(out/'SHA256SUMS').write_text(summary['sha256']+'  '+archive.name+'\n')
print(json.dumps(summary))
