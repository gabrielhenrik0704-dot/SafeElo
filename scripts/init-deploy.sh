#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
if [ -e .env ]; then echo 'O arquivo .env já existe. Ele não foi alterado.'; exit 1; fi
umask 077
python3 - <<'PY'
import base64,secrets
from pathlib import Path
content=Path('.env.example').read_text().replace('DB_PASSWORD=\n','DB_PASSWORD='+secrets.token_urlsafe(32)+'\n').replace('SAFEELO_ENCRYPTION_KEY=\n','SAFEELO_ENCRYPTION_KEY='+base64.b64encode(secrets.token_bytes(32)).decode()+'\n')
Path('.env').write_text(content)
print('Configuração local gerada. Guarde .env em segurança e configure DOMAIN para produção.')
PY
