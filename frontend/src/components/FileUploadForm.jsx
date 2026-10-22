import { useRef, useState } from 'react';

export default function FileUploadForm({ bucket, onUploaded }) {
  const formRef = useRef(null);
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function handleSubmit(event) {
    event.preventDefault();

    if (uploading) {
      return;
    }

    setMessage('');
    setError('');

    if (!file) {
      setError('Выберите файл.');
      return;
    }

    if (file.size === 0) {
      setError('Выберите непустой файл.');
      return;
    }

    if (file.size > 1024 * 1024) {
      setError('Максимальный размер файла — 1 МБ.');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);

    setUploading(true);

    try {
      const response = await fetch(
        `/api/buckets/${encodeURIComponent(bucket)}/objects`,
        {
          method: 'POST',
          body: formData,
        },
      );

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        setError(data?.error || 'Не удалось загрузить файл.');
        return;
      }

      setMessage(`Файл «${file.name}» загружен.`);
      setFile(null);
      formRef.current?.reset();
      onUploaded();
    } catch {
      setError(
        'Не удалось получить ответ. Обновите список файлов перед повторной попыткой.',
      );
    } finally {
      setUploading(false);
    }
  }

  return (
    <form ref={formRef} onSubmit={handleSubmit}>
      <h3>Загрузить файл</h3>

      <label htmlFor="upload-file">Изображение или другой файл</label>
      <input
        id="upload-file"
        type="file"
        required
        disabled={uploading}
        aria-describedby="upload-hint"
        onChange={(event) => {
          setFile(event.target.files?.[0] || null);
          setMessage('');
          setError('');
        }}
      />

      <p id="upload-hint">Максимальный размер файла — 1 МБ.</p>

      <button type="submit" disabled={uploading}>
        {uploading ? 'Загружаем…' : 'Загрузить файл'}
      </button>

      {message && <p role="status">{message}</p>}
      {error && <p role="alert">{error}</p>}
    </form>
  );
}