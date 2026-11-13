"""Gera um EPUB 3 (compatível com Kindle) a partir do CURSO.md.

Uso (de dentro da pasta livro/, depois da preparação descrita no README):
    python gerar_epub.py                      # gera saida/curso-angular-spring-boot.epub
    python gerar_epub.py <CURSO.md> <saida.epub>

Precisa do Google Chrome instalado: ele desenha os diagramas e a capa.
Se o Chrome estiver em outro lugar, informe o caminho na variável de ambiente CHROME.
"""
import datetime
import html
import json
import os
import re
import shutil
import subprocess
import sys
import uuid
import zipfile
from pathlib import Path

import markdown

LIVRO = Path(__file__).resolve().parent
CURSO = Path(sys.argv[1]) if len(sys.argv) > 1 else LIVRO.parent / "CURSO.md"
SAIDA = Path(sys.argv[2]) if len(sys.argv) > 2 else LIVRO / "saida" / "curso-angular-spring-boot.epub"
TRABALHO = LIVRO / "trabalho"  # arquivos intermediários (diagramas, capa)
GITHUB = "https://github.com/erickdeoliveiraleal/hello-angular/blob/jsession-uppercase/"
TITULO = "Login por sessão com Angular e Spring Boot"
SUBTITULO = "Um curso para quem vem de JSF, Java e WildFly"


def achar_chrome():
    candidatos = [os.environ.get("CHROME"),
                  r"C:\Program Files\Google\Chrome\Application\chrome.exe",
                  r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
                  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                  shutil.which("google-chrome"), shutil.which("chromium")]
    for c in candidatos:
        if c and Path(c).exists():
            return c
    sys.exit("Chrome não encontrado. Informe o caminho na variável de ambiente CHROME.")


CHROME = achar_chrome()
NPX = shutil.which("npx") or sys.exit("npx não encontrado: instale o Node.js.")

if TRABALHO.exists():
    shutil.rmtree(TRABALHO)
imagens = TRABALHO / "images"
imagens.mkdir(parents=True)
# O mermaid-cli usa o Chrome já instalado, em vez de baixar um só para ele.
(TRABALHO / "puppeteer.json").write_text(
    json.dumps({"executablePath": CHROME, "args": ["--no-sandbox"]}), encoding="utf-8")
texto = CURSO.read_text(encoding="utf-8")


# 1. Diagramas Mermaid -> PNG (o Kindle não desenha Mermaid).
def renderizar(match, contador=[0]):
    contador[0] += 1
    n = contador[0]
    fonte = TRABALHO / f"diagrama-{n}.mmd"
    png = imagens / f"diagrama-{n}.png"
    # Tela do Kindle é "em pé": fluxogramas horizontais (LR) ficam verticais (TB).
    fonte.write_text(match.group(1).replace("flowchart LR", "flowchart TB"), encoding="utf-8")
    subprocess.run(
        [NPX, "--no-install", "mmdc", "-p", str(TRABALHO / "puppeteer.json"), "-c", "mermaid.json",
         "-b", "white", "-w", "900", "-s", "2", "-i", str(fonte), "-o", str(png)],
        cwd=LIVRO, check=True, capture_output=True)
    return f"![Diagrama {n}](images/diagrama-{n}.png)"


texto = re.sub(r"```mermaid\n(.*?)```", renderizar, texto, flags=re.S)


# 2. Links para arquivos do repositório viram links para o GitHub.
def link_repo(match):
    rotulo, alvo = match.group(1), match.group(2)
    if alvo.startswith(("#", "http://", "https://", "images/")):
        return match.group(0)
    return f"[{rotulo}]({GITHUB}{alvo})"


texto = re.sub(r"\[([^\]]*)\]\(([^)\s]+)\)", link_repo, texto)


# 3. Âncoras iguais às do GitHub, para os links internos (#...) continuarem valendo.
def slug(valor, separador="-"):
    valor = valor.strip().lower()
    valor = re.sub(r"[^\w\- ]", "", valor)
    return valor.replace(" ", separador)


def para_xhtml(md):
    return markdown.markdown(
        md, output_format="xhtml",
        extensions=["tables", "fenced_code", "sane_lists", "toc"],
        extension_configs={"toc": {"slugify": slug}})


# 4. Divide em capítulos nos títulos "## ". Pula o sumário manual (o EPUB tem o seu).
linhas = [l for l in texto.splitlines() if l.strip() != "---"]
capitulos, atual = [], {"titulo": None, "linhas": []}
for linha in linhas:
    if linha.startswith("## "):
        capitulos.append(atual)
        atual = {"titulo": linha[3:].strip(), "linhas": [linha]}
    else:
        atual["linhas"].append(linha)
capitulos.append(atual)

abertura = capitulos.pop(0)  # título "# ..." e o parágrafo de apresentação
capitulos = [c for c in capitulos if c["titulo"] != "Sumário"]
abertura_md = "\n".join(l for l in abertura["linhas"] if not l.startswith("# "))

ESTILO = """
body { font-family: serif; line-height: 1.45; margin: 0 4%; }
h1, h2, h3, h4 { font-family: sans-serif; line-height: 1.2; page-break-after: avoid; }
h2 { font-size: 1.5em; margin-top: 0; }
h3 { font-size: 1.2em; margin-top: 1.6em; }
h4 { font-size: 1.05em; margin-top: 1.3em; }
code { font-family: monospace; font-size: 0.9em; }
pre { font-family: monospace; font-size: 0.78em; line-height: 1.3; white-space: pre-wrap;
      word-wrap: break-word; border-left: 3px solid #888; padding: 0.4em 0.6em;
      background: #f2f2f2; page-break-inside: avoid; }
pre code { font-size: 1em; }
table { border-collapse: collapse; width: 100%; font-size: 0.85em; margin: 0.8em 0; }
th, td { border: 1px solid #888; padding: 0.25em 0.4em; vertical-align: top; text-align: left; }
th { background: #e6e6e6; }
blockquote { margin: 1em 0; padding: 0.2em 0.8em; border-left: 3px solid #555; font-style: italic; }
img { max-width: 100%; height: auto; }
p.figura { text-align: center; margin: 1em 0; }
.capa-titulo { font-family: sans-serif; font-size: 2em; margin-top: 30%; text-align: center; }
.capa-sub { text-align: center; font-style: italic; }
"""


def pagina(titulo, corpo):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="pt-BR" xml:lang="pt-BR">
<head><meta charset="utf-8" /><title>{html.escape(titulo)}</title>
<link rel="stylesheet" type="text/css" href="estilo.css" /></head>
<body>
{corpo}
</body>
</html>
"""


arquivos = []  # (nome, titulo, html)
abertura_html = (f'<h1 class="capa-titulo">{html.escape(TITULO)}</h1>\n'
                 f'<p class="capa-sub">{html.escape(SUBTITULO)}</p>\n' + para_xhtml(abertura_md))
arquivos.append(("abertura.xhtml", "Apresentação", abertura_html))
for i, cap in enumerate(capitulos, start=1):
    corpo = para_xhtml("\n".join(cap["linhas"]))
    # Imagem sozinha num parágrafo vira uma figura centralizada.
    corpo = re.sub(r"<p>(<img [^>]+/>)</p>", r'<p class="figura">\1</p>', corpo)
    # Em tabela, "\|" serve só para escapar a barra vertical; o GitHub não mostra a "\".
    corpo = corpo.replace("\\|", "|")
    arquivos.append((f"cap{i:02d}.xhtml", cap["titulo"], corpo))

# 5. Links "#ancora" apontam para o arquivo do capítulo onde a âncora está.
onde = {}
for nome, _, corpo in arquivos:
    for ancora in re.findall(r' id="([^"]+)"', corpo):
        onde.setdefault(ancora, nome)
faltando = set()


def corrigir_ancora(match):
    ancora = html.unescape(match.group(1))
    if ancora not in onde:
        faltando.add(ancora)
        return match.group(0)
    return f'href="{onde[ancora]}#{match.group(1)}"'


arquivos = [(n, t, re.sub(r'href="#([^"]+)"', corrigir_ancora, c)) for n, t, c in arquivos]

# 6. Sumário (nav do EPUB 3 e toc.ncx para leitores antigos), com os títulos ### dentro.
itens = []
for nome, titulo, corpo in arquivos:
    subs = re.findall(r'<h3 id="([^"]+)">(.*?)</h3>', corpo)
    itens.append((nome, titulo, [(f"{nome}#{a}", re.sub("<[^>]+>", "", t)) for a, t in subs]))


def nav_li(href, titulo, filhos):
    sub = ""
    if filhos:
        sub = "<ol>" + "".join(f'<li><a href="{h}">{html.escape(html.unescape(t))}</a></li>' for h, t in filhos) + "</ol>"
    return f'<li><a href="{href}">{html.escape(titulo)}</a>{sub}</li>'


nav = pagina("Sumário", '<nav epub:type="toc" id="toc"><h1>Sumário</h1><ol>'
             + "".join(nav_li(n, t, f) for n, t, f in itens) + "</ol></nav>")

identificador = f"urn:uuid:{uuid.uuid5(uuid.NAMESPACE_URL, GITHUB + 'CURSO.md')}"
ncx_pontos, ordem = "", 0
for nome, titulo, filhos in itens:
    ordem += 1
    meu = ordem
    sub = ""
    for href, t in filhos:
        ordem += 1
        sub += (f'<navPoint id="p{ordem}" playOrder="{ordem}"><navLabel><text>{html.escape(html.unescape(t))}</text>'
                f'</navLabel><content src="{href}"/></navPoint>')
    ncx_pontos += (f'<navPoint id="p{meu}" playOrder="{meu}"><navLabel><text>{html.escape(titulo)}</text>'
                   f'</navLabel><content src="{nome}"/>{sub}</navPoint>')
ncx = f"""<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="{identificador}"/></head>
<docTitle><text>{html.escape(TITULO)}</text></docTitle>
<navMap>{ncx_pontos}</navMap>
</ncx>
"""

# 7. Capa: uma página HTML simples fotografada pelo Chrome em modo headless.
capa_html = TRABALHO / "capa.html"
capa_html.write_text(f"""<!doctype html><html><body style="margin:0;width:1600px;height:2560px;
background:#1f2d3d;color:#fff;font-family:Segoe UI,Arial,sans-serif;display:flex;flex-direction:column;
justify-content:center;padding:0 140px;box-sizing:border-box">
<div style="font-size:64px;opacity:.75;margin-bottom:60px">Curso</div>
<div style="font-size:150px;font-weight:700;line-height:1.08">Login por sessão com Angular e Spring Boot</div>
<div style="font-size:66px;margin-top:90px;opacity:.85">Para quem vem de JSF, Java e WildFly</div>
<div style="font-size:52px;margin-top:220px;opacity:.6">JSESSIONID · Spring Security · CSRF · BCrypt · Permissões</div>
</body></html>""", encoding="utf-8")
capa_png = imagens / "capa.png"
subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                f"--screenshot={capa_png}", "--window-size=1600,2560", capa_html.as_uri()],
               check=True, capture_output=True)

# 8. Manifesto (content.opf).
agora = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
pngs = sorted(p.name for p in imagens.glob("*.png"))
manifesto = "".join(
    f'<item id="{n.replace(".xhtml", "")}" href="{n}" media-type="application/xhtml+xml"/>' for n, _, _ in arquivos)
manifesto += "".join(
    f'<item id="img-{p.replace(".png", "")}" href="images/{p}" media-type="image/png"'
    + (' properties="cover-image"' if p == "capa.png" else "") + "/>" for p in pngs)
espinha = '<itemref idref="capa"/>' + "".join(f'<itemref idref="{n.replace(".xhtml", "")}"/>' for n, _, _ in arquivos)
opf = f"""<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid" xml:lang="pt-BR">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">{identificador}</dc:identifier>
<dc:title>{html.escape(TITULO)}</dc:title>
<dc:creator>Projeto hello-angular</dc:creator>
<dc:language>pt-BR</dc:language>
<dc:description>{html.escape(SUBTITULO)}</dc:description>
<meta property="dcterms:modified">{agora}</meta>
<meta name="cover" content="img-capa"/>
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
<item id="css" href="estilo.css" media-type="text/css"/>
<item id="capa" href="capa.xhtml" media-type="application/xhtml+xml"/>
{manifesto}
</manifest>
<spine toc="ncx">{espinha}</spine>
<guide><reference type="cover" title="Capa" href="capa.xhtml"/><reference type="toc" title="Sumário" href="nav.xhtml"/></guide>
</package>
"""
capa_xhtml = pagina("Capa", '<div style="text-align:center"><img src="images/capa.png" alt="Capa" /></div>')

# 9. Empacota: "mimetype" precisa ser o primeiro arquivo, sem compressão.
SAIDA.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(SAIDA, "w") as z:
    z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
    def add(caminho, conteudo):
        z.writestr(caminho, conteudo, compress_type=zipfile.ZIP_DEFLATED)
    add("META-INF/container.xml", """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
""")
    add("OEBPS/content.opf", opf)
    add("OEBPS/nav.xhtml", nav)
    add("OEBPS/toc.ncx", ncx)
    add("OEBPS/estilo.css", ESTILO)
    add("OEBPS/capa.xhtml", capa_xhtml)
    for nome, titulo, corpo in arquivos:
        add(f"OEBPS/{nome}", pagina(titulo, corpo))
    for p in pngs:
        z.write(imagens / p, f"OEBPS/images/{p}", compress_type=zipfile.ZIP_DEFLATED)

print(f"capítulos: {len(arquivos)}, imagens: {len(pngs)}, âncoras sem destino: {sorted(faltando) or 'nenhuma'}")
print(f"gerado: {SAIDA} ({SAIDA.stat().st_size // 1024} KB)")
