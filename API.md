# API soap4 (v2)

Неофициальное описание API, которым пользуется Soap TV. Документации у API нет, всё ниже проверено запросами или взято из поведения клиентов. Пометка «не проверялось» значит, что запрос в Soap TV есть, но ответ сервера не сверялся.

## Общее

- Хост `https://api.soap4uand.me/v2`. Обложки и субтитры лежат на `soap4youand.me`.
- Заголовки:
  - `X-API-TOKEN: <token>` и `Cookie: PHPSESSID=<sid>`, оба приходят при входе;
  - `Accept-Encoding: gzip`;
  - `User-Agent: Android: soap4u_flutter v1.0.0`;
  - для POST `Content-Type: application/x-www-form-urlencoded`.
- `GET /soap/genres` работает без токена.

## Вход

- `POST /auth`, тело `login=…&password=…`. Ответ: `token`, `sid`, `till` (конец подписки), `success`, `message`.
- `POST /logout` выходит.
- `GET /auth/check` отдаёт `login`, `till` (секунды), `4k`, `family` (список с `fid`, `name`, `main`) и `family_test`.
- `GET /family/` отдаёт `family` и `selected`.

## Сериалы

| Метод и путь | Что отдаёт |
|---|---|
| `GET /soap/?nodesc` | все сериалы |
| `GET /soap/my/?nodesc` | сериалы пользователя |
| `GET /soap/top/all` | популярные, 20 шт. |
| `GET /soap/top/alive` | популярные идущие |
| `GET /soap/recommendations/personal` | рекомендации |
| `GET /soap/description/<sid>` | описание, сеть, страна, жанры, лайки, статус |
| `GET /soap/genres`, `/soap/genre/<g>/ids/` | жанры и id по жанру |
| `GET /episodes/<sid>?nodesc` | серии сериала |
| `GET /episodes/continue` | «продолжить просмотр», 10 записей |
| `GET /episodes/new` | последние вышедшие серии |
| `GET /shedule/<sid>`, `/shedule/my/` | расписание (в API так, с опечаткой) |

Поля сериала: `sid`, `title`, `title_ru`, `year`, `likes`, `episode_runtime`, `country`, `status`, `network`, `description`, `imdb_id`, `imdb_rating`, `kinopoisk_id`, `tvdb_id`, `tvmaze_id`, `soap_rating`, `updated`, `4k`, `trailers`, `watching`, `unwatched`, `total_episodes`, `genres`, `covers`.

- `status`: 0 идёт, 1 пауза, 2 закрыт.
- `watching` (0/1): сериал в «Мои».
- `unwatched`: число непросмотренных серий.

Запись «продолжить просмотр»: `soap`, `soap_ru`, `title_ru`, `title_en`, `sid`, `season_id`, `season`, `episode`, `played` (время запуска), `time`, `covers`, `files`.

`/episodes/new`: 15 последних серий. У тестового аккаунта все из сериалов в «Мои», похоже, лента персональная. Поля как у «продолжить просмотр», вместо `played` и `time` идут `first_aired` (`YYYY-MM-DD`) и `spoiler`. Числа строками. Порядок не по дате: записи сгруппированы по сериалу.

`/shedule/my/`: ближайшие серии сериалов из «Мои», около недели вперёд, плюс пара прошедших дней. Поля: `sid`, `soap`, `soap_ru`, `covers`, `season`, `episode`, `short` (`s02e06`), `title` (бывает `TBA`), `date` (`DD.MM.YYYY`). `files` нет.

`/shedule/<sid>/`: все серии сериала, прошлые и будущие, от новых к старым. Поля: `season`, `episode`, `short`, `title`, `date`.

В `files` у каждого файла:
- `eid`, `hash`;
- `quality`: 1 SD, 2 HD, 3 FullHD, 4 UltraHD, бывает строкой;
- `translate`: 1 оригинал, 2 оригинал с английскими субтитрами, 3 оригинал с русскими, 4 перевод.

Постер: `https://soap4youand.me/assets/covers/soap/big/<sid>.jpg`.

### Действия

| Запрос | Тело |
|---|---|
| `POST /soap/watch/<sid>/`, `/soap/unwatch/<sid>/` | `sid`. Добавить в «Мои» и убрать |
| `POST /episodes/watch/<sid>/<season>/<episode>/`, `unwatch` | `sid&season&episode`, не проверялось |
| `POST /episodes/watch/full/<sid>/<season>/` | `sid&season`, весь сезон, не проверялось |

Запрос, снимающий отметку с целого сезона, неизвестен.

### Поток серии

1. Из `files` выбирается один файл.
2. `POST /play/episode/<eid>`, тело `hash=<md5>&eid=<eid>`, где `md5 = MD5(token + eid + sid + file.hash)` в hex нижнего регистра.
3. Ответ: `ok`, `stream` (прямая ссылка), `start_from` (секунды строкой), `subs`, `poster`, `title`.
4. Позиция: `POST /play/episode/<eid>/savets/`, тело `eid=<eid>&time=<секунды>`.

Перевод обычно отдельный файл в `files`, но бывает и второй аудиодорожкой внутри потока.

## Фильмы

| Метод и путь | Что отдаёт |
|---|---|
| `GET /movies/` | все фильмы |
| `GET /movies/my` | «Мои», то же, что `liked=True` |
| `GET /movies/popular` | популярные, 32 шт. |
| `GET /movies/description/<id>` | карточка фильма с потоком |

Карточка: название, оригинальное название, описание на двух языках, `year`, `runtime`, `countries`, `genres`, `actors`, `directors`, `qualities`, `subs`, `imdb_rating`, `kinopoisk_rating`, `watched`, `start_from`, `stream_url`.

- Отдельного запроса потока нет, играется `stream_url`.
- `watched` и `liked` зависят от пользователя, список фильмов надолго кэшировать нельзя.
- Обложки `covers.small/big` в списке приходят по `http://`, нужна замена на `https://`. `covers.small` на ТВ размыты, лучше брать `big`.

### Действия

| Запрос | Тело | Ответ |
|---|---|---|
| `POST /movies/like/<id>/` | `id=<id>&sub=like&do=like` или `do=unlike` | `{"ok":1,"status":"added"\|"removed"}` |
| `POST /movies/watch/<id>/`, `/movies/unwatch/<id>/` | `id=<id>` | `{"ok":1,"status":"watched"\|"unwatched"}` |

POST на `/movies/like/` без `sub` и `do` отвечает 405.

Позицию фильма сохраняет `/movies/savets/`, формат тела неизвестен.

### Поток фильма

- HLS по пути `/hls/…` без `.m3u8`, поэтому MIME плееру задаётся явно.
- В мастер-плейлисте несколько аудиодорожек (`Русский`, `English`, бывает `Français`) и субтитры.
- Варианты по качеству из `qualities` (`SD`, `HD`, `FHD`, `UHD`), каждый в `avc1` и `hvc1`. Ширина 720, 1280, 1920, 3840, высота зависит от кадра.

## Другие пути

Встречаются, но в Soap TV не используются: `/movies/franchise`, `/movies/interest/`, `/movies/actor|director|writer/`, `/soap/person/`.
