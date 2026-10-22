import { useEffect, useState } from 'react';
import BucketCreateForm from './components/BucketCreateForm.jsx';
import BucketDeleteButton from './components/BucketDeleteButton.jsx';
import ObjectList from './components/ObjectList.jsx';

export default function App() {
  const [buckets, setBuckets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshIndex, setRefreshIndex] = useState(0);
  const [bucketMessage, setBucketMessage] = useState('');
  const [selectedBucket, setSelectedBucket] = useState('');

  function handleBucketDeleted(name) {
    setBucketMessage(`Бакет «${name}» удалён.`);
    setSelectedBucket((current) => current === name ? '' : current);
    setRefreshIndex((value) => value + 1);
  }

  useEffect(() => {
    const controller = new AbortController();

    async function loadBuckets() {
      setLoading(true);
      setError('');

      try {
        const response = await fetch('/api/buckets', {
          signal: controller.signal,
        });

        if (!response.ok) {
          throw new Error('Не удалось получить список бакетов.');
        }

        const data = await response.json();

        if (
          !Array.isArray(data) ||
          !data.every((name) => typeof name === 'string')
        ) {
          throw new Error('Получен некорректный список бакетов.');
        }

        if (!controller.signal.aborted) {
          setBuckets(data);
          setSelectedBucket((current) =>
            data.includes(current) ? current : '',
          );
        }
      } catch {
        if (!controller.signal.aborted) {
          setError(
            'Не удалось загрузить список бакетов. Проверьте соединение и повторите попытку.',
          );
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    }

    loadBuckets();

    return () => controller.abort();
  }, [refreshIndex]);

  return (
    <main>
      <h1>S3 Галерея</h1>
      <p>Изображения и файлы в облачном хранилище.</p>

      <section aria-labelledby="buckets-title">
        <h2 id="buckets-title">Бакеты</h2>

        <BucketCreateForm
          onCreated={() => {
            setBucketMessage('');
            setRefreshIndex((value) => value + 1);
          }}
        />

        <button
          type="button"
          disabled={loading}
          onClick={() => setRefreshIndex((value) => value + 1)}
        >
          Обновить список
        </button>

        {bucketMessage && <p role="status">{bucketMessage}</p>}
        {loading && <p role="status">Загружаем бакеты…</p>}
        {error && <p role="alert">{error}</p>}

        {!loading && !error && (
          buckets.length === 0 ? (
            <p>Бакетов пока нет.</p>
          ) : (
            <ul>
              {buckets.map((name) => (
                <li key={name}>
                  <button
                    type="button"
                    onClick={() => setSelectedBucket(name)}
                    aria-pressed={selectedBucket === name}
                  >
                    {name}
                  </button>

                  <BucketDeleteButton
                    bucket={name}
                    onDeleted={handleBucketDeleted}
                  />
                </li>
              ))}
            </ul>
          )
        )}
      </section>

      {selectedBucket ? (
        <ObjectList
          key={selectedBucket}
          bucket={selectedBucket}
        />
      ) : (
        <p>Выберите бакет, чтобы увидеть файлы.</p>
      )}
    </main>
  );
}