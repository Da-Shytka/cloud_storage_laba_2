import { useState } from 'react';

export default function ObjectDeleteButton({
  bucket,
  objectKey,
  onDeleted,
  disabled = false,
}) {
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState('');

  async function handleDelete() {
    if (deleting || disabled) {
      return;
    }

    const confirmed = window.confirm(
      `Безвозвратно удалить файл «${objectKey}» ` +
      `из бакета «${bucket}»?\n\n` +
      'Все версии файла и маркеры удаления будут удалены.\n\n' +
      'Это действие нельзя отменить.',
    );

    if (!confirmed) {
      return;
    }

    setDeleting(true);
    setError('');

    try {
      const response = await fetch(
        `/api/buckets/${encodeURIComponent(bucket)}/objects` +
        `?key=${encodeURIComponent(objectKey)}&confirmed=true`,
        {
          method: 'DELETE',
        },
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        setError(
          data?.error ||
          'Не удалось завершить удаление. Проверьте состояние файла.',
        );
        return;
      }

      onDeleted(objectKey);
    } catch {
      setError(
        'Не удалось получить ответ. Обновите список файлов перед повторной попыткой.',
      );
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="object-delete">
      <button
        type="button"
        className="danger-button"
        onClick={handleDelete}
        disabled={deleting || disabled}
        aria-label={`Удалить файл ${objectKey}`}
      >
        {deleting ? 'Удаляем…' : 'Удалить файл'}
      </button>

      {error && <p role="alert">{error}</p>}
    </div>
  );
}