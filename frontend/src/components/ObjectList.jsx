import { useEffect, useState } from 'react';
import FileUploadForm from './FileUploadForm.jsx';
import ObjectDeleteButton from './ObjectDeleteButton.jsx';

function ObjectCard({ bucket, object, onDeleted }) {
  const [imageFailed, setImageFailed] = useState(false);

  const contentUrl =
    `/api/buckets/${encodeURIComponent(bucket)}/objects/content` +
    `?key=${encodeURIComponent(object.key)}`;

  const previewUrl = `${contentUrl}&download=false`;
  const downloadUrl = `${contentUrl}&download=true`;

  const canPreview = /\.(png|jpe?g|gif|webp|avif|bmp)$/i.test(object.key);

  return (
    <li className="object-card">
      {canPreview && !imageFailed ? (
        <img
          src={previewUrl}
          alt={`Предпросмотр ${object.key}`}
          loading="lazy"
          width="240"
          height="160"
          style={{ objectFit: 'contain' }}
          onError={() => setImageFailed(true)}
        />
      ) : (
        <p>
          {canPreview
            ? 'Предпросмотр недоступен.'
            : 'Файл для скачивания.'}
        </p>
      )}

      <h3>{object.key}</h3>
      <p>Размер: {object.size} байт</p>

      <div className="object-actions">
        {canPreview && !imageFailed && (
          <>
            <a
              href={previewUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              Просмотреть
            </a>
            {' '}
          </>
        )}

        <a href={downloadUrl}>Скачать</a>
      </div>

      <ObjectDeleteButton
        bucket={bucket}
        objectKey={object.key}
        onDeleted={onDeleted}
      />
    </li>
  );
}

export default function ObjectList({ bucket }) {
  const [objects, setObjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshIndex, setRefreshIndex] = useState(0);
  const [objectMessage, setObjectMessage] = useState('');

  function handleObjectDeleted(key) {
    setObjectMessage(`Файл «${key}» и все его версии удалены.`);
    setRefreshIndex((value) => value + 1);
  }

  useEffect(() => {
    const controller = new AbortController();

    async function loadObjects() {
      setLoading(true);
      setError('');
      setObjects([]);

      try {
        const response = await fetch(
          `/api/buckets/${encodeURIComponent(bucket)}/objects`,
          {
            signal: controller.signal,
          },
        );

        if (!response.ok) {
          throw new Error('Не удалось получить список файлов.');
        }

        const data = await response.json();

        if (
          !Array.isArray(data) ||
          !data.every(
            (item) =>
              item !== null &&
              typeof item.key === 'string' &&
              typeof item.size === 'number',
          )
        ) {
          throw new Error('Получен некорректный список файлов.');
        }

        if (!controller.signal.aborted) {
          setObjects(data);
        }
      } catch {
        if (!controller.signal.aborted) {
          setError(
            'Не удалось загрузить файлы. Попробуйте обновить список.',
          );
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    loadObjects();

    return () => controller.abort();
  }, [bucket, refreshIndex]);

  return (
    <section aria-labelledby="objects-title">
      <h2 id="objects-title">Файлы бакета «{bucket}»</h2>

      <FileUploadForm
        bucket={bucket}
        onUploaded={() => {
          setObjectMessage('');
          setRefreshIndex((value) => value + 1);
        }}
      />

      <button
        type="button"
        disabled={loading}
        onClick={() => setRefreshIndex((value) => value + 1)}
      >
        Обновить файлы
      </button>

      {objectMessage && <p role="status">{objectMessage}</p>}
      {loading && <p role="status">Загружаем файлы…</p>}
      {error && <p role="alert">{error}</p>}

      {!loading && !error && (
        objects.length === 0 ? (
          <p>В этом бакете пока нет файлов.</p>
        ) : (
          <ul className="object-grid">
            {objects.map((object) => (
              <ObjectCard
                key={object.key}
                bucket={bucket}
                object={object}
                onDeleted={handleObjectDeleted}
              />
            ))}
          </ul>
        )
      )}
    </section>
  );
}