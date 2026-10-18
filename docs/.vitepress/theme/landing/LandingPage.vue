<script setup lang="ts">
import { withBase } from 'vitepress'
import IdeShowcase from './IdeShowcase.vue'
import CompareCards from './CompareCards.vue'
import RunnerPair from './RunnerPair.vue'
import CompatCard from './CompatCard.vue'
import LandingFooter from './LandingFooter.vue'
import LucideIcon from './LucideIcon.vue'
import { ideTabs, generateCompare, validateRules, skills } from './data'
</script>

<template>
  <div class="landing-root">
    <section class="band hero white">
      <div class="inner">
        <h1>Your BPMN. <span class="accent">In&nbsp;Code.</span><br />Validated. AI&#8209;ready.</h1>
        <p class="lead">
          Generate type-safe APIs, validate architecture rules, and make your BPMN models AI-ready, straight from your build.
        </p>
        <div class="actions">
          <a class="btn-primary" href="https://bpmn-to-code.miragon.io/static/index.html" target="_blank" rel="noopener">Try it in the browser</a>
          <a class="text-link" :href="withBase('/overview/why')">Why bpmn-to-code <LucideIcon name="arrow-right" /></a>
        </div>
        <div class="hero-stage">
          <IdeShowcase :tabs="ideTabs" />
        </div>
        <CompatCard />
      </div>
    </section>

    <section class="band gray">
      <div class="inner">
        <div class="intro">
          <p class="eyebrow"><span class="num">01</span><span class="name">Generate</span><span class="sub">compile-time safety</span></p>
          <h2>BPMN strings break silently. <span class="accent">Until runtime.</span></h2>
          <p>Your Zeebe and Camunda code is full of references to BPMN elements: process IDs, message names, task types. Rename one in the modeler and nothing warns you until it fails in production. bpmn-to-code generates a type-safe Process API in Kotlin, Java or C# (experimental) from your <code>.bpmn</code> files, via Gradle, Maven or the web app.</p>
          <a class="text-link" :href="withBase('/guide/generated-api')">Learn how the generated API works <LucideIcon name="arrow-right" /></a>
        </div>
        <CompareCards
          before-pill="Before"
          before-title="hardcoded strings"
          :before-code="generateCompare.before"
          after-pill="After"
          after-title="generated Process API"
          :after-code="generateCompare.after"
        />
      </div>
    </section>

    <section class="band white">
      <div class="inner">
        <div class="intro">
          <p class="eyebrow"><span class="num">02</span><span class="name">Validate</span><span class="sub">architecture rules</span><span class="tag">beta</span></p>
          <h2>Process rules erode <span class="accent">between deploys.</span></h2>
          <p>Missing implementations, undefined timers, unnamed messages: without architecture tests, BPMN quality drifts. The testing module is a JUnit-friendly rule set you add to any project, and you can write your own rules for team conventions. Violations show up in your test suite before they reach staging.</p>
          <a class="text-link" :href="withBase('/validate/')">See the validation rules <LucideIcon name="arrow-right" /></a>
        </div>
        <RunnerPair
          :command="validateRules.command"
          :fail="validateRules.fail"
          fail-summary="4 violations"
          fail-note="build blocked"
          :pass="validateRules.pass"
          pass-summary="4 rules validated"
          pass-note="ready to ship"
        />
      </div>
    </section>

    <section class="band gray">
      <div class="inner">
        <div class="intro">
          <p class="eyebrow"><span class="num">03</span><span class="name">Agent Skills</span><span class="tag">beta</span></p>
          <h2>Build processes with agents, <span class="accent">not around them.</span></h2>
          <p>Agent skills built on bpmn-to-code. Set up the plugin in one prompt, migrate hardcoded BPMN strings to the generated Process API, and turn your BPMN style guide into validation rules. They work with Claude Code and any other agent that supports the <a href="https://agentskills.io" target="_blank" rel="noopener">agentskills.io</a> standard.</p>
          <a class="text-link" :href="withBase('/skills/')">See all skills <LucideIcon name="arrow-right" /></a>
        </div>
        <ul class="skills">
          <li v-for="skill in skills" :key="skill.name">
            <a :href="withBase('/skills/')">
              <span class="skill-name">{{ skill.name }}</span>
              <span class="skill-desc">{{ skill.description }}</span>
              <LucideIcon name="arrow-right" />
            </a>
          </li>
        </ul>
      </div>
    </section>

    <section class="band white">
      <div class="inner closing">
        <h2>Try it on your own model.</h2>
        <p class="lead">Upload a BPMN file in the browser, or wire the generator into your build.</p>
        <div class="actions">
          <a class="btn-primary" href="https://bpmn-to-code.miragon.io/static/index.html" target="_blank" rel="noopener">Open the web app</a>
          <a class="text-link" :href="withBase('/getting-started/gradle')">Gradle setup <LucideIcon name="arrow-right" /></a>
          <a class="text-link" :href="withBase('/getting-started/maven')">Maven setup <LucideIcon name="arrow-right" /></a>
        </div>
      </div>
    </section>

    <LandingFooter />
  </div>
</template>
