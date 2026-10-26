<script setup lang="ts">
import { computed, nextTick, onMounted, provide, ref } from "vue"
import LandingDemo from "./LandingDemo.vue"

const version = "7.0.1" // x-release-please-version

const facts = [
  { label: "Languages", value: "Kotlin · Java · C# · TypeScript", note: "(C# and TS experimental)" },
  { label: "Engines", value: "Camunda 8 · Camunda 7 · Operaton" },
  { label: "Build", value: "Gradle · Maven · MSBuild · CLI" },
]
const webApp = "https://bpmn-to-code.miragon.io/static/index.html"
const installs = [
  { name: "Gradle", prompt: "", lines: [`id("io.miragon.bpmn-to-code-gradle") version "${version}"`] },
  { name: "Maven", prompt: "", lines: [`<artifactId>bpmn-to-code-maven</artifactId>`] },
  { name: "CLI", prompt: "$", lines: ["brew install miragon/tap/bpmn-to-code"] },
  {
    name: "Claude Code",
    prompt: ">",
    lines: ["/plugin marketplace add Miragon/bpmn-to-code", "/plugin install bpmn-to-code@bpmn-to-code"],
  },
]
const steps = [
  { name: "Generate", effect: "One typed API per process, and the code that references it." },
  { name: "Implement", effect: "An agent skill writes the worker against the generated API." },
  { name: "Test", effect: "Architecture rules for your models, as a JUnit test." },
  { name: "Trace", effect: "Assert the route of a process test, typed by the model." },
]

const selected = ref(0)
const scene = ref(0)
const copied = ref(false)

// One clock for the page: the demo plays a scene, then moves on to the next step.
// Pause in the demo, hovering or focusing the steps, or choosing one stops it.
const moving = ref(false)
const stepsChosen = ref(false)
const stepsHeld = ref(false)
const advancing = computed(() => moving.value && !stepsChosen.value && !stepsHeld.value)
const sceneProgress = ref(0)
provide("landing-moving", moving)
provide("landing-scene", scene)
provide("landing-advancing", advancing)
provide("landing-scene-progress", sceneProgress)
provide("landing-scene-count", steps.length)

onMounted(() => {
  if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) moving.value = true
})

function chooseScene(index: number) {
  stepsChosen.value = true
  scene.value = index
}

const tabButtons = ref([] as HTMLButtonElement[])
async function selectTabBy(offset: number) {
  selected.value = (selected.value + offset + installs.length) % installs.length
  await nextTick()
  tabButtons.value[selected.value]?.focus()
}

const sceneButtons = ref([] as HTMLButtonElement[])
async function selectSceneBy(offset: number) {
  chooseScene((scene.value + offset + steps.length) % steps.length)
  await nextTick()
  sceneButtons.value[scene.value]?.focus()
}

async function copyInstall() {
  await navigator.clipboard.writeText(installs[selected.value].lines.join("\n"))
  copied.value = true
  setTimeout(() => (copied.value = false), 1600)
}
</script>

<template>
  <div class="landing">
    <section class="hero">
      <div class="frame hero-frame">
        <div class="pitch">
          <h1>Model it once.<br /><span class="accent">Code</span> against it.</h1>
          <p class="lede">
            Turns every BPMN process into a typed API, checks the models in your tests and gives your agent the
            skills to implement them.
          </p>

          <dl class="facts">
            <template v-for="fact in facts" :key="fact.label">
              <dt>{{ fact.label }}</dt>
              <dd>{{ fact.value }} <span v-if="fact.note" class="note">{{ fact.note }}</span></dd>
            </template>
          </dl>

          <div class="install">
            <div
              class="tabs"
              role="tablist"
              aria-label="Install"
              @keydown.right.prevent="selectTabBy(1)"
              @keydown.left.prevent="selectTabBy(-1)"
            >
              <button
                v-for="(install, index) in installs"
                :key="install.name"
                ref="tabButtons"
                class="tab"
                role="tab"
                :aria-selected="index === selected"
                :tabindex="index === selected ? 0 : -1"
                @click="selected = index"
              >
                {{ install.name }}
              </button>
            </div>
            <div class="command" role="tabpanel">
              <pre><template v-for="line in installs[selected].lines" :key="line"><span v-if="installs[selected].prompt" class="prompt">{{ installs[selected].prompt }} </span>{{ line }}
</template></pre>
              <button class="copy" type="button" @click="copyInstall">{{ copied ? "Copied" : "Copy" }}</button>
            </div>
          </div>
          <p class="meta">
            <span>Needs JDK 21+</span>
            <span class="release">
              <span class="marker" aria-hidden="true" />
              <strong>{{ version }}</strong>
            </span>
          </p>
          <div class="start">
            <a class="demo" :href="webApp" target="_blank" rel="noopener">
              Try the demo <span aria-hidden="true">↗</span>
            </a>
            <p class="start-note">Upload a BPMN file in the browser, download its API.<br />No install, no account.</p>
          </div>
        </div>

        <div class="stage">
          <LandingDemo />
          <ol
            class="steps"
            role="tablist"
            aria-label="How it works"
            @mouseenter="stepsHeld = true"
            @mouseleave="stepsHeld = false"
            @focusin="stepsHeld = true"
            @focusout="stepsHeld = false"
            @keydown.right.prevent="selectSceneBy(1)"
            @keydown.left.prevent="selectSceneBy(-1)"
          >
            <li v-for="(step, index) in steps" :key="step.name" class="step" :class="{ active: index === scene }">
              <button
                ref="sceneButtons"
                class="step-button"
                role="tab"
                :aria-selected="index === scene"
                :tabindex="index === scene ? 0 : -1"
                @click="chooseScene(index)"
              >
                <span class="rail">
                  <span class="node">0{{ index + 1 }}</span>
                  <span class="track" aria-hidden="true"><span class="fill" :style="{ transform: `scaleX(${index === scene ? sceneProgress : 0})` }" /></span>
                </span>
                <span class="step-name">{{ step.name }}</span>
                <span class="step-effect">{{ step.effect }}</span>
              </button>
            </li>
          </ol>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.landing {
  --gutter: 32px;
  --line: 1px solid var(--cd-ui-linie);
  display: flex;
  flex-direction: column;
  border-top: var(--line);
}

.frame {
  width: 100%;
  max-width: var(--vp-layout-max-width);
  margin: 0 auto;
}

/* Split: the pitch on white, the demo on a grey half over the full height of the hero. */
.hero {
  flex: 1;
  display: flex;
  min-height: 0;
}
.hero-frame {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}
.pitch {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px var(--gutter);
  border-bottom: var(--line);
}
h1 {
  margin: 0;
  font-size: clamp(48px, 7vh, 72px);
  line-height: 1.02;
  font-weight: 700;
  letter-spacing: -0.045em;
}
.accent {
  color: var(--cd-ui-link);
}
.lede {
  max-width: 46ch;
  margin: 20px 0 0;
  color: var(--cd-ui-text-leise);
  line-height: 1.55;
}

.facts {
  display: grid;
  grid-template-columns: max-content 1fr;
  margin: 24px 0 0;
  border-top: var(--line);
  font-size: 15px;
}
.facts dt,
.facts dd {
  margin: 0;
  padding: 9px 0;
  border-bottom: var(--line);
}
.facts dt {
  padding-right: 36px;
  font-size: 14px;
  color: var(--cd-ui-text-leise);
}
.facts .note {
  color: var(--cd-ui-text-leise);
}

.install {
  margin-top: 28px;
  border: var(--line);
  border-radius: var(--cd-radius-sm);
  background: var(--cd-ui-abschnitt);
}
.tabs {
  display: flex;
  overflow-x: auto;
  scrollbar-width: none;
  border-bottom: var(--line);
}
.tab {
  position: relative;
  flex: none;
  padding: 11px 14px;
  border-right: var(--line);
  font: 500 13px/1 var(--vp-font-family-mono);
  color: var(--cd-ui-text-leise);
}
.tab:first-child {
  border-top-left-radius: var(--cd-radius-sm);
}
.tab[aria-selected="true"] {
  color: var(--cd-ui-text);
  background: var(--cd-ui-seite);
}
.tab[aria-selected="true"]::after {
  content: "";
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: var(--cd-ui-interaktion);
  transform-origin: left;
}
@keyframes fill {
  from {
    transform: scaleX(0);
  }
}
.command {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 12px 12px 16px;
}
/* Room for two lines in every tab, so the pitch keeps its height when the tab changes. */
.command pre {
  flex: 1;
  min-height: calc(2 * 1.7em);
  min-width: 0;
  margin: 0;
  font: 400 14px/1.7 var(--vp-font-family-mono);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.command .prompt {
  color: var(--cd-ui-text-leise);
  user-select: none;
}
.copy {
  flex: none;
  height: 32px;
  padding: 0 10px;
  border: var(--line);
  border-radius: var(--cd-radius-sm);
  font: 500 12px/1 var(--vp-font-family-mono);
  color: var(--cd-ui-text);
}
.copy:hover {
  background: var(--cd-ui-hover);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 20px;
  margin: 12px 0 0;
  font: 400 12px/1.5 var(--vp-font-family-mono);
  color: var(--cd-ui-text-leise);
}
.meta a {
  color: var(--cd-ui-text-leise);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.meta a:hover {
  color: var(--cd-ui-link);
}
.release {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--cd-ui-text);
}
.release strong {
  font-weight: 500;
}
.marker {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: var(--cd-ui-success);
}
.start {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 18px;
  margin-top: 24px;
}
.demo {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 20px;
  border-radius: var(--cd-radius-sm);
  background: var(--vp-button-brand-bg);
  color: var(--vp-button-brand-text);
  font-weight: 600;
  text-decoration: none;
  transition: background var(--cd-motion-fast) var(--cd-ease);
}
.demo:hover {
  background: var(--vp-button-brand-hover-bg);
  color: var(--vp-button-brand-hover-text);
}
.start-note {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  color: var(--cd-ui-text-leise);
}

.stage {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 24px;
  min-width: 0;
  padding: 32px var(--gutter);
  background: var(--cd-ui-abschnitt);
}

/* The steps below the window: each one is a scene of the demo, the active one fills its rail. */
.steps {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 24px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.step-button {
  display: flex;
  flex-direction: column;
  width: 100%;
  text-align: left;
  color: inherit;
}
.rail {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 26px;
}
.node {
  display: inline-grid;
  flex: none;
  place-items: center;
  height: 22px;
  min-width: 28px;
  padding: 0 4px;
  border: 1px solid var(--cd-ui-kontur);
  border-radius: 3px;
  background: var(--cd-ui-seite);
  font: 500 11px/1 var(--vp-font-family-mono);
  color: var(--cd-ui-text-leise);
}
.active .node {
  border-color: var(--cd-ui-interaktion);
  background: var(--cd-ui-interaktion);
  color: var(--cd-ui-seite);
}
.track {
  position: relative;
  flex: 1;
  height: 1px;
  background: var(--cd-ui-linie);
}
.fill {
  position: absolute;
  inset: -0.5px 0;
  background: var(--cd-ui-interaktion);
  transform: scaleX(0);
  transform-origin: left;
}
.step-name {
  margin: 12px 0 6px;
  font-size: 19px;
  line-height: 1.2;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.step-button:hover .step-name {
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-decoration-color: var(--cd-ui-interaktion);
  text-underline-offset: 4px;
}
.step-effect {
  font-size: 14px;
  line-height: 1.45;
  color: var(--cd-ui-text-leise);
}

@media (min-width: 960px) {
  .hero-frame {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-width: none;
  }
  .pitch {
    width: 100%;
    max-width: calc(var(--vp-layout-max-width) / 2);
    justify-self: end;
    border-right: var(--line);
    border-bottom: 0;
  }
  .stage {
    gap: 20px;
    padding: 24px 40px;
  }
  .steps {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

/* One screen on desktop: navigation, hero and the legal row (53px) fill the viewport. */
@media (min-width: 960px) and (min-height: 700px) {
  .landing {
    min-height: calc(100dvh - var(--vp-nav-height) - 53px);
  }
}

@media (max-width: 640px) {
  .landing {
    --gutter: 20px;
  }
  .pitch {
    padding: 44px var(--gutter) 40px;
  }
  h1 {
    font-size: clamp(36px, 11.5vw, 56px);
  }
  .facts dt {
    padding-right: 20px;
  }
  .stage {
    padding: 24px 12px;
  }
  .command {
    flex-direction: column;
    gap: 10px;
    padding: 12px;
  }
  .command pre {
    width: 100%;
    font-size: 12px;
  }
  .copy {
    align-self: flex-end;
  }
}

</style>
