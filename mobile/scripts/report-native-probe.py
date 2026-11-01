"""CI diagnostics: only the fixed native library probe, never raw app logs."""
import json,re,sys
from pathlib import Path
def safe(text):
    text = re.sub(r'https?://\S+', '[url]', text)
    text = re.sub(r'\d{5,}:[A-Za-z0-9_-]{15,}', '[redacted]', text)
    return text[:2000].replace('%','%25').replace('\r','%0D').replace('\n','%0A')
try:
    probe = json.loads(Path(sys.argv[1]).read_text())
    print('::error title=Native worker probe::'+safe(str(probe.get('state','unknown'))+': '+str(probe.get('reason',''))))
except Exception:
    print('::error title=Native worker probe::App did not write its fixed native probe file.')
# Only JS/Android exception type/message lines from a fresh credential-free launch.
# Do not forward arbitrary logs, network activity, config, stack or account data.
for line in Path(sys.argv[2]).read_text(errors='replace').splitlines():
    if re.search(r'(TypeError:|ReferenceError:|SyntaxError:|UnsatisfiedLinkError:|JavascriptException:)',line):
        print('::error title=App startup exception::'+safe(line))
