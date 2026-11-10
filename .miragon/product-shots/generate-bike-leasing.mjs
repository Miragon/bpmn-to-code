export default async function generateBikeLeasingApi(page, { click, pause }) {
    await click(page.locator('[data-sample-file="zeebe-bike-leasing.bpmn"]'));
    await page.locator("#bpmn-canvas svg").first().waitFor();
    await page.locator("#output-language").selectOption("KOTLIN");
    await click(page.locator("#generate-btn"));
    await page.locator("#results-content").waitFor({ state: "visible" });
    await pause(800);
}
