import { themes as prismThemes } from "prism-react-renderer";
import type { Config } from "@docusaurus/types";
import type * as Preset from "@docusaurus/preset-classic";

const config: Config = {
  title: "ChatOps4s",
  tagline: "Simplified Chat-ops for Scala",
  favicon: "img/favicon/favicon.ico",

  // GitHub pages deployment config.
  url: "https://business4s.github.io/",
  baseUrl: "/chatops4s/",
  organizationName: "business4s",
  projectName: "chatops4s",
  trailingSlash: true,

  onBrokenLinks: "throw",
  onBrokenMarkdownLinks: "warn",

  i18n: {
    defaultLocale: "en",
    locales: ["en"],
  },

  presets: [
    [
      "classic",
      {
        docs: {
          sidebarPath: "./sidebars.ts",
          editUrl: "https://github.com/business4s/chatops4s/tree/main/website",
          beforeDefaultRemarkPlugins: [
            [
              require("remark-code-snippets"),
              { baseDir: ".." },
            ],
          ],
        },
        theme: {
          customCss: "./src/css/custom.css",
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    navbar: {
      title: "ChatOps4s",
      logo: {
        alt: "ChatOps4s Logo",
        src: "img/chatops4s-logo.drawio.png",
      },
      items: [
        {
          type: "docSidebar",
          sidebarId: "tutorialSidebar",
          position: "left",
          label: "Docs",
        },
        {
          href: "https://github.com/business4s/chatops4s",
          label: "GitHub",
          position: "right",
        },
      ],
    },
    footer: {
      style: "dark",
      links: [],
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ["java", "scala", "json"],
    },
  } satisfies Preset.ThemeConfig,
  customFields: {
    chatops4sVersion: process.env.CHATOPS4S_VERSION,
  },
};

export default config;
