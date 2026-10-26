"""Validate Arabic UI resources, format arguments and separation from document content."""
from pathlib import Path
import hashlib,json,re,unittest,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
MAIN=ROOT/'apps/wordcraft-android/android/app/src/main'
UI=MAIN/'java/io/github/artcraftandroid/wordcraft/ui'
class ArabicResources(unittest.TestCase):
    def test_catalog_and_resources_match(self):
        labels=json.loads((ROOT/'docs/arabic-labels.json').read_text(encoding='utf-8'))
        resources=[]
        for folder in ['values','values-ar']:
            nodes=list(ET.parse(MAIN/f'res/{folder}/ui_strings.xml').getroot())
            self.assertEqual(len(nodes),len({n.attrib['name'] for n in nodes}))
            resources.append({n.attrib['name']:n.text or '' for n in nodes})
        self.assertEqual(resources[0].keys(),resources[1].keys())
        self.assertEqual(len(labels),len(resources[0]))
        for source,arabic in labels.items():
            key='ui_'+hashlib.sha256(source.encode()).hexdigest()[:12]
            self.assertIn(key,resources[0]);self.assertTrue(arabic.strip())
            self.assertEqual(sorted(re.findall(r'%\d+\$[sd]',source)),sorted(re.findall(r'%\d+\$[sd]',arabic)),source)
    def test_android_locale_support(self):
        ns='{http://schemas.android.com/apk/res/android}'
        app=ET.parse(MAIN/'AndroidManifest.xml').getroot().find('application')
        self.assertEqual(app.attrib[ns+'supportsRtl'],'true')
        locales=ET.parse(MAIN/'res/xml/locales_config.xml').getroot()
        self.assertEqual({x.attrib[ns+'name'] for x in locales},{'ar','en'})
    def test_document_content_is_not_translated(self):
        self.assertIn('Text(c.text,', (UI/'editor/Comments.kt').read_text(encoding='utf-8'))
        self.assertIn('Text(title,', (UI/'editor/EditorChrome.kt').read_text(encoding='utf-8'))
        self.assertIn('Text(r.name.substringBeforeLast', (UI/'StartScreen.kt').read_text(encoding='utf-8'))
        self.assertNotIn('uiText(vm.encodingPreview)', (UI/'editor/TextEncoding.kt').read_text(encoding='utf-8'))
    def test_kotlin_format_dollars_are_escaped(self):
        for p in UI.rglob('*.kt'):
            self.assertFalse(re.search(r'%\d+\$',p.read_text(encoding='utf-8')),str(p))
if __name__=='__main__':unittest.main()
