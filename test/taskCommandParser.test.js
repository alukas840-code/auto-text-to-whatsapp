import test from 'node:test';
import assert from 'node:assert/strict';
import { parseTaskArgs } from '../src/parsers/taskCommandParser.js';

test('parse dated task with explicit time', () => {
  const parsed = parseTaskArgs(['18:00', '@ivanov', '25.05.2026', 'Подготовить', 'отчёт']);
  assert.equal(parsed.type, 'dated');
  assert.equal(parsed.reminderTime, '18:00');
  assert.deepEqual(parsed.assignees, ['ivanov']);
  assert.equal(parsed.dueDate, '25.05.2026');
  assert.equal(parsed.text, 'Подготовить отчёт');
});

test('parse weekly task', () => {
  const parsed = parseTaskArgs(['@petrov', 'понедельник', 'Сформировать', 'сводку']);
  assert.equal(parsed.type, 'weekly');
  assert.equal(parsed.weekday, 'понедельник');
  assert.equal(parsed.text, 'Сформировать сводку');
});

test('parse simple task with two assignees', () => {
  const parsed = parseTaskArgs(['@a', '@b', 'Сделать', 'задачу']);
  assert.equal(parsed.type, 'simple');
  assert.deepEqual(parsed.assignees, ['a', 'b']);
});
