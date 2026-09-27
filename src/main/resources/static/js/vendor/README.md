# Chat Markdown dependencies

Locally served browser distributions (no runtime CDN or frontend build required):

- Marked 18.0.14: `lib/marked.umd.js`, MIT, https://github.com/markedjs/marked
- DOMPurify 3.4.16: `dist/purify.min.js`, Apache-2.0 OR MPL-2.0, https://github.com/cure53/DOMPurify

Downloaded from the pinned npm package tarballs and verified against their SHA-512 integrity metadata. License files are included alongside the distributions. When updating, replace the browser distributions and licenses, run the chat browser tests, and bump the app-shell asset version.
