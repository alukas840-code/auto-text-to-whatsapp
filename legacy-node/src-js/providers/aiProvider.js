export class AIProvider {
  async generateText(_prompt, _context = {}) {
    throw new Error('Not implemented');
  }

  async rewriteStyle(_text, _style) {
    throw new Error('Not implemented');
  }

  async summarize(_messages) {
    throw new Error('Not implemented');
  }
}

export class MockAIProvider extends AIProvider {
  async generateText(prompt, context = {}) {
    if (context?.persona?.includes('Дәурен')) {
      return `Понял Вас. Как Дәурен, виртуальный коллега, предлагаю такой ответ: ${prompt}`;
    }
    return `AI-ответ: ${prompt}`;
  }

  async rewriteStyle(text, style = 'официальный') {
    return `[${style}] ${text}`;
  }

  async summarize(messages) {
    return `Сводка (${messages.length}): ${messages.join(' ').slice(0, 180)}`;
  }
}
