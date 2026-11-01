#!/usr/bin/env bash
# CI compiler/test output only. No real Meta credentials are supplied in CI.
set -euo pipefail
label="${1:?stage label required}"
shift
log="$RUNNER_TEMP/meta6-$label.log"
if "$@" > "$log" 2>&1; then
  cat "$log"
else
  cat "$log"
  python3 - "$log" "$label" <<'PY'
import pathlib,re,sys
lines=pathlib.Path(sys.argv[1]).read_text(errors='replace').splitlines()
relevant=[s for s in lines if re.search(r'error|failed|requires|incompatible|what went wrong|exception|minimum supported|compileSdk|desugar|not found|could not',s,re.I)]
for line in (relevant or lines)[-8:]:
    line=re.sub(r'https?://\S+', '[url]',line)[:1000]
    print('::error title=Meta6 '+sys.argv[2]+'::'+line.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
PY
  exit 1
fi
