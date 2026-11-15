import { describe, expect, it } from "vitest";
import { dashboardPage } from "../src/dashboard";
import worker, { type Env } from "../src/index";

const env = { RATE_LIMIT_SALT: "test-salt" } as Env;

async function page(path: string) {
  const response = await worker.fetch(new Request(`https://quote.test${path}`), env);
  return { response, html: await response.text() };
}

function count(html: string, tag: string): number {
  return html.split(`<${tag}`).length - 1;
}

describe("privacy page", () => {
  it("serves English by default and links the German version", async () => {
    const { response, html } = await page("/privacy");
    expect(response.headers.get("Content-Language")).toBe("en");
    expect(html).toContain('<html lang="en">');
    expect(html).toContain('href="/privacy?lang=de"');
  });

  it("serves German at /privacy?lang=de and links the English version", async () => {
    const { response, html } = await page("/privacy?lang=DE");
    expect(response.headers.get("Content-Language")).toBe("de");
    expect(html).toContain('<html lang="de">');
    expect(html).toContain("Datenschutz- und Einreichungsrichtlinie");
    expect(html).toContain('href="/privacy?lang=en"');
  });

  it("falls back to English for unknown languages", async () => {
    expect((await page("/privacy?lang=fr")).html).toContain('<html lang="en">');
  });

  it("has the same sections and paragraphs in both languages", async () => {
    const english = (await page("/privacy?lang=en")).html;
    const german = (await page("/privacy?lang=de")).html;
    for (const tag of ["h2", "p", "a"]) {
      expect(count(german, tag), tag).toBe(count(english, tag));
    }
  });
});

describe("dashboard page", () => {
  it("contains scripts that parse", async () => {
    const html = await dashboardPage().text();
    const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(match => match[1]);
    expect(scripts.length).toBeGreaterThan(0);
    for (const script of scripts) {
      expect(() => new Function(script)).not.toThrow();
    }
  });
});
