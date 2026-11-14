#!/usr/bin/env node
// Sinh bộ dữ liệu dev: seed Flyway (src/main/resources/db/dev) + user Keycloak (docker/keycloak/{dev,vps}).
// Chạy: node scripts/dev/gen-dev-dataset.mjs  (tất định — chạy lại ra đúng các file đã commit)
import { writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildDataset } from './dataset/people.mjs';
import { renderPeopleStructure } from './dataset/sql-people-structure.mjs';
import { renderKeycloakUsers } from './dataset/keycloak-users.mjs';
import { renderAccountsDoc } from './dataset/accounts-doc.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const write = (path, content) => {
  writeFileSync(resolve(root, path), content.replace(/\r\n/g, '\n'), 'utf8');
  console.log(`wrote ${path}`);
};

export function generate() {
  const dataset = buildDataset();
  return {
    'src/main/resources/db/dev/R__dev_01_people_structure.sql': renderPeopleStructure(dataset),
    'docker/keycloak/dev/carenest-users-0.json': renderKeycloakUsers(dataset, 'dev'),
    'docker/keycloak/vps/carenest-users-0.json': renderKeycloakUsers(dataset, 'vps'),
    'docs/TAI_KHOAN_DEV.md': renderAccountsDoc(dataset),
  };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  for (const [path, content] of Object.entries(generate())) write(path, content);
}
