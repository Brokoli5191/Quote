// The parts of node:sqlite (Node.js 22.13+) the D1 test double uses. The project does not
// depend on @types/node; the Worker itself is typed by @cloudflare/workers-types.
declare module "node:sqlite" {
  type SqlValue = null | number | bigint | string | Uint8Array;

  interface StatementSync {
    all(...parameters: SqlValue[]): Record<string, SqlValue>[];
    get(...parameters: SqlValue[]): Record<string, SqlValue> | undefined;
    run(...parameters: SqlValue[]): { changes: number | bigint; lastInsertRowid: number | bigint };
  }

  export class DatabaseSync {
    constructor(location: string);
    exec(sql: string): void;
    prepare(sql: string): StatementSync;
    close(): void;
  }
}
