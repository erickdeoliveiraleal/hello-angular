# O curso em EPUB (para Kindle)

O `gerar_epub.py` transforma o [`CURSO.md`](../CURSO.md) num livro EPUB 3:

- um capítulo para cada título `## `, com sumário navegável que inclui as subseções;
- os diagramas Mermaid viram imagens PNG, porque o Kindle não desenha Mermaid. Os fluxogramas horizontais ficam em pé para caber na tela;
- os links para arquivos do projeto apontam para o GitHub, e os links internos (`#...`) continuam funcionando entre capítulos;
- uma capa gerada a partir de uma página HTML.

Quando o `CURSO.md` mudar, é só gerar de novo.

## Preparar (uma vez)

Precisa de Python 3, Node.js e Google Chrome. Dentro da pasta `livro/`:

```bash
npm install
python -m venv .venv
```

No Windows:

```bash
.venv\Scripts\pip install -r requirements.txt
```

No Linux ou macOS:

```bash
.venv/bin/pip install -r requirements.txt
```

O `npm install` traz o mermaid-cli, que desenha os diagramas usando o Chrome já instalado. Se o Chrome estiver num lugar diferente do padrão, informe o caminho na variável de ambiente `CHROME`.

## Gerar

No Windows:

```bash
.venv\Scripts\python gerar_epub.py
```

No Linux ou macOS:

```bash
.venv/bin/python gerar_epub.py
```

O livro sai em `saida/curso-angular-spring-boot.epub`. As pastas `saida/`, `trabalho/` (arquivos intermediários), `node_modules/` e `.venv/` não vão para o Git.

## Mandar para o Kindle

Pelo app Send to Kindle, pelo site amazon.com/sendtokindle ou por e-mail para o endereço do seu Kindle. A Amazon aceita EPUB e converte sozinha.
