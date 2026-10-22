import { useState } from 'react';

export default function BucketCreateForm({ onCreated }) {
  const [name, setName] = useState('');
  const [creating, setCreating] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();

    if (creating) {
      return;
    }

    const bucketName = name.trim();

    if (!bucketName) {
      setError('Введите имя бакета.');
      return;
    }

    setCreating(true);
    setMessage('');
    setError('');

    try {
      const response = await fetch('/api/buckets', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name: bucketName }),
      });

      const data = await response.json();

      if (!response.ok) {
        setError(data.error || 'Не удалось создать бакет.');
        return;
      }

      setName('');
      setMessage(`Бакет «${data.name}» создан.`);
      onCreated();
    } catch {
      setError(
        'Не удалось получить ответ. Обновите список бакетов перед повторной попыткой.',
      );
    } finally {
      setCreating(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <h3>Новый бакет</h3>

      <label htmlFor="new-bucket-name">Имя бакета</label>
      <input
        id="new-bucket-name"
        type="text"
        value={name}
        onChange={(event) => setName(event.target.value)}
        minLength={3}
        maxLength={63}
        required
        disabled={creating}
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        aria-describedby="bucket-name-hint"
      />

      <p id="bucket-name-hint">
        3–63 символа: строчные латинские буквы, цифры, точки и дефисы.
      </p>

      <button type="submit" disabled={creating}>
        {creating ? 'Создаём…' : 'Создать бакет'}
      </button>

      {message && <p role="status">{message}</p>}
      {error && <p role="alert">{error}</p>}
    </form>
  );
}