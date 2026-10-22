import { useState } from 'react';

export default function BucketDeleteButton({
  bucket,
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
      `Удалить бакет «${bucket}»?\n\n` +
      'Все файлы, их версии и маркеры удаления будут удалены безвозвратно. ' +
      'Незавершённые загрузки будут отменены.\n\n' +
      'Это действие нельзя отменить.',
    );

    if (!confirmed) {
      return;
    }

    setDeleting(true);
    setError('');

    try {
      const response = await fetch(
        `/api/buckets/${encodeURIComponent(bucket)}?confirmed=true`,
        {
          method: 'DELETE',
        },
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        setError(
          data?.error ||
          'Не удалось завершить удаление. Проверьте состояние бакета.',
        );
        return;
      }

      onDeleted(bucket);
    } catch {
      setError(
        'Не удалось получить ответ. Обновите список бакетов перед повторной попыткой.',
      );
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div>
      <button
        type="button"
        onClick={handleDelete}
        disabled={deleting || disabled}
        aria-label={`Удалить бакет ${bucket}`}
      >
        {deleting ? 'Удаляем…' : 'Удалить бакет'}
      </button>

      {error && <p role="alert">{error}</p>}
    </div>
  );
}