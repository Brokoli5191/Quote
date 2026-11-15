/// <reference types="vite/client" />
import { DatabaseSync } from "node:sqlite";

// An in-memory D1 stand-in on top of SQLite with the real migrations applied, so tests see
// the same CHECK, UNIQUE and foreign key constraints as production. batch() runs in one
// transaction like D1's.

const migrations = import.meta.glob<string>("../migrations/*.sql", { query: "?raw", import: "default", eager: true });

type Row = Record<string, unknown>;

class TestStatement {
  constructor(
    private readonly sqlite: DatabaseSync,
    private readonly sql: string,
    private readonly values: unknown[] = []
  ) {}

  bind(...values: unknown[]): TestStatement {
    return new TestStatement(this.sqlite, this.sql, values);
  }

  async first<T = Row>(column?: string): Promise<T | null> {
    const row = this.sqlite.prepare(this.sql).get(...this.parameters());
    if (!row) return null;
    return (column === undefined ? { ...row } : row[column]) as T;
  }

  async all<T = Row>(): Promise<D1Result<T>> {
    const rows = this.sqlite.prepare(this.sql).all(...this.parameters());
    return result(rows.map(row => ({ ...row }) as T), 0);
  }

  async run<T = Row>(): Promise<D1Result<T>> {
    return this.execute<T>();
  }

  execute<T = Row>(): D1Result<T> {
    if (/^\s*(SELECT|WITH)\b/i.test(this.sql)) {
      const rows = this.sqlite.prepare(this.sql).all(...this.parameters());
      return result(rows.map(row => ({ ...row }) as T), 0);
    }
    const outcome = this.sqlite.prepare(this.sql).run(...this.parameters());
    return result<T>([], Number(outcome.changes));
  }

  private parameters() {
    return this.values.map(value => {
      if (value === undefined) throw new TypeError("D1_TYPE_ERROR: Type 'undefined' not supported");
      return value as null | number | string;
    });
  }
}

function result<T>(results: T[], changes: number): D1Result<T> {
  return { success: true, results, meta: { changes } } as unknown as D1Result<T>;
}

export interface TestDatabase {
  db: D1Database;
  sqlite: DatabaseSync;
}

export function createTestDatabase(): TestDatabase {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec("PRAGMA foreign_keys = ON");
  for (const path of Object.keys(migrations).sort()) sqlite.exec(migrations[path]);

  const db = {
    prepare: (sql: string) => new TestStatement(sqlite, sql),
    async batch(statements: TestStatement[]) {
      sqlite.exec("BEGIN");
      try {
        const results = statements.map(statement => statement.execute());
        sqlite.exec("COMMIT");
        return results;
      } catch (error) {
        sqlite.exec("ROLLBACK");
        throw error;
      }
    }
  };
  return { db: db as unknown as D1Database, sqlite };
}
