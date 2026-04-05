export class STTProvider {
  async transcribe(_audioFile, _languageHint = null) {
    throw new Error('Not implemented');
  }
}

export class MockSTTProvider extends STTProvider {
  async transcribe(_audioFile, languageHint = null) {
    if (languageHint === 'kz') return 'Бұл тесттік транскрипция.';
    return 'Это тестовая транскрипция.';
  }
}
