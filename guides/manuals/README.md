# Template manual

The canonical source is `template-getting-started-en.html`. It covers architecture,
setup, modes, seeds, extension, testing, deployment, recovery and releases.
`MyScoutee_Template_Getting_Started_EN.pdf` is its stable downloadable output.

With the frontend development dependencies and Chrome/Chromium available:

```sh
node guides/manuals/build-manuals.mjs T-START-001
pdfinfo guides/manuals/MyScoutee_Template_Getting_Started_EN.pdf
```

The renderer checks metadata and internal links, then generates the PDF. Inspect
its pages before committing. Update the document version in the HTML and
`manuals.json` together; the software version changes only with the software.

The same renderer accepts `--catalog /path/to/manuals.json` for the shared
components guide. Paths in a catalogue are relative to that catalogue.
This runs only the document renderer, not an application build or server.
