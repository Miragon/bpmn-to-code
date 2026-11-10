export default async function generateBikeLeasingApi(page, { click, pause }) {
  await click(page.locator('[data-sample-file="zeebe-bike-leasing.bpmn"]'));
  await page.locator("#bpmn-canvas svg").first().waitFor();
  await page.locator("#output-language").selectOption("KOTLIN");
  await click(page.locator("#generate-btn"));
  await page.locator("#results-content").waitFor({ state: "visible" });
  await page.locator("#results-content pre").first().waitFor();
  await page.evaluate(() => {
    const code = document.querySelector("#results-content pre");
    const target = code.textContent.indexOf("object ServiceTaskSendContract");
    const walker = document.createTreeWalker(code, NodeFilter.SHOW_TEXT);
    let offset = 0;
    while (walker.nextNode()) {
      const length = walker.currentNode.textContent.length;
      if (offset + length > target) {
        const range = document.createRange();
        range.setStart(walker.currentNode, target - offset);
        const top = range.getBoundingClientRect().top + window.scrollY;
        window.scrollTo(0, top - 150);
        return;
      }
      offset += length;
    }
  });
  await pause(800);
}
