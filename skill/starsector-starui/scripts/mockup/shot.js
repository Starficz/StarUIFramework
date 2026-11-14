// screenshots every .board of <dir>/mock.html at 1x and 2x and reports layout problems
const path = require('path');
const { chromium } = require('playwright');

(async () => {
  const dir = path.resolve(process.argv[2] || '.');
  const browser = await chromium.launch();
  let warnings = 0;
  for (const scale of [1, 2]) {
    const page = await browser.newPage({ deviceScaleFactor: scale, viewport: { width: 2200, height: 1200 } });
    await page.goto('file://' + path.join(dir, 'mock.html'));
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(300);

    if (scale === 1) {
      const found = await page.evaluate(() => {
        const out = [];
        const tight = el => {
          const r = document.createRange();
          r.selectNodeContents(el);
          const a = r.getBoundingClientRect(), b = el.getBoundingClientRect();
          return { l: Math.max(a.left, b.left), t: Math.max(a.top, b.top), r: Math.min(a.right, b.right), b: Math.min(a.bottom, b.bottom) };
        };
        for (const host of document.querySelectorAll('.frame, .screen')) {
          const board = host.closest('.board');
          if (!board) continue;
          const hr = host.getBoundingClientRect();
          const texts = [...host.querySelectorAll('.ins15, .ins21, .orb12, .orb20, .vic10')].filter(e => e.textContent.trim());
          for (const el of texts) {
            const say = el.textContent.trim().slice(0, 40);
            if (el.classList.contains('t') && el.scrollWidth > el.clientWidth + 1)
              out.push(`${board.id}: text does not fit its box (${el.scrollWidth} > ${el.clientWidth} px): "${say}"`);
            const r = el.getBoundingClientRect();
            if (r.left < hr.left - 1 || r.top < hr.top - 1 || r.right > hr.right + 1 || r.bottom > hr.bottom + 1)
              out.push(`${board.id}: text outside the frame: "${say}"`);
          }
          const boxes = texts.map(e => ({ e, r: tight(e) })).filter(b => b.r.r - b.r.l > 1 && b.r.b - b.r.t > 1);
          for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
            const a = boxes[i], b = boxes[j];
            if (a.e.contains(b.e) || b.e.contains(a.e)) continue;
            if (!!a.e.closest('.tip') !== !!b.e.closest('.tip')) continue;
            const w = Math.min(a.r.r, b.r.r) - Math.max(a.r.l, b.r.l), h = Math.min(a.r.b, b.r.b) - Math.max(a.r.t, b.r.t);
            if (w > 2 && h > 4)
              out.push(`${board.id}: text overlaps: "${a.e.textContent.trim().slice(0, 30)}" and "${b.e.textContent.trim().slice(0, 30)}"`);
          }
        }
        return out;
      });
      for (const f of found) console.log('WARN ' + f);
      warnings = found.length;
    }

    for (const el of await page.$$('.board')) {
      const id = await el.getAttribute('id');
      await el.screenshot({ path: path.join(dir, id + (scale > 1 ? '@2x' : '') + '.png') });
      if (scale === 1) console.log('shot ' + id + '.png and ' + id + '@2x.png');
    }
    await page.close();
  }
  await browser.close();
  console.log(warnings ? warnings + ' layout warnings: fix them before showing the boards' : 'no layout warnings');
})().catch(e => { console.error(String(e)); process.exit(1); });
