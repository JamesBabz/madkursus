const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const path = require('node:path');

let browser;
before(async () => {
  browser = await chromium.launch({ headless: true, channel: process.env.CHAT_TEST_BROWSER_CHANNEL || 'msedge' });
});
after(async () => { await browser?.close(); });

async function render(text, check) {
  const page = await browser.newPage({ viewport: { width: 320, height: 640 } });
  try {
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.setContent('<article class="chat-message chat-message-assistant"><strong>Madhjælp</strong><div class="chat-markdown"></div></article>');
    const root = path.resolve(__dirname, '../../main/resources/static');
    await page.addStyleTag({ path: path.join(root, 'css/app.css') });
    for (const file of ['vendor/marked.umd.js', 'vendor/purify.min.js', 'chat-markdown.js']) {
      await page.addScriptTag({ path: path.join(root, 'js', file) });
    }
    await page.evaluate(text => {
      window.executed = false;
      renderChatMarkdown(document.querySelector('.chat-markdown'), text);
    }, text);
    await check(page, page.locator('.chat-markdown'));
    assert.equal(await page.evaluate(() => window.executed), false);
    assert.deepEqual(errors, []);
  } finally { await page.close(); }
}

test('plain Danish text, paragraphs and line breaks remain readable', async () => {
  await render('Kog kartoflerne i vand & salt.\nPrøv med en kniv.\n\nSmag til.', async (page, content) => {
    assert.equal(await content.locator('p').count(), 2);
    assert.equal(await content.locator('br').count(), 1);
    assert.match(await content.textContent(), /vand & salt/);
    assert.equal(await content.locator('p').first().evaluate(el => getComputedStyle(el).marginTop), '0px');
    assert.equal(await content.locator('p').last().evaluate(el => getComputedStyle(el).marginBottom), '0px');
  });
});

test('emphasis, unordered/ordered lists and modest headings are real formatting', async () => {
  await render('## Sovs\n\n* **Mel:** tilsæt lidt.\n* *Fløde:* rør rundt.\n\n1. Kog op.\n2. Smag til.', async (page, content) => {
    assert.equal(await content.locator('h2').textContent(), 'Sovs');
    assert.equal(await content.locator('ul > li').count(), 2);
    assert.equal(await content.locator('ol > li').count(), 2);
    assert.equal(await content.locator('strong').textContent(), 'Mel:');
    assert.equal(await content.locator('em').textContent(), 'Fløde:');
    assert.equal(await content.locator('strong').evaluate(el => getComputedStyle(el).display), 'inline');
  });
});

test('inline and fenced code stay literal and long code wraps inside mobile bubbles', async () => {
  await render('Brug `1:2`.\n\n```html\n<script>window.executed=true</script>\n' + 'long'.repeat(120) + '\n```', async (page, content) => {
    assert.equal(await content.locator('p > code').textContent(), '1:2');
    assert.match(await content.locator('pre > code').textContent(), /<script>window.executed=true<\/script>/);
    assert.equal(await content.locator('script').count(), 0);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  });
});

test('raw active HTML, malicious attributes, links and embedded media are removed', async () => {
  await render(`<script>window.executed=true</script>
<p onclick="window.executed=true" style="position:fixed" id="chat-input" data-x="x">Sikker tekst <strong onmouseover="window.executed=true">fed</strong></p>
<img src=x onerror="window.executed=true"><svg onload="window.executed=true"></svg>
<iframe srcdoc="<script>parent.executed=true</script>"></iframe><form><input autofocus onfocus="window.executed=true"></form>

[farligt link](javascript:window.executed=true)
![billede](https://example.invalid/tracker)
<a href="javascript:window.executed=true">link</a>
<math><mtext><table><mglyph><style><!--</style><img title="--><img src=x onerror=window.executed=true>">`, async (page, content) => {
    assert.equal(await content.locator('script,img,svg,math,iframe,form,input,a,style,table').count(), 0);
    assert.equal(await content.locator('[onclick],[onmouseover],[onerror],[onload],[style],[id],[data-x]').count(), 0);
    assert.match(await content.textContent(), /Sikker tekst/);
    await content.locator('strong').click();
  });
});
