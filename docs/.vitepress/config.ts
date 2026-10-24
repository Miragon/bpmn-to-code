// The docs site, built with the Miragon docs kit (docs/.vitepress/miragon/, vendored from
// Miragon/corporate-identity, skill docs-site). The kit sets everything else: base from
// DOCS_BASE, theme, colours light and dark, GitHub anchors, Mermaid, search, the legal row.
import { miragonDocs } from './miragon/config'

export default miragonDocs({
  repo: 'Miragon/bpmn-to-code',
  title: 'Miragon bpmn-to-code',
  description: 'Generates a typed API from BPMN models and validates them, for Camunda 8, Camunda 7 and Operaton.',
  productPage: 'https://miragon.ai/tools/bpmn-to-code/',
  footerMessage: 'Released under the MIT License.',
  ignoreDeadLinks: [/\/\.claude\//],
  vite: {
    server: {
      // portless serves the dev server under a `<name>.localhost` host, which Vite rejects unless allow-listed.
      allowedHosts: ['.localhost'],
    },
  },
  nav: [
    { text: 'Guide', link: '/getting-started/gradle' },
    { text: 'Generated API', link: '/guide/generated-api' },
    { text: 'Validate', link: '/validate/' },
    { text: 'Agent skills', link: '/skills/' },
    { text: 'Demo', link: 'https://bpmn-to-code.miragon.io/static/index.html' },
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
        { text: '.NET (experimental)', link: '/getting-started/dotnet' },
        { text: 'TypeScript (experimental)', link: '/getting-started/typescript' },
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
      text: 'Agent Skills',
      items: [
        { text: 'Available Skills', link: '/skills/' },
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
            { text: 'v7 Migration Guide', link: '/changelog/v7' },
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
        { text: 'Languages and Ecosystems', link: '/contributing/languages' },
        { text: 'Releasing', link: '/contributing/releasing' },
        { text: 'Benchmarking the Generator', link: '/contributing/benchmark' },
        { text: 'Architecture Decisions', link: '/contributing/adr/' },
      ],
    },
  ],
})

