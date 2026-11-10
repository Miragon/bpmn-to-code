export default class ProcessApiRecipe {
    #options;

    constructor(options) {
        this.#options = options;
    }

    async capture(window) {
        await window.openFile(this.#options.file);
        await window.pause(1500);
        await window.page.keyboard.press("Control+G");
        await window.page.keyboard.type(String(this.#options.line));
        await window.page.keyboard.press("Enter");
        await window.page.keyboard.press("Escape");
        await window.pause(800);
        await window.hero(
            await window.page.locator(".part.editor").boundingBox(),
        );
    }
}
