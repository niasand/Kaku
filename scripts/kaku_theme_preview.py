#!/usr/bin/env python3
"""
Generate a WezTerm/Kaku color-scheme preview gallery.

Reads the 1001 schemes baked into config/src/scheme_data.rs, renders one PNG
per scheme (background + foreground + ANSI sample lines + 16-color swatch),
splits them into dark/ and light/ subfolders by background luminance, and emits
a single-file searchable index.html (lazy thumbnails + lightbox).

Output (default ~/Desktop/kaku-theme/):
    dark/<name>.png
    light/<name>.png
    index.html

Usage:
    python3 scripts/kaku_theme_preview.py            # full gallery
    python3 scripts/kaku_theme_preview.py --sample    # 3 schemes only
    python3 scripts/kaku_theme_preview.py --out DIR   # custom output dir
"""
import json
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SCHEME_FILE = ROOT / "config" / "src" / "scheme_data.rs"
DEFAULT_OUT = ROOT / "theme-gallery"
FONT_PATH = "/System/Library/Fonts/Menlo.ttc"
W, H = 960, 560

_FONTS = {}


def out_dir():
    if "--out" in sys.argv:
        return Path(sys.argv[sys.argv.index("--out") + 1])
    return DEFAULT_OUT


def font(size):
    if size not in _FONTS:
        _FONTS[size] = ImageFont.truetype(FONT_PATH, size)
    return _FONTS[size]


def parse_schemes(path):
    data = Path(path).read_text(encoding="utf-8", errors="replace")
    out = []
    for line in data.splitlines():
        m = re.match(r'^\("([^"]*)", "(.*)"\),?\s*$', line)
        if not m:
            continue
        name = m.group(1)
        toml = m.group(2).replace('\\"', '"').replace('\\n', '\n')
        out.append((name, toml))
    return out


def color(toml, key):
    m = re.search(rf'{key}\s*=\s*"([^"]+)"', toml)
    return m.group(1) if m else None


def colorlist(toml, key):
    m = re.search(rf'{key}\s*=\s*\[(.*?)\]', toml, re.DOTALL)
    return re.findall(r'"([^"]+)"', m.group(1)) if m else []


def safe(name):
    return name.replace('/', '-').replace(':', '-').strip()


def norm(c, fb):
    return c if (isinstance(c, str) and c.startswith('#') and len(c) in (4, 7)) else fb


def luminance(hex_color):
    h = hex_color.lstrip('#')
    r, g, b = int(h[0:2], 16) / 255, int(h[2:4], 16) / 255, int(h[4:6], 16) / 255
    return 0.299 * r + 0.587 * g + 0.114 * b


def category(bg):
    return 'light' if luminance(bg) > 0.5 else 'dark'


def build(name, toml, outroot):
    bg = norm(color(toml, 'background'), '#000000')
    fg = norm(color(toml, 'foreground'), '#cccccc')
    cursor = norm(color(toml, 'cursor_bg'), fg)
    ansi = [norm(a, bg) for a in (colorlist(toml, 'ansi') + [bg] * 8)[:8]]
    brights = [norm(b, bg) for b in (colorlist(toml, 'brights') + [bg] * 8)[:8]]
    cat = category(bg)

    img = Image.new('RGB', (W, H), bg)
    d = ImageDraw.Draw(img)

    title = name if len(name) <= 44 else name[:41] + '...'
    d.text((40, 26), title, font=font(26), fill=fg)

    samples = [
        ("$ kaku --version", fg),
        ("kaku 0.12.2 - forked from WezTerm", fg),
        ("[ OK ] build succeeded in 142s", ansi[2]),
        ("[ERR] 3 tests failed", ansi[1]),
        ("[WRN] 5 warnings", ansi[3]),
        ("[ i ] pushed to origin/main", ansi[4]),
    ]
    y = 96
    for text, col in samples:
        d.text((40, y), text, font=font(18), fill=col)
        y += 30

    d.text((40, 300), "Normal  (0-7)", font=font(13), fill=fg)
    d.text((40, 396), "Bright  (8-15)", font=font(13), fill=fg)

    bw, gap, x0 = 100, 12, 40
    for i, c in enumerate(ansi):
        x = x0 + i * (bw + gap)
        d.rectangle([x, 322, x + bw, 364], fill=c)
        d.text((x + 44, 368), str(i), font=font(11), fill=fg)
    for i, c in enumerate(brights):
        x = x0 + i * (bw + gap)
        d.rectangle([x, 418, x + bw, 460], fill=c)
        d.text((x + 44, 464), str(i + 8), font=font(11), fill=fg)

    d.text((40, 520), f"bg={bg}   fg={fg}   cursor={cursor}", font=font(12), fill=fg)

    rel = f"{cat}/{safe(name)}.png"
    (outroot / rel).parent.mkdir(parents=True, exist_ok=True)
    img.save(outroot / rel, 'PNG')
    return {'name': name, 'file': rel, 'cat': cat, 'bg': bg, 'fg': fg}


INDEX_HTML = """<!DOCTYPE html>
<html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Kaku Themes &mdash; __COUNT__ schemes</title>
<style>
*{box-sizing:border-box}
body{margin:0;font-family:-apple-system,"SF Pro Text",Menlo,monospace;background:#15141b;color:#d5d4d6}
.bar{position:sticky;top:0;background:#1f1d27;padding:12px 18px;display:flex;gap:10px;align-items:center;flex-wrap:wrap;border-bottom:1px solid #2e2c3a;z-index:10}
input{flex:1;min-width:220px;padding:9px 13px;background:#100f15;color:#eee;border:1px solid #3a3848;border-radius:8px;font-size:14px}
input:focus{outline:none;border-color:#8e6ad9}
button{padding:7px 13px;background:#2a2836;color:#cfcde0;border:1px solid #3a3848;border-radius:7px;cursor:pointer;font-size:13px}
button.active{background:#5e3db3;border-color:#8e6ad9;color:#fff}
.stats{color:#8a8898;font-size:13px;margin-left:4px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(290px,1fr));gap:12px;padding:16px}
.card{background:#1f1d27;border:1px solid #2e2c3a;border-radius:10px;overflow:hidden;cursor:pointer;transition:transform .08s,border-color .08s}
.card:hover{transform:translateY(-2px);border-color:#5e3db3}
.card img{width:100%;display:block;aspect-ratio:960/560;object-fit:cover;background:#000}
.card .name{padding:8px 11px;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tag{font-size:9px;padding:2px 6px;border-radius:4px;margin-right:7px;text-transform:uppercase;letter-spacing:.5px}
.tag.dark{background:#3a3848;color:#aaa}
.tag.light{background:#e6e3da;color:#5a574c}
.empty{grid-column:1/-1;text-align:center;color:#6a6878;padding:60px}
#lightbox{position:fixed;inset:0;background:rgba(0,0,0,.93);display:none;align-items:center;justify-content:center;z-index:100;padding:30px;cursor:zoom-out}
#lightbox.show{display:flex;flex-direction:column}
#lightbox img{max-width:96%;max-height:88vh;border-radius:8px;box-shadow:0 8px 40px rgba(0,0,0,.6)}
#lightbox .cap{margin-top:14px;text-align:center;color:#cfcde0;font-size:14px}
.card{position:relative}
.copy{position:absolute;top:7px;right:7px;width:30px;height:30px;border-radius:7px;background:rgba(30,29,40,.85);color:#cfcde0;border:1px solid #3a3848;cursor:pointer;font-size:15px;opacity:0;transition:opacity .12s;display:flex;align-items:center;justify-content:center;z-index:2}
.card:hover .copy{opacity:1}
.copy:hover{background:#5e3db3;border-color:#8e6ad9;color:#fff}
.copy.done{background:#58d8ad;border-color:#58d8ad;color:#15141b}
.apply{position:absolute;top:7px;right:43px;width:30px;height:30px;border-radius:7px;background:rgba(30,29,40,.85);color:#f0c674;border:1px solid #3a3848;cursor:pointer;font-size:14px;opacity:0;transition:opacity .12s;display:flex;align-items:center;justify-content:center;z-index:2}
.card:hover .apply{opacity:1}
.apply:hover{background:#5e3db3;border-color:#8e6ad9;color:#fff}
.apply.done{background:#58d8ad;border-color:#58d8ad;color:#15141b}
#lbapply{margin-left:8px;padding:10px 20px;background:#2a2836;color:#f0c674;border:1px solid #3a3848;border-radius:8px;cursor:pointer;font-size:13px;font-family:inherit}
#lbapply:hover{background:#5e3db3;color:#fff;border-color:#8e6ad9}
#lbapply.done{background:#58d8ad;color:#15141b}
#lbcopy{margin-top:12px;padding:10px 20px;background:#5e3db3;color:#fff;border:none;border-radius:8px;cursor:pointer;font-size:13px;font-family:inherit;max-width:92%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
#lbcopy:hover{background:#8e6ad9}
#lbcopy.done{background:#58d8ad}
.toast{position:fixed;bottom:32px;left:50%;transform:translateX(-50%);background:#58d8ad;color:#15141b;padding:11px 24px;border-radius:9px;font-size:14px;font-weight:600;z-index:200;opacity:0;transition:opacity .2s;pointer-events:none;box-shadow:0 6px 24px rgba(0,0,0,.4)}
.toast.show{opacity:1}
</style></head><body>
<div class="bar">
  <input id="q" placeholder="搜索主题名（gruvbox / tokyo / dracula…）" autofocus>
  <button data-cat="all" class="active">全部</button>
  <button data-cat="dark">深色</button>
  <button data-cat="light">浅色</button>
  <span class="stats" id="stats"></span>
</div>
<div class="grid" id="grid"></div>
<div id="lightbox"><img id="lb"><div class="cap" id="lbcap"></div><div style="margin-top:14px;display:flex;gap:8px;flex-wrap:wrap;justify-content:center"><button id="lbcopy">复制 config.color_scheme</button><button id="lbapply">⚡ 应用到 kaku</button></div></div>
<div class="toast" id="toast"></div>
<script>
const THEMES = __THEMES__;
const grid = document.getElementById('grid');
const stats = document.getElementById('stats');
let curCat='all', curQ='';
const esc = s => s.replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
function render(){
  const q=curQ.toLowerCase();
  const f=THEMES.filter(t => (curCat==='all'||t.cat===curCat) && t.name.toLowerCase().includes(q));
  const dc=f.filter(t=>t.cat==='dark').length;
  stats.textContent = f.length+' / '+THEMES.length+'  ('+dc+' dark, '+(f.length-dc)+' light)';
  grid.innerHTML = f.length ? f.map(t =>
    `<div class="card" data-file="${t.file}" data-name="${esc(t.name)}">
       <button class="copy" data-name="${esc(t.name)}" title="复制 color_scheme">⧉</button>
       <button class="apply" data-name="${esc(t.name)}" title="应用到 kaku">⚡</button>
       <img loading="lazy" src="${t.file}" alt="${esc(t.name)}">
       <div class="name"><span class="tag ${t.cat}">${t.cat}</span>${esc(t.name)}</div>
     </div>`).join('') : '<div class="empty">没有匹配的主题</div>';
}
document.getElementById('q').addEventListener('input', e=>{curQ=e.target.value;render();});
document.querySelectorAll('.bar button').forEach(b=>b.addEventListener('click',()=>{
  document.querySelectorAll('.bar button').forEach(x=>x.classList.remove('active'));
  b.classList.add('active'); curCat=b.dataset.cat; render();
}));
const APPLY_CMD="python3 __APPLY_PATH__ ";
function copyText(text,btn,msg){
  let ok=false; const ta=document.createElement('textarea');
  ta.value=text; ta.style.position='fixed'; ta.style.top='-9999px';
  document.body.appendChild(ta); ta.focus(); ta.select();
  try{ok=document.execCommand('copy');}catch(e){}
  document.body.removeChild(ta);
  const done=()=>{flash(btn);showToast(msg);};
  if(!ok&&navigator.clipboard){navigator.clipboard.writeText(text).then(done).catch(done);return;}
  done();
}
function copyScheme(name,btn){copyText("config.color_scheme = '"+name+"'",btn,"✓ 已复制   config.color_scheme = '"+name+"'");}
function copyApply(name,btn){copyText(APPLY_CMD+'"'+name+'"',btn,"✓ 已复制应用命令，终端粘贴回车即生效");}
function flash(btn){
  if(!btn)return;
  const isLb=btn.id==='lbcopy'||btn.id==='lbapply';
  btn.classList.add('done');
  if(isLb){btn._old=btn.textContent; btn.textContent='✓ 已复制';}
  setTimeout(()=>{btn.classList.remove('done'); if(isLb&&btn._old){btn.textContent=btn._old; btn._old=null;}},1400);
}
function showToast(msg){
  const t=document.getElementById('toast');
  t.textContent=msg; t.classList.add('show'); setTimeout(()=>t.classList.remove('show'),1600);
}
function openLightbox(file,name){
  document.getElementById('lb').src=file;
  document.getElementById('lbcap').textContent=name;
  const c=document.getElementById('lbcopy'); c.dataset.name=name; c.textContent="复制  config.color_scheme = '"+name+"'";
  const a=document.getElementById('lbapply'); a.dataset.name=name;
  document.getElementById('lightbox').classList.add('show');
}
grid.addEventListener('click',e=>{
  const cp=e.target.closest('.copy');
  if(cp){e.stopPropagation(); copyScheme(cp.dataset.name,cp); return;}
  const ap=e.target.closest('.apply');
  if(ap){e.stopPropagation(); copyApply(ap.dataset.name,ap); return;}
  const c=e.target.closest('.card'); if(!c)return;
  openLightbox(c.dataset.file,c.dataset.name);
});
document.getElementById('lbcopy').addEventListener('click',function(e){e.stopPropagation(); copyScheme(this.dataset.name,this);});
document.getElementById('lbapply').addEventListener('click',function(e){e.stopPropagation(); copyApply(this.dataset.name,this);});
const lb=document.getElementById('lightbox');
lb.addEventListener('click',()=>lb.classList.remove('show'));
document.addEventListener('keydown',e=>{if(e.key==='Escape')lb.classList.remove('show');});
render();
</script></body></html>
"""


def generate_index(themes, outpath):
    html = INDEX_HTML.replace('__COUNT__', str(len(themes)))
    html = html.replace('__THEMES__', json.dumps(themes, ensure_ascii=False))
    html = html.replace('__APPLY_PATH__', str(ROOT / 'scripts' / 'apply_kaku_theme.py'))
    outpath.write_text(html, encoding='utf-8')


def main():
    sample = '--sample' in sys.argv
    outroot = out_dir()
    outroot.mkdir(parents=True, exist_ok=True)
    for sub in ('dark', 'light'):
        (outroot / sub).mkdir(exist_ok=True)

    schemes = parse_schemes(SCHEME_FILE)
    if sample:
        wanted = {'Dracula', 'Catppuccin Latte', 'Tokyo Night'}
        schemes = [(n, t) for n, t in schemes if n in wanted]

    print(f"rendering {len(schemes)} schemes -> {outroot}")

    def task(item):
        name, toml = item
        try:
            return build(name, toml, outroot)
        except Exception as e:  # noqa: BLE001
            print(f"  FAIL {name}: {e}")
            return None

    themes = []
    with ThreadPoolExecutor(max_workers=8) as ex:
        for r in ex.map(task, schemes):
            if r:
                themes.append(r)

    # tidy: remove stale flat-layout PNGs left directly under outroot
    stale = [p for p in outroot.glob('*.png')]
    for p in stale:
        p.unlink()
    if stale:
        print(f"removed {len(stale)} stale flat-layout png")

    generate_index(sorted(themes, key=lambda t: t['name'].lower()), outroot / 'index.html')

    dark = sum(1 for t in themes if t['cat'] == 'dark')
    print(f"done: {len(themes)} themes ({dark} dark, {len(themes) - dark} light)")
    print(f"gallery: file://{outroot / 'index.html'}")


if __name__ == '__main__':
    main()
