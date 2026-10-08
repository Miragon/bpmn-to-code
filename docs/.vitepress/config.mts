import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'bpmn-to-code',
  titleTemplate: false,
  description: 'Type-safe BPMN toolkit — generate APIs, validate models, surface process structure to your toolchain.',
  base: '/bpmn-to-code/',
  appearance: false,
  ignoreDeadLinks: [/\/\.claude\//],

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/bpmn-to-code/favicon.png' }],
  ],

  themeConfig: {
    logo: '/favicon.png',

    nav: [
      { text: 'Generate', link: '/getting-started/gradle' },
      { text: 'Validate', link: '/validate/' },
      { text: 'JSON Export', link: '/surface/json' },
      { text: 'AI Skills', link: '/skills/' },
      {
        text: 'Links',
        items: [
          { text: 'GitHub', link: 'https://github.com/Miragon/bpmn-to-code' },
          { text: 'Maven Central', link: 'https://central.sonatype.com/artifact/io.miragon/bpmn-to-code-maven' },
          { text: 'Gradle Plugin Portal', link: 'https://plugins.gradle.org/plugin/io.miragon.bpmn-to-code-gradle' },
        ],
      },
    ],

    sidebar: [
      {
        text: 'Start',
        items: [
          { text: 'Why bpmn-to-code', link: '/overview/why' },
        ],
      },
      {
        text: 'Generate',
        items: [
          { text: 'Gradle', link: '/getting-started/gradle' },
          { text: 'Maven', link: '/getting-started/maven' },
          { text: 'Web App', link: '/web/' },
          { text: 'Generated API', link: '/guide/generated-api' },
          { text: 'Process Paths', link: '/guide/process-path' },
          { text: 'Modeling Guide', link: '/guide/modeling' },
          { text: 'Verify in CI', link: '/guide/verify-in-ci' },
        ],
      },
      {
        text: 'Validate',
        items: [
          { text: 'Build-time Validation', link: '/validate/' },
          { text: 'Testing Module', link: '/validate/testing' },
          { text: 'Custom Rules', link: '/validate/custom-rules' },
        ],
      },
      {
        text: 'JSON Export',
        items: [
          { text: 'Process JSON', link: '/surface/json' },
        ],
      },
      {
        text: 'AI Skills',
        items: [
          { text: 'Agent Skills', link: '/skills/' },
        ],
      },
      {
        text: 'Reference',
        items: [
          { text: 'Configuration', link: '/guide/configuration' },
          { text: 'Engines', link: '/engines/' },
          {
            text: 'Changelog',
            collapsed: true,
            items: [
              { text: 'Release Notes', link: '/changelog/' },
              { text: 'v6 Migration Guide', link: '/changelog/v6' },
              { text: 'Older Versions', link: '/changelog/older' },
            ],
          },
        ],
      },
      {
        text: 'Contributing',
        collapsed: true,
        items: [
          { text: 'Contributing Guide', link: '/contributing/' },
          { text: 'Architecture', link: '/contributing/architecture' },
          { text: 'Releasing', link: '/contributing/releasing' },
          { text: 'Benchmarking the Generator', link: '/contributing/benchmark' },
          { text: 'Architecture Decisions', link: '/contributing/adr/' },
        ],
      },
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/Miragon/bpmn-to-code' },
    ],

    search: {
      provider: 'local',
    },

    editLink: {
      pattern: 'https://github.com/Miragon/bpmn-to-code/edit/main/docs/:path',
    },

    footer: {
      message: 'Open source under the <a href="https://github.com/Miragon/bpmn-to-code?tab=MIT-1-ov-file#readme" target="_blank">MIT License</a>. Contributions welcome!',
      copyright: 'Created with ♥ by <a href="https://www.linkedin.com/in/schaeckm" target="_blank">Marco Schäck</a> at <a href="https://miragon.io" target="_blank">Miragon</a> · <a href="https://www.linkedin.com/in/schaeckm" target="_blank">LinkedIn</a> · <a href="https://medium.com/@emaarco" target="_blank">Medium</a>',
    },
  },
})
