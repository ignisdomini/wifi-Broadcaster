<?php
declare(strict_types=1);

/**
 * Пользователи РАДИОИНФОРМАТОРа.
 *
 * Учётная запись — это позывной и кодовое слово, и больше ничего. Ни почты,
 * ни телефона, ни привязки к устройству. Само кодовое слово сюда не доезжает:
 * телефон присылает `auth_key` = sha256(позывной в нижнем регистре + ':' +
 * кодовое слово), а на сервере от него берётся password_hash. То есть даже
 * при утечке базы восстановить кодовое слово нельзя, а сам сервер никогда
 * не видел его в открытом виде.
 *
 * Обратная сторона честная и её стоит помнить: забытое кодовое слово —
 * это потерянная лента, восстанавливать нечем и некому.
 */

/** Длина кода личной ленты. 31^12 — перебрать нереально, а это и есть защита. */
const EFIR_PROFILE_CODE_LENGTH = 12;

const EFIR_HANDLE_MIN = 2;
const EFIR_HANDLE_MAX = 16;

/**
 * Позывной: буквы (любого алфавита), цифры, дефис, подчёркивание, точка.
 * Без пробелов — он мелькает в эфире и в служебных строках.
 */
function validate_handle(string $handle): ?string
{
    $handle = trim($handle);
    $length = mb_strlen($handle);

    if ($length < EFIR_HANDLE_MIN || $length > EFIR_HANDLE_MAX) {
        return 'Позывной должен быть от ' . EFIR_HANDLE_MIN . ' до ' . EFIR_HANDLE_MAX . ' символов';
    }
    if (!preg_match('/^[\p{L}\p{N}._-]+$/u', $handle)) {
        return 'В позывном можно использовать буквы, цифры, точку, дефис и подчёркивание';
    }
    return null;
}

/** auth_key приходит с телефона как hex sha256 — 64 символа. */
function validate_auth_key(string $key): bool
{
    return (bool)preg_match('/^[a-f0-9]{64}$/i', $key);
}

function find_user_by_handle(string $handle): ?array
{
    return db_one('SELECT * FROM users WHERE handle_lower = ?', [mb_strtolower(trim($handle))]);
}

function find_user_by_profile_code(string $code): ?array
{
    return db_one('SELECT * FROM users WHERE profile_code = ?', [$code]);
}

function find_user_by_id(int $id): ?array
{
    return db_one('SELECT * FROM users WHERE id = ?', [$id]);
}

/**
 * Регистрация либо вход — намеренно одно и то же действие.
 *
 * Свободный позывной занимается, занятый пускает внутрь при верном кодовом
 * слове. Так человек возвращает свою ленту на новом телефоне, не заводя
 * никакого «восстановления доступа», которого при полной анонимности
 * всё равно неоткуда взять.
 *
 * @return array{user: array, created: bool}
 * @throws RuntimeException если позывной занят кем-то другим
 */
function register_or_login(string $handle, string $authKey): array
{
    $handle = trim($handle);
    $existing = find_user_by_handle($handle);

    if ($existing !== null) {
        if (!password_verify($authKey, (string)$existing['auth_hash'])) {
            throw new RuntimeException('Этот позывной уже занят. Выберите другой.');
        }
        if ((int)$existing['is_blocked'] === 1) {
            throw new RuntimeException('Этот позывной заблокирован.');
        }
        // Записи, заведённые до появления auth_lookup, дозаполняются при входе.
        db_exec(
            'UPDATE users SET last_seen_at = NOW(), auth_lookup = ? WHERE id = ?',
            [auth_lookup($authKey), (int)$existing['id']]
        );
        return ['user' => find_user_by_handle($handle) ?? $existing, 'created' => false];
    }

    $code = unique_profile_code();
    db_exec(
        'INSERT INTO users (handle, handle_lower, auth_hash, auth_lookup, profile_code, created_at, last_seen_at)
         VALUES (?, ?, ?, ?, ?, NOW(), NOW())',
        [
            $handle,
            mb_strtolower($handle),
            password_hash($authKey, PASSWORD_DEFAULT),
            auth_lookup($authKey),
            $code,
        ]
    );

    $user = find_user_by_handle($handle);
    if ($user === null) {
        throw new RuntimeException('Не удалось создать позывной');
    }
    return ['user' => $user, 'created' => true];
}

/**
 * Индекс для поиска владельца ключа. Быстрый хеш здесь уместен: проверку
 * всё равно делает медленный auth_hash, а по этому столбцу только ищут.
 */
function auth_lookup(string $authKey): string
{
    return hash('sha256', strtolower($authKey));
}

/**
 * Авторизация запроса от приложения.
 *
 * Позывной в запросе не участвует намеренно: он кириллический, а
 * HTTP-заголовки не переживают не-ASCII, и вдобавок при каждой публикации
 * светить имя незачем. Уходит только ключ, владелец находится по индексу.
 *
 * Возвращает null, если приложение не представилось, — это не ошибка:
 * без учётной записи передача просто не попадёт ни в чью ленту.
 */
function authenticate_request(): ?array
{
    $authKey = trim((string)($_SERVER['HTTP_X_EFIR_AUTH'] ?? $_POST['auth_key'] ?? ''));
    if (!validate_auth_key($authKey)) {
        return null;
    }

    $user = db_one('SELECT * FROM users WHERE auth_lookup = ?', [auth_lookup($authKey)]);
    if ($user === null || (int)$user['is_blocked'] === 1) {
        return null;
    }
    if (!password_verify($authKey, (string)$user['auth_hash'])) {
        return null;
    }

    db_exec('UPDATE users SET last_seen_at = NOW() WHERE id = ?', [(int)$user['id']]);
    return $user;
}

function unique_profile_code(): string
{
    for ($attempt = 0; $attempt < 10; $attempt++) {
        $code = generate_code(EFIR_PROFILE_CODE_LENGTH);
        if (find_user_by_profile_code($code) === null) {
            return $code;
        }
    }
    throw new RuntimeException('Не удалось подобрать код ленты');
}

function refresh_posts_count(int $userId): void
{
    db_exec(
        'UPDATE users SET posts_count = (SELECT COUNT(*) FROM posts WHERE user_id = ?) WHERE id = ?',
        [$userId, $userId]
    );
}

/** @return array<int, array<string, mixed>> */
function user_posts(int $userId, int $limit = 50, int $offset = 0): array
{
    $limit = max(1, min(100, $limit));
    $offset = max(0, $offset);
    return db_all(
        'SELECT * FROM posts WHERE user_id = ? AND is_hidden = 0
         ORDER BY id DESC LIMIT ' . $limit . ' OFFSET ' . $offset,
        [$userId]
    );
}

function count_user_posts(int $userId): int
{
    $row = db_one(
        'SELECT COUNT(*) AS c FROM posts WHERE user_id = ? AND is_hidden = 0',
        [$userId]
    );
    return (int)($row['c'] ?? 0);
}
