import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { render } from './dist/client/entry-server.js'

// @ts-ignore
const __dirname = dirname(fileURLToPath(import.meta.url));
const template = readFileSync(resolve(__dirname, 'dist/client/index.html'), 'utf-8')

// Add any routes you want pre-rendered:
const routes = ['/', '/about']

for (const url of routes) {
    const { html } = render(url)
    const out = template.replace('<!-- ssr-outlet -->', html)
    const outFile = resolve(__dirname, 'dist/client', url === '/' ? 'index.html' : `${url.replace(/^\//,'')}/index.html`)
    mkdirSync(dirname(outFile), { recursive: true })
    writeFileSync(outFile, out)
    console.log(`Pre-rendered: ${url}`)
}
