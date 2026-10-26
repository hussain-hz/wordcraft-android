"""Regenerate Android UI catalogs and stable label lookup from the reviewed bilingual catalog."""
from pathlib import Path
import json,hashlib,html
ROOT=Path(__file__).resolve().parents[1]
main=ROOT/'apps/wordcraft-android/android/app/src/main'
labels=json.loads((ROOT/'docs/arabic-labels.json').read_text(encoding='utf-8'))
def key(text):return 'ui_'+hashlib.sha256(text.encode()).hexdigest()[:12]
def xml(text):return html.escape(text,quote=False).replace("'",r"\'").replace('"',r'\"')
for lang in ['', '-ar']:
    out='<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
    out+=''.join(f'    <string name="{key(en)}">{xml(ar if lang else en)}</string>\n' for en,ar in sorted(labels.items()))+'</resources>\n'
    (main/f'res/values{lang}/ui_strings.xml').write_bytes(out.encode())
p=main/'java/io/github/artcraftandroid/wordcraft/ui/UiStrings.kt'
s=p.read_text(encoding='utf-8').split('private fun labelResource')[0]
s+='private fun labelResource(source: String): Int? = when (source) {\n'
s+=''.join('    '+json.dumps(en,ensure_ascii=False).replace('$',r'\$')+' -> R.string.'+key(en)+'\n' for en in sorted(labels))+'    else -> null\n}\n'
p.write_bytes(s.encode())
print(f'Generated {len(labels)} bilingual UI labels.')
